package com.rton.expensebucket.ui.viewmodel

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.rton.expensebucket.R
import com.rton.expensebucket.data.backup.BackupConsentIncompleteException
import com.rton.expensebucket.data.backup.BackupDeviceState
import com.rton.expensebucket.data.backup.BackupPasswordException
import com.rton.expensebucket.data.backup.BackupProblem
import com.rton.expensebucket.data.backup.BackupVersionException
import com.rton.expensebucket.data.backup.CloudBackupRepository
import com.rton.expensebucket.data.backup.CloudBackupWorker
import com.rton.expensebucket.data.backup.DriveBackupFile
import com.rton.expensebucket.data.backup.PreparedBackup
import com.rton.expensebucket.data.backup.RestoreMode
import com.rton.expensebucket.data.backup.backupProblem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal enum class BackupWorkState { IDLE, QUEUED, UPLOADING }

internal data class BackupNotice(@param:StringRes val message: Int, val count: Int? = null)

internal data class BackupUiState(
    val busy: Boolean = false,
    val consent: PendingIntent? = null,
    val files: List<DriveBackupFile>? = null,
    val prepared: PreparedBackup? = null,
    val notice: BackupNotice? = null
)

@HiltViewModel
internal class CloudBackupViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val repository: CloudBackupRepository
) : ViewModel() {
    private val mutableUi = MutableStateFlow(BackupUiState())
    val ui = mutableUi.asStateFlow()
    val device: StateFlow<BackupDeviceState?> =
        repository.state.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val work = WorkManager.getInstance(context).getWorkInfosByTagFlow(CloudBackupWorker.BACKUP_TAG)
        .map { jobs ->
            when {
                jobs.any { it.state == WorkInfo.State.RUNNING } -> BackupWorkState.UPLOADING
                jobs.any { !it.state.isFinished && CloudBackupWorker.IMMEDIATE_TAG in it.tags } ->
                    BackupWorkState.QUEUED
                else -> BackupWorkState.IDLE
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackupWorkState.IDLE)

    fun authorize() = perform(fallback = R.string.cloud_backup_authorization_incomplete) {
        val result = repository.requestAuthorization()
        if (result.hasResolution()) mutableUi.update { it.copy(consent = result.pendingIntent) }
        else repository.connect(result)
    }

    fun consumeConsent() = mutableUi.update { it.copy(consent = null) }

    fun finishAuthorization(intent: Intent?, accepted: Boolean) {
        if (intent == null) authorizationUnavailable()
        else perform(fallback = R.string.cloud_backup_authorization_incomplete) {
            repository.connect(intent, accepted)
        }
    }

    fun authorizationUnavailable() {
        mutableUi.update { it.copy(notice = BackupNotice(R.string.cloud_backup_authorization_incomplete)) }
    }

    fun enable(password: CharArray) = perform(password) { repository.enable(password) }
    fun disable() = perform { repository.disable() }
    fun disconnect() = perform { repository.disconnect() }
    fun backupNow() = perform { repository.backupNow() }

    fun listBackups() = perform {
        val files = repository.availableBackups()
        mutableUi.update {
            if (files.isEmpty()) it.copy(notice = BackupNotice(R.string.cloud_backup_no_files))
            else it.copy(files = files)
        }
    }

    fun dismissFiles() = mutableUi.update { it.copy(files = null) }

    fun prepare(file: DriveBackupFile, password: CharArray) = perform(password) {
        val prepared = repository.prepare(file, password)
        mutableUi.update { it.copy(files = null, prepared = prepared) }
    }

    fun skipRestore() = mutableUi.update { it.copy(prepared = null) }

    fun restore(mode: RestoreMode) = perform {
        val prepared = mutableUi.value.prepared ?: return@perform
        val count = repository.restore(prepared, mode)
        val message = if (mode == RestoreMode.MERGE) R.string.cloud_backup_merge_done else R.string.cloud_backup_restore_done
        mutableUi.update { it.copy(prepared = null, notice = BackupNotice(message, count)) }
    }

    fun consumeNotice(notice: BackupNotice) {
        mutableUi.update { if (it.notice == notice) it.copy(notice = null) else it }
    }

    private fun perform(
        password: CharArray? = null,
        @StringRes fallback: Int = R.string.cloud_backup_failed,
        action: suspend () -> Unit
    ) {
        if (mutableUi.value.busy) {
            password?.fill('\u0000')
            return
        }
        mutableUi.update { it.copy(busy = true, notice = null) }
        viewModelScope.launch {
            try {
                action()
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                val message = when (failure) {
                    is BackupConsentIncompleteException -> R.string.cloud_backup_authorization_incomplete
                    is BackupPasswordException -> R.string.cloud_backup_password_invalid
                    is BackupVersionException -> R.string.cloud_backup_update_required
                    else -> failure.backupProblem().let { if (it == BackupProblem.FAILED) fallback else it.message }
                }
                mutableUi.update { it.copy(notice = BackupNotice(message)) }
            } finally {
                password?.fill('\u0000')
                mutableUi.update { it.copy(busy = false) }
            }
        }
    }
}

@get:StringRes
internal val BackupProblem.message: Int
    get() = when (this) {
        BackupProblem.REAUTHORIZE -> R.string.cloud_backup_authorize_again
        BackupProblem.CONFIGURATION -> R.string.cloud_backup_configuration
        BackupProblem.TOO_LARGE -> R.string.cloud_backup_too_large
        BackupProblem.LOCAL_KEY -> R.string.cloud_backup_key_missing
        BackupProblem.FAILED -> R.string.cloud_backup_failed
    }
