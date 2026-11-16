package com.rton.expensebucket

import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ui.statistics.StatisticsCalculator
import com.rton.expensebucket.ui.statistics.StatsPeriod
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class StatisticsCalculatorTest {

    private val food = Category(id = 1, name = "餐飲", icon = "Restaurant", color = 0xFFF97316)
    private val lunch = Category(id = 2, name = "午餐", icon = "Restaurant", color = 0xFFF97316, parentId = 1)
    private val transport = Category(id = 3, name = "交通", icon = "DirectionsBus", color = 0xFF3B82F6)
    private val salary = Category(id = 10, name = "薪資", icon = "Work", color = 0xFF10B981, isExpense = false)
    private val categories = listOf(food, lunch, transport, salary)

    private val now = millis(2026, Calendar.NOVEMBER, 16, 12)

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 10): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, 0)
        }.timeInMillis

    private fun tx(id: Long, amount: Double, categoryId: Long?, date: Long, isExpense: Boolean = true) =
        Transaction(id = id, amount = amount, categoryId = categoryId, date = date, isExpense = isExpense)

    @Test
    fun compute_separatesExpenseAndIncomeAndBalance() {
        val transactions = listOf(
            tx(1, 300.0, 2, millis(2026, Calendar.NOVEMBER, 3)),
            tx(2, 200.0, 3, millis(2026, Calendar.NOVEMBER, 5)),
            tx(3, 50_000.0, 10, millis(2026, Calendar.NOVEMBER, 5), isExpense = false),
            tx(4, 999.0, 3, millis(2026, Calendar.OCTOBER, 5))
        )

        val summary = StatisticsCalculator.compute(transactions, categories, StatsPeriod.MONTH, 0, now)

        assertEquals(500.0, summary.expense.total, 0.001)
        assertEquals(50_000.0, summary.income.total, 0.001)
        assertEquals(49_500.0, summary.balance, 0.001)
        assertEquals(2, summary.expense.count)
        assertEquals(1, summary.income.count)
    }

    @Test
    fun compute_ignoresDrafts() {
        val transactions = listOf(
            tx(1, 300.0, 3, millis(2026, Calendar.NOVEMBER, 3)),
            tx(2, 700.0, 3, millis(2026, Calendar.NOVEMBER, 3)).copy(isDraft = true)
        )

        val summary = StatisticsCalculator.compute(transactions, categories, StatsPeriod.MONTH, 0, now)

        assertEquals(300.0, summary.expense.total, 0.001)
    }

    @Test
    fun compute_rollsChildCategoriesIntoParent() {
        val transactions = listOf(
            tx(1, 300.0, 2, millis(2026, Calendar.NOVEMBER, 3)),
            tx(2, 100.0, 1, millis(2026, Calendar.NOVEMBER, 4)),
            tx(3, 100.0, 3, millis(2026, Calendar.NOVEMBER, 4))
        )

        val stats = StatisticsCalculator.compute(transactions, categories, StatsPeriod.MONTH, 0, now).expense.categories

        assertEquals(listOf("餐飲", "交通"), stats.map { it.name })
        assertEquals(400.0, stats[0].amount, 0.001)
        assertEquals(0.8f, stats[0].share, 0.001f)
        assertEquals(2, stats[0].children.size)
        assertEquals("午餐", stats[0].children[0].name)
    }

    @Test
    fun compute_comparesWithPreviousMonth() {
        val transactions = listOf(
            tx(1, 150.0, 3, millis(2026, Calendar.NOVEMBER, 3)),
            tx(2, 100.0, 3, millis(2026, Calendar.OCTOBER, 3))
        )

        val expense = StatisticsCalculator.compute(transactions, categories, StatsPeriod.MONTH, 0, now).expense

        assertEquals(100.0, expense.previousTotal!!, 0.001)
        assertEquals(0.5, expense.changeRatio!!, 0.001)
    }

    @Test
    fun compute_allPeriodHasNoComparison() {
        val transactions = listOf(tx(1, 150.0, 3, millis(2024, Calendar.MARCH, 3)))

        val summary = StatisticsCalculator.compute(transactions, categories, StatsPeriod.ALL, 0, now)

        assertNull(summary.expense.previousTotal)
        assertEquals(listOf("2024", "2025", "2026"), summary.expense.trend.map { it.label })
        assertEquals(150.0, summary.expense.trend[0].amount, 0.001)
    }

    @Test
    fun compute_monthTrendHasOneBucketPerDay() {
        val transactions = listOf(
            tx(1, 120.0, 3, millis(2026, Calendar.NOVEMBER, 5)),
            tx(2, 80.0, 3, millis(2026, Calendar.NOVEMBER, 5, 20))
        )

        val trend = StatisticsCalculator.compute(transactions, categories, StatsPeriod.MONTH, 0, now).expense.trend

        assertEquals(30, trend.size)
        assertEquals(200.0, trend[4].amount, 0.001)
    }

    @Test
    fun compute_dailyAverageUsesElapsedDaysForCurrentMonth() {
        val transactions = listOf(tx(1, 1_600.0, 3, millis(2026, Calendar.NOVEMBER, 2)))

        val expense = StatisticsCalculator.compute(transactions, categories, StatsPeriod.MONTH, 0, now).expense

        assertEquals(100.0, expense.dailyAverage, 0.001)
    }

    @Test
    fun compute_spreadsAmortizedExpenseAcrossMonths() {
        val amortized = tx(1, 1_200.0, 3, millis(2026, Calendar.OCTOBER, 1)).copy(
            amortizationEnabled = true,
            amortizationStartYearMonth = 202610,
            amortizationMonthCount = 12
        )

        val expense = StatisticsCalculator.compute(listOf(amortized), categories, StatsPeriod.MONTH, 0, now).expense

        assertEquals(100.0, expense.total, 0.001)
    }

    @Test
    fun yearTrend_labelsMonths() {
        val trend = StatisticsCalculator.compute(emptyList(), categories, StatsPeriod.YEAR, 0, now).income.trend

        assertEquals(12, trend.size)
        assertEquals("1月", trend.first().label)
        assertEquals("12月", trend.last().label)
    }

    @Test
    fun offsetFor_returnsMonthDistance() {
        val target = millis(2025, Calendar.SEPTEMBER, 1)

        assertEquals(-14, StatisticsCalculator.offsetFor(StatsPeriod.MONTH, target, now))
        assertEquals(-1, StatisticsCalculator.offsetFor(StatsPeriod.YEAR, target, now))
    }

    @Test
    fun weekWindow_startsOnConfiguredFirstDay() {
        val window = StatisticsCalculator.window(StatsPeriod.WEEK, 0, now, Calendar.MONDAY)
        val start = Calendar.getInstance().apply { timeInMillis = window.start }

        assertEquals(Calendar.MONDAY, start.get(Calendar.DAY_OF_WEEK))
        assertEquals(16, start.get(Calendar.DAY_OF_MONTH))
        assertEquals("11/16 – 11/22", StatisticsCalculator.periodLabel(StatsPeriod.WEEK, window, Calendar.MONDAY, now))
    }
}
