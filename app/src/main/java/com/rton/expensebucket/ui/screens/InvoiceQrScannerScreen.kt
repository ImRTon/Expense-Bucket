package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import com.rton.expensebucket.R
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.rton.expensebucket.ocr.InvoiceQrPayload
import com.rton.expensebucket.ocr.ReceiptOcrEngine
import com.rton.expensebucket.ocr.TaiwanInvoiceOcrMetadataParser
import com.rton.expensebucket.ocr.TaiwanInvoiceQrParser
import com.rton.expensebucket.ocr.TaiwanInvoiceQrResult
import com.rton.expensebucket.ui.model.TransactionPrefill
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvoiceQrScannerScreen(
    receiptOcrEngine: ReceiptOcrEngine,
    onApplyPrefill: (TransactionPrefill) -> Unit,
    onBack: () -> Unit
) {
    val strings = LocalContext.current.resources

    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var autoRequestedPermission by remember { mutableStateOf(false) }
    var leftDetected by remember { mutableStateOf(false) }
    var rightDetected by remember { mutableStateOf(false) }
    var scanResult by remember { mutableStateOf<TaiwanInvoiceQrResult?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isResolvingMetadata by remember { mutableStateOf(false) }

    val requestCameraPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            errorMessage = strings.getString(R.string.invoice_qr_camera_required)
        }
    }

    LaunchedEffect(hasCameraPermission) {
        if (!hasCameraPermission && !autoRequestedPermission) {
            autoRequestedPermission = true
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    val currentResultState = rememberUpdatedState(scanResult)
    val currentResolvingState = rememberUpdatedState(isResolvingMetadata)

    DisposableEffect(hasCameraPermission, lifecycleOwner, previewView) {
        if (!hasCameraPermission) {
            onDispose { }
        } else {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            val analysisExecutor = Executors.newSingleThreadExecutor()
            val analyzer = InvoiceQrAnalyzer(
                isPaused = { currentResultState.value != null || currentResolvingState.value },
                onFrameEvaluated = { left, right ->
                    leftDetected = left
                    rightDetected = right
                },
                onPairDetected = { leftPayload, rightPayload ->
                    if (currentResultState.value == null && !currentResolvingState.value) {
                        runCatching {
                            TaiwanInvoiceQrParser.parse(leftPayload, rightPayload)
                        }.onSuccess { parsed ->
                            errorMessage = null
                            isResolvingMetadata = true
                            scanResult = parsed
                        }.onFailure { throwable ->
                            errorMessage = throwable.localizedMessage?.let { strings.localizedLabel(it) } ?: strings.getString(R.string.invoice_qr_failed)
                        }
                    }
                }
            )

            bindCameraUseCases(
                context = context,
                lifecycleOwner = lifecycleOwner,
                previewView = previewView,
                cameraProviderFuture = cameraProviderFuture,
                analyzer = analyzer,
                analysisExecutor = analysisExecutor
            )

            onDispose {
                analyzer.close()
                cameraProviderFuture.getOrNull()?.unbindAll()
                analysisExecutor.shutdown()
            }
        }
    }

    LaunchedEffect(scanResult?.invoiceNumber, isResolvingMetadata) {
        val result = scanResult ?: return@LaunchedEffect
        if (!isResolvingMetadata) return@LaunchedEffect

        val previewBitmap = previewView.bitmap?.copy(android.graphics.Bitmap.Config.ARGB_8888, false)
        val metadata = previewBitmap?.let { bitmap ->
            runCatching {
                val ocrResult = receiptOcrEngine.processBitmap(
                    bitmap = bitmap,
                    includeJapaneseRecognizer = false
                )
                TaiwanInvoiceOcrMetadataParser.parse(
                    rawText = ocrResult.rawText,
                    fallbackDate = result.invoiceDate
                )
            }.getOrNull()
        }

        scanResult = result.copy(metadata = metadata)
        isResolvingMetadata = false
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        strings.getString(R.string.invoice_scan),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            if (hasCameraPermission) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize()
                )
                InvoiceScannerOverlay(
                    leftDetected = leftDetected,
                    rightDetected = rightDetected,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PermissionPlaceholder(
                    onRequestPermission = {
                        requestCameraPermission.launch(Manifest.permission.CAMERA)
                    }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                errorMessage?.let { message ->
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Text(
                            message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }

                if (isResolvingMetadata) {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(strings.getString(R.string.invoice_qr_processing))
                        }
                    }
                }

                scanResult?.takeIf { !isResolvingMetadata }?.let { result ->
                    InvoiceScanResultCard(
                        result = result,
                        onRescan = {
                            scanResult = null
                            errorMessage = null
                            leftDetected = false
                            rightDetected = false
                        },
                        onApplyPrefill = {
                            onApplyPrefill(
                                TransactionPrefill(
                                    amount = result.totalAmount.toDouble(),
                                    note = localizedInvoiceNote(strings, result),
                                    isExpense = true,
                                    date = result.resolvedDateMillis,
                                    source = "invoice_qr",
                                    currency = "TWD",
                                    exchangeRate = 1.0
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionPlaceholder(
    onRequestPermission: () -> Unit
) {
    val strings = LocalContext.current.resources

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
            ),
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    Icons.Filled.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(36.dp)
                )
                Text(strings.getString(R.string.invoice_camera_required))
                Button(onClick = onRequestPermission) {
                    Text(strings.getString(R.string.enable_camera))
                }
            }
        }
    }
}

@Composable
private fun InvoiceScannerOverlay(
    leftDetected: Boolean,
    rightDetected: Boolean,
    modifier: Modifier = Modifier
) {
    val strings = LocalContext.current.resources

    val inactiveColor = Color.White.copy(alpha = 0.78f)
    val activeColor = Color(0xFF7DFF9B)
    val maskColor = Color.Black.copy(alpha = 0.34f)

    BoxWithConstraints(modifier = modifier) {
        val boxWidthDp = maxWidth * 0.3f
        val gapWidthDp = maxWidth * 0.11f
        val totalWidthDp = boxWidthDp + gapWidthDp + boxWidthDp
        val startPaddingDp = (maxWidth - totalWidthDp) / 2f

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(maskColor)

            val boxSize = size.width * 0.3f
            val gap = size.width * 0.11f
            val top = size.height * 0.24f
            val totalWidth = boxSize + gap + boxSize
            val leftStart = (size.width - totalWidth) / 2f

            val leftRectTopLeft = Offset(leftStart, top)
            val rightRectTopLeft = Offset(leftStart + boxSize + gap, top)

            drawRoundRect(
                color = if (leftDetected) activeColor else inactiveColor,
                topLeft = leftRectTopLeft,
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(32f, 32f),
                style = Stroke(width = 8f)
            )
            drawRoundRect(
                color = if (rightDetected) activeColor else inactiveColor,
                topLeft = rightRectTopLeft,
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(32f, 32f),
                style = Stroke(width = 8f)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.QrCodeScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            strings.getString(R.string.invoice_position),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        strings.getString(R.string.invoice_qr_position),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = (maxHeight * 0.24f) + (maxWidth * 0.3f) + 12.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.width(boxWidthDp),
                contentAlignment = Alignment.Center
            ) {
                GuideLabel(
                    text = if (leftDetected) strings.getString(R.string.left_qr_detected) else strings.getString(R.string.left_qr),
                    active = leftDetected
                )
            }
            Spacer(modifier = Modifier.width(gapWidthDp))
            Box(
                modifier = Modifier.width(boxWidthDp),
                contentAlignment = Alignment.Center
            ) {
                GuideLabel(
                    text = if (rightDetected) strings.getString(R.string.right_qr_detected) else strings.getString(R.string.right_qr),
                    active = rightDetected
                )
            }
        }
    }
}

@Composable
private fun GuideLabel(
    text: String,
    active: Boolean
) {
    Card(
        shape = RoundedCornerShape(999.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (active) Color(0xFF0E6F3F) else Color.Black.copy(alpha = 0.44f)
        )
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text,
                color = Color.White,
                fontSize = 12.sp,
                maxLines = 1,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun InvoiceScanResultCard(
    result: TaiwanInvoiceQrResult,
    onRescan: () -> Unit,
    onApplyPrefill: () -> Unit
) {
    val strings = LocalContext.current.resources

    val scrollState = rememberScrollState()
    val dateLabel = remember(result.metadata, result.invoiceDate) {
        val exactTime = result.metadata?.invoiceDateTime
        if (exactTime != null) {
            exactTime.format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm"))
        } else {
            result.invoiceDate.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
        }
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    strings.getString(R.string.invoice_scanned),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            Text(
                result.totalAmount.toCurrencyString(),
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
            )
            Text(strings.getString(R.string.invoice_number, result.invoiceNumber))
            Text(strings.getString(R.string.invoice_merchant, localizedInvoiceMerchant(strings, result)))
            Text(strings.getString(R.string.invoice_date, dateLabel))
            Text(strings.getString(R.string.invoice_random_code, result.randomCode))

            if (result.items.isNotEmpty()) {
                Text(
                    strings.getString(R.string.invoice_items),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                result.items.take(8).forEach { item ->
                    Text(item.displayText)
                }
                if (result.encodedItemCount < result.totalItemCount) {
                    Text(
                        strings.getString(R.string.invoice_partial_items, result.totalItemCount, result.encodedItemCount),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            if (result.metadata?.merchantName == null || result.metadata?.invoiceDateTime == null) {
                Text(
                    strings.getString(R.string.invoice_edit_hint),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FilledTonalButton(
                    onClick = onRescan,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.getString(R.string.scan_again))
                }
                Button(
                    onClick = onApplyPrefill,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.ReceiptLong, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(strings.getString(R.string.open_transaction_editor))
                }
            }
        }
    }
}

private fun BigDecimal.toCurrencyString(): String {
    return "NT$ ${stripTrailingZeros().toPlainString()}"
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    cameraProviderFuture: ListenableFuture<ProcessCameraProvider>,
    analyzer: InvoiceQrAnalyzer,
    analysisExecutor: ExecutorService
) {
    cameraProviderFuture.addListener(
        {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().apply {
                surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(android.util.Size(1920, 1080))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .apply {
                    setAnalyzer(analysisExecutor, analyzer)
                }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        },
        ContextCompat.getMainExecutor(context)
    )
}

private class InvoiceQrAnalyzer(
    private val isPaused: () -> Boolean,
    private val onFrameEvaluated: (leftDetected: Boolean, rightDetected: Boolean) -> Unit,
    private val onPairDetected: (left: InvoiceQrPayload, right: InvoiceQrPayload) -> Unit
) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val scanner = BarcodeScanning.getClient(options)
    private val processing = AtomicBoolean(false)

    override fun analyze(imageProxy: ImageProxy) {
        if (isPaused()) {
            imageProxy.close()
            return
        }
        if (!processing.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            processing.set(false)
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val payloads = barcodes.mapNotNull { barcode ->
                    val rawValue = barcode.rawValue.orEmpty()
                    val rawBytes = barcode.rawBytes
                    if (rawValue.isBlank() && (rawBytes == null || rawBytes.isEmpty())) return@mapNotNull null
                    InvoiceQrPayload(
                        text = rawValue,
                        rawBytes = rawBytes,
                        centerX = barcode.boundingBox?.exactCenterX()
                    )
                }

                val frameCenterX = imageProxy.width / 2f
                val leftDetected = payloads.any {
                    TaiwanInvoiceQrParser.isLikelyLeftPayload(it, frameCenterX)
                }
                val rightDetected = payloads.any {
                    TaiwanInvoiceQrParser.isLikelyRightPayload(it, frameCenterX)
                }
                onFrameEvaluated(leftDetected, rightDetected)

                TaiwanInvoiceQrParser.findPair(payloads)?.let { (left, right) ->
                    onPairDetected(left, right)
                }
            }
            .addOnCompleteListener {
                processing.set(false)
                imageProxy.close()
            }
    }

    fun close() {
        scanner.close()
    }
}

private fun <T> ListenableFuture<T>.getOrNull(): T? {
    return if (isDone) runCatching { get() }.getOrNull() else null
}

private fun localizedInvoiceMerchant(strings: android.content.res.Resources, result: TaiwanInvoiceQrResult): String =
    result.metadata?.merchantName?.takeIf { it.isNotBlank() }
        ?: strings.getString(R.string.seller_tax_id, result.sellerIdentifier)

private fun localizedInvoiceNote(strings: android.content.res.Resources, result: TaiwanInvoiceQrResult): String = buildString {
    append(strings.getString(R.string.invoice_note_number, result.invoiceNumber))
    append('\n').append(localizedInvoiceMerchant(strings, result))
    append('\n').append(strings.getString(R.string.invoice_note_random_code, result.randomCode))
    append('\n').append(strings.getString(R.string.total_amount)).append(' ')
        .append(result.totalAmount.stripTrailingZeros().toPlainString())
    if (result.items.isNotEmpty()) {
        append("\n\n").append(strings.getString(R.string.invoice_items))
        result.items.forEach { append("\n- ").append(it.displayText) }
    }
    result.supplement?.takeIf { it.isNotBlank() }?.let {
        append("\n\n").append(strings.getString(R.string.invoice_supplement)).append('\n').append(it)
    }
}
