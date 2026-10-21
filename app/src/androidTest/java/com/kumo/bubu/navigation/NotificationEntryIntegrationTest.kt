package com.kumo.bubu.navigation

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.kumo.bubu.BuBuApplication
import com.kumo.bubu.MainActivity
import com.kumo.bubu.R
import com.kumo.bubu.core.notification.ReminderNotificationWorker
import com.kumo.bubu.core.notification.ReminderNotifications
import com.kumo.bubu.data.local.entity.ServiceTypeEntity
import com.kumo.bubu.data.local.entity.VehicleServiceReminderPreferenceEntity
import com.kumo.bubu.domain.model.ManualReminderInput
import com.kumo.bubu.domain.model.ServiceReminderPreferenceInput
import com.kumo.bubu.domain.model.VehicleInput
import com.kumo.bubu.domain.model.VehicleType
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class, ExperimentalTestApi::class)
class NotificationEntryIntegrationTest {
    @get:Rule val rule = createEmptyComposeRule(UnconfinedTestDispatcher())
    private lateinit var application: BuBuApplication
    private lateinit var manager: NotificationManager
    private lateinit var workManager: WorkManager
    private var scenario: ActivityScenario<MainActivity>? = null
    private val vehicleIds = mutableListOf<Long>()
    private val typeIds = mutableListOf<Long>()
    private val workIds = mutableListOf<UUID>()
    private var previousNotificationsEnabled = false
    private var previousNotificationIds = emptySet<Int>()
    private var targetReminderId = 0L
    private var secondVehicleId = 0L

    @Before fun setUp() = runBlocking<Unit> {
        application = ApplicationProvider.getApplicationContext()
        val container = application.container
        manager = application.getSystemService(NotificationManager::class.java)
        workManager = WorkManager.getInstance(application)
        previousNotificationIds = manager.activeNotifications.map { it.id }.toSet()
        previousNotificationsEnabled = container.reminderNotificationSettings.observeEnabled().first()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                application.packageName, Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        container.reminderNotificationSettings.setEnabled(true)
        val today = LocalDate.now()
        listOf("000 T08 A", "001 T08 B").forEach { name ->
            vehicleIds += container.vehicleRepository.createVehicle(VehicleInput(
                name = name, vehicleType = VehicleType.CAR, motorcycleClass = null,
                trackingStartDateEpochDay = today.toEpochDay(), trackingStartOdometerKm = 10_000,
            ))
        }
        secondVehicleId = vehicleIds[1]
        vehicleIds.forEach { vehicleId ->
            val id = container.reminderRepository.createManualReminder(ManualReminderInput(
                vehicleId = vehicleId, title = "通知定位 $vehicleId", dueOdometerKm = 10_000,
                dueDateEpochDay = today.toEpochDay(),
            ))
            if (vehicleId == secondVehicleId) targetReminderId = id
        }
        repeat(3) { index ->
            val typeId = container.database.serviceTypeDao().insert(ServiceTypeEntity(
                publicId = UUID.randomUUID().toString(), name = "通知測試保養 ${UUID.randomUUID()}",
                isBuiltIn = false, isArchived = false, sortOrder = index, createdAt = 1, updatedAt = 1,
            ))
            typeIds += typeId
            container.database.vehicleServiceReminderPreferenceDao().insert(VehicleServiceReminderPreferenceEntity(
                publicId = UUID.randomUUID().toString(), vehicleId = secondVehicleId, serviceTypeId = typeId,
                intervalKm = 10_000, intervalMonths = 6, sortOrder = index, createdAt = 1, updatedAt = 1,
            ))
        }
    }

    @After fun tearDown() = runBlocking<Unit> {
        scenario?.close()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val monitor = ActivityLifecycleMonitorRegistry.getInstance()
            Stage.values().filter { it != Stage.DESTROYED }
                .flatMap { monitor.getActivitiesInStage(it) }.filterIsInstance<MainActivity>()
                .distinct().forEach { it.finish() }
        }
        workIds.forEach { workManager.cancelWorkById(it).result.get() }
        val container = application.container
        container.reminderRepository.observeReminders().first().filter { it.vehicleId in vehicleIds }
            .forEach { container.database.vehicleReminderDao().deleteById(it.id) }
        container.serviceRepository.observeAllServiceReminderPreferences().first().filter { it.vehicleId in vehicleIds }
            .forEach { container.database.vehicleServiceReminderPreferenceDao().delete(it.vehicleId, it.serviceTypeId) }
        typeIds.forEach { container.database.serviceTypeDao().deleteById(it) }
        vehicleIds.forEach { container.vehicleRepository.deleteUnreferencedVehicle(it) }
        container.reminderNotificationSettings.setEnabled(previousNotificationsEnabled)
        manager.activeNotifications.filter { it.id !in previousNotificationIds }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    @Test fun postedNotificationSelectsTheSecondVehicleWhileTheAppIsAlreadyOpen() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route), 10_000)
        postedTarget().send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-scroll-list"), 10_000)
        // Inspect the selector through public scrolling; target positioning is a separate slice.
        scrollToVehicle(secondVehicleId)
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-vehicle-$secondVehicleId") and isSelected(), 5_000)
        rule.onNodeWithTag("reminders-vehicle-$secondVehicleId").assertIsSelected()
    }

    @Test fun postedNotificationScrollsToItsTargetAfterMultipleMaintenanceSections() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route), 10_000)
        postedTarget().send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-scroll-list"), 10_000)
        // Do not scroll the test: the notification must expose its target itself.
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId"), 5_000)
        rule.onNodeWithTag("reminder-$targetReminderId").assertIsDisplayed()
    }

    @Test fun postedNotificationMarksItsTargetReminder() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route), 10_000)
        postedTarget().send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId"), 10_000)
        rule.onNodeWithTag("reminder-$targetReminderId").assertIsSelected()
    }

    @Test fun theSameNotificationCanReopenAfterBackWithoutDuplicatingTheBackStack() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        val notification = postedTarget()
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId") and isSelected(), 10_000)
        rule.onNodeWithContentDescription(application.getString(R.string.back)).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId") and isSelected(), 10_000)
        notification.send()
        rule.waitForIdle()
        rule.onNodeWithContentDescription(application.getString(R.string.back)).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
    }

    @Test fun maintenanceSummaryOpensTheReminderTabAndAllowsCreatingAServiceRecord() = runBlocking<Unit> {
        enableDueMaintenance()
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        postedTarget() // The real worker posts both ordinary reminders and the due-maintenance summary.
        val summary = postedSummary()
        summary.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        listOf(
            TopLevelDestination.DASHBOARD, TopLevelDestination.REMINDERS,
            TopLevelDestination.REPORTS, TopLevelDestination.SETTINGS,
        ).forEach { rule.onNodeWithTag(it.route).assertIsDisplayed() }
        openTargetMaintenanceForm()
        rule.onNodeWithText(application.getString(R.string.add_service_title)).assertIsDisplayed()
    }

    @Test fun aNotificationStartsANewActivityAtItsTargetAndSurvivesRecreation() = runBlocking<Unit> {
        postedTarget().send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId") and isSelected(), 10_000)
        rule.onNodeWithTag("reminder-$targetReminderId").assertIsDisplayed()
        var originalActivity: MainActivity? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            originalActivity = ActivityLifecycleMonitorRegistry.getInstance()
                .getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().single()
            requireNotNull(originalActivity).recreate()
        }
        rule.waitUntil(10_000) {
            var recreated = false
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                recreated = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().any { it !== originalActivity }
            }
            recreated
        }
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId") and isSelected(), 10_000)
        rule.onNodeWithTag("reminder-$targetReminderId").assertIsDisplayed()
        rule.onNodeWithContentDescription(application.getString(R.string.back)).performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
    }

    @Test fun aDeletedNotificationTargetFallsBackToAnActiveVehicleWithoutMarkingAnotherReminder() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        val notification = postedTarget()
        application.container.database.vehicleReminderDao().deleteById(targetReminderId)
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-vehicle-${vehicleIds.first()}") and isSelected(), 10_000)
        rule.onNodeWithTag("reminder-$targetReminderId").assertDoesNotExist()
        val remainingReminderId = application.container.reminderRepository.observeReminders().first()
            .first { it.vehicleId == vehicleIds.first() && it.source == com.kumo.bubu.domain.model.ReminderSource.MANUAL }.id
        rule.onNodeWithTag("reminders-scroll-list").performScrollToNode(hasTestTag("reminder-$remainingReminderId"))
        rule.onNodeWithTag("reminder-$remainingReminderId").assertIsNotSelected()
    }

    @Test fun anArchivedVehicleNotificationFallsBackToAnActiveVehicle() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        val notification = postedTarget()
        application.container.vehicleRepository.setVehicleArchived(secondVehicleId, true)
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-vehicle-${vehicleIds.first()}") and isSelected(), 10_000)
        rule.onNodeWithTag("reminders-vehicle-$secondVehicleId").assertDoesNotExist()
        rule.onNodeWithTag("reminder-$targetReminderId").assertDoesNotExist()
        rule.onNodeWithTag("reminders-scroll-list").assertIsDisplayed()
    }

    @Test fun resendingTheNotificationRefocusesItsVehicleAfterManualSelection() = runBlocking<Unit> {
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        val notification = postedTarget()
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId") and isSelected(), 10_000)
        scrollToVehicle(vehicleIds.first())
        rule.onNodeWithTag("reminders-vehicle-${vehicleIds.first()}").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-vehicle-${vehicleIds.first()}") and isSelected(), 10_000)
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminder-$targetReminderId") and isSelected(), 10_000)
        rule.onNodeWithTag("reminder-$targetReminderId").assertIsDisplayed()
    }

    @Test fun resendingTheSummaryFromItsServiceFormDoesNotDuplicateTheReminderEntry() = runBlocking<Unit> {
        enableDueMaintenance()
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        postedTarget()
        val notification = postedSummary()
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        openTargetMaintenanceForm()
        rule.waitUntilDoesNotExist(hasTestTag(TopLevelDestination.REMINDERS.route), 10_000)
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        rule.waitForIdle()
        requireNotNull(scenario).onActivity { it.onBackPressedDispatcher.onBackPressed() }
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
    }

    @Test fun summaryReplayPreservesTheInterruptedServiceItemDraft() = runBlocking<Unit> {
        enableDueMaintenance()
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        postedTarget()
        val notification = postedSummary()
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        openTargetMaintenanceForm()
        rule.waitUntilAtLeastOneExists(hasText(application.getString(R.string.service_item_note)), 10_000)
        rule.onNodeWithText(application.getString(R.string.service_item_note))
            .performTextReplacement("T08 interrupted item draft")
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        openTargetMaintenanceForm()
        rule.waitUntilAtLeastOneExists(hasText("T08 interrupted item draft"), 10_000)
        rule.onNodeWithText("T08 interrupted item draft").assertIsDisplayed()
    }

    @Test fun summaryReplayDoesNotRestoreADraftForAnotherMaintenanceItem() = runBlocking<Unit> {
        enableDueMaintenance()
        scenario = ActivityScenario.launch(Intent(application, MainActivity::class.java))
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.DASHBOARD.route) and isSelected(), 10_000)
        postedTarget()
        val notification = postedSummary()
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        openTargetMaintenanceForm()
        rule.waitUntilAtLeastOneExists(hasText(application.getString(R.string.service_item_note)), 10_000)
        rule.onNodeWithText(application.getString(R.string.service_item_note))
            .performTextReplacement("T08 draft for item X")
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        openTargetMaintenanceForm(typeIds[1])
        val requestedTypeName = application.container.serviceRepository.observeServiceTypes().first()
            .single { it.id == typeIds[1] }.name
        rule.waitUntilAtLeastOneExists(hasText(requestedTypeName), 10_000)
        rule.onNodeWithText(requestedTypeName).assertIsDisplayed()
        rule.onNodeWithText("T08 draft for item X").assertDoesNotExist()
        rule.onNodeWithText(application.getString(R.string.service_item_note))
            .performTextReplacement("T08 draft for item Y")
        notification.send()
        rule.waitUntilAtLeastOneExists(hasTestTag(TopLevelDestination.REMINDERS.route) and isSelected(), 10_000)
        openTargetMaintenanceForm(typeIds[1])
        rule.waitUntilAtLeastOneExists(hasText("T08 draft for item Y"), 10_000)
        rule.onNodeWithText("T08 draft for item Y").assertIsDisplayed()
        rule.onNodeWithText("T08 draft for item X").assertDoesNotExist()
    }

    private suspend fun enableDueMaintenance() {
        application.container.serviceRepository.saveServiceReminderPreference(ServiceReminderPreferenceInput(
            vehicleId = secondVehicleId, serviceTypeId = typeIds.first(), isEnabled = true,
            intervalKm = 10_000, baseOdometerKm = 0, sortOrder = 0,
        ))
    }

    private fun scrollToVehicle(vehicleId: Long) {
        rule.waitForIdle()
        rule.onNodeWithTag("reminders-scroll-list")
            .performScrollToNode(hasTestTag("reminders-notifications-switch"))
        rule.onNodeWithTag("reminders-vehicle-$vehicleId").performScrollTo().assertIsDisplayed()
    }

    private fun openTargetMaintenanceForm(serviceTypeId: Long = typeIds.first()) {
        scrollToVehicle(secondVehicleId)
        rule.onNodeWithTag("reminders-vehicle-$secondVehicleId").performClick()
        rule.waitUntilAtLeastOneExists(hasTestTag("reminders-vehicle-$secondVehicleId") and isSelected(), 10_000)
        val maintenanceTag = "maintenance-reminder-$secondVehicleId-$serviceTypeId"
        rule.onNodeWithTag("reminders-scroll-list").performScrollToNode(hasTestTag(maintenanceTag))
        rule.onNodeWithTag(maintenanceTag).performSemanticsAction(SemanticsActions.OnClick) { it() }
        rule.waitUntilAtLeastOneExists(hasText(application.getString(R.string.maintenance_reminder_add_record)), 10_000)
        rule.onNodeWithText(application.getString(R.string.maintenance_reminder_add_record)).performClick()
        rule.waitUntilAtLeastOneExists(hasText(application.getString(R.string.add_service_title)), 10_000)
    }

    private suspend fun postedSummary(): PendingIntent {
        var result: PendingIntent? = null
        withTimeout(10_000) {
            while (result == null) {
                result = manager.activeNotifications.firstOrNull {
                    it.id == ReminderNotifications.MAINTENANCE_NOTIFICATION_ID &&
                        it.notification.channelId == ReminderNotifications.CHANNEL_ID
                }?.notification?.contentIntent
                if (result == null) delay(100)
            }
        }
        return requireNotNull(result)
    }

    private suspend fun postedTarget(): PendingIntent {
        val request = OneTimeWorkRequestBuilder<ReminderNotificationWorker>().build()
        workIds += request.id
        workManager.enqueue(request).result.get()
        withTimeout(30_000) {
            while (workManager.getWorkInfoById(request.id).get()?.state?.isFinished != true) delay(100)
        }
        assertEquals(WorkInfo.State.SUCCEEDED, requireNotNull(workManager.getWorkInfoById(request.id).get()).state)
        var result: PendingIntent? = null
        withTimeout(10_000) {
            while (result == null) {
                result = manager.activeNotifications.firstOrNull {
                    it.id == ReminderNotifications.notificationId(targetReminderId) &&
                        it.notification.channelId == ReminderNotifications.CHANNEL_ID
                }?.notification?.contentIntent
                if (result == null) delay(100)
            }
        }
        return requireNotNull(result)
    }
}
