package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ui.components.YearMonthPickerDialog
import com.rton.expensebucket.ui.components.YearPickerDialog
import com.rton.expensebucket.ui.components.StatisticsPeriodSelector
import com.rton.expensebucket.ui.statistics.CategoryStat
import com.rton.expensebucket.ui.statistics.RankedTransaction
import com.rton.expensebucket.ui.statistics.StatisticsCalculator
import com.rton.expensebucket.ui.statistics.StatisticsSummary
import com.rton.expensebucket.ui.statistics.StatsPeriod
import com.rton.expensebucket.ui.statistics.StatsType
import com.rton.expensebucket.ui.statistics.TrendBucket
import com.rton.expensebucket.ui.statistics.TypeBreakdown
import com.rton.expensebucket.ui.theme.amountDisplay
import com.rton.expensebucket.ui.theme.amountMedium
import com.rton.expensebucket.ui.theme.amountSmall
import com.rton.expensebucket.ui.theme.semanticColors
import com.rton.expensebucket.ui.util.IconMapper
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.min

private val CardShape = RoundedCornerShape(24.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    firstDayOfWeek: Int = Calendar.MONDAY,
    onTransactionClick: (Transaction) -> Unit = {}
) {
    val strings = LocalContext.current.resources

    val types = remember { StatsType.entries }
    val pagerState = rememberPagerState(initialPage = 0) { types.size }
    val coroutineScope = rememberCoroutineScope()

    var period by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var offset by rememberSaveable { mutableIntStateOf(0) }
    var showPicker by remember { mutableStateOf(false) }

    val now = remember(transactions, period, offset) { System.currentTimeMillis() }
    val summary = remember(transactions, categories, period, offset, firstDayOfWeek, now) {
        StatisticsCalculator.compute(transactions, categories, period, offset, now, firstDayOfWeek)
    }
    val periodLabel = remember(summary, firstDayOfWeek, strings.configuration.locales[0]) {
        StatisticsCalculator.periodLabel(period, summary.window, firstDayOfWeek, now, strings.configuration.locales[0])
    }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        strings.getString(R.string.statistics),
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    PeriodNavigator(
                        period = period,
                        periodLabel = periodLabel,
                        isCurrent = offset == 0,
                        onStep = { offset += it },
                        onLabelClick = { if (period != StatsPeriod.ALL) showPicker = true },
                        onResetToCurrent = { offset = 0 }
                    )
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatsTypeSwitch(
                    position = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f),
                    expenseTotal = summary.expense.total,
                    incomeTotal = summary.income.total,
                    onSelect = { type ->
                        coroutineScope.launch { pagerState.animateScrollToPage(types.indexOf(type)) }
                    }
                )
                StatisticsPeriodSelector(
                    period = period,
                    onPeriodChange = {
                        period = it
                        offset = 0
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                key = { types[it].name }
            ) { page ->
                StatisticsTypePage(
                    summary = summary,
                    type = types[page],
                    now = now,
                    categoryMap = categoryMap,
                    onTransactionClick = onTransactionClick
                )
            }
        }
    }

    if (showPicker) {
        val onPicked: (Long) -> Unit = { picked ->
            offset = StatisticsCalculator.offsetFor(period, picked, System.currentTimeMillis(), firstDayOfWeek)
            showPicker = false
        }
        when (period) {
            StatsPeriod.MONTH -> YearMonthPickerDialog(
                initialTimeMillis = summary.window.start,
                onDateSelected = onPicked,
                onDismiss = { showPicker = false }
            )
            StatsPeriod.YEAR -> YearPickerDialog(
                initialTimeMillis = summary.window.start,
                onDateSelected = onPicked,
                onDismiss = { showPicker = false }
            )
            StatsPeriod.WEEK -> WeekPickerDialog(
                initialTimeMillis = summary.window.start,
                onDateSelected = onPicked,
                onDismiss = { showPicker = false }
            )
            StatsPeriod.ALL -> Unit
        }
    }
}

// ─── Header controls ─────────────────────────────────────────────

@Composable
private fun StatsTypeSwitch(
    position: Float,
    expenseTotal: Double,
    incomeTotal: Double,
    onSelect: (StatsType) -> Unit
) {
    val strings = LocalContext.current.resources

    val semantic = MaterialTheme.semanticColors
    val indicatorColor = lerp(semantic.expense, semantic.income, position)

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.padding(4.dp)) {
            val segmentWidth = maxWidth / 2
            Box(
                modifier = Modifier
                    .offset(x = segmentWidth * position)
                    .width(segmentWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            Row(modifier = Modifier.fillMaxSize()) {
                StatsType.entries.forEachIndexed { index, type ->
                    val selectedness = 1f - abs(position - index).coerceIn(0f, 1f)
                    val contentColor = lerp(
                        MaterialTheme.colorScheme.onSurfaceVariant,
                        Color.White,
                        selectedness
                    )
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable { onSelect(type) },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (type == StatsType.EXPENSE) {
                                Icons.AutoMirrored.Filled.TrendingDown
                            } else {
                                Icons.AutoMirrored.Filled.TrendingUp
                            },
                            contentDescription = null,
                            tint = contentColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                strings.localizedLabel(type.label),
                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                color = contentColor
                            )
                            Text(
                                formatCompact(strings, if (type == StatsType.EXPENSE) expenseTotal else incomeTotal),
                                style = MaterialTheme.typography.labelSmall,
                                color = contentColor.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                        // Balance the icon and gap so the text stays centered in the segment.
                        Spacer(modifier = Modifier.width(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodNavigator(
    period: StatsPeriod,
    periodLabel: String,
    isCurrent: Boolean,
    onStep: (Int) -> Unit,
    onLabelClick: () -> Unit,
    onResetToCurrent: () -> Unit
) {
    val strings = LocalContext.current.resources

    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = period != StatsPeriod.ALL && !isCurrent) {
            IconButton(onClick = onResetToCurrent, modifier = Modifier.size(32.dp)) {
                Icon(
                    Icons.Filled.Today,
                    contentDescription = strings.getString(R.string.current_period),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        if (period != StatsPeriod.ALL) {
            IconButton(onClick = { onStep(-1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = strings.getString(R.string.previous_period), modifier = Modifier.size(20.dp))
            }
        }
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .clickable(enabled = period != StatsPeriod.ALL, onClick = onLabelClick)
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                periodLabel,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            if (period != StatsPeriod.ALL) {
                Icon(
                    Icons.Filled.ArrowDropDown,
                    contentDescription = strings.getString(R.string.choose_period),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (period != StatsPeriod.ALL) {
            IconButton(onClick = { onStep(1) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Filled.ChevronRight, contentDescription = strings.getString(R.string.next_period), modifier = Modifier.size(20.dp))
            }
        } else {
            Spacer(Modifier.width(8.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WeekPickerDialog(
    initialTimeMillis: Long,
    onDateSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val strings = LocalContext.current.resources

    val initialUtc = remember(initialTimeMillis) {
        val local = Calendar.getInstance().apply { timeInMillis = initialTimeMillis }
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }.timeInMillis
    }
    val state = rememberDatePickerState(initialSelectedDateMillis = initialUtc)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val selected = state.selectedDateMillis
                if (selected == null) {
                    onDismiss()
                } else {
                    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = selected }
                    val local = Calendar.getInstance().apply {
                        clear()
                        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH), 12, 0)
                    }
                    onDateSelected(local.timeInMillis)
                }
            }) { Text(strings.getString(R.string.ok)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.cancel)) } }
    ) {
        DatePicker(state = state, title = { Text(strings.getString(R.string.choose_day_in_week), modifier = Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

// ─── Type page ───────────────────────────────────────────────────

@Composable
private fun StatisticsTypePage(
    summary: StatisticsSummary,
    type: StatsType,
    now: Long,
    categoryMap: Map<Long, Category>,
    onTransactionClick: (Transaction) -> Unit
) {
    val breakdown = summary.breakdown(type)
    val semantic = MaterialTheme.semanticColors
    val accent = if (type == StatsType.EXPENSE) semantic.expense else semantic.income
    var selectedCategoryIndex by remember(summary, type) { mutableStateOf<Int?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(key = "hero") {
            HeroCard(summary = summary, breakdown = breakdown, accent = accent)
        }
        item(key = "balance") {
            BalanceCard(summary = summary)
        }
        if (breakdown.total <= 0.0) {
            item(key = "empty") { EmptyStatsCard(type = type) }
        } else {
            item(key = "trend") {
                TrendCard(summary = summary, breakdown = breakdown, accent = accent, now = now)
            }
            item(key = "composition") {
                CompositionCard(
                    breakdown = breakdown,
                    selectedIndex = selectedCategoryIndex,
                    onSelect = { selectedCategoryIndex = it }
                )
            }
            item(key = "ranking") {
                CategoryRankingCard(
                    breakdown = breakdown,
                    selectedIndex = selectedCategoryIndex,
                    onSelect = { selectedCategoryIndex = if (selectedCategoryIndex == it) null else it }
                )
            }
            if (breakdown.topTransactions.isNotEmpty()) {
                item(key = "top") {
                    TopTransactionsCard(
                        type = type,
                        entries = breakdown.topTransactions,
                        accent = accent,
                        categoryMap = categoryMap,
                        onTransactionClick = onTransactionClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StatsCard(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 20.dp,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box(modifier = Modifier.padding(contentPadding)) { content() }
    }
}

@Composable
private fun CardTitle(title: String, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

// ─── Hero ────────────────────────────────────────────────────────

@Composable
private fun HeroCard(summary: StatisticsSummary, breakdown: TypeBreakdown, accent: Color) {
    val strings = LocalContext.current.resources

    val countUp = remember { Animatable(0f) }
    LaunchedEffect(breakdown.total) {
        countUp.snapTo(0f)
        countUp.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val displayed = breakdown.total * countUp.value

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = CardShape,
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        listOf(accent.copy(alpha = 0.22f), accent.copy(alpha = 0.06f))
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.getString(R.string.period_total, periodPrefix(strings, summary.period), strings.localizedLabel(breakdown.type.label)),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                ChangeBadge(summary.period, breakdown)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                formatMoney(displayed),
                style = MaterialTheme.typography.amountDisplay,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HeroMetric(
                    label = strings.getString(R.string.daily_average),
                    value = formatMoney(breakdown.dailyAverage),
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = strings.getString(R.string.record_count),
                    value = strings.getQuantityString(R.plurals.records_count, breakdown.count, breakdown.count),
                    modifier = Modifier.weight(1f)
                )
                HeroMetric(
                    label = if (breakdown.type == StatsType.EXPENSE) strings.getString(R.string.share_of_income) else strings.getString(R.string.savings_rate),
                    value = when (breakdown.type) {
                        StatsType.EXPENSE ->
                            if (summary.income.total > 0) formatPercent(summary.expense.total / summary.income.total) else "—"
                        StatsType.INCOME -> summary.savingsRate?.let(::formatPercent) ?: "—"
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HeroMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.amountSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ChangeBadge(period: StatsPeriod, breakdown: TypeBreakdown) {
    val strings = LocalContext.current.resources

    val ratio = breakdown.changeRatio ?: return
    val semantic = MaterialTheme.semanticColors
    val increased = ratio >= 0
    val isGood = if (breakdown.type == StatsType.EXPENSE) !increased else increased
    val color = if (isGood) semantic.income else semantic.expense
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "${previousPeriodLabel(strings, period)} ${if (increased) "▲" else "▼"} ${formatPercent(abs(ratio))}",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = color
        )
    }
}

// ─── Balance ─────────────────────────────────────────────────────

@Composable
private fun BalanceCard(summary: StatisticsSummary) {
    val strings = LocalContext.current.resources

    val semantic = MaterialTheme.semanticColors
    val income = summary.income.total
    val expense = summary.expense.total
    val sum = income + expense
    val incomeFraction by animateFloatAsState(
        targetValue = if (sum > 0) (income / sum).toFloat() else 0.5f,
        animationSpec = tween(600),
        label = "balanceSplit"
    )
    val balance = summary.balance

    StatsCard(contentPadding = 18.dp) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    strings.getString(R.string.balance),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    (if (balance >= 0) "+" else "−") + formatMoney(abs(balance)),
                    style = MaterialTheme.typography.amountMedium,
                    color = if (balance >= 0) semantic.income else semantic.expense
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(CircleShape)
            ) {
                if (sum <= 0) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    )
                } else {
                    if (incomeFraction > 0f) {
                        Box(
                            Modifier
                                .weight(incomeFraction.coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .background(semantic.income)
                        )
                    }
                    if (incomeFraction > 0f && incomeFraction < 1f) Spacer(Modifier.width(3.dp))
                    if (incomeFraction < 1f) {
                        Box(
                            Modifier
                                .weight((1f - incomeFraction).coerceAtLeast(0.001f))
                                .fillMaxHeight()
                                .background(semantic.expense)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row {
                LegendAmount(strings.getString(R.string.income), income, semantic.income, Modifier.weight(1f))
                LegendAmount(strings.getString(R.string.expense), expense, semantic.expense, Modifier.weight(1f), alignEnd = true)
            }
        }
    }
}

@Composable
private fun LegendAmount(
    label: String,
    amount: Double,
    color: Color,
    modifier: Modifier = Modifier,
    alignEnd: Boolean = false
) {
    Row(
        modifier = modifier,
        horizontalArrangement = if (alignEnd) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text(formatMoney(amount), style = MaterialTheme.typography.amountSmall, color = MaterialTheme.colorScheme.onSurface)
    }
}

// ─── Trend ───────────────────────────────────────────────────────

@Composable
private fun TrendCard(summary: StatisticsSummary, breakdown: TypeBreakdown, accent: Color, now: Long) {
    val strings = LocalContext.current.resources

    val buckets = breakdown.trend
    var selectedIndex by remember(buckets) { mutableStateOf<Int?>(null) }
    val elapsedBuckets = remember(buckets, now) { buckets.count { it.start <= now }.coerceAtLeast(1) }
    val average = breakdown.total / elapsedBuckets
    val peakIndex = remember(buckets) {
        buckets.indices.maxByOrNull { buckets[it].amount }?.takeIf { buckets[it].amount > 0 }
    }
    val unit = when (summary.period) {
        StatsPeriod.WEEK, StatsPeriod.MONTH -> strings.getString(R.string.day_unit)
        StatsPeriod.YEAR -> strings.getString(R.string.month_unit)
        StatsPeriod.ALL -> strings.getString(R.string.year_unit)
    }

    StatsCard {
        Column {
            CardTitle(strings.getString(R.string.type_trend, localizedStatType(strings, breakdown.type)))
            Spacer(Modifier.height(4.dp))
            val focus = selectedIndex?.let(buckets::getOrNull)
            if (focus != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        bucketTitle(strings, summary.period, focus),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(formatMoney(focus.amount), style = MaterialTheme.typography.amountMedium, color = accent)
                }
            } else {
                Text(
                    buildString {
                        append(strings.getString(R.string.average_per_unit, unit, formatMoney(average)))
                        peakIndex?.let { append(strings.getString(R.string.peak_bucket, bucketTitle(strings, summary.period, buckets[it]))) }
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            TrendBarChart(
                buckets = buckets,
                period = summary.period,
                accent = accent,
                selectedIndex = selectedIndex,
                average = average,
                onSelect = { selectedIndex = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(170.dp)
            )
        }
    }
}

@Composable
private fun TrendBarChart(
    buckets: List<TrendBucket>,
    period: StatsPeriod,
    accent: Color,
    selectedIndex: Int?,
    average: Double,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalContext.current.resources

    if (buckets.isEmpty()) return
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedLabelStyle = labelStyle.copy(color = accent, fontWeight = FontWeight.Bold)
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
    val averageColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val progress = remember { Animatable(0f) }
    LaunchedEffect(buckets) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val maxValue = (buckets.maxOf { it.amount }).takeIf { it > 0 } ?: 1.0
    val labelStep = if (period == StatsPeriod.ALL && buckets.size > 8) ceil(buckets.size / 6.0).toInt() else 1
    val currentSelected by androidx.compose.runtime.rememberUpdatedState(selectedIndex)

    Canvas(
        modifier = modifier.pointerInput(buckets.size) {
            detectTapGestures { position ->
                val slot = size.width.toFloat() / buckets.size
                val index = (position.x / slot).toInt().coerceIn(0, buckets.lastIndex)
                onSelect(if (index == currentSelected) null else index)
            }
        }
    ) {
        val labelHeight = 20.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slot = size.width / buckets.size
        val barWidth = (slot * 0.62f).coerceAtMost(26.dp.toPx())
        val corner = CornerRadius(min(barWidth / 2f, 8.dp.toPx()))

        buckets.forEachIndexed { index, bucket ->
            val left = slot * index + (slot - barWidth) / 2f
            drawRoundRect(trackColor, Offset(left, 0f), Size(barWidth, chartHeight), corner)
            val fraction = (bucket.amount / maxValue).toFloat()
            if (fraction > 0f) {
                val height = (chartHeight * fraction * progress.value).coerceAtLeast(3.dp.toPx())
                val color = when (selectedIndex) {
                    null -> accent.copy(alpha = 0.85f)
                    index -> accent
                    else -> accent.copy(alpha = 0.28f)
                }
                drawRoundRect(color, Offset(left, chartHeight - height), Size(barWidth, height), corner)
            }

            val showLabel = index == selectedIndex || when (period) {
                StatsPeriod.MONTH -> bucket.label == "1" || bucket.label.toInt() % 5 == 0
                StatsPeriod.ALL -> index % labelStep == 0
                else -> true
            }
            if (showLabel) {
                val layout = textMeasurer.measure(
                    localizedBucketLabel(strings, period, bucket),
                    if (index == selectedIndex) selectedLabelStyle else labelStyle
                )
                val x = (slot * index + slot / 2f - layout.size.width / 2f)
                    .coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, chartHeight + (labelHeight - layout.size.height) / 2f + 2.dp.toPx()))
            }
        }

        if (average > 0) {
            val y = chartHeight - (average / maxValue).toFloat() * chartHeight
            drawLine(
                color = averageColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))
            )
        }
    }
}

// ─── Composition (donut) ─────────────────────────────────────────

@Composable
private fun CompositionCard(
    breakdown: TypeBreakdown,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit
) {
    val strings = LocalContext.current.resources

    val categories = breakdown.categories
    val selected = selectedIndex?.let(categories::getOrNull)

    StatsCard {
        Column {
            CardTitle(strings.getString(R.string.category_share)) {
                Text(
                    strings.getQuantityString(R.plurals.categories_count, categories.size, categories.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(156.dp)) {
                    DonutChart(
                        categories = categories,
                        selectedIndex = selectedIndex,
                        onSelect = onSelect,
                        modifier = Modifier.fillMaxSize()
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(92.dp)
                    ) {
                        Text(
                            selected?.name?.let { localizedCategoryName(strings, it) } ?: strings.getString(R.string.total),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            selected?.let { formatPercent(it.share.toDouble()) } ?: formatCompact(strings, breakdown.total),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = selected?.let { Color(it.color) } ?: MaterialTheme.colorScheme.onSurface,
                            maxLines = 1
                        )
                        if (selected != null) {
                            Text(
                                formatMoney(selected.amount),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val visible = categories.take(5)
                    visible.forEachIndexed { index, stat ->
                        val isSelected = index == selectedIndex
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(stat.color).copy(alpha = 0.12f) else Color.Transparent
                                )
                                .clickable { onSelect(if (isSelected) null else index) }
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color(stat.color))
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                localizedCategoryName(strings, stat.name),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatPercent(stat.share.toDouble()),
                                style = MaterialTheme.typography.amountSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (categories.size > visible.size) {
                        val rest = categories.drop(visible.size)
                        Text(
                            strings.getString(R.string.other_categories, rest.size, formatPercent(rest.sumOf { it.share.toDouble() })),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    categories: List<CategoryStat>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(categories) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val total = categories.sumOf { it.amount }.takeIf { it > 0 } ?: 1.0
    val sweeps = remember(categories) { categories.map { (it.amount / total * 360.0).toFloat() } }
    val currentSelected by androidx.compose.runtime.rememberUpdatedState(selectedIndex)

    Canvas(
        modifier = modifier.pointerInput(categories) {
            detectTapGestures { position ->
                val center = Offset(size.width / 2f, size.height / 2f)
                val delta = position - center
                val outer = min(size.width, size.height) / 2f
                val distance = delta.getDistance()
                if (distance > outer || distance < outer * 0.45f) {
                    onSelect(null)
                    return@detectTapGestures
                }
                val angle = ((Math.toDegrees(atan2(delta.y, delta.x).toDouble()) + 90.0 + 360.0) % 360.0).toFloat()
                var cumulative = 0f
                val index = sweeps.indexOfFirst { sweep ->
                    cumulative += sweep
                    angle <= cumulative
                }.takeIf { it >= 0 }
                onSelect(if (index == currentSelected) null else index)
            }
        }
    ) {
        val baseStroke = size.minDimension * 0.15f
        val selectedStroke = baseStroke * 1.35f
        val radius = (size.minDimension - selectedStroke) / 2f
        val topLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val gap = if (categories.size > 1) 1.6f else 0f

        var start = -90f
        val visibleSweep = 360f * progress.value
        categories.forEachIndexed { index, stat ->
            val sweep = sweeps[index]
            val drawnEnd = min(start + sweep, -90f + visibleSweep)
            val drawn = drawnEnd - start
            if (drawn > 0f) {
                val isSelected = index == selectedIndex
                val dimmed = selectedIndex != null && !isSelected
                val actualGap = if (sweep > gap * 2) gap else 0f
                drawArc(
                    color = Color(stat.color).copy(alpha = if (dimmed) 0.35f else 1f),
                    startAngle = start + actualGap / 2f,
                    sweepAngle = (drawn - actualGap).coerceAtLeast(0.1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = if (isSelected) selectedStroke else baseStroke, cap = StrokeCap.Butt)
                )
            }
            start += sweep
        }
    }
}

// ─── Category ranking ────────────────────────────────────────────

@Composable
private fun CategoryRankingCard(
    breakdown: TypeBreakdown,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit
) {
    val strings = LocalContext.current.resources

    var expanded by remember(breakdown) { mutableStateOf(setOf<Int>()) }
    StatsCard(contentPadding = 8.dp) {
        Column {
            Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                CardTitle(strings.getString(R.string.category_ranking))
            }
            breakdown.categories.forEachIndexed { index, stat ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                    )
                }
                val isExpanded = index in expanded
                CategoryRankRow(
                    stat = stat,
                    highlighted = index == selectedIndex,
                    expandable = stat.children.isNotEmpty(),
                    expanded = isExpanded,
                    onClick = {
                        onSelect(index)
                        if (stat.children.isNotEmpty()) {
                            expanded = if (isExpanded) expanded - index else expanded + index
                        }
                    }
                )
                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .padding(start = 68.dp, end = 12.dp, bottom = 10.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainer)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        stat.children.forEachIndexed { childIndex, child ->
                            if (childIndex > 0) {
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                                )
                            }
                            SubCategoryRow(child)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRankRow(
    stat: CategoryStat,
    highlighted: Boolean,
    expandable: Boolean,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources

    val color = Color(stat.color)
    val animatedShare by animateFloatAsState(stat.share, tween(800), label = "share")
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val background by animateColorAsState(
        if (highlighted) color.copy(alpha = 0.10f) else Color.Transparent,
        label = "rankBg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                IconMapper.getIcon(stat.icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        localizedCategoryName(strings, stat.name),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        strings.getQuantityString(R.plurals.category_record_count, stat.count, stat.count),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    if (expandable) {
                        Spacer(Modifier.width(2.dp))
                        Icon(
                            Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) strings.getString(R.string.collapse_subcategories) else strings.getString(R.string.expand_subcategories),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(chevronRotation)
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(formatMoney(stat.amount), style = MaterialTheme.typography.amountSmall)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.12f))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(animatedShare.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(color)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    formatPercent(stat.share.toDouble()),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

@Composable
private fun SubCategoryRow(stat: CategoryStat) {
    val strings = LocalContext.current.resources

    val color = Color(stat.color)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            IconMapper.getIcon(stat.icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(16.dp)
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                localizedCategoryName(strings, stat.name),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                strings.getQuantityString(R.plurals.category_record_count, stat.count, stat.count),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatPercent(stat.share.toDouble()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(formatMoney(stat.amount), style = MaterialTheme.typography.amountSmall)
    }
}

// ─── Top transactions ────────────────────────────────────────────

@Composable
private fun TopTransactionsCard(
    type: StatsType,
    entries: List<RankedTransaction>,
    accent: Color,
    categoryMap: Map<Long, Category>,
    onTransactionClick: (Transaction) -> Unit
) {
    val strings = LocalContext.current.resources

    StatsCard(contentPadding = 8.dp) {
        Column {
            Box(Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp)) {
                CardTitle(strings.getString(R.string.largest_transaction, localizedStatType(strings, type)))
            }
            entries.forEachIndexed { index, entry ->
                val tx = entry.transaction
                val category = tx.categoryId?.let(categoryMap::get)
                val color = Color(category?.color ?: 0xFF6B7280)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onTransactionClick(tx) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = if (index == 0) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(20.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(color.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            IconMapper.getIcon(category?.icon ?: "MoreHoriz"),
                            contentDescription = null,
                            tint = color,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            tx.note.ifBlank { category?.name?.let { strings.localizedLabel(it) } ?: strings.getString(R.string.uncategorized) },
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            strings.getString(R.string.dated_category, formatShortDate(entry.displayDate), category?.name?.let { strings.localizedLabel(it) } ?: strings.getString(R.string.uncategorized)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                    Text(
                        (if (type == StatsType.EXPENSE) "−" else "+") + formatMoney(entry.amount),
                        style = MaterialTheme.typography.amountSmall,
                        color = accent
                    )
                }
            }
        }
    }
}

// ─── Empty ───────────────────────────────────────────────────────

@Composable
private fun EmptyStatsCard(type: StatsType) {
    val strings = LocalContext.current.resources

    StatsCard(contentPadding = 32.dp) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            EmptyIcon(Icons.Filled.Insights)
            Spacer(Modifier.height(14.dp))
            Text(
                strings.getString(R.string.no_type_records, localizedStatType(strings, type)),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                strings.getString(R.string.switch_stats_hint, if (type == StatsType.EXPENSE) strings.getString(R.string.income) else strings.getString(R.string.expense)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun EmptyIcon(icon: ImageVector) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
    }
}

// ─── Formatting helpers ──────────────────────────────────────────

private val moneyFormat: NumberFormat = NumberFormat.getNumberInstance(Locale.TAIWAN).apply {
    maximumFractionDigits = 0
}

private fun formatMoney(amount: Double): String = "$" + moneyFormat.format(amount)

private fun formatCompact(strings: android.content.res.Resources, amount: Double): String = when {
    strings.configuration.locales[0].language == "en" && amount >= 1_000_000_000 -> String.format(Locale.US, "$%.1fB", amount / 1_000_000_000)
    strings.configuration.locales[0].language == "en" && amount >= 1_000_000 -> String.format(Locale.US, "$%.1fM", amount / 1_000_000)
    strings.configuration.locales[0].language == "en" && amount >= 1_000 -> String.format(Locale.US, "$%.1fK", amount / 1_000)
    strings.configuration.locales[0].language == "en" -> formatMoney(amount)
    amount >= 100_000_000 -> String.format(Locale.US, strings.getString(R.string.compact_hundred_million), amount / 100_000_000)
    amount >= 10_000 -> String.format(Locale.US, strings.getString(R.string.compact_ten_thousand), amount / 10_000)
    else -> formatMoney(amount)
}

private fun formatPercent(ratio: Double): String {
    val percent = ratio * 100
    return if (percent >= 10 || percent == 0.0) {
        String.format(Locale.US, "%.0f%%", percent)
    } else {
        String.format(Locale.US, "%.1f%%", percent)
    }
}

private fun formatShortDate(time: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = time }
    return "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)}"
}

private fun periodPrefix(strings: android.content.res.Resources, period: StatsPeriod): String = when (period) {
    StatsPeriod.WEEK -> strings.getString(R.string.week_unit)
    StatsPeriod.MONTH -> strings.getString(R.string.month_unit)
    StatsPeriod.YEAR -> strings.getString(R.string.year_unit)
    StatsPeriod.ALL -> strings.getString(R.string.cumulative)
}

private fun previousPeriodLabel(strings: android.content.res.Resources, period: StatsPeriod): String = when (period) {
    StatsPeriod.WEEK -> strings.getString(R.string.versus_last_week)
    StatsPeriod.MONTH -> strings.getString(R.string.versus_last_month)
    StatsPeriod.YEAR -> strings.getString(R.string.versus_last_year)
    StatsPeriod.ALL -> ""
}

private fun bucketTitle(strings: android.content.res.Resources, period: StatsPeriod, bucket: TrendBucket): String {
    val cal = Calendar.getInstance().apply { timeInMillis = bucket.start }
    return when (period) {
        StatsPeriod.WEEK -> "${cal.get(Calendar.MONTH) + 1}/${cal.get(Calendar.DAY_OF_MONTH)} (${localizedBucketLabel(strings, period, bucket)})"
        StatsPeriod.MONTH -> strings.getString(R.string.month_day, cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH))
        StatsPeriod.YEAR -> strings.getString(R.string.year_month, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1)
        StatsPeriod.ALL -> strings.getString(R.string.calendar_year, cal.get(Calendar.YEAR))
    }
}

private fun localizedBucketLabel(strings: android.content.res.Resources, period: StatsPeriod, bucket: TrendBucket): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = bucket.start }
    return when (period) {
        StatsPeriod.WEEK -> java.text.SimpleDateFormat("E", strings.configuration.locales[0]).format(calendar.time)
        StatsPeriod.YEAR -> strings.getString(R.string.trend_month, calendar.get(Calendar.MONTH) + 1)
        else -> bucket.label
    }
}

private fun localizedCategoryName(strings: android.content.res.Resources, name: String): String {
    val suffix = "（未細分）"
    return if (name.endsWith(suffix)) {
        strings.getString(R.string.category_unspecified, strings.localizedLabel(name.removeSuffix(suffix)))
    } else strings.localizedLabel(name)
}

private fun localizedStatType(strings: android.content.res.Resources, type: StatsType): String =
    strings.getString(if (type == StatsType.EXPENSE) R.string.expense_noun else R.string.income_noun)
