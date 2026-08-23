package com.kumo.bubu.core.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kumo.bubu.BuBuApplication
import com.kumo.bubu.MainActivity
import com.kumo.bubu.R
import com.kumo.bubu.data.mapper.toDomain
import com.kumo.bubu.domain.model.ReminderStatus
import com.kumo.bubu.domain.model.ReminderSource
import com.kumo.bubu.domain.model.status
import kotlinx.coroutines.flow.first
import java.time.LocalDate

class ReminderNotificationWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val application = applicationContext as BuBuApplication
        val container = application.container
        val today = LocalDate.now()
        if (runCatching { container.reminderRepository.refreshAutomaticReminders(today) }.isFailure) {
            return Result.retry()
        }
        if (!container.reminderNotificationSettings.observeEnabled().first()) {
            ReminderNotifications.cancelAll(applicationContext)
            return Result.success()
        }
        val taxAndFeeRemindersEnabled =
            container.statutoryReminderSettings.observeTaxAndFeeEnabled().first()
        if (!canPostNotifications()) {
            return Result.success()
        }
        createChannel()
        val reminderEntities = container.database.vehicleReminderDao().getAll()
        ReminderNotifications.cancelDeleted(applicationContext, reminderEntities.map { it.id })
        reminderEntities.forEach { reminderEntity ->
            val vehicle = container.database.vehicleDao().getById(reminderEntity.vehicleId)
                ?: return@forEach
            val reminder = reminderEntity.toDomain()
            val status = reminder.status(vehicle.currentOdometerKm, today)
            val taxOrFeeDisabled = !taxAndFeeRemindersEnabled &&
                (reminder.source == ReminderSource.LICENSE_TAX ||
                    reminder.source == ReminderSource.ROAD_MAINTENANCE_FEE)
            if (vehicle.isArchived || !reminder.isEnabled || reminder.isCompleted || status == null ||
                taxOrFeeDisabled
            ) {
                ReminderNotifications.cancel(applicationContext, reminder.id)
                return@forEach
            }
            val isSnoozed = reminder.snoozedUntilEpochDay?.let { it > today.toEpochDay() } == true
            if (isSnoozed) {
                ReminderNotifications.cancel(applicationContext, reminder.id)
                return@forEach
            }
            val trigger = selectReminderNotificationTrigger(
                source = reminder.source,
                status = status,
                dueDateEpochDay = reminder.dueDateEpochDay,
                referenceDateEpochDay = reminder.referenceDateEpochDay,
                estimatedNotificationEpochDay = reminder.estimatedNotificationEpochDay,
                lastNotifiedTrigger = reminder.lastNotifiedTrigger,
                today = today,
                isEnabled = reminder.isEnabled,
                isCompleted = reminder.isCompleted,
                isArchived = vehicle.isArchived,
                isSnoozed = isSnoozed,
            )
            if (trigger != null) {
                postNotification(reminder.id, reminder.title, trigger.notificationStatus)
                container.database.vehicleReminderDao().updateNotificationTracking(
                    id = reminder.id,
                    status = trigger.notificationStatus,
                    trigger = trigger.key,
                    updatedAt = System.currentTimeMillis(),
                )
                val latestEntity = container.database.vehicleReminderDao().getById(reminder.id)
                val latestVehicle = latestEntity?.let { container.database.vehicleDao().getById(it.vehicleId) }
                val latestReminder = latestEntity?.toDomain()
                val latestSnoozed = latestReminder?.snoozedUntilEpochDay
                    ?.let { it > today.toEpochDay() } == true
                val latestEligibleTrigger = if (latestReminder != null && latestVehicle != null) {
                    selectReminderNotificationTrigger(
                        source = latestReminder.source,
                        status = latestReminder.status(latestVehicle.currentOdometerKm, today),
                        dueDateEpochDay = latestReminder.dueDateEpochDay,
                        referenceDateEpochDay = latestReminder.referenceDateEpochDay,
                        estimatedNotificationEpochDay = latestReminder.estimatedNotificationEpochDay,
                        lastNotifiedTrigger = null,
                        today = today,
                        isEnabled = latestReminder.isEnabled,
                        isCompleted = latestReminder.isCompleted,
                        isArchived = latestVehicle.isArchived,
                        isSnoozed = latestSnoozed,
                    )
                } else {
                    null
                }
                val notificationsStillEnabled =
                    container.reminderNotificationSettings.observeEnabled().first()
                val taxAndFeeRemindersStillEnabled =
                    container.statutoryReminderSettings.observeTaxAndFeeEnabled().first()
                val latestTaxOrFeeDisabled = latestReminder != null && !taxAndFeeRemindersStillEnabled &&
                    (latestReminder.source == ReminderSource.LICENSE_TAX ||
                        latestReminder.source == ReminderSource.ROAD_MAINTENANCE_FEE)
                if (!notificationsStillEnabled || !canPostNotifications() || latestEligibleTrigger == null ||
                    latestTaxOrFeeDisabled
                ) {
                    ReminderNotifications.cancel(applicationContext, reminder.id)
                    if (latestEntity != null) {
                        container.database.vehicleReminderDao().updateNotificationTracking(
                            id = reminder.id,
                            status = null,
                            trigger = null,
                            updatedAt = System.currentTimeMillis(),
                        )
                    }
                }
            } else if (status == ReminderStatus.NORMAL && reminder.lastNotifiedTrigger?.startsWith("status:") == true) {
                container.database.vehicleReminderDao().updateNotificationTracking(
                    id = reminder.id,
                    status = null,
                    trigger = null,
                    updatedAt = System.currentTimeMillis(),
                )
            }
        }
        return Result.success()
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, applicationContext.getString(R.string.reminder_notification_channel), NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    @SuppressLint("MissingPermission")
    private fun postNotification(reminderId: Long, title: String, status: ReminderStatus) {
        if (!canPostNotifications()) return
        val intent = Intent(applicationContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_REMINDER_ID, reminderId)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ReminderNotifications.notificationId(reminderId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val text = applicationContext.getString(
            if (status == ReminderStatus.OVERDUE) R.string.reminder_notification_overdue else R.string.reminder_notification_due_soon,
            title,
        )
        NotificationManagerCompat.from(applicationContext).notify(
            ReminderNotifications.notificationId(reminderId),
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(applicationContext.getString(R.string.reminder_notification_title))
                .setContentText(text)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }

    private companion object {
        const val CHANNEL_ID = ReminderNotifications.CHANNEL_ID
    }
}
