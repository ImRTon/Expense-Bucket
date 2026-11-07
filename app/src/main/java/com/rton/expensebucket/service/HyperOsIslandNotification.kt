package com.rton.expensebucket.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import com.rton.expensebucket.R

object HyperOsIslandNotification {
    private const val TAG = "HyperOsIsland"

    fun attachIfSupported(
        context: Context,
        notification: Notification,
        editIntent: PendingIntent,
        amountText: String,
        typeText: String,
        merchant: String
    ): Boolean {
        // Read only the documented capability and permission interfaces. The system
        // makes the final eligibility decision, including region-specific restrictions.
        if (Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0) < 3) return false
        if (!hasFocusPermission(context)) return false

        val actions = Bundle().apply {
            putParcelable(
                HyperOsIslandPayload.OPEN_ACTION,
                Notification.Action.Builder(
                    Icon.createWithResource(context, R.drawable.ic_island_edit), "記帳", editIntent
                ).build()
            )
        }
        val pictures = Bundle().apply {
            putParcelable("miui.focus.pic_expense", Icon.createWithResource(context, R.drawable.ic_notification_expense))
            putParcelable(HyperOsIslandPayload.EDIT_ICON, Icon.createWithResource(context, R.drawable.ic_island_edit))
        }
        notification.extras.putBundle("miui.focus.actions", actions)
        notification.extras.putBundle("miui.focus.pics", pictures)
        notification.extras.putString("miui.focus.param", HyperOsIslandPayload.build(amountText, typeText, merchant))
        return true
    }

    fun hasFocusPermission(context: Context): Boolean = try {
        val arguments = Bundle().apply { putString("package", context.packageName) }
        context.contentResolver.call(
            Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null, arguments
        )?.getBoolean("canShowFocus", false) == true
    } catch (exception: Exception) {
        Log.d(TAG, "Focus notification permission is unavailable: ${exception.javaClass.simpleName}")
        false
    }
}
