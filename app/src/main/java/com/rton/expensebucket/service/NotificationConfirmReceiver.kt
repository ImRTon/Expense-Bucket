package com.rton.expanses.service

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.rton.expanses.data.dao.TransactionDao
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handles the "✓ 確認" action button from the heads-up notification.
 * Confirms the draft transaction directly without opening the app.
 */
@AndroidEntryPoint
class NotificationConfirmReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_CONFIRM = "com.rton.expanses.ACTION_CONFIRM_DRAFT"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val TAG = "NotifConfirmRcv"
    }

    @Inject lateinit var transactionDao: TransactionDao

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_CONFIRM) return

        val transactionId = intent.getLongExtra(EXTRA_TRANSACTION_ID, -1L)
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)

        if (transactionId == -1L) {
            Log.w(TAG, "No transaction ID in intent")
            return
        }

        Log.d(TAG, "Confirming draft transaction: $transactionId")

        // Confirm the draft in DB
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                transactionDao.confirmDraft(transactionId)
                Log.d(TAG, "Draft $transactionId confirmed successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to confirm draft $transactionId", e)
            } finally {
                // Dismiss the notification
                if (notificationId != -1) {
                    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.cancel(notificationId)
                }
                pendingResult.finish()
            }
        }
    }
}
