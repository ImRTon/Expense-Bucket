package com.rton.expensebucket.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.rton.expensebucket.data.model.BillingCycleType
import com.rton.expensebucket.data.model.BillingLimitType
import com.rton.expensebucket.data.model.PaymentMethod
import com.rton.expensebucket.ui.util.CurrencyFormats
import com.rton.expensebucket.ui.util.PaymentIconMapper
import com.rton.expensebucket.util.PaymentMethodBillingSummary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun PaymentMethodsScreen(
    paymentMethods: List<PaymentMethod>,
    billingSummaries: Map<Long, PaymentMethodBillingSummary>,
    onAdd: (PaymentMethod) -> Unit,
    onEdit: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onSetDefault: (Long) -> Unit,
    onBack: (() -> Unit)? = null
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedParentIdForAdd by remember { mutableStateOf<Long?>(null) }
    var methodToEdit by remember { mutableStateOf<PaymentMethod?>(null) }
    val parents = remember(paymentMethods) {
        paymentMethods.filter { it.parentId == null }.sortedBy { it.sortOrder }
    }
    val childrenMap = remember(paymentMethods) {
        paymentMethods.filter { it.parentId != null }.groupBy { it.parentId }
    }
    val tabs = listOf("統計", "管理")
    val pagerState = rememberPagerState(initialPage = 0) { tabs.size }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "支付工具",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                windowInsets = WindowInsets(0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        floatingActionButton = {
            if (pagerState.currentPage == 1) {
                ExtendedFloatingActionButton(
                    onClick = {
                        methodToEdit = null
                        selectedParentIdForAdd = null
                        showAddDialog = true
                    },
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("新增支付工具") },
                    containerColor = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(index)
                            }
                        },
                        text = { Text(title) }
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                when (page) {
                    0 -> PaymentMethodStatisticsTab(
                        parents = parents,
                        childrenMap = childrenMap,
                        billingSummaries = billingSummaries
                    )
                    else -> PaymentMethodManagementTab(
                        parents = parents,
                        childrenMap = childrenMap,
                        onEditParent = { parent ->
                            methodToEdit = parent
                            showAddDialog = true
                        },
                        onAddChild = { parentId ->
                            methodToEdit = null
                            selectedParentIdForAdd = parentId
                            showAddDialog = true
                        },
                        onEditMethod = { method ->
                            methodToEdit = method
                            showAddDialog = true
                        },
                        onDelete = onDelete,
                        onSetDefault = onSetDefault
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddPaymentMethodDialog(
            parentId = selectedParentIdForAdd,
            initialMethod = methodToEdit,
            onDismiss = {
                showAddDialog = false
                methodToEdit = null
            },
            onConfirm = { method ->
                if (methodToEdit != null) {
                    onEdit(method)
                } else {
                    onAdd(method)
                }
                showAddDialog = false
                methodToEdit = null
            }
        )
    }
}

@Composable
private fun PaymentMethodStatisticsTab(
    parents: List<PaymentMethod>,
    childrenMap: Map<Long?, List<PaymentMethod>>,
    billingSummaries: Map<Long, PaymentMethodBillingSummary>
) {
    val showConfigurationHint = billingSummaries.isEmpty()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parents.forEach { parent ->
            val children = childrenMap[parent.id]?.sortedBy { it.sortOrder } ?: emptyList()
            val methodsToShow = buildList {
                if (children.isEmpty() || BillingCycleType.fromValue(parent.billingCycleType) != BillingCycleType.NONE) {
                    add(parent)
                }
                addAll(children)
            }

            if (methodsToShow.isNotEmpty()) {
                item(key = "stats_parent_${parent.id}") {
                    Text(
                        text = parent.name,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }

                items(methodsToShow, key = { "stats_method_${it.id}" }) { method ->
                    PaymentMethodStatisticItem(
                        method = method,
                        billingSummary = billingSummaries[method.id]
                    )
                }
            }
        }
        if (showConfigurationHint) {
            item {
                MissingBillingConfigHint()
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun PaymentMethodManagementTab(
    parents: List<PaymentMethod>,
    childrenMap: Map<Long?, List<PaymentMethod>>,
    onEditParent: (PaymentMethod) -> Unit,
    onAddChild: (Long) -> Unit,
    onEditMethod: (PaymentMethod) -> Unit,
    onDelete: (PaymentMethod) -> Unit,
    onSetDefault: (Long) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        parents.forEach { parent ->
            item(key = "manage_parent_${parent.id}") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        parent.name,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { onEditParent(parent) }) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = "編輯父項目",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { onAddChild(parent.id) }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "新增子項目",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            val children = childrenMap[parent.id]?.sortedBy { it.sortOrder } ?: emptyList()
            if (children.isEmpty()) {
                item(key = "manage_single_${parent.id}") {
                    PaymentMethodItem(
                        method = parent,
                        billingSummary = null,
                        onEdit = { onEditMethod(parent) },
                        onDelete = { onDelete(parent) },
                        onSetDefault = { onSetDefault(parent.id) }
                    )
                }
            } else {
                items(children, key = { "manage_child_${it.id}" }) { method ->
                    PaymentMethodItem(
                        method = method,
                        billingSummary = null,
                        onEdit = { onEditMethod(method) },
                        onDelete = { onDelete(method) },
                        onSetDefault = { onSetDefault(method.id) }
                    )
                }
            }
        }
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun PaymentMethodItem(
    method: PaymentMethod,
    billingSummary: PaymentMethodBillingSummary?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    val color = Color(method.color)

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    PaymentIconMapper.getIcon(method.icon),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        method.name,
                        style = MaterialTheme.typography.titleSmall.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    if (method.isDefault) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                "預設",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
                Text(
                    when (method.type) {
                        "cash" -> "現金"
                        "credit" -> "信用卡"
                        "epay" -> "電子支付"
                        else -> "其他"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (billingSummary != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                billingSummary.cycleDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "當期帳單 ${billingSummary.periodLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                CurrencyFormats.formatAmount("TWD", billingSummary.totalAmount),
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }

            if (!method.isDefault) {
                TextButton(onClick = onSetDefault) {
                    Text("設為預設", style = MaterialTheme.typography.labelMedium)
                }
            }

            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = "編輯",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "刪除",
                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
private fun PaymentMethodStatisticItem(
    method: PaymentMethod,
    billingSummary: PaymentMethodBillingSummary?
) {
    val color = Color(method.color)
    val typeLabel = when (method.type) {
        "cash" -> "現金"
        "credit" -> "信用卡"
        "epay" -> "電子支付"
        else -> "其他"
    }
    val billingLimit = method.billingLimitAmount?.takeIf { it > 0.0 }
    val progress = if (billingSummary != null && billingLimit != null) {
        (billingSummary.totalAmount / billingLimit).coerceAtMost(1.0).toFloat()
    } else {
        0f
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        PaymentIconMapper.getIcon(method.icon),
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        method.name,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Text(
                        typeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (method.isDefault) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            "預設",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            if (billingSummary != null) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                billingSummary.cycleDescription,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "當期帳單 ${billingSummary.periodLabel}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (billingLimit != null) {
                            Text(
                                CurrencyFormats.formatAmount("TWD", billingSummary.totalAmount),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.TrendingUp,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        billingLimitLabel(method),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    CurrencyFormats.formatAmount("TWD", billingLimit),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MissingBillingConfigHint() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "還沒有任何帳單統計",
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "到管理分頁為支付工具新增帳單週期，之後這裡就會顯示當期帳單統計。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddPaymentMethodDialog(
    parentId: Long?,
    initialMethod: PaymentMethod? = null,
    onDismiss: () -> Unit,
    onConfirm: (PaymentMethod) -> Unit
) {
    var name by remember(initialMethod) { mutableStateOf(initialMethod?.name ?: "") }
    var selectedType by remember(initialMethod) { mutableStateOf(initialMethod?.type ?: "epay") }
    var selectedIcon by remember(initialMethod) { mutableStateOf(initialMethod?.icon ?: "other") }
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
    
    val presetColors = listOf(
        0xFF4ADE80, 0xFF60A5FA, 0xFFF59E0B, 0xFFEC4899,
        0xFF818CF8, 0xFF10B981, 0xFFFF8C00, 0xFF6B7280,
        0xFF1434CB, 0xFFFF5F00, 0xFF003E94, 0xFFE31837,
        0xFF06C755, 0xFF3B82F6, 0xFF000000, 0xFF4285F4, 
        0xFF1428A0, 0xFF00457C, 0xFF0EA5E9
    )
    
    var selectedColorIndex by remember(initialMethod) { 
        mutableStateOf(
            if (initialMethod != null) {
                val index = presetColors.indexOf(initialMethod.color)
                if (index != -1) index else -1 // -1 means custom color
            } else 0
        ) 
    }

    var customHex by remember(initialMethod) {
        mutableStateOf(
            if (initialMethod != null && presetColors.indexOf(initialMethod.color) == -1) {
                String.format("#%06X", 0xFFFFFF and initialMethod.color.toInt())
            } else ""
        )
    }

    val types = listOf("cash" to "現金", "credit" to "信用卡", "epay" to "電子支付", "other" to "其他")
    val billingDay = billingDayText.toIntOrNull()?.takeIf { it in 1..31 }
    val billingLimitChoices = listOf(
        BillingLimitType.CREDIT to "信用額度",
        BillingLimitType.PROMO to "優惠額度"
    )
    val billingLimitAmount = billingLimitAmountText.toDoubleOrNull()
    val dialogScrollState = rememberScrollState()

    // Icon picker — group by type relevance
    val iconChoices = listOf(
        "cash", "credit_card", "visa", "mastercard", "jcb",
        "applepay", "googlepay", "samsungpay", "linepay", "paypal",
        "tpay", "jko", "easycard", "pi", "pay_full", "bank_transfer", "other"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initialMethod != null) "編輯支付工具" else "新增支付工具") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .verticalScroll(dialogScrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名稱") },
                    placeholder = { Text(if (parentId != null) "例如：街口支付" else "例如：虛擬貨幣") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                if (parentId == null && initialMethod == null) {
                    // Type picker for parents only
                    Text("類型", style = MaterialTheme.typography.labelLarge)
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("帳單統計", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "開啟後會依每月結帳日統計當期帳單總額",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
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

                if (billingEnabled) {
                    OutlinedTextField(
                        value = billingDayText,
                        onValueChange = { value ->
                            billingDayText = value.filter(Char::isDigit).take(2)
                        },
                        label = { Text("每月結帳日") },
                        placeholder = { Text("1 - 31") },
                        singleLine = true,
                        isError = billingDay == null,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            Text(if (billingDay == null) "請輸入 1 到 31 之間的日期" else "例如 5 代表每月 5 日結帳")
                        }
                    )

                    Text("額度類型", style = MaterialTheme.typography.labelLarge)
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

                    OutlinedTextField(
                        value = billingLimitAmountText,
                        onValueChange = { value ->
                            billingLimitAmountText = value
                                .filter { it.isDigit() || it == '.' }
                                .let { sanitizeDecimalInput(it) }
                        },
                        label = { Text("額度金額") },
                        placeholder = { Text("留空或 0 代表不設定額度") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        supportingText = {
                            Text("可設定真實額度或活動優惠額度")
                        }
                    )
                }

                // Icon picker
                Text("圖示", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    iconChoices.forEach { key ->
                        val isSelected = selectedIcon == key
                        IconButton(
                            onClick = { selectedIcon = key },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                )
                        ) {
                            Icon(
                                PaymentIconMapper.getIcon(key),
                                contentDescription = key,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                // Custom Color Input
                Text("自訂顏色", style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = customHex,
                    onValueChange = { 
                        customHex = it
                        selectedColorIndex = -1 // Switch to custom color mode
                    },
                    label = { Text("輸入色碼 (例如 #FF0000)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                )

                // Color picker
                Text("預設顏色", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetColors.forEachIndexed { index, colorLong ->
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color(colorLong))
                                .then(
                                    if (isSelected) Modifier.padding(4.dp) else Modifier
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = {
                                    selectedColorIndex = index
                                    customHex = "" // Clear custom hex
                                },
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check, null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalColor = if (selectedColorIndex >= 0) {
                        presetColors[selectedColorIndex]
                    } else {
                        try {
                            val parsed = android.graphics.Color.parseColor(
                                if (!customHex.startsWith("#")) "#$customHex" else customHex
                            )
                            parsed.toLong() or 0xFF000000
                        } catch (e: Exception) {
                            presetColors[0] // fallback if invalid
                        }
                    }

                    val finalMethod = initialMethod?.copy(
                        name = name.trim(),
                        icon = selectedIcon,
                        color = finalColor,
                        billingCycleType = if (billingEnabled) BillingCycleType.MONTHLY_CLOSING_DAY.value else BillingCycleType.NONE.value,
                        billingCycleDay = if (billingEnabled) billingDay else null,
                        billingLimitType = if (billingEnabled) billingLimitType.value else BillingLimitType.CREDIT.value,
                        billingLimitAmount = if (billingEnabled) billingLimitAmount else null
                    ) ?: PaymentMethod(
                        name = name.trim(),
                        icon = selectedIcon,
                        color = finalColor,
                        type = selectedType,
                        parentId = parentId,
                        billingCycleType = if (billingEnabled) BillingCycleType.MONTHLY_CLOSING_DAY.value else BillingCycleType.NONE.value,
                        billingCycleDay = if (billingEnabled) billingDay else null,
                        billingLimitType = if (billingEnabled) billingLimitType.value else BillingLimitType.CREDIT.value,
                        billingLimitAmount = if (billingEnabled) billingLimitAmount else null
                    )
                    onConfirm(finalMethod)
                },
                enabled = name.isNotBlank() && (!billingEnabled || billingDay != null)
            ) { Text(if (initialMethod != null) "儲存" else "新增") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

private fun hasBillingLimit(method: PaymentMethod): Boolean =
    (method.billingLimitAmount ?: 0.0) > 0.0

private fun billingLimitLabel(method: PaymentMethod): String =
    when (BillingLimitType.fromValue(method.billingLimitType)) {
        BillingLimitType.CREDIT -> "信用額度"
        BillingLimitType.PROMO -> "優惠額度"
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
