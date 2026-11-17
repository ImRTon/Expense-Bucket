package com.rton.expensebucket.ui.screens

import androidx.compose.ui.res.stringResource
import com.rton.expensebucket.R
import android.app.Activity
import android.content.IntentSender
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.google.mlkit.common.MlKitException
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.rton.expensebucket.ocr.ReceiptOcrEngine
import com.rton.expensebucket.ocr.ReceiptOcrResult
import com.rton.expensebucket.ui.model.TransactionPrefill
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptOcrScreen(
    receiptOcrEngine: ReceiptOcrEngine,
    title: String = stringResource(R.string.receipt_ocr),
    emptyStateTitle: String = stringResource(R.string.receipt_empty_title),
    emptyStateSubtitle: String = stringResource(R.string.receipt_empty_subtitle),
    onApplyPrefill: (TransactionPrefill) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalContext.current.resources

    val context = LocalContext.current
    val appLocale = strings.configuration.locales[0]
    val appLanguageTag = appLocale.toLanguageTag()
    val appLanguageLabel = remember(appLanguageTag) {
        appLocale.getDisplayLanguage(appLocale).ifBlank { strings.getString(R.string.app_language) }
    }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var rawReceiptResult by remember { mutableStateOf<ReceiptOcrResult?>(null) }
    var translatedReceiptResult by remember { mutableStateOf<ReceiptOcrResult?>(null) }
    var showTranslatedResult by remember { mutableStateOf(false) }
    var amountText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var autoOpenedCamera by remember { mutableStateOf(false) }
    var showRawText by remember { mutableStateOf(false) }

    val scannerOptions = remember {
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(false)
            .setPageLimit(1)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
    }
    val documentScanner = remember(scannerOptions) {
        GmsDocumentScanning.getClient(scannerOptions)
    }

    val documentScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            imageUri = scanResult?.pages?.firstOrNull()?.imageUri
            errorMessage = if (imageUri == null) strings.getString(R.string.receipt_scan_no_image) else null
        } else if (result.resultCode != Activity.RESULT_CANCELED) {
            errorMessage = strings.getString(R.string.receipt_scan_incomplete)
        }
    }

    val pickImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { pickedUri ->
        if (pickedUri != null) {
            imageUri = pickedUri
            errorMessage = null
        }
    }

    fun launchCamera() {
        val activity = context.findActivity()
        if (activity == null) {
            errorMessage = strings.getString(R.string.scanner_unavailable)
            return
        }

        errorMessage = null
        documentScanner.getStartScanIntent(activity)
            .addOnSuccessListener { intentSender ->
                documentScannerLauncher.launch(
                    IntentSenderRequest.Builder(intentSender).build()
                )
            }
            .addOnFailureListener { throwable ->
                errorMessage = when ((throwable as? MlKitException)?.errorCode) {
                    MlKitException.UNSUPPORTED -> strings.getString(R.string.scanner_unsupported)
                    else -> throwable.localizedMessage ?: strings.getString(R.string.receipt_scan_start_failed)
                }
            }
    }

    fun applyAnalysis(result: ReceiptOcrResult) {
        amountText = result.totalAmount?.let { formatEditableAmount(it) }.orEmpty()
        note = result.note
    }

    LaunchedEffect(Unit) {
        if (!autoOpenedCamera) {
            autoOpenedCamera = true
            launchCamera()
        }
    }

    LaunchedEffect(imageUri) {
        val uri = imageUri ?: return@LaunchedEffect
        isProcessing = true
        errorMessage = null
        rawReceiptResult = null
        translatedReceiptResult = null
        showTranslatedResult = false
        runCatching {
            receiptOcrEngine.processReceipt(context, uri)
        }.onSuccess { result ->
            rawReceiptResult = result
            applyAnalysis(result)
        }.onFailure { throwable ->
            errorMessage = throwable.localizedMessage ?: strings.getString(R.string.receipt_recognition_failed)
        }
        isProcessing = false
    }

    val receiptResult = if (showTranslatedResult) {
        translatedReceiptResult ?: rawReceiptResult
    } else {
        rawReceiptResult
    }
    val parsedAmount = amountText.toDoubleOrNull()
    val canContinue = parsedAmount?.let { it > 0 } == true
    val canTranslate = rawReceiptResult?.let {
        shouldOfferTranslation(
            detectedLanguageTag = it.detectedLanguageTag,
            targetLanguageTag = appLanguageTag,
            translatedText = translatedReceiptResult?.translatedText
        )
    } == true

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.getString(R.string.back))
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { launchCamera() },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.CameraAlt, contentDescription = null)
                    Text(strings.getString(R.string.scan_receipt), modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Collections, contentDescription = null)
                    Text(strings.getString(R.string.choose_image), modifier = Modifier.padding(start = 8.dp))
                }
            }

            if (imageUri != null) {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageUri),
                        contentDescription = strings.getString(R.string.receipt),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .clip(RoundedCornerShape(18.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            } else {
                OutlinedCard(shape = RoundedCornerShape(18.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.ReceiptLong,
                                contentDescription = null,
                                modifier = Modifier.height(40.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Text(emptyStateTitle, style = MaterialTheme.typography.titleMedium)
                            Text(
                                emptyStateSubtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isProcessing || isTranslating) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(24.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            if (isTranslating) strings.getString(R.string.translating_text) else strings.getString(R.string.recognizing_receipt),
                            modifier = Modifier.padding(start = 12.dp)
                        )
                    }
                }
            }

            errorMessage?.let { error ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        error,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }

            receiptResult?.let { result ->
                if (canTranslate && !isTranslating) {
                    FilledTonalButton(
                        onClick = {
                            isTranslating = true
                            errorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Translate, contentDescription = null)
                        Text(strings.getString(R.string.translate_to, appLanguageLabel), modifier = Modifier.padding(start = 8.dp))
                    }
                }

                LaunchedEffect(isTranslating, result.rawText, result.detectedLanguageTag, appLanguageTag) {
                    if (!isTranslating) return@LaunchedEffect

                    val translated = receiptOcrEngine.translateToLanguage(
                        text = result.rawText,
                        sourceLanguageTag = result.detectedLanguageTag,
                        targetLanguageTag = appLanguageTag
                    )

                    if (translated.isNullOrBlank()) {
                        errorMessage = strings.getString(R.string.translation_failed)
                    } else {
                        val translatedResult = receiptOcrEngine.applyTranslation(
                            rawText = result.rawText,
                            detectedLanguageTag = result.detectedLanguageTag,
                            translatedText = translated
                        )
                        translatedReceiptResult = translatedResult
                        showTranslatedResult = true
                        applyAnalysis(translatedResult)
                    }
                    isTranslating = false
                }

                if (translatedReceiptResult != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.45f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Translate, contentDescription = null)
                            Text(
                                if (showTranslatedResult) strings.getString(R.string.showing_translation) else strings.getString(R.string.showing_original),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    showTranslatedResult = !showTranslatedResult
                                    applyAnalysis(
                                        if (showTranslatedResult) {
                                            translatedReceiptResult ?: rawReceiptResult ?: result
                                        } else {
                                            rawReceiptResult ?: result
                                        }
                                    )
                                }
                            ) {
                                Text(if (showTranslatedResult) strings.getString(R.string.switch_to_original) else strings.getString(R.string.view_translation))
                            }
                        }
                    }
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Text(
                                strings.getString(R.string.receipt_result),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(
                            strings.getString(R.string.recognized_total, result.totalAmount?.let { formatEditableAmount(it) } ?: strings.getString(R.string.not_recognized)),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        if (result.lineItems.isNotEmpty()) {
                            Text(
                                result.lineItems.joinToString("\n"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { newValue ->
                        if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                            amountText = newValue
                        }
                    },
                    label = { Text(strings.getString(R.string.receipt_total)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text(strings.getString(R.string.items_note)) },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Button(
                    onClick = {
                        val detectedCurrency = receiptResult?.detectedCurrency ?: "TWD"
                        onApplyPrefill(
                            TransactionPrefill(
                                amount = parsedAmount,
                                note = note,
                                isExpense = true,
                                date = System.currentTimeMillis(),
                                source = "ocr",
                                currency = detectedCurrency,
                                exchangeRate = if (detectedCurrency == "TWD") 1.0 else 0.0
                            )
                        )
                    },
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(strings.getString(R.string.open_transaction_editor))
                }

                HorizontalDivider()

                TextButton(
                    onClick = { showRawText = !showRawText },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (showRawText) {
                        Icon(Icons.Filled.ExpandLess, contentDescription = null)
                    } else {
                        Icon(Icons.Filled.ExpandMore, contentDescription = null)
                    }
                    Text(strings.getString(R.string.view_ocr_text), modifier = Modifier.padding(start = 6.dp))
                }

                AnimatedVisibility(visible = showRawText) {
                    OutlinedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        SelectionContainer {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    strings.getString(R.string.original_text),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(result.rawText.ifBlank { strings.getString(R.string.empty_text) })
                                result.translatedText?.let { translated ->
                                    HorizontalDivider()
                                    Text(
                                        strings.getString(R.string.translated_text),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(translated)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun shouldOfferTranslation(
    detectedLanguageTag: String?,
    targetLanguageTag: String,
    translatedText: String?
): Boolean {
    if (translatedText != null || detectedLanguageTag.isNullOrBlank()) return false
    val sourceBase = detectedLanguageTag.substringBefore('-').lowercase()
    val targetBase = targetLanguageTag.substringBefore('-').lowercase()
    return sourceBase != targetBase
}

private fun formatEditableAmount(amount: Double): String {
    return if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        "%.2f".format(amount)
    }
}

private fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}
