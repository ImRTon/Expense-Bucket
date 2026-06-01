package com.rton.expensebucket

import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.addMonths
import com.rton.expensebucket.data.model.amortizedShare
import com.rton.expensebucket.data.model.budgetExpenseForRange
import com.rton.expensebucket.data.model.effectiveAmount
import com.rton.expensebucket.data.model.effectiveConvertedAmount
import com.rton.expensebucket.data.model.millisForYearMonth
import com.rton.expensebucket.data.model.toBudgetTransactionsForRange
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionAmountsTest {

    @Test
    fun usesPersonalAmountWhenPresent() {
        val transaction = Transaction(
            amount = 1200.0,
            personalAmount = 300.0,
            exchangeRate = 1.0
        )

        assertEquals(300.0, transaction.effectiveAmount(), 0.001)
        assertEquals(300.0, transaction.effectiveConvertedAmount(), 0.001)
    }

    @Test
    fun fallsBackToAmountWhenPersonalAmountMissing() {
        val transaction = Transaction(
            amount = 1200.0,
            personalAmount = null,
            exchangeRate = 1.0
        )

        assertEquals(1200.0, transaction.effectiveAmount(), 0.001)
        assertEquals(1200.0, transaction.effectiveConvertedAmount(), 0.001)
    }

    @Test
    fun amortizesAmountAcrossTotalMonthCount() {
        val transaction = Transaction(
            id = 7,
            amount = 3000.0,
            date = millisForYearMonth(202607),
            amortizationEnabled = true,
            amortizationStartYearMonth = 202607,
            amortizationMonthCount = 6
        )

        val julyToDecember = transaction.toBudgetTransactionsForRange(
            startDate = millisForYearMonth(202607),
            endDate = millisForYearMonth(202701) - 1
        )
        val january = transaction.toBudgetTransactionsForRange(
            startDate = millisForYearMonth(202701),
            endDate = millisForYearMonth(202702) - 1
        )

        assertEquals(listOf(202607, 202608, 202609, 202610, 202611, 202612), julyToDecember.map { it.displayYearMonth })
        assertEquals(6, julyToDecember.size)
        julyToDecember.forEach { projection ->
            assertEquals(500.0, projection.displayAmount, 0.001)
        }
        assertEquals(emptyList<Int>(), january.map { it.displayYearMonth })
    }

    @Test
    fun amortizationRemainderKeepsOriginalTotal() {
        val shares = (0 until 3).map { amortizedShare(100.0, 3, it) }

        assertEquals(listOf(33.34, 33.33, 33.33), shares)
        assertEquals(100.0, shares.sum(), 0.001)
    }

    @Test
    fun amortizationCanCrossYearBoundary() {
        val transaction = Transaction(
            amount = 400.0,
            date = millisForYearMonth(202611),
            amortizationEnabled = true,
            amortizationStartYearMonth = 202611,
            amortizationMonthCount = 4
        )

        val projections = transaction.toBudgetTransactionsForRange(
            startDate = millisForYearMonth(202601),
            endDate = millisForYearMonth(202801) - 1
        )

        assertEquals(listOf(202611, 202612, 202701, 202702), projections.map { it.displayYearMonth })
        projections.forEach { projection ->
            assertEquals(100.0, projection.displayAmount, 0.001)
        }
    }

    @Test
    fun budgetExpenseUsesAmortizedAmountOnlyInProjectedMonth() {
        val transaction = Transaction(
            amount = 1200.0,
            date = millisForYearMonth(202607),
            amortizationEnabled = true,
            amortizationStartYearMonth = 202607,
            amortizationMonthCount = 12
        )

        val augustStart = millisForYearMonth(202608)
        val septemberStart = millisForYearMonth(202609)
        val nextJulyStart = millisForYearMonth(addMonths(202607, 12))

        assertEquals(100.0, transaction.budgetExpenseForRange(augustStart, septemberStart - 1), 0.001)
        assertEquals(0.0, transaction.budgetExpenseForRange(nextJulyStart, millisForYearMonth(202708) - 1), 0.001)
    }
}
