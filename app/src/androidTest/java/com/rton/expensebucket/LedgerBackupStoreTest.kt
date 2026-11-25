package com.rton.expensebucket

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.AppSettingsDataStore
import com.rton.expensebucket.data.BudgetDataStore
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.backup.LedgerBackupStore
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.TransactionItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LedgerBackupStoreTest {
    @Test
    fun restore_mergesWithoutReusingDeletedIdsAndCanReplaceTheLedger() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AppSettingsDataStore(context)
        val budgets = BudgetDataStore(context)
        val source = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        val target = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        try {
            val sourceStore = LedgerBackupStore(source, settings, budgets)
            val targetStore = LedgerBackupStore(target, settings, budgets)
            val remoteCategory = Category(id = 1, name = "Coffee", icon = "Coffee", color = 0)
            val remoteTransaction = Transaction(
                id = 1, amount = 100.0, categoryId = 1, date = 10, createdAt = 10,
                items = listOf(TransactionItem("Coffee", 100.0))
            )
            source.cloudBackupDao().insertCategories(listOf(remoteCategory))
            source.cloudBackupDao().insertTransactions(listOf(remoteTransaction))
            val backup = targetStore.deserialize(sourceStore.serialize())

            val local = Transaction(id = 1, amount = 20.0, date = 20, createdAt = 20)
            target.cloudBackupDao().insertTransactions(listOf(local, local.copy(id = 99)))
            target.transactionDao().deleteTransactionById(99)

            assertEquals(1, targetStore.restore(backup, replace = false))
            assertEquals(0, targetStore.restore(backup, replace = false))
            val merged = target.cloudBackupDao().transactions()
            assertEquals(local, merged.first())
            assertTrue(merged.last().id > 99)
            assertEquals(remoteTransaction.items, merged.last().items)
            assertEquals(2, targetStore.transactionSummary().count)
            assertEquals(20L, targetStore.transactionSummary().latestDate)

            assertEquals(1, targetStore.restore(backup, replace = true))
            assertEquals(listOf(remoteCategory), target.cloudBackupDao().categories())
            assertEquals(listOf(remoteTransaction), target.cloudBackupDao().transactions())
            assertEquals(10L, targetStore.transactionSummary().latestDate)
        } finally {
            target.close()
            source.close()
        }
    }
}
