package com.rton.expensebucket

import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.rton.expensebucket.data.repository.TransitCardImportPreview
import com.rton.expensebucket.data.repository.TransitCardImportResult
import com.rton.expensebucket.data.repository.TransitCardImportStatus
import com.rton.expensebucket.nfc.TransitCardEntry
import com.rton.expensebucket.nfc.TransitCardEntryType
import com.rton.expensebucket.nfc.TransitCardScan
import com.rton.expensebucket.ui.screens.TransitCardPlacementHint
import com.rton.expensebucket.ui.screens.TransitCardReview
import com.rton.expensebucket.ui.screens.TransitNfcBottomSheet
import com.rton.expensebucket.ui.screens.TransitNfcMascotStatus
import com.rton.expensebucket.ui.theme.ExpensesTheme
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TransitCardReviewUiTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun placementHint_pulsesWhileWaitingAndStopsWhenReaderCloses() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val readerActive = mutableStateOf(true)
        compose.mainClock.autoAdvance = false

        compose.setContent {
            MaterialTheme {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
                    TransitCardPlacementHint("Suica", readerActive.value)
                }
            }
        }

        val diagram = compose.onNodeWithContentDescription(strings.getString(R.string.transit_nfc_antenna_diagram))
        diagram.assertIsDisplayed()
        val waitingPosition = diagram.fetchSemanticsNode().boundsInRoot
        assertEquals(9f / 19.5f, waitingPosition.width / waitingPosition.height, 0.01f)
        compose.onNodeWithText(strings.getString(R.string.transit_nfc_hold_card, "Suica")).assertIsDisplayed()
        compose.onNodeWithText(strings.getString(R.string.transit_nfc_antenna_hint)).assertIsDisplayed()

        val waitingFrame = diagram.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(240)
        assertFalse(waitingFrame.sameAs(diagram.captureToImage().asAndroidBitmap()))

        assertEquals(waitingPosition, diagram.fetchSemanticsNode().boundsInRoot)

        compose.runOnIdle { readerActive.value = false }
        compose.mainClock.advanceTimeByFrame()
        val stoppedFrame = diagram.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(240)
        assertTrue(stoppedFrame.sameAs(diagram.captureToImage().asAndroidBitmap()))
    }

    @Test
    fun mascot_isStillWhileReadingAndFinishesItsGestureBeforeReview() {
        assertMascotGesture("icoca", "ICOCA", remainsInNewPose = false)
    }

    @Test
    fun suicaMascot_takesOneStepAndHoldsItsLandingPose() {
        assertMascotGesture("suica", "Suica", remainsInNewPose = true)
    }

    private fun assertMascotGesture(cardIcon: String, cardName: String, remainsInNewPose: Boolean) {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val confirmed = mutableStateOf(false)
        val animationFinished = mutableStateOf(false)
        compose.mainClock.autoAdvance = false
        compose.setContent {
            ExpensesTheme {
                TransitNfcBottomSheet(cardName, onDismiss = {}, contentState = animationFinished.value) { finished ->
                    if (finished) {
                        Text(strings.getString(R.string.transit_nfc_update_balance))
                    } else {
                        Box(Modifier.fillMaxSize().testTag("scan-mascot")) {
                            TransitNfcMascotStatus(cardIcon, confirmed.value) { animationFinished.value = true }
                        }
                    }
                }
            }
        }

        val scene = compose.onNodeWithTag("scan-mascot")
        val readingLabel = compose.onNodeWithText(strings.getString(R.string.transit_nfc_reading))
        val successLabel = compose.onNodeWithText(strings.getString(R.string.transit_nfc_success))
        val reviewAction = compose.onNodeWithText(strings.getString(R.string.transit_nfc_update_balance))
        readingLabel.assertIsDisplayed()
        successLabel.assertDoesNotExist()
        val waitingFrame = scene.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(350)
        assertTrue(waitingFrame.sameAs(scene.captureToImage().asAndroidBitmap()))
        reviewAction.assertDoesNotExist()

        compose.runOnIdle { confirmed.value = true }
        compose.mainClock.advanceTimeByFrame()
        successLabel.assertIsDisplayed()
        val startOfSuccess = scene.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(240)
        assertFalse(startOfSuccess.sameAs(scene.captureToImage().asAndroidBitmap()))
        compose.mainClock.advanceTimeBy(368)
        val finalPose = scene.captureToImage().asAndroidBitmap()
        assertEquals(!remainsInNewPose, startOfSuccess.sameAs(finalPose))
        compose.mainClock.advanceTimeBy(64)
        assertTrue(finalPose.sameAs(scene.captureToImage().asAndroidBitmap()))
        reviewAction.assertDoesNotExist()

        compose.mainClock.autoAdvance = true
        compose.waitUntil(timeoutMillis = 2_000) { animationFinished.value }
        reviewAction.assertIsDisplayed()
    }

    @Test
    fun reviewSheet_keepsFullSaveButtonAboveNavigationAtBothHeights() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val cardName = "ICOCA"
        val stage = mutableStateOf(0)
        lateinit var dialogView: View
        val saved = mutableListOf<Set<String>>()
        val date = LocalDate.of(2026, 11, 18)
        val history = (1..20).map { TransitCardEntry(date, it, TransitCardEntryType.PURCHASE, -130, 500) }
        val scan = TransitCardScan("c".repeat(64), 0, 500, history, true)
        compose.setContent {
            ExpensesTheme {
                TransitNfcBottomSheet(cardName, onDismiss = {}, contentState = stage.value) { visibleStage ->
                    dialogView = LocalView.current
                    when (visibleStage) {
                        0 -> TransitCardPlacementHint(cardName, readerActive = true)
                        1 -> TransitNfcMascotStatus("icoca", confirmed = true, onAnimationFinished = {})
                        else -> TransitCardReview(
                            scan = scan,
                            preview = TransitCardImportPreview(emptySet(), emptySet()),
                            cardName = cardName,
                            onSave = { _, entries, _ ->
                                saved.add(entries.keys)
                                TransitCardImportResult(TransitCardImportStatus.SAVED)
                            },
                            onSaved = {},
                            onRescan = {}
                        )
                    }
                }
            }
        }
        val header = compose.onNodeWithText(cardName)
        header.assertIsDisplayed()
        val scanPosition = header.fetchSemanticsNode().boundsInRoot.top
        assertTrue(scanPosition < strings.displayMetrics.heightPixels * 0.45f)
        val holdInstruction = strings.getString(R.string.transit_nfc_hold_card, cardName)
        val antennaInstruction = strings.getString(R.string.transit_nfc_antenna_hint)
        assertTrue(holdInstruction.contains('\n'))
        assertTrue(antennaInstruction.contains('\n'))
        listOf(holdInstruction, antennaInstruction).forEach { instruction ->
            assertFullyVisibleAboveNavigation(compose.onNodeWithText(instruction), dialogView)
        }

        compose.runOnIdle { stage.value = 1 }
        compose.onNodeWithText(strings.getString(R.string.transit_nfc_success)).assertIsDisplayed()
        assertEquals(scanPosition, header.fetchSemanticsNode().boundsInRoot.top, 1f)

        compose.runOnIdle { stage.value = 2 }
        val balanceButton = compose.onNodeWithText(strings.getString(R.string.transit_nfc_update_balance))
        assertFullyVisibleAboveNavigation(balanceButton, dialogView)
        assertEquals(scanPosition, header.fetchSemanticsNode().boundsInRoot.top, 1f)
        balanceButton.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(listOf(emptySet<String>()), saved) }

        compose.onNodeWithText(strings.getString(R.string.transit_nfc_select_new)).performClick()
        val importButton = compose.onNodeWithText(strings.getString(R.string.transit_nfc_import_selected, history.size))
        assertFullyVisibleAboveNavigation(importButton, dialogView)
        importButton.performTouchInput { click(center) }
        compose.runOnIdle { assertEquals(history.map(scan::reference).toSet(), saved.last()) }

        header.performTouchInput {
            swipe(start = center, end = Offset(center.x, center.y - 290.dp.toPx()))
        }
        assertFullyVisibleAboveNavigation(importButton, dialogView)
        assertTrue(header.fetchSemanticsNode().boundsInRoot.top < scanPosition)
    }

    private fun assertFullyVisibleAboveNavigation(node: SemanticsNodeInteraction, dialogView: View) {
        node.assertIsDisplayed()
        val fullBounds = node.getUnclippedBoundsInRoot()
        val visibleBounds = node.getBoundsInRoot()
        assertEquals((fullBounds.bottom - fullBounds.top).value, (visibleBounds.bottom - visibleBounds.top).value, 1f)

        val safeBottom = compose.runOnIdle {
            val navigationBottom = ViewCompat.getRootWindowInsets(dialogView)
                ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
            (dialogView.height - navigationBottom) / dialogView.resources.displayMetrics.density
        }
        assertTrue("Expected bottom padding above the navigation bar", safeBottom - visibleBounds.bottom.value >= 31f)
    }

    @Test
    fun selectNewExpenses_leavesTopUpsAndPossibleDuplicatesOutOfBatch() {
        val strings = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val date = LocalDate.of(2026, 11, 18)
        val fare = TransitCardEntry(date, 23, TransitCardEntryType.TRANSPORT, -180, 400)
        val purchase = TransitCardEntry(date, 22, TransitCardEntryType.PURCHASE, -120, 580)
        val topUp = TransitCardEntry(date, 21, TransitCardEntryType.TOP_UP, 500, 700)
        val scan = TransitCardScan("b".repeat(64), 0, 400, listOf(fare, purchase, topUp), false)
        val preview = TransitCardImportPreview(emptySet(), setOf(scan.reference(purchase)))
        var submitted: Set<String>? = null

        compose.setContent {
            MaterialTheme {
                Column {
                    TransitCardReview(
                        scan = scan,
                        preview = preview,
                        cardName = "Suica",
                        onSave = { _, selected, _ ->
                            submitted = selected.keys
                            TransitCardImportResult(TransitCardImportStatus.SAVED, 1)
                        },
                        onSaved = {},
                        onRescan = {}
                    )
                }
            }
        }

        compose.onNodeWithText(strings.getString(R.string.transit_nfc_possible_duplicate)).assertIsDisplayed()
        compose.onNodeWithText(strings.getString(R.string.transit_nfc_select_new)).performClick()
        compose.onNodeWithText(strings.getString(R.string.transit_nfc_import_selected, 1)).performClick()
        compose.runOnIdle { assertEquals(setOf(scan.reference(fare)), submitted) }
    }
}
