package com.rton.expensebucket.data.dao

import androidx.room.*
import com.rton.expensebucket.data.model.PaymentMethod
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

    @Query("SELECT * FROM payment_methods WHERE name = :name LIMIT 1")
    suspend fun getPaymentMethodByName(name: String): PaymentMethod?

    @Query("SELECT * FROM payment_methods WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPaymentMethod(): PaymentMethod?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethod(method: PaymentMethod): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaymentMethods(methods: List<PaymentMethod>)

    @Update
    suspend fun updatePaymentMethod(method: PaymentMethod)

    @Transaction
    suspend fun updatePaymentMethodDetails(method: PaymentMethod) {
        val current = getPaymentMethodById(method.id) ?: return
        updatePaymentMethod(
            method.copy(storedValueBalance = current.storedValueBalance, nfcCardFingerprint = current.nfcCardFingerprint)
        )
    }

    @Query(
        """UPDATE payment_methods
           SET storedValue_amount = :amount, storedValue_asOf = :asOf, storedValue_currency = :currency
           WHERE id = :id AND (:onlyIfUnset = 0 OR storedValue_amount IS NULL)"""
    )
    suspend fun setStoredValueBalance(
        id: Long,
        amount: Double,
        asOf: Long,
        currency: String,
        onlyIfUnset: Boolean
    ): Int

    @Query(
        """UPDATE payment_methods SET storedValue_amount = ROUND(storedValue_amount + :amount, 2)
           WHERE id = :id AND storedValue_amount IS NOT NULL"""
    )
    suspend fun topUpStoredValueBalance(id: Long, amount: Double)

    @Query("SELECT id FROM payment_methods WHERE nfcCardFingerprint = :fingerprint LIMIT 1")
    suspend fun findNfcCardOwner(fingerprint: String): Long?

    @Query(
        """UPDATE payment_methods SET nfcCardFingerprint = :fingerprint
           WHERE id = :id AND nfcCardFingerprint IS NULL AND
           NOT EXISTS (SELECT 1 FROM payment_methods WHERE nfcCardFingerprint = :fingerprint)"""
    )
    suspend fun restoreNfcCardBinding(id: Long, fingerprint: String): Int

    @Query(
        """UPDATE payment_methods SET storedValue_amount = :amount, storedValue_asOf = :asOf,
               storedValue_currency = 'JPY', nfcCardFingerprint = :fingerprint WHERE id = :id"""
    )
    suspend fun setNfcCardBalance(id: Long, amount: Double, asOf: Long, fingerprint: String)

    @Delete
    suspend fun deletePaymentMethod(method: PaymentMethod)

    @Query("DELETE FROM payment_methods WHERE parentId = :parentId")
    suspend fun deleteSubPaymentMethods(parentId: Long)

    @Query("UPDATE payment_methods SET isDefault = 0")
    suspend fun clearAllDefaults()

    @Query("UPDATE payment_methods SET isDefault = 1 WHERE id = :id")
    suspend fun setDefault(id: Long)
}
