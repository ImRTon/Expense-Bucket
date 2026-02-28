package com.rton.expanses.data.repository

import com.rton.expanses.data.dao.CategoryDao
import com.rton.expanses.data.dao.PaymentMethodDao
import com.rton.expanses.data.dao.ProjectDao
import com.rton.expanses.data.dao.TransactionDao
import com.rton.expanses.data.model.Category
import com.rton.expanses.data.model.PaymentMethod
import com.rton.expanses.data.model.Project
import com.rton.expanses.data.model.Transaction
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExpansesRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val projectDao: ProjectDao,
    private val paymentMethodDao: PaymentMethodDao
) {
    // ─── Transactions ───────────────────────────────────────────────
    fun getAllTransactions(): Flow<List<Transaction>> = transactionDao.getAllTransactions()
    fun getDraftTransactions(): Flow<List<Transaction>> = transactionDao.getDraftTransactions()
    fun getTransactionsByProject(projectId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByProject(projectId)
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByDateRange(startDate, endDate)
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByCategory(categoryId)
    fun getTotalExpenseByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        transactionDao.getTotalExpenseByDateRange(startDate, endDate)
    fun getTotalIncomeByDateRange(startDate: Long, endDate: Long): Flow<Double?> =
        transactionDao.getTotalIncomeByDateRange(startDate, endDate)
    fun getTotalExpenseByProject(projectId: Long): Flow<Double?> =
        transactionDao.getTotalExpenseByProject(projectId)
    suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getTransactionById(id)
    suspend fun insertTransaction(transaction: Transaction): Long =
        transactionDao.insertTransaction(transaction)
    suspend fun updateTransaction(transaction: Transaction) =
        transactionDao.updateTransaction(transaction)
    suspend fun deleteTransaction(transaction: Transaction) =
        transactionDao.deleteTransaction(transaction)
    suspend fun deleteTransactionById(id: Long) =
        transactionDao.deleteTransactionById(id)
    suspend fun confirmDraft(id: Long) =
        transactionDao.confirmDraft(id)

    // ─── Categories ─────────────────────────────────────────────────
    fun getAllCategories(): Flow<List<Category>> = categoryDao.getAllCategories()
    fun getCategoriesByType(isExpense: Boolean): Flow<List<Category>> =
        categoryDao.getCategoriesByType(isExpense)
    fun getParentCategories(isExpense: Boolean): Flow<List<Category>> =
        categoryDao.getParentCategories(isExpense)
    fun getSubCategories(parentId: Long): Flow<List<Category>> =
        categoryDao.getSubCategories(parentId)
    suspend fun getCategoryById(id: Long): Category? = categoryDao.getCategoryById(id)
    suspend fun insertCategory(category: Category): Long = categoryDao.insertCategory(category)
    suspend fun insertCategories(categories: List<Category>) = categoryDao.insertCategories(categories)
    suspend fun updateCategory(category: Category) = categoryDao.updateCategory(category)
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)
    suspend fun deleteSubCategories(parentId: Long) = categoryDao.deleteSubCategories(parentId)

    // ─── Projects ───────────────────────────────────────────────────
    fun getAllProjects(): Flow<List<Project>> = projectDao.getAllProjects()
    fun getActiveProjects(): Flow<List<Project>> = projectDao.getActiveProjects()
    suspend fun getProjectById(id: Long): Project? = projectDao.getProjectById(id)
    suspend fun insertProject(project: Project): Long = projectDao.insertProject(project)
    suspend fun updateProject(project: Project) = projectDao.updateProject(project)
    suspend fun deleteProject(project: Project) = projectDao.deleteProject(project)

    // ─── Payment Methods ────────────────────────────────────────────
    fun getAllPaymentMethods(): Flow<List<PaymentMethod>> = paymentMethodDao.getAllPaymentMethods()
    suspend fun getPaymentMethodById(id: Long): PaymentMethod? = paymentMethodDao.getPaymentMethodById(id)
    suspend fun getDefaultPaymentMethod(): PaymentMethod? = paymentMethodDao.getDefaultPaymentMethod()
    suspend fun insertPaymentMethod(method: PaymentMethod): Long = paymentMethodDao.insertPaymentMethod(method)
    suspend fun insertPaymentMethods(methods: List<PaymentMethod>) = paymentMethodDao.insertPaymentMethods(methods)
    suspend fun updatePaymentMethod(method: PaymentMethod) = paymentMethodDao.updatePaymentMethod(method)
    suspend fun deletePaymentMethod(method: PaymentMethod) = paymentMethodDao.deletePaymentMethod(method)
    suspend fun setDefaultPaymentMethod(id: Long) {
        paymentMethodDao.clearAllDefaults()
        paymentMethodDao.setDefault(id)
    }
}
