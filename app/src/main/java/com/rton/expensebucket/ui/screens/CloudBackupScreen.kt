package com.rton.expensebucket.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rton.expensebucket.R
import com.rton.expensebucket.data.backup.BackupDeviceState
import com.rton.expensebucket.data.backup.BackupProblem
import com.rton.expensebucket.data.backup.BackupPreview
import com.rton.expensebucket.data.backup.DriveBackupFile
import com.rton.expensebucket.data.backup.RestoreMode
import com.rton.expensebucket.ui.viewmodel.BackupNotice
import com.rton.expensebucket.ui.viewmodel.BackupWorkState
import com.rton.expensebucket.ui.viewmodel.CloudBackupViewModel
import com.rton.expensebucket.ui.viewmodel.message
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CloudBackupScreen(onBack: () -> Unit, viewModel: CloudBackupViewModel = hiltViewModel()) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val work by viewModel.work.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var createPassword by rememberSaveable { mutableStateOf(false) }
    var selectedFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var awaitingConsent by rememberSaveable { mutableStateOf(false) }
    val selectedFile = ui.files?.firstOrNull { it.id == selectedFileId }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val authorizationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        awaitingConsent = false
        viewModel.finishAuthorization(result.data, accepted = result.resultCode == Activity.RESULT_OK)
    }
    LaunchedEffect(ui.consent) {
        val intent = ui.consent ?: return@LaunchedEffect
        viewModel.consumeConsent()
        awaitingConsent = true
        try {
            authorizationLauncher.launch(IntentSenderRequest.Builder(intent).build())
        } catch (_: IllegalStateException) {
            awaitingConsent = false
            viewModel.authorizationUnavailable()
        }
    }
    LaunchedEffect(ui.prepared) {
        if (ui.prepared != null) selectedFileId = null
    }
    LaunchedEffect(device?.enabled) {
        if (device?.enabled == true) createPassword = false
    }
    val modalError = if (createPassword || selectedFile != null || ui.prepared != null) ui.notice else null
    LaunchedEffect(ui.notice, modalError) {
        val notice = ui.notice ?: return@LaunchedEffect
        if (modalError == null) {
            val message = notice.count?.let { context.getString(notice.message, it) }
                ?: context.getString(notice.message)
            snackbar.showSnackbar(message)
            viewModel.consumeNotice(notice)
        }
    }

    CloudBackupPage(
        device = device,
        work = work,
        busy = ui.busy || awaitingConsent,
        snackbar = snackbar,
        onBack = onBack,
        onAuthorize = viewModel::authorize,
        onEnable = { createPassword = true },
        onPause = viewModel::disable,
        onBackupNow = viewModel::backupNow,
        onFindBackups = viewModel::listBackups,
        onDisconnect = viewModel::disconnect
    )

    val prepared = ui.prepared
    val files = ui.files
    if (createPassword || prepared != null || files != null) {
        BackupBottomSheet(
            busy = ui.busy,
            onDismiss = {
                createPassword = false
                selectedFileId = null
                if (prepared != null) viewModel.skipRestore()
                if (files != null) viewModel.dismissFiles()
            }
        ) {
            when {
                prepared != null -> RestoreDecisionContent(
                    preview = prepared.preview,
                    busy = ui.busy,
                    notice = modalError,
                    onSkip = viewModel::skipRestore,
                    onRestore = viewModel::restore
                )
                createPassword -> BackupPasswordContent(
                    creating = true,
                    busy = ui.busy,
                    notice = modalError,
                    onCancel = { createPassword = false },
                    onSubmit = viewModel::enable
                )
                selectedFile != null -> BackupPasswordContent(
                    creating = false,
                    busy = ui.busy,
                    notice = modalError,
                    onCancel = {
                        modalError?.let(viewModel::consumeNotice)
                        selectedFileId = null
                    },
                    onSubmit = { password -> viewModel.prepare(selectedFile, password) }
                )
                files != null -> BackupFilesContent(
                    files = files,
                    onCancel = viewModel::dismissFiles,
                    onSelect = { selectedFileId = it.id }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CloudBackupPage(
    device: BackupDeviceState?,
    work: BackupWorkState,
    busy: Boolean,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAuthorize: () -> Unit,
    onEnable: () -> Unit,
    onPause: () -> Unit,
    onBackupNow: () -> Unit,
    onFindBackups: () -> Unit,
    onDisconnect: () -> Unit
) {
    val connected = device?.account != null
    val actionsEnabled = !busy && work != BackupWorkState.UPLOADING
    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                }
            )
        },
        bottomBar = {
            if (device != null) {
                val (title, action) = when {
                    !connected -> R.string.cloud_backup_connect to onAuthorize
                    device.problem == BackupProblem.REAUTHORIZE -> R.string.cloud_backup_reconnect to onAuthorize
                    device.enabled -> R.string.cloud_backup_now to onBackupNow
                    else -> R.string.cloud_backup_enable to onEnable
                }
                Surface {
                    Box(
                        Modifier.fillMaxWidth().windowInsetsPadding(
                            WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (connected) {
                                OutlinedButton(
                                    onClick = onFindBackups,
                                    enabled = actionsEnabled,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                ) {
                                    Text(stringResource(R.string.cloud_backup_find), textAlign = TextAlign.Center)
                                }
                            }
                            BackupPrimaryButton(
                                title = stringResource(title),
                                enabled = actionsEnabled && (title != R.string.cloud_backup_now || work == BackupWorkState.IDLE),
                                onClick = action
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { insets ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(insets).consumeWindowInsets(insets)) {
            val viewportHeight = maxHeight
            val headingHeight = (viewportHeight * if (connected) 0.42f else 0.70f).coerceAtMost(368.dp)
            Column(
                Modifier.align(Alignment.TopCenter).widthIn(max = 520.dp).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).heightIn(min = viewportHeight)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    Modifier.fillMaxWidth().heightIn(min = headingHeight),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)
                ) {
                    Text(
                        stringResource(R.string.cloud_backup_title),
                        modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineLarge,
                        textAlign = TextAlign.Center
                    )
                    if (device != null && !connected) {
                        Text(
                            stringResource(R.string.cloud_backup_connect_hint),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (device == null || (!connected && busy)) LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                device?.account?.let { account ->
                    Column(Modifier.padding(top = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(account.email, style = MaterialTheme.typography.titleMedium)
                        Text(
                            stringResource(
                                if (device.enabled) R.string.cloud_backup_daily else R.string.cloud_backup_paused
                            ),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        device.lastBackup?.let {
                            Text(stringResource(R.string.cloud_backup_latest, backupTime(it)))
                        }
                        when (work) {
                            BackupWorkState.UPLOADING -> Text(stringResource(R.string.cloud_backup_uploading))
                            BackupWorkState.QUEUED -> Text(stringResource(R.string.cloud_backup_queued))
                            BackupWorkState.IDLE -> Unit
                        }
                        device.problem?.let {
                            Text(stringResource(it.message), color = MaterialTheme.colorScheme.error)
                        }
                        if (busy || work == BackupWorkState.UPLOADING) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                        if (device.enabled) {
                            BackupTextButton(stringResource(R.string.cloud_backup_pause), actionsEnabled, onPause)
                        }
                        BackupTextButton(stringResource(R.string.cloud_backup_disconnect), actionsEnabled, onDisconnect)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BackupBottomSheet(busy: Boolean, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val dismissBlocked by rememberUpdatedState(busy)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value -> value != SheetValue.Hidden || !dismissBlocked }
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        content()
    }
}

@Composable
private fun BackupSheetLayout(title: String, actions: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.titleLarge)
            content()
        }
        actions()
    }
}

@Composable
private fun BackupFilesContent(files: List<DriveBackupFile>, onCancel: () -> Unit, onSelect: (DriveBackupFile) -> Unit) {
    BackupSheetLayout(
        title = stringResource(R.string.cloud_backup_find),
        actions = { BackupTextButton(stringResource(R.string.cancel), enabled = true, onClick = onCancel) }
    ) {
        Column {
            files.forEach { file ->
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable(role = Role.Button) { onSelect(file) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(backupTime(file.time), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
internal fun BackupPasswordContent(
    creating: Boolean,
    busy: Boolean,
    notice: BackupNotice?,
    onCancel: () -> Unit,
    onSubmit: (CharArray) -> Unit
) {
    var password by remember { mutableStateOf("") }
    var repeated by remember { mutableStateOf("") }
    val focus = LocalFocusManager.current
    val canSubmit = if (creating) password.length >= 12 && password == repeated else password.isNotEmpty()
    val title = stringResource(if (creating) R.string.cloud_backup_enable else R.string.cloud_backup_unlock)

    BackupSheetLayout(
        title = title,
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupPrimaryButton(title, enabled = !busy && canSubmit) {
                    val value = password.toCharArray()
                    password = ""
                    repeated = ""
                    focus.clearFocus()
                    onSubmit(value)
                }
                BackupTextButton(
                    stringResource(if (creating) R.string.cancel else R.string.back), enabled = !busy, onClick = onCancel
                )
            }
        }
    ) {
        Text(
            stringResource(if (creating) R.string.cloud_backup_password_hint else R.string.cloud_backup_unlock_hint),
            style = MaterialTheme.typography.bodyMedium
        )
        OutlinedTextField(
            password, { password = it },
            modifier = Modifier.fillMaxWidth(),
            enabled = !busy, singleLine = true,
            label = { Text(stringResource(R.string.cloud_backup_password)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )
        if (creating) {
            OutlinedTextField(
                repeated, { repeated = it },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy, singleLine = true,
                label = { Text(stringResource(R.string.cloud_backup_repeat_password)) },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let { Text(stringResource(it.message), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
internal fun RestoreDecisionContent(
    preview: BackupPreview,
    busy: Boolean,
    notice: BackupNotice?,
    onSkip: () -> Unit,
    onRestore: (RestoreMode) -> Unit
) {
    var choice by remember(preview) { mutableStateOf<RestoreMode?>(null) }
    BackupSheetLayout(
        title = stringResource(R.string.cloud_backup_review),
        actions = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BackupPrimaryButton(
                    stringResource(R.string.confirm),
                    enabled = choice != null && !busy,
                    onClick = { choice?.let(onRestore) }
                )
                BackupTextButton(stringResource(R.string.cloud_backup_skip), enabled = !busy, onClick = onSkip)
            }
        }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.cloud_backup_remote_time, backupTime(preview.time)))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cloud_backup_remote_count, preview.remote.count))
                preview.remote.latestDate?.let {
                    Text(
                        stringResource(R.string.cloud_backup_latest_transaction, backupTime(it, includeTime = false)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(R.string.cloud_backup_local_count, preview.local.count))
                preview.local.latestDate?.let {
                    Text(
                        stringResource(R.string.cloud_backup_latest_transaction, backupTime(it, includeTime = false)),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RestoreChoice(
                R.string.cloud_backup_merge, R.string.cloud_backup_merge_hint,
                choice == RestoreMode.MERGE, !busy
            ) { choice = RestoreMode.MERGE }
            RestoreChoice(
                R.string.cloud_backup_overwrite, R.string.cloud_backup_overwrite_hint,
                choice == RestoreMode.REPLACE, !busy
            ) { choice = RestoreMode.REPLACE }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        notice?.let { Text(stringResource(it.message), color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun BackupPrimaryButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(title, textAlign = TextAlign.Center)
    }
}

@Composable
private fun BackupTextButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(title, textAlign = TextAlign.Center)
    }
}

@Composable
private fun RestoreChoice(title: Int, detail: Int, selected: Boolean, enabled: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Column(Modifier.weight(1f)) {
            Text(stringResource(title), style = MaterialTheme.typography.titleSmall)
            Text(stringResource(detail), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun backupTime(time: Long, includeTime: Boolean = true): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = if (includeTime) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale)
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
    }
    return format.format(Date(time))
}
