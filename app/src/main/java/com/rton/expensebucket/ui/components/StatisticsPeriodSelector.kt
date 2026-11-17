package com.rton.expensebucket.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.ui.statistics.StatsPeriod
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
internal fun StatisticsPeriodSelector(
    period: StatsPeriod,
    onPeriodChange: (StatsPeriod) -> Unit,
    modifier: Modifier = Modifier
) {
    val periods = StatsPeriod.entries
    val selectedIndex = periods.indexOf(period)
    val currentOnPeriodChange by rememberUpdatedState(onPeriodChange)
    val currentPeriod by rememberUpdatedState(period)
    val layoutDirection = LocalLayoutDirection.current
    var dragPosition by remember { mutableStateOf<Float?>(null) }
    val position by animateFloatAsState(
        targetValue = dragPosition ?: selectedIndex.toFloat(),
        animationSpec = if (dragPosition != null) snap() else spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "statisticsPeriodPosition"
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(3.dp)
            .selectableGroup()
            .testTag("statistics_period_selector")
            .pointerInput(layoutDirection) {
                fun positionAt(x: Float): Float {
                    val logicalX = if (layoutDirection == LayoutDirection.Rtl) size.width - x else x
                    return (logicalX / (size.width.toFloat() / periods.size) - 0.5f)
                        .coerceIn(0f, periods.lastIndex.toFloat())
                }

                detectHorizontalDragGestures(
                    onDragStart = { dragPosition = positionAt(it.x) },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragPosition = positionAt(change.position.x)
                    },
                    onDragEnd = {
                        // Commit once on release so charts do not rebuild for every crossed tab.
                        val target = dragPosition?.roundToInt()?.let { periods[it] }
                        dragPosition = null
                        if (target != null && target != currentPeriod) currentOnPeriodChange(target)
                    },
                    onDragCancel = { dragPosition = null }
                )
            }
    ) {
        val segmentWidth = maxWidth / periods.size
        Box(
            modifier = Modifier
                .offset(x = segmentWidth * position)
                .width(segmentWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
                .testTag("statistics_period_indicator")
        )
        Row(Modifier.fillMaxSize()) {
            periods.forEachIndexed { index, option ->
                val selectedness = 1f - abs(position - index).coerceIn(0f, 1f)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .selectable(
                            selected = option == period,
                            role = Role.Tab,
                            onClick = { currentOnPeriodChange(option) }
                        )
                        .testTag("statistics_period_${option.name.lowercase()}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selectedness >= 0.5f) FontWeight.SemiBold else FontWeight.Normal,
                        color = lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant,
                            MaterialTheme.colorScheme.onPrimary,
                            selectedness
                        )
                    )
                }
            }
        }
    }
}
