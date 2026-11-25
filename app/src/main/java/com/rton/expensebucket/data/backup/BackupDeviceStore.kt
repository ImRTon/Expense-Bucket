package com.rton.expensebucket.data.backup

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.File
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

internal enum class BackupProblem { REAUTHORIZE, CONFIGURATION, TOO_LARGE, LOCAL_KEY, FAILED }

internal data class BackupDeviceState(
    val account: DriveAccount? = null,
    val enabled: Boolean = false,
    val lastBackup: Long? = null,
    val problem: BackupProblem? = null
)

internal class BackupKeyMissingException : Exception()

@Singleton
internal class BackupDeviceStore @Inject constructor(@ApplicationContext context: Context) {
    private val dataStore = PreferenceDataStoreFactory.create {
        File(context.noBackupFilesDir, "drive_backup.preferences_pb")
    }
    val state = dataStore.data.map(::readState)

    suspend fun connect(account: DriveAccount) {
        dataStore.edit { prefs ->
            if (prefs[ACCOUNT_ID] != account.id) {
                prefs.remove(LOCAL_KEY)
                prefs.remove(SALT)
                prefs.remove(LAST_BACKUP)
            }
            prefs[ACCOUNT_ID] = account.id
            prefs[ACCOUNT_EMAIL] = account.email
            prefs.remove(PROBLEM)
        }
    }

    suspend fun disconnect() {
        dataStore.edit { prefs ->
            val installation = prefs[INSTALLATION]
            prefs.clear()
            installation?.let { prefs[INSTALLATION] = it }
        }
    }

    suspend fun enable(salt: ByteArray, key: ByteArray) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deviceKey(create = true))
        dataStore.edit { prefs ->
            prefs[SALT] = Base64.encodeToString(salt, Base64.NO_WRAP)
            prefs[LOCAL_KEY] = Base64.encodeToString(cipher.iv + cipher.doFinal(key), Base64.NO_WRAP)
            if (prefs[INSTALLATION] == null) prefs[INSTALLATION] = UUID.randomUUID().toString()
            prefs.remove(PROBLEM)
        }
    }

    suspend fun disable() {
        dataStore.edit { prefs ->
            prefs.remove(LOCAL_KEY)
            prefs.remove(SALT)
            prefs.remove(PROBLEM)
        }
    }

    suspend fun secret(): Pair<ByteArray, ByteArray> {
        val prefs = dataStore.data.first()
        val wrapped = prefs[LOCAL_KEY] ?: throw BackupKeyMissingException()
        val salt = prefs[SALT] ?: throw BackupKeyMissingException()
        return try {
            val bytes = Base64.decode(wrapped, Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, deviceKey(create = false), GCMParameterSpec(128, bytes, 0, 12))
            Base64.decode(salt, Base64.NO_WRAP) to cipher.doFinal(bytes, 12, bytes.size - 12)
        } catch (failure: Exception) {
            throw BackupKeyMissingException().also { it.initCause(failure) }
        }
    }

    suspend fun installation(): String = checkNotNull(dataStore.data.first()[INSTALLATION])

    suspend fun record(time: Long? = null, problem: BackupProblem? = null) {
        dataStore.edit { prefs ->
            time?.let { prefs[LAST_BACKUP] = it }
            if (problem == null) prefs.remove(PROBLEM) else prefs[PROBLEM] = problem.name
        }
    }

    private fun readState(prefs: Preferences): BackupDeviceState {
        val id = prefs[ACCOUNT_ID]
        val email = prefs[ACCOUNT_EMAIL]
        return BackupDeviceState(
            account = if (id != null && email != null) DriveAccount(id, email) else null,
            enabled = prefs[LOCAL_KEY] != null,
            lastBackup = prefs[LAST_BACKUP],
            problem = BackupProblem.entries.firstOrNull { it.name == prefs[PROBLEM] }
        )
    }

    private fun deviceKey(create: Boolean): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        if (!create) throw BackupKeyMissingException()
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            generateKey()
        }
    }

    private companion object {
        const val KEY_ALIAS = "expense_bucket_drive_backup"
        val ACCOUNT_ID = stringPreferencesKey("account_id")
        val ACCOUNT_EMAIL = stringPreferencesKey("account_email")
        val INSTALLATION = stringPreferencesKey("installation")
        val LOCAL_KEY = stringPreferencesKey("wrapped_key")
        val SALT = stringPreferencesKey("salt")
        val LAST_BACKUP = longPreferencesKey("last_backup")
        val PROBLEM = stringPreferencesKey("problem")
    }
}
