package com.kumo.bubu.core.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

object BackupReminderNotifications {
    const val CHANNEL_ID = "backup_reminders"
    const val NOTIFICATION_ID = 7_007

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID }
            .forEach { manager.cancel(it.tag, it.id) }
    }
}
