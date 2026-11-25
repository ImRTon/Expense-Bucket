package com.rton.expensebucket.data.backup

import androidx.room.withTransaction
import com.google.gson.Gson
import com.rton.expensebucket.data.AppPalette
import com.rton.expensebucket.data.AppSettingsDataStore
import com.rton.expensebucket.data.AppTheme
import com.rton.expensebucket.data.BudgetDataStore
import com.rton.expensebucket.data.ExpenseBucketDatabase
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class LedgerBackupStore @Inject constructor(
    private val database: ExpenseBucketDatabase,
    private val settings: AppSettingsDataStore,
    private val budgets: BudgetDataStore
) {
    private val dao get() = database.cloudBackupDao()
    private val gson = Gson()

    suspend fun serialize(): ByteArray = gson.toJson(
        database.withTransaction { snapshot() }
    ).toByteArray(Charsets.UTF_8).also {
        if (it.size > BackupEncryption.MAX_PAYLOAD_BYTES) throw BackupTooLargeException()
    }

    fun deserialize(payload: ByteArray): LedgerSnapshot {
        val snapshot = checkNotNull(gson.fromJson(payload.toString(Charsets.UTF_8), LedgerSnapshot::class.java))
        if (snapshot.formatVersion > LedgerSnapshot.FORMAT_VERSION) throw BackupVersionException()
        check(snapshot.formatVersion == LedgerSnapshot.FORMAT_VERSION)
        validateIds(snapshot.categories.map { it.id })
        validateIds(snapshot.paymentMethods.map { it.id })
        validateIds(snapshot.projects.map { it.id })
        validateIds(snapshot.transactions.map { it.id })
        val transactionIds = snapshot.transactions.mapTo(mutableSetOf()) { it.id }
        check(snapshot.notificationCaptures.all { it.transactionId in transactionIds })
        snapshot.preferences.validate()
        return snapshot
    }

    suspend fun transactionSummary() = dao.transactionSummary()

    suspend fun restore(remote: LedgerSnapshot, replace: Boolean): Int {
        var previousPreferences: BackupPreferences? = null
        return withContext(NonCancellable) {
            try {
                database.withTransaction {
                    val rows = if (replace) {
                        dao.clearTransactions()
                        dao.clearCategories()
                        dao.clearPaymentMethods()
                        dao.clearProjects()
                        remote
                    } else {
                        LedgerMerge.additions(snapshot(), remote, readSequences())
                    }
                    dao.insertCategories(rows.categories)
                    dao.insertPaymentMethods(rows.paymentMethods)
                    dao.insertProjects(rows.projects)
                    dao.insertTransactions(rows.transactions)
                    dao.insertNotificationCaptures(rows.notificationCaptures)
                    if (replace) {
                        previousPreferences = readPreferences()
                        writePreferences(remote.preferences)
                    }
                    rows.transactions.size
                }
            } catch (failure: Exception) {
                try {
                    previousPreferences?.let { writePreferences(it) }
                } catch (rollbackFailure: Exception) {
                    failure.addSuppressed(rollbackFailure)
                }
                throw failure
            }
        }
    }

    private suspend fun snapshot() = LedgerSnapshot(
        formatVersion = LedgerSnapshot.FORMAT_VERSION,
        categories = dao.categories(),
        paymentMethods = dao.paymentMethods(),
        projects = dao.projects(),
        transactions = dao.transactions(),
        notificationCaptures = dao.notificationCaptures(),
        preferences = readPreferences()
    )

    private suspend fun readPreferences() = BackupPreferences(
        theme = settings.theme.first().name,
        palette = settings.palette.first().name,
        compareMode = settings.compareMode.first(),
        firstDayOfWeek = settings.firstDayOfWeek.first(),
        monthlyBudget = budgets.monthlyBudget.first()
    )

    private fun readSequences(): BackupSequences {
        val sequences = database.openHelper.writableDatabase.query("SELECT name, seq FROM sqlite_sequence")
            .use { cursor ->
                buildMap {
                    while (cursor.moveToNext()) put(cursor.getString(0), cursor.getLong(1))
                }
            }
        return BackupSequences(
            categories = sequences["categories"] ?: 0,
            methods = sequences["payment_methods"] ?: 0,
            projects = sequences["projects"] ?: 0,
            transactions = sequences["transactions"] ?: 0
        )
    }

    private fun BackupPreferences.validate() {
        AppTheme.valueOf(theme)
        AppPalette.valueOf(palette)
        check(compareMode.isNotBlank())
        check(firstDayOfWeek in 1..7)
        check(monthlyBudget.isFinite())
    }

    private suspend fun writePreferences(preferences: BackupPreferences) {
        settings.restoreBackupSettings(
            AppTheme.valueOf(preferences.theme),
            AppPalette.valueOf(preferences.palette),
            preferences.compareMode,
            preferences.firstDayOfWeek
        )
        budgets.setMonthlyBudget(preferences.monthlyBudget)
    }

    private fun validateIds(ids: List<Long>) {
        check(ids.all { it > 0 && it < Long.MAX_VALUE } && ids.size == ids.toSet().size)
    }
}

internal class BackupTooLargeException : Exception()
internal class BackupVersionException : Exception()
