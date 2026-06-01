package com.rton.expensebucket.data.model

import java.util.Calendar

data class BudgetTransaction(
    val transaction: Transaction,
    val displayDate: Long,
    val displayYearMonth: Int,
    val displayAmount: Double,
    val displayConvertedAmount: Double,
    val amortizationIndex: Int? = null,
    val amortizationTotalMonths: Int? = null
) {
    val sourceTransactionId: Long = transaction.id
    val isAmortizedProjection: Boolean = amortizationIndex != null && amortizationTotalMonths != null
}

fun Transaction.isValidAmortization(): Boolean =
    isExpense &&
        amortizationEnabled &&
        amortizationStartYearMonth != null &&
        (amortizationMonthCount ?: 0) >= 2

fun Transaction.toBudgetTransactionsForRange(startDate: Long, endDate: Long): List<BudgetTransaction> {
    if (!isValidAmortization()) {
        return if (date in startDate..endDate) {
            listOf(
                BudgetTransaction(
                    transaction = this,
                    displayDate = date,
                    displayYearMonth = yearMonthFromMillis(date),
                    displayAmount = effectiveAmount(),
                    displayConvertedAmount = effectiveConvertedAmount()
                )
            )
        } else {
            emptyList()
        }
    }

    val startYearMonth = amortizationStartYearMonth ?: return emptyList()
    val monthCount = amortizationMonthCount ?: return emptyList()
    val baseAmount = effectiveAmount()
    val convertedAmount = effectiveConvertedAmount()
    val displayTime = timeFieldsFromMillis(date)

    return (0 until monthCount).mapNotNull { monthIndex ->
        val displayYearMonth = addMonths(startYearMonth, monthIndex)
        val displayDate = millisForYearMonth(displayYearMonth, displayTime)
        if (displayDate !in startDate..endDate) {
            null
        } else {
            BudgetTransaction(
                transaction = this,
                displayDate = displayDate,
                displayYearMonth = displayYearMonth,
                displayAmount = amortizedShare(baseAmount, monthCount, monthIndex),
                displayConvertedAmount = amortizedShare(convertedAmount, monthCount, monthIndex),
                amortizationIndex = monthIndex + 1,
                amortizationTotalMonths = monthCount
            )
        }
    }
}

fun Transaction.budgetExpenseForRange(startDate: Long, endDate: Long): Double =
    toBudgetTransactionsForRange(startDate, endDate).sumOf {
        if (it.transaction.isExpense) it.displayConvertedAmount else 0.0
    }

fun yearMonthFromMillis(timeMillis: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = timeMillis }
    return calendar.get(Calendar.YEAR) * 100 + calendar.get(Calendar.MONTH) + 1
}

fun addMonths(yearMonth: Int, offset: Int): Int {
    val year = yearMonth / 100
    val month = yearMonth % 100
    val zeroBasedTotal = year * 12 + (month - 1) + offset
    val targetYear = Math.floorDiv(zeroBasedTotal, 12)
    val targetMonth = Math.floorMod(zeroBasedTotal, 12) + 1
    return targetYear * 100 + targetMonth
}

fun millisForYearMonth(yearMonth: Int, timeFields: TimeFields = TimeFields()): Long {
    val calendar = Calendar.getInstance().apply {
        clear()
        set(Calendar.YEAR, yearMonth / 100)
        set(Calendar.MONTH, yearMonth % 100 - 1)
        val dayOfMonth = timeFields.dayOfMonth.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH))
        set(Calendar.DAY_OF_MONTH, dayOfMonth)
        set(Calendar.HOUR_OF_DAY, timeFields.hour)
        set(Calendar.MINUTE, timeFields.minute)
        set(Calendar.SECOND, timeFields.second)
        set(Calendar.MILLISECOND, timeFields.millisecond)
    }
    return calendar.timeInMillis
}

fun amortizationEndYearMonth(startYearMonth: Int, monthCount: Int): Int =
    addMonths(startYearMonth, monthCount - 1)

fun amortizedShare(total: Double, monthCount: Int, monthIndex: Int): Double {
    if (monthCount <= 0) return 0.0
    val cents = kotlin.math.round(total * 100.0).toLong()
    val base = cents / monthCount
    val remainder = cents % monthCount
    val shareCents = base + if (monthIndex < remainder) 1 else 0
    return shareCents / 100.0
}

data class TimeFields(
    val dayOfMonth: Int = 1,
    val hour: Int = 0,
    val minute: Int = 0,
    val second: Int = 0,
    val millisecond: Int = 0
)

private fun timeFieldsFromMillis(timeMillis: Long): TimeFields {
    val calendar = Calendar.getInstance().apply { this.timeInMillis = timeMillis }
    return TimeFields(
        dayOfMonth = calendar.get(Calendar.DAY_OF_MONTH),
        hour = calendar.get(Calendar.HOUR_OF_DAY),
        minute = calendar.get(Calendar.MINUTE),
        second = calendar.get(Calendar.SECOND),
        millisecond = calendar.get(Calendar.MILLISECOND)
    )
}
