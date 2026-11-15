package com.rton.expensebucket.ui.components

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.ui.util.ExpressionEvaluator

/**
 * Compact arithmetic numpad without duplicate amount header.
 * Layout:
 *   [ ÷ ] [ × ] [ − ] [ + ]
 *   [ 7 ] [ 8 ] [ 9 ] [ ⌫ ]
 *   [ 4 ] [ 5 ] [ 6 ] [   ]
 *   [ 1 ] [ 2 ] [ 3 ] [ ✓ ]  (shows "=" when expression has operators)
 *   [ . ] [ 0 ] [00 ] [   ]
 */
@Composable
fun ExpenseNumpad(
    displayAmount: String,
    onDigitClick: (String) -> Unit,
    onDotClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirm: () -> Unit,
    onEvaluate: (() -> Unit)? = null,
    includeNavigationBarInset: Boolean = true,
    bottomContentPadding: Dp = 0.dp,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current

    fun performKeyHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    fun performConfirmHaptic() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    val hasOperator = ExpressionEvaluator.hasOperator(displayAmount)
    val keyHeight = 50.dp
    val rowGap = 8.dp
    val tallKeyHeight = keyHeight * 2 + rowGap
    val topSheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = topSheetShape,
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(rowGap)
        ) {
            // ─── Top Operator Row: ÷ × − + ──────────────────────────
            val operators = listOf("÷", "×", "−", "+")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                operators.forEach { op ->
                    NumpadKey(
                        label = op,
                        height = 40.dp,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        isBold = true,
                        onClick = {
                            performKeyHaptic()
                            onDigitClick(op)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // ─── Main Grid: 3x4 digits on left + 2 tall keys on right ──
            val digitRows = listOf(
                listOf("7", "8", "9"),
                listOf("4", "5", "6"),
                listOf("1", "2", "3"),
                listOf(".", "0", "00")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(rowGap)
            ) {
                // Left 3 columns
                Column(
                    modifier = Modifier.weight(3f),
                    verticalArrangement = Arrangement.spacedBy(rowGap)
                ) {
                    digitRows.forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(rowGap)
                        ) {
                            row.forEach { key ->
                                NumpadKey(
                                    label = key,
                                    height = keyHeight,
                                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurface,
                                    onClick = {
                                        performKeyHaptic()
                                        if (key == ".") {
                                            onDotClick()
                                        } else {
                                            onDigitClick(key)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }

                // Right column: Backspace (top 2 rows) + Confirm/Equals (bottom 2 rows)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(rowGap)
                ) {
                    NumpadKey(
                        label = "⌫",
                        height = tallKeyHeight,
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        isBackspace = true,
                        onClick = {
                            performKeyHaptic()
                            onBackspaceClick()
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    NumpadKey(
                        label = if (hasOperator) "=" else "✓",
                        height = tallKeyHeight,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        isConfirm = !hasOperator,
                        isBold = true,
                        onClick = {
                            if (hasOperator && onEvaluate != null) {
                                performKeyHaptic()
                                onEvaluate()
                            } else {
                                performConfirmHaptic()
                                onConfirm()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (includeNavigationBarInset) {
                Spacer(
                    modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                )
            }
            if (bottomContentPadding > 0.dp) {
                Spacer(modifier = Modifier.height(bottomContentPadding))
            }
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    height: Dp,
    containerColor: Color,
    contentColor: Color,
    isBackspace: Boolean = false,
    isConfirm: Boolean = false,
    isBold: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 90),
        label = "numpadScale"
    )

    Box(
        modifier = modifier
            .height(height)
            .scale(scale)
            .clip(MaterialTheme.shapes.medium)
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        when {
            isBackspace -> {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "刪除",
                    tint = contentColor,
                    modifier = Modifier.size(22.dp)
                )
            }
            isConfirm -> {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "完成金額",
                    tint = contentColor,
                    modifier = Modifier.size(26.dp)
                )
            }
            else -> {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Medium,
                        fontSize = if (label == "=") 24.sp else 20.sp
                    ),
                    color = contentColor
                )
            }
        }
    }
}
