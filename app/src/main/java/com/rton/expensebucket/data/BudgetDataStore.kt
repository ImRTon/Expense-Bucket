package com.rton.expensebucket.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.budgetDataStore: DataStore<Preferences> by preferencesDataStore(name = "budget_settings")

@Singleton
class BudgetDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val MONTHLY_BUDGET_KEY = doublePreferencesKey("monthly_budget")
    }

    val monthlyBudget: Flow<Double> = context.budgetDataStore.data.map { prefs ->
        prefs[MONTHLY_BUDGET_KEY] ?: 0.0
    }

    suspend fun setMonthlyBudget(amount: Double) {
        context.budgetDataStore.edit { prefs ->
            prefs[MONTHLY_BUDGET_KEY] = amount
        }
    }
}
