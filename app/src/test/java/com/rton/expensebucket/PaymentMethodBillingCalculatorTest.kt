package com.rton.expensebucket

import com.rton.expensebucket.data.model.BillingCycleType
import com.rton.expensebucket.data.model.BillingLimitType
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.util.PaymentMethodBillingCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class PaymentMethodBillingCalculatorTest {

    @Test
    fun returnsNullWhenMethodHasNoBillingCycle() {
        val method = PaymentMethod(id = 1, name = "現金", icon = "cash")

        val summary = PaymentMethodBillingCalculator.buildCurrentSummary(method, emptyList())

        assertNull(summary)
    }

    @Test
    fun sumsOnlyTransactionsInCurrentBillingPeriod() {
        val method = PaymentMethod(
            id = 7,
            name = "測試卡",
            icon = "credit_card",
            type = "credit",
            billingCycleType = BillingCycleType.MONTHLY_CLOSING_DAY.value,
            billingCycleDay = 5,
            billingLimitType = BillingLimitType.CREDIT.value,
            billingLimitAmount = 1000.0
        )
        val now = calendarOf(2026, Calendar.MARCH, 19).timeInMillis

        val transactions = listOf(
            Transaction(id = 1, amount = 100.0, paymentMethodId = 7, date = calendarOf(2026, Calendar.MARCH, 6).timeInMillis),
            Transaction(id = 2, amount = 250.0, paymentMethodId = 7, date = calendarOf(2026, Calendar.MARCH, 12).timeInMillis),
            Transaction(id = 3, amount = 999.0, paymentMethodId = 7, date = calendarOf(2026, Calendar.MARCH, 5).timeInMillis),
            Transaction(id = 4, amount = 50.0, paymentMethodId = 8, date = calendarOf(2026, Calendar.MARCH, 10).timeInMillis)
        )

        val summary = PaymentMethodBillingCalculator.buildCurrentSummary(method, transactions, now)

        assertEquals(350.0, summary?.totalAmount ?: 0.0, 0.001)
        assertEquals("3/6 ~ 4/5", summary?.periodLabel)
    }

    @Test
    fun clampsClosingDayForShortMonths() {
        val method = PaymentMethod(
            id = 9,
            name = "月底卡",
            icon = "credit_card",
            type = "credit",
            billingCycleType = BillingCycleType.MONTHLY_CLOSING_DAY.value,
            billingCycleDay = 31
        )
        val now = calendarOf(2026, Calendar.FEBRUARY, 20).timeInMillis

        val period = PaymentMethodBillingCalculator.currentBillingPeriod(method, now)

        assertEquals("2/1 ~ 2/28", period?.label)
    }

    private fun calendarOf(year: Int, month: Int, day: Int): Calendar =
        Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
}
