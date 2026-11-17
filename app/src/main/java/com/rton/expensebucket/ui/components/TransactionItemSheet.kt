package com.rton.expensebucket.ui.components

import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
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
    val strings = LocalContext.current.resources

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
                            if (initialItem == null) strings.getString(R.string.quick_add_item) else strings.getString(R.string.edit_item),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (initialItem == null) {
                            Text(
                                strings.getString(R.string.item_number_currency, itemCount + 1, currency),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    TextButton(onClick = ::requestDismiss) { Text(strings.getString(R.string.close)) }
                }

                if (lastSavedName.isNotEmpty()) {
                    Text(
                        strings.getString(R.string.item_added, lastSavedName),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.getString(R.string.item_name)) },
                    placeholder = { Text(strings.getString(R.string.item_example)) },
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
                        Text(strings.getString(R.string.item_amount), style = MaterialTheme.typography.labelMedium,
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
                    ) { Text(strings.getString(R.string.next_enter_amount)) }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (initialItem == null) {
                            OutlinedButton(
                                onClick = { save(another = true) },
                                enabled = item != null,
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text(strings.getString(R.string.next_item)) }
                        }
                        Button(
                            onClick = { save(another = false) },
                            enabled = item != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (initialItem == null) strings.getString(R.string.done) else strings.getString(R.string.save_changes))
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
            title = { Text(strings.getString(R.string.discard_item_title)) },
            text = { Text(strings.getString(R.string.discard_item_message)) },
            confirmButton = { TextButton(onClick = onDismiss) { Text(strings.getString(R.string.discard)) } },
            dismissButton = { TextButton(onClick = { showDiscardDialog = false }) { Text(strings.getString(R.string.keep_entering)) } }
        )
    }
}
