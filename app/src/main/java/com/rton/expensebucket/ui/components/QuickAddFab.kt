package com.rton.expensebucket.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun QuickAddFab(
    onAddClick: () -> Unit,
    onReceiptOcrClick: () -> Unit,
    onInvoiceOcrClick: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var highlightedOptionId by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    val view = LocalView.current

    val mainFabRadius = 32.dp
    val mainFabDiameter = mainFabRadius * 2
    val fanMenuSize = 280.dp

    Box(
        modifier = Modifier.size(fanMenuSize),
        contentAlignment = Alignment.BottomEnd
    ) {
        val fanOptions = remember(onReceiptOcrClick, onInvoiceOcrClick, density, fanMenuSize) {
            val menuSizePx = with(density) { fanMenuSize.toPx() }
            val fabRadiusPx = with(density) { mainFabRadius.toPx() }
            buildFanOptions(
                density = density,
                centerX = menuSizePx - fabRadiusPx,
                centerY = menuSizePx - fabRadiusPx,
                onReceiptOcrClick = onReceiptOcrClick,
                onInvoiceOcrClick = onInvoiceOcrClick
            )
        }

        fanOptions.forEach { option ->
            AnimatedVisibility(
                visible = menuExpanded,
                enter = fadeIn() + scaleIn(animationSpec = spring()),
                exit = fadeOut() + scaleOut(animationSpec = spring()),
                modifier = Modifier.fillMaxSize()
            ) {
                FanOptionCard(
                    option = option,
                    highlighted = highlightedOptionId == option.id,
                    density = density,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Box(
            modifier = Modifier
                .size(mainFabDiameter)
                .pointerInput(onAddClick, onReceiptOcrClick, onInvoiceOcrClick) {
                    val fabRadiusPx = with(density) { mainFabRadius.toPx() }
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)

                        val centerX = fabRadiusPx
                        val centerY = fabRadiusPx
                        val dx = down.position.x - centerX
                        val dy = down.position.y - centerY
                        if (dx * dx + dy * dy > fabRadiusPx * fabRadiusPx) {
                            return@awaitEachGesture
                        }

                        menuExpanded = false
                        highlightedOptionId = null

                        var releasedBeforeLongPress = false
                        withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                                if (!change.pressed) {
                                    releasedBeforeLongPress = true
                                    return@withTimeoutOrNull
                                }
                            }
                        }

                        if (releasedBeforeLongPress) {
                            onAddClick()
                            return@awaitEachGesture
                        }

                        menuExpanded = true
                        var currentOption: FanOptionLayout? = null

                        val gestureOptions = buildFanOptions(
                            density = density,
                            centerX = centerX,
                            centerY = centerY,
                            onReceiptOcrClick = onReceiptOcrClick,
                            onInvoiceOcrClick = onInvoiceOcrClick
                        )

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                            val hoveredOption = gestureOptions.firstOrNull { option ->
                                val odx = change.position.x - option.hitCenterX
                                val ody = change.position.y - option.hitCenterY
                                odx * odx + ody * ody < option.hitRadiusPx * option.hitRadiusPx
                            }

                            if (hoveredOption?.id != currentOption?.id) {
                                currentOption = hoveredOption
                                highlightedOptionId = hoveredOption?.id
                                if (hoveredOption != null) {
                                    view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                }
                            }

                            if (!change.pressed) {
                                menuExpanded = false
                                highlightedOptionId = null
                                currentOption?.onSelected?.invoke()
                                break
                            }
                        }
                    }
                }
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "新增記帳",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FanOptionCard(
    option: FanOptionLayout,
    highlighted: Boolean,
    density: Density,
    modifier: Modifier = Modifier
) {
    val iconSize = with(density) { option.iconSizePx.toDp() }
    val rowWidth = with(density) { option.rowWidthPx.toDp() }
    val rowHeight = with(density) { option.rowHeightPx.toDp() }
    val labelWidth = with(density) { option.labelWidthPx.toDp() }
    val labelHeight = with(density) { option.labelHeightPx.toDp() }
    val labelGap = with(density) { option.labelGapPx.toDp() }

    Box(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .offset(
                    x = with(density) { option.rowLeftPx.toDp() },
                    y = with(density) { option.rowTopPx.toDp() }
                )
                .size(rowWidth, rowHeight)
        ) {
            Surface(
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(999.dp),
                tonalElevation = if (highlighted) 4.dp else 2.dp,
                shadowElevation = if (highlighted) 4.dp else 2.dp,
                modifier = Modifier
                    .size(labelWidth, labelHeight)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        option.label,
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        softWrap = false,
                        color = if (highlighted) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }

            Box(modifier = Modifier.size(labelGap))

            Surface(
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                },
                shape = CircleShape,
                tonalElevation = if (highlighted) 4.dp else 2.dp,
                shadowElevation = if (highlighted) 4.dp else 2.dp,
                modifier = Modifier.size(iconSize)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        option.icon,
                        contentDescription = option.label,
                        tint = if (highlighted) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

private data class FanOptionLayout(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val rowLeftPx: Float,
    val rowTopPx: Float,
    val rowWidthPx: Float,
    val rowHeightPx: Float,
    val labelWidthPx: Float,
    val labelHeightPx: Float,
    val labelGapPx: Float,
    val iconSizePx: Float,
    val hitCenterX: Float,
    val hitCenterY: Float,
    val hitRadiusPx: Float,
    val onSelected: () -> Unit
)

private data class FanPolarSpec(
    val radiusDp: Float,
    val angleDegrees: Float
)

private fun buildFanOptions(
    density: Density,
    centerX: Float,
    centerY: Float,
    onReceiptOcrClick: () -> Unit,
    onInvoiceOcrClick: () -> Unit
): List<FanOptionLayout> {
    val iconSizePx = with(density) { 60.dp.toPx() }
    val iconRadiusPx = iconSizePx / 2f
    val labelWidthPx = with(density) { 148.dp.toPx() }
    val labelHeightPx = with(density) { 44.dp.toPx() }
    val labelGapPx = with(density) { 4.dp.toPx() }
    val rowWidthPx = labelWidthPx + labelGapPx + iconSizePx
    val rowHeightPx = maxOf(labelHeightPx, iconSizePx)

    val specs = listOf(
        Triple("receipt", FanPolarSpec(radiusDp = 92f, angleDegrees = 255f), onReceiptOcrClick),
        Triple("invoice", FanPolarSpec(radiusDp = 92f, angleDegrees = 195f), onInvoiceOcrClick)
    )

    return specs.map { (id, polar, onSelected) ->
        val label = if (id == "receipt") "收據 OCR" else "發票辨識"
        val icon = if (id == "receipt") Icons.Filled.CameraAlt else Icons.Filled.Receipt
        val angleRadians = Math.toRadians(polar.angleDegrees.toDouble())
        val radiusPx = with(density) { polar.radiusDp.dp.toPx() }

        val iconCenterX = centerX + (cos(angleRadians) * radiusPx).toFloat()
        val iconCenterY = centerY + (sin(angleRadians) * radiusPx).toFloat()
        val rowLeftPx = iconCenterX - labelWidthPx - labelGapPx - iconRadiusPx
        val rowTopPx = iconCenterY - (rowHeightPx / 2f)

        FanOptionLayout(
            id = id,
            label = label,
            icon = icon,
            rowLeftPx = rowLeftPx,
            rowTopPx = rowTopPx,
            rowWidthPx = rowWidthPx,
            rowHeightPx = rowHeightPx,
            labelWidthPx = labelWidthPx,
            labelHeightPx = labelHeightPx,
            labelGapPx = labelGapPx,
            iconSizePx = iconSizePx,
            hitCenterX = iconCenterX,
            hitCenterY = iconCenterY,
            hitRadiusPx = iconRadiusPx * 1.2f,
            onSelected = onSelected
        )
    }
}
