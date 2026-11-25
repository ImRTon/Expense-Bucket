package com.rton.expensebucket.data.backup

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.rton.expensebucket.data.dao.BackupTransactionSummary
import com.rton.expensebucket.service.CaptureNotificationContract
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.crypto.AEADBadTagException
import javax.inject.Inject
import javax.inject.Singleton

internal enum class RestoreMode { MERGE, REPLACE }
internal enum class BackupRun { COMPLETED, RETRY, FAILED }
internal class BackupPasswordException : Exception()

internal class PreparedBackup(
    val preview: BackupPreview,
    val snapshot: LedgerSnapshot,
    val accountId: String
)

@Singleton
internal class CloudBackupRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val device: BackupDeviceStore,
    private val authorization: DriveBackupAuthorization,
    private val drive: DriveBackupClient,
    private val ledger: LedgerBackupStore
) {
    private val mutex = Mutex()
    val state = device.state

    suspend fun requestAuthorization(): AuthorizationResult =
        authorization.request(state.first().account?.email)

    suspend fun connect(intent: Intent, accepted: Boolean) {
        val result = authorization.complete(intent)
        if (!accepted) throw BackupConsentIncompleteException()
        connect(result)
    }

    suspend fun connect(result: AuthorizationResult) = serialized {
        val account = drive.account(authorization.token(result))
        val current = state.first()
        if (current.account != null && current.account.id != account.id) throw BackupAuthorizationException()
        device.connect(account)
        if (current.enabled && current.problem == BackupProblem.REAUTHORIZE) {
            CloudBackupWorker.runSoon(workManager())
        }
    }

    suspend fun disconnect() = serialized {
        device.disconnect()
        CloudBackupWorker.cancel(workManager())
    }

    suspend fun enable(password: CharArray) = serialized {
        val account = connectedAccount()
        withToken(account) { }
        val salt = BackupEncryption.newSalt()
        val key = BackupEncryption.deriveKey(password, salt)
        try {
            device.enable(salt, key)
            CloudBackupWorker.schedule(workManager())
            CloudBackupWorker.runSoon(workManager())
        } finally {
            key.fill(0)
        }
    }

    suspend fun disable() = serialized {
        device.disable()
        CloudBackupWorker.cancel(workManager())
    }

    suspend fun backupNow() = serialized {
        if (state.first().enabled) CloudBackupWorker.runSoon(workManager())
    }

    suspend fun availableBackups(): List<DriveBackupFile> = serialized {
        withToken(connectedAccount()) { drive.files(it) }
    }

    suspend fun prepare(file: DriveBackupFile, password: CharArray): PreparedBackup = serialized {
        val account = connectedAccount()
        val encrypted = withToken(account) { drive.download(it, file) }
        val key = BackupEncryption.deriveKey(password, BackupEncryption.salt(encrypted))
        val payload = try {
            BackupEncryption.decrypt(encrypted, key)
        } catch (_: AEADBadTagException) {
            throw BackupPasswordException()
        } finally {
            key.fill(0)
        }
        val snapshot = try {
            ledger.deserialize(payload)
        } finally {
            payload.fill(0)
        }
        PreparedBackup(
            BackupPreview(
                file.time,
                BackupTransactionSummary(snapshot.transactions.size, snapshot.transactions.maxOfOrNull { it.date }),
                ledger.transactionSummary()
            ),
            snapshot,
            account.id
        )
    }

    suspend fun restore(prepared: PreparedBackup, mode: RestoreMode): Int = serialized {
        if (connectedAccount().id != prepared.accountId) throw BackupAuthorizationException()
        val replace = mode == RestoreMode.REPLACE
        val notifications = context.getSystemService(NotificationManager::class.java)
        val oldReminders = if (replace) {
            notifications?.activeNotifications?.filter {
                it.notification.channelId == CaptureNotificationContract.CHANNEL_ID
            }.orEmpty()
        } else {
            emptyList()
        }
        val count = ledger.restore(prepared.snapshot, replace)
        oldReminders.forEach { notifications?.cancel(it.tag, it.id) }
        count
    }

    suspend fun runBackup(retryAllowed: Boolean): BackupRun = serialized {
        val config = state.first()
        val account = config.account ?: return@serialized BackupRun.COMPLETED
        if (!config.enabled) return@serialized BackupRun.COMPLETED

        try {
            val (salt, key) = device.secret()
            val encrypted = try {
                val payload = ledger.serialize()
                try {
                    BackupEncryption.encrypt(payload, salt, key)
                } finally {
                    payload.fill(0)
                }
            } finally {
                key.fill(0)
            }
            val installation = device.installation()
            withToken(account) { token ->
                val uploaded = drive.upload(token, installation, encrypted)
                device.record(time = uploaded.time)
                pruneOlderBackups(token, installation, uploaded)
            }
            BackupRun.COMPLETED
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            device.record(problem = failure.backupProblem())
            if (retryAllowed && failure.isRetryable()) BackupRun.RETRY else BackupRun.FAILED
        }
    }

    private fun pruneOlderBackups(token: String, installation: String, uploaded: DriveBackupFile) {
        try {
            drive.files(token, installation, limit = 100)
                .filter { it.id != uploaded.id && it.time < uploaded.time }
                .drop(2)
                .forEach { drive.delete(token, it) }
        } catch (_: Exception) {
            // The upload succeeded. Try pruning again after the next backup.
        }
    }

    private suspend fun connectedAccount(): DriveAccount =
        state.first().account ?: throw BackupAuthorizationException()

    private suspend fun <T> withToken(account: DriveAccount, action: suspend (String) -> T): T {
        suspend fun call(token: String): T {
            if (drive.account(token).id != account.id) throw BackupAuthorizationException()
            return action(token)
        }
        try {
            val token = authorization.token(authorization.request(account.email))
            try {
                return call(token)
            } catch (failure: DriveRequestException) {
                if (failure.status != 401) throw failure
                authorization.clearToken(token)
                return call(authorization.token(authorization.request(account.email)))
            }
        } catch (failure: Exception) {
            if (failure.backupProblem() == BackupProblem.REAUTHORIZE) {
                device.record(problem = BackupProblem.REAUTHORIZE)
            }
            throw failure
        }
    }

    private suspend fun <T> serialized(action: suspend () -> T): T =
        withContext(Dispatchers.IO) { mutex.withLock { action() } }

    private fun workManager(): WorkManager = WorkManager.getInstance(context)
}

internal fun Throwable.backupProblem(): BackupProblem = when {
    this is BackupAuthorizationException -> BackupProblem.REAUTHORIZE
    this is BackupKeyMissingException -> BackupProblem.LOCAL_KEY
    this is BackupTooLargeException -> BackupProblem.TOO_LARGE
    this is DriveRequestException && status == 401 -> BackupProblem.REAUTHORIZE
    this is DriveRequestException && status == 403 && !retryable -> BackupProblem.CONFIGURATION
    this is ApiException && statusCode == CommonStatusCodes.DEVELOPER_ERROR -> BackupProblem.CONFIGURATION
    this is ApiException && statusCode == CommonStatusCodes.SIGN_IN_REQUIRED -> BackupProblem.REAUTHORIZE
    else -> BackupProblem.FAILED
}

private fun Throwable.isRetryable(): Boolean = when (this) {
    is DriveRequestException -> retryable
    is IOException -> true
    is ApiException -> statusCode == CommonStatusCodes.NETWORK_ERROR ||
        statusCode == CommonStatusCodes.TIMEOUT || statusCode == CommonStatusCodes.INTERNAL_ERROR
    else -> false
}
