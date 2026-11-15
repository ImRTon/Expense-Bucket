package com.rton.expensebucket.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.ui.util.PaymentIconMapper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionDetailList(
    selectedDateTime: Long,
    selectedPaymentMethod: PaymentMethod?,
    projects: List<Project>,
    selectedProject: Project?,
    isExpense: Boolean,
    amortizationEnabled: Boolean,
    amortizationMonthCount: Int,
    note: String,
    isNoteFocused: Boolean,
    onDateClick: () -> Unit,
    onPaymentMethodClick: () -> Unit,
    onProjectClick: () -> Unit,
    onAmortizationClick: () -> Unit,
    onNoteChange: (String) -> Unit,
    onNoteFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val paymentMethodLabel = selectedPaymentMethod?.name ?: "未選擇"
    val showProjectTile = projects.isNotEmpty() || selectedProject != null
    val amortizationLabel = if (amortizationEnabled && amortizationMonthCount >= 2) {
        "分 ${amortizationMonthCount} 期"
    } else {
        "不攤提"
    }

    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)

    // Keep the note card in view while focused and as the software keyboard animates up
    LaunchedEffect(isNoteFocused, imeBottom, note) {
        if (isNoteFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ─── Row 1: Date & Payment Method Bento Tiles ───────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DetailBentoTile(
                icon = Icons.Filled.CalendarToday,
                label = "日期",
                value = formatFriendlyDateTime(selectedDateTime),
                onClick = onDateClick,
                modifier = Modifier.weight(1f)
            )
            DetailBentoTile(
                icon = selectedPaymentMethod?.let { PaymentIconMapper.getIcon(it.icon) }
                    ?: Icons.Filled.CreditCard,
                iconTint = selectedPaymentMethod?.let { Color(it.color) }
                    ?: MaterialTheme.colorScheme.primary,
                label = "支付工具",
                value = paymentMethodLabel,
                onClick = onPaymentMethodClick,
                modifier = Modifier.weight(1f)
            )
        }

        // ─── Row 2: Project & Amortization Bento Tiles ──────────
        if (showProjectTile || isExpense) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (showProjectTile) {
                    DetailBentoTile(
                        icon = Icons.Filled.Luggage,
                        label = "專案",
                        value = selectedProject?.name ?: "日常帳",
                        isHighlighted = selectedProject != null,
                        onClick = onProjectClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (isExpense) {
                    DetailBentoTile(
                        icon = Icons.Filled.DateRange,
                        label = "攤提",
                        value = amortizationLabel,
                        isHighlighted = amortizationEnabled,
                        onClick = onAmortizationClick,
                        modifier = Modifier.weight(1f)
                    )
                }
                if (showProjectTile xor isExpense) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // ─── Expandable Note Input Card ─────────────────────────
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester)
                .animateContentSize()
                .clip(MaterialTheme.shapes.medium)
                .clickable {
                    focusRequester.requestFocus()
                },
            shape = MaterialTheme.shapes.medium,
            color = if (isNoteFocused) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            border = if (isNoteFocused) {
                BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            } else {
                null
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = if (isNoteFocused) Alignment.Top else Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.EditNote,
                        contentDescription = null,
                        tint = if (isNoteFocused) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(top = if (isNoteFocused) 2.dp else 0.dp)
                            .size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = if (isNoteFocused) 68.dp else 24.dp),
                        contentAlignment = Alignment.TopStart
                    ) {
                        if (note.isEmpty()) {
                            Text(
                                text = if (isNoteFocused) "輸入備註內容…" else "新增備註…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                            )
                        }
                        BasicTextField(
                            value = note,
                            onValueChange = onNoteChange,
                            singleLine = !isNoteFocused,
                            minLines = if (isNoteFocused) 3 else 1,
                            maxLines = if (isNoteFocused) 5 else 1,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = { focusManager.clearFocus() }
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { onNoteFocusChanged(it.isFocused) }
                        )
                    }
                    if (isNoteFocused) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .clickable { focusManager.clearFocus() }
                                .padding(horizontal = 12.dp, vertical = 5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "完成",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBentoTile(
    icon: ImageVector,
    label: String,
    value: String,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val activeTint = if (isHighlighted) MaterialTheme.colorScheme.primary else iconTint

    Surface(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = if (isHighlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(activeTint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = activeTint,
                    modifier = Modifier.size(17.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isHighlighted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
