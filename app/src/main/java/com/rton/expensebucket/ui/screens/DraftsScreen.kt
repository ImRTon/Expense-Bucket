package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
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
    onClearDrafts: () -> Unit = {},
    onClearNonPaymentNotifications: () -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalContext.current.resources

    val dateFormat = remember { SimpleDateFormat("MM/dd HH:mm", Locale.getDefault()) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    var showNonPaymentInbox by remember { mutableStateOf(false) }
    var showClearDraftsDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        strings.getString(R.string.pending_transactions),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.getString(R.string.back))
                    }
                },
                actions = {
                    if (drafts.isNotEmpty()) {
                        TextButton(onClick = { showClearDraftsDialog = true }) {
                            Text(strings.getString(R.string.clear_all), color = MaterialTheme.colorScheme.error)
                        }
                    }
                    BadgedBox(
                        badge = {
                            if (nonPaymentNotifications.isNotEmpty()) {
                                Badge { Text(nonPaymentNotifications.size.toString()) }
                            }
                        }
                    ) {
                        IconButton(onClick = { showNonPaymentInbox = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = strings.getString(R.string.non_payment_notifications))
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
                        strings.getString(R.string.no_pending_transactions),
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
                        strings.getString(R.string.review_captured_transactions),
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

    if (showClearDraftsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDraftsDialog = false },
            title = { Text(strings.getString(R.string.clear_pending_title)) },
            text = { Text(strings.getString(R.string.clear_pending_message, drafts.size)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearDrafts()
                        showClearDraftsDialog = false
                    }
                ) {
                    Text(strings.getString(R.string.clear_all), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDraftsDialog = false }) {
                    Text(strings.getString(R.string.cancel))
                }
            }
        )
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
    val strings = LocalContext.current.resources

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(strings.getString(R.string.non_payment_notifications)) },
        text = {
            if (notifications.isEmpty()) {
                Text(
                    strings.getString(R.string.no_records),
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
                Text(strings.getString(R.string.clear_records))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strings.getString(R.string.close))
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
    val strings = LocalContext.current.resources

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
                        text = strings.getString(R.string.draft_expense_total, CurrencyFormats.formatAmount(draft.currency, draft.amount)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                Text(
                    text = strings.getString(R.string.draft_source, category?.name?.let { strings.localizedLabel(it) } ?: strings.getString(R.string.uncategorized),
                        when (draft.source) {
                            "ocr" -> strings.getString(R.string.screenshot_ocr)
                            "notification" -> strings.getString(R.string.source_notification)
                            else -> strings.getString(R.string.source_manual)
                        }
                    ),
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
                    contentDescription = strings.getString(R.string.edit),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onConfirm) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = strings.getString(R.string.confirm),
                    tint = Color(0xFF10B981)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = strings.getString(R.string.delete),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
