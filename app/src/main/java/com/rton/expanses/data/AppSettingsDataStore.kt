package com.rton.expanses.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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

private val Context.appSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

@Singleton
class AppSettingsDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val APP_THEME_KEY = stringPreferencesKey("app_theme")
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
}
