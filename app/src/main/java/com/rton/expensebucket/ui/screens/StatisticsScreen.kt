package com.rton.expensebucket.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.effectiveConvertedAmount
import com.rton.expensebucket.ui.util.IconMapper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.*

enum class TimeRange(val label: String) {
    TODAY("本日"),
    THIS_WEEK("本週"),
    THIS_MONTH("本月"),
    THIS_YEAR("今年"),
    ALL_TIME("所有")
}

private enum class StatisticsChartType(val label: String) {
    PIE("圓餅圖"),
    BAR("長條圖"),
    DONUT("甜甜圈")
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun StatisticsScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    onBack: () -> Unit = {}
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("zh", "TW")) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val chartTypes = remember { StatisticsChartType.entries }
    val pagerState = rememberPagerState(initialPage = 0) { chartTypes.size }
    val coroutineScope = rememberCoroutineScope()

    var selectedRange by remember { mutableStateOf(TimeRange.THIS_MONTH) }

    // Filter transactions based on selected range
    val filteredTransactions = remember(transactions, selectedRange) {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance().apply { timeInMillis = now }

        val (startTime, endTime) = when (selectedRange) {
            TimeRange.TODAY -> {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.DAY_OF_MONTH, 1)
                start to calendar.timeInMillis - 1
            }
            TimeRange.THIS_WEEK -> {
                calendar.set(Calendar.DAY_OF_WEEK, calendar.firstDayOfWeek)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.WEEK_OF_YEAR, 1)
                start to calendar.timeInMillis - 1
            }
            TimeRange.THIS_MONTH -> {
                calendar.set(Calendar.DAY_OF_MONTH, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.MONTH, 1)
                start to calendar.timeInMillis - 1
            }
            TimeRange.THIS_YEAR -> {
                calendar.set(Calendar.DAY_OF_YEAR, 1)
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
                calendar.set(Calendar.MILLISECOND, 0)
                val start = calendar.timeInMillis
                calendar.add(Calendar.YEAR, 1)
                start to calendar.timeInMillis - 1
            }
            TimeRange.ALL_TIME -> 0L to Long.MAX_VALUE
        }

        transactions.filter { it.date in startTime..endTime }
    }

    // Calculate category totals for expenses
    val categoryTotals = remember(filteredTransactions) {
        filteredTransactions
            .filter { it.isExpense && !it.isDraft }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.effectiveConvertedAmount() } }
            .entries
            .sortedByDescending { it.value }
    }
    val totalExpense = categoryTotals.sumOf { it.value }
    val chartData = remember(categoryTotals, categoryMap) {
        categoryTotals.map { (categoryId, amount) ->
            val category = categoryMap[categoryId]
            ChartSlice(
                label = category?.name ?: "未分類",
                value = amount.toFloat(),
                color = Color(category?.color ?: 0xFF6B7280)
            )
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "支出統計",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ─── Filter Chips ───────────────────────────────────
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TimeRange.values().forEach { range ->
                        FilterChip(
                            selected = selectedRange == range,
                            onClick = { selectedRange = range },
                            label = { Text(range.label) },
                            leadingIcon = if (selectedRange == range) {
                                {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Selected",
                                        modifier = Modifier.size(FilterChipDefaults.IconSize)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            // ─── Donut Chart ────────────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "${selectedRange.label}支出",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currencyFormat.format(totalExpense),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        // Donut chart
                        if (chartData.isNotEmpty()) {
                            Text(
                                "左右滑動切換圖表",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))

                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(260.dp),
                                beyondViewportPageCount = 1,
                                key = { chartTypes[it].name }
                            ) { page ->
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    when (chartTypes[page]) {
                                        StatisticsChartType.PIE -> PieChartPage(
                                            data = chartData,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        StatisticsChartType.BAR -> BarChart(
                                            data = chartData,
                                            currencyFormat = currencyFormat,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        StatisticsChartType.DONUT -> DonutChart(
                                            data = chartData,
                                            modifier = Modifier.size(210.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            PagerIndicator(
                                labels = chartTypes.map { it.label },
                                selectedIndex = pagerState.currentPage,
                                onSelected = { index ->
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            )
                        } else {
                            Text(
                                "這個時間範圍還沒有可顯示的支出資料",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            // ─── Category Breakdown List ────────────────────────
            item {
                Text(
                    "分類明細",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(categoryTotals) { (categoryId, amount) ->
                val category = categoryMap[categoryId]
                val percentage = if (totalExpense > 0) (amount / totalExpense * 100) else 0.0

                CategoryStatItem(
                    category = category,
                    amount = currencyFormat.format(amount),
                    percentage = percentage,
                    total = totalExpense
                )
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CategoryStatItem(
    category: Category?,
    amount: String,
    percentage: Double,
    total: Double
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (percentage / 100).toFloat(),
        animationSpec = tween(800),
        label = "progress"
    )
    val color = Color(category?.color ?: 0xFF6B7280)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .then(
                        Modifier.padding(0.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    IconMapper.getIcon(category?.icon ?: "MoreHoriz"),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        category?.name ?: "未分類",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Text(
                        amount,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.12f),
                    strokeCap = StrokeCap.Round,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "%.1f%%".format(percentage),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private data class ChartSlice(
    val label: String,
    val value: Float,
    val color: Color
)

@Composable
private fun DonutChart(
    data: List<ChartSlice>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000),
        label = "donutAnim"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 36.dp.toPx()
        val radius = (size.minDimension - strokeWidth) / 2
        val topLeft = Offset(
            (size.width - radius * 2) / 2,
            (size.height - radius * 2) / 2
        )

        var startAngle = -90f
        data.forEach { slice ->
            val sweepAngle = (slice.value / total) * 360f * animationProgress
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = false,
                topLeft = topLeft,
                size = Size(radius * 2, radius * 2),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun PieChartPage(
    data: List<ChartSlice>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PieChart(
            data = data,
            modifier = Modifier.size(180.dp)
        )
        Spacer(modifier = Modifier.height(12.dp))
        CategoryLegend(
            data = data,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PieChart(
    data: List<ChartSlice>,
    modifier: Modifier = Modifier
) {
    val total = data.sumOf { it.value.toDouble() }.toFloat()
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900),
        label = "pieAnim"
    )

    Canvas(modifier = modifier) {
        var startAngle = -90f
        data.forEach { slice ->
            val sweepAngle = (slice.value / total) * 360f * animationProgress
            drawArc(
                color = slice.color,
                startAngle = startAngle,
                sweepAngle = sweepAngle,
                useCenter = true
            )
            startAngle += sweepAngle
        }
    }
}

@Composable
private fun CategoryLegend(
    data: List<ChartSlice>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        data.forEach { slice ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(slice.color)
                )
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun BarChart(
    data: List<ChartSlice>,
    currencyFormat: NumberFormat,
    modifier: Modifier = Modifier
) {
    val topData = remember(data) { data.take(6) }
    val maxValue = remember(topData) { topData.maxOfOrNull { it.value } ?: 0f }
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(900),
        label = "barAnim"
    )

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        topData.forEach { slice ->
            val fraction = if (maxValue > 0f) slice.value / maxValue else 0f

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    formatCompactCurrency(slice.value.toDouble(), currencyFormat),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.72f)
                            .fillMaxHeight(fraction * animationProgress)
                            .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                            .background(slice.color)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = slice.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.height(32.dp)
                )
            }
        }
    }
}

@Composable
private fun PagerIndicator(
    labels: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selectedIndex
            Surface(
                modifier = Modifier.clickable { onSelected(index) },
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private fun formatCompactCurrency(
    amount: Double,
    currencyFormat: NumberFormat
): String {
    return when {
        amount >= 1_000_000 -> String.format(Locale.US, "%.1fM", amount / 1_000_000)
        amount >= 10_000 -> String.format(Locale.US, "%.1f萬", amount / 10_000)
        amount >= 1_000 -> String.format(Locale.US, "%.1fK", amount / 1_000)
        else -> currencyFormat.format(amount)
    }
}
