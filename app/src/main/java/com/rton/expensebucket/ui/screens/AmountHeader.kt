package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CurrencyExchange
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.People
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.ui.theme.amountDisplay
import com.rton.expensebucket.ui.theme.amountSmall
import com.rton.expensebucket.ui.theme.semanticColors
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.ExpressionEvaluator
import com.rton.expensebucket.ui.util.IconMapper

@Composable
fun AmountHeader(
    amountText: String,
    resolvedAmount: Double?,
    isExpense: Boolean,
    selectedCategory: Category?,
    parentCategoryName: String?,
    selectedCurrency: String,
    settlementCurrency: String,
    parsedExchangeRate: Double?,
    convertedAmount: Double?,
    parsedPersonalAmount: Double?,
    personalAmountError: String?,
    isNumpadVisible: Boolean,
    onAmountClick: () -> Unit,
    onCurrencyClick: () -> Unit,
    onPersonalAmountClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val strings = LocalContext.current.resources

    val semanticColors = MaterialTheme.semanticColors
    val accentColor = if (isExpense) semanticColors.expense else semanticColors.income
    val categoryColor = selectedCategory?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outline
    val categoryLabel = when {
        selectedCategory == null -> strings.getString(R.string.no_category_selected)
        !parentCategoryName.isNullOrBlank() -> "${strings.localizedLabel(parentCategoryName)} · ${strings.localizedLabel(selectedCategory.name)}"
        else -> strings.localizedLabel(selectedCategory.name)
    }

    val hasOperator = ExpressionEvaluator.hasOperator(amountText)
    val formattedDisplayAmount = when {
        amountText.isEmpty() -> "0"
        !hasOperator && !amountText.endsWith(".") -> {
            amountText.toDoubleOrNull()?.let { num ->
                if (amountText.contains('.')) {
                    amountText
                } else {
                    "%,d".format(num.toLong())
                }
            } ?: amountText
        }
        else -> amountText
    }

    val hasSplit = isExpense && parsedPersonalAmount != null
    val hasFx = selectedCurrency != settlementCurrency

    Surface(
        onClick = onAmountClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = if (isNumpadVisible) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Row 1: Category (left) + Amount (right) ────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedCategory != null) {
                                    categoryColor.copy(alpha = 0.16f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = selectedCategory?.let { IconMapper.getIcon(it.icon) }
                                ?: Icons.Filled.Category,
                            contentDescription = categoryLabel,
                            tint = if (selectedCategory != null) {
                                categoryColor
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(21.dp)
                        )
                    }
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = if (selectedCategory != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Text(
                    text = formattedDisplayAmount,
                    style = MaterialTheme.typography.amountDisplay,
                    color = if (amountText.isEmpty()) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
                    } else {
                        accentColor
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
            }

            // ─── Optional Expression Preview Line ───────────────────
            AnimatedVisibility(visible = hasOperator && resolvedAmount != null) {
                Text(
                    text = "$amountText = ${resolvedAmount?.let { ExpressionEvaluator.formatResult(it) }.orEmpty()}",
                    style = MaterialTheme.typography.amountSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // ─── Row 2: Action Pills (Currency / FX + Split) ────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Currency & FX Rate Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (hasFx) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .clickable(onClick = onCurrencyClick)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            if (hasFx) {
                                Icon(
                                    imageVector = Icons.Filled.CurrencyExchange,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = if (hasFx) {
                                    val rateText = parsedExchangeRate?.let { CurrencyFormats.formatRate(it) } ?: "..."
                                    "$selectedCurrency → $settlementCurrency ($rateText)"
                                } else {
                                    selectedCurrency
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = if (hasFx) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = strings.getString(R.string.choose_currency),
                                tint = if (hasFx) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }

                    if (hasFx && convertedAmount != null) {
                        Text(
                            text = "≈ ${CurrencyFormats.formatAmount(settlementCurrency, convertedAmount)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Right: Split / Personal Amount Pill (for Expenses)
                if (isExpense) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(
                                if (hasSplit) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                }
                            )
                            .clickable(onClick = onPersonalAmountClick)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.People,
                                contentDescription = strings.getString(R.string.split),
                                tint = if (hasSplit) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (hasSplit) {
                                    strings.getString(R.string.my_share_amount, CurrencyFormats.formatAmount(selectedCurrency, parsedPersonalAmount!!))
                                } else {
                                    strings.getString(R.string.split)
                                },
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (hasSplit) FontWeight.SemiBold else FontWeight.Medium
                                ),
                                color = if (hasSplit) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            if (isExpense && personalAmountError != null) {
                Text(
                    text = strings.localizedLabel(personalAmountError),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
