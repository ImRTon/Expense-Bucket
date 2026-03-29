package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.effectiveConvertedAmount
import com.rton.expensebucket.ui.components.ProjectFormDialog
import com.rton.expensebucket.ui.components.QuickAddFab
import com.rton.expensebucket.ui.components.TransactionListItem
import com.rton.expensebucket.ui.components.TransactionListItemStyle
import com.rton.expensebucket.ui.components.WaterLevelSurface
import com.rton.expensebucket.ui.components.rememberWaterMotionState
import com.rton.expensebucket.ui.components.waterLevelColor
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.IconMapper
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    project: Project? = null,
    transactions: List<Transaction>,
    categories: List<Category>,
    totalExpense: Double,
    appPalette: AppPalette = AppPalette.DEFAULT,
    isDarkTheme: Boolean = false,
    onBack: () -> Unit,
    onUpdateProject: (Project) -> Unit,
    onDeleteProject: (Project) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onAddTransaction: () -> Unit,
    onReceiptOcrClick: () -> Unit,
    onInvoiceOcrClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit
) {
    val settlementCurrency = project?.defaultCurrency ?: "TWD"
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val sortedTransactions = remember(transactions) { transactions.sortedByDescending { it.date } }
    val waterMotionState = rememberWaterMotionState()
    val categoryStats = remember(transactions) {
        transactions
            .filter { it.isExpense }
            .groupBy { it.categoryId }
            .mapValues { (_, txs) -> txs.sumOf { it.effectiveConvertedAmount() } }
            .toList()
            .sortedByDescending { it.second }
    }
    val transactionEntries: List<ProjectTransactionEntry> = remember(sortedTransactions) {
        buildProjectTransactionEntries(sortedTransactions)
    }
    
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        project?.name ?: "專案明細",
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
                    if (project != null) {
                        IconButton(onClick = { showEditDialog = true }) {
                            Icon(Icons.Filled.Edit, contentDescription = "編輯專案")
                        }
                        IconButton(onClick = { showDeleteConfirmDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "刪除專案", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (project != null) {
                QuickAddFab(
                    onAddClick = onAddTransaction,
                    onReceiptOcrClick = onReceiptOcrClick,
                    onInvoiceOcrClick = onInvoiceOcrClick
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ─── Total + Budget Card ────────────────────────────
            item {
                val budget = project?.budget
                val remaining = if (budget != null) budget - totalExpense else null
                val remainingRatio = if (budget != null && budget > 0) {
                    (remaining!! / budget).toFloat().coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                val waterColor = waterLevelColor(
                    level = remainingRatio.toDouble(),
                    palette = appPalette,
                    isDarkTheme = isDarkTheme
                )
                val usedRatio = if (budget != null && budget > 0) {
                    (totalExpense / budget).toFloat().coerceAtLeast(0f)
                } else {
                    0f
                }
                val remainingAmount = if (budget != null && budget > 0) {
                    budget - totalExpense
                } else {
                    null
                }
                val statusLabel = if (remainingAmount == null) {
                    null
                } else if (remainingAmount >= 0) {
                    "剩餘"
                } else {
                    "超支"
                }
                val statusPercent = if (remainingAmount == null) {
                    null
                } else if (remainingAmount >= 0) {
                    "${(remainingRatio * 100).toInt()}%"
                } else {
                    "${(((usedRatio - 1f).coerceAtLeast(0f)) * 100).toInt()}%"
                }

                WaterLevelSurface(
                    level = remainingRatio,
                    waterColor = waterColor,
                    motionState = waterMotionState,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    val primaryTextColor = MaterialTheme.colorScheme.onSurface
                    val secondaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    val tertiaryTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "累計支出",
                                style = MaterialTheme.typography.labelLarge,
                                color = secondaryTextColor
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                CurrencyFormats.formatAmount(settlementCurrency, totalExpense),
                                style = MaterialTheme.typography.headlineMedium.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = primaryTextColor
                            )

                            // ─── Budget Info ───────────────────────
                            if (budget != null && budget > 0) {
                                val currency = project.defaultCurrency
                                val displayedRemainingAmount = remainingAmount ?: 0.0

                                Spacer(modifier = Modifier.height(12.dp))
                                Column(
                                    modifier = Modifier.padding(end = 104.dp)
                                ) {
                                    Text(
                                        "預算 $currency ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(budget)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = tertiaryTextColor
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        if (displayedRemainingAmount >= 0)
                                            "剩餘 $currency ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(displayedRemainingAmount)}"
                                        else
                                            "超支 $currency ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(-displayedRemainingAmount)}",
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontWeight = FontWeight.Medium
                                        ),
                                        color = secondaryTextColor
                                    )
                                }
                            }

                            // ─── Date range info ────────────────────
                            val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
                            if (project?.startDate != null && project.endDate != null) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${dateFormat.format(Date(project.startDate))} – ${dateFormat.format(Date(project.endDate))}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = tertiaryTextColor
                                )
                            }

                            Text(
                                "${transactions.size} 筆交易",
                                style = MaterialTheme.typography.bodySmall,
                                color = tertiaryTextColor
                            )
                        }

                        if (statusLabel != null && statusPercent != null) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 2.dp, bottom = 2.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = secondaryTextColor
                                )
                                Text(
                                    text = statusPercent,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = primaryTextColor
                                )
                            }
                        }
                    }
                }
            }

            // ─── Category Statistics ────────────────────────────
            if (categoryStats.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "類別統計",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column {
                                for (index in categoryStats.indices) {
                                    val (categoryId, total) = categoryStats[index]
                                    CategoryStatRow(
                                        category = categoryMap[categoryId],
                                        settlementCurrency = settlementCurrency,
                                        totalAmount = total,
                                        totalExpense = totalExpense
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "交易明細",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                    )
                    Surface(
                        shape = RoundedCornerShape(24.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        if (transactionEntries.isEmpty()) {
                            Text(
                                text = "這個專案還沒有交易",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp)
                            )
                        } else {
                            Column {
                                for (entry in transactionEntries) {
                                    when (entry) {
                                        is ProjectTransactionEntry.DateHeader -> {
                                            ProjectDateDivider(label = entry.label)
                                        }
                                        is ProjectTransactionEntry.TransactionRow -> {
                                            TransactionListItem(
                                                transaction = entry.transaction,
                                                category = categoryMap[entry.transaction.categoryId],
                                                settlementCurrency = settlementCurrency,
                                                onDelete = { onDeleteTransaction(entry.transaction) },
                                                onClick = { onTransactionClick(entry.transaction) },
                                                style = TransactionListItemStyle.List
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showEditDialog && project != null) {
        ProjectFormDialog(
            initialProject = project,
            onDismiss = { showEditDialog = false },
            onConfirm = { updatedProject ->
                onUpdateProject(updatedProject)
                showEditDialog = false
            }
        )
    }

    if (showDeleteConfirmDialog && project != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("刪除專案") },
            text = { Text("確定要刪除「${project.name}」嗎？這將會刪除該專案的所有設定，但專案內的交易紀錄會被保留且變回未分類。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteProject(project)
                        showDeleteConfirmDialog = false
                        onBack()
                    }
                ) {
                    Text("刪除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun CategoryStatRow(
    category: Category?,
    settlementCurrency: String,
    totalAmount: Double,
    totalExpense: Double
) {
    val ratio = if (totalExpense > 0) (totalAmount / totalExpense).toFloat() else 0f
    val categoryColor = category?.color?.let(::Color) ?: MaterialTheme.colorScheme.primary

    ListItem(
        leadingContent = {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(categoryColor.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = IconMapper.getIcon(category?.icon ?: "MoreHoriz"),
                    contentDescription = category?.name ?: "類別",
                    tint = categoryColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        },
        headlineContent = {
            Text(
                text = category?.name ?: "未知類別",
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
            )
        },
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LinearProgressIndicator(
                    progress = { ratio.coerceAtMost(1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = categoryColor,
                    trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                    drawStopIndicator = {}
                )
                Text(
                    text = "${(ratio * 100).toInt()}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                text = CurrencyFormats.formatAmount(settlementCurrency, totalAmount),
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = Color.Transparent
        )
    )
}

@Composable
private fun ProjectDateDivider(label: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(12.dp))
        HorizontalDivider(
            modifier = Modifier
                .weight(1f)
                .padding(top = 1.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
        )
    }
}

private sealed interface ProjectTransactionEntry {
    data class DateHeader(val label: String) : ProjectTransactionEntry
    data class TransactionRow(val transaction: Transaction, val showDivider: Boolean) : ProjectTransactionEntry
}

private fun buildProjectTransactionEntries(
    transactions: List<Transaction>
): List<ProjectTransactionEntry> {
    if (transactions.isEmpty()) return emptyList()

    val grouped = transactions.groupBy { transaction ->
        Calendar.getInstance().apply {
            timeInMillis = transaction.date
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    val headerFormat = SimpleDateFormat("MM/dd EEE", Locale.getDefault())

    return grouped
        .toSortedMap(compareByDescending { it })
        .flatMap { (dayStart, dayTransactions) ->
            buildList {
                add(ProjectTransactionEntry.DateHeader(headerFormat.format(Date(dayStart))))
                dayTransactions.forEachIndexed { index, transaction ->
                    add(
                        ProjectTransactionEntry.TransactionRow(
                            transaction = transaction,
                            showDivider = index < dayTransactions.lastIndex
                        )
                    )
                }
            }
        }
}
