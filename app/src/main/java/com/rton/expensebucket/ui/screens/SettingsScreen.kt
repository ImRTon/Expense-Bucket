package com.rton.expensebucket.ui.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rton.expensebucket.service.ExpenseBucketNotificationService
import com.rton.expensebucket.data.AppTheme
import com.rton.expensebucket.data.AppPalette
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    currentTheme: AppTheme = AppTheme.SYSTEM,
    onSetTheme: (AppTheme) -> Unit = {},
    currentPalette: AppPalette = AppPalette.DEFAULT,
    onSetPalette: (AppPalette) -> Unit = {},
    monthlyBudget: Double = 0.0,
    onSetMonthlyBudget: (Double) -> Unit = {},
    firstDayOfWeek: Int = java.util.Calendar.MONDAY,
    onSetFirstDayOfWeek: (Int) -> Unit = {},
    onBack: () -> Unit = {},
    onNavigateToCategories: () -> Unit = {},
    onNavigateToDrafts: () -> Unit = {},
    onExportData: (Context, Uri, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onImportData: (Context, Uri, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> }
) {
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showFirstDayDialog by remember { mutableStateOf(false) }

    // ─── Theme Dialog ──────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    "選擇主題",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                Column {
                    AppTheme.entries.forEach { themeOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetTheme(themeOption)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = (currentTheme == themeOption),
                                onClick = {
                                    onSetTheme(themeOption)
                                    showThemeDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = themeOption.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ─── Palette Dialog ──────────────────────────────────────────
    if (showPaletteDialog) {
        AlertDialog(
            onDismissRequest = { showPaletteDialog = false },
            title = {
                Text(
                    "選擇主題色彩",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                Column {
                    AppPalette.entries.forEach { paletteOption ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetPalette(paletteOption)
                                    showPaletteDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = (currentPalette == paletteOption),
                                onClick = {
                                    onSetPalette(paletteOption)
                                    showPaletteDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = paletteOption.displayName,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPaletteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ─── First Day of Week Dialog ──────────────────────────────
    if (showFirstDayDialog) {
        AlertDialog(
            onDismissRequest = { showFirstDayDialog = false },
            title = {
                Text(
                    "每週起始日",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                Column {
                    val days = listOf(java.util.Calendar.SUNDAY to "星期日", java.util.Calendar.MONDAY to "星期一")
                    days.forEach { (value, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSetFirstDayOfWeek(value)
                                    showFirstDayDialog = false
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            RadioButton(
                                selected = (firstDayOfWeek == value),
                                onClick = {
                                    onSetFirstDayOfWeek(value)
                                    showFirstDayDialog = false
                                }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFirstDayDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // ─── Budget Dialog ─────────────────────────────────────────
    if (showBudgetDialog) {
        var budgetText by remember {
            mutableStateOf(if (monthlyBudget > 0) monthlyBudget.toLong().toString() else "")
        }

        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("設定月預算") },
            text = {
                Column {
                    Text(
                        "設定每月預算金額，用於「支出/預算」水位顯示",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.all { it.isDigit() || it == '.' }) {
                                budgetText = newValue
                            }
                        },
                        label = { Text("月預算金額") },
                        prefix = { Text("NT$ ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val amount = budgetText.toDoubleOrNull() ?: 0.0
                    onSetMonthlyBudget(amount)
                    showBudgetDialog = false
                }) {
                    Text("確定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            onExportData(context, uri) { success, errorMsg ->
                if (success) {
                    Toast.makeText(context, "匯出成功", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "匯出失敗: ${errorMsg ?: "未知錯誤"}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            onImportData(context, uri) { success, errorMsg ->
                if (success) {
                    Toast.makeText(context, errorMsg ?: "匯入成功", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "匯入失敗: ${errorMsg ?: "未知錯誤"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "設定",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // ─── General ────────────────────────────────────────
            Text(
                "一般",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Filled.Palette,
                title = "主題色彩",
                subtitle = currentPalette.displayName,
                onClick = { showPaletteDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.BrightnessMedium,
                title = "深淺模式",
                subtitle = currentTheme.displayName,
                onClick = { showThemeDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.Language,
                title = "語言",
                subtitle = "繁體中文"
            )
            SettingsItem(
                icon = Icons.Filled.AttachMoney,
                title = "預設幣別",
                subtitle = "TWD"
            )
            SettingsItem(
                icon = Icons.Filled.CalendarToday,
                title = "每週起始日",
                subtitle = if (firstDayOfWeek == java.util.Calendar.SUNDAY) "星期日" else "星期一",
                onClick = { showFirstDayDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.AccountBalanceWallet,
                title = "月預算",
                subtitle = if (monthlyBudget > 0) {
                    "NT$ ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(monthlyBudget)}"
                } else {
                    "未設定"
                },
                onClick = { showBudgetDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.Category,
                title = "分類管理",
                subtitle = "新增、編輯子母分類",
                onClick = onNavigateToCategories
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Notifications ──────────────────────────────────
            Text(
                "智慧記帳",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            val context = LocalContext.current
            val lifecycleOwner = LocalLifecycleOwner.current

            val notificationEnabled = remember {
                mutableStateOf(isNotificationListenerEnabled(context))
            }
            // Refresh when returning from system settings
            DisposableEffect(lifecycleOwner) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationEnabled.value = isNotificationListenerEnabled(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }
            SettingsToggleItem(
                icon = Icons.Filled.Notifications,
                title = "通知欄攔截",
                subtitle = if (notificationEnabled.value) "已啟用 - 自動攔截刷卡通知"
                           else "點擊前往系統設定開啟權限",
                checked = notificationEnabled.value,
                onCheckedChange = {
                    // Open system notification listener settings
                    context.startActivity(
                        Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    )
                },
                onClick = onNavigateToDrafts
            )

            var ocrEnabled by remember { mutableStateOf(true) }
            SettingsToggleItem(
                icon = Icons.Filled.DocumentScanner,
                title = "OCR 截圖辨識",
                subtitle = "透過分享截圖自動記帳",
                checked = ocrEnabled,
                onCheckedChange = { ocrEnabled = it }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Data ───────────────────────────────────────────
            Text(
                "資料",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Filled.FileUpload,
                title = "匯出資料",
                subtitle = "匯出為 CSV 檔案",
                onClick = {
                    exportLauncher.launch("ExpenseBucket_Export.csv")
                }
            )
            SettingsItem(
                icon = Icons.Filled.FileDownload,
                title = "匯入資料",
                subtitle = "從 CSV 檔案匯入",
                onClick = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                }
            )
            SettingsItem(
                icon = Icons.Filled.Category,
                title = "管理分類",
                subtitle = "新增或編輯記帳分類",
                onClick = onNavigateToCategories
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── About ──────────────────────────────────────────
            Text(
                "關於",
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Filled.Info,
                title = "版本",
                subtitle = "1.0.0"
            )
        }
    }
}

@Composable
private fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
    }
}

@Composable
private fun SettingsToggleItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    onClick: (() -> Unit)? = null
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        val modifier = if (onClick != null) {
            Modifier.fillMaxWidth().clickable { onClick() }
        } else {
            Modifier.fillMaxWidth()
        }
        
        Row(
            modifier = modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Medium
                    )
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (onClick != null) {
                VerticalDivider(
                    modifier = Modifier
                        .height(32.dp)
                        .padding(horizontal = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

/**
 * Check if our NotificationListenerService has permission.
 */
private fun isNotificationListenerEnabled(context: Context): Boolean {
    val cn = ComponentName(context, ExpenseBucketNotificationService::class.java)
    val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return flat != null && flat.contains(cn.flattenToString())
}
