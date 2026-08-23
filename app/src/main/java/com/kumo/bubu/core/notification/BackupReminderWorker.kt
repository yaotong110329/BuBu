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
import java.time.LocalDate
import kotlinx.coroutines.flow.first

class BackupReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val container = (applicationContext as BuBuApplication).container
        if (!container.backupReminderSettings.observeEnabled().first()) {
            BackupReminderNotifications.cancelAll(applicationContext)
            return Result.success()
        }
        val shouldRemind = needsMonthlyBackupReminder(
            container.backupReminderSettings.observeLastSuccessfulBackupEpochDay().first(),
            LocalDate.now(),
        )
        if (!shouldRemind || !canPostNotifications()) {
            if (!shouldRemind) BackupReminderNotifications.cancel(applicationContext)
            return Result.success()
        }
        createChannel()
        postNotification()
        return Result.success()
    }

    private fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun createChannel() {
        applicationContext.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                BackupReminderNotifications.CHANNEL_ID,
                applicationContext.getString(R.string.backup_reminder_notification_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    @SuppressLint("MissingPermission")
    private fun postNotification() {
        if (!canPostNotifications()) return
        val intent = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            BackupReminderNotifications.NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(applicationContext).notify(
            BackupReminderNotifications.NOTIFICATION_ID,
            NotificationCompat.Builder(applicationContext, BackupReminderNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(applicationContext.getString(R.string.backup_reminder_notification_title))
                .setContentText(applicationContext.getString(R.string.backup_reminder_notification_message))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build(),
        )
    }
}
