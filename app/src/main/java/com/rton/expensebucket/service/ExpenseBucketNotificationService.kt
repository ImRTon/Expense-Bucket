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
import com.rton.expensebucket.MainActivity
import com.rton.expensebucket.R
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import com.rton.expensebucket.ocr.NotificationParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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
        private const val CHANNEL_NAME = "記帳通知"

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
            "com.esunbank",             // 玉山銀行
            "com.cathaybk.mymobibank",  // 國泰世華
            "com.ctbcbank.ctbc",        // 中國信託
            "com.fubon.mobilebank",     // 富邦銀行
            "com.fubon.aibank",         // 富邦銀行 (aibank)
            "com.sinopac.DaCard",       // 永豐大咖
            "wbank.ubot.com.tw",        // 聯邦銀行
            "tw.gov.post.mpost",        // 郵局

            // Google Pay / Apple Pay (system-level)
            "com.google.android.apps.nbu.paisa.user",  // Google Pay
        )
    }

    @Inject lateinit var repository: ExpenseBucketRepository

    private val parser = NotificationParser()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("zh", "TW"))
    private var notificationIdCounter = 10000

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

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val combined = extractNotificationText(extras)

        if (combined.isBlank()) {
            Log.d(TAG, "[$pkg] Empty notification text, skipping")
            return
        }

        Log.d(TAG, "[$pkg] Intercepted: $combined")

        // Step 2: Parse with NotificationParser
        val parsed = parser.parse(combined, pkg)
        if (parsed == null) {
            Log.d(TAG, "[$pkg] Parser returned null — no pattern matched")
            return
        }

        Log.d(TAG, "[$pkg] Parsed: amount=${parsed.amount}, merchant='${parsed.merchant}', isExpense=${parsed.isExpense}")

        // Step 3: Insert as draft and show heads-up notification
        serviceScope.launch {
            try {
                val transactionId = repository.insertTransaction(
                    Transaction(
                        amount = parsed.amount,
                        note = parsed.merchant.ifBlank { parsed.note },
                        isExpense = parsed.isExpense,
                        source = "notification",
                        isDraft = true,
                        date = parsed.date ?: System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Draft created: id=$transactionId, amount=${parsed.amount}")

                // Show heads-up notification to the user
                showConfirmationNotification(transactionId, parsed)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create draft", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    // ─── Heads-up notification ──────────────────────────────────────

    private fun showConfirmationNotification(
        transactionId: Long,
        parsed: com.rton.expensebucket.ocr.ParsedTransaction
    ) {
        val notifId = notificationIdCounter++
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        val amountText = currencyFormat.format(parsed.amount)
        val typeText = if (parsed.isExpense) "消費" else "收入"
        val merchantText = if (parsed.merchant.isNotBlank()) " 於 ${parsed.merchant}" else ""

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

        // Action 2: Edit (open app → DraftsScreen)
        val editIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("navigate_to", "drafts")
        }
        val editPendingIntent = PendingIntent.getActivity(
            this, transactionId.toInt() + 50000, editIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("偵測到${typeText} $amountText")
            .setContentText("${typeText}${merchantText}，點擊確認記帳")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(editPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_save,
                "✓ 確認記帳",
                confirmPendingIntent
            )
            .addAction(
                android.R.drawable.ic_menu_edit,
                "📝 編輯",
                editPendingIntent
            )
            .build()

        nm.notify(notifId, notification)
        Log.d(TAG, "Heads-up notification shown: id=$notifId")
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "攔截支付通知並提醒確認記帳"
                enableLights(true)
                enableVibration(true)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun extractNotificationText(extras: Bundle): String {
        val textKeys = listOf(
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_CONVERSATION_TITLE
        )

        val parts = linkedSetOf<String>()

        textKeys.forEach { key ->
            extras.getCharSequence(key)
                ?.toString()
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?.let(parts::add)
        }

        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            ?.mapNotNull { it?.toString()?.trim()?.takeIf(String::isNotBlank) }
            ?.forEach(parts::add)

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
