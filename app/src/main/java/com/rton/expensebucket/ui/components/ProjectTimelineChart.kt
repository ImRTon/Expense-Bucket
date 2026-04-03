package com.rton.expensebucket.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.data.model.Project
import java.util.*

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
    var selectedWindow by remember { mutableStateOf(TimelineWindow.ONE_YEAR) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }

    val now = remember { System.currentTimeMillis() }
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    // Pre-calculate all dp -> px conversions outside Canvas
    val barWidthPx = with(density) { 16.dp.toPx() } // Progress bar style width
    val halfBarWidthPx = barWidthPx / 2f
    val axisBottomPaddingPx = with(density) { 24.dp.toPx() }
    val tickLenPx = with(density) { 4.dp.toPx() }
    val tickOffsetPx = with(density) { 4.dp.toPx() }
    val barTopPaddingPx = with(density) { 36.dp.toPx() } // Room for emoji above bar
    val strokeWidthPx = with(density) { 2.dp.toPx() }
    val thinStrokePx = with(density) { 1.dp.toPx() }
    val emojiOffsetYPx = with(density) { 28.dp.toPx() } 
    val emojiSizePx = with(density) { 20.dp.toPx() }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(190.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(20.dp)
            )
            .clip(RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        val componentWidth = maxWidth
        val componentWidthPx = with(density) { componentWidth.toPx() }

        // Center on newest project when window changes
        LaunchedEffect(selectedWindow, projects) {
            val pxPerMonth = componentWidthPx / selectedWindow.months
            val newestProject = projects.maxByOrNull { it.startDate ?: it.createdAt }
            if (newestProject != null) {
                val cal = Calendar.getInstance()
                cal.timeInMillis = now
                val curY = cal.get(Calendar.YEAR)
                val curM = cal.get(Calendar.MONTH)
                
                cal.timeInMillis = newestProject.startDate ?: newestProject.createdAt
                val pY = cal.get(Calendar.YEAR)
                val pM = cal.get(Calendar.MONTH)
                
                val monthDiff = (curY * 12 + curM) - (pY * 12 + pM)
                // We want: componentWidthPx / 2 = componentWidthPx - monthDiff*pxPerMonth - scrollShift*pxPerMonth
                // So scrollShift = (componentWidthPx/2) / pxPerMonth - monthDiff
                val targetScrollShift = (componentWidthPx / 2f) / pxPerMonth - monthDiff
                scrollOffset = targetScrollShift * pxPerMonth
            }
        }

        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "專案花費時間軸",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TimelineWindow.entries.forEach { window ->
                        val isSelected = window == selectedWindow
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.surface
                                )
                                .clickable { selectedWindow = window }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = window.label,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Chart
            Box(modifier = Modifier.fillMaxSize()) {
                val maxExpense = remember(expenseTotals) {
                    expenseTotals.values.maxOrNull() ?: 1.0
                }
                val onSurfaceColor = MaterialTheme.colorScheme.onSurface

                val tickTextStyle = remember(onSurfaceColor) {
                    TextStyle(color = onSurfaceColor.copy(alpha = 0.6f), fontSize = 10.sp)
                }
                val emojiTextStyle = remember {
                    TextStyle(fontSize = 18.sp)
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(selectedWindow) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                scrollOffset += dragAmount
                            }
                        }
                        .pointerInput(selectedWindow) {
                            detectTapGestures { offset ->
                                val cal = Calendar.getInstance()
                                val visibleMonths = selectedWindow.months
                                val pxPerMonth = size.width.toFloat() / visibleMonths
                                val scrollShift = scrollOffset / pxPerMonth
                                val rightEdge = now - (scrollShift * 30L * 24 * 60 * 60 * 1000).toLong()

                                cal.timeInMillis = rightEdge
                                val rY = cal.get(Calendar.YEAR)
                                val rM = cal.get(Calendar.MONTH)

                                val clicked = projects.find { p ->
                                    val t = p.startDate ?: p.createdAt
                                    cal.timeInMillis = t
                                    val pY = cal.get(Calendar.YEAR)
                                    val pM = cal.get(Calendar.MONTH)
                                    val diff = (rY * 12 + rM) - (pY * 12 + pM)
                                    val x = size.width.toFloat() - diff * pxPerMonth
                                    // Make hit box slightly larger for easier tapping
                                    offset.x in (x - barWidthPx*1.5f)..(x + barWidthPx*1.5f)
                                }
                                if (clicked != null) {
                                    onProjectFocused(clicked.id)
                                }
                            }
                        }
                ) {
                    val cal = Calendar.getInstance()
                    val visibleMonths = selectedWindow.months
                    val pxPerMonth = size.width / visibleMonths
                    val scrollShift = scrollOffset / pxPerMonth
                    val rightEdgeMillis = now - (scrollShift * 30L * 24 * 60 * 60 * 1000).toLong()
                    val axisY = size.height - axisBottomPaddingPx

                    // Horizontal axis
                    drawLine(
                        color = onSurfaceColor.copy(alpha = 0.2f),
                        start = Offset(0f, axisY),
                        end = Offset(size.width, axisY),
                        strokeWidth = strokeWidthPx,
                        cap = StrokeCap.Round
                    )

                    // Timeline ticks & grid lines
                    cal.timeInMillis = rightEdgeMillis
                    val rYear = cal.get(Calendar.YEAR)
                    val rMonth = cal.get(Calendar.MONTH)

                    for (i in -2..(visibleMonths + 2)) {
                        val totalMonthOffset = rMonth - i
                        val cYear: Int
                        val cMonth: Int
                        if (totalMonthOffset >= 0) {
                            cYear = rYear + totalMonthOffset / 12
                            cMonth = totalMonthOffset % 12
                        } else {
                            val absOffset = -totalMonthOffset
                            cYear = rYear - (absOffset + 11) / 12
                            cMonth = (12 - absOffset % 12) % 12
                        }

                        val monthDiff = (rYear * 12 + rMonth) - (cYear * 12 + cMonth)
                        val xPos = size.width - monthDiff * pxPerMonth

                        if (xPos < -40f || xPos > size.width + 40f) continue

                        val isJan = cMonth == 0
                        val isHalfYear = cMonth == 0 || cMonth == 6
                        val isQuarter = cMonth % 3 == 0

                        val showTick = when (selectedWindow) {
                            TimelineWindow.FIVE_YEARS -> isJan
                            TimelineWindow.THREE_YEARS -> isHalfYear
                            TimelineWindow.ONE_YEAR -> isQuarter
                            TimelineWindow.SIX_MONTHS -> true
                        }

                        if (showTick) {
                            // Vertical guideline
                            drawLine(
                                color = onSurfaceColor.copy(alpha = if (isJan) 0.15f else 0.08f),
                                start = Offset(xPos, 0f),
                                end = Offset(xPos, axisY)
                            )
                            // Tick
                            drawLine(
                                color = onSurfaceColor.copy(alpha = 0.4f),
                                start = Offset(xPos, axisY),
                                end = Offset(xPos, axisY + tickLenPx),
                                strokeWidth = thinStrokePx
                            )
                            // Label
                            val label = if (isJan) "$cYear" else "${cMonth + 1}月"
                            val measuredTick = textMeasurer.measure(label, tickTextStyle, maxLines = 1)
                            val tickLabelX = xPos + tickOffsetPx
                            if (tickLabelX + measuredTick.size.width <= size.width + 40f) {
                                drawText(measuredTick, topLeft = Offset(tickLabelX, axisY + tickOffsetPx))
                            }
                        }
                    }

                    // Project bars
                    projects.forEach { project ->
                        val projectTime = project.startDate ?: project.createdAt
                        cal.timeInMillis = rightEdgeMillis
                        val curRY = cal.get(Calendar.YEAR)
                        val curRM = cal.get(Calendar.MONTH)
                        cal.timeInMillis = projectTime
                        val pY = cal.get(Calendar.YEAR)
                        val pM = cal.get(Calendar.MONTH)
                        val monthDiff = (curRY * 12 + curRM) - (pY * 12 + pM)
                        val xPos = size.width - monthDiff * pxPerMonth

                        if (xPos < -barWidthPx*2 || xPos > size.width + barWidthPx*2) return@forEach

                        val expense = expenseTotals[project.id] ?: 0.0
                        val maxBarH = axisY - barTopPaddingPx
                        val computed = if (maxExpense > 0) (expense / maxExpense).toFloat() * maxBarH else 0f
                        val barH = computed.coerceAtLeast(barWidthPx)

                        val topY = axisY - barH + halfBarWidthPx
                        val bottomY = axisY - halfBarWidthPx

                        // Rounded pill bar with project color
                        val barColor = Color(project.color)
                        drawLine(
                            color = barColor,
                            start = Offset(xPos, topY),
                            end = Offset(xPos, bottomY),
                            strokeWidth = barWidthPx,
                            cap = StrokeCap.Round
                        )

                        // Project emoji label horizontally centered above the bar
                        val measuredEmoji = textMeasurer.measure(
                            text = project.icon,
                            style = emojiTextStyle,
                            maxLines = 1
                        )
                        val emojiWidth = measuredEmoji.size.width.toFloat()
                        val emojiX = xPos - (emojiWidth / 2f)
                        val emojiY = axisY - barH - emojiOffsetYPx
                        drawText(measuredEmoji, topLeft = Offset(emojiX, emojiY))
                    }
                }
            }
        }
    }
}
