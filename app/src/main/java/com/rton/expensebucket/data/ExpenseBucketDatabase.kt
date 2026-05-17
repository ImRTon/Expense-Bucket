package com.rton.expensebucket.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rton.expensebucket.data.dao.CategoryDao
import com.rton.expensebucket.data.dao.NonPaymentNotificationDao
import com.rton.expensebucket.data.dao.PaymentMethodDao
import com.rton.expensebucket.data.dao.ProjectDao
import com.rton.expensebucket.data.dao.TransactionDao
import com.rton.expensebucket.data.model.Category
import com.rton.expensebucket.data.model.NonPaymentNotification
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.model.Project
import com.rton.expensebucket.data.model.Transaction

@Database(
    entities = [Transaction::class, Category::class, Project::class, PaymentMethod::class, NonPaymentNotification::class],
    version = 10,
    exportSchema = true
)
abstract class ExpenseBucketDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun paymentMethodDao(): PaymentMethodDao
    abstract fun nonPaymentNotificationDao(): NonPaymentNotificationDao

    companion object {
        /**
         * Migration from v1 → v2: adds payment_methods table and paymentMethodId column to transactions.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Create payment_methods table
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `payment_methods` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `name` TEXT NOT NULL,
                        `icon` TEXT NOT NULL,
                        `color` INTEGER NOT NULL DEFAULT -4276608,
                        `type` TEXT NOT NULL DEFAULT 'cash',
                        `isDefault` INTEGER NOT NULL DEFAULT 0,
                        `sortOrder` INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                // Add paymentMethodId column to transactions
                database.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `paymentMethodId` INTEGER DEFAULT NULL REFERENCES `payment_methods`(`id`) ON DELETE SET NULL"
                )
                // Create index
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_transactions_paymentMethodId` ON `transactions` (`paymentMethodId`)"
                )
            }
        }

        /**
         * Migration from v2 → v3: adds parentId column to categories for parent-child hierarchy.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `categories` ADD COLUMN `parentId` INTEGER DEFAULT NULL"
                )
            }
        }

        /**
         * Migration from v3 → v4: adds startDate, endDate, budget columns to projects.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE `projects` ADD COLUMN `startDate` INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE `projects` ADD COLUMN `endDate` INTEGER DEFAULT NULL")
                database.execSQL("ALTER TABLE `projects` ADD COLUMN `budget` REAL DEFAULT NULL")
            }
        }

        /**
         * Migration from v4 → v5: adds parentId to payment_methods and populates default parent methods.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // 1. Add parentId column
                database.execSQL("ALTER TABLE `payment_methods` ADD COLUMN `parentId` INTEGER DEFAULT NULL")

                // 2. Insert parent categories (現金, 信用卡, 電子支付, 其他)
                // Note: The ARGB colors are approximate defaults, since we only need them as logic groupings now
                val cashColor = 0xFF4CAF50.toLong()
                val creditColor = 0xFF2196F3.toLong()
                val epayColor = 0xFFFF9800.toLong()
                val otherColor = 0xFF9E9E9E.toLong()

                // Execute inserts and get their new IDs (using generic icons for parents)
                val cashId = database.insert("payment_methods", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    android.content.ContentValues().apply {
                        put("name", "現金")
                        put("icon", "Payments")
                        put("color", cashColor)
                        put("type", "cash")
                        put("isDefault", 0)
                        put("sortOrder", 0)
                        putNull("parentId")
                    })

                val creditId = database.insert("payment_methods", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    android.content.ContentValues().apply {
                        put("name", "信用卡")
                        put("icon", "CreditCard")
                        put("color", creditColor)
                        put("type", "credit")
                        put("isDefault", 0)
                        put("sortOrder", 1)
                        putNull("parentId")
                    })

                val epayId = database.insert("payment_methods", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    android.content.ContentValues().apply {
                        put("name", "電子支付")
                        put("icon", "PhoneIphone")
                        put("color", epayColor)
                        put("type", "epay")
                        put("isDefault", 0)
                        put("sortOrder", 2)
                        putNull("parentId")
                    })

                val otherId = database.insert("payment_methods", android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE,
                    android.content.ContentValues().apply {
                        put("name", "其他")
                        put("icon", "Toll")
                        put("color", otherColor)
                        put("type", "other")
                        put("isDefault", 0)
                        put("sortOrder", 3)
                        putNull("parentId")
                    })

                // 3. Update existing records to link to the new parents based on their 'type'
                // We only update records that were just made sub-categories (which originally had parentId = NULL before we added it, but let's be safe and check IDs != the ones we just inserted)
                val updateArgsCash = arrayOf(cashId, "cash", cashId, creditId, epayId, otherId)
                database.execSQL("UPDATE `payment_methods` SET `parentId` = ? WHERE `type` = ? AND `id` NOT IN (?, ?, ?, ?)", updateArgsCash)

                val updateArgsCredit = arrayOf(creditId, "credit", cashId, creditId, epayId, otherId)
                database.execSQL("UPDATE `payment_methods` SET `parentId` = ? WHERE `type` = ? AND `id` NOT IN (?, ?, ?, ?)", updateArgsCredit)

                val updateArgsEpay = arrayOf(epayId, "epay", cashId, creditId, epayId, otherId)
                database.execSQL("UPDATE `payment_methods` SET `parentId` = ? WHERE `type` = ? AND `id` NOT IN (?, ?, ?, ?)", updateArgsEpay)

                val updateArgsOther = arrayOf(otherId, "other", cashId, creditId, epayId, otherId)
                database.execSQL("UPDATE `payment_methods` SET `parentId` = ? WHERE `type` = ? AND `id` NOT IN (?, ?, ?, ?)", updateArgsOther)
            }
        }

        /**
         * Migration from v5 → v6: adds billing cycle settings to payment_methods.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `payment_methods` ADD COLUMN `billingCycleType` TEXT NOT NULL DEFAULT 'none'"
                )
                database.execSQL(
                    "ALTER TABLE `payment_methods` ADD COLUMN `billingCycleDay` INTEGER DEFAULT NULL"
                )
            }
        }

        /**
         * Migration from v6 → v7: adds billing limit settings to payment_methods.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `payment_methods` ADD COLUMN `billingLimitType` TEXT NOT NULL DEFAULT 'credit'"
                )
                database.execSQL(
                    "ALTER TABLE `payment_methods` ADD COLUMN `billingLimitAmount` REAL DEFAULT NULL"
                )
            }
        }

        /**
         * Migration from v7 → v8: adds personalAmount to transactions for per-person accounting.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE `transactions` ADD COLUMN `personalAmount` REAL DEFAULT NULL"
                )
                // Also add project custom icons and colors if they don't exist
                // (Note: some previous versions might have had them in model but not in migration)
                try {
                    database.execSQL("ALTER TABLE `projects` ADD COLUMN `icon` TEXT NOT NULL DEFAULT '✈️'")
                } catch (e: Exception) { /* already exists */ }
                try {
                    database.execSQL("ALTER TABLE `projects` ADD COLUMN `color` INTEGER NOT NULL DEFAULT -14575885")
                } catch (e: Exception) { /* already exists */ }
            }
        }
        /**
         * Migration from v8 → v9: adds icon and color to projects.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Ensure columns exist (fixes broken v8 schema where columns were in Entity but not in DB)
                try {
                    database.execSQL("ALTER TABLE `projects` ADD COLUMN `icon` TEXT NOT NULL DEFAULT '✈️'")
                } catch (e: Exception) { /* ignore if already exists */ }
                try {
                    database.execSQL("ALTER TABLE `projects` ADD COLUMN `color` INTEGER NOT NULL DEFAULT -14575885")
                } catch (e: Exception) { /* ignore if already exists */ }
            }
        }
        /**
         * Migration from v9 → v10: stores intercepted notifications that are not payments.
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `non_payment_notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `packageName` TEXT NOT NULL,
                        `text` TEXT NOT NULL,
                        `capturedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_non_payment_notifications_capturedAt` ON `non_payment_notifications` (`capturedAt`)"
                )
                database.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_non_payment_notifications_packageName` ON `non_payment_notifications` (`packageName`)"
                )
            }
        }
    }
}
