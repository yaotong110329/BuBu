package com.kumo.bubu.core.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

interface ReminderNotificationScheduler {
    fun scheduleDailyCheck()
    fun cancelDailyCheck()
    fun cancelReminderNotification(reminderId: Long)
    fun cancelAllReminderNotifications()
}

class WorkManagerReminderNotificationScheduler(context: Context) : ReminderNotificationScheduler {
    private val appContext = context.applicationContext
    private val workManager = WorkManager.getInstance(appContext)

    override fun scheduleDailyCheck() {
        val now = LocalDateTime.now()
        val nextNineAm = now.toLocalDate().atTime(LocalTime.of(9, 0)).let {
            if (it.isAfter(now)) it else it.plusDays(1)
        }
        val request = PeriodicWorkRequestBuilder<ReminderNotificationWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, nextNineAm))
            .build()
        workManager.enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    override fun cancelDailyCheck() {
        workManager.cancelUniqueWork(WORK_NAME)
    }

    override fun cancelReminderNotification(reminderId: Long) =
        ReminderNotifications.cancel(appContext, reminderId)

    override fun cancelAllReminderNotifications() = ReminderNotifications.cancelAll(appContext)

    private companion object {
        const val WORK_NAME = "vehicle-reminder-notifications"
    }
}
