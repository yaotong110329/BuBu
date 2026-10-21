package com.kumo.bubu.navigation

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.CompositionLocalProvider
import androidx.core.app.ActivityOptionsCompat
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.model.CloudBackupConnection
import com.kumo.bubu.domain.model.CloudBackupError
import com.kumo.bubu.domain.repository.CloudBackupDownload
import com.kumo.bubu.domain.repository.CloudBackupRepository
import com.kumo.bubu.feature.settings.GoogleDriveAccount
import com.kumo.bubu.feature.settings.GoogleDriveAuthorizationGateway
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class ExternalOperationsTestActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalActivityResultRegistryOwner provides REGISTRY_OWNER) {
                BuBuTheme {
                    BuBuNavHost(
                        cloudBackupRepositoryOverride = cloudRepository,
                        googleAuthorizationFactory = { authorization },
                    )
                }
            }
        }
    }

    companion object {
        val registry = RecordingActivityResultRegistry()
        val cloudRepository = RecordingCloudBackupRepository()
        val authorization = RecordingGoogleAuthorization()
        private val REGISTRY_OWNER = object : ActivityResultRegistryOwner {
            override val activityResultRegistry: ActivityResultRegistry = registry
        }
    }
}

class RecordingCloudBackupRepository : CloudBackupRepository {
    private val connection = MutableStateFlow<CloudBackupConnection>(CloudBackupConnection.NotConnected)
    var nextDownload: CloudBackupDownload? = null
    var downloadCalls = 0
    var rememberedConnections = 0
        private set

    fun reset() {
        connection.value = CloudBackupConnection.NotConnected
        rememberedConnections = 0
        nextDownload = null
        downloadCalls = 0
    }

    override fun observeConnection() = connection.asStateFlow()
    override suspend fun rememberConnection(account: CloudBackupAccount) {
        rememberedConnections++
        connection.value = CloudBackupConnection.Connected(account)
    }
    override suspend fun clearConnection() { connection.value = CloudBackupConnection.NotConnected }
    override suspend fun markNeedsAuthorization() { connection.value = CloudBackupConnection.NeedsAuthorization }
    override suspend fun uploadBackup(accessToken: String): CloudBackup = error("Not used by this test")
    override suspend fun listBackups(accessToken: String): List<CloudBackup> = nextDownload?.let { listOf(it.backup) } ?: emptyList()
    override suspend fun downloadBackup(accessToken: String, backupId: String): CloudBackupDownload {
        downloadCalls++
        return requireNotNull(nextDownload)
    }
    override fun discardDownload(download: CloudBackupDownload) { File(download.localFilePath).delete() }
    override suspend fun deleteBackup(accessToken: String, backupId: String) = Unit
}

class RecordingGoogleAuthorization : GoogleDriveAuthorizationGateway {
    @Volatile var requestCount = 0
        private set
    var completionCount = 0
        private set

    var completionFailure: CloudBackupError? = null
    var deferResolution = false
    private val pendingResolutions = ArrayDeque<() -> Unit>()

    fun releaseResolution() { pendingResolutions.removeFirst().invoke() }

    fun reset() { completionCount = 0; requestCount = 0; completionFailure = null; deferResolution = false; pendingResolutions.clear() }

    override fun authorize(
        activity: Activity,
        onResolution: (IntentSenderRequest) -> Unit,
        scope: CoroutineScope,
        onAccountSelected: (GoogleDriveAccount) -> Unit,
        onAuthorized: (GoogleDriveAccount, String) -> Unit,
        onError: (CloudBackupError) -> Unit,
    ) {
        requestCount++
        onAccountSelected(GoogleDriveAccount("driver@example.com"))
        val pendingIntent = PendingIntent.getActivity(
            activity, 0, Intent(activity, ExternalOperationsTestActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val resolve = { onResolution(IntentSenderRequest.Builder(pendingIntent.intentSender).build()) }
        if (deferResolution) pendingResolutions.addLast(resolve) else resolve()
    }

    override fun completeAuthorization(
        data: Intent?,
        account: GoogleDriveAccount,
        onAuthorized: (GoogleDriveAccount, String) -> Unit,
        onError: (CloudBackupError) -> Unit,
    ) {
        completionCount++
        completionFailure?.let { onError(it); return }
        onAuthorized(account, "test-token")
    }

    override fun revoke(accountEmail: String, onComplete: () -> Unit, onError: (CloudBackupError) -> Unit) {
        onComplete()
    }
}

class RecordingActivityResultRegistry : ActivityResultRegistry() {
    @Volatile var launchCount = 0
        private set
    @Volatile var launchedRequestCode: Int? = null
        private set

    override fun <I, O> onLaunch(
        requestCode: Int,
        contract: ActivityResultContract<I, O>,
        input: I,
        options: ActivityOptionsCompat?,
    ) {
        launchCount++
        launchedRequestCode = requestCode
    }

    fun deliver(result: Any?) {
        dispatchResult(requireNotNull(launchedRequestCode), result)
    }

    fun deliverTo(requestCode: Int, result: Any?) { dispatchResult(requestCode, result) }

    fun reset() {
        launchCount = 0
        launchedRequestCode = null
    }
}
