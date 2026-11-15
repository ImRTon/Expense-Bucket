package com.rton.expensebucket.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.data.AppTheme
import com.rton.expensebucket.data.model.BudgetTransaction
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.ui.components.QuickAddFab
import com.rton.expensebucket.ui.components.TransactionListItem
import com.rton.expensebucket.ui.components.TransactionListItemStyle
import com.rton.expensebucket.ui.components.WaterLevelCard
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.viewmodel.CompareMode
import com.rton.expensebucket.ui.viewmodel.PeriodSummary
import com.rton.expensebucket.ui.viewmodel.TimePeriod
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    transactions: List<BudgetTransaction>,
    categories: List<Category>,
    paymentMethods: List<PaymentMethod> = emptyList(),
    projects: List<Project> = emptyList(),
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
    val paymentMethodMap = remember(paymentMethods) { paymentMethods.associateBy { it.id } }
    val projectMap = remember(projects) { projects.associateBy { it.id } }

    // Group transactions by date
    val groupedTransactions = remember(transactions) {
        transactions.groupBy { tx ->
            AddTransactionFormLogic.startOfDay(tx.displayDate)
        }.toSortedMap(compareByDescending { it })
    }

    var showDatePicker by remember { mutableStateOf(false) }

    val isDarkTheme = when (appTheme) {
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
            verticalArrangement = Arrangement.spacedBy(14.dp)
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
                    }
                )
            }

            // ─── Empty State ────────────────────────────────────
            if (groupedTransactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                            Text(
                                text = "尚無記帳紀錄",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                textAlign = TextAlign.Center
                            )
                            FilledTonalButton(
                                onClick = onAddClick,
                                shape = MaterialTheme.shapes.medium
                            ) {
                                Icon(
                                    Icons.Filled.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("記第一筆")
                            }
                        }
                    }
                }
            }

            // ─── Grouped Day Cards ──────────────────────────────
            groupedTransactions.forEach { (dateMillis, txList) ->
                val dailyExpense = txList
                    .filter { it.transaction.isExpense }
                    .sumOf { it.displayConvertedAmount }
                val dailyIncome = txList
                    .filter { !it.transaction.isExpense }
                    .sumOf { it.displayConvertedAmount }

                item(key = "day_group_$dateMillis") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Date header: Date + subtle daily summary on the left (keeps right column clear for item amounts)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = formatHomeDateHeader(dateMillis),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            val summaryText = buildList {
                                if (dailyExpense > 0) {
                                    add("支出 ${CurrencyFormats.formatCompactAmount("TWD", dailyExpense)}")
                                }
                                if (dailyIncome > 0) {
                                    add("收入 ${CurrencyFormats.formatCompactAmount("TWD", dailyIncome)}")
                                }
                            }.joinToString(" · ")

                            if (summaryText.isNotEmpty()) {
                                Text(
                                    text = summaryText,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }

                        // Unified card container for the day's transactions
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(MaterialTheme.shapes.medium),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                txList.forEach { budgetTransaction ->
                                    val transaction = budgetTransaction.transaction
                                    key("${transaction.id}_${budgetTransaction.displayDate}") {
                                        TransactionListItem(
                                            transaction = transaction,
                                            category = categoryMap[transaction.categoryId],
                                            budgetTransaction = budgetTransaction,
                                            paymentMethodName = transaction.paymentMethodId?.let { paymentMethodMap[it]?.name },
                                            projectName = transaction.projectId?.let { projectMap[it]?.name },
                                            style = TransactionListItemStyle.List,
                                            showDivider = false,
                                            onDelete = { onDeleteTransaction(transaction) },
                                            onClick = { onTransactionClick(transaction) }
                                        )
                                    }
                                }
                            }
                        }
                    }
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

internal fun formatHomeDateHeader(dateMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    return when (getDayOffsetFromToday(dateMillis, nowMillis)) {
        0 -> "今天"
        -1 -> "昨天"
        else -> {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val targetCal = Calendar.getInstance().apply { timeInMillis = dateMillis }
            val pattern = if (nowCal.get(Calendar.YEAR) == targetCal.get(Calendar.YEAR)) {
                "MM/dd E"
            } else {
                "yyyy/MM/dd E"
            }
            SimpleDateFormat(pattern, Locale.TAIWAN).format(Date(dateMillis))
        }
    }
}
