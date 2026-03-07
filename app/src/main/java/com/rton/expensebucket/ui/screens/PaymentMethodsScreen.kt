package com.rton.expensebucket.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.ui.util.PaymentIconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    paymentMethods: List<PaymentMethod>,
    onAdd: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onSetDefault: (Long) -> Unit,
    onBack: () -> Unit = {}
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedParentIdForAdd by remember { mutableStateOf<Long?>(null) }

    // Group methods: Parents first, then map parentId to their children
    val parents = remember(paymentMethods) {
        paymentMethods.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val childrenMap = remember(paymentMethods) {
        paymentMethods.filter { it.parentId != null }.groupBy { it.parentId }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "支付工具",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    selectedParentIdForAdd = null
                    showAddDialog = true
                },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("新增支付工具") },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            parents.forEach { parent ->
                item(key = "parent_${parent.id}") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            parent.name,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            selectedParentIdForAdd = parent.id
                            showAddDialog = true
                        }) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = "新增子項目",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                val children = childrenMap[parent.id]?.sortedBy { it.sortOrder } ?: emptyList()
                if (children.isEmpty()) {
                    item {
                        Text(
                            "尚無子項目",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                        )
                    }
                } else {
                    items(children, key = { "child_${it.id}" }) { method ->
                        PaymentMethodItem(
                            method = method,
                            onDelete = { onDelete(method) },
                            onSetDefault = { onSetDefault(method.id) }
                        )
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showAddDialog) {
        AddPaymentMethodDialog(
            parentId = selectedParentIdForAdd,
            onDismiss = { showAddDialog = false },
            onConfirm = { method ->
                onAdd(method)
                showAddDialog = false
            }
        )
    }
}

@Composable
private fun PaymentMethodItem(
    method: PaymentMethod,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    val color = Color(method.color)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    PaymentIconMapper.getIcon(method.icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        method.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (method.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "預設",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    when (method.type) {
                        "cash" -> "現金"
                        "credit" -> "信用卡"
                        "epay" -> "電子支付"
                        else -> "其他"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!method.isDefault) {
                TextButton(onClick = onSetDefault) {
                    Text("設為預設", style = MaterialTheme.typography.labelMedium)
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "刪除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPaymentMethodDialog(
    parentId: Long?,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("epay") }
    var selectedIcon by remember { mutableStateOf("other") }
    var selectedColorIndex by remember { mutableStateOf(0) }

    val types = listOf("cash" to "現金", "credit" to "信用卡", "epay" to "電子支付", "other" to "其他")
    val presetColors = listOf(
        0xFF4ADE80, 0xFF60A5FA, 0xFFF59E0B, 0xFFEC4899,
        0xFF818CF8, 0xFF10B981, 0xFFFF8C00, 0xFF6B7280
    )

    // Icon picker — group by type relevance
    val iconChoices = listOf(
        "cash", "credit_card", "jko", "linepay", "easycard",
        "applepay", "googlepay", "pi", "pay_full", "bank_transfer",
        "richart", "esun", "cathay", "other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新增支付工具") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名稱") },
                    placeholder = { Text(if (parentId != null) "例如：街口支付" else "例如：虛擬貨幣") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (parentId == null) {
                    // Type picker for parents only
                    Text("類型", style = MaterialTheme.typography.labelLarge)
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        types.forEachIndexed { index, (key, label) ->
                            SegmentedButton(
                                selected = selectedType == key,
                                onClick = { selectedType = key },
                                shape = SegmentedButtonDefaults.itemShape(index, types.size),
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }

                // Icon picker
                Text("圖示", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconChoices.forEach { key ->
                        val isSelected = selectedIcon == key
                        IconButton(
                            onClick = { selectedIcon = key },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                        ) {
                            Icon(
                                PaymentIconMapper.getIcon(key),
                                contentDescription = key,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Color picker
                Text("顏色", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEachIndexed { index, colorLong ->
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .then(
                                    if (isSelected) Modifier.padding(4.dp) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { selectedColorIndex = index },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        PaymentMethod(
                            name = name.trim(),
                            icon = selectedIcon,
                            color = presetColors[selectedColorIndex],
                            type = selectedType,
                            parentId = parentId
                        )
                    )
                },
                enabled = name.isNotBlank()
            ) { Text("新增") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
