package com.kumo.bubu

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kumo.bubu.core.ui.theme.BuBuTheme
import com.kumo.bubu.navigation.BuBuNavHost

class MainActivity : ComponentActivity() {
    private var notificationReminderId by mutableStateOf<Long?>(null)
    private var notificationOpenReminders by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationReminderId = intent.reminderId()
        notificationOpenReminders = intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)
        enableEdgeToEdge()
        setContent {
            BuBuTheme {
                BuBuNavHost(
                    initialReminderId = notificationReminderId,
                    initialOpenReminders = notificationOpenReminders,
                    onNotificationHandled = {
                        notificationReminderId = null
                        notificationOpenReminders = false
                        intent.removeExtra(EXTRA_REMINDER_ID)
                        intent.removeExtra(EXTRA_OPEN_REMINDERS)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationReminderId = intent.reminderId()
        notificationOpenReminders = intent.getBooleanExtra(EXTRA_OPEN_REMINDERS, false)
    }

    private fun Intent.reminderId(): Long? =
        getLongExtra(EXTRA_REMINDER_ID, -1L).takeIf { it > 0L }

    companion object {
        const val EXTRA_REMINDER_ID = "com.kumo.bubu.extra.REMINDER_ID"
        const val EXTRA_OPEN_REMINDERS = "com.kumo.bubu.extra.OPEN_REMINDERS"
    }
}
