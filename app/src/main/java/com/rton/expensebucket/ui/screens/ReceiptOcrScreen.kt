package com.rton.expensebucket.ui.screens

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.rton.expensebucket.ocr.ReceiptOcrEngine
import com.rton.expensebucket.ocr.ReceiptOcrResult
import com.rton.expensebucket.ui.model.TransactionPrefill
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptOcrScreen(
    receiptOcrEngine: ReceiptOcrEngine,
    title: String = "收據 OCR",
    emptyStateTitle: String = "拍一張收據或從相簿選圖",
    emptyStateSubtitle: String = "會自動抓總額與細項備註",
    onApplyPrefill: (TransactionPrefill) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val appLanguageTag = remember {
        context.resources.configuration.locales[0]?.toLanguageTag() ?: Locale.getDefault().toLanguageTag()
    }
    val appLanguageLabel = remember(appLanguageTag) {
        Locale.forLanguageTag(appLanguageTag).displayLanguage.ifBlank { "app 語言" }
    }

    var imageUri by remember { mutableStateOf<Uri?>(null) }
    var captureUri by remember { mutableStateOf<Uri?>(null) }
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

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            imageUri = captureUri
            errorMessage = null
        }
    }

    val requestCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            val uri = createReceiptImageUri(context)
            captureUri = uri
            takePictureLauncher.launch(uri)
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
        requestCameraPermission.launch(Manifest.permission.CAMERA)
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
            errorMessage = throwable.localizedMessage ?: "收據辨識失敗"
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
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
                    Text("拍攝收據", modifier = Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(
                    onClick = { pickImageLauncher.launch("image/*") },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Collections, contentDescription = null)
                    Text("選取圖片", modifier = Modifier.padding(start = 8.dp))
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
                        contentDescription = "收據",
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
                            if (isTranslating) "正在翻譯文字..." else "正在辨識收據...",
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
                        Text("翻譯成 $appLanguageLabel", modifier = Modifier.padding(start = 8.dp))
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
                        errorMessage = "翻譯失敗，請直接使用原文辨識結果"
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
                                if (showTranslatedResult) "目前顯示翻譯結果" else "目前顯示原文結果",
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
                                Text(if (showTranslatedResult) "切回原文" else "查看翻譯")
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
                                "收據辨識結果",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        Text(
                            "總額：${result.totalAmount?.let { formatEditableAmount(it) } ?: "未辨識"}",
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
                    label = { Text("收據總額") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("細項 / 備註") },
                    singleLine = false,
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                )

                Button(
                    onClick = {
                        onApplyPrefill(
                            TransactionPrefill(
                                amount = parsedAmount,
                                note = note,
                                isExpense = true,
                                date = System.currentTimeMillis(),
                                source = "ocr",
                                currency = "TWD",
                                exchangeRate = 1.0
                            )
                        )
                    },
                    enabled = canContinue,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("帶入記帳編輯")
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
                    Text("查看 OCR 文字", modifier = Modifier.padding(start = 6.dp))
                }

                AnimatedVisibility(visible = showRawText) {
                    OutlinedCard(shape = RoundedCornerShape(14.dp)) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                "原始文字",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(result.rawText.ifBlank { "(空白)" })
                            result.translatedText?.let { translated ->
                                HorizontalDivider()
                                Text(
                                    "翻譯後文字",
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

private fun createReceiptImageUri(context: android.content.Context): Uri {
    val imageDir = File(context.cacheDir, "receipt_images").apply { mkdirs() }
    val imageFile = File(imageDir, "receipt_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        imageFile
    )
}

private fun formatEditableAmount(amount: Double): String {
    return if (amount == amount.toLong().toDouble()) {
        amount.toLong().toString()
    } else {
        "%.2f".format(amount)
    }
}
