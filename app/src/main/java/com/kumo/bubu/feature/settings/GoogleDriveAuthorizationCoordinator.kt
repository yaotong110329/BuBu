package com.kumo.bubu.feature.settings

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.common.Scopes
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.kumo.bubu.BuildConfig
import com.kumo.bubu.domain.model.CloudBackupError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

data class GoogleDriveAccount(val email: String)

class GoogleDriveAuthorizationCoordinator(context: Context) {
    private val applicationContext = context.applicationContext
    private val credentialManager = CredentialManager.create(applicationContext)
    private val authorizationClient = Identity.getAuthorizationClient(applicationContext)

    fun authorize(
        activity: Activity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        scope: CoroutineScope,
        onAccountSelected: (GoogleDriveAccount) -> Unit,
        onAuthorized: (GoogleDriveAccount, String) -> Unit,
        onError: (CloudBackupError) -> Unit,
    ) {
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank()) {
            onError(CloudBackupError.ConfigurationMissing)
            return
        }
        scope.launch {
            val account = selectAccount(activity) ?: run {
                onError(CloudBackupError.NotConnected)
                return@launch
            }
            onAccountSelected(account)
            val request = AuthorizationRequest.builder()
                .setAccount(Account(account.email, GOOGLE_ACCOUNT_TYPE))
                .setRequestedScopes(DRIVE_SCOPES)
                .build()
            authorizationClient.authorize(request)
                .addOnSuccessListener { result ->
                    if (result.hasResolution()) {
                        launcher.launch(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender).build())
                    } else {
                        Log.d(LOG_TAG, "Google Drive granted scopes: ${result.grantedScopes}")
                        result.accessToken?.let { token -> onAuthorized(account, token) }
                            ?: onError(CloudBackupError.AuthorizationExpired)
                    }
                }
                .addOnFailureListener { onError(CloudBackupError.AuthorizationExpired) }
        }
    }

    fun completeAuthorization(
        data: Intent?,
        account: GoogleDriveAccount,
        onAuthorized: (GoogleDriveAccount, String) -> Unit,
        onError: (CloudBackupError) -> Unit,
    ) {
        runCatching { authorizationClient.getAuthorizationResultFromIntent(data) }
            .onSuccess { result ->
                Log.d(LOG_TAG, "Google Drive granted scopes: ${result.grantedScopes}")
                result.accessToken?.let { onAuthorized(account, it) } ?: onError(CloudBackupError.AuthorizationExpired)
            }
            .onFailure { onError(CloudBackupError.AuthorizationExpired) }
    }

    fun revoke(accountEmail: String, onComplete: () -> Unit, onError: (CloudBackupError) -> Unit) {
        val request = com.google.android.gms.auth.api.identity.RevokeAccessRequest.builder()
            .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
            .setScopes(DRIVE_SCOPES)
            .build()
        authorizationClient.revokeAccess(request)
            .addOnSuccessListener { onComplete() }
            .addOnFailureListener { onError(CloudBackupError.NetworkUnavailable) }
    }

    private suspend fun selectAccount(activity: Activity): GoogleDriveAccount? = try {
        val option = GetGoogleIdOption.Builder()
            .setServerClientId(BuildConfig.GOOGLE_WEB_CLIENT_ID)
            .setFilterByAuthorizedAccounts(false)
            .build()
        val result = credentialManager.getCredential(
            context = activity,
            request = GetCredentialRequest.Builder().addCredentialOption(option).build(),
        )
        val credential = result.credential as? CustomCredential
            ?: return null
        if (credential.type != GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) return null
        GoogleDriveAccount(GoogleIdTokenCredential.createFrom(credential.data).id)
    } catch (_: Exception) {
        null
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        const val LOG_TAG = "BuBuDriveAuth"
        val DRIVE_SCOPES = listOf(
            Scope(Scopes.DRIVE_APPFOLDER),
            Scope(Scopes.DRIVE_FILE),
        )
    }
}
