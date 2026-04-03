package com.rton.expensebucket.data.dao

import androidx.room.*
import com.rton.expensebucket.data.model.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {

    @Query("SELECT * FROM transactions WHERE isDraft = 0 ORDER BY date DESC")
    fun getAllConfirmedTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDraft = 0 AND projectId IS NULL ORDER BY date DESC")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE isDraft = 1 ORDER BY createdAt DESC")
    fun getDraftTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE projectId = :projectId AND isDraft = 0 ORDER BY date DESC")
    fun getTransactionsByProject(projectId: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date BETWEEN :startDate AND :endDate AND isDraft = 0 AND projectId IS NULL ORDER BY date DESC")
    fun getTransactionsByDateRange(startDate: Long, endDate: Long): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND isDraft = 0 ORDER BY date DESC")
    fun getTransactionsByCategory(categoryId: Long): Flow<List<Transaction>>

    @Query("SELECT SUM(COALESCE(personalAmount, amount) * exchangeRate) FROM transactions WHERE isExpense = 1 AND isDraft = 0 AND projectId IS NULL AND date BETWEEN :startDate AND :endDate")
    fun getTotalExpenseByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(COALESCE(personalAmount, amount) * exchangeRate) FROM transactions WHERE isExpense = 0 AND isDraft = 0 AND projectId IS NULL AND date BETWEEN :startDate AND :endDate")
    fun getTotalIncomeByDateRange(startDate: Long, endDate: Long): Flow<Double?>

    @Query("SELECT SUM(COALESCE(personalAmount, amount) * exchangeRate) FROM transactions WHERE projectId = :projectId AND isExpense = 1 AND isDraft = 0")
    fun getTotalExpenseByProject(projectId: Long): Flow<Double?>

    @Query("SELECT projectId, SUM(COALESCE(personalAmount, amount) * exchangeRate) as totalExpense FROM transactions WHERE isExpense = 1 AND isDraft = 0 AND projectId IS NOT NULL GROUP BY projectId")
    fun getAllProjectExpenseTotals(): Flow<List<com.rton.expensebucket.data.model.ProjectExpenseTotal>>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getTransactionById(id: Long): Transaction?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("UPDATE transactions SET isDraft = 0 WHERE id = :id")
    suspend fun confirmDraft(id: Long)
}
