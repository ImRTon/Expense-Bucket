package com.rton.expensebucket.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.effectiveAmount
import com.rton.expensebucket.data.model.effectiveConvertedAmount
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.IconMapper
import java.text.SimpleDateFormat
import java.util.*

/**
 * A single transaction list item with category icon, note, amount, and swipe-to-delete support.
 */
enum class TransactionListItemStyle {
    Card,
    List
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionListItem(
    transaction: Transaction,
    category: Category?,
    settlementCurrency: String = "TWD",
    onDelete: () -> Unit,
    onClick: () -> Unit = {},
    style: TransactionListItemStyle = TransactionListItemStyle.Card,
    showDivider: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

    val itemWidth = remember { mutableFloatStateOf(0f) }
    val stateHolder = remember { mutableStateOf<SwipeToDismissBoxState?>(null) }

    val dismissState = rememberSwipeToDismissBoxState(
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }, // 至少滑超過一半的距離

        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                // Ignore fast fling if the physical distance hasn't met the 50% requirement
                val currentOffset = try { Math.abs(stateHolder.value?.requireOffset() ?: 0f) } catch (e: Exception) { 0f }
                if (itemWidth.floatValue > 0 && currentOffset < itemWidth.floatValue * 0.5f) {
                    return@rememberSwipeToDismissBoxState false
                }
                onDelete()
                true
            } else false
        }
    )
    stateHolder.value = dismissState

    val displayAmount = remember(transaction.amount, transaction.personalAmount) {
        transaction.effectiveAmount()
    }
    val originalAmountText = remember(displayAmount, transaction.currency) {
        CurrencyFormats.formatAmount(transaction.currency, displayAmount)
    }
    val convertedAmountText = remember(transaction.amount, transaction.personalAmount, transaction.exchangeRate, settlementCurrency) {
        CurrencyFormats.formatAmount(settlementCurrency, transaction.effectiveConvertedAmount())
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
                            Modifier.clip(RoundedCornerShape(16.dp))
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
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                TransactionListRowContent(
                    transaction = transaction,
                    category = category,
                    settlementCurrency = settlementCurrency,
                    modifier = Modifier
                        .padding(16.dp),
                    timeFormat = timeFormat,
                    originalAmountText = originalAmountText,
                    convertedAmountText = convertedAmountText
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onClick)
                    .onSizeChanged { size ->
                        itemWidth.floatValue = size.width.toFloat()
                    },
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column {
                    TransactionListRowContent(
                        transaction = transaction,
                        category = category,
                        settlementCurrency = settlementCurrency,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        timeFormat = timeFormat,
                        originalAmountText = originalAmountText,
                        convertedAmountText = convertedAmountText
                    )
                    if (showDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 74.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
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
    settlementCurrency: String,
    modifier: Modifier,
    timeFormat: SimpleDateFormat,
    originalAmountText: String,
    convertedAmountText: String
) {
    val categoryColor = Color(category?.color ?: 0xFF6B7280)

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(categoryColor.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                IconMapper.getIcon(category?.icon ?: "MoreHoriz"),
                contentDescription = category?.name ?: "其他",
                tint = categoryColor,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category?.name ?: "未分類",
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (transaction.note.isNotBlank()) {
                Text(
                    text = transaction.note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = timeFormat.format(Date(transaction.date)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            if (transaction.personalAmount != null && transaction.personalAmount != transaction.amount) {
                Text(
                    text = "支出總額 ${CurrencyFormats.formatAmount(transaction.currency, transaction.amount)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            if (transaction.currency != settlementCurrency || transaction.exchangeRate != 1.0) {
                Text(
                    text = "約 $convertedAmountText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
        }

        Text(
            text = "${if (transaction.isExpense) "-" else "+"}$originalAmountText",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold
            ),
            color = if (transaction.isExpense)
                MaterialTheme.colorScheme.error
            else
                Color(0xFF10B981)
        )
    }
}
