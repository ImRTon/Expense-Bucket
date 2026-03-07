package com.rton.expanses.data.dao

import androidx.room.*
import com.rton.expanses.data.model.PaymentMethod
import kotlinx.coroutines.flow.Flow

@Dao
interface PaymentMethodDao {
    @Query("SELECT * FROM payment_methods ORDER BY sortOrder ASC, id ASC")
    fun getAllPaymentMethods(): Flow<List<PaymentMethod>>

    @Query("SELECT * FROM payment_methods WHERE parentId IS NULL ORDER BY sortOrder ASC, id ASC")
    fun getParentPaymentMethods(): Flow<List<PaymentMethod>>

    @Query("SELECT * FROM payment_methods WHERE parentId = :parentId ORDER BY sortOrder ASC, id ASC")
    fun getSubPaymentMethods(parentId: Long): Flow<List<PaymentMethod>>

    @Query("SELECT * FROM payment_methods WHERE id = :id")
    suspend fun getPaymentMethodById(id: Long): PaymentMethod?

    @Query("SELECT * FROM payment_methods WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPaymentMethod(): PaymentMethod?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethod(method: PaymentMethod): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethods(methods: List<PaymentMethod>)

    @Update
    suspend fun updatePaymentMethod(method: PaymentMethod)

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethod)

    @Query("DELETE FROM payment_methods WHERE parentId = :parentId")
    suspend fun deleteSubPaymentMethods(parentId: Long)

    @Query("UPDATE payment_methods SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE payment_methods SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)
}
