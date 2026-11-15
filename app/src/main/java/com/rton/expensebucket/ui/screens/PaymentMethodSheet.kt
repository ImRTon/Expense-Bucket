package com.rton.expensebucket.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.ui.util.PaymentIconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodSheet(
    parentPaymentMethods: List<PaymentMethod>,
    childPaymentMethodsMap: Map<Long?, List<PaymentMethod>>,
    selectedPaymentMethodId: Long?,
    onSelectPaymentMethod: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialExpandedParentId = remember(selectedPaymentMethodId, parentPaymentMethods, childPaymentMethodsMap) {
        childPaymentMethodsMap.entries.firstOrNull { (_, children) ->
            children.any { it.id == selectedPaymentMethodId }
        }?.key
    }
    var expandedParentId by remember { mutableStateOf(initialExpandedParentId) }
    val rows = remember(parentPaymentMethods) { parentPaymentMethods.chunked(4) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "選擇支付工具",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
            )

            rows.forEach { rowMethods ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowMethods.forEach { parent ->
                        val children = childPaymentMethodsMap[parent.id].orEmpty()
                        val isSelected = selectedPaymentMethodId == parent.id ||
                            children.any { it.id == selectedPaymentMethodId }
                        val isExpanded = expandedParentId == parent.id

                        PaymentMethodGridTile(
                            method = parent,
                            isSelected = isSelected,
                            hasChildren = children.isNotEmpty(),
                            isExpanded = isExpanded,
                            circleSize = 46.dp,
                            iconSize = 22.dp,
                            onClick = {
                                if (children.isEmpty()) {
                                    onSelectPaymentMethod(parent.id)
                                    onDismiss()
                                } else {
                                    expandedParentId = if (isExpanded) null else parent.id
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    repeat(4 - rowMethods.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }

                val expandedParent = rowMethods.find { it.id == expandedParentId }
                val expandedChildren = expandedParent?.let { childPaymentMethodsMap[it.id] }.orEmpty()
                AnimatedVisibility(
                    visible = expandedParent != null && expandedChildren.isNotEmpty(),
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    val parentAccent = expandedParent?.let { Color(it.color) }
                        ?: MaterialTheme.colorScheme.primary
                    val childRows = remember(expandedChildren) { expandedChildren.chunked(4) }

                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        border = BorderStroke(1.dp, parentAccent.copy(alpha = 0.22f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            childRows.forEach { childRow ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    childRow.forEach { child ->
                                        val isChildSelected = selectedPaymentMethodId == child.id
                                        PaymentMethodGridTile(
                                            method = child,
                                            isSelected = isChildSelected,
                                            hasChildren = false,
                                            isExpanded = false,
                                            circleSize = 40.dp,
                                            iconSize = 19.dp,
                                            onClick = {
                                                onSelectPaymentMethod(child.id)
                                                onDismiss()
                                            },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                    repeat(4 - childRow.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentMethodGridTile(
    method: PaymentMethod,
    isSelected: Boolean,
    hasChildren: Boolean,
    isExpanded: Boolean,
    circleSize: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val methodColor = Color(method.color)

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(CircleShape)
                .background(if (isSelected) methodColor else methodColor.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = PaymentIconMapper.getIcon(method.icon),
                contentDescription = method.name,
                tint = if (isSelected) Color.White else methodColor,
                modifier = Modifier.size(iconSize)
            )
        }
        Text(
            text = when {
                hasChildren && isExpanded -> "${method.name} ▴"
                hasChildren -> "${method.name} ▾"
                else -> method.name
            },
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isSelected) methodColor else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}
