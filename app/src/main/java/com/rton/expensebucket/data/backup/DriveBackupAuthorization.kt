package com.rton.expensebucket.data.backup

import android.accounts.Account
import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.ClearTokenRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Task
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class DriveBackupAuthorization @Inject constructor(@ApplicationContext context: Context) {
    private val client = Identity.getAuthorizationClient(context)

    suspend fun request(email: String? = null): AuthorizationResult {
        val request = AuthorizationRequest.builder().setRequestedScopes(listOf(Scope(DRIVE_SCOPE)))
        if (email != null) {
            request.setAccount(Account(email, "com.google"))
        } else {
            request.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }
        return client.authorize(request.build()).awaitResult()
    }

    fun complete(intent: Intent): AuthorizationResult = client.getAuthorizationResultFromIntent(intent)

    fun token(result: AuthorizationResult): String {
        if (result.hasResolution() || DRIVE_SCOPE !in result.grantedScopes) throw BackupAuthorizationException()
        return result.accessToken ?: throw BackupAuthorizationException()
    }

    suspend fun clearToken(token: String) {
        client.clearToken(ClearTokenRequest.builder().setToken(token).build()).awaitResult()
    }

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnCompleteListener(Runnable::run) { task ->
            when {
                task.isCanceled -> continuation.cancel()
                task.isSuccessful -> continuation.resume(task.result)
                else -> continuation.resumeWithException(checkNotNull(task.exception))
            }
        }
    }

    private companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}

internal class BackupAuthorizationException : Exception()
internal class BackupConsentIncompleteException : Exception()
