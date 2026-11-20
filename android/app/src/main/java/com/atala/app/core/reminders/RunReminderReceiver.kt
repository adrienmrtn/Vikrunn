package com.atala.app.core.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Shows a session reminder when its alarm goes off, and registers the alarms again after a reboot. */
class RunReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            RunReminders.ACTION_REMIND -> {
                val id = intent.getStringExtra(RunReminders.EXTRA_ID) ?: return
                RunReminders.take(context, id)?.let { RunReminders.post(context, it) }
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> RunReminders.restore(context)
        }
    }
}
