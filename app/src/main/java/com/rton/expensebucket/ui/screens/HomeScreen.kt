package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.clickable
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
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.BudgetTransaction
import com.rton.expensebucket.ui.components.WaterLevelCard
import com.rton.expensebucket.ui.components.TransactionListItem
import com.rton.expensebucket.ui.components.QuickAddFab
import com.rton.expensebucket.ui.viewmodel.CompareMode
import com.rton.expensebucket.ui.viewmodel.PeriodSummary
import com.rton.expensebucket.ui.viewmodel.TimePeriod
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.data.AppTheme
import androidx.compose.foundation.isSystemInDarkTheme
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    transactions: List<BudgetTransaction>,
    categories: List<Category>,
    periodData: Map<TimePeriod, PeriodSummary>,
    monthlyBudget: Double,
    compareMode: CompareMode,
    appPalette: AppPalette,
    appTheme: AppTheme,
    onCompareModeChanged: (CompareMode) -> Unit,
    selectedPeriod: TimePeriod,
    periodLabel: String,
    draftCount: Int,
    onSelectPeriod: (TimePeriod) -> Unit,
    onStepPeriod: (Int) -> Unit,
    onAddClick: () -> Unit,
    onReceiptOcrClick: () -> Unit,
    onInvoiceOcrClick: () -> Unit,
    onTransactionClick: (com.rton.expensebucket.data.model.Transaction) -> Unit,
    onDeleteTransaction: (com.rton.expensebucket.data.model.Transaction) -> Unit,
    onNavigateToDrafts: () -> Unit,
    onSetPeriodDate: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val categoryMap = remember(categories) { categories.associateBy { it.id } }
    val dateFormat = remember { SimpleDateFormat("yyyy年MM月dd日 (E)", Locale.TAIWAN) }

    // Group transactions by date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.displayDate }
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.timeInMillis
        }.toSortedMap(compareByDescending { it })
    }

    var showDatePicker by remember { mutableStateOf(false) }

    val isDarkTheme = when(appTheme) {
        AppTheme.SYSTEM -> isSystemInDarkTheme()
        AppTheme.DARK -> true
        AppTheme.LIGHT -> false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Expenses",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
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
                        Box(
                            modifier = Modifier
                                .clickable { showDatePicker = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = periodLabel,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            QuickAddFab(
                onAddClick = onAddClick,
                onReceiptOcrClick = onReceiptOcrClick,
                onInvoiceOcrClick = onInvoiceOcrClick
            )
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
                    compareMode = compareMode,
                    appPalette = appPalette,
                    isDarkTheme = isDarkTheme,
                    onCompareModeChanged = onCompareModeChanged,
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
                    key = { "${it.sourceTransactionId}_${it.displayYearMonth}_${it.amortizationIndex ?: 0}" }
                ) { budgetTransaction ->
                    val transaction = budgetTransaction.transaction
                    TransactionListItem(
                        transaction = transaction,
                        category = categoryMap[transaction.categoryId],
                        budgetTransaction = budgetTransaction,
                        onDelete = { onDeleteTransaction(transaction) },
                        onClick = { onTransactionClick(transaction) }
                    )
                }
            }

            // Bottom spacing for FAB
            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember { System.currentTimeMillis() }

        when (selectedPeriod) {
            TimePeriod.MONTH -> {
                com.rton.expensebucket.ui.components.YearMonthPickerDialog(
                    initialTimeMillis = initialMillis,
                    onDateSelected = { dateMillis ->
                        onSetPeriodDate(dateMillis)
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
            }
            TimePeriod.YEAR -> {
                com.rton.expensebucket.ui.components.YearPickerDialog(
                    initialTimeMillis = initialMillis,
                    onDateSelected = { dateMillis ->
                        onSetPeriodDate(dateMillis)
                        showDatePicker = false
                    },
                    onDismiss = { showDatePicker = false }
                )
            }
            else -> {
                val datePickerState = rememberDatePickerState()
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { dateMillis ->
                                onSetPeriodDate(dateMillis)
                            }
                            showDatePicker = false
                        }) {
                            Text("確定")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("取消")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }
        }
    }
}

