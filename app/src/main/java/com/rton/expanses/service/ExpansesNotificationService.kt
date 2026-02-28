package com.rton.expanses.service

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.rton.expanses.data.model.Transaction
import com.rton.expanses.data.repository.ExpansesRepository
import com.rton.expanses.ocr.NotificationParser
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Intercepts notifications from banking / payment apps and
 * creates draft transactions automatically.
 *
 * User must grant notification access in Settings → Notification access.
 */
@AndroidEntryPoint
class ExpansesNotificationService : NotificationListenerService() {

    companion object {
        private const val TAG = "ExpansesNotifSvc"

        /**
         * Package name prefixes of apps we want to intercept.
         * Add more as needed.
         */
        val WATCHED_PACKAGES = setOf(
            // E-payments
            "com.jkos.jkopay",         // 街口支付
            "com.linecorp.linepay",     // LINE Pay
            "com.linecorp.line",        // LINE (contains LINE Pay notifs)
            "tw.com.easycard.easygo",   // 悠遊付
            "com.pi.mobile",            // Pi 拍錢包
            "com.allpay",               // 全支付

            // Banks
            "com.taishin.mobile",       // 台新 Richart
            "com.esunbank",             // 玉山銀行
            "com.cathaybk.mymobibank",  // 國泰世華
            "com.ctbcbank.ctbc",        // 中國信託
            "com.fubon.mobilebank",     // 富邦銀行

            // Google Pay / Apple Pay (system-level)
            "com.google.android.apps.nbu.paisa.user",  // Google Pay
        )
    }

    @Inject lateinit var repository: ExpansesRepository

    private val parser = NotificationParser()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return

        val pkg = sbn.packageName ?: return
        if (!isWatchedPackage(pkg)) return

        val notification = sbn.notification ?: return
        val extras = notification.extras ?: return

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""

        // Combine all text for parsing
        val combined = listOf(title, text, bigText)
            .filter { it.isNotBlank() }
            .joinToString(" ")

        if (combined.isBlank()) return

        Log.d(TAG, "Notification from $pkg: $combined")

        val parsed = parser.parse(combined, pkg) ?: return

        Log.d(TAG, "Parsed: amount=${parsed.amount}, merchant=${parsed.merchant}")

        // Create draft transaction
        serviceScope.launch {
            try {
                repository.insertTransaction(
                    Transaction(
                        amount = parsed.amount,
                        note = parsed.merchant.ifBlank { parsed.note },
                        isExpense = parsed.isExpense,
                        source = "notification",
                        isDraft = true,
                        date = System.currentTimeMillis(),
                        createdAt = System.currentTimeMillis()
                    )
                )
                Log.d(TAG, "Draft transaction created: ${parsed.amount}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create draft", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // no-op
    }

    private fun isWatchedPackage(pkg: String): Boolean {
        return WATCHED_PACKAGES.any { pkg.startsWith(it) || pkg.contains(it) }
    }
}
