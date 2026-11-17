package com.rton.expensebucket

import com.rton.expensebucket.data.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageTest {
    @Test
    fun fromLanguageTag_matchesRegionalEnglishAndJapanese() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTag("en-GB"))
        assertEquals(AppLanguage.JAPANESE, AppLanguage.fromLanguageTag("ja-JP"))
    }

    @Test
    fun fromLanguageTag_distinguishesChineseScriptsAndRegions() {
        listOf("zh-Hant", "zh-TW", "zh-HK", "zh-Hant-CN").forEach {
            assertEquals(it, AppLanguage.TRADITIONAL_CHINESE, AppLanguage.fromLanguageTag(it))
        }
        listOf("zh-Hans", "zh-CN", "zh-SG", "zh-Hans-TW").forEach {
            assertEquals(it, AppLanguage.SIMPLIFIED_CHINESE, AppLanguage.fromLanguageTag(it))
        }
    }

    @Test
    fun fromLanguageTag_emptyAndUnsupportedTagsFollowSystem() {
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag(""))
        assertEquals(AppLanguage.SYSTEM, AppLanguage.fromLanguageTag("fr-FR"))
    }
}
