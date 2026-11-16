package com.rton.expensebucket

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.AppTheme
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.TransactionItem
import com.rton.expensebucket.ui.components.TransactionItemSheet
import com.rton.expensebucket.ui.components.TransactionItemsCard
import com.rton.expensebucket.ui.screens.AddTransactionScreen
import com.rton.expensebucket.ui.screens.TransactionDetailList
import com.rton.expensebucket.ui.theme.ExpensesTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

@OptIn(ExperimentalTestApi::class)
class TransactionItemsUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun quickEntry_movesFromNameToAmountAndSupportsAnotherThenDone() {
        var items by mutableStateOf(emptyList<TransactionItem>())
        var show by mutableStateOf(true)
        compose.setContent {
            ExpensesTheme(dynamicColor = false) {
                if (show) TransactionItemSheet("TWD", null, items.size, { items = items + it }, { show = false })
            }
        }
        compose.onNodeWithText("項目名稱").assertIsFocused().performTextInput("豆腐")
        compose.onNodeWithText("項目名稱").performImeAction()
        compose.onNodeWithText("項目名稱").assertIsNotFocused()
        typeAmount("2", "0")
        compose.onNodeWithText("下一個項目").performScrollTo().performClick()
        screenshot("items-name-entry")
        compose.onNodeWithText("項目名稱").assertIsFocused().performTextInput("豆干")
        compose.onNodeWithText("項目名稱").performImeAction()
        compose.onNodeWithText("項目名稱").assertIsNotFocused()
        typeAmount("2", "0", "+", "5", "=")
        screenshot("items-quick-entry")
        compose.onNodeWithText("完成").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(show)
            assertEquals(listOf(TransactionItem("豆腐", 20.0), TransactionItem("豆干", 25.0)), items)
        }
    }

    @Test
    fun note_usesEnterForNewlineAndKeepsFocus() {
        var note by mutableStateOf("")
        var focused by mutableStateOf(false)
        compose.setContent {
            ExpensesTheme(dynamicColor = false) {
                Surface {
                    TransactionDetailList(
                        items = emptyList(), currency = "TWD", onAddItem = {}, onEditItem = {}, onRemoveItem = {},
                        selectedDateTime = 1_000_000L, selectedPaymentMethod = null, projects = emptyList(),
                        selectedProject = null, isExpense = false, amortizationEnabled = false, amortizationMonthCount = 6,
                        note = note, isNoteFocused = focused, onDateClick = {}, onPaymentMethodClick = {},
                        onProjectClick = {}, onAmortizationClick = {}, onNoteChange = { note = it },
                        onNoteFocusChanged = { focused = it }
                    )
                }
            }
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick().performTextInput("第一行")
        field.assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Default))
        field.performKeyInput { pressKey(Key.Enter) }
        field.performTextInput("第二行")
        field.assertIsFocused()
        compose.runOnIdle { assertEquals("第一行\n第二行", note) }
        screenshot("note-multiline")
    }

    @Test
    fun form_editsAndDeletesItemsWithoutChangingAccountingAmount() {
        var saved: Transaction? = null
        compose.setContent {
            ExpensesTheme(dynamicColor = false) {
                AddTransactionScreen(
                    categories = emptyList(),
                    existingTransaction = Transaction(
                        id = 42, amount = 40.0, note = "晚餐食材\n折扣 5 元",
                        items = listOf(TransactionItem("豆腐", 20.0), TransactionItem("豆干", 25.0))
                    ),
                    onSave = { saved = it }, onBack = {}
                )
            }
        }
        compose.onNodeWithText("豆腐").performScrollTo().performClick()
        compose.onNodeWithText("項目名稱").performTextReplacement("嫩豆腐")
        compose.onNodeWithText("項目名稱").performImeAction()
        compose.onNodeWithContentDescription("刪除").performScrollTo().performClick().performClick()
        typeAmount("2", "2")
        compose.onNodeWithText("儲存變更").performScrollTo().performClick()
        compose.onNodeWithText("嫩豆腐").performScrollTo().assertIsDisplayed()
        screenshot("items-form")
        compose.onNodeWithContentDescription("刪除豆干").performScrollTo().performClick()
        compose.onNodeWithText("更新").performClick()
        compose.runOnIdle {
            assertEquals(40.0, saved!!.amount, 0.0)
            assertEquals(listOf(TransactionItem("嫩豆腐", 22.0)), saved!!.items)
            assertEquals("晚餐食材\n折扣 5 元", saved!!.note)
        }
    }

    @Test
    fun card_rendersInDarkThemeWithLongNameAndZeroAmount() {
        compose.setContent {
            ExpensesTheme(appTheme = AppTheme.DARK, dynamicColor = false) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(16.dp)) {
                        TransactionItemsCard(
                            listOf(TransactionItem("豆腐（超長品項名稱也應完整呈現或適當省略）", 20.0), TransactionItem("豆干", 25.0), TransactionItem("贈品", 0.0)),
                            "TWD", {}, {}, {}
                        )
                    }
                }
            }
        }
        compose.onNodeWithText("TWD 45").assertIsDisplayed()
        screenshot("items-dark")
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        compose.waitForIdle()
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    private fun typeAmount(vararg keys: String) {
        keys.forEach { key ->
            compose.onNode(hasText(key) and hasAnyAncestor(hasTestTag("itemNumpad")), useUnmergedTree = true)
                .performScrollTo().performClick()
        }
    }
}
