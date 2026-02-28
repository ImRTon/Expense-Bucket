package com.rton.expanses.ui.screens

import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import com.rton.expanses.data.model.Category
import com.rton.expanses.data.model.PaymentMethod
import com.rton.expanses.data.model.Transaction
import com.rton.expanses.ocr.OcrEngine
import com.rton.expanses.ocr.OcrResult
import com.rton.expanses.ui.util.PaymentIconMapper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrPreviewScreen(
    imageUri: Uri?,
    categories: List<Category>,
    paymentMethods: List<PaymentMethod>,
    ocrEngine: OcrEngine,
    onSave: (Transaction) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Editable fields from OCR result
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var isExpense by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var selectedPaymentMethodId by remember { mutableStateOf<Long?>(null) }

    // Run OCR when screen opens
    LaunchedEffect(imageUri) {
        if (imageUri == null) {
            errorMessage = "沒有收到圖片"
            isProcessing = false
            return@LaunchedEffect
        }
        try {
            val result = ocrEngine.processImage(context, imageUri)
            ocrResult = result
            // Pre-fill fields from parsed result
            result.parsed?.let { parsed ->
                amount = if (parsed.amount == parsed.amount.toLong().toDouble()) {
                    parsed.amount.toLong().toString()
                } else {
                    parsed.amount.toString()
                }
                note = parsed.merchant.ifBlank { parsed.note }
                isExpense = parsed.isExpense
                // Try to match payment method hint
                parsed.paymentMethodHint?.let { hint ->
                    paymentMethods.find { it.icon == hint }?.let {
                        selectedPaymentMethodId = it.id
                    }
                }
            }
        } catch (e: Exception) {
            errorMessage = "OCR 辨識失敗: ${e.localizedMessage}"
        }
        isProcessing = false
    }

    // Default to first payment method if none matched
    LaunchedEffect(selectedPaymentMethodId, paymentMethods) {
        if (selectedPaymentMethodId == null && paymentMethods.isNotEmpty()) {
            selectedPaymentMethodId = paymentMethods.firstOrNull { it.isDefault }?.id
                ?: paymentMethods.first().id
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "截圖辨識",
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
            // ─── Screenshot preview ─────────────────────────────
            if (imageUri != null) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    )
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(model = imageUri),
                        contentDescription = "截圖",
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // ─── Processing state ───────────────────────────────
            if (isProcessing) {
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
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("正在辨識文字...")
                    }
                }
                return@Column
            }

            // ─── Error state ────────────────────────────────────
            errorMessage?.let { err ->
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }
                return@Column
            }

            // ─── OCR raw text ───────────────────────────────────
            ocrResult?.let { result ->
                // Confidence indicator
                result.parsed?.let { parsed ->
                    val confidence = (parsed.confidence * 100).toInt()
                    val confColor = when {
                        confidence >= 80 -> Color(0xFF22C55E)
                        confidence >= 50 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = confColor.copy(alpha = 0.1f)
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = confColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "辨識信心度: $confidence%",
                                style = MaterialTheme.typography.labelLarge,
                                color = confColor
                            )
                        }
                    }
                }

                // Raw text (expandable)
                var showRawText by remember { mutableStateOf(false) }
                OutlinedCard(
                    onClick = { showRawText = !showRawText },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.TextSnippet,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "原始辨識文字",
                                style = MaterialTheme.typography.labelLarge,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (showRawText) Icons.Filled.ExpandLess
                                else Icons.Filled.ExpandMore,
                                contentDescription = null
                            )
                        }
                        AnimatedVisibility(visible = showRawText) {
                            Text(
                                result.rawText.ifBlank { "(未辨識到文字)" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            // ─── Editable fields ────────────────────────────────
            Text(
                "確認交易資料",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )

            // Amount
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("金額") },
                leadingIcon = { Text("$", style = MaterialTheme.typography.titleMedium) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Note / merchant
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("商家 / 備註") },
                leadingIcon = {
                    Icon(Icons.Filled.Store, contentDescription = null)
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            // Expense / Income toggle
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isExpense,
                    onClick = { isExpense = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    label = { Text("支出") }
                )
                SegmentedButton(
                    selected = !isExpense,
                    onClick = { isExpense = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    label = { Text("收入") }
                )
            }

            // Category selector
            val filteredCategories = categories.filter { it.isExpense == isExpense }
            if (filteredCategories.isNotEmpty()) {
                Text(
                    "分類",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    filteredCategories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory?.id == cat.id,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat.name) }
                        )
                    }
                }
            }

            // Payment method selector
            if (paymentMethods.isNotEmpty()) {
                Text(
                    "支付工具",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    paymentMethods.forEach { method ->
                        val color = Color(method.color)
                        FilterChip(
                            selected = selectedPaymentMethodId == method.id,
                            onClick = { selectedPaymentMethodId = method.id },
                            label = { Text(method.name, style = MaterialTheme.typography.labelMedium) },
                            leadingIcon = {
                                Icon(
                                    PaymentIconMapper.getIcon(method.icon),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = color
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Save button
            Button(
                onClick = {
                    val amountValue = amount.toDoubleOrNull() ?: return@Button
                    onSave(
                        Transaction(
                            amount = amountValue,
                            note = note,
                            categoryId = selectedCategory?.id,
                            paymentMethodId = selectedPaymentMethodId,
                            isExpense = isExpense,
                            source = "ocr",
                            isDraft = false,
                            date = System.currentTimeMillis(),
                            createdAt = System.currentTimeMillis()
                        )
                    )
                    onBack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                enabled = amount.toDoubleOrNull() != null && (amount.toDoubleOrNull() ?: 0.0) > 0
            ) {
                Icon(Icons.Filled.Check, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("確認記帳", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
