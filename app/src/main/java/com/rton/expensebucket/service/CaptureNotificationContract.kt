package com.rton.expensebucket.service

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.rton.expensebucket.MainActivity
import com.rton.expensebucket.navigation.Screen

object CaptureNotificationContract {
    const val EXTRA_NOTIFICATION_ID = "capture_notification_id"

    fun editIntent(context: Context, transactionId: Long, notificationId: Int): Intent =
        Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // PendingIntent identity includes data, so separate drafts never share extras.
            data = Uri.parse("expensebucket://notification/transaction/$transactionId")
            putExtra("navigate_to", Screen.EditTransaction.createRoute(transactionId))
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

    fun transactionId(route: String?): Long? = route
        ?.takeIf { it.matches(Regex("edit_transaction/[1-9]\\d*")) }
        ?.substringAfterLast('/')?.toLongOrNull()
}
