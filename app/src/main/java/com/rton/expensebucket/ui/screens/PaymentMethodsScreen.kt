package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import androidx.compose.ui.platform.LocalContext
import com.rton.expensebucket.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.BillingCycleType
import com.rton.expensebucket.data.model.BillingLimitType
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.data.repository.TransitCardImportPreview
import com.rton.expensebucket.data.repository.TransitCardImportResult
import com.rton.expensebucket.nfc.TransitCardScan
import com.rton.expensebucket.ui.components.SlidingPillSelector
import com.rton.expensebucket.ui.theme.Amber40
import com.rton.expensebucket.ui.theme.amountMedium
import com.rton.expensebucket.ui.theme.amountSmall
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.PaymentIconMapper
import com.rton.expensebucket.util.PaymentMethodBillingSummary
import com.rton.expensebucket.util.isTransitCardGroup
import com.rton.expensebucket.util.storedValueCurrency
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PaymentMethodsScreen(
    paymentMethods: List<PaymentMethod>,
    billingSummaries: Map<Long, PaymentMethodBillingSummary>,
    balances: Map<Long, Double>,
    onAdd: (PaymentMethod) -> Unit,
    onEdit: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onSetDefault: (Long) -> Unit,
    onReconcileBalance: (Long, String, Double) -> Unit,
    onTopUp: (Long, Double) -> Unit,
    onPreviewTransitCard: suspend (Long, TransitCardScan) -> TransitCardImportPreview?,
    onSaveTransitCard: suspend (Long, TransitCardScan, Map<String, String>, Double?) -> TransitCardImportResult,
    onBack: (() -> Unit)? = null
) {
    val strings = LocalContext.current.resources

    var showEditorSheet by remember { mutableStateOf(false) }
    var selectedParentIdForAdd by remember { mutableStateOf<Long?>(null) }
    var methodToEdit by remember { mutableStateOf<PaymentMethod?>(null) }
    var balanceMethodId by rememberSaveable { mutableStateOf<Long?>(null) }
    var scanningCard by rememberSaveable { mutableStateOf(false) }
    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val balanceMethod = paymentMethods.firstOrNull { it.id == balanceMethodId }
    val balanceCurrency = balanceMethod?.let { method ->
        method.storedValueCurrency(paymentMethods.firstOrNull { it.id == method.parentId })
    }

    val parents = remember(paymentMethods) {
        paymentMethods.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val childrenMap = remember(paymentMethods) {
        paymentMethods.filter { it.parentId != null }.groupBy { it.parentId }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHost) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        strings.getString(R.string.payment_methods),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.getString(R.string.back))
                        }
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            methodToEdit = null
                            selectedParentIdForAdd = null
                            showEditorSheet = true
                        }
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(strings.getString(R.string.add_category))
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            parents.forEach { parent ->
                val children = childrenMap[parent.id]?.sortedBy { it.sortOrder }.orEmpty()
                val parentColor = Color(parent.color)

                item(key = "group_${parent.id}") {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // ─── Group Header ───────────────────────────
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable {
                                        methodToEdit = parent
                                        selectedParentIdForAdd = null
                                        showEditorSheet = true
                                    }
                                    .padding(vertical = 4.dp, horizontal = 4.dp)
                            ) {
                                Icon(
                                    painter = PaymentIconMapper.getPainter(parent.icon),
                                    contentDescription = null,
                                    tint = parentColor,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = strings.localizedLabel(parent.name),
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .clickable {
                                        methodToEdit = null
                                        selectedParentIdForAdd = parent.id
                                        showEditorSheet = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Add,
                                        contentDescription = strings.getString(R.string.add_child_item),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = strings.getString(R.string.add),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }

                        // ─── Group Items Card ───────────────────────
                        val itemsToDisplay = children.ifEmpty { listOf(parent) }
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceContainerLow
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                itemsToDisplay.forEach { method ->
                                    val currency = method.storedValueCurrency(parent.takeIf { method.parentId == it.id })
                                    UnifiedPaymentMethodRow(
                                        method = method,
                                        billingSummary = billingSummaries[method.id],
                                        balanceCurrency = currency,
                                        balance = balances[method.id],
                                        onClick = {
                                            if (currency != null) {
                                                balanceMethodId = method.id
                                            } else {
                                                methodToEdit = method
                                                selectedParentIdForAdd = method.parentId
                                                showEditorSheet = true
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(48.dp)) }
        }
    }

    if (balanceMethod != null && balanceCurrency != null && scanningCard) {
        TransitNfcSheet(
            cardName = strings.localizedLabel(balanceMethod.name),
            cardIcon = balanceMethod.icon,
            onDismiss = { scanningCard = false },
            onPreview = { onPreviewTransitCard(balanceMethod.id, it) },
            onSave = { scan, selectedNotes, manualRate ->
                onSaveTransitCard(balanceMethod.id, scan, selectedNotes, manualRate)
            },
            onSaved = { count ->
                scanningCard = false
                balanceMethodId = null
                scope.launch {
                    snackbarHost.showSnackbar(
                        if (count == 0) strings.getString(R.string.transit_nfc_balance_saved)
                        else strings.getString(R.string.transit_nfc_import_saved, count)
                    )
                }
            }
        )
    } else if (balanceMethod != null && balanceCurrency != null) {
        StoredValueBalanceSheet(
            method = balanceMethod,
            currency = balanceCurrency,
            balance = balances[balanceMethod.id],
            onScan = if (balanceCurrency == "JPY" && balanceMethod.icon in setOf("suica", "icoca")) {
                { scanningCard = true }
            } else null,
            onDismiss = { balanceMethodId = null },
            onEditMethod = {
                balanceMethodId = null
                methodToEdit = balanceMethod
                selectedParentIdForAdd = balanceMethod.parentId
                showEditorSheet = true
            },
            onReconcile = { amount ->
                onReconcileBalance(balanceMethod.id, balanceCurrency, amount)
                balanceMethodId = null
            },
            onTopUp = { amount ->
                onTopUp(balanceMethod.id, amount)
                balanceMethodId = null
            }
        )
    }

    if (showEditorSheet) {
        PaymentMethodEditorSheet(
            parentId = selectedParentIdForAdd,
            initialMethod = methodToEdit,
            parentMethod = parents.firstOrNull { it.id == selectedParentIdForAdd },
            onDismiss = {
                showEditorSheet = false
                methodToEdit = null
            },
            onConfirm = { method ->
                if (methodToEdit != null) {
                    onEdit(method)
                } else {
                    onAdd(method)
                }
                showEditorSheet = false
                methodToEdit = null
            },
            onDelete = { method ->
                onDelete(method)
                showEditorSheet = false
                methodToEdit = null
            },
            onSetDefault = { methodId ->
                onSetDefault(methodId)
                showEditorSheet = false
                methodToEdit = null
            }
        )
    }
}

@Composable
private fun UnifiedPaymentMethodRow(
    method: PaymentMethod,
    billingSummary: PaymentMethodBillingSummary?,
    balanceCurrency: String?,
    balance: Double?,
    onClick: () -> Unit
) {
    val strings = LocalContext.current.resources

    val color = Color(method.color)
    val billingLimit = method.billingLimitAmount?.takeIf { it > 0.0 }
    val progress = if (billingSummary != null && billingLimit != null) {
        (billingSummary.totalAmount / billingLimit).coerceIn(0.0, 1.0).toFloat()
    } else {
        null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = PaymentIconMapper.getPainter(method.icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = strings.localizedLabel(method.name),
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (method.isDefault) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = strings.getString(R.string.default_label),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                if (balanceCurrency != null) {
                    Text(
                        text = strings.getString(
                            if (balance == null) R.string.stored_value_set_initial else R.string.stored_value_balance
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (billingSummary != null) {
                    Text(
                        text = "${billingSummary.periodLabel} · ${strings.getString(R.string.billing_day, method.billingCycleDay ?: "-")}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                } else if (!method.isTransitCardGroup()) {
                    Text(
                        text = strings.getString(R.string.no_billing_day),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            if (balanceCurrency != null && balance != null) {
                Text(
                    text = CurrencyFormats.formatAmount(balanceCurrency, balance),
                    style = MaterialTheme.typography.amountMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            } else if (balanceCurrency == null && billingSummary != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = CurrencyFormats.formatCompactAmount("TWD", billingSummary.totalAmount),
                        style = MaterialTheme.typography.amountMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = strings.getString(R.string.current_statement),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Progress bar when both billing cycle and credit/promo limit are active
        if (balanceCurrency == null && billingSummary != null && billingLimit != null && progress != null) {
            val barColor = when {
                billingSummary.totalAmount > billingLimit -> MaterialTheme.colorScheme.error
                progress >= 0.85f -> Amber40
                else -> color
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 52.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = barColor,
                    trackColor = barColor.copy(alpha = 0.14f),
                    strokeCap = StrokeCap.Round
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${billingLimitLabel(strings, method)} ${CurrencyFormats.formatCompactAmount("TWD", billingLimit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    val remaining = (billingLimit - billingSummary.totalAmount).coerceAtLeast(0.0)
                    Text(
                        text = strings.getString(R.string.remaining_amount, CurrencyFormats.formatCompactAmount("TWD", remaining)),
                        style = MaterialTheme.typography.amountSmall.copy(
                            fontSize = MaterialTheme.typography.labelSmall.fontSize
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoredValueBalanceSheet(
    method: PaymentMethod,
    currency: String,
    balance: Double?,
    onScan: (() -> Unit)?,
    onDismiss: () -> Unit,
    onEditMethod: () -> Unit,
    onReconcile: (Double) -> Unit,
    onTopUp: (Double) -> Unit
) {
    val strings = LocalContext.current.resources
    val hasBalance = method.storedValueBalance != null
    var topUpSelected by rememberSaveable(method.id) { mutableStateOf(true) }
    val isTopUp = hasBalance && topUpSelected
    var amountText by rememberSaveable(method.id, isTopUp) { mutableStateOf("") }
    var negative by rememberSaveable(method.id, isTopUp) { mutableStateOf(false) }
    val amount = amountText.toDoubleOrNull()
        ?.takeIf { it.isFinite() && (!isTopUp || it > 0) }
        ?.let { if (negative) -it else it }
    val action = when {
        isTopUp -> R.string.stored_value_top_up
        hasBalance -> R.string.stored_value_reconcile
        else -> R.string.stored_value_set_initial
    }
    val choices = listOf(R.string.stored_value_top_up, R.string.stored_value_reconcile)
    val amountPattern = remember(currency) {
        val decimals = if (currency == "JPY") "" else "(\\.\\d{0,2})?"
        Regex("\\d{0,10}$decimals")
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = strings.localizedLabel(method.name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onEditMethod) {
                    Icon(Icons.Filled.Edit, contentDescription = strings.getString(R.string.edit_payment_method))
                }
            }
            if (balance != null) {
                Text(
                    text = strings.getString(
                        R.string.stored_value_balance_amount,
                        CurrencyFormats.formatAmount(currency, balance)
                    ),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            if (onScan != null) {
                OutlinedButton(onClick = onScan, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Contactless, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(strings.getString(R.string.transit_nfc_scan))
                }
            }
            if (hasBalance) {
                SlidingPillSelector(
                    options = choices,
                    selected = action,
                    onSelect = { topUpSelected = it == R.string.stored_value_top_up },
                    label = { strings.getString(it) }
                )
            }
            OutlinedTextField(
                value = amountText,
                onValueChange = { input ->
                    val normalized = input.replace(',', '.').replace('−', '-')
                    val digits = if (isTopUp) normalized else normalized.removePrefix("-")
                    if (amountPattern.matches(digits)) {
                        amountText = digits
                        if (normalized.startsWith("-")) negative = true
                    }
                },
                leadingIcon = if (isTopUp) null else {
                    {
                        IconToggleButton(checked = negative, onCheckedChange = { negative = it }) {
                            Icon(
                                imageVector = if (negative) Icons.Filled.Remove else Icons.Filled.Add,
                                contentDescription = strings.getString(R.string.stored_value_negative)
                            )
                        }
                    }
                },
                label = {
                    Text(strings.getString(if (isTopUp) R.string.stored_value_top_up_amount else R.string.stored_value_actual_amount))
                },
                suffix = { Text(currency) },
                supportingText = {
                    Text(strings.getString(if (isTopUp) R.string.stored_value_top_up_help else R.string.stored_value_reconcile_help))
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (currency == "JPY") KeyboardType.Number else KeyboardType.Decimal
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Button(
                onClick = { amount?.let(if (isTopUp) onTopUp else onReconcile) },
                enabled = amount != null,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(strings.getString(action))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun PaymentMethodEditorSheet(
    parentId: Long?,
    initialMethod: PaymentMethod? = null,
    parentMethod: PaymentMethod?,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onSetDefault: (Long) -> Unit
) {
    val strings = LocalContext.current.resources

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(initialMethod) { mutableStateOf(initialMethod?.name ?: "") }
    var selectedType by remember(initialMethod) { mutableStateOf(initialMethod?.type ?: "credit") }
    var selectedIcon by remember(initialMethod) { mutableStateOf(initialMethod?.icon ?: "credit_card") }
    var billingEnabled by remember(initialMethod) {
        mutableStateOf(
            BillingCycleType.fromValue(initialMethod?.billingCycleType) == BillingCycleType.MONTHLY_CLOSING_DAY
        )
    }
    var billingDayText by remember(initialMethod) {
        mutableStateOf(initialMethod?.billingCycleDay?.toString().orEmpty())
    }
    var billingLimitType by remember(initialMethod) {
        mutableStateOf(BillingLimitType.fromValue(initialMethod?.billingLimitType))
    }
    var billingLimitAmountText by remember(initialMethod) {
        mutableStateOf(
            initialMethod?.billingLimitAmount
                ?.takeIf { it != 0.0 }
                ?.let { formatEditableAmount(it) }
                .orEmpty()
        )
    }

    val presetColors = remember {
        listOf(
            0xFF3B82F6, 0xFF10B981, 0xFFF59E0B, 0xFFEC4899,
            0xFF818CF8, 0xFF06C755, 0xFFE31837, 0xFFFF5F00,
            0xFF1434CB, 0xFF003E94, 0xFF0EA5E9, 0xFF6B7280
        )
    }
    var selectedColor by remember(initialMethod) {
        mutableLongStateOf(initialMethod?.color ?: presetColors.first())
    }

    val types = listOf("credit" to strings.getString(R.string.credit_card), "epay" to strings.getString(R.string.electronic_payment), "cash" to strings.getString(R.string.cash), "other" to strings.getString(R.string.other))
    val billingDay = billingDayText.toIntOrNull()?.takeIf { it in 1..31 }
    val billingLimitChoices = listOf(
        BillingLimitType.CREDIT to strings.getString(R.string.credit_limit),
        BillingLimitType.PROMO to strings.getString(R.string.reward_limit)
    )
    val billingLimitAmount = billingLimitAmountText.toDoubleOrNull()
    val iconChoices = PaymentIconMapper.allKeys

    val method = (initialMethod ?: PaymentMethod(name = name, icon = selectedIcon, parentId = parentId))
        .copy(name = name, icon = selectedIcon)
    val showBilling = !method.isTransitCardGroup() && method.storedValueCurrency(parentMethod) == null
    val canSave = name.isNotBlank() && (!showBilling || !billingEnabled || billingDay != null)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ─── Sheet Header ───────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (initialMethod != null) strings.getString(R.string.edit_payment_method) else strings.getString(R.string.add_payment_method),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
                if (initialMethod != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!initialMethod.isDefault) {
                            FilterChip(
                                selected = false,
                                onClick = { onSetDefault(initialMethod.id) },
                                label = { Text(strings.getString(R.string.set_default)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Filled.StarOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            )
                        }
                        IconButton(onClick = { onDelete(initialMethod) }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = strings.getString(R.string.delete),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }

            // ─── Live Preview Icon + Name Field ─────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(selectedColor).copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = PaymentIconMapper.getPainter(selectedIcon),
                        contentDescription = null,
                        tint = Color(selectedColor),
                        modifier = Modifier.size(24.dp)
                    )
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(strings.getString(R.string.name)) },
                    placeholder = { Text(if (parentId != null) strings.getString(R.string.card_name_example) else strings.getString(R.string.payment_name_example)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium
                )
            }

            if (parentId == null && initialMethod == null) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, (key, label) ->
                        SegmentedButton(
                            selected = selectedType == key,
                            onClick = { selectedType = key },
                            shape = SegmentedButtonDefaults.itemShape(index, types.size),
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                        )
                    }
                }
            }

            // ─── Icon & Color Pickers ───────────────────────────────
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = strings.getString(R.string.icon_and_color),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        iconChoices.forEach { key ->
                            val isSelected = selectedIcon == key
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color(selectedColor)
                                        else MaterialTheme.colorScheme.surfaceContainerHigh
                                    )
                                    .clickable {
                                        if (selectedIcon != key) {
                                            selectedIcon = key
                                            PaymentIconMapper.defaultColor(key)?.let { selectedColor = it }
                                        }
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = PaymentIconMapper.getPainter(key),
                                    contentDescription = strings.localizedLabel(PaymentIconMapper.displayName(key)),
                                    tint = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        presetColors.forEach { colorLong ->
                            val isSelected = selectedColor == colorLong
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(Color(colorLong))
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { selectedColor = colorLong },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (showBilling) Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = strings.getString(R.string.billing_and_limit),
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Switch(
                            checked = billingEnabled,
                            onCheckedChange = { enabled ->
                                billingEnabled = enabled
                                if (!enabled) {
                                    billingDayText = ""
                                    billingLimitAmountText = ""
                                    billingLimitType = BillingLimitType.CREDIT
                                }
                            }
                        )
                    }

                    AnimatedVisibility(visible = billingEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedTextField(
                                    value = billingDayText,
                                    onValueChange = { value ->
                                        billingDayText = value.filter(Char::isDigit).take(2)
                                    },
                                    label = { Text(strings.getString(R.string.billing_day_input)) },
                                    singleLine = true,
                                    isError = billingDay == null && billingDayText.isNotEmpty(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f),
                                    shape = MaterialTheme.shapes.small
                                )
                                OutlinedTextField(
                                    value = billingLimitAmountText,
                                    onValueChange = { value ->
                                        billingLimitAmountText = value
                                            .filter { it.isDigit() || it == '.' }
                                            .let { sanitizeDecimalInput(it) }
                                    },
                                    label = { Text(strings.getString(R.string.optional_limit)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier.weight(1.3f),
                                    shape = MaterialTheme.shapes.small
                                )
                            }

                            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                billingLimitChoices.forEachIndexed { index, (type, label) ->
                                    SegmentedButton(
                                        selected = billingLimitType == type,
                                        onClick = { billingLimitType = type },
                                        shape = SegmentedButtonDefaults.itemShape(index, billingLimitChoices.size),
                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ─── Save Button ────────────────────────────────────────
            Button(
                onClick = {
                    val billingActive = showBilling && billingEnabled
                    val finalMethod = (initialMethod ?: PaymentMethod(
                        name = name.trim(),
                        icon = selectedIcon,
                        type = selectedType,
                        parentId = parentId
                    )).copy(
                        name = name.trim(),
                        icon = selectedIcon,
                        color = selectedColor,
                        billingCycleType = if (billingActive) BillingCycleType.MONTHLY_CLOSING_DAY.value else BillingCycleType.NONE.value,
                        billingCycleDay = if (billingActive) billingDay else null,
                        billingLimitType = if (billingActive) billingLimitType.value else BillingLimitType.CREDIT.value,
                        billingLimitAmount = if (billingActive) billingLimitAmount else null
                    )
                    onConfirm(finalMethod)
                },
                enabled = canSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Text(if (initialMethod != null) strings.getString(R.string.save_changes) else strings.getString(R.string.add_payment_method))
            }
        }
    }
}

private fun billingLimitLabel(strings: android.content.res.Resources, method: PaymentMethod): String =
    when (BillingLimitType.fromValue(method.billingLimitType)) {
        BillingLimitType.CREDIT -> strings.getString(R.string.credit_limit)
        BillingLimitType.PROMO -> strings.getString(R.string.reward_limit)
    }

private fun formatEditableAmount(amount: Double): String =
    if (amount % 1.0 == 0.0) {
        amount.toLong().toString()
    } else {
        amount.toString()
    }

private fun sanitizeDecimalInput(input: String): String {
    val parts = input.split('.')
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts[0]
        else -> parts.first() + "." + parts.drop(1).joinToString("").take(2)
    }
}
