package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.NonPaymentNotification
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.effectiveAmount
import java.text.SimpleDateFormat
import java.util.*
import com.rton.expensebucket.ui.util.CurrencyFormats

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DraftsScreen(
    drafts: List<Transaction>,
    nonPaymentNotifications: List<NonPaymentNotification>,
    categories: List<Category>,
    onConfirm: (Long) -> Unit,
    onDelete: (Transaction) -> Unit,
    onEdit: (Transaction) -> Unit,
    onClearNonPaymentNotifications: () -> Unit,
    onBack: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var showNonPaymentInbox by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "待確認記帳",
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
                actions = {
                    BadgedBox(
                        badge = {
                            if (nonPaymentNotifications.isNotEmpty()) {
                                Badge { Text(nonPaymentNotifications.size.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { showNonPaymentInbox = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "非支付通知")
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "沒有待確認的紀錄",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    Text(
                        "以下記錄來自通知或 OCR 擷取，請確認或刪除：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(drafts, key = { it.id }) { draft ->
                    DraftItem(
                        draft = draft,
                        category = categoryMap[draft.categoryId],
                        dateFormat = dateFormat,
                        onConfirm = { onConfirm(draft.id) },
                        onDelete = { onDelete(draft) },
                        onEdit = { onEdit(draft) }
                    )
                }
            }
        }
    }

    if (showNonPaymentInbox) {
        NonPaymentNotificationInboxDialog(
            notifications = nonPaymentNotifications,
            dateFormat = dateFormat,
            onClear = onClearNonPaymentNotifications,
            onDismiss = { showNonPaymentInbox = false }
        )
    }
}

@Composable
private fun NonPaymentNotificationInboxDialog(
    notifications: List<NonPaymentNotification>,
    dateFormat: SimpleDateFormat,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("非支付通知") },
        text = {
            if (notifications.isEmpty()) {
                Text(
                    "目前沒有紀錄",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(notifications, key = { it.id }) { notification ->
                        NonPaymentNotificationItem(
                            notification = notification,
                            dateFormat = dateFormat
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = notifications.isNotEmpty(),
                onClick = {
                    onClear()
                    onDismiss()
                }
            ) {
                Text("清空紀錄")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉")
            }
        }
    )
}

@Composable
private fun NonPaymentNotificationItem(
    notification: NonPaymentNotification,
    dateFormat: SimpleDateFormat
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = notification.packageName,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = notification.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = dateFormat.format(Date(notification.capturedAt)),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    }
}

@Composable
private fun DraftItem(
    draft: Transaction,
    category: Category?,
    dateFormat: SimpleDateFormat,
    onConfirm: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = CurrencyFormats.formatAmount(draft.currency, draft.effectiveAmount()),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.error
                )
                if (draft.personalAmount != null && draft.personalAmount != draft.amount) {
                    Text(
                        text = "支出總額 ${CurrencyFormats.formatAmount(draft.currency, draft.amount)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = "${category?.name ?: "未分類"} · 來源: ${
                        when (draft.source) {
                            "ocr" -> "截圖辨識"
                            "notification" -> "通知攔截"
                            else -> "手動"
                        }
                    }",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (draft.note.isNotBlank()) {
                    Text(
                        text = draft.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = dateFormat.format(Date(draft.createdAt)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }

            // Action buttons
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "編輯",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onConfirm) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "確認",
                    tint = Color(0xFF10B981)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "刪除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
