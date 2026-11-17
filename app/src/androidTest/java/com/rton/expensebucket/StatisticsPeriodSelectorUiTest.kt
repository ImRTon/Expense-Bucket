package com.rton.expensebucket

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.ui.components.StatisticsPeriodSelector
import com.rton.expensebucket.ui.screens.StatisticsScreen
import com.rton.expensebucket.ui.statistics.StatsPeriod
import com.rton.expensebucket.ui.theme.ExpensesTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class StatisticsPeriodSelectorUiTest {
    @get:Rule val compose = createComposeRule()

    private val changes = mutableListOf<StatsPeriod>()

    @Test
    fun drag_followsFingerThenSnapsAndCommitsOnceOnRelease() {
        showSelector()
        compose.mainClock.autoAdvance = false
        selector().performTouchInput {
            down(Offset(width * 0.375f, height / 2f))
            moveTo(Offset(width * 0.55f, height / 2f), delayMillis = 100)
        }
        advanceAnimation(32)
        assertTrue(indicatorX() > tabX(StatsPeriod.MONTH) + 5f)
        assertTrue(indicatorX() < tabX(StatsPeriod.YEAR) - 5f)
        tab(StatsPeriod.MONTH).assertIsSelected()
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
        screenshot("statistics-period-drag")

        selector().performTouchInput { up() }
        advanceAnimation(1500)
        tab(StatsPeriod.YEAR).assertIsSelected()
        assertEquals(tabX(StatsPeriod.YEAR), indicatorX(), 1f)
        compose.runOnIdle { assertEquals(listOf(StatsPeriod.YEAR), changes) }
        screenshot("statistics-period-selected")
    }

    @Test
    fun dragAcrossEntireControl_selectsBothEndpointsWithoutOvershooting() {
        showSelector()
        selector().performTouchInput {
            down(Offset(width * 0.375f, height / 2f))
            moveTo(Offset(width * 1.2f, height / 2f), delayMillis = 200)
            up()
        }
        tab(StatsPeriod.ALL).assertIsSelected()
        assertEquals(tabX(StatsPeriod.ALL), indicatorX(), 1f)

        selector().performTouchInput {
            down(Offset(width * 0.875f, height / 2f))
            moveTo(Offset(-width * 0.2f, height / 2f), delayMillis = 200)
            up()
        }
        tab(StatsPeriod.WEEK).assertIsSelected()
        assertEquals(tabX(StatsPeriod.WEEK), indicatorX(), 1f)
        compose.runOnIdle { assertEquals(listOf(StatsPeriod.ALL, StatsPeriod.WEEK), changes) }
    }

    @Test
    fun shortDrag_returnsToCurrentPeriodWithoutReloadingStatistics() {
        showSelector()
        selector().performTouchInput {
            down(Offset(width * 0.375f, height / 2f))
            moveTo(Offset(width * 0.46f, height / 2f), delayMillis = 100)
            up()
        }
        tab(StatsPeriod.MONTH).assertIsSelected()
        assertEquals(tabX(StatsPeriod.MONTH), indicatorX(), 1f)
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun cancelledDrag_animatesBackWithoutChangingPeriod() {
        showSelector()
        compose.mainClock.autoAdvance = false
        selector().performTouchInput {
            down(Offset(width * 0.375f, height / 2f))
            moveTo(Offset(width * 0.8f, height / 2f), delayMillis = 100)
        }
        advanceAnimation(32)
        assertTrue(indicatorX() > tabX(StatsPeriod.YEAR))
        selector().performTouchInput { cancel() }
        advanceAnimation(1500)
        tab(StatsPeriod.MONTH).assertIsSelected()
        assertEquals(tabX(StatsPeriod.MONTH), indicatorX(), 1f)
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun tap_animatesIndicatorAndRetainsSelectedTabSemantics() {
        showSelector()
        compose.mainClock.autoAdvance = false
        tab(StatsPeriod.ALL).performTouchInput { click() }
        advanceAnimation(80)
        tab(StatsPeriod.ALL).assertIsSelected()
        assertTrue(indicatorX() > tabX(StatsPeriod.MONTH) + 5f)
        assertTrue(indicatorX() < tabX(StatsPeriod.ALL) - 5f)
        advanceAnimation(1500)
        assertEquals(tabX(StatsPeriod.ALL), indicatorX(), 1f)
    }

    @Test
    fun verticalDrag_doesNotChangePeriod() {
        showSelector()
        selector().performTouchInput {
            down(center)
            moveBy(Offset(0f, height * 3f), delayMillis = 200)
            up()
        }
        tab(StatsPeriod.MONTH).assertIsSelected()
        compose.runOnIdle { assertTrue(changes.isEmpty()) }
    }

    @Test
    fun rightToLeft_dragAndIndicatorMatchVisualTabOrder() {
        showSelector(LayoutDirection.Rtl)
        selector().performTouchInput {
            down(Offset(width * 0.625f, height / 2f))
            moveTo(Offset(width * 0.125f, height / 2f), delayMillis = 200)
            up()
        }
        tab(StatsPeriod.ALL).assertIsSelected()
        assertEquals(tabX(StatsPeriod.ALL), indicatorX(), 1f)
    }

    @Test
    fun statisticsScreen_dragSwitchesPeriod() {
        compose.setContent {
            ExpensesTheme(dynamicColor = false) {
                StatisticsScreen(transactions = emptyList(), categories = emptyList())
            }
        }
        selector().performTouchInput {
            down(Offset(width * 0.375f, height / 2f))
            moveTo(Offset(width * 0.875f, height / 2f), delayMillis = 200)
            up()
        }
        tab(StatsPeriod.ALL).assertIsSelected()
        assertEquals(tabX(StatsPeriod.ALL), indicatorX(), 1f)
        screenshot("statistics-period-screen")
    }

    @Test
    fun tapCurrentPeriod_preservesResetToCurrentPeriodCallback() {
        showSelector()
        tab(StatsPeriod.MONTH).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(StatsPeriod.MONTH), changes) }
    }

    private fun showSelector(direction: LayoutDirection = LayoutDirection.Ltr) {
        compose.setContent {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                ExpensesTheme(dynamicColor = false) {
                    var period by remember { mutableStateOf(StatsPeriod.MONTH) }
                    Column {
                        StatisticsPeriodSelector(
                            period = period,
                            onPeriodChange = { changes += it; period = it }
                        )
                    }
                }
            }
        }
    }

    private fun selector() = compose.onNodeWithTag("statistics_period_selector")
    private fun tab(period: StatsPeriod) = compose.onNodeWithTag("statistics_period_${period.name.lowercase()}")
    private fun tabX(period: StatsPeriod) = tab(period).fetchSemanticsNode().boundsInRoot.left
    private fun indicatorX() = compose.onNodeWithTag("statistics_period_indicator")
        .fetchSemanticsNode().boundsInRoot.left

    private fun advanceAnimation(milliseconds: Long) {
        compose.mainClock.advanceTimeBy(milliseconds)
        compose.waitForIdle()
    }

    private fun screenshot(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(context.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
}
