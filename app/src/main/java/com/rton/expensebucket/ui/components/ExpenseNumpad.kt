package com.rton.expensebucket.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.background
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.ui.util.ExpressionEvaluator

/**
 * Custom 9-pad numpad with arithmetic operator support.
 * Layout:
 *   [ 7 ] [ 8 ] [ 9 ] [ ÷ ]
 *   [ 4 ] [ 5 ] [ 6 ] [ × ]
 *   [ 1 ] [ 2 ] [ 3 ] [ − ]
 *   [ . ] [ 0 ] [ ⌫ ] [ + ]
 *   [        ✓ 完成        ]
 */
@Composable
fun ExpenseNumpad(
    displayAmount: String,
    categories: List<com.rton.expensebucket.data.model.Category> = emptyList(),
    selectedCategory: com.rton.expensebucket.data.model.Category? = null,
    onCategorySelected: (com.rton.expensebucket.data.model.Category) -> Unit = {},
    onDigitClick: (String) -> Unit,
    onDotClick: () -> Unit,
    onBackspaceClick: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    // Calculate result preview if expression has operators
    val hasOperator = displayAmount.any { it in "+−×÷" }
    val previewResult = if (hasOperator) {
        ExpressionEvaluator.evaluate(displayAmount)?.let {
            "= ${ExpressionEvaluator.formatResult(it)}"
        }
    } else null

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .imePadding(),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
        ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            // Combining solid surface color with the semi-transparent variant overlay
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // ─── Amount Display ─────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = if (displayAmount.isEmpty()) "0" else displayAmount,
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.5).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Preview result when expression has operators
                    if (previewResult != null) {
                        Text(
                            text = previewResult,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            textAlign = TextAlign.End,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                // ─── Numpad Grid (4×4 with operators) ───────────────────
                val grid = listOf(
                    listOf("7", "8", "9", "÷"),
                    listOf("4", "5", "6", "×"),
                    listOf("1", "2", "3", "−"),
                    listOf(".", "0", "⌫", "+")
                )

                grid.forEach { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        row.forEach { key ->
                            val isOperator = key in listOf("+", "−", "×", "÷")
                            val isBackspace = key == "⌫"

                            NumpadKey(
                                label = key,
                                isOperator = isOperator,
                                isBackspace = isBackspace,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    when (key) {
                                        "⌫" -> onBackspaceClick()
                                        "." -> onDotClick()
                                        in listOf("+", "−", "×", "÷") -> onDigitClick(key)
                                        else -> onDigitClick(key)
                                    }
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // ─── Confirm Button ─────────────────────────────────────
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .padding(horizontal = 4.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(
                        Icons.Filled.Check,
                        contentDescription = "完成",
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "完成",
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
            }
            }
        }
            Spacer(
                modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    }
}

@Composable
private fun NumpadKey(
    label: String,
    isOperator: Boolean = false,
    isBackspace: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "numpadScale"
    )

    val bgColor = when {
        isBackspace -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
        isOperator -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isBackspace -> MaterialTheme.colorScheme.error
        isOperator -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .height(52.dp)
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true),
                onClick = onClick
            ),
        shape = RoundedCornerShape(12.dp),
        color = bgColor,
        tonalElevation = 1.dp
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isBackspace) {
                Icon(
                    Icons.AutoMirrored.Filled.Backspace,
                    contentDescription = "刪除",
                    tint = textColor,
                    modifier = Modifier.size(22.dp)
                )
            } else {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = if (isOperator) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = textColor
                )
            }
        }
    }
}
