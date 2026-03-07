package com.rton.expanses.di

import android.content.Context
import androidx.room.Room
import com.rton.expanses.data.ExpansesDatabase
import com.rton.expanses.data.dao.CategoryDao
import com.rton.expanses.data.dao.PaymentMethodDao
import com.rton.expanses.data.dao.ProjectDao
import com.rton.expanses.data.dao.TransactionDao
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
    fun provideDatabase(@ApplicationContext context: Context): ExpansesDatabase {
        return Room.databaseBuilder(
            context,
            ExpansesDatabase::class.java,
            "expanses_database"
        )
            .addMigrations(
                ExpansesDatabase.MIGRATION_1_2,
                ExpansesDatabase.MIGRATION_2_3,
                ExpansesDatabase.MIGRATION_3_4
            )
            .build()
    }

    @Provides
    fun provideTransactionDao(database: ExpansesDatabase): TransactionDao =
        database.transactionDao()

    @Provides
    fun provideCategoryDao(database: ExpansesDatabase): CategoryDao =
        database.categoryDao()

    @Provides
    fun provideProjectDao(database: ExpansesDatabase): ProjectDao =
        database.projectDao()

    @Provides
    fun providePaymentMethodDao(database: ExpansesDatabase): PaymentMethodDao =
        database.paymentMethodDao()
}
