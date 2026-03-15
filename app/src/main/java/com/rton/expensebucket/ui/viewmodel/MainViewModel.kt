package com.rton.expensebucket.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rton.expensebucket.data.BudgetDataStore
import com.rton.expensebucket.data.AppTheme
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.data.AppSettingsDataStore
import com.rton.expensebucket.data.DefaultCategories
import com.rton.expensebucket.data.DefaultPaymentMethods
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import com.rton.expensebucket.util.DataExportImportManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

// ─── Enums for WaterLevelCard ───────────────────────────────────────

enum class TimePeriod(val label: String) {
    TODAY("本日"),
    WEEK("本週"),
    MONTH("本月"),
    YEAR("本年"),
    ALL("全部")
}

enum class CompareMode(val label: String) {
    EXPENSE_INCOME("支出/收入"),
    EXPENSE_BUDGET("支出/預算")
}

data class PeriodSummary(
    val expense: Double = 0.0,
    val income: Double = 0.0
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExpenseBucketRepository,
    private val budgetDataStore: BudgetDataStore,
    private val appSettingsDataStore: AppSettingsDataStore,
    private val dataExportImportManager: DataExportImportManager
) : ViewModel() {

    // ─── Categories ─────────────────────────────────────────────────
    val expenseCategories: StateFlow<List<Category>> =
        repository.getCategoriesByType(true)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val incomeCategories: StateFlow<List<Category>> =
        repository.getCategoriesByType(false)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Transactions ───────────────────────────────────────────────
    val allTransactions: StateFlow<List<Transaction>> =
        repository.getAllTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val draftTransactions: StateFlow<List<Transaction>> =
        repository.getDraftTransactions()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Selected Period (shared by WaterLevelCard & transaction list) ──
    private val _selectedPeriod = MutableStateFlow(TimePeriod.MONTH)
    val selectedPeriod: StateFlow<TimePeriod> = _selectedPeriod.asStateFlow()

    private val _periodOffset = MutableStateFlow(0)
    val periodOffset: StateFlow<Int> = _periodOffset.asStateFlow()

    fun setSelectedPeriod(period: TimePeriod) {
        _selectedPeriod.value = period
        _periodOffset.value = 0
    }

    fun stepPeriod(delta: Int) {
        if (_selectedPeriod.value == TimePeriod.ALL) return
        _periodOffset.value += delta
    }

    fun setPeriodDate(targetDateMillis: Long) {
        if (_selectedPeriod.value == TimePeriod.ALL) return

        val utcCalendar = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply { timeInMillis = targetDateMillis }
        val target = Calendar.getInstance().apply {
            set(Calendar.YEAR, utcCalendar.get(Calendar.YEAR))
            set(Calendar.MONTH, utcCalendar.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, utcCalendar.get(Calendar.DAY_OF_MONTH))
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        
        val now = Calendar.getInstance()

        val newOffset = when (_selectedPeriod.value) {
            TimePeriod.TODAY -> {
                now.set(Calendar.HOUR_OF_DAY, 0)
                now.set(Calendar.MINUTE, 0)
                now.set(Calendar.SECOND, 0)
                now.set(Calendar.MILLISECOND, 0)

                Math.round((target.timeInMillis - now.timeInMillis).toDouble() / (1000L * 60 * 60 * 24)).toInt()
            }
            TimePeriod.WEEK -> {
                val currentFirstDay = firstDayOfWeek.value

                // Find the exact start of the week for "now"
                now.firstDayOfWeek = currentFirstDay
                now.set(java.util.Calendar.DAY_OF_WEEK, currentFirstDay)
                now.set(java.util.Calendar.HOUR_OF_DAY, 0)
                now.set(java.util.Calendar.MINUTE, 0)
                now.set(java.util.Calendar.SECOND, 0)
                now.set(java.util.Calendar.MILLISECOND, 0)

                // Find the exact start of the week for "target"
                target.firstDayOfWeek = currentFirstDay
                target.set(java.util.Calendar.DAY_OF_WEEK, currentFirstDay)
                target.set(java.util.Calendar.HOUR_OF_DAY, 0)
                target.set(java.util.Calendar.MINUTE, 0)
                target.set(java.util.Calendar.SECOND, 0)
                target.set(java.util.Calendar.MILLISECOND, 0)

                // By strictly aligning both to 00:00:00 of the same day-of-week based
                // on the configured preference, we can just Math.round the difference in days over 7.
                val diffMillis = target.timeInMillis - now.timeInMillis
                Math.round(diffMillis.toDouble() / (1000L * 60 * 60 * 24 * 7)).toInt()
            }
            TimePeriod.MONTH -> {
                val yearsDiff = target.get(Calendar.YEAR) - now.get(Calendar.YEAR)
                val monthDiff = target.get(Calendar.MONTH) - now.get(Calendar.MONTH)
                yearsDiff * 12 + monthDiff
            }
            TimePeriod.YEAR -> {
                target.get(Calendar.YEAR) - now.get(Calendar.YEAR)
            }
            TimePeriod.ALL -> 0
        }

        _periodOffset.value = newOffset
    }

    // ─── First Day of Week (persisted) ──────────────────────────────
    val firstDayOfWeek: StateFlow<Int> =
        appSettingsDataStore.firstDayOfWeek
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Calendar.MONDAY)

    /** Human-readable label for the current period+offset, e.g. "2026年3月", "3/1~3/7" */
    val currentPeriodLabel: StateFlow<String> = combine(
        _selectedPeriod, _periodOffset, firstDayOfWeek
    ) { period, offset, firstDay ->
        formatPeriodLabel(period, offset, firstDay)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "")

    /** Transactions filtered by selected period + offset */
    val filteredTransactions: StateFlow<List<Transaction>> = combine(
        _selectedPeriod, _periodOffset, firstDayOfWeek
    ) { period, offset, firstDay ->
        getDateRangeForPeriod(period, offset, firstDay)
    }.flatMapLatest { (start, end) ->
        repository.getTransactionsByDateRange(start, end)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Multi-period totals for WaterLevelCard ─────────────────────
    // Reactive: selected period uses periodOffset, others use offset 0.
    /** Combined map of all period summaries for the WaterLevelCard. */
    val periodData: StateFlow<Map<TimePeriod, PeriodSummary>> = combine(
        _selectedPeriod, _periodOffset, firstDayOfWeek
    ) { selectedPeriod, offset, firstDay ->
        // Compute date ranges: the selected period uses the current offset,
        // all other periods use offset=0 (current).
        TimePeriod.entries.map { tp ->
            val tpOffset = if (tp == selectedPeriod) offset else 0
            tp to getDateRangeForPeriod(tp, tpOffset, firstDay)
        }
    }.flatMapLatest { rangesWithPeriod ->
        val flows = rangesWithPeriod.map { (tp, range) ->
            combine(
                repository.getTotalExpenseByDateRange(range.first, range.second)
                    .map { it ?: 0.0 },
                repository.getTotalIncomeByDateRange(range.first, range.second)
                    .map { it ?: 0.0 }
            ) { expense, income ->
                tp to PeriodSummary(expense, income)
            }
        }
        combine(flows) { pairs -> pairs.toMap() }
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        TimePeriod.entries.associateWith { PeriodSummary() }
    )

    // ─── Monthly Budget ─────────────────────────────────────────────
    val monthlyBudget: StateFlow<Double> =
        budgetDataStore.monthlyBudget
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun setMonthlyBudget(amount: Double) {
        viewModelScope.launch { budgetDataStore.setMonthlyBudget(amount) }
    }

    // ─── App Theme ──────────────────────────────────────────────────
    val currentTheme: StateFlow<AppTheme> =
        appSettingsDataStore.theme
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppTheme.SYSTEM)

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { appSettingsDataStore.setTheme(theme) }
    }

    // ─── App Palette ────────────────────────────────────────────────
    val currentPalette: StateFlow<AppPalette> =
        appSettingsDataStore.palette
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppPalette.DEFAULT)

    fun setPalette(palette: AppPalette) {
        viewModelScope.launch { appSettingsDataStore.setPalette(palette) }
    }

    // ─── Compare Mode (persisted) ───────────────────────────────────
    val compareMode: StateFlow<CompareMode> =
        appSettingsDataStore.compareMode.map { name ->
            try { CompareMode.valueOf(name) } catch (_: Exception) { CompareMode.EXPENSE_INCOME }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CompareMode.EXPENSE_INCOME)

    fun setCompareMode(mode: CompareMode) {
        viewModelScope.launch { appSettingsDataStore.setCompareMode(mode.name) }
    }


    fun setFirstDayOfWeek(day: Int) {
        viewModelScope.launch { appSettingsDataStore.setFirstDayOfWeek(day) }
    }

    // ─── Projects ───────────────────────────────────────────────────
    val activeProjects: StateFlow<List<Project>> =
        repository.getActiveProjects()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allProjects: StateFlow<List<Project>> =
        repository.getAllProjects()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ─── Payment Methods ────────────────────────────────────────────
    val allPaymentMethods: StateFlow<List<PaymentMethod>> =
        repository.getAllPaymentMethods()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        seedCategoriesIfEmpty()
        seedPaymentMethodsIfEmpty()
    }

    /**
     * Seed categories with parent-child hierarchy.
     * Inserts parents first, then children with correct parentId.
     */
    private fun seedCategoriesIfEmpty() {
        viewModelScope.launch {
            repository.getAllCategories().first().let { categories ->
                if (categories.isEmpty()) {
                    val seeds = DefaultCategories.getSeeds()
                    val parents = seeds.filter { it.parentName == null }
                    val children = seeds.filter { it.parentName != null }

                    // Insert parents and collect their generated IDs
                    val parentIdMap = mutableMapOf<String, Long>()
                    for (parent in parents) {
                        val id = repository.insertCategory(
                            Category(
                                name = parent.name,
                                icon = parent.icon,
                                color = parent.color,
                                isExpense = parent.isExpense,
                                sortOrder = parent.sortOrder,
                                parentId = null
                            )
                        )
                        parentIdMap[parent.name] = id
                    }

                    // Insert children with correct parentId
                    val childCategories = children.mapNotNull { child ->
                        val parentId = parentIdMap[child.parentName] ?: return@mapNotNull null
                        Category(
                            name = child.name,
                            icon = child.icon,
                            color = child.color,
                            isExpense = child.isExpense,
                            sortOrder = child.sortOrder,
                            parentId = parentId
                        )
                    }
                    repository.insertCategories(childCategories)
                }
            }
        }
    }

    private fun seedPaymentMethodsIfEmpty() {
        viewModelScope.launch {
            repository.getAllPaymentMethods().first().let { methods ->
                if (methods.isEmpty()) {
                    val seeds = DefaultPaymentMethods.getSeeds()
                    val parents = seeds.filter { it.parentName == null }
                    val children = seeds.filter { it.parentName != null }

                    // Insert parents and collect their generated IDs
                    val parentIdMap = mutableMapOf<String, Long>()
                    for (parent in parents) {
                        val id = repository.insertPaymentMethod(
                            PaymentMethod(
                                name = parent.name,
                                icon = parent.icon,
                                color = parent.color,
                                type = parent.type,
                                isDefault = parent.isDefault,
                                sortOrder = parent.sortOrder,
                                parentId = null
                            )
                        )
                        parentIdMap[parent.name] = id
                    }

                    // Insert children with correct parentId
                    val childMethods = children.mapNotNull { child ->
                        val parentId = parentIdMap[child.parentName] ?: return@mapNotNull null
                        PaymentMethod(
                            name = child.name,
                            icon = child.icon,
                            color = child.color,
                            type = child.type,
                            isDefault = child.isDefault,
                            sortOrder = child.sortOrder,
                            parentId = parentId
                        )
                    }
                    repository.insertPaymentMethods(childMethods)
                }
            }
        }
    }

    // ─── Transaction actions ────────────────────────────────────────
    fun addTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.insertTransaction(transaction) }
    }

    fun updateTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.updateTransaction(transaction) }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch { repository.deleteTransaction(transaction) }
    }

    fun confirmDraft(id: Long) {
        viewModelScope.launch { repository.confirmDraft(id) }
    }

    suspend fun getTransactionById(id: Long): Transaction? =
        repository.getTransactionById(id)

    // ─── Category actions ───────────────────────────────────────────
    fun addCategory(category: Category) {
        viewModelScope.launch { repository.insertCategory(category) }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch { repository.updateCategory(category) }
    }

    fun updateCategories(categories: List<Category>) {
        viewModelScope.launch { repository.updateCategories(categories) }
    }

    fun deleteCategory(category: Category) {
        viewModelScope.launch {
            // Delete subcategories first if it's a parent
            if (category.parentId == null) {
                repository.deleteSubCategories(category.id)
            }
            repository.deleteCategory(category)
        }
    }

    fun getSubCategories(parentId: Long): Flow<List<Category>> =
        repository.getSubCategories(parentId)

    // ─── Project actions ────────────────────────────────────────────
    fun addProject(project: Project) {
        viewModelScope.launch { repository.insertProject(project) }
    }

    fun updateProject(project: Project) {
        viewModelScope.launch { repository.updateProject(project) }
    }

    fun deleteProject(project: Project) {
        viewModelScope.launch { repository.deleteProject(project) }
    }

    fun getTransactionsByProject(projectId: Long): Flow<List<Transaction>> =
        repository.getTransactionsByProject(projectId)

    fun getProjectExpenseTotal(projectId: Long): Flow<Double?> =
        repository.getTotalExpenseByProject(projectId)

    // ─── Payment Method actions ─────────────────────────────────────
    fun addPaymentMethod(method: PaymentMethod) {
        viewModelScope.launch { repository.insertPaymentMethod(method) }
    }

    fun updatePaymentMethod(method: PaymentMethod) {
        viewModelScope.launch { repository.updatePaymentMethod(method) }
    }

    fun deletePaymentMethod(method: PaymentMethod) {
        viewModelScope.launch {
            if (method.parentId == null) {
                repository.deleteSubPaymentMethods(method.id)
            }
            repository.deletePaymentMethod(method)
        }
    }

    fun setDefaultPaymentMethod(id: Long) {
        viewModelScope.launch { repository.setDefaultPaymentMethod(id) }
    }

    fun getSubPaymentMethods(parentId: Long): Flow<List<PaymentMethod>> =
        repository.getSubPaymentMethods(parentId)

    // ─── Data Export/Import ─────────────────────────────────────────
    fun exportData(context: Context, uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = dataExportImportManager.exportToCsv(context, uri)
            if (result.isSuccess) {
                onResult(true, null)
            } else {
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    fun importData(context: Context, uri: Uri, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val result = dataExportImportManager.importFromCsv(context, uri)
            if (result.isSuccess) {
                onResult(true, "成功匯入 ${result.getOrNull()} 筆資料")
            } else {
                onResult(false, result.exceptionOrNull()?.message)
            }
        }
    }

    // ─── Date Range Helpers ─────────────────────────────────────────

    /**
     * Computes the date range for a given [period] shifted by [offset].
     * offset=0 means current, -1 means previous, +1 means next.
     */
    private fun getDateRangeForPeriod(period: TimePeriod, offset: Int, firstDayOfWeek: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        when (period) {
            TimePeriod.TODAY -> {
                cal.add(Calendar.DAY_OF_YEAR, offset)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                return Pair(start, end)
            }
            TimePeriod.WEEK -> {
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                cal.firstDayOfWeek = firstDayOfWeek
                cal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis

                cal.add(Calendar.DAY_OF_YEAR, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23)
                cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59)
                cal.set(Calendar.MILLISECOND, 999)
                val end = cal.timeInMillis
                return Pair(start, end)
            }
            TimePeriod.MONTH -> {
                cal.add(Calendar.MONTH, offset)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.MONTH, 1)
                cal.add(Calendar.MILLISECOND, -1)
                return start to cal.timeInMillis
            }
            TimePeriod.YEAR -> {
                cal.add(Calendar.YEAR, offset)
                cal.set(Calendar.MONTH, Calendar.JANUARY)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0)
                cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                val start = cal.timeInMillis
                cal.add(Calendar.YEAR, 1)
                cal.add(Calendar.MILLISECOND, -1)
                return start to cal.timeInMillis
            }
            TimePeriod.ALL -> return 0L to Long.MAX_VALUE
        }
    }

    /** Formats a human-readable label for the given period + offset. */
    private fun formatPeriodLabel(period: TimePeriod, offset: Int, firstDay: Int): String {
        val cal = Calendar.getInstance()
        return when (period) {
            TimePeriod.TODAY -> {
                cal.add(Calendar.DAY_OF_YEAR, offset)
                SimpleDateFormat("M/d (E)", Locale.TAIWAN).format(cal.time)
            }
            TimePeriod.WEEK -> {
                cal.add(Calendar.WEEK_OF_YEAR, offset)
                cal.firstDayOfWeek = firstDay
                cal.set(Calendar.DAY_OF_WEEK, firstDay)
                val startStr = SimpleDateFormat("MM/dd", Locale.getDefault()).format(cal.time)
                cal.add(Calendar.DAY_OF_YEAR, 6)
                val endStr = SimpleDateFormat("MM/dd", Locale.getDefault()).format(cal.time)
                "$startStr ~ $endStr"
            }
            TimePeriod.MONTH -> {
                cal.add(Calendar.MONTH, offset)
                SimpleDateFormat("yyyy年M月", Locale.TAIWAN).format(cal.time)
            }
            TimePeriod.YEAR -> {
                cal.add(Calendar.YEAR, offset)
                SimpleDateFormat("yyyy年", Locale.TAIWAN).format(cal.time)
            }
            TimePeriod.ALL -> "全部"
        }
    }
}
