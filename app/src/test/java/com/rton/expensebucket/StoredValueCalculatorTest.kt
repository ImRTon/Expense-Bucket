package com.rton.expensebucket

import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.StoredValueBalance
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.util.StoredValueCalculator
import com.rton.expensebucket.util.storedValueCurrency
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredValueCalculatorTest {
    private val card = PaymentMethod(
        id = 6,
        name = "ICOCA",
        icon = "icoca",
        storedValueBalance = StoredValueBalance(amount = 500.0, asOf = 100, currency = "JPY")
    )
    private val ride = Transaction(
        amount = 60.0,
        personalAmount = 30.0,
        paymentMethodId = card.id,
        date = 101,
        currency = "JPY",
        exchangeRate = 0.21
    )

    @Test
    fun currentBalance_deductsTheFullNativeAmountOfConfirmedSpendingAfterReconciliation() {
        val transactions = listOf(
            ride,
            ride.copy(date = 100),
            ride.copy(date = 99),
            ride.copy(isDraft = true),
            ride.copy(isExpense = false),
            ride.copy(paymentMethodId = 7),
            ride.copy(currency = "TWD")
        )

        assertEquals(440.0, StoredValueCalculator.currentBalance(card, transactions)!!, 0.0)
        assertEquals(460.0, StoredValueCalculator.currentBalance(card, listOf(ride.copy(amount = 40.0)))!!, 0.0)
        assertEquals(500.0, StoredValueCalculator.currentBalance(card, emptyList())!!, 0.0)
    }

    @Test
    fun currentBalance_reconciliationSupersedesOlderSpendingAndSupportsZeroOrNegativeBalances() {
        val reconciledCard = card.copy(storedValueBalance = StoredValueBalance(0.0, 101, "JPY"))

        assertEquals(0.0, StoredValueCalculator.currentBalance(reconciledCard, listOf(ride))!!, 0.0)
        assertEquals(-60.0, StoredValueCalculator.currentBalance(reconciledCard, listOf(ride.copy(date = 102)))!!, 0.0)
        assertNull(StoredValueCalculator.currentBalance(card.copy(storedValueBalance = null), listOf(ride)))
    }

    @Test
    fun currentBalance_deductsAnOlderExpenseReassignedAfterReconciliation() {
        val expense = ride.copy(date = 99, paymentMethodChangedAt = 101)
        val reconciledCard = card.copy(storedValueBalance = StoredValueBalance(440.0, 101, "JPY"))

        assertEquals(440.0, StoredValueCalculator.currentBalance(card, listOf(expense))!!, 0.0)
        assertEquals(500.0, StoredValueCalculator.currentBalance(card, listOf(expense.copy(paymentMethodId = 7)))!!, 0.0)
        assertEquals(440.0, StoredValueCalculator.currentBalance(reconciledCard, listOf(expense))!!, 0.0)
    }

    @Test
    fun storedValueCurrency_usesCardCurrencyAndKeepsItAfterChangingTheIcon() {
        val transitGroup = PaymentMethod(id = 2, name = "電子票證", icon = "transit_card")
        val child = PaymentMethod(id = 3, name = "My transit pass", icon = "other", parentId = transitGroup.id)

        assertEquals("JPY", card.copy(icon = "other").storedValueCurrency(transitGroup))
        assertEquals("JPY", card.copy(storedValueBalance = null).storedValueCurrency(null))
        assertEquals("TWD", child.storedValueCurrency(transitGroup))
        assertNull(transitGroup.storedValueCurrency(null))
        assertNull(child.storedValueCurrency(null))
    }
}
