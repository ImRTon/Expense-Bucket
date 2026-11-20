package com.rton.expensebucket.data.dao

import androidx.room.*
import androidx.room.Transaction as RoomTransaction
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.ocr.NotificationDeduplicator
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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNfcTransactions(transactions: List<Transaction>): List<Long>

    @Query("SELECT * FROM transactions WHERE paymentMethodId = :methodId AND currency = 'JPY' COLLATE NOCASE AND isExpense = 1 AND date >= :since")
    suspend fun getNfcImportCandidates(methodId: Long, since: Long): List<Transaction>

    @Query("SELECT nfcReference FROM transactions WHERE nfcReference IN (:references)")
    suspend fun getImportedNfcReferences(references: List<String>): List<String>

    @Query("SELECT * FROM notification_captures WHERE capturedAt >= :recentSince ORDER BY capturedAt DESC")
    suspend fun getRecentNotificationCaptures(recentSince: Long): List<NotificationCapture>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotificationCapture(capture: NotificationCapture)

    @Query("DELETE FROM notification_captures WHERE capturedAt < :before")
    suspend fun deleteOldNotificationCaptures(before: Long)

    @Query("UPDATE transactions SET paymentMethodId = :paymentMethodId WHERE id = :id AND isDraft = 1 AND paymentMethodId IS NULL")
    suspend fun fillDraftPaymentMethod(id: Long, paymentMethodId: Long)

    @Query("DELETE FROM transactions WHERE isDraft = 1")
    suspend fun deleteAllDrafts()

    @RoomTransaction
    suspend fun insertNotificationDraftIfAbsent(
        transaction: Transaction,
        capture: NotificationCapture
    ): Long? {
        val recent = getRecentNotificationCaptures(capture.capturedAt - NotificationDeduplicator.MATCH_WINDOW_MS)
        val duplicateId = NotificationDeduplicator.findDuplicate(capture, recent)
        if (duplicateId != null) {
            insertNotificationCapture(capture.copy(transactionId = duplicateId))
            transaction.paymentMethodId?.let { fillDraftPaymentMethod(duplicateId, it) }
            return null
        }
        val id = insertTransaction(transaction)
        insertNotificationCapture(capture.copy(transactionId = id))
        deleteOldNotificationCaptures(capture.capturedAt - 24 * 60 * 60 * 1000L)
        return id
    }

    @Update
    suspend fun updateTransactionRow(transaction: Transaction)

    @RoomTransaction
    suspend fun updateTransaction(transaction: Transaction) {
        val previous = getTransactionById(transaction.id) ?: return
        val paymentMethodChangedAt = when {
            transaction.paymentMethodId == null -> null
            transaction.paymentMethodId != previous.paymentMethodId -> System.currentTimeMillis()
            else -> previous.paymentMethodChangedAt
        }
        updateTransactionRow(transaction.copy(paymentMethodChangedAt = paymentMethodChangedAt))
    }

    @Delete
    suspend fun deleteTransaction(transaction: Transaction)

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("UPDATE transactions SET isDraft = 0 WHERE id = :id")
    suspend fun confirmDraft(id: Long)
}
