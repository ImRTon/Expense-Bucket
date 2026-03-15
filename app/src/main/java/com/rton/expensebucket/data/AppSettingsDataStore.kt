package com.rton.expensebucket.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class AppTheme(val displayName: String) {
    SYSTEM("跟隨系統"),
    LIGHT("淺色主題"),
    DARK("深色主題")
}

enum class AppPalette(val displayName: String) {
    DEFAULT("預設主題"),
    LATTE("宇宙拿鐵")
}

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val APP_THEME_KEY = stringPreferencesKey("app_theme")
        private val APP_PALETTE_KEY = stringPreferencesKey("app_palette")
        private val COMPARE_MODE_KEY = stringPreferencesKey("compare_mode")
        private val FIRST_DAY_OF_WEEK_KEY = intPreferencesKey("first_day_of_week")
    }

    val theme: Flow<AppTheme> = context.appSettingsDataStore.data.map { prefs ->
        val themeName = prefs[APP_THEME_KEY] ?: AppTheme.SYSTEM.name
        try {
            AppTheme.valueOf(themeName)
        } catch (e: IllegalArgumentException) {
            AppTheme.SYSTEM
        }
    }

    suspend fun setTheme(theme: AppTheme) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[APP_THEME_KEY] = theme.name
        }
    }

    val palette: Flow<AppPalette> = context.appSettingsDataStore.data.map { prefs ->
        val paletteName = prefs[APP_PALETTE_KEY] ?: AppPalette.DEFAULT.name
        try {
            AppPalette.valueOf(paletteName)
        } catch (e: IllegalArgumentException) {
            AppPalette.DEFAULT
        }
    }

    suspend fun setPalette(palette: AppPalette) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[APP_PALETTE_KEY] = palette.name
        }
    }

    val compareMode: Flow<String> = context.appSettingsDataStore.data.map { prefs ->
        prefs[COMPARE_MODE_KEY] ?: "EXPENSE_INCOME"
    }

    suspend fun setCompareMode(mode: String) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[COMPARE_MODE_KEY] = mode
        }
    }

    val firstDayOfWeek: Flow<Int> = context.appSettingsDataStore.data.map { prefs ->
        prefs[FIRST_DAY_OF_WEEK_KEY] ?: java.util.Calendar.MONDAY
    }

    suspend fun setFirstDayOfWeek(day: Int) {
        context.appSettingsDataStore.edit { prefs ->
            prefs[FIRST_DAY_OF_WEEK_KEY] = day
        }
    }
}
