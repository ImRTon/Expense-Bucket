package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.ui.components.ProjectFormDialog
import com.rton.expensebucket.ui.components.TransactionListItem
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    projectId: Long,
    project: Project? = null,
    transactions: List<Transaction>,
    categories: List<Category>,
    totalExpense: Double,
    onBack: () -> Unit,
    onUpdateProject: (Project) -> Unit
) {
    val currencyFormat = remember { NumberFormat.getCurrencyInstance(Locale("zh", "TW")) }
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    
    var showEditDialog by remember { mutableStateOf(false) }

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
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
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
            // ─── Total + Budget Card ────────────────────────────
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp)
                    ) {
                        Text(
                            "累計支出",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            currencyFormat.format(totalExpense),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )

                        // ─── Budget Progress ────────────────────
                        val budget = project?.budget
                        if (budget != null && budget > 0) {
                            val ratio = (totalExpense / budget).toFloat().coerceIn(0f, 1.5f)
                            val progressColor = when {
                                ratio < 0.6f -> Color(0xFF4CAF50)   // green
                                ratio < 0.85f -> Color(0xFFFFC107)  // yellow
                                else -> Color(0xFFF44336)           // red
                            }
                            val remaining = budget - totalExpense
                            val currency = project.defaultCurrency

                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "預算 $currency ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(budget)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                                )
                                Text(
                                    "${(ratio * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = progressColor
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { ratio.coerceAtMost(1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp),
                                color = progressColor,
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.1f),
                                drawStopIndicator = {}
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                if (remaining >= 0)
                                    "剩餘 $currency ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(remaining)}"
                                else
                                    "超支 $currency ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(-remaining)}",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = FontWeight.Medium
                                ),
                                color = if (remaining >= 0)
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                else
                                    Color(0xFFF44336)
                            )
                        }

                        // ─── Date range info ────────────────────
                        val dateFormat = remember { SimpleDateFormat("MM/dd", Locale.getDefault()) }
                        if (project?.startDate != null && project.endDate != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "${dateFormat.format(Date(project.startDate))} – ${dateFormat.format(Date(project.endDate))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f)
                            )
                        }

                        Text(
                            "${transactions.size} 筆交易",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // ─── Transaction List ───────────────────────────────
            item {
                Text(
                    "交易明細",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            items(transactions, key = { it.id }) { transaction ->
                TransactionListItem(
                    transaction = transaction,
                    category = categoryMap[transaction.categoryId],
                    onDelete = { /* TODO: delete from project */ }
                )
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
}
