package dev.compatvideo.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.compatvideo.reminder.OriginalReminderManager

class AutomaticModeBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (AutomaticModeManager(context).currentState().active) {
            MediaChangeScheduler(context).scheduleWatcher()
        }
        OriginalReminderManager(context).reconcile()
    }
}
