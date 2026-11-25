package com.rton.expensebucket.ui.screens

import com.rton.expensebucket.ui.util.localizedLabel
import com.rton.expensebucket.R
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.rton.expensebucket.service.ExpenseBucketNotificationService
import com.rton.expensebucket.ui.theme.CactusPrimaryLight
import com.rton.expensebucket.ui.theme.CactusSecondaryLight
import com.rton.expensebucket.ui.theme.CactusTertiaryLight
import com.rton.expensebucket.ui.theme.Emerald20
import com.rton.expensebucket.ui.theme.LatteCaramelLight
import com.rton.expensebucket.ui.theme.LatteSageLight
import com.rton.expensebucket.ui.theme.LatteTerracottaLight
import com.rton.expensebucket.ui.theme.StrawberryPrimaryLight
import com.rton.expensebucket.ui.theme.StrawberrySecondaryLight
import com.rton.expensebucket.ui.theme.StrawberryTertiaryLight
import com.rton.expensebucket.ui.theme.Teal20
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
    notificationDraftsSheet: @Composable (
        onDismiss: () -> Unit,
        onEnableNotificationAccess: (() -> Unit)?
    ) -> Unit = { _, _ -> },
    onExportData: (Context, Uri, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onImportData: (Context, Uri, (Boolean, String?) -> Unit) -> Unit = { _, _, _ -> },
    onNavigateToCloudBackup: () -> Unit = {},
    screenshotOcrEnabled: Boolean = true,
    onSetScreenshotOcrEnabled: (Boolean) -> Unit = {}
) {
    val strings = LocalContext.current.resources
    val languageContext = LocalContext.current
    var showLanguageDialog by remember { mutableStateOf(false) }
    val currentLanguage = com.rton.expensebucket.data.AppLanguage.current(languageContext)

    if (showLanguageDialog) {
        AlertDialog(
            onDismissRequest = { showLanguageDialog = false },
            title = { Text(strings.getString(R.string.choose_language)) },
            text = {
                Column {
                    com.rton.expensebucket.data.AppLanguage.entries.forEach { language ->
                        val selectLanguage = {
                            showLanguageDialog = false
                            com.rton.expensebucket.data.AppLanguage.set(languageContext, language)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable(onClick = selectLanguage)
                                .padding(vertical = 8.dp)
                        ) {
                            RadioButton(selected = currentLanguage == language, onClick = selectLanguage)
                            Spacer(Modifier.width(8.dp))
                            Text(if (language == com.rton.expensebucket.data.AppLanguage.SYSTEM) {
                                strings.getString(R.string.language_system)
                            } else language.nativeName)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showLanguageDialog = false }) {
                    Text(strings.getString(R.string.cancel))
                }
            }
        )
    }

    var showBudgetDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showPaletteDialog by remember { mutableStateOf(false) }
    var showFirstDayDialog by remember { mutableStateOf(false) }
    var showScreenshotOcrGuide by rememberSaveable { mutableStateOf(false) }
    var showNotificationDrafts by rememberSaveable { mutableStateOf(false) }

    if (showScreenshotOcrGuide) {
        ScreenshotOcrGuideSheet(
            enabled = screenshotOcrEnabled,
            onEnabledChange = onSetScreenshotOcrEnabled,
            onDismiss = { showScreenshotOcrGuide = false }
        )
    }

    // ─── Theme Dialog ──────────────────────────────────────────
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = {
                Text(
                    strings.getString(R.string.choose_theme),
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
                                text = strings.localizedLabel(themeOption.displayName),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text(strings.getString(R.string.cancel))
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
                    strings.getString(R.string.choose_palette),
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = strings.localizedLabel(paletteOption.displayName),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                PalettePreview(palette = paletteOption)
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPaletteDialog = false }) {
                    Text(strings.getString(R.string.cancel))
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
                    strings.getString(R.string.first_day_of_week),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
            },
            text = {
                Column {
                    val days = listOf(java.util.Calendar.SUNDAY to strings.getString(R.string.sunday), java.util.Calendar.MONDAY to strings.getString(R.string.monday))
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
                    Text(strings.getString(R.string.cancel))
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
            title = { Text(strings.getString(R.string.set_monthly_budget)) },
            text = {
                Column {
                    Text(
                        strings.getString(R.string.monthly_budget_hint),
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
                        label = { Text(strings.getString(R.string.monthly_budget_amount)) },
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
                    Text(strings.getString(R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) {
                    Text(strings.getString(R.string.cancel))
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
                    Toast.makeText(context, strings.getString(R.string.export_success), Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, strings.getString(R.string.export_failed, errorMsg ?: strings.getString(R.string.unknown_error)), Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(context, errorMsg ?: strings.getString(R.string.import_success), Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, strings.getString(R.string.import_failed, errorMsg ?: strings.getString(R.string.unknown_error)), Toast.LENGTH_LONG).show()
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
                        strings.getString(R.string.settings),
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
                strings.getString(R.string.general),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Filled.Palette,
                title = strings.getString(R.string.theme_colors),
                subtitle = strings.localizedLabel(currentPalette.displayName),
                onClick = { showPaletteDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.BrightnessMedium,
                title = strings.getString(R.string.theme_mode),
                subtitle = strings.localizedLabel(currentTheme.displayName),
                onClick = { showThemeDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.Language,
                title = strings.getString(R.string.language),
                subtitle = if (currentLanguage == com.rton.expensebucket.data.AppLanguage.SYSTEM) {
                    strings.getString(R.string.language_system)
                } else currentLanguage.nativeName,
                onClick = { showLanguageDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.AttachMoney,
                title = strings.getString(R.string.default_currency),
                subtitle = "TWD"
            )
            SettingsItem(
                icon = Icons.Filled.CalendarToday,
                title = strings.getString(R.string.first_day_of_week),
                subtitle = if (firstDayOfWeek == java.util.Calendar.SUNDAY) strings.getString(R.string.sunday) else strings.getString(R.string.monday),
                onClick = { showFirstDayDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.AccountBalanceWallet,
                title = strings.getString(R.string.monthly_budget),
                subtitle = if (monthlyBudget > 0) {
                    "NT$ ${NumberFormat.getNumberInstance().apply { maximumFractionDigits = 0 }.format(monthlyBudget)}"
                } else {
                    strings.getString(R.string.not_set)
                },
                onClick = { showBudgetDialog = true }
            )
            SettingsItem(
                icon = Icons.Filled.Category,
                title = strings.getString(R.string.category_management),
                subtitle = strings.getString(R.string.manage_categories_hint),
                onClick = onNavigateToCategories
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Notifications ──────────────────────────────────
            Text(
                strings.getString(R.string.smart_capture),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            val lifecycleOwner = LocalLifecycleOwner.current
            var notificationEnabled by remember(context) {
                mutableStateOf(isNotificationListenerEnabled(context))
            }
            val openNotificationSettings: () -> Unit = {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            DisposableEffect(lifecycleOwner, context) {
                val observer = LifecycleEventObserver { _, event ->
                    if (event == Lifecycle.Event.ON_RESUME) {
                        notificationEnabled = isNotificationListenerEnabled(context)
                    }
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose {
                    lifecycleOwner.lifecycle.removeObserver(observer)
                }
            }

            if (showNotificationDrafts) {
                notificationDraftsSheet(
                    { showNotificationDrafts = false },
                    openNotificationSettings.takeUnless { notificationEnabled }
                )
            }

            SettingsToggleItem(
                icon = Icons.Filled.Notifications,
                title = strings.getString(R.string.notification_capture),
                subtitle = strings.getString(
                    if (notificationEnabled) R.string.notification_capture_enabled
                    else R.string.notification_access_required
                ),
                checked = notificationEnabled,
                onCheckedChange = { openNotificationSettings() },
                onClick = { showNotificationDrafts = true },
                onClickLabel = strings.getString(R.string.pending_transactions)
            )

            SettingsToggleItem(
                icon = Icons.Filled.DocumentScanner,
                title = strings.getString(R.string.screenshot_recognition),
                subtitle = strings.getString(R.string.screenshot_share_hint),
                checked = screenshotOcrEnabled,
                onCheckedChange = onSetScreenshotOcrEnabled,
                onClick = { showScreenshotOcrGuide = true },
                onClickLabel = strings.getString(R.string.screenshot_ocr_guide_title)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── Data ───────────────────────────────────────────
            Text(
                strings.getString(R.string.data),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Filled.CloudUpload,
                title = strings.getString(R.string.cloud_backup_title),
                subtitle = strings.getString(R.string.cloud_backup_settings_hint),
                onClick = onNavigateToCloudBackup
            )
            SettingsItem(
                icon = Icons.Filled.FileUpload,
                title = strings.getString(R.string.export_data),
                subtitle = strings.getString(R.string.export_csv),
                onClick = {
                    exportLauncher.launch("ExpenseBucket_Export.csv")
                }
            )
            SettingsItem(
                icon = Icons.Filled.FileDownload,
                title = strings.getString(R.string.import_data),
                subtitle = strings.getString(R.string.import_csv),
                onClick = {
                    importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv", "*/*"))
                }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // ─── About ──────────────────────────────────────────
            Text(
                strings.getString(R.string.about),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )

            SettingsItem(
                icon = Icons.Filled.Info,
                title = strings.getString(R.string.version),
                subtitle = "1.1.0"
            )
        }
    }
}

@Composable
private fun PalettePreview(palette: AppPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        palettePreviewColors(palette).forEach { swatch ->
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color = swatch, shape = CircleShape)
            )
        }
    }
}

private fun palettePreviewColors(palette: AppPalette): List<Color> = when (palette) {
    AppPalette.DEFAULT -> listOf(Emerald20, Teal20, Color(0xFF236488))
    AppPalette.LATTE -> listOf(LatteSageLight, LatteCaramelLight, LatteTerracottaLight)
    AppPalette.STRAWBERRY_MILK -> listOf(
        StrawberryPrimaryLight,
        StrawberrySecondaryLight,
        StrawberryTertiaryLight
    )
    AppPalette.BERRY_YOGURT -> listOf(CactusPrimaryLight, CactusSecondaryLight, CactusTertiaryLight)
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
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
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
    onClick: (() -> Unit)? = null,
    onClickLabel: String? = null
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        val modifier = if (onClick != null) {
            Modifier.fillMaxWidth().clickable(onClickLabel = onClickLabel, onClick = onClick)
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
                onCheckedChange = onCheckedChange,
                modifier = Modifier.semantics { contentDescription = title }
            )
        }
    }
}

/**
 * Check if our NotificationListenerService has permission.
 */
private fun isNotificationListenerEnabled(context: Context): Boolean {
    val listener = ComponentName(context, ExpenseBucketNotificationService::class.java)
    val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
    return enabledListeners.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == listener }
}
