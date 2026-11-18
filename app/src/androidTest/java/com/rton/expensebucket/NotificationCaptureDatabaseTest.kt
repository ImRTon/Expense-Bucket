package com.rton.expensebucket

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.model.NotificationCapture
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotificationCaptureDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val bank = NotificationCapture(
        packageName = "com.sinopac.DaCard", notificationKey = "bank-key", textHash = "bank-hash",
        amount = 413.0, isExpense = true, merchant = "SAMPLE CAFE B", cardSuffix = "1234",
        transactionDate = 1_000_000L, capturedAt = 1_005_000L
    )
    private val wallet = bank.copy(
        packageName = "com.google.android.apps.walletnfcrel", notificationKey = "wallet-key",
        textHash = "wallet-hash", merchant = "Sample Cafe B", transactionDate = null, capturedAt = 1_010_000L
    )
    private val draft = Transaction(amount = 413.0, note = "SAMPLE CAFE B", source = "notification", isDraft = true)

    private fun openDatabase(name: String): ExpenseBucketDatabase =
        Room.databaseBuilder(context, ExpenseBucketDatabase::class.java, name)
            .addMigrations(
                ExpenseBucketDatabase.MIGRATION_11_12,
                ExpenseBucketDatabase.MIGRATION_12_13,
                ExpenseBucketDatabase.MIGRATION_13_14,
                ExpenseBucketDatabase.MIGRATION_14_15,
                ExpenseBucketDatabase.MIGRATION_15_16
            ).build()

    @Test
    fun migratesVersion11PreservingTransactionsAndValidatingSchema() = runBlocking {
        val name = "notification_migration_test"
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.rton.expensebucket.data.ExpenseBucketDatabase/11.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(11) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        val entities = schema.getJSONArray("entities")
                        for (i in 0 until entities.length()) {
                            val entity = entities.getJSONObject(i)
                            val tableName = entity.getString("tableName")
                            db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", tableName))
                            val indices = entity.optJSONArray("indices")
                            if (indices != null) for (j in 0 until indices.length()) {
                                db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", tableName))
                            }
                        }
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        db.execSQL("""INSERT INTO transactions
                            (id, amount, note, date, currency, exchangeRate, isExpense, source, isDraft, amortizationEnabled, createdAt)
                            VALUES (1, 413, 'existing transaction', 1000000, 'TWD', 1, 1, 'notification', 1, 0, 1005000)""")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            helper.writableDatabase
            helper.close()
            val database = openDatabase(name)
            try {
                val existing = database.transactionDao().getTransactionById(1)!!
                assertEquals("existing transaction", existing.note)
                assertEquals(413.0, existing.amount, 0.001)
                database.transactionDao().insertNotificationCapture(bank.copy(transactionId = 1))
                assertEquals(1, database.transactionDao().getRecentNotificationCaptures(0).size)
            } finally {
                database.close()
            }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun deduplicatesConcurrentPostsAndPersistsEvidenceAfterRestartAndConfirmation() = runBlocking {
        val name = "notification_dedup_test"
        context.deleteDatabase(name)
        var database = openDatabase(name)
        try {
            val dao = database.transactionDao()
            val results = (1..8).map { async(Dispatchers.IO) { dao.insertNotificationDraftIfAbsent(draft, bank) } }.awaitAll()
            assertEquals(1, results.count { it != null })
            val id = results.filterNotNull().single()
            dao.confirmDraft(id)
            database.close()
            database = openDatabase(name)
            assertNull(database.transactionDao().insertNotificationDraftIfAbsent(draft.copy(note = "Sample Cafe B"), wallet))
            assertFalse(database.transactionDao().getTransactionById(id)!!.isDraft)
            assertEquals(2, database.transactionDao().getRecentNotificationCaptures(0).size)
            database.transactionDao().deleteTransactionById(id)
            assertTrue(database.transactionDao().getRecentNotificationCaptures(0).isEmpty())
        } finally {
            database.close()
            context.deleteDatabase(name)
        }
    }

    @Test
    fun walletFillsPaymentMethodOnExistingDraftAndPreservesChosenMethod() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(context, ExpenseBucketDatabase::class.java).build()
        try {
            val sports = database.paymentMethodDao().insertPaymentMethod(PaymentMethod(name = "永豐 Sports", icon = "sinopac", type = "epay"))
            val dao = database.transactionDao()
            val id = dao.insertNotificationDraftIfAbsent(draft, bank)!!
            assertNull(dao.insertNotificationDraftIfAbsent(draft.copy(paymentMethodId = sports), wallet))
            assertEquals(sports, dao.getTransactionById(id)!!.paymentMethodId)
            val another = database.paymentMethodDao().insertPaymentMethod(PaymentMethod(name = "另一張卡", icon = "credit_card", type = "credit"))
            dao.updateTransaction(dao.getTransactionById(id)!!.copy(paymentMethodId = another))
            assertNull(dao.insertNotificationDraftIfAbsent(draft.copy(paymentMethodId = sports), wallet))
            assertEquals(another, dao.getTransactionById(id)!!.paymentMethodId)
            assertEquals(1, dao.getDraftTransactions().first().size)
        } finally {
            database.close()
        }
    }
}
