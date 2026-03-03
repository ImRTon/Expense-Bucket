package com.rton.expanses.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rton.expanses.data.DefaultCategories
import com.rton.expanses.data.DefaultPaymentMethods
import com.rton.expanses.data.model.Category
import com.rton.expanses.data.model.PaymentMethod
import com.rton.expanses.data.model.Project
import com.rton.expanses.data.model.Transaction
import com.rton.expanses.data.repository.ExpansesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val repository: ExpansesRepository
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

    // ─── Current month totals ───────────────────────────────────────
    private val _currentMonthRange = MutableStateFlow(getMonthRange())

    val monthlyExpense: StateFlow<Double> =
        _currentMonthRange.flatMapLatest { (start, end) ->
            repository.getTotalExpenseByDateRange(start, end).map { it ?: 0.0 }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val monthlyIncome: StateFlow<Double> =
        _currentMonthRange.flatMapLatest { (start, end) ->
            repository.getTotalIncomeByDateRange(start, end).map { it ?: 0.0 }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

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
                    repository.insertPaymentMethods(DefaultPaymentMethods.get())
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
        viewModelScope.launch { repository.deletePaymentMethod(method) }
    }

    fun setDefaultPaymentMethod(id: Long) {
        viewModelScope.launch { repository.setDefaultPaymentMethod(id) }
    }

    // ─── Helpers ────────────────────────────────────────────────────
    private fun getMonthRange(year: Int? = null, month: Int? = null): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        if (year != null) cal.set(Calendar.YEAR, year)
        if (month != null) cal.set(Calendar.MONTH, month)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        cal.add(Calendar.MILLISECOND, -1)
        val end = cal.timeInMillis
        return start to end
    }
}
