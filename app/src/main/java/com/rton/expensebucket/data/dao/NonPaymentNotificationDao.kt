package com.rton.expensebucket.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.rton.expensebucket.data.model.NonPaymentNotification
import kotlinx.coroutines.flow.Flow

@Dao
interface NonPaymentNotificationDao {

    @Query("SELECT * FROM non_payment_notifications ORDER BY capturedAt DESC")
    fun getAll(): Flow<List<NonPaymentNotification>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(notification: NonPaymentNotification): Long

    @Query("DELETE FROM non_payment_notifications")
    suspend fun clearAll()
}
