package com.rton.expensebucket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction

data class BackupTransactionSummary(val count: Int, val latestDate: Long?)

@Dao
interface CloudBackupDao {
    @Query("SELECT * FROM categories ORDER BY id")
    suspend fun categories(): List<Category>

    @Query("SELECT * FROM payment_methods ORDER BY id")
    suspend fun paymentMethods(): List<PaymentMethod>

    @Query("SELECT * FROM projects ORDER BY id")
    suspend fun projects(): List<Project>

    @Query("SELECT * FROM transactions ORDER BY id")
    suspend fun transactions(): List<Transaction>

    @Query("SELECT COUNT(*) AS count, MAX(date) AS latestDate FROM transactions")
    suspend fun transactionSummary(): BackupTransactionSummary

    @Query("SELECT * FROM notification_captures ORDER BY transactionId, packageName")
    suspend fun notificationCaptures(): List<NotificationCapture>

    @Query("DELETE FROM transactions")
    suspend fun clearTransactions()

    @Query("DELETE FROM categories")
    suspend fun clearCategories()

    @Query("DELETE FROM payment_methods")
    suspend fun clearPaymentMethods()

    @Query("DELETE FROM projects")
    suspend fun clearProjects()

    @Insert suspend fun insertCategories(rows: List<Category>)
    @Insert suspend fun insertPaymentMethods(rows: List<PaymentMethod>)
    @Insert suspend fun insertProjects(rows: List<Project>)
    @Insert suspend fun insertTransactions(rows: List<Transaction>)
    @Insert suspend fun insertNotificationCaptures(rows: List<NotificationCapture>)
}
