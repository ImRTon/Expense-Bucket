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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.cos
import kotlin.math.roundToInt
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

    val menuSize = 220.dp
    val mainFabSize = 64.dp

    Box(
        modifier = Modifier
            .size(menuSize)
            .pointerInput(onAddClick, onReceiptOcrClick, onInvoiceOcrClick) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
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

                    val mainRadiusPx = with(density) { mainFabSize.toPx() / 2f }
                    val mainCenterX = size.width - mainRadiusPx
                    val mainCenterY = size.height - mainRadiusPx
                    val fanOptions = buildFanOptions(
                        density = density,
                        centerX = mainCenterX,
                        centerY = mainCenterY,
                        onReceiptOcrClick = onReceiptOcrClick,
                        onInvoiceOcrClick = onInvoiceOcrClick
                    )

                    menuExpanded = true
                    var currentOption: FanOptionLayout? = null

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: continue

                        val hoveredOption = fanOptions.firstOrNull { option ->
                            option.hitRect.contains(change.position)
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
            },
        contentAlignment = Alignment.BottomEnd
    ) {
        val mainRadiusPx = with(density) { mainFabSize.toPx() / 2f }
        val fanOptions = remember(onReceiptOcrClick, onInvoiceOcrClick, density) {
            val center = with(density) { menuSize.toPx() } - mainRadiusPx
            buildFanOptions(
                density = density,
                centerX = center,
                centerY = center,
                onReceiptOcrClick = onReceiptOcrClick,
                onInvoiceOcrClick = onInvoiceOcrClick
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopStart
        ) {
            fanOptions.forEach { option ->
                AnimatedVisibility(
                    visible = menuExpanded,
                    enter = fadeIn() + scaleIn(animationSpec = spring()),
                    exit = fadeOut() + scaleOut(animationSpec = spring()),
                    modifier = Modifier.offset {
                        IntOffset(option.groupLeftPx.roundToInt(), option.groupTopPx.roundToInt())
                    }
                ) {
                    FanOptionCard(
                        option = option,
                        highlighted = highlightedOptionId == option.id,
                        density = density
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .size(mainFabSize)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                tonalElevation = 6.dp,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxSize()
            ) {}
            Icon(
                Icons.Filled.Add,
                contentDescription = "新增記帳",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

@Composable
private fun FanOptionCard(
    option: FanOptionLayout,
    highlighted: Boolean,
    density: Density
) {
    val groupWidth = with(density) { option.groupWidthPx.toDp() }
    val groupHeight = with(density) { option.groupHeightPx.toDp() }
    val labelLeft = with(density) { option.labelLeftPx.toDp() }
    val labelTop = with(density) { option.labelTopPx.toDp() }
    val labelWidth = with(density) { option.labelWidthPx.toDp() }
    val labelHeight = with(density) { option.labelHeightPx.toDp() }
    val iconLeft = with(density) { option.iconLeftPx.toDp() }
    val iconTop = with(density) { option.iconTopPx.toDp() }
    val iconSize = with(density) { option.iconSizePx.toDp() }

    Box(modifier = Modifier.size(groupWidth, groupHeight)) {
        Surface(
            color = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            shape = RoundedCornerShape(999.dp),
            tonalElevation = if (highlighted) 8.dp else 4.dp,
            shadowElevation = if (highlighted) 8.dp else 4.dp,
            modifier = Modifier
                .offset(x = labelLeft, y = labelTop)
                .size(labelWidth, labelHeight)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    option.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (highlighted) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        Surface(
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
            shape = CircleShape,
            tonalElevation = if (highlighted) 8.dp else 5.dp,
            shadowElevation = if (highlighted) 8.dp else 5.dp,
            modifier = Modifier
                .offset(x = iconLeft, y = iconTop)
                .size(iconSize)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    option.icon,
                    contentDescription = option.label,
                    tint = if (highlighted) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    }
                )
            }
        }
    }
}

private data class FanOptionLayout(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val groupLeftPx: Float,
    val groupTopPx: Float,
    val groupWidthPx: Float,
    val groupHeightPx: Float,
    val labelLeftPx: Float,
    val labelTopPx: Float,
    val labelWidthPx: Float,
    val labelHeightPx: Float,
    val iconLeftPx: Float,
    val iconTopPx: Float,
    val iconSizePx: Float,
    val hitRect: Rect,
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
    val iconSizePx = with(density) { 48.dp.toPx() }
    val iconRadiusPx = iconSizePx / 2f
    val labelWidthPx = with(density) { 96.dp.toPx() }
    val labelHeightPx = with(density) { 36.dp.toPx() }
    val labelGapPx = with(density) { 10.dp.toPx() }

    val specs = listOf(
        Triple("receipt", FanPolarSpec(radiusDp = 64f, angleDegrees = 235f), onReceiptOcrClick),
        Triple("invoice", FanPolarSpec(radiusDp = 94f, angleDegrees = 198f), onInvoiceOcrClick)
    )

    return specs.map { (id, polar, onSelected) ->
        val label = if (id == "receipt") "收據 OCR" else "發票辨識"
        val icon = if (id == "receipt") Icons.Filled.CameraAlt else Icons.Filled.Receipt
        val angleRadians = Math.toRadians(polar.angleDegrees.toDouble())
        val radiusPx = with(density) { polar.radiusDp.dp.toPx() }

        val iconCenterX = centerX + (cos(angleRadians) * radiusPx).toFloat()
        val iconCenterY = centerY + (sin(angleRadians) * radiusPx).toFloat()

        val labelCenterX = iconCenterX + (cos(angleRadians) * (iconRadiusPx + labelGapPx + labelWidthPx / 2f)).toFloat()
        val labelCenterY = iconCenterY + (sin(angleRadians) * (iconRadiusPx + labelGapPx + labelHeightPx / 2f)).toFloat()

        val iconLeftAbs = iconCenterX - iconRadiusPx
        val iconTopAbs = iconCenterY - iconRadiusPx
        val labelLeftAbs = labelCenterX - labelWidthPx / 2f
        val labelTopAbs = labelCenterY - labelHeightPx / 2f

        val groupLeft = minOf(iconLeftAbs, labelLeftAbs)
        val groupTop = minOf(iconTopAbs, labelTopAbs)
        val groupRight = maxOf(iconLeftAbs + iconSizePx, labelLeftAbs + labelWidthPx)
        val groupBottom = maxOf(iconTopAbs + iconSizePx, labelTopAbs + labelHeightPx)

        FanOptionLayout(
            id = id,
            label = label,
            icon = icon,
            groupLeftPx = groupLeft,
            groupTopPx = groupTop,
            groupWidthPx = groupRight - groupLeft,
            groupHeightPx = groupBottom - groupTop,
            labelLeftPx = labelLeftAbs - groupLeft,
            labelTopPx = labelTopAbs - groupTop,
            labelWidthPx = labelWidthPx,
            labelHeightPx = labelHeightPx,
            iconLeftPx = iconLeftAbs - groupLeft,
            iconTopPx = iconTopAbs - groupTop,
            iconSizePx = iconSizePx,
            hitRect = Rect(groupLeft, groupTop, groupRight, groupBottom),
            onSelected = onSelected
        )
    }
}
