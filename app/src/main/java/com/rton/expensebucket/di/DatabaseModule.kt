package com.rton.expensebucket.di

import android.content.Context
import androidx.room.Room
import com.rton.expensebucket.data.ExpenseBucketDatabase
import com.rton.expensebucket.data.dao.CategoryDao
import com.rton.expensebucket.data.dao.NonPaymentNotificationDao
import com.rton.expensebucket.data.dao.PaymentMethodDao
import com.rton.expensebucket.data.dao.ProjectDao
import com.rton.expensebucket.data.dao.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ExpenseBucketDatabase {
        return Room.databaseBuilder(
            context,
            ExpenseBucketDatabase::class.java,
            "expenses_database"
        )
            .addMigrations(
                ExpenseBucketDatabase.MIGRATION_1_2,
                ExpenseBucketDatabase.MIGRATION_2_3,
                ExpenseBucketDatabase.MIGRATION_3_4,
                ExpenseBucketDatabase.MIGRATION_4_5,
                ExpenseBucketDatabase.MIGRATION_5_6,
                ExpenseBucketDatabase.MIGRATION_6_7,
                ExpenseBucketDatabase.MIGRATION_7_8,
                ExpenseBucketDatabase.MIGRATION_8_9,
                ExpenseBucketDatabase.MIGRATION_9_10
            )
            .build()
    }

    @Provides
    fun provideTransactionDao(database: ExpenseBucketDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun provideCategoryDao(database: ExpenseBucketDatabase): CategoryDao =
        database.categoryDao()

    @Provides
    fun provideProjectDao(database: ExpenseBucketDatabase): ProjectDao =
        database.projectDao()

    @Provides
    fun providePaymentMethodDao(database: ExpenseBucketDatabase): PaymentMethodDao =
        database.paymentMethodDao()

    @Provides
    fun provideNonPaymentNotificationDao(database: ExpenseBucketDatabase): NonPaymentNotificationDao =
        database.nonPaymentNotificationDao()
}
