package com.rton.expensebucket

import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ui.screens.AddTransactionFormDraft
import com.rton.expensebucket.ui.screens.AddTransactionFormLogic
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AddTransactionFormStateTest {

    @Test
    fun appendAmountKey_replacesTrailingOperatorAndHandlesDoubleZero() {
        var text = ""
        text = AddTransactionFormLogic.appendAmountKey(text, "+")
        assertEquals("", text)

        text = AddTransactionFormLogic.appendAmountKey(text, "00")
        assertEquals("0", text)

        text = AddTransactionFormLogic.appendAmountKey(text, "5")
        assertEquals("5", text)

        text = AddTransactionFormLogic.appendAmountKey(text, "00")
        assertEquals("500", text)

        text = AddTransactionFormLogic.appendAmountKey(text, "+")
        assertEquals("500+", text)

        text = AddTransactionFormLogic.appendAmountKey(text, "×")
        assertEquals("500×", text)

        text = AddTransactionFormLogic.appendAmountKey(text, "2")
        assertEquals("500×2", text)
    }

    @Test
    fun evaluateExpressionToText_computesArithmeticAndStripsTrailingOperator() {
        assertEquals("1250", AddTransactionFormLogic.evaluateExpressionToText("120×10+50"))
        assertEquals("120", AddTransactionFormLogic.evaluateExpressionToText("120+"))
    }

    @Test
    fun validatePersonalAmount_rejectsExceedingTotalOrNonPositive() {
        assertNull(AddTransactionFormLogic.validatePersonalAmount("", isExpense = true, resolvedAmount = 1000.0))
        assertNull(AddTransactionFormLogic.validatePersonalAmount("400", isExpense = true, resolvedAmount = 1000.0))
        assertEquals(
            "我的花費不能大於支出總額",
            AddTransactionFormLogic.validatePersonalAmount("1200", isExpense = true, resolvedAmount = 1000.0)
        )
        assertEquals(
            "我的花費需大於 0",
            AddTransactionFormLogic.validatePersonalAmount("0", isExpense = true, resolvedAmount = 1000.0)
        )
        assertNull(
            AddTransactionFormLogic.validatePersonalAmount("1200", isExpense = false, resolvedAmount = 1000.0)
        )
    }

    @Test
    fun buildTransaction_and_resetForSaveAndAddAnother_preservesDatePaymentProject() {
        val draft = AddTransactionFormDraft(
            amountText = "120×10+50",
            personalAmountText = "400",
            selectedCategoryId = 3L,
            isExpense = true,
            note = "聚餐",
            selectedDateTime = 1700000000000L,
            selectedPaymentMethodId = 7L,
            selectedProjectId = 2L,
            selectedCurrency = "JPY",
            exchangeRateText = "0.215",
            amortizationEnabled = true,
            amortizationStartYearMonth = 202611,
            amortizationMonthCount = 3,
            showNumpad = false
        )

        val tx: Transaction? = AddTransactionFormLogic.buildTransaction(
            existingTransaction = null,
            prefillSource = null,
            draft = draft,
            settlementCurrency = "TWD",
            nowMillis = 1700000001000L
        )
        assertNotNull(tx)
        assertEquals(1250.0, tx!!.amount, 0.001)
        assertEquals(400.0, tx.personalAmount!!, 0.001)
        assertEquals(3L, tx.categoryId)
        assertEquals(7L, tx.paymentMethodId)
        assertEquals(2L, tx.projectId)
        assertEquals("JPY", tx.currency)
        assertEquals(0.215, tx.exchangeRate, 0.0001)
        assertTrue(tx.amortizationEnabled)

        val reset = AddTransactionFormLogic.resetForSaveAndAddAnother(draft)
        assertEquals("", reset.amountText)
        assertEquals("", reset.personalAmountText)
        assertNull(reset.selectedCategoryId)
        assertEquals("", reset.note)
        assertFalse(reset.amortizationEnabled)
        assertTrue(reset.showNumpad)
        // Preserved fields:
        assertEquals(1700000000000L, reset.selectedDateTime)
        assertEquals(7L, reset.selectedPaymentMethodId)
        assertEquals(2L, reset.selectedProjectId)
        assertEquals("JPY", reset.selectedCurrency)
        assertEquals("0.215", reset.exchangeRateText)
    }
}
