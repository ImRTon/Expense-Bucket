package com.rton.expensebucket

import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.DefaultPaymentMethods
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.model.StoredValueBalance
import com.rton.expensebucket.util.StoredValueCalculator
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PaymentMethodDefaultsMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun upgrade_preservesDeviceIdsTransactionsAndCustomCardSettings() = runBlocking {
        withVersion13Database("payment_brand_migration", false) { database ->
            val methods = database.paymentMethodDao().getAllPaymentMethods().first()
            val transit = methods.single { it.name == "電子票證" }
            assertEquals(25L, transit.id)
            val icoca = methods.single { it.id == 24L }
            assertEquals("icoca", icoca.icon)
            assertEquals(transit.id, icoca.parentId)
            assertEquals(0xFF123456, icoca.color)
            assertEquals(24L, database.transactionDao().getTransactionById(1)!!.paymentMethodId)
            assertEquals(16L, methods.single { it.icon == "pay_full" }.id)
            assertEquals(17L, methods.single { it.icon == "tpay" }.id)
            assertEquals(23L, methods.single { it.name == "Custom card" }.id)
            assertEquals(17, methods.single { it.id == 23L }.billingCycleDay)
            assertEquals(1L, methods.single { it.isDefault }.id)
            assertFalse(methods.any { it.icon == "samsungpay" || it.icon == "paypal" })
            for (icon in listOf("easycard_card", "icash", "ipass_card", "suica", "icoca")) {
                assertEquals(transit.id, methods.single { it.icon == icon }.parentId)
            }
            val before = methods.associateBy { it.id }
            ExpenseBucketDatabase.MIGRATION_13_14.migrate(database.openHelper.writableDatabase)
            assertEquals(before, database.paymentMethodDao().getAllPaymentMethods().first().associateBy { it.id })
        }
    }

    @Test
    fun upgrade_allocatesFreeIdsWhenPreferredIdsBelongToCustomMethods() = runBlocking {
        withVersion13Database("payment_brand_collision", true) { database ->
            val methods = database.paymentMethodDao().getAllPaymentMethods().first()
            assertEquals("Existing custom method", methods.single { it.id == 25L }.name)
            val transit = methods.single { it.name == "電子票證" }
            assertNotEquals(25L, transit.id)
            assertEquals(transit.id, methods.single { it.id == 24L }.parentId)
            assertEquals(methods.size, methods.map { it.id }.distinct().size)
            for (key in DefaultPaymentMethods.addedIconKeys) {
                assertEquals(1, methods.count { it.icon == key })
            }
        }
    }

    @Test
    fun upgrade_allowsTopUpAndReconciliationWithoutAddingTransactions() = runBlocking {
        withVersion13Database("payment_stored_value_migration", false) { database ->
            val methods = database.paymentMethodDao()
            val card = methods.getPaymentMethodById(24)!!
            val transactions = database.transactionDao().getAllConfirmedTransactions().first()
            assertNull(card.storedValueBalance)
            assertEquals(1, methods.setStoredValueBalance(card.id, 500.0, 999_999, "JPY", false))
            methods.topUpStoredValueBalance(card.id, 200.0)
            methods.updatePaymentMethodDetails(card.copy(name = "My ICOCA"))

            val toppedUp = methods.getPaymentMethodById(card.id)!!
            assertEquals("My ICOCA", toppedUp.name)
            assertEquals(StoredValueBalance(700.0, 999_999, "JPY"), toppedUp.storedValueBalance)
            assertEquals(600.0, StoredValueCalculator.currentBalance(toppedUp, transactions)!!, 0.0)
            assertEquals(0, methods.setStoredValueBalance(card.id, 20.0, 999_999, "JPY", true))

            assertEquals(1, methods.setStoredValueBalance(card.id, 350.0, 1_000_000, "JPY", false))
            val reconciled = methods.getPaymentMethodById(card.id)!!
            assertEquals(350.0, StoredValueCalculator.currentBalance(reconciled, transactions)!!, 0.0)
            assertEquals(transactions, database.transactionDao().getAllConfirmedTransactions().first())
        }
    }

    private suspend fun withVersion13Database(
        name: String,
        occupiedId: Boolean,
        verify: suspend (ExpenseBucketDatabase) -> Unit
    ) {
        context.deleteDatabase(name)
        val schema = InstrumentationRegistry.getInstrumentation().context.assets
            .open("com.rton.expensebucket.data.ExpenseBucketDatabase/13.json")
            .bufferedReader().use { JSONObject(it.readText()).getJSONObject("database") }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context).name(name)
                .callback(object : SupportSQLiteOpenHelper.Callback(13) {
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
                        val setup = schema.getJSONArray("setupQueries")
                        for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                        for (seed in DefaultPaymentMethods.getSeeds().filter {
                            it.icon !in DefaultPaymentMethods.addedIconKeys && it.id !in 5L..15L
                        }) {
                            val parentId = seed.parentName?.let { parent ->
                                DefaultPaymentMethods.getSeeds().single { it.name == parent }.id
                            }
                            db.execSQL("""INSERT INTO payment_methods
                                (id, name, icon, color, type, isDefault, parentId, sortOrder,
                                 billingCycleType, billingLimitType)
                                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'none', 'credit')""",
                                arrayOf<Any?>(seed.id, seed.name, seed.icon, seed.color, seed.type,
                                    if (seed.isDefault) 1 else 0, parentId, seed.sortOrder))
                        }
                        db.execSQL("""INSERT INTO payment_methods
                            (id, name, icon, color, type, isDefault, parentId, sortOrder,
                             billingCycleType, billingCycleDay, billingLimitType, billingLimitAmount)
                            VALUES (23, 'Custom card', 'richart', 1, 'credit', 0, 2, 0,
                                    'monthly_closing_day', 17, 'promo', 5000)""")
                        db.execSQL("""INSERT INTO payment_methods
                            (id, name, icon, color, type, isDefault, parentId, sortOrder, billingCycleType, billingLimitType)
                            VALUES (24, 'ICOCA', 'credit_card', 4279383126, 'other', 0, 4, 31, 'none', 'credit')""")
                        if (occupiedId) db.execSQL("""INSERT INTO payment_methods
                            (id, name, icon, color, type, isDefault, sortOrder, billingCycleType, billingLimitType)
                            VALUES (25, 'Existing custom method', 'other', 1, 'other', 0, 0, 'none', 'credit')""")
                        db.execSQL("""INSERT INTO transactions
                            (id, amount, note, paymentMethodId, date, currency, exchangeRate,
                             isExpense, source, isDraft, amortizationEnabled, createdAt, items)
                            VALUES (1, 100, 'Synthetic transit fixture', 24, 1000000,
                                    'JPY', 0.2, 1, 'manual', 0, 0, 1000000, '[]')""")
                    }
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                }).build()
        )
        try {
            helper.writableDatabase
            helper.close()
            val database = Room.databaseBuilder(context, ExpenseBucketDatabase::class.java, name)
                .addMigrations(
                    ExpenseBucketDatabase.MIGRATION_13_14,
                    ExpenseBucketDatabase.MIGRATION_14_15,
                    ExpenseBucketDatabase.MIGRATION_15_16
                )
                .build()
            try { verify(database) } finally { database.close() }
        } finally {
            helper.close()
            context.deleteDatabase(name)
        }
    }
}
