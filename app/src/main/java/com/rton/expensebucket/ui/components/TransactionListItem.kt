package com.rton.expensebucket.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.BudgetTransaction
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.effectiveAmount
import com.rton.expensebucket.data.model.effectiveConvertedAmount
import com.rton.expensebucket.ui.theme.amountMedium
import com.rton.expensebucket.ui.theme.semanticColors
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.IconMapper

enum class TransactionListItemStyle {
    Card,
    List
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListItem(
    transaction: Transaction,
    category: Category?,
    budgetTransaction: BudgetTransaction? = null,
    paymentMethodName: String? = null,
    projectName: String? = null,
    settlementCurrency: String = "TWD",
    onDelete: () -> Unit,
    onClick: () -> Unit = {},
    style: TransactionListItemStyle = TransactionListItemStyle.Card,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentOnDelete by rememberUpdatedState(onDelete)
    val itemKey = "${transaction.id}_${budgetTransaction?.displayDate ?: transaction.date}"

    key(itemKey) {
        val itemWidth = remember { mutableFloatStateOf(0f) }
        val stateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

        val dismissState = rememberSwipeToDismissBoxState(
            positionalThreshold = { totalDistance -> totalDistance * 0.5f },
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    val currentOffset = try {
                        Math.abs(stateHolder.value?.requireOffset() ?: 0f)
                    } catch (e: Exception) {
                        0f
                    }
                    if (itemWidth.floatValue > 0 && currentOffset < itemWidth.floatValue * 0.5f) {
                        return@rememberSwipeToDismissBoxState false
                    }
                    currentOnDelete()
                    true
                } else {
                    false
                }
            }
        )
        stateHolder.value = dismissState

    val displayAmount = remember(transaction.amount, transaction.personalAmount, budgetTransaction?.displayAmount) {
        budgetTransaction?.displayAmount ?: transaction.effectiveAmount()
    }
    val originalAmountText = remember(displayAmount, transaction.currency, settlementCurrency) {
        CurrencyFormats.formatCompactAmount(transaction.currency, displayAmount, settlementCurrency)
    }
    val convertedAmountText = remember(
        transaction.amount,
        transaction.personalAmount,
        transaction.exchangeRate,
        settlementCurrency,
        budgetTransaction?.displayConvertedAmount
    ) {
        CurrencyFormats.formatCompactAmount(
            settlementCurrency,
            budgetTransaction?.displayConvertedAmount ?: transaction.effectiveConvertedAmount(),
            settlementCurrency
        )
    }

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val color by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.errorContainer
                },
                label = "swipeColor"
            )
            val iconColor by animateColorAsState(
                when (dismissState.targetValue) {
                    SwipeToDismissBoxValue.EndToStart -> MaterialTheme.colorScheme.onError
                    else -> MaterialTheme.colorScheme.onErrorContainer
                },
                label = "iconColor"
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (style == TransactionListItemStyle.Card) {
                            Modifier.clip(MaterialTheme.shapes.medium)
                        } else {
                            Modifier
                        }
                    )
                    .background(color)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "刪除",
                    tint = iconColor
                )
            }
        },
        enableDismissFromStartToEnd = false,
        modifier = modifier
    ) {
        if (style == TransactionListItemStyle.Card) {
            Card(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { size ->
                        itemWidth.floatValue = size.width.toFloat()
                    },
                shape = MaterialTheme.shapes.medium,
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                TransactionListRowContent(
                    transaction = transaction,
                    category = category,
                    budgetTransaction = budgetTransaction,
                    paymentMethodName = paymentMethodName,
                    projectName = projectName,
                    settlementCurrency = settlementCurrency,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    originalAmountText = originalAmountText,
                    convertedAmountText = convertedAmountText
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .clickable(onClick = onClick)
                    .onSizeChanged { size ->
                        itemWidth.floatValue = size.width.toFloat()
                    }
            ) {
                TransactionListRowContent(
                    transaction = transaction,
                    category = category,
                    budgetTransaction = budgetTransaction,
                    paymentMethodName = paymentMethodName,
                    projectName = projectName,
                    settlementCurrency = settlementCurrency,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    originalAmountText = originalAmountText,
                    convertedAmountText = convertedAmountText
                )
                if (showDivider) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun TransactionListRowContent(
    transaction: Transaction,
    category: Category?,
    budgetTransaction: BudgetTransaction?,
    paymentMethodName: String?,
    projectName: String?,
    settlementCurrency: String,
    modifier: Modifier,
    originalAmountText: String,
    convertedAmountText: String
) {
    val categoryColor = Color(category?.color ?: 0xFF6B7280)
    val semanticColors = MaterialTheme.semanticColors

    val subtitleLine = remember(transaction.note, paymentMethodName, projectName) {
        listOfNotNull(
            transaction.note.trim().takeIf { it.isNotEmpty() },
            paymentMethodName?.takeIf { it.isNotBlank() },
            projectName?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
    }

    val isAmortized = budgetTransaction?.isAmortizedProjection == true
    val isSplit = transaction.personalAmount != null && transaction.personalAmount != transaction.amount

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                IconMapper.getIcon(category?.icon ?: "MoreHoriz"),
                contentDescription = category?.name ?: "其他",
                tint = categoryColor,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = category?.name ?: "未分類",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (isAmortized && budgetTransaction != null) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "${budgetTransaction.amortizationIndex}/${budgetTransaction.amortizationTotalMonths}期",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                } else if (isSplit) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "分攤",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (subtitleLine.isNotEmpty()) {
                Text(
                    text = subtitleLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (transaction.isExpense) "-" else "+"}$originalAmountText",
                style = MaterialTheme.typography.amountMedium,
                color = if (transaction.isExpense) semanticColors.expense else semanticColors.income,
                textAlign = TextAlign.End
            )
            if (transaction.currency != settlementCurrency) {
                Text(
                    text = "≈ $convertedAmountText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    textAlign = TextAlign.End
                )
            }
        }
    }
}
