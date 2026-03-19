package com.rton.expensebucket.util

import com.rton.expensebucket.data.model.BillingCycleType
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class BillingPeriod(
    val startMillis: Long,
    val endMillis: Long,
    val label: String
)

data class PaymentMethodBillingSummary(
    val methodId: Long,
    val totalAmount: Double,
    val cycleDescription: String,
    val periodLabel: String
)

object PaymentMethodBillingCalculator {

    fun buildCurrentSummary(
        method: PaymentMethod,
        transactions: List<Transaction>,
        nowMillis: Long = System.currentTimeMillis()
    ): PaymentMethodBillingSummary? {
        val billingPeriod = currentBillingPeriod(method, nowMillis) ?: return null
        val totalAmount = transactions.asSequence()
            .filter { it.paymentMethodId == method.id }
            .filter { it.isExpense }
            .filter { it.date in billingPeriod.startMillis..billingPeriod.endMillis }
            .sumOf { it.amount * it.exchangeRate }

        return PaymentMethodBillingSummary(
            methodId = method.id,
            totalAmount = totalAmount,
            cycleDescription = billingCycleDescription(method),
            periodLabel = billingPeriod.label
        )
    }

    fun currentBillingPeriod(
        method: PaymentMethod,
        nowMillis: Long = System.currentTimeMillis()
    ): BillingPeriod? {
        val cycleType = BillingCycleType.fromValue(method.billingCycleType)
        val closingDay = method.billingCycleDay?.coerceIn(1, 31)
        if (cycleType != BillingCycleType.MONTHLY_CLOSING_DAY || closingDay == null) {
            return null
        }

        val now = Calendar.getInstance().apply {
            timeInMillis = nowMillis
        }
        val currentMonthClosing = closingCalendar(
            year = now.get(Calendar.YEAR),
            month = now.get(Calendar.MONTH),
            closingDay = closingDay
        )

        val periodEnd = if (now.timeInMillis <= currentMonthClosing.timeInMillis) {
            currentMonthClosing
        } else {
            closingCalendar(
                year = now.get(Calendar.YEAR),
                month = now.get(Calendar.MONTH) + 1,
                closingDay = closingDay
            )
        }

        val previousClosing = closingCalendar(
            year = periodEnd.get(Calendar.YEAR),
            month = periodEnd.get(Calendar.MONTH) - 1,
            closingDay = closingDay
        )
        val periodStart = (previousClosing.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        return BillingPeriod(
            startMillis = periodStart.timeInMillis,
            endMillis = periodEnd.timeInMillis,
            label = formatPeriodLabel(periodStart.timeInMillis, periodEnd.timeInMillis)
        )
    }

    fun billingCycleDescription(method: PaymentMethod): String {
        val cycleType = BillingCycleType.fromValue(method.billingCycleType)
        return when (cycleType) {
            BillingCycleType.MONTHLY_CLOSING_DAY ->
                "每月結帳日 ${method.billingCycleDay ?: "-"} 日"
            BillingCycleType.NONE -> "無帳單週期"
        }
    }

    private fun closingCalendar(year: Int, month: Int, closingDay: Int): Calendar {
        return Calendar.getInstance().apply {
            clear()
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, 1)
            val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
            set(Calendar.DAY_OF_MONTH, closingDay.coerceAtMost(maxDay))
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }
    }

    private fun formatPeriodLabel(startMillis: Long, endMillis: Long): String {
        val formatter = SimpleDateFormat("M/d", Locale.TAIWAN)
        return "${formatter.format(startMillis)} ~ ${formatter.format(endMillis)}"
    }
}
