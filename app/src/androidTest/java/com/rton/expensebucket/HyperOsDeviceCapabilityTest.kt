package com.rton.expensebucket

import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/** Read-only device diagnostics; it does not post notifications or open the database. */
@RunWith(AndroidJUnit4::class)
class HyperOsDeviceCapabilityTest {
    @Test
    fun reportsActualFocusNotificationPermission() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val protocol = Settings.System.getInt(context.contentResolver, "notification_focus_protocol", 0)
        val result = try {
            val arguments = Bundle().apply { putString("package", context.packageName) }
            context.contentResolver.call(
                Uri.parse("content://miui.statusbar.notification.public"), "canShowFocus", null, arguments
            )?.getBoolean("canShowFocus", false).toString()
        } catch (exception: Exception) {
            "unavailable:${exception.javaClass.simpleName}"
        }
        instrumentation.sendStatus(0, Bundle().apply {
            putString("focus_protocol", protocol.toString())
            putString("can_show_focus", result)
        })
    }
}
