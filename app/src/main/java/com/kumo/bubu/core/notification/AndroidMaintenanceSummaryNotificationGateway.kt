package com.kumo.bubu.core.notification

import android.annotation.SuppressLint
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kumo.bubu.MainActivity
import com.kumo.bubu.R

class AndroidMaintenanceSummaryNotificationGateway(context: Context) : MaintenanceSummaryNotificationGateway {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(NotificationManager::class.java)

    override fun isActive(): Boolean = manager.activeNotifications.any { notification ->
        notification.id == ReminderNotifications.MAINTENANCE_NOTIFICATION_ID &&
            notification.notification.channelId == ReminderNotifications.CHANNEL_ID
    }

    @SuppressLint("MissingPermission")
    override fun post(count: Int, alert: Boolean) {
        val intent = Intent(appContext, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_OPEN_REMINDERS, true)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            ReminderNotifications.MAINTENANCE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        NotificationManagerCompat.from(appContext).notify(
            ReminderNotifications.MAINTENANCE_NOTIFICATION_ID,
            NotificationCompat.Builder(appContext, ReminderNotifications.CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(appContext.getString(R.string.reminder_notification_title))
                .setContentText(appContext.getString(R.string.maintenance_notification_summary, count))
                .setContentIntent(pendingIntent)
                .setOnlyAlertOnce(!alert)
                .setAutoCancel(true)
                .build(),
        )
    }

    override fun cancel() {
        NotificationManagerCompat.from(appContext).cancel(ReminderNotifications.MAINTENANCE_NOTIFICATION_ID)
    }
}
