package com.kumo.bubu.core.notification

import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationManagerCompat

object ReminderNotifications {
    const val CHANNEL_ID = "vehicle_reminders"

    fun cancel(context: Context, reminderId: Long) {
        NotificationManagerCompat.from(context).cancel(notificationId(reminderId))
    }

    fun cancelAll(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun cancelDeleted(context: Context, activeReminderIds: Collection<Long>) {
        val knownNotificationIds = activeReminderIds.mapTo(mutableSetOf(), ::notificationId)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.activeNotifications
            .filter { it.notification.channelId == CHANNEL_ID && it.id !in knownNotificationIds }
            .forEach { manager.cancel(it.tag, it.id) }
    }

    fun notificationId(reminderId: Long): Int = (reminderId xor (reminderId ushr 32)).toInt()
}
