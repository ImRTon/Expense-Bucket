package com.rton.expensebucket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.data.model.Project
import kotlin.math.ceil
import kotlin.math.floor

enum class TimelineWindow(val label: String, val months: Int) {
    FIVE_YEARS("5Y", 60),
    THREE_YEARS("3Y", 36),
    ONE_YEAR("1Y", 12),
    SIX_MONTHS("6M", 6)
}

@Composable
fun ProjectTimelineChart(
    projects: List<Project>,
    expenseTotals: Map<Long, Double>,
    onProjectFocused: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedWindow by rememberSaveable { mutableStateOf(TimelineWindow.ONE_YEAR) }
    var scrollOffsetPx by remember { mutableFloatStateOf(0f) }
    var chartWidthPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val currentMonthIndex = remember { monthIndex(System.currentTimeMillis()) }
    val newestProject = remember(projects) { projects.maxByOrNull { it.startDate ?: it.createdAt } }

    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant
    val surfaceColor = MaterialTheme.colorScheme.surface
    val selectorContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh

    LaunchedEffect(chartWidthPx, selectedWindow, newestProject?.id) {
        if (chartWidthPx <= 0 || newestProject == null) return@LaunchedEffect
        scrollOffsetPx = computeAutoFocusOffset(
            widthPx = chartWidthPx.toFloat(),
            window = selectedWindow,
            currentMonthIndex = currentMonthIndex,
            projectTimeMillis = newestProject.startDate ?: newestProject.createdAt
        )
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "時間軸",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                    )
                    TimelineWindowSelector(
                        selectedWindow = selectedWindow,
                        containerColor = selectorContainerColor,
                        onSelectionChange = { selectedWindow = it }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                val axisBottomPaddingPx = with(density) { 24.dp.toPx() }
                val topPaddingPx = with(density) { 12.dp.toPx() }
                val barWidthPx = with(density) { 7.dp.toPx() }
                val minBarHeightPx = with(density) { 16.dp.toPx() }
                val badgeRadiusPx = with(density) { 13.dp.toPx() }
                val badgeGapPx = with(density) { 8.dp.toPx() }
                val tickLabelOffsetPx = with(density) { 4.dp.toPx() }
                val tapTolerancePx = with(density) { 22.dp.toPx() }

                val maxExpense = remember(expenseTotals) {
                    expenseTotals.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
                }
                val tickTextStyle = remember(onSurfaceVariantColor) {
                    TextStyle(
                        color = onSurfaceVariantColor,
                        fontSize = 10.sp
                    )
                }
                val emojiTextStyle = remember {
                    TextStyle(fontSize = 14.sp)
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .onSizeChanged { chartWidthPx = it.width }
                            .pointerInput(selectedWindow) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    scrollOffsetPx += dragAmount
                                }
                            }
                            .pointerInput(selectedWindow, projects, scrollOffsetPx) {
                                detectTapGestures { tapOffset ->
                                    val width = size.width.toFloat()
                                    if (width <= 0f) return@detectTapGestures

                                    val hitProject = projects
                                        .asSequence()
                                        .map { project ->
                                            val x = projectX(
                                                widthPx = width,
                                                window = selectedWindow,
                                                currentMonthIndex = currentMonthIndex,
                                                projectTimeMillis = project.startDate ?: project.createdAt,
                                                scrollOffsetPx = scrollOffsetPx
                                            )
                                            project to x
                                        }
                                        .filter { (_, x) -> tapOffset.x in (x - tapTolerancePx)..(x + tapTolerancePx) }
                                        .minByOrNull { (_, x) -> kotlin.math.abs(tapOffset.x - x) }
                                        ?.first

                                    if (hitProject != null) {
                                        onProjectFocused(hitProject.id)
                                        scrollOffsetPx = computeAutoFocusOffset(
                                            widthPx = width,
                                            window = selectedWindow,
                                            currentMonthIndex = currentMonthIndex,
                                            projectTimeMillis = hitProject.startDate ?: hitProject.createdAt
                                        )
                                    } else {
                                        onProjectFocused(null)
                                    }
                                }
                            }
                    ) {
                        val width = size.width
                        val height = size.height
                        val axisY = height - axisBottomPaddingPx
                        val pxPerMonth = width / selectedWindow.months.toFloat()

                        drawLine(
                            color = onSurfaceColor.copy(alpha = 0.18f),
                            start = Offset(0f, axisY),
                            end = Offset(width, axisY),
                            strokeWidth = 1.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val minDiff = floor((scrollOffsetPx - 40f) / pxPerMonth).toInt()
                        val maxDiff = ceil((width + scrollOffsetPx + 40f) / pxPerMonth).toInt()

                        for (diff in minDiff..maxDiff) {
                            val monthIdx = currentMonthIndex - diff
                            val (year, monthZeroBased) = yearMonthFromIndex(monthIdx)
                            val x = width - diff * pxPerMonth + scrollOffsetPx

                            if (x < -40f || x > width + 40f) continue

                            val isJan = monthZeroBased == 0
                            val isHalfYear = monthZeroBased == 0 || monthZeroBased == 6
                            val isQuarter = monthZeroBased % 3 == 0
                            val showTick = when (selectedWindow) {
                                TimelineWindow.FIVE_YEARS -> isJan
                                TimelineWindow.THREE_YEARS -> isHalfYear
                                TimelineWindow.ONE_YEAR -> isQuarter
                                TimelineWindow.SIX_MONTHS -> true
                            }

                            if (!showTick) continue

                            drawLine(
                                color = onSurfaceColor.copy(alpha = if (isJan) 0.11f else 0.05f),
                                start = Offset(x, topPaddingPx),
                                end = Offset(x, axisY),
                                strokeWidth = if (isJan) 1.2.dp.toPx() else 1.dp.toPx()
                            )

                            val label = if (isJan) year.toString() else "${monthZeroBased + 1}月"
                            val measuredLabel = textMeasurer.measure(label, tickTextStyle)
                            val labelX = (x - measuredLabel.size.width / 2f)
                                .coerceIn(0f, width - measuredLabel.size.width.toFloat())

                            drawText(
                                measuredLabel,
                                topLeft = Offset(labelX, axisY + tickLabelOffsetPx)
                            )
                        }

                        projects.forEach { project ->
                            val projectTime = project.startDate ?: project.createdAt
                            val x = projectX(
                                widthPx = width,
                                window = selectedWindow,
                                currentMonthIndex = currentMonthIndex,
                                projectTimeMillis = projectTime,
                                scrollOffsetPx = scrollOffsetPx
                            )

                            if (x < -36f || x > width + 36f) return@forEach

                            val expense = expenseTotals[project.id] ?: 0.0
                            val availableHeight = axisY - topPaddingPx - (badgeRadiusPx * 2f) - badgeGapPx
                            val scaledHeight = if (expense <= 0.0) {
                                minBarHeightPx
                            } else {
                                ((expense / maxExpense).toFloat() * availableHeight)
                                    .coerceAtLeast(minBarHeightPx)
                            }

                            val barHeight = scaledHeight.coerceAtMost(availableHeight)
                            val barTop = axisY - barHeight
                            val barLeft = x - barWidthPx / 2f
                            val barColor = Color(project.color)
                            val badgeCenter = Offset(x, barTop - badgeGapPx - badgeRadiusPx)

                            drawLine(
                                color = barColor.copy(alpha = 0.22f),
                                start = Offset(x, topPaddingPx + 6.dp.toPx()),
                                end = Offset(x, axisY),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Round
                            )

                            drawRoundRect(
                                color = barColor,
                                topLeft = Offset(barLeft, barTop),
                                size = Size(barWidthPx, barHeight),
                                cornerRadius = CornerRadius(barWidthPx, barWidthPx)
                            )

                            drawCircle(
                                color = surfaceColor,
                                radius = badgeRadiusPx,
                                center = badgeCenter
                            )
                            drawCircle(
                                color = barColor.copy(alpha = 0.18f),
                                radius = badgeRadiusPx,
                                center = badgeCenter
                            )
                            drawCircle(
                                color = barColor.copy(alpha = 0.34f),
                                radius = badgeRadiusPx,
                                center = badgeCenter,
                                style = Stroke(width = 1.dp.toPx())
                            )

                            val measuredEmoji = textMeasurer.measure(project.icon, emojiTextStyle)
                            drawText(
                                measuredEmoji,
                                topLeft = Offset(
                                    x - measuredEmoji.size.width / 2f,
                                    badgeCenter.y - measuredEmoji.size.height / 2f
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineWindowSelector(
    selectedWindow: TimelineWindow,
    containerColor: Color,
    onSelectionChange: (TimelineWindow) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = containerColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineWindow.entries.forEach { window ->
                val isSelected = window == selectedWindow
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary
                    else Color.Transparent,
                    modifier = Modifier.clickable { onSelectionChange(window) }
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = window.label,
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

private fun computeAutoFocusOffset(
    widthPx: Float,
    window: TimelineWindow,
    currentMonthIndex: Int,
    projectTimeMillis: Long
): Float {
    val pxPerMonth = widthPx / window.months.toFloat()
    val projectMonthIndex = monthIndex(projectTimeMillis)
    val baseX = widthPx - (currentMonthIndex - projectMonthIndex) * pxPerMonth
    val targetX = widthPx * 0.74f
    return targetX - baseX
}

private fun projectX(
    widthPx: Float,
    window: TimelineWindow,
    currentMonthIndex: Int,
    projectTimeMillis: Long,
    scrollOffsetPx: Float
): Float {
    val pxPerMonth = widthPx / window.months.toFloat()
    val projectMonthIndex = monthIndex(projectTimeMillis)
    val monthDiff = currentMonthIndex - projectMonthIndex
    return widthPx - monthDiff * pxPerMonth + scrollOffsetPx
}

private fun monthIndex(timeMillis: Long): Int {
    val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timeMillis }
    return calendar.get(java.util.Calendar.YEAR) * 12 + calendar.get(java.util.Calendar.MONTH)
}

private fun yearMonthFromIndex(index: Int): Pair<Int, Int> {
    val year = floor(index / 12.0).toInt()
    val month = index - year * 12
    return year to month
}
