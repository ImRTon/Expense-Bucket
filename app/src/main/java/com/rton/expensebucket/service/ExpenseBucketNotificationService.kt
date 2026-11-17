package com.rton.expensebucket.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.rton.expensebucket.R
import com.rton.expensebucket.data.model.NonPaymentNotification
import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import com.rton.expensebucket.ocr.NotificationCapturePolicy
import com.rton.expensebucket.ocr.NotificationParser
import com.rton.expensebucket.ocr.NotificationDeduplicator
import com.rton.expensebucket.ocr.NotificationPaymentMethodMatcher
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Locale
import javax.inject.Inject

/**
 * Intercepts notifications from banking / payment apps and
 * shows a heads-up confirmation notification to the user.
 *
 * User must grant notification access in Settings → Notification access.
 */
@AndroidEntryPoint
class ExpenseBucketNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpensesNotifSvc"
        private const val CHANNEL_ID = "expenses_transaction_capture"

        /**
         * Package name prefixes of apps we want to intercept.
         */
        val WATCHED_PACKAGES = setOf(
            // E-payments
            "com.jkos.app",             // 街口支付
            "com.linepaytw.upay",       // LINE Pay
            "jp.naver.line.android",    // LINE (contains LINE Pay notifs)
            "com.easycard.wallet",      // 悠遊付
            "tw.com.pchome.android.pi", // Pi 拍錢包
            "com.pxpayplus.app",        // 全支付

            // Banks
            "tw.com.taishinbank.ccapp", // 台新 Richart Life
            "tw.com.taishinbank.richart", // 台新 Richart
            "com.esunbank",             // 玉山銀行
            "com.cathaybk.mymobibank",  // 國泰世華
            "com.ctbcbank.ctbc",        // 中國信託
            "com.fubon.mobilebank",     // 富邦銀行
            "com.fubon.aibank",         // 富邦銀行 (aibank)
            "com.sinopac.DaCard",       // 永豐大咖
            "com.sinopac.dawho",        // 永豐大戶
            "com.sionpac.app.SinoPac",  // 永豐行動銀行
            "wbank.ubot.com.tw",        // 聯邦銀行
            "tw.gov.post.mpost",        // 郵局

            // Google Pay / Apple Pay (system-level)
            "com.google.android.apps.walletnfcrel",  // Google Pay
        )
    }

    @Inject lateinit var repository: ExpenseBucketRepository

    private val parser = NotificationParser()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("zh", "TW"))

    private val processedNonPaymentNotifications = object : java.util.LinkedHashMap<String, Long>(50, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Long>?): Boolean {
            return size > 50
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created, notification channel ready")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName ?: return

        // Step 1: Filter by watched packages
        if (!isWatchedPackage(pkg)) return

        val now = System.currentTimeMillis()

        val notification = sbn.notification ?: return
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) {
            Log.d(TAG, "[$pkg] Group summary notification, skipping")
            return
        }

        val extras = notification.extras ?: return

        val combined = extractNotificationText(extras)

        if (combined.isBlank()) {
            return
        }

        val primaryText = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT)
            .joinToString(" ") { extras.getCharSequence(it)?.toString().orEmpty() }
        if (!NotificationCapturePolicy.shouldInspect(pkg, primaryText)) return

        Log.d(TAG, "[$pkg] Intercepted notification")

        // Step 2: Parse with NotificationParser
        val parsed = parser.parse(combined, pkg)
        if (parsed == null) {
            Log.d(TAG, "[$pkg] Parser returned null — no pattern matched")
            if (NotificationCapturePolicy.shouldKeepUnparsed(combined)) {
                recordNonPaymentNotification(pkg, combined, now)
            }
            return
        }

        Log.d(TAG, "[$pkg] Payment notification parsed")

        // Step 3: Insert as draft and show heads-up notification
        serviceScope.launch {
            try {
                val paymentMethodId = if (pkg == "com.google.android.apps.walletnfcrel") {
                    NotificationPaymentMethodMatcher.findPaymentMethodId(
                        combined, repository.getAllPaymentMethods().first()
                    )
                } else null
                val capturePackage = if (pkg == "jp.naver.line.android" && combined.contains("刷卡")) {
                    "$pkg#card"
                } else pkg
                val transactionId = repository.insertNotificationDraftIfAbsent(
                    Transaction(
                        amount = parsed.amount,
                        note = parsed.merchant.ifBlank { parsed.note },
                        isExpense = parsed.isExpense,
                        source = "notification",
                        paymentMethodId = paymentMethodId,
                        isDraft = true,
                        date = parsed.date ?: now,
                        createdAt = now
                    ),
                    NotificationCapture(
                        packageName = capturePackage,
                        notificationKey = sbn.key,
                        textHash = NotificationDeduplicator.fingerprint(combined),
                        amount = parsed.amount,
                        isExpense = parsed.isExpense,
                        merchant = parsed.merchant,
                        cardSuffix = NotificationDeduplicator.extractCardSuffix(combined),
                        transactionDate = parsed.date,
                        capturedAt = now
                    )
                )
                if (transactionId == null) {
                    Log.d(TAG, "[$pkg] Duplicate notification transaction, skipping")
                    return@launch
                }
                Log.d(TAG, "Draft created: id=$transactionId")

                // Show heads-up notification to the user
                showConfirmationNotification(transactionId, parsed)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create draft", e)
            }
        }
    }

    private fun recordNonPaymentNotification(pkg: String, text: String, now: Long) {
        val duplicateKey = "$pkg|${text.hashCode()}"
        val lastProcessed = processedNonPaymentNotifications[duplicateKey]
        if (lastProcessed != null && (now - lastProcessed) < 30 * 1000) {
            Log.d(TAG, "[$pkg] Non-payment notification recently recorded, skipping")
            return
        }
        processedNonPaymentNotifications[duplicateKey] = now

        serviceScope.launch {
            try {
                repository.insertNonPaymentNotification(
                    NonPaymentNotification(
                        packageName = pkg,
                        text = text.take(1000),
                        capturedAt = now
                    )
                )
                Log.d(TAG, "[$pkg] Non-payment notification recorded")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to record non-payment notification", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    // ─── Heads-up notification ──────────────────────────────────────

    private val strings: android.content.res.Resources
        get() = com.rton.expensebucket.data.AppLanguage.wrap(this).resources

    private fun showConfirmationNotification(
        transactionId: Long,
        parsed: com.rton.expensebucket.ocr.ParsedTransaction
    ) {
        createNotificationChannel()
        val notifId = transactionId.toInt()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val amountText = currencyFormat.format(parsed.amount)
        val typeText = if (parsed.isExpense) strings.getString(R.string.purchase) else strings.getString(R.string.income)
        val merchantText = if (parsed.merchant.isNotBlank()) strings.getString(R.string.merchant_at, parsed.merchant) else ""

        // Action 1: Quick confirm (via BroadcastReceiver)
        val confirmIntent = Intent(this, NotificationConfirmReceiver::class.java).apply {
            action = NotificationConfirmReceiver.ACTION_CONFIRM
            putExtra(NotificationConfirmReceiver.EXTRA_TRANSACTION_ID, transactionId)
            putExtra(NotificationConfirmReceiver.EXTRA_NOTIFICATION_ID, notifId)
        }
        val confirmPendingIntent = PendingIntent.getBroadcast(
            this, transactionId.toInt(), confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Open this draft directly, including from the HyperOS focus-card button.
        val editIntent = CaptureNotificationContract.editIntent(this, transactionId, notifId)
        val editPendingIntent = PendingIntent.getActivity(
            this, notifId, editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_expense)
            .setContentTitle(strings.getString(R.string.detected_transaction, typeText, amountText))
            .setContentText(strings.getString(R.string.confirm_notification, typeText, merchantText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(editPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_save,
                strings.getString(R.string.notification_confirm),
                confirmPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_edit,
                strings.getString(R.string.notification_edit),
                editPendingIntent
            )
            .build()

        val attachedIsland = HyperOsIslandNotification.attachIfSupported(
            this, notification, editPendingIntent, amountText, typeText, parsed.merchant
        )
        Log.d(TAG, "HyperOS island template attached: $attachedIsland")

        nm.notify(notifId, notification)
        Log.d(TAG, "Heads-up notification shown: id=$notifId")
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                strings.getString(R.string.notification_channel),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = strings.getString(R.string.notification_description)
                enableLights(true)
                enableVibration(true)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun extractNotificationText(extras: Bundle): String {
        val parts = linkedSetOf<String>()
        fun addText(value: String?) {
            val normalized = value
                ?.replace('\u00A0', ' ')
                ?.replace(Regex("""\s+"""), " ")
                ?.trim()
                ?.takeIf { it.isNotBlank() && it != "null" && it.length <= 500 }
                ?: return

            if (parts.none { existing -> existing.contains(normalized) }) {
                parts.removeIf { existing -> normalized.contains(existing) }
                parts.add(normalized)
            }
        }

        val priorityKeys = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_CONVERSATION_TITLE
        )

        priorityKeys.forEach { key -> addText(extras.getCharSequence(key)?.toString()) }

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.forEach { addText(it?.toString()) }

        return parts.joinToString(" ")
    }

    private fun isWatchedPackage(pkg: String): Boolean {
        val normalizedPkg = pkg.lowercase()
        if (WATCHED_PACKAGES.any { watched ->
            val normalizedWatched = watched.lowercase()
            normalizedPkg.startsWith(normalizedWatched) || normalizedPkg.contains(normalizedWatched)
        }) {
            return true
        }

        val packageHints = listOf(
            "fubon",
            "taishin",
            "richart",
            "esun",
            "cathay",
            "ctbc",
            "sinopac",
            "sionpac",
            "dawho",
            "dacard",
            "mpost",
            "jkos",
            "linepay",
            "easycard",
            "pxpay",
            "pchome.android.pi"
        )

        return packageHints.any(normalizedPkg::contains)
    }
}
