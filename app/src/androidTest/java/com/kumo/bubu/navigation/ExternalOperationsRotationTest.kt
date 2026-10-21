package com.kumo.bubu.navigation

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.result.ActivityResult
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.core.content.FileProvider
import com.kumo.bubu.domain.model.CloudBackup
import com.kumo.bubu.domain.model.CloudBackupAccount
import com.kumo.bubu.domain.repository.CloudBackupDownload
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kumo.bubu.BuBuApplication
import com.kumo.bubu.R
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExternalOperationsRotationTest {
    @get:Rule val rule = createAndroidComposeRule<ExternalOperationsTestActivity>()

    private var vehicleId: Long = 0
    private var vehiclePublicId: String = ""
    private val fixtureFiles = mutableListOf<File>()
    private lateinit var destination: File

    @Before fun setUp() = runBlocking<Unit> {
        ExternalOperationsTestActivity.registry.reset()
        ExternalOperationsTestActivity.cloudRepository.reset()
        ExternalOperationsTestActivity.authorization.reset()
        val application = rule.activity.application as BuBuApplication
        vehicleId = application.container.vehicleRepository.createVehicle(
            VehicleInput(
                name = "旋轉匯出測試車",
                vehicleType = VehicleType.CAR,
                motorcycleClass = null,
                trackingStartDateEpochDay = LocalDate.now().toEpochDay(),
                trackingStartOdometerKm = 0,
            ),
        )
        vehiclePublicId = requireNotNull(application.container.vehicleRepository.getVehicle(vehicleId)).publicId
        destination = File(application.cacheDir, "rotated-csv-export.zip")
        destination.delete()
    }

    @After fun tearDown() = runBlocking<Unit> {
        destination.delete()
        fixtureFiles.forEach(File::delete)
        val vehicles = (rule.activity.application as BuBuApplication).container.vehicleRepository
        vehicles.observeVehicles().first().firstOrNull { it.publicId == vehiclePublicId }?.let {
            vehicles.deleteUnreferencedVehicle(it.id)
        }
    }

    @Test fun csvDestinationResultAfterRotationExportsTheOriginalRequest() {
        rule.onNodeWithTag(TopLevelDestination.SETTINGS.route).performClick()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-csv-export"))
        rule.onNodeWithTag("settings-csv-export").performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.csv_export_choose_destination)).performClick()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.registry.launchedRequestCode != null }

        rule.activityRule.scenario.recreate()
        deliver(Uri.fromFile(destination))

        rule.waitUntil(10_000) { destination.isFile && destination.length() > 0 }
        assertTrue(destination.readBytes().copyOfRange(0, 2).contentEquals(byteArrayOf(0x50, 0x4b)))
    }

    @Test fun csvCancelAfterRotationAndLeavingRejectOldDestinationResults() {
        openCsvDestination()
        val cancelledCode = requireNotNull(ExternalOperationsTestActivity.registry.launchedRequestCode)
        rule.activityRule.scenario.recreate()
        deliver(null)
        rule.waitForIdle()
        assertTrue(!destination.exists())
        openCsvDestination()
        val abandonedCode = requireNotNull(ExternalOperationsTestActivity.registry.launchedRequestCode)
        rule.onNodeWithTag(TopLevelDestination.DASHBOARD.route).performClick()
        rule.waitForIdle()
        openCsvDestination()
        deliverTo(cancelledCode, Uri.fromFile(destination))
        deliverTo(abandonedCode, Uri.fromFile(destination))
        rule.waitForIdle()
        assertTrue(!destination.exists())
        deliver(Uri.fromFile(destination))
        rule.waitUntil(10_000) { destination.isFile && destination.length() > 0 }
    }

    private fun openCsvDestination() {
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-csv-export"))
        rule.onNodeWithTag("settings-csv-export").performClick()
        val before = ExternalOperationsTestActivity.registry.launchCount
        rule.onNodeWithText(rule.activity.getString(R.string.csv_export_choose_destination)).performClick()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.registry.launchCount == before + 1 }
    }

    @Test fun googleAuthorizationResultAfterRotationConnectsTheSelectedAccountOnce() {
        rule.onNodeWithTag(TopLevelDestination.SETTINGS.route).performClick()
        rule.onNodeWithTag("settings-scroll-list")
            .performScrollToNode(hasTestTag("settings-google-drive-connect"))
        rule.onNodeWithTag("settings-google-drive-connect").performClick()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.registry.launchedRequestCode != null }

        rule.activityRule.scenario.recreate()
        deliver(ActivityResult(Activity.RESULT_OK, Intent()))

        rule.waitUntil(10_000) {
            ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1
        }
        deliver(ActivityResult(Activity.RESULT_OK, Intent()))
        rule.waitForIdle()
        assertEquals(1, ExternalOperationsTestActivity.authorization.completionCount)
        assertEquals(1, ExternalOperationsTestActivity.cloudRepository.rememberedConnections)
    }

    @Test fun cancelledGoogleAuthorizationAfterRotationCanBeRetried() {
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        rule.activityRule.scenario.recreate()
        deliver(ActivityResult(Activity.RESULT_CANCELED, null))
        rule.waitForIdle()
        assertEquals(0, ExternalOperationsTestActivity.cloudRepository.rememberedConnections)
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        rule.onNodeWithTag("settings-google-drive-connect").assertIsEnabled()
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        deliverAuthorization()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1 }
    }

    @Test fun failedGoogleAuthorizationAfterRotationCanBeRetried() {
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        ExternalOperationsTestActivity.authorization.completionFailure = com.kumo.bubu.domain.model.CloudBackupError.NetworkUnavailable
        rule.activityRule.scenario.recreate()
        deliverAuthorization()
        rule.waitForIdle()
        assertEquals(0, ExternalOperationsTestActivity.cloudRepository.rememberedConnections)
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        rule.onNodeWithTag("settings-google-drive-connect").assertIsEnabled()
        ExternalOperationsTestActivity.authorization.completionFailure = null
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        deliverAuthorization()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1 }
        assertEquals(2, ExternalOperationsTestActivity.authorization.completionCount)
    }

    @Test fun delayedGoogleResolutionUsesTheRecreatedActivityOnce() {
        ExternalOperationsTestActivity.authorization.deferResolution = true
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        rule.onNodeWithTag("settings-google-drive-connect").performClick()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.authorization.requestCount == 1 }
        assertEquals(0, ExternalOperationsTestActivity.registry.launchCount)
        rule.activityRule.scenario.recreate()
        rule.runOnUiThread { ExternalOperationsTestActivity.authorization.releaseResolution() }
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.registry.launchCount == 1 }
        deliverAuthorization()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1 }
        assertEquals(1, ExternalOperationsTestActivity.authorization.requestCount)
        assertEquals(1, ExternalOperationsTestActivity.authorization.completionCount)
    }

    @Test fun leavingAndReturningToSettingsRejectsOldActivityResults() {
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        val oldCode = requireNotNull(ExternalOperationsTestActivity.registry.launchedRequestCode)
        rule.onNodeWithTag(TopLevelDestination.DASHBOARD.route).performClick()
        rule.waitForIdle()
        deliverTo(oldCode, ActivityResult(Activity.RESULT_OK, Intent()))
        rule.waitForIdle()
        assertEquals(0, ExternalOperationsTestActivity.cloudRepository.rememberedConnections)
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        deliverTo(oldCode, ActivityResult(Activity.RESULT_OK, Intent()))
        rule.waitForIdle()
        assertEquals(0, ExternalOperationsTestActivity.authorization.completionCount)
        deliverAuthorization()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1 }
        assertEquals(1, ExternalOperationsTestActivity.authorization.completionCount)
    }

    @Test fun abandonedDelayedResolutionCannotLaunchOverANewRequest() {
        ExternalOperationsTestActivity.authorization.deferResolution = true
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        rule.onNodeWithTag("settings-google-drive-connect").performClick()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.authorization.requestCount == 1 }
        rule.onNodeWithTag(TopLevelDestination.DASHBOARD.route).performClick()
        rule.waitForIdle()
        ExternalOperationsTestActivity.authorization.deferResolution = false
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        rule.runOnUiThread { ExternalOperationsTestActivity.authorization.releaseResolution() }
        rule.waitForIdle()
        assertEquals(1, ExternalOperationsTestActivity.registry.launchCount)
        deliverAuthorization()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1 }
    }

    @Test fun rotatedCloudPreviewCancelRemovesOnlyItsDownload() {
        val (original, download, unrelated) = prepareCloudPreview()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(rule.activity.getString(R.string.restore_cancel)).performClick()
        rule.waitUntil(10_000) { !download.exists() }
        assertTrue(original.isFile)
        assertEquals("unrelated", unrelated.readText())
        assertEquals(1, ExternalOperationsTestActivity.cloudRepository.downloadCalls)
    }

    @Test fun rotatedCloudPreviewConfirmedRestoreRemovesOnlyItsDownload() {
        val (original, download, unrelated) = prepareCloudPreview()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithText(rule.activity.getString(R.string.restore_confirm_overwrite)).performClick()
        rule.waitUntil(20_000) { !download.exists() }
        assertTrue(original.isFile)
        assertEquals("unrelated", unrelated.readText())
        assertEquals(1, ExternalOperationsTestActivity.cloudRepository.downloadCalls)
        runBlocking {
            val vehicles = (rule.activity.application as BuBuApplication).container.vehicleRepository.observeVehicles().first()
            assertTrue(vehicles.any { it.publicId == vehiclePublicId })
        }
    }

    @Test fun accountChangeClosesThePreviousBackupSelectionAndDeleteConfirmation() = runBlocking<Unit> {
        val application = rule.activity.application as BuBuApplication
        val source = File(application.cacheDir, "cloud-backup/ticket-09-selection.bubu")
        source.parentFile?.mkdirs()
        fixtureFiles += source
        val sourceUri = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", source)
        application.container.backupRepository.createBackup(sourceUri.toString())
        val now = System.currentTimeMillis()
        val backup = CloudBackup("ticket-09-a", source.name, now, now, source.length(), "1.1.1", 1, 1, 0, 0)
        ExternalOperationsTestActivity.cloudRepository.nextDownload = CloudBackupDownload(backup, source.absolutePath)
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        deliverAuthorization()
        rule.waitUntil(10_000) {
            rule.onAllNodesWithText("driver@example.com", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-restore"))
        authorize { rule.onNodeWithTag("settings-google-drive-restore").performClick() }
        deliverAuthorization()
        val deleteButton = rule.activity.getString(R.string.cloud_backup_list_delete)
        val cloudDelete = hasText(deleteButton) and hasAnyAncestor(isDialog())
        rule.waitUntil(10_000) { rule.onAllNodes(cloudDelete).fetchSemanticsNodes().size == 1 }
        rule.onNode(cloudDelete).performClick()
        val deleteTitle = rule.activity.getString(R.string.cloud_backup_delete_title)
        rule.onNodeWithText(deleteTitle).assertIsDisplayed()
        rule.onNodeWithText(rule.activity.getString(R.string.cloud_backup_delete_message, backup.fileName)).assertIsDisplayed()
        ExternalOperationsTestActivity.cloudRepository.rememberConnection(CloudBackupAccount("DRIVER@example.com", 42))
        rule.waitForIdle()
        rule.onNodeWithText(deleteTitle).assertIsDisplayed()

        ExternalOperationsTestActivity.cloudRepository.rememberConnection(CloudBackupAccount("b@example.com"))

        rule.waitUntil(10_000) { rule.onAllNodesWithText(deleteTitle).fetchSemanticsNodes().isEmpty() }
        rule.onNodeWithText(deleteTitle).assertDoesNotExist()
        rule.onNodeWithText(rule.activity.getString(R.string.cloud_backup_list_title)).assertDoesNotExist()
    }

    @Test fun productionCleanupNeverDeletesAnUnownedFile() = runBlocking<Unit> {
        val application = rule.activity.application as BuBuApplication
        val file = File(application.cacheDir, "cloud-backup/download-unowned-test.bubu")
        file.parentFile?.mkdirs()
        file.writeText("source")
        fixtureFiles += file
        val metadata = CloudBackup("unused", "unused.bubu", 1, 1, 1, "1.1.1", 1, 0, 0, 0)
        application.container.cloudBackupRepository.discardDownload(CloudBackupDownload(metadata, file.absolutePath))
        assertEquals("source", file.readText())
    }

    private fun openSettings() {
        rule.onNodeWithTag(TopLevelDestination.SETTINGS.route).performClick()
    }

    private fun authorize(click: () -> Unit) {
        val before = ExternalOperationsTestActivity.authorization.requestCount
        val launches = ExternalOperationsTestActivity.registry.launchCount
        click()
        try {
            rule.waitUntil(10_000) { ExternalOperationsTestActivity.authorization.requestCount == before + 1 && ExternalOperationsTestActivity.registry.launchCount == launches + 1 }
        } catch (error: AssertionError) {
            throw AssertionError("Authorization expected request ${before + 1}/launch ${launches + 1}; actual request ${ExternalOperationsTestActivity.authorization.requestCount}/launch ${ExternalOperationsTestActivity.registry.launchCount}", error)
        }
    }

    private fun deliver(result: Any?) {
        rule.runOnUiThread { ExternalOperationsTestActivity.registry.deliver(result) }
    }

    private fun deliverTo(requestCode: Int, result: Any?) {
        rule.runOnUiThread { ExternalOperationsTestActivity.registry.deliverTo(requestCode, result) }
    }

    private fun deliverAuthorization() {
        deliver(ActivityResult(Activity.RESULT_OK, Intent()))
    }

    private fun prepareCloudPreview(): Triple<File, File, File> {
        val application = rule.activity.application as BuBuApplication
        val original = File(application.cacheDir, "cloud-backup/rotation-source.bubu")
        val download = File(application.cacheDir, "cloud-backup/download-rotation-test.bubu")
        val unrelated = File(application.cacheDir, "cloud-backup/download-unrelated-test.bubu")
        download.parentFile?.mkdirs()
        fixtureFiles += listOf(original, download, unrelated)
        runBlocking {
            val source = FileProvider.getUriForFile(application, "${application.packageName}.fileprovider", original)
            application.container.backupRepository.createBackup(source.toString())
        }
        original.copyTo(download, overwrite = true)
        unrelated.writeText("unrelated")
        val metadata = CloudBackup("rotation", "rotation.bubu", 1, 1, download.length(), "1.1.1", 1, 1, 0, 0)
        ExternalOperationsTestActivity.cloudRepository.nextDownload = CloudBackupDownload(metadata, download.absolutePath)
        openSettings()
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-connect"))
        authorize { rule.onNodeWithTag("settings-google-drive-connect").performClick() }
        deliverAuthorization()
        rule.waitUntil(10_000) { ExternalOperationsTestActivity.cloudRepository.rememberedConnections == 1 }
        rule.onNodeWithTag("settings-scroll-list").performScrollToNode(hasTestTag("settings-google-drive-restore"))
        authorize { rule.onNodeWithTag("settings-google-drive-restore").performClick() }
        deliverAuthorization()
        rule.waitForIdle()
        authorize { rule.onNodeWithText(rule.activity.getString(R.string.cloud_backup_list_select)).performClick() }
        deliverAuthorization()
        val title = rule.activity.getString(R.string.restore_preview_title)
        rule.waitUntil(10_000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.waitForIdle()
        assertTrue(download.isFile)
        return Triple(original, download, unrelated)
    }
}
