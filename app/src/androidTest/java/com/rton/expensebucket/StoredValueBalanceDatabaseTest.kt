package com.rton.expensebucket

import android.net.Uri
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.StoredValueBalance
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import com.rton.expensebucket.util.DataExportImportManager
import com.rton.expensebucket.util.StoredValueCalculator
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class StoredValueBalanceDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun repository(database: ExpenseBucketDatabase) = ExpenseBucketRepository(
        database.transactionDao(), database.categoryDao(), database.projectDao(),
        database.paymentMethodDao(), database.nonPaymentNotificationDao()
    )

    @Test
    fun changingCashToStoredValue_updatesBalanceAndSurvivesCsvRestore() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        val restoredDatabase = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        val backup = File.createTempFile("stored_value_balance", ".csv", context.cacheDir)
        try {
            val repo = repository(database)
            val cashId = repo.insertPaymentMethod(PaymentMethod(name = "現金", icon = "Payments"))
            val cardId = repo.insertPaymentMethod(
                PaymentMethod(
                    name = "王品儲值卡", icon = "other",
                    storedValueBalance = StoredValueBalance(500.0, 2000, "TWD")
                )
            )
            val expense = Transaction(amount = 120.0, date = 1000, paymentMethodId = cashId)
            val transactionId = repo.insertTransaction(expense)
            val card = requireNotNull(repo.getPaymentMethodById(cardId))

            assertEquals(500.0, StoredValueCalculator.currentBalance(card, listOf(expense))!!, 0.0)
            repo.updateTransaction(expense.copy(id = transactionId, paymentMethodId = cardId))
            val reassigned = requireNotNull(repo.getTransactionById(transactionId))
            assertEquals(expense.date, reassigned.date)
            assertEquals(380.0, StoredValueCalculator.currentBalance(card, listOf(reassigned))!!, 0.0)

            repo.updateTransaction(reassigned.copy(note = "晚餐", paymentMethodChangedAt = null))
            assertEquals(reassigned.paymentMethodChangedAt, repo.getTransactionById(transactionId)?.paymentMethodChangedAt)

            val restoredRepo = repository(restoredDatabase)
            val uri = Uri.fromFile(backup)
            DataExportImportManager(repo).exportToCsv(context, uri).getOrThrow()
            DataExportImportManager(restoredRepo).importFromCsv(context, uri).getOrThrow()
            val restoredCard = requireNotNull(restoredRepo.getPaymentMethodByName(card.name))
            val restored = restoredRepo.getAllConfirmedTransactions().first()
            assertEquals(reassigned.paymentMethodChangedAt, restored.single().paymentMethodChangedAt)
            assertEquals(380.0, StoredValueCalculator.currentBalance(restoredCard, restored)!!, 0.0)

            repo.updateTransaction(reassigned.copy(paymentMethodId = cashId))
            val switchedBack = repo.getAllConfirmedTransactions().first()
            assertEquals(500.0, StoredValueCalculator.currentBalance(card, switchedBack)!!, 0.0)
        } finally {
            database.close()
            restoredDatabase.close()
            backup.delete()
        }
    }
}
