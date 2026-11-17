package com.rton.expensebucket.ui.statistics

import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.toBudgetTransactionsForRange
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class StatsPeriod(val label: String) {
    WEEK("週"),
    MONTH("月"),
    YEAR("年"),
    ALL("全部")
}

enum class StatsType(val label: String) {
    EXPENSE("支出"),
    INCOME("收入")
}

/** Inclusive time window in epoch millis. */
data class StatsWindow(val start: Long, val end: Long) {
    operator fun contains(time: Long): Boolean = time in start..end
}

data class TrendBucket(
    val label: String,
    val start: Long,
    val amount: Double
)

data class CategoryStat(
    val categoryId: Long?,
    val name: String,
    val icon: String,
    val color: Long,
    val amount: Double,
    val count: Int,
    val share: Float,
    val children: List<CategoryStat> = emptyList()
)

data class RankedTransaction(
    val transaction: Transaction,
    val amount: Double,
    val displayDate: Long
)

data class TypeBreakdown(
    val type: StatsType,
    val total: Double,
    /** Total of the previous comparable period, or null when there is none (e.g. ALL). */
    val previousTotal: Double?,
    val count: Int,
    val dailyAverage: Double,
    val trend: List<TrendBucket>,
    val categories: List<CategoryStat>,
    val topTransactions: List<RankedTransaction>
) {
    /** Relative change vs previous period, e.g. 0.12 = +12%. Null when not comparable. */
    val changeRatio: Double?
        get() {
            val previous = previousTotal ?: return null
            if (previous <= 0.0) return null
            return (total - previous) / previous
        }
}

data class StatisticsSummary(
    val period: StatsPeriod,
    val window: StatsWindow,
    val expense: TypeBreakdown,
    val income: TypeBreakdown
) {
    val balance: Double get() = income.total - expense.total

    /** Share of income kept (savings rate), or null when there is no income. */
    val savingsRate: Double?
        get() = if (income.total > 0.0) balance / income.total else null

    fun breakdown(type: StatsType): TypeBreakdown = when (type) {
        StatsType.EXPENSE -> expense
        StatsType.INCOME -> income
    }
}

object StatisticsCalculator {

    private const val UNCATEGORIZED_NAME = "未分類"
    private const val UNCATEGORIZED_ICON = "MoreHoriz"
    private const val UNCATEGORIZED_COLOR = 0xFF6B7280
    private const val TOP_TRANSACTION_COUNT = 5
    private val WEEKDAY_LABELS = mapOf(
        Calendar.SUNDAY to "日",
        Calendar.MONDAY to "一",
        Calendar.TUESDAY to "二",
        Calendar.WEDNESDAY to "三",
        Calendar.THURSDAY to "四",
        Calendar.FRIDAY to "五",
        Calendar.SATURDAY to "六"
    )

    fun window(
        period: StatsPeriod,
        offset: Int,
        now: Long,
        firstDayOfWeek: Int = Calendar.MONDAY,
        earliestTransaction: Long? = null
    ): StatsWindow {
        val cal = calendar(now, firstDayOfWeek)
        cal.startOfDay()
        return when (period) {
            StatsPeriod.WEEK -> {
                cal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                if (cal.timeInMillis > now) cal.add(Calendar.WEEK_OF_YEAR, -1)
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                val start = cal.timeInMillis
                cal.add(Calendar.WEEK_OF_YEAR, 1)
                StatsWindow(start, cal.timeInMillis - 1)
            }
            StatsPeriod.MONTH -> {
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.add(Calendar.MONTH, offset)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                StatsWindow(start, cal.timeInMillis - 1)
            }
            StatsPeriod.YEAR -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.add(Calendar.YEAR, offset)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                StatsWindow(start, cal.timeInMillis - 1)
            }
            StatsPeriod.ALL -> {
                val firstYear = calendar(minOf(earliestTransaction ?: now, now), firstDayOfWeek)
                firstYear.startOfDay()
                firstYear.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.add(Calendar.YEAR, 1)
                StatsWindow(firstYear.timeInMillis, cal.timeInMillis - 1)
            }
        }
    }

    fun periodLabel(
        period: StatsPeriod,
        window: StatsWindow,
        firstDayOfWeek: Int = Calendar.MONDAY,
        now: Long = System.currentTimeMillis(),
        locale: Locale = Locale.TAIWAN
    ): String {
        val start = calendar(window.start, firstDayOfWeek)
        val end = calendar(window.end, firstDayOfWeek)
        return when (period) {
            StatsPeriod.WEEK -> {
                val currentYear = calendar(now, firstDayOfWeek).get(Calendar.YEAR)
                val startYear = start.get(Calendar.YEAR)
                val endYear = end.get(Calendar.YEAR)
                val startText = "${start.get(Calendar.MONTH) + 1}/${start.get(Calendar.DAY_OF_MONTH)}"
                val endText = "${end.get(Calendar.MONTH) + 1}/${end.get(Calendar.DAY_OF_MONTH)}"
                when {
                    startYear != endYear -> "$startYear/$startText – $endYear/$endText"
                    startYear != currentYear -> "$startYear/$startText – $endText"
                    else -> "$startText – $endText"
                }
            }
            StatsPeriod.MONTH -> java.text.SimpleDateFormat(if (locale.language == "en") "yyyy/MM" else "yyyy年M月", locale).format(start.time)
            StatsPeriod.YEAR -> java.text.SimpleDateFormat(if (locale.language == "en") "yyyy" else "yyyy年", locale).format(start.time)
            StatsPeriod.ALL -> {
                val startYear = start.get(Calendar.YEAR)
                val endYear = end.get(Calendar.YEAR)
                if (locale.language == "en") {
                    if (startYear == endYear) "$startYear" else "$startYear – $endYear"
                } else {
                    if (startYear == endYear) "${startYear}年" else "$startYear – ${endYear}年"
                }
            }
        }
    }

    /** Offset that moves [period] to contain [targetTime], relative to [now]. */
    fun offsetFor(period: StatsPeriod, targetTime: Long, now: Long, firstDayOfWeek: Int = Calendar.MONDAY): Int {
        val target = calendar(targetTime, firstDayOfWeek)
        val current = calendar(now, firstDayOfWeek)
        return when (period) {
            StatsPeriod.MONTH ->
                (target.get(Calendar.YEAR) - current.get(Calendar.YEAR)) * 12 +
                    (target.get(Calendar.MONTH) - current.get(Calendar.MONTH))
            StatsPeriod.YEAR -> target.get(Calendar.YEAR) - current.get(Calendar.YEAR)
            StatsPeriod.WEEK -> {
                val targetStart = window(period, 0, targetTime, firstDayOfWeek).start
                val currentStart = window(period, 0, now, firstDayOfWeek).start
                Math.round((targetStart - currentStart) / TimeUnit.DAYS.toMillis(7).toDouble()).toInt()
            }
            StatsPeriod.ALL -> 0
        }
    }

    fun compute(
        transactions: List<Transaction>,
        categories: List<Category>,
        period: StatsPeriod,
        offset: Int,
        now: Long = System.currentTimeMillis(),
        firstDayOfWeek: Int = Calendar.MONDAY
    ): StatisticsSummary {
        val confirmed = transactions.filter { !it.isDraft }
        val earliest = confirmed.minOfOrNull { it.date }
        val window = window(period, offset, now, firstDayOfWeek, earliest)
        val previousWindow = if (period == StatsPeriod.ALL) null
        else window(period, offset - 1, now, firstDayOfWeek)

        val entries = confirmed.flatMap { tx ->
            tx.toBudgetTransactionsForRange(window.start, window.end).map { budget ->
                RankedTransaction(tx, budget.displayConvertedAmount, budget.displayDate)
            }
        }
        val previousTotals = previousWindow?.let { prev ->
            confirmed.flatMap { tx -> tx.toBudgetTransactionsForRange(prev.start, prev.end) }
                .groupBy { it.transaction.isExpense }
                .mapValues { (_, items) -> items.sumOf { it.displayConvertedAmount } }
        }
        val categoryMap = categories.associateBy { it.id }
        val buckets = bucketStarts(period, window, now, firstDayOfWeek)
        val elapsedDays = elapsedDays(window, now)

        fun breakdown(type: StatsType): TypeBreakdown {
            val isExpense = type == StatsType.EXPENSE
            val typed = entries.filter { it.transaction.isExpense == isExpense }
            val total = typed.sumOf { it.amount }
            return TypeBreakdown(
                type = type,
                total = total,
                previousTotal = previousTotals?.get(isExpense) ?: previousTotals?.let { 0.0 },
                count = typed.map { it.transaction.id }.distinct().size,
                dailyAverage = if (elapsedDays > 0) total / elapsedDays else 0.0,
                trend = trend(typed, buckets, window, period, firstDayOfWeek),
                categories = categoryStats(typed, total, categoryMap),
                topTransactions = typed.sortedByDescending { it.amount }.take(TOP_TRANSACTION_COUNT)
            )
        }

        return StatisticsSummary(
            period = period,
            window = window,
            expense = breakdown(StatsType.EXPENSE),
            income = breakdown(StatsType.INCOME)
        )
    }

    private fun categoryStats(
        entries: List<RankedTransaction>,
        total: Double,
        categoryMap: Map<Long, Category>
    ): List<CategoryStat> {
        fun share(amount: Double) = if (total > 0.0) (amount / total).toFloat() else 0f

        return entries
            .groupBy { entry ->
                val category = entry.transaction.categoryId?.let(categoryMap::get)
                category?.parentId?.takeIf { categoryMap.containsKey(it) } ?: category?.id
            }
            .map { (parentId, parentEntries) ->
                val parent = parentId?.let(categoryMap::get)
                val parentAmount = parentEntries.sumOf { it.amount }
                val children = parentEntries
                    .groupBy { it.transaction.categoryId?.takeIf(categoryMap::containsKey) }
                    .map { (childId, childEntries) ->
                        val child = childId?.let(categoryMap::get)
                        val childAmount = childEntries.sumOf { it.amount }
                        CategoryStat(
                            categoryId = childId,
                            name = when {
                                child == null -> UNCATEGORIZED_NAME
                                child.id == parentId -> "${child.name}（未細分）"
                                else -> child.name
                            },
                            icon = child?.icon ?: UNCATEGORIZED_ICON,
                            color = child?.color ?: UNCATEGORIZED_COLOR,
                            amount = childAmount,
                            count = childEntries.map { it.transaction.id }.distinct().size,
                            share = if (parentAmount > 0.0) (childAmount / parentAmount).toFloat() else 0f
                        )
                    }
                    .sortedByDescending { it.amount }
                CategoryStat(
                    categoryId = parentId,
                    name = parent?.name ?: UNCATEGORIZED_NAME,
                    icon = parent?.icon ?: UNCATEGORIZED_ICON,
                    color = parent?.color ?: UNCATEGORIZED_COLOR,
                    amount = parentAmount,
                    count = parentEntries.map { it.transaction.id }.distinct().size,
                    share = share(parentAmount),
                    children = if (children.size > 1) children else emptyList()
                )
            }
            .sortedByDescending { it.amount }
    }

    private fun trend(
        entries: List<RankedTransaction>,
        bucketStarts: List<Long>,
        window: StatsWindow,
        period: StatsPeriod,
        firstDayOfWeek: Int
    ): List<TrendBucket> {
        if (bucketStarts.isEmpty()) return emptyList()
        val sums = DoubleArray(bucketStarts.size)
        entries.forEach { entry ->
            if (entry.displayDate in window) {
                val index = bucketStarts.indexOfLast { it <= entry.displayDate }
                if (index >= 0) sums[index] += entry.amount
            }
        }
        return bucketStarts.mapIndexed { index, start ->
            TrendBucket(bucketLabel(period, start, firstDayOfWeek), start, sums[index])
        }
    }

    private fun bucketStarts(period: StatsPeriod, window: StatsWindow, now: Long, firstDayOfWeek: Int): List<Long> {
        val cal = calendar(window.start, firstDayOfWeek)
        val (field, limit) = when (period) {
            StatsPeriod.WEEK, StatsPeriod.MONTH -> Calendar.DAY_OF_MONTH to window.end
            StatsPeriod.YEAR -> Calendar.MONTH to window.end
            StatsPeriod.ALL -> Calendar.YEAR to minOf(window.end, now)
        }
        val starts = mutableListOf<Long>()
        while (cal.timeInMillis <= limit) {
            starts += cal.timeInMillis
            cal.add(field, 1)
        }
        return starts
    }

    private fun bucketLabel(period: StatsPeriod, start: Long, firstDayOfWeek: Int): String {
        val cal = calendar(start, firstDayOfWeek)
        return when (period) {
            StatsPeriod.WEEK -> WEEKDAY_LABELS.getValue(cal.get(Calendar.DAY_OF_WEEK))
            StatsPeriod.MONTH -> cal.get(Calendar.DAY_OF_MONTH).toString()
            StatsPeriod.YEAR -> "${cal.get(Calendar.MONTH) + 1}月"
            StatsPeriod.ALL -> cal.get(Calendar.YEAR).toString()
        }
    }

    /** Number of days counted for the daily average: elapsed days for the current period, full length otherwise. */
    private fun elapsedDays(window: StatsWindow, now: Long): Long {
        if (now < window.start) return 0
        val effectiveEnd = minOf(window.end, now)
        val startCal = Calendar.getInstance().apply { timeInMillis = window.start; startOfDay() }
        val endCal = Calendar.getInstance().apply { timeInMillis = effectiveEnd; startOfDay() }
        var days = 1L
        while (startCal.before(endCal)) {
            startCal.add(Calendar.DAY_OF_MONTH, 1)
            days++
        }
        return days
    }

    private fun calendar(time: Long, firstDayOfWeek: Int): Calendar =
        Calendar.getInstance().apply {
            this.firstDayOfWeek = firstDayOfWeek
            timeInMillis = time
        }

    private fun Calendar.startOfDay() {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
}
