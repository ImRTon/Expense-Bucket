package com.rton.expensebucket

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.backup.BackupDeviceState
import com.rton.expensebucket.data.backup.BackupPreview
import com.rton.expensebucket.data.backup.RestoreMode
import com.rton.expensebucket.data.dao.BackupTransactionSummary
import com.rton.expensebucket.ui.screens.BackupBottomSheet
import com.rton.expensebucket.ui.screens.CloudBackupPage
import com.rton.expensebucket.ui.screens.RestoreDecisionContent
import com.rton.expensebucket.ui.theme.ExpensesTheme
import com.rton.expensebucket.ui.viewmodel.BackupWorkState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CloudBackupLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun connectAction_isInTheBottomQuarter() {
        compose.setContent {
            ExpensesTheme {
                CloudBackupPage(
                    device = BackupDeviceState(),
                    work = BackupWorkState.IDLE,
                    busy = false,
                    snackbar = remember { SnackbarHostState() },
                    onBack = {},
                    onAuthorize = {},
                    onEnable = {},
                    onPause = {},
                    onBackupNow = {},
                    onFindBackups = {},
                    onDisconnect = {}
                )
            }
        }

        val viewport = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val connect = compose.onNodeWithText(string(R.string.cloud_backup_connect)).assertIsDisplayed()
            .fetchSemanticsNode().boundsInRoot
        assertTrue(connect.center.y > viewport.top + viewport.height * 0.75f)
    }

    @Test
    fun restoreSheet_displaysCountsAndRequiresAChoiceBeforeConfirmation() {
        var restored: RestoreMode? = null
        var skipped = false
        compose.setContent {
            ExpensesTheme {
                BackupBottomSheet(busy = false, onDismiss = {}) {
                    RestoreDecisionContent(
                        preview = BackupPreview(
                            time = 1_700_000_000_000,
                            remote = BackupTransactionSummary(24, 1_690_000_000_000),
                            local = BackupTransactionSummary(8, 1_695_000_000_000)
                        ),
                        busy = false,
                        notice = null,
                        onSkip = { skipped = true },
                        onRestore = { restored = it }
                    )
                }
            }
        }

        compose.onNodeWithText(string(R.string.cloud_backup_remote_count, 24)).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.cloud_backup_local_count, 8)).assertIsDisplayed()
        val confirm = compose.onNodeWithText(string(R.string.confirm)).assertIsNotEnabled()
        val skip = compose.onNodeWithText(string(R.string.cloud_backup_skip)).assertIsDisplayed()
        val confirmBounds = confirm.fetchSemanticsNode().boundsInRoot
        val skipBounds = skip.fetchSemanticsNode().boundsInRoot
        assertTrue(skipBounds.top >= confirmBounds.bottom)
        compose.onNodeWithText(string(R.string.cloud_backup_overwrite)).performClick()
        confirm.performClick()
        compose.runOnIdle { assertEquals(RestoreMode.REPLACE, restored) }
        skip.performClick()
        compose.runOnIdle { assertTrue(skipped) }
    }

    private fun string(id: Int, vararg arguments: Any): String =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *arguments)
}
