package com.rton.expensebucket

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.service.CaptureNotificationContract
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CaptureNotificationUiTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test
    fun opensExactDraftOnColdAndWarmStartAndDismissesOnlyItsReminder(): Unit = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val database = Room.databaseBuilder(context, ExpenseBucketDatabase::class.java, "expenses_database")
            .addMigrations(ExpenseBucketDatabase.MIGRATION_11_12, ExpenseBucketDatabase.MIGRATION_12_13, ExpenseBucketDatabase.MIGRATION_13_14).build()
        val dao = database.transactionDao()
        val first = dao.insertTransaction(Transaction(amount = 1234.5, note = "島通知測試A", source = "notification", isDraft = true))
        val second = dao.insertTransaction(Transaction(amount = 50.0, note = "島通知測試B", source = "notification", isDraft = true))
        val manager = context.getSystemService(NotificationManager::class.java)
        val channel = "notification_navigation_test"
        val firstNotificationId = 98761
        val secondNotificationId = 98762
        manager.createNotificationChannel(NotificationChannel(channel, "Navigation test", NotificationManager.IMPORTANCE_LOW))
        val firstIntent = CaptureNotificationContract.editIntent(context, first, firstNotificationId)
        val secondIntent = CaptureNotificationContract.editIntent(context, second, secondNotificationId)
        val firstPending = PendingIntent.getActivity(context, 0, firstIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val secondPending = PendingIntent.getActivity(context, 0, secondIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        var activity: MainActivity? = null
        try {
            assertNotEquals(firstPending, secondPending)
            manager.notify(firstNotificationId, Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification_expense).setContentTitle("Test A").setContentIntent(firstPending).build())
            manager.notify(secondNotificationId, Notification.Builder(context, channel)
                .setSmallIcon(R.drawable.ic_notification_expense).setContentTitle("Test B").setContentIntent(secondPending).build())
            activity = instrumentation.startActivitySync(firstIntent) as MainActivity
            compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("島通知測試A")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("編輯記帳").assertIsDisplayed()
            assertFalse(manager.activeNotifications.any { it.id == firstNotificationId })
            assertTrue(manager.activeNotifications.any { it.id == secondNotificationId })
            secondPending.send()
            compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("島通知測試B")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("編輯記帳").assertIsDisplayed()
            assertFalse(manager.activeNotifications.any { it.id == secondNotificationId })

            dao.deleteTransactionById(first)
            firstPending.send()
            compose.waitUntil(15_000) { compose.onAllNodes(androidx.compose.ui.test.hasText("待確認記帳")).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("待確認記帳").assertIsDisplayed()
        } finally {
            // The activity's intent data changes on warm deep links. Avoid a scenario
            // monitor tied to the initial intent when finishing this singleTask activity.
            instrumentation.runOnMainSync { activity?.finish() }
            firstPending.cancel()
            secondPending.cancel()
            manager.cancel(firstNotificationId)
            manager.cancel(secondNotificationId)
            manager.deleteNotificationChannel(channel)
            dao.deleteTransactionById(first)
            dao.deleteTransactionById(second)
            database.close()
        }
    }
}
