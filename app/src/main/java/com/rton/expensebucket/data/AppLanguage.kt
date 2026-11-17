package com.rton.expensebucket.data

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

enum class AppLanguage(val languageTag: String, val nativeName: String) {
    SYSTEM("", ""),
    TRADITIONAL_CHINESE("zh-Hant", "繁體中文"),
    ENGLISH("en", "English"),
    JAPANESE("ja", "日本語"),
    SIMPLIFIED_CHINESE("zh-Hans", "简体中文");

    companion object {
        private const val PREFERENCES = "app_language"
        private const val LANGUAGE_TAG = "language_tag"

        fun current(context: Context): AppLanguage {
            val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
            } else {
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                    .getString(LANGUAGE_TAG, "").orEmpty()
            }
            return fromLanguageTag(tag.substringBefore(','))
        }

        fun fromLanguageTag(tag: String): AppLanguage {
            val locale = Locale.forLanguageTag(tag)
            return when (locale.language) {
                "en" -> ENGLISH
                "ja" -> JAPANESE
                "zh" -> if (locale.script == "Hans" || (locale.script != "Hant" && locale.country in listOf("CN", "SG"))) {
                    SIMPLIFIED_CHINESE
                } else {
                    TRADITIONAL_CHINESE
                }
                else -> SYSTEM
            }
        }

        /** Android 13 persists the choice and recreates activities through the framework. */
        fun set(context: Context, language: AppLanguage) {
            if (current(context) == language) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.getSystemService(LocaleManager::class.java).applicationLocales =
                    LocaleList.forLanguageTags(language.languageTag)
            } else {
                context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
                    .putString(LANGUAGE_TAG, language.languageTag).apply()
                var owner = context
                while (owner is ContextWrapper && owner !is Activity) owner = owner.baseContext
                (owner as? Activity)?.recreate()
            }
        }

        /** Legacy activities and background notifications use the same saved language. */
        fun wrap(context: Context): Context {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return context
            val language = current(context)
            if (language == SYSTEM) return context
            val configuration = Configuration(context.resources.configuration)
            configuration.setLocales(LocaleList.forLanguageTags(language.languageTag))
            return context.createConfigurationContext(configuration)
        }
    }
}
