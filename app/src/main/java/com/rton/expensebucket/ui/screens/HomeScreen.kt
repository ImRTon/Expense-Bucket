package com.rton.expanses.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expanses.data.model.Category
import com.rton.expanses.data.model.Transaction
import com.rton.expanses.ui.components.WaterLevelCard
import com.rton.expanses.ui.components.TransactionListItem
import com.rton.expanses.ui.viewmodel.PeriodSummary
import com.rton.expanses.ui.viewmodel.TimePeriod
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    transactions: List<Transaction>,
    categories: List<Category>,
    periodData: Map<TimePeriod, PeriodSummary>,
    monthlyBudget: Double,
    selectedPeriod: TimePeriod,
    periodLabel: String,
    draftCount: Int,
    onSelectPeriod: (TimePeriod) -> Unit,
    onStepPeriod: (Int) -> Unit,
    onAddClick: () -> Unit,
    onTransactionClick: (Transaction) -> Unit,
    onDeleteTransaction: (Transaction) -> Unit,
    onNavigateToDrafts: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.TAIWAN) }

    // Group transactions by date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.date }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedMap(compareByDescending { it })
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Expanses",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
                    // Period navigation: ◀ label ▶
                    if (selectedPeriod != TimePeriod.ALL) {
                        IconButton(
                            onClick = { onStepPeriod(-1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ChevronLeft,
                                contentDescription = "上一期",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Text(
                            text = periodLabel,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(
                            onClick = { onStepPeriod(1) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.ChevronRight,
                                contentDescription = "下一期",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "全部",
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }

                    if (draftCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge { Text("$draftCount") }
                            }
                        ) {
                            IconButton(onClick = onNavigateToDrafts) {
                                Icon(Icons.Filled.Notifications, contentDescription = "待確認")
                            }
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
            FloatingActionButton(
                onClick = onAddClick,
                containerColor = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = "新增記帳",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ─── Water Level Card ───────────────────────────────
            item {
                WaterLevelCard(
                    periodData = periodData,
                    monthlyBudget = monthlyBudget,
                    selectedPage = TimePeriod.entries.indexOf(selectedPeriod),
                    onPageChanged = { pageIndex ->
                        val period = TimePeriod.entries[pageIndex]
                        if (period != selectedPeriod) {
                            onSelectPeriod(period)
                        }
                    },
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            // ─── Transaction List (grouped by date) ─────────────
            if (groupedTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "尚無記帳紀錄\n點擊下方 ＋ 開始記帳",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }

            groupedTransactions.forEach { (dateMillis, txList) ->
                // Date header
                item(key = "header_$dateMillis") {
                    Text(
                        text = dateFormat.format(Date(dateMillis)),
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
                    )
                }

                // Transaction items
                items(
                    items = txList,
                    key = { it.id }
                ) { transaction ->
                    TransactionListItem(
                        transaction = transaction,
                        category = categoryMap[transaction.categoryId],
                        onDelete = { onDeleteTransaction(transaction) },
                        onClick = { onTransactionClick(transaction) }
                    )
                }
            }

            // Bottom spacing for FAB
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

