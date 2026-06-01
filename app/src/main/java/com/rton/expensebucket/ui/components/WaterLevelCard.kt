package com.rton.expensebucket.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.ui.viewmodel.CompareMode
import com.rton.expensebucket.ui.viewmodel.PeriodSummary
import com.rton.expensebucket.ui.viewmodel.TimePeriod
import java.text.NumberFormat
import java.util.Locale

/**
 * MIUI-style water level card with swipeable time periods and compare mode toggle.
 */
@Composable
fun WaterLevelCard(
    periodData: Map<TimePeriod, PeriodSummary>,
    monthlyBudget: Double,
    compareMode: CompareMode,
    appPalette: AppPalette = AppPalette.DEFAULT,
    isDarkTheme: Boolean = false,
    onCompareModeChanged: (CompareMode) -> Unit,
    selectedPage: Int = 2,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val periods = TimePeriod.entries
    val pagerState = rememberPagerState(
        initialPage = selectedPage,
        pageCount = { periods.size }
    )
    val motionState = rememberWaterMotionState()
    val currentPeriod = periods[pagerState.currentPage]

    LaunchedEffect(selectedPage) {
        if (pagerState.currentPage != selectedPage && !pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(selectedPage)
        }
    }

    LaunchedEffect(pagerState, selectedPage) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            if (page != selectedPage) {
                onPageChanged(page)
            }
        }
    }

    val effectiveMode = if (currentPeriod == TimePeriod.ALL) {
        CompareMode.EXPENSE_INCOME
    } else {
        compareMode
    }
    val summary = periodData[currentPeriod] ?: PeriodSummary()
    val currentDisplay = computeDisplayData(effectiveMode, summary, monthlyBudget, currentPeriod)
    val currentPageColor by animateColorAsState(
        targetValue = waterLevelColor(currentDisplay.level, appPalette, isDarkTheme),
        label = "currentWaterLevelColor"
    )
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("zh", "TW")) }

    HorizontalPager(
        state = pagerState,
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp),
        beyondViewportPageCount = 1,
        key = { periods[it].name }
    ) { pageIndex ->
        val pagePeriod = periods[pageIndex]
        val pageMode = if (pagePeriod == TimePeriod.ALL) CompareMode.EXPENSE_INCOME else compareMode
        val pageSummary = periodData[pagePeriod] ?: PeriodSummary()
        val pageDisplay = computeDisplayData(pageMode, pageSummary, monthlyBudget, pagePeriod)
        val pageWaterColor = waterLevelColor(pageDisplay.level, appPalette, isDarkTheme)
        val pageBalance = when (pageMode) {
            CompareMode.EXPENSE_INCOME -> pageSummary.income - pageSummary.expense
            CompareMode.EXPENSE_BUDGET -> {
                val periodBudget = pageDisplay.secondaryValue
                if (periodBudget > 0) periodBudget - pageDisplay.primaryValue else 0.0
            }
        }

        WaterLevelSurface(
            level = pageDisplay.level.toFloat(),
            waterColor = pageWaterColor,
            motionState = motionState,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 12.dp, top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${pagePeriod.label}${if (pageMode == CompareMode.EXPENSE_BUDGET) "預算" else ""}餘額",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.weight(1f)
                    )
                    if (pagePeriod != TimePeriod.ALL) {
                        FilledTonalButton(
                            onClick = {
                                val newMode = if (compareMode == CompareMode.EXPENSE_INCOME) {
                                    CompareMode.EXPENSE_BUDGET
                                } else {
                                    CompareMode.EXPENSE_INCOME
                                }
                                onCompareModeChanged(newMode)
                            },
                            shape = RoundedCornerShape(12.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = "切換模式",
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text(
                                text = pageMode.label,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        }
                    }
                }

                Text(
                    text = if (pageMode == CompareMode.EXPENSE_BUDGET && monthlyBudget <= 0) {
                        "未設定預算"
                    } else {
                        currencyFormat.format(pageBalance)
                    },
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1).sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(start = 24.dp, top = 2.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        val secondaryColor = if (pageMode == CompareMode.EXPENSE_BUDGET) {
                            Color(0xFF60A5FA)
                        } else {
                            Color(0xFF4ADE80)
                        }

                        WaterSummaryItem(
                            label = pageDisplay.secondaryLabel,
                            amount = currencyFormat.format(pageDisplay.secondaryValue),
                            color = secondaryColor
                        )
                        WaterSummaryItem(
                            label = pageDisplay.primaryLabel,
                            amount = currencyFormat.format(pageDisplay.primaryValue),
                            color = Color(0xFFFB7185)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        periods.forEachIndexed { index, _ ->
                            val isSelected = index == pagerState.currentPage
                            val dotColor = if (isSelected) {
                                currentPageColor
                            } else {
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
                            }
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 3.dp)
                                    .size(if (isSelected) 18.dp else 6.dp, 6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(dotColor)
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class DisplayData(
    val level: Double,
    val primaryValue: Double,
    val secondaryValue: Double,
    val primaryLabel: String,
    val secondaryLabel: String
)

private fun computeDisplayData(
    mode: CompareMode,
    summary: PeriodSummary,
    budget: Double,
    period: TimePeriod
): DisplayData {
    return when (mode) {
        CompareMode.EXPENSE_INCOME -> {
            val level = if (summary.income > 0) {
                ((summary.income - summary.expense) / summary.income).coerceIn(0.0, 1.0)
            } else if (summary.expense > 0) {
                0.0
            } else {
                0.5
            }
            DisplayData(
                level = level,
                primaryValue = summary.expense,
                secondaryValue = summary.income,
                primaryLabel = "支出",
                secondaryLabel = "收入"
            )
        }
        CompareMode.EXPENSE_BUDGET -> {
            val expense = summary.budgetExpense
            val periodBudget = when (period) {
                TimePeriod.TODAY -> budget / 30.0
                TimePeriod.WEEK -> budget * 7.0 / 30.0
                TimePeriod.MONTH -> budget
                TimePeriod.YEAR -> budget * 12.0
                TimePeriod.ALL -> budget
            }
            val level = if (periodBudget > 0) {
                ((periodBudget - expense) / periodBudget).coerceIn(0.0, 1.0)
            } else {
                0.5
            }
            DisplayData(
                level = level,
                primaryValue = expense,
                secondaryValue = periodBudget,
                primaryLabel = "支出",
                secondaryLabel = "預算"
            )
        }
    }
}

@Composable
private fun WaterSummaryItem(
    label: String,
    amount: String,
    color: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(color)
        )
        Spacer(modifier = Modifier.size(8.dp))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
