package com.rton.expensebucket

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ui.screens.AddTransactionScreen
import com.rton.expensebucket.ui.theme.ExpensesTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class TransactionTypeSwipeUiTest {
    @get:Rule val compose = createComposeRule()

    private var saved: Transaction? = null

    @Test
    fun blankSwipe_followsFingerAndAnimatesToIncomeThenBackToExpense() {
        showForm()
        compose.mainClock.autoAdvance = false
        val originalExpenseX = labelX("支出分類4")
        val firstCategoryX = labelX("支出分類1")
        pager().performTouchInput {
            down(Offset(width - 2f, height / 2f))
            moveBy(Offset(-width * 0.3f, 0f), delayMillis = 100)
        }
        frame()
        assertTrue(labelX("支出分類4") < originalExpenseX - 20f)
        compose.onNodeWithText("收入分類1").assertIsDisplayed()
        screenshot("transaction-type-drag")

        pager().performTouchInput { up() }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        val animatingIncomeX = labelX("收入分類1")
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
        assertTrue(labelX("收入分類1") < animatingIncomeX - 10f)
        assertEquals(firstCategoryX, labelX("收入分類1"), 1f)
        compose.onNodeWithText("支出分類4").assertIsNotDisplayed()
        screenshot("transaction-type-income")

        val originalIncomeX = labelX("收入分類1")
        pager().performTouchInput {
            down(Offset(2f, height / 2f))
            moveBy(Offset(width * 0.3f, 0f), delayMillis = 100)
        }
        frame()
        assertTrue(labelX("收入分類1") > originalIncomeX + 20f)
        pager().performTouchInput { up() }
        finishAnimation()
        compose.onNodeWithText("支出分類4").assertIsDisplayed()
        assertEquals(originalExpenseX, labelX("支出分類4"), 1f)
        compose.onNodeWithText("收入分類1").assertIsNotDisplayed()
    }

    @Test
    fun shortSwipe_movesWithFingerThenReturnsToOriginalPage() {
        showForm()
        compose.mainClock.autoAdvance = false
        val originalX = labelX("支出分類4")
        val shortDistance = with(compose.density) { 40.dp.toPx() }
        pager().performTouchInput {
            down(Offset(width - 2f, height / 2f))
            moveBy(Offset(-shortDistance, 0f), delayMillis = 100)
        }
        frame()
        assertTrue(labelX("支出分類4") < originalX - 5f)
        pager().performTouchInput { up() }
        finishAnimation()
        assertEquals(originalX, labelX("支出分類4"), 1f)
        compose.onNodeWithText("收入分類1").assertIsNotDisplayed()
    }

    @Test
    fun cancelledSwipe_returnsToOriginalPage() {
        showForm()
        compose.mainClock.autoAdvance = false
        val originalX = labelX("支出分類4")
        pager().performTouchInput {
            down(Offset(width - 2f, height / 2f))
            moveBy(Offset(-width * 0.4f, 0f), delayMillis = 100)
        }
        frame()
        pager().performTouchInput { cancel() }
        finishAnimation()
        assertEquals(originalX, labelX("支出分類4"), 1f)
        compose.onNodeWithText("收入分類1").assertIsNotDisplayed()
    }

    @Test
    fun fieldDrag_doesNotSwitchTypeAndTabClickAnimatesWithoutLosingInput() {
        showForm()
        val pagerWidth = pager().fetchSemanticsNode().size.width
        compose.onNodeWithText("支出分類4").performTouchInput {
            down(center)
            moveBy(Offset(-pagerWidth * 0.3f, 0f), delayMillis = 100)
            up()
        }
        compose.onNodeWithText("支出分類4").assertIsDisplayed()
        compose.onNodeWithText("收入分類1").assertIsNotDisplayed()

        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("收入").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        compose.onNodeWithText("收入分類1").assertIsDisplayed()
        finishAnimation()
        compose.onNodeWithText("更新").performClick()
        compose.runOnIdle {
            assertFalse(saved!!.isExpense)
            assertEquals(123.0, saved!!.amount, 0.0)
            assertEquals("動畫測試", saved!!.note)
        }
    }

    @Test
    fun saveDuringSlide_usesTheChosenTypeImmediately() {
        showForm()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("收入").performClick()
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        compose.onNodeWithText("更新").performClick()
        compose.runOnIdle {
            assertFalse(saved!!.isExpense)
            assertEquals(123.0, saved!!.amount, 0.0)
            assertEquals("動畫測試", saved!!.note)
        }
    }

    @Test
    fun verticalSwipe_scrollsFormWithoutChangingType() {
        showForm(expenseCategoryCount = 24)
        pager().performTouchInput {
            swipe(
                start = Offset(width - 2f, height * 0.8f),
                end = Offset(width - 2f, height * 0.2f)
            )
        }
        val scroll = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(scroll.value() > 0f)
        compose.onNodeWithText("收入分類1").assertIsNotDisplayed()
        compose.onNodeWithText("更新").performClick()
        compose.runOnIdle { assertTrue(saved!!.isExpense) }
    }

    private fun showForm(expenseCategoryCount: Int = 4) {
        val categories = (1..expenseCategoryCount).map { category(it, true) } +
            (1..4).map { category(it, false) }
        compose.setContent {
            ExpensesTheme(dynamicColor = false) {
                AddTransactionScreen(
                    categories = categories,
                    existingTransaction = Transaction(id = 1, amount = 123.0, note = "動畫測試"),
                    onSave = { saved = it },
                    onBack = {}
                )
            }
        }
    }

    private fun category(index: Int, expense: Boolean) = Category(
        id = (if (expense) index else index + 100).toLong(),
        name = "${if (expense) "支出" else "收入"}分類$index",
        icon = if (expense) "Restaurant" else "Payments",
        color = if (expense) 0xFFEF5350 else 0xFF26A69A,
        isExpense = expense,
        sortOrder = index
    )

    private fun pager() = compose.onNodeWithTag("transaction_type_pager")

    private fun labelX(text: String) = compose.onNodeWithText(text).fetchSemanticsNode().boundsInRoot.left

    private fun frame() {
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
    }

    private fun finishAnimation() {
        compose.mainClock.advanceTimeBy(400)
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
