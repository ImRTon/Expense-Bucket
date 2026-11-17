package com.rton.expensebucket

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import java.io.File
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.isDialog
import android.content.res.Configuration
import android.os.LocaleList
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.ui.screens.SettingsScreen
import com.rton.expensebucket.ui.theme.ExpensesTheme
import com.rton.expensebucket.ui.util.localizedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LocalizationUiTest {
    @get:Rule val compose = createComposeRule()

    private fun localizedContext(tag: String): Context {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val configuration = Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tag))
        }
        return context.createConfigurationContext(configuration)
    }

    @Test
    fun settings_languagePickerOffersSystemAndAllFourLanguages() {
        val context = localizedContext("en")
        compose.setContent {
            val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides context,
                LocalConfiguration provides context.resources.configuration
            ) {
                ExpensesTheme(dynamicColor = false) { SettingsScreen() }
            }
        }
        compose.onNodeWithText("Language").performClick()
        listOf("Follow system", "繁體中文", "English", "日本語", "简体中文").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        }
    }

    @Test
    fun settings_updatesLabelsWhenLocaleChanges() {
        val context = mutableStateOf(localizedContext("en"))
        compose.setContent {
            val registryOwner = requireNotNull(LocalActivityResultRegistryOwner.current)
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides registryOwner,
                LocalContext provides context.value,
                LocalConfiguration provides context.value.resources.configuration
            ) {
                ExpensesTheme(dynamicColor = false) { SettingsScreen() }
            }
        }
        listOf("en" to "Language", "ja" to "言語", "zh-Hans" to "语言", "zh-Hant" to "語言").forEach { (tag, text) ->
            compose.runOnIdle { context.value = localizedContext(tag) }
            compose.onNodeWithText(text).assertIsDisplayed()
            val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "localization").apply { mkdirs() }
            File(directory, "settings-$tag.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }

    @Test
    fun resources_formatAmountsAndMessagesForEveryLanguage() {
        listOf("en", "ja", "zh-Hans", "zh-Hant").forEach { tag ->
            val strings = localizedContext(tag).resources
            assertFalse(strings.getString(R.string.detected_transaction, "SALE", "$120").contains("%"))
            assertFalse(strings.getString(R.string.amortization_summary, "2026/11", 6, "").contains("%"))
            assertEquals(1, strings.getString(R.string.recognition_confidence, 85).count { it == '%' })
            assertEquals("我的自訂分類", strings.localizedLabel("我的自訂分類"))
            assertEquals(strings.getString(R.string.cash), strings.localizedLabel("現金"))
        }
        assertEquals("设置", localizedContext("zh-CN").getString(R.string.settings))
        assertEquals("設定", localizedContext("zh-TW").getString(R.string.settings))
    }
}
