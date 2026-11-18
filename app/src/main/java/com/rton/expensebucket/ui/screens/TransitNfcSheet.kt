package com.rton.expensebucket.ui.screens

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.NfcAdapter
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contactless
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.rton.expensebucket.R
import com.rton.expensebucket.data.repository.TransitCardImportPreview
import com.rton.expensebucket.data.repository.TransitCardImportResult
import com.rton.expensebucket.data.repository.TransitCardImportStatus
import com.rton.expensebucket.nfc.TransitCardDetailType
import com.rton.expensebucket.nfc.TransitCardEntry
import com.rton.expensebucket.nfc.TransitCardEntryType
import com.rton.expensebucket.nfc.TransitCardReader
import com.rton.expensebucket.nfc.TransitCardScan
import com.rton.expensebucket.ui.components.TransitReadMascot
import com.rton.expensebucket.ui.theme.semanticColors
import com.rton.expensebucket.ui.util.CurrencyFormats
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class ScanState { WAITING, READING, DISABLED, UNAVAILABLE, READ_FAILED, WRONG_CARD, SUCCESS, REVIEW }

private const val MIN_READING_FEEDBACK_MS = 250L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TransitNfcSheet(
    cardName: String,
    cardIcon: String,
    onDismiss: () -> Unit,
    onPreview: suspend (TransitCardScan) -> TransitCardImportPreview?,
    onSave: suspend (TransitCardScan, Map<String, String>, Double?) -> TransitCardImportResult,
    onSaved: (Int) -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>()
            .firstOrNull()
    }
    val strings = context.resources
    val view = LocalView.current
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val scope = rememberCoroutineScope()
    val previewCard by rememberUpdatedState(onPreview)
    var state by remember { mutableStateOf(ScanState.WAITING) }
    var readerActive by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf<Pair<TransitCardScan, TransitCardImportPreview>?>(null) }

    if (state == ScanState.WAITING || state == ScanState.READING || state == ScanState.DISABLED) {
        LifecycleResumeEffect(adapter, activity) {
            val active = AtomicBoolean(true)
            val reading = AtomicBoolean(false)
            var readerEnabled = false
            when {
                adapter == null || activity == null -> state = ScanState.UNAVAILABLE
                !adapter.isEnabled -> state = ScanState.DISABLED
                else -> {
                    state = ScanState.WAITING
                    try {
                        adapter.enableReaderMode(
                            activity,
                            { tag ->
                                if (active.get() && reading.compareAndSet(false, true)) {
                                    scope.launch {
                                        if (!active.get()) return@launch
                                        val detectedAt = SystemClock.elapsedRealtime()
                                        state = ScanState.READING
                                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                        try {
                                            val scan = withContext(Dispatchers.IO) { TransitCardReader.read(tag) }
                                            if (!active.get()) return@launch
                                            val preview = previewCard(scan)
                                            if (!active.get()) return@launch
                                            if (preview == null) {
                                                state = ScanState.WRONG_CARD
                                            } else {
                                                val elapsed = SystemClock.elapsedRealtime() - detectedAt
                                                delay((MIN_READING_FEEDBACK_MS - elapsed).coerceAtLeast(0L))
                                                if (!active.get()) return@launch
                                                review = scan to preview
                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                                    view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                                                }
                                                state = ScanState.SUCCESS
                                            }
                                        } catch (canceled: CancellationException) {
                                            throw canceled
                                        } catch (_: Exception) {
                                            if (active.get()) state = ScanState.READ_FAILED
                                        } finally {
                                            if (active.get() && state == ScanState.READING) {
                                                reading.set(false)
                                                state = ScanState.WAITING
                                            }
                                        }
                                    }
                                }
                            },
                            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                            null
                        )
                        readerEnabled = true
                        readerActive = true
                    } catch (_: UnsupportedOperationException) {
                        state = ScanState.UNAVAILABLE
                    } catch (_: SecurityException) {
                        state = ScanState.UNAVAILABLE
                    }
                }
            }
            onPauseOrDispose {
                active.set(false)
                readerActive = false
                if (state == ScanState.READING) state = ScanState.WAITING
                if (readerEnabled && activity != null) {
                    runCatching { adapter?.disableReaderMode(activity) }
                }
            }
        }
    }

    // Keep the same mascot mounted while its status changes from reading to confirmed.
    val contentState = if (state == ScanState.SUCCESS) ScanState.READING else state
    TransitNfcBottomSheet(cardName = cardName, onDismiss = onDismiss, contentState = contentState) { visibleState ->
        when (visibleState) {
            ScanState.WAITING -> TransitCardPlacementHint(cardName, readerActive)
            ScanState.READING -> TransitNfcMascotStatus(
                cardIcon = cardIcon,
                confirmed = state == ScanState.SUCCESS || state == ScanState.REVIEW,
                onAnimationFinished = { if (state == ScanState.SUCCESS) state = ScanState.REVIEW }
            )
            ScanState.REVIEW -> review?.let { (scan, preview) ->
                TransitCardReview(
                    scan = scan,
                    preview = preview,
                    cardName = cardName,
                    onSave = onSave,
                    onSaved = onSaved,
                    onRescan = { state = ScanState.WAITING }
                )
            }
            else -> Column(
                modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val message = when (visibleState) {
                    ScanState.DISABLED -> R.string.transit_nfc_disabled
                    ScanState.UNAVAILABLE -> R.string.transit_nfc_unavailable
                    ScanState.WRONG_CARD -> R.string.transit_nfc_wrong_card
                    else -> R.string.transit_nfc_read_error
                }
                Text(strings.getString(message), style = MaterialTheme.typography.bodyLarge)
                when (visibleState) {
                    ScanState.DISABLED -> Button(
                        onClick = { activity?.startActivity(Intent(Settings.ACTION_NFC_SETTINGS)) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.getString(R.string.transit_nfc_open_settings)) }
                    ScanState.READ_FAILED, ScanState.WRONG_CARD -> Button(
                        onClick = { state = ScanState.WAITING },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(strings.getString(R.string.transit_nfc_retry)) }
                    else -> Unit
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> TransitNfcBottomSheet(
    cardName: String,
    onDismiss: () -> Unit,
    contentState: T,
    content: @Composable (T) -> Unit
) {
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val scope = rememberCoroutineScope()
    val dismiss by rememberUpdatedState(onDismiss)
    val closeLabel = stringResource(R.string.cancel)
    LaunchedEffect(sheetState) {
        snapshotFlow { sheetState.currentValue }.collect { value ->
            if (value == SheetValue.Hidden) dismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (sheetState.currentValue == SheetValue.Expanded) {
                scope.launch { sheetState.partialExpand() }
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        BoxWithConstraints(
            Modifier.fillMaxSize().safeDrawingPadding()
                .windowInsetsPadding(WindowInsets.safeGestures.only(WindowInsetsSides.Bottom))
        ) {
            val peekHeight = maxOf(maxHeight * 0.68f, 500.dp)
                .coerceAtMost((maxHeight - 56.dp).coerceAtLeast(0.dp))
            val initialOffset = maxHeight - peekHeight
            BottomSheetScaffold(
                modifier = Modifier.clipToBounds(),
                scaffoldState = scaffoldState,
                sheetPeekHeight = peekHeight,
                sheetContainerColor = MaterialTheme.colorScheme.surface,
                containerColor = Color.Transparent,
                sheetContent = {
                    // Keep both drag anchors independent of the scan state, and size the body to the visible area.
                    Box(Modifier.fillMaxSize()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .layout { measurable, constraints ->
                                    val offset = runCatching { sheetState.requireOffset().roundToInt() }
                                        .getOrDefault(initialOffset.roundToPx())
                                    val visibleHeight = (constraints.maxHeight - offset)
                                        .coerceIn(0, constraints.maxHeight)
                                    val body = measurable.measure(
                                        constraints.copy(minHeight = visibleHeight, maxHeight = visibleHeight)
                                    )
                                    layout(body.width, body.height) { body.placeRelative(0, 0) }
                                }
                                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
                                .semantics { paneTitle = cardName },
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(cardName, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                                IconButton(onClick = onDismiss) {
                                    Icon(Icons.Filled.Close, contentDescription = closeLabel)
                                }
                            }
                            Crossfade(
                                targetState = contentState,
                                modifier = Modifier.weight(1f).fillMaxWidth().clipToBounds(),
                                animationSpec = tween(160),
                                label = "NFC scan status"
                            ) { visibleState ->
                                Box(Modifier.fillMaxSize()) { content(visibleState) }
                            }
                        }
                    }
                }
            ) {
                Box(
                    Modifier.fillMaxSize().clickable(
                        interactionSource = null,
                        indication = null,
                        onClickLabel = closeLabel,
                        onClick = onDismiss
                    )
                )
            }
        }
    }
}

@Composable
internal fun TransitNfcMascotStatus(
    cardIcon: String,
    confirmed: Boolean,
    onAnimationFinished: () -> Unit
) {
    val palette = MaterialTheme.colorScheme
    val semantic = MaterialTheme.semanticColors
    val isIcoca = cardIcon == "icoca"
    val mascotProgress = remember(cardIcon) { Animatable(0f) }
    val finish by rememberUpdatedState(onAnimationFinished)
    LaunchedEffect(confirmed, cardIcon) {
        if (confirmed) {
            if (isIcoca) {
                mascotProgress.animateTo(0f, keyframes {
                    durationMillis = 560
                    0f at 0 using LinearOutSlowInEasing
                    1f at 180
                    1f at 360
                })
            } else {
                mascotProgress.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            }
            delay(220)
            finish()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
    ) {
        Box(
            modifier = Modifier.size(104.dp).graphicsLayer {
                val amount = if (isIcoca) mascotProgress.value else 0f
                val scale = 1f + 0.05f * amount
                scaleX = scale
                scaleY = scale
                translationY = -4.dp.toPx() * amount
            },
            contentAlignment = Alignment.Center
        ) {
            Box(Modifier.size(100.dp).background(palette.primaryContainer, CircleShape))
            TransitReadMascot(cardIcon, mascotProgress.asState(), Modifier.size(88.dp))
            if (confirmed) {
                val badgePosition = Modifier.align(Alignment.BottomEnd)
                Box(
                    modifier = (if (isIcoca) badgePosition else badgePosition.offset(x = 8.dp, y = 4.dp))
                        .size(34.dp).background(palette.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = semantic.income,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Text(
            text = stringResource(if (confirmed) R.string.transit_nfc_success else R.string.transit_nfc_reading),
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium,
            minLines = 2
        )
    }
}

@Composable
internal fun TransitCardPlacementHint(
    cardName: String,
    readerActive: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val viewportHeight = maxHeight
        Layout(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 8.dp),
            content = {
                TransitNfcPhoneGuide(readerActive)
                TransitNfcInstructions(cardName)
            }
        ) { measurables, constraints ->
            val spacing = 12.dp.roundToPx()
            val childConstraints = constraints.copy(minWidth = 0)
            val instructions = measurables[1].measure(childConstraints)
            // Give the localized text the height it needs, then fit the phone into the remaining space.
            val phoneHeight = (viewportHeight.roundToPx() - 16.dp.roundToPx() - spacing - instructions.height)
                .coerceIn(112.dp.roundToPx(), 234.dp.roundToPx())
            val phone = measurables[0].measure(childConstraints.copy(maxHeight = phoneHeight))
            layout(constraints.maxWidth, phone.height + spacing + instructions.height) {
                phone.placeRelative((constraints.maxWidth - phone.width) / 2, 0)
                instructions.placeRelative((constraints.maxWidth - instructions.width) / 2, phone.height + spacing)
            }
        }
    }
}

@Composable
private fun TransitNfcPhoneGuide(readerActive: Boolean) {
    val colors = MaterialTheme.colorScheme
    val diagramDescription = stringResource(R.string.transit_nfc_antenna_diagram)
    val pulseProgress = if (readerActive) {
        rememberInfiniteTransition(label = "NFC reader active").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(durationMillis = 1800, easing = LinearEasing)),
            label = "NFC ripples"
        )
    } else null

    val phoneShape = RoundedCornerShape(20)
    BoxWithConstraints(
        modifier = Modifier
            .aspectRatio(9f / 19.5f, matchHeightConstraintsFirst = true)
            .background(colors.surfaceContainerHigh, phoneShape)
            .border(2.dp, if (readerActive) colors.primary else colors.outline, phoneShape)
            .semantics { contentDescription = diagramDescription }
    ) {
        val phoneWidth = maxWidth
        Row(
            modifier = Modifier.align(Alignment.TopStart)
                .padding(start = phoneWidth * (12f / 108f), top = phoneWidth * (12f / 108f))
                .background(colors.outlineVariant, CircleShape)
                .padding(horizontal = phoneWidth * (5f / 108f), vertical = phoneWidth * (4f / 108f)),
            horizontalArrangement = Arrangement.spacedBy(phoneWidth * (4f / 108f))
        ) {
            repeat(2) { Box(Modifier.size(phoneWidth * (7f / 108f)).background(colors.outline, CircleShape)) }
        }
        Box(
            modifier = Modifier.align(Alignment.TopCenter)
                .padding(top = phoneWidth * 0.36f)
                .size(phoneWidth * 0.92f)
                .drawBehind {
                    val progress = pulseProgress?.value ?: return@drawBehind
                    repeat(3) { ring ->
                        val phase = (progress + ring / 3f) % 1f
                        val radiusFraction = LinearOutSlowInEasing.transform(phase)
                        val fadeIn = (phase / 0.22f).coerceIn(0f, 1f)
                        val smoothFadeIn = fadeIn * fadeIn * (3f - 2f * fadeIn)
                        val fadeOutProgress = ((phase - 0.22f) / 0.78f).coerceIn(0f, 1f)
                        val fadeOut = (1f - fadeOutProgress) * (1f - fadeOutProgress)
                        val alpha = 0.56f * smoothFadeIn * fadeOut
                        if (alpha > 0.005f) {
                            drawCircle(
                                color = colors.primary.copy(alpha = alpha),
                                radius = size.minDimension * (0.29f + 0.19f * radiusFraction),
                                style = Stroke(width = (2.2f - 0.8f * radiusFraction).dp.toPx())
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Contactless,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(phoneWidth * (2f / 3f))
            )
        }
    }
}

@Composable
private fun TransitNfcInstructions(cardName: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.transit_nfc_hold_card, cardName),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = stringResource(R.string.transit_nfc_antenna_hint),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
internal fun TransitCardReview(
    scan: TransitCardScan,
    preview: TransitCardImportPreview,
    cardName: String,
    onSave: suspend (TransitCardScan, Map<String, String>, Double?) -> TransitCardImportResult,
    onSaved: (Int) -> Unit,
    onRescan: () -> Unit
) {
    val strings = LocalContext.current.resources
    val scope = rememberCoroutineScope()
    val dateFormat = remember { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM) }
    val timeFormat = remember { DateTimeFormatter.ofPattern("HH:mm") }
    var selected by remember(scan) { mutableStateOf(emptySet<String>()) }
    var saving by remember(scan) { mutableStateOf(false) }
    var status by remember(scan) { mutableStateOf<TransitCardImportStatus?>(null) }
    var failed by remember(scan) { mutableStateOf(false) }
    var rateInput by remember(scan) { mutableStateOf("") }
    val manualRate = rateInput.toDoubleOrNull()?.takeIf { it > 0 && it.isFinite() }
    val rescanRequired = status == TransitCardImportStatus.WRONG_CARD || status == TransitCardImportStatus.STALE_SCAN
    val selectable = scan.history.filter { it.expenseAmount != null && scan.reference(it) !in preview.imported }
    val selectableWithoutDuplicates = selectable.map(scan::reference).filterNot { it in preview.possibleDuplicates }.toSet()

    val showManualRate = status == TransitCardImportStatus.RATE_REQUIRED && selected.isNotEmpty()
    val error = when {
        status == TransitCardImportStatus.STALE_SCAN -> R.string.transit_nfc_stale_scan
        status == TransitCardImportStatus.WRONG_CARD -> R.string.transit_nfc_wrong_card
        failed -> R.string.transit_nfc_save_error
        else -> null
    }
    val buttonLabel = when {
        saving -> strings.getString(R.string.transit_nfc_saving)
        rescanRequired -> strings.getString(R.string.transit_nfc_retry)
        selected.isEmpty() -> strings.getString(R.string.transit_nfc_update_balance)
        else -> strings.getString(R.string.transit_nfc_import_selected, selected.size)
    }
    val historyListState = rememberLazyListState()
    LaunchedEffect(showManualRate, error) {
        if (showManualRate || error != null) historyListState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LazyColumn(state = historyListState, modifier = Modifier.weight(1f)) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        strings.getString(
                            R.string.transit_nfc_read_balance,
                            CurrencyFormats.formatAmount("JPY", scan.balance.toDouble())
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                    error?.let { Text(strings.getString(it), color = MaterialTheme.colorScheme.error) }
                    if (showManualRate) {
                        OutlinedTextField(
                            value = rateInput,
                            onValueChange = { input ->
                                rateInput = input.replace(',', '.').filter { it in '0'..'9' || it == '.' }.take(12)
                            },
                            label = { Text(strings.getString(R.string.transit_nfc_manual_rate)) },
                            supportingText = { Text(strings.getString(R.string.transit_nfc_rate_unavailable)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (scan.historyIncomplete) {
                        Text(strings.getString(R.string.transit_nfc_partial_history), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onRescan, enabled = !saving) {
                            Text(strings.getString(R.string.transit_nfc_scan_again))
                        }
                    }
                    if (scan.history.isEmpty()) {
                        Text(strings.getString(R.string.transit_nfc_empty_history), style = MaterialTheme.typography.bodyMedium)
                    }
                    if (selectableWithoutDuplicates.isNotEmpty()) {
                        val allNewExpensesSelected = selected.containsAll(selectableWithoutDuplicates)
                        TextButton(
                            onClick = {
                                selected = if (allNewExpensesSelected) selected - selectableWithoutDuplicates
                                else selected + selectableWithoutDuplicates
                            },
                            enabled = !saving
                        ) {
                            val label = if (allNewExpensesSelected) R.string.transit_nfc_clear_selection
                            else R.string.transit_nfc_select_new
                            Text(strings.getString(label))
                        }
                    }
                }
            }
            items(scan.history, key = scan::reference) { entry ->
                val reference = scan.reference(entry)
                val imported = reference in preview.imported
                val possibleDuplicate = reference in preview.possibleDuplicates
                val canSelect = entry.expenseAmount != null && !imported
                val checked = reference in selected
                val rowModifier = if (canSelect) {
                    Modifier.toggleable(value = checked, enabled = !saving, role = Role.Checkbox) {
                        selected = if (it) selected + reference else selected - reference
                    }
                } else Modifier
                Row(
                    modifier = rowModifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (canSelect) Checkbox(checked, onCheckedChange = null, enabled = !saving)
                    Column(Modifier.weight(1f)) {
                        val title = entry.displayTitle(strings)
                        val typeLabel = entry.detailLabel(strings)
                        val dateTimeText = buildString {
                            append(dateFormat.format(entry.date))
                            entry.time?.let {
                                append(" ")
                                append(timeFormat.format(it))
                            }
                            if (entry.entryStation != null || entry.exitStation != null || entry.busOperator != null) {
                                append(" · ")
                                append(typeLabel)
                            }
                        }
                        Text(title, style = MaterialTheme.typography.bodyLarge)
                        Text(dateTimeText, style = MaterialTheme.typography.bodySmall)
                        val detail = when {
                            imported -> R.string.transit_nfc_already_imported
                            possibleDuplicate -> R.string.transit_nfc_possible_duplicate
                            entry.type == TransitCardEntryType.TOP_UP -> R.string.transit_nfc_top_up_only
                            else -> null
                        }
                        detail?.let { Text(strings.getString(it), style = MaterialTheme.typography.bodySmall) }
                    }
                    val amount = entry.balanceChange?.let { CurrencyFormats.formatAmount("JPY", it.toDouble()) }
                        ?: strings.getString(R.string.transit_nfc_unknown_amount)
                    Text(amount, style = MaterialTheme.typography.bodyMedium)
                }
                HorizontalDivider()
            }
        }

        Button(
            enabled = !saving && (!showManualRate || manualRate != null),
            onClick = {
                if (rescanRequired) {
                    onRescan()
                } else {
                    saving = true
                    failed = false
                    val notes = selectable.filter { scan.reference(it) in selected }.associate {
                        scan.reference(it) to it.importNote(cardName, strings, timeFormat)
                    }
                    scope.launch {
                        try {
                            val result = onSave(scan, notes, manualRate)
                            status = result.status
                            if (result.status == TransitCardImportStatus.SAVED) onSaved(result.importedCount)
                        } catch (canceled: CancellationException) {
                            throw canceled
                        } catch (_: Exception) {
                            failed = true
                        } finally {
                            saving = false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) { Text(buttonLabel) }
    }
}

private fun TransitCardEntry.detailLabel(strings: android.content.res.Resources): String =
    strings.getString(detailType?.label ?: type.label)

private fun TransitCardEntry.displayTitle(strings: android.content.res.Resources): String = when {
    entryStation != null && exitStation != null -> "${entryStation.name} → ${exitStation.name}"
    entryStation != null -> entryStation.name
    exitStation != null -> exitStation.name
    busOperator != null -> busOperator
    else -> detailLabel(strings)
}

private fun TransitCardEntry.importNote(
    cardName: String,
    strings: android.content.res.Resources,
    timeFormat: DateTimeFormatter
): String {
    val label = detailLabel(strings)
    val detail = when {
        entryStation != null && exitStation != null -> "${entryStation.name} → ${exitStation.name}"
        entryStation != null -> "${entryStation.name} ($label)"
        exitStation != null -> "${exitStation.name} ($label)"
        busOperator != null -> "$busOperator ($label)"
        time != null -> "$label (${timeFormat.format(time)})"
        else -> label
    }
    return "$cardName · $detail"
}

private val TransitCardDetailType.label: Int
    get() = when (this) {
        TransitCardDetailType.GATE_EXIT -> R.string.transit_nfc_detail_gate_exit
        TransitCardDetailType.TICKET_PURCHASE -> R.string.transit_nfc_detail_ticket_purchase
        TransitCardDetailType.FARE_ADJUSTMENT -> R.string.transit_nfc_detail_fare_adjustment
        TransitCardDetailType.COUNTER_EXIT -> R.string.transit_nfc_detail_counter_exit
        TransitCardDetailType.SHINKANSEN -> R.string.transit_nfc_detail_shinkansen
        TransitCardDetailType.BUS -> R.string.transit_nfc_detail_bus
        TransitCardDetailType.VENDING_MACHINE -> R.string.transit_nfc_detail_vending_machine
        TransitCardDetailType.RETAIL -> R.string.transit_nfc_detail_retail
        TransitCardDetailType.AUTO_TOP_UP -> R.string.transit_nfc_detail_auto_top_up
        TransitCardDetailType.CASH_TOP_UP -> R.string.transit_nfc_detail_cash_top_up
        TransitCardDetailType.REFUND -> R.string.transit_nfc_refund
    }

private val TransitCardEntryType.label: Int
    get() = when (this) {
        TransitCardEntryType.TRANSPORT -> R.string.transit_nfc_transport
        TransitCardEntryType.PURCHASE -> R.string.transit_nfc_purchase
        TransitCardEntryType.TOP_UP -> R.string.stored_value_top_up
        TransitCardEntryType.REFUND -> R.string.transit_nfc_refund
        TransitCardEntryType.OTHER -> R.string.other
    }
