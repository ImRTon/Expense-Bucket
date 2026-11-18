package com.rton.expensebucket

import android.net.Uri
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.model.Transaction
import com.rton.expensebucket.data.model.TransactionItem
import com.rton.expensebucket.data.repository.ExpenseBucketRepository
import com.rton.expensebucket.util.DataExportImportManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class TransactionItemsDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun manager(database: ExpenseBucketDatabase) = DataExportImportManager(
        ExpenseBucketRepository(database.transactionDao(), database.categoryDao(), database.projectDao(),
            database.paymentMethodDao(), database.nonPaymentNotificationDao())
    )

    @Test
    fun migration12To13_preservesOldNotesAndPersistsItemEditsAfterReopen() = runBlocking {
        val name = "transaction_items_migration_test"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.rton.expensebucket.data.ExpenseBucketDatabase/12.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val table = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                            }
                        }
                        val queries = schema.getJSONArray("setupQueries")
                        for (i in 0 until queries.length()) db.execSQL(queries.getString(i))
                        db.execSQL("""INSERT INTO transactions
                            (id, amount, note, date, currency, exchangeRate, isExpense, source, isDraft, amortizationEnabled, createdAt)
                            VALUES (1, 40, '舊備註', 1000000, 'TWD', 1, 1, 'manual', 0, 0, 1000000)""")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        fun open() = Room.databaseBuilder(context, ExpenseBucketDatabase::class.java, name)
            .addMigrations(
                ExpenseBucketDatabase.MIGRATION_12_13,
                ExpenseBucketDatabase.MIGRATION_13_14,
                ExpenseBucketDatabase.MIGRATION_14_15,
                ExpenseBucketDatabase.MIGRATION_15_16
            ).build()
        try {
            helper.writableDatabase
            helper.close()
            val database = open()
            val items = listOf(TransactionItem("豆腐", 20.0), TransactionItem("豆干", 25.0))
            try {
                val old = database.transactionDao().getTransactionById(1)!!
                assertTrue(old.items.isEmpty())
                assertEquals("舊備註", old.note)
                database.transactionDao().updateTransaction(old.copy(note = "第一行\n第二行", items = items))
            } finally { database.close() }
            val reopened = open()
            try {
                val updated = reopened.transactionDao().getTransactionById(1)!!
                assertEquals(items, updated.items)
                assertEquals(40.0, updated.amount, 0.0)
                assertEquals("第一行\n第二行", updated.note)
                reopened.transactionDao().deleteTransaction(updated)
                assertNull(reopened.transactionDao().getTransactionById(1))
            } finally { reopened.close() }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun csvRoundTrip_preservesItemsAndMultilineNotesAndAcceptsOldBackups() = runBlocking {
        val source = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        val destination = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        val file = File(context.cacheDir, "items_round_trip_test.csv")
        try {
            val original = Transaction(
                amount = 40.0, note = "購物, \"市場\"\n折扣 5 元",
                items = listOf(TransactionItem("豆腐, \"嫩\"", 20.0), TransactionItem("豆干", 25.0))
            )
            source.transactionDao().insertTransaction(original)
            manager(source).exportToCsv(context, Uri.fromFile(file)).getOrThrow()
            manager(destination).importFromCsv(context, Uri.fromFile(file)).getOrThrow()
            val restored = destination.transactionDao().getAllConfirmedTransactions().first().single()
            assertEquals(original.items, restored.items)
            assertEquals(original.note, restored.note)
            assertEquals(original.amount, restored.amount, 0.0)
            file.writeText("amount,note,date\n10,舊版匯入,1000000\n")
            manager(destination).importFromCsv(context, Uri.fromFile(file)).getOrThrow()
            file.writeText("# ExpenseBucket Export v2\n#SECTION:TRANSACTIONS\namount,note,date\n15,舊版分段匯入,1000001\n")
            manager(destination).importFromCsv(context, Uri.fromFile(file)).getOrThrow()
            val legacy = destination.transactionDao().getAllConfirmedTransactions().first().filter { it.note.startsWith("舊版") }
            assertEquals(2, legacy.size)
            assertTrue(legacy.all { it.items.isEmpty() })

        } finally {
            source.close()
            destination.close()
            file.delete()
        }
    }
}
