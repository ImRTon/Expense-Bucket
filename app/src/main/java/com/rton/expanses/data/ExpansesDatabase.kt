package com.rton.expanses.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.rton.expanses.data.dao.CategoryDao
import com.rton.expanses.data.dao.PaymentMethodDao
import com.rton.expanses.data.dao.ProjectDao
import com.rton.expanses.data.dao.TransactionDao
import com.rton.expanses.data.model.Category
import com.rton.expanses.data.model.PaymentMethod
import com.rton.expanses.data.model.Project
import com.rton.expanses.data.model.Transaction

@Database(
    entities = [Transaction::class, Category::class, Project::class, PaymentMethod::class],
    version = 3,
    exportSchema = true
)
abstract class ExpansesDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun projectDao(): ProjectDao
    abstract fun paymentMethodDao(): PaymentMethodDao

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
    }
}
