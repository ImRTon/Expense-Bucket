package com.rton.expensebucket.data.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

internal class CloudBackupWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repository = EntryPointAccessors.fromApplication(
            applicationContext, BackupWorkerDependencies::class.java
        ).backupRepository()
        return when (repository.runBackup(retryAllowed = runAttemptCount < 3)) {
            BackupRun.COMPLETED -> Result.success()
            BackupRun.RETRY -> Result.retry()
            BackupRun.FAILED -> Result.failure()
        }
    }

    companion object {
        private const val PERIODIC_WORK = "drive-backup-periodic"
        private const val IMMEDIATE_WORK = "drive-backup-immediate"
        const val BACKUP_TAG = "drive-backup"
        const val IMMEDIATE_TAG = "drive-backup-requested"

        private val connected = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        fun schedule(manager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(24, TimeUnit.HOURS)
                .setConstraints(connected)
                .addTag(BACKUP_TAG)
                .build()
            manager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun runSoon(manager: WorkManager) {
            val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
                .setConstraints(connected)
                .addTag(BACKUP_TAG)
                .addTag(IMMEDIATE_TAG)
                .build()
            manager.enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.KEEP, request)
        }

        fun cancel(manager: WorkManager) {
            manager.cancelUniqueWork(PERIODIC_WORK)
            manager.cancelUniqueWork(IMMEDIATE_WORK)
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
internal interface BackupWorkerDependencies {
    fun backupRepository(): CloudBackupRepository
}
