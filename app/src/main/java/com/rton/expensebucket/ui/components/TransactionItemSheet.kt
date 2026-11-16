package com.rton.expensebucket.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.TransactionItem
import com.rton.expensebucket.data.model.TransactionItemLogic
import com.rton.expensebucket.ui.screens.AddTransactionFormLogic

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionItemSheet(
    currency: String,
    initialItem: TransactionItem?,
    itemCount: Int,
    onSaveItem: (TransactionItem) -> Unit,
    onDismiss: () -> Unit
) {
    var name by rememberSaveable { mutableStateOf(initialItem?.name.orEmpty()) }
    val initialAmountText = initialItem?.amount?.let {
        if (it == it.toLong().toDouble()) it.toLong().toString() else it.toString()
    }.orEmpty()
    var amountText by rememberSaveable { mutableStateOf(initialAmountText) }
    var amountStep by rememberSaveable { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var lastSavedName by rememberSaveable { mutableStateOf("") }
    val nameFocus = remember { FocusRequester() }
    val resolvedAmount = AddTransactionFormLogic.resolveAmount(amountText)
    val item = TransactionItemLogic.create(name, resolvedAmount?.toString().orEmpty())
    val isDirty = name != initialItem?.name.orEmpty() || amountText != initialAmountText
    val currentDirty by rememberUpdatedState(isDirty)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { target ->
            if (target == SheetValue.Hidden && currentDirty) {
                showDiscardDialog = true
                false
            } else true
        }
    )

    fun requestDismiss() {
        if (isDirty) showDiscardDialog = true else onDismiss()
    }

    fun save(another: Boolean) {
        val validItem = item ?: return
        onSaveItem(validItem)
        if (another) {
            lastSavedName = validItem.name
            name = ""
            amountText = ""
            amountStep = false
            nameFocus.requestFocus()
        } else {
            onDismiss()
        }
    }

    LaunchedEffect(sheetState.isVisible) {
        if (sheetState.isVisible) nameFocus.requestFocus()
    }

    ModalBottomSheet(
        onDismissRequest = ::requestDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        val focusManager = LocalFocusManager.current
        val keyboardController = LocalSoftwareKeyboardController.current

        fun startAmountEntry() {
            if (name.isBlank()) return
            focusManager.clearFocus()
            keyboardController?.hide()
            amountStep = true
        }

        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(bottom = if (amountStep) 0.dp else 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (initialItem == null) "快速新增項目" else "編輯項目",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (initialItem == null) {
                            Text(
                                "第 ${itemCount + 1} 個項目 · $currency",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = ::requestDismiss) { Text("關閉") }
                }

                if (lastSavedName.isNotEmpty()) {
                    Text(
                        "✓ 已新增 $lastSavedName",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("項目名稱") },
                    placeholder = { Text("例如：豆腐") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(onNext = { startAmountEntry() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(nameFocus)
                        .onFocusChanged { if (it.isFocused) amountStep = false },
                    shape = MaterialTheme.shapes.medium
                )
                Surface(
                    onClick = ::startAmountEntry,
                    enabled = name.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = if (amountStep) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null
                ) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                        Text("項目金額", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(currency, style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                amountText.ifBlank { "0" },
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.headlineMedium,
                                textAlign = TextAlign.End,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (amountText.isNotBlank() && item == null) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                if (!amountStep) {
                    Button(
                        onClick = ::startAmountEntry,
                        enabled = name.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text("下一步：輸入金額") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (initialItem == null) {
                            OutlinedButton(
                                onClick = { save(another = true) },
                                enabled = item != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("下一個項目") }
                        }
                        Button(
                            onClick = { save(another = false) },
                            enabled = item != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (initialItem == null) "完成" else "儲存變更")
                        }
                    }
                }
            }
            if (amountStep) {
                ExpenseNumpad(
                    displayAmount = amountText,
                    onDigitClick = { amountText = AddTransactionFormLogic.appendAmountKey(amountText, it) },
                    onDotClick = { amountText = AddTransactionFormLogic.appendAmountDot(amountText) },
                    onBackspaceClick = { amountText = amountText.dropLast(1) },
                    onEvaluate = { amountText = AddTransactionFormLogic.evaluateExpressionToText(amountText) },
                    onConfirm = { save(another = false) },
                    includeNavigationBarInset = false,
                    modifier = Modifier.testTag("itemNumpad")
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("捨棄這個項目？") },
            text = { Text("目前輸入的內容尚未套用，已新增的其他項目會保留。") },
            confirmButton = { TextButton(onClick = onDismiss) { Text("捨棄") } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text("繼續輸入") } }
        )
    }
}
