package dev.compatvideo.reminder

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.media3.common.util.UnstableApi
import dev.compatvideo.MainActivity
import dev.compatvideo.R
import java.util.UUID

data class OriginalReminderState(
    val enabled: Boolean,
)

/**
 * Schedules one-shot, local reminders for originals that were kept after a successful copy.
 * Only anonymous timestamps are stored; source URIs and filenames are deliberately excluded.
 */
class OriginalReminderManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )
    private val jobScheduler = appContext.getSystemService(JobScheduler::class.java)

    fun currentState(): OriginalReminderState = OriginalReminderState(
        enabled = preferences.getBoolean(KEY_ENABLED, false) && hasNotificationPermission(),
    )

    fun enable(): OriginalReminderState {
        if (!hasNotificationPermission()) return disable()
        createNotificationChannel()
        preferences.edit(commit = true) { putBoolean(KEY_ENABLED, true) }
        scheduleNext()
        return currentState()
    }

    fun disable(): OriginalReminderState {
        jobScheduler.cancel(REMINDER_JOB_ID)
        preferences.edit(commit = true) {
            putBoolean(KEY_ENABLED, false)
            remove(KEY_EVENTS)
        }
        return currentState()
    }

    fun reconcile(): OriginalReminderState {
        if (!preferences.getBoolean(KEY_ENABLED, false)) {
            jobScheduler.cancel(REMINDER_JOB_ID)
            return currentState()
        }
        if (!hasNotificationPermission()) return disable()
        createNotificationChannel()
        scheduleNext()
        return currentState()
    }

    fun recordCompatibleCopy() {
        synchronized(lock) {
            if (!currentState().enabled) return
            val events = readEvents().toMutableSet()
            events += "${System.currentTimeMillis()}|${UUID.randomUUID()}"
            preferences.edit(commit = true) { putStringSet(KEY_EVENTS, events) }
            scheduleNext()
        }
    }

    internal fun deliverDueReminder() {
        synchronized(lock) {
            if (!currentState().enabled) return
            val now = System.currentTimeMillis()
            val events = readEvents()
            val due = OriginalReminderSchedule.dueEvents(events, now)
            if (due.isEmpty()) {
                scheduleNext()
                return
            }

            postNotification(due.size)
            preferences.edit(commit = true) {
                putStringSet(KEY_EVENTS, events.minus(due.toSet()))
            }
            scheduleNext()
        }
    }

    fun requiredPermission(): String? = if (
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        !hasNotificationPermission()
    ) {
        Manifest.permission.POST_NOTIFICATIONS
    } else {
        null
    }

    private fun scheduleNext() {
        jobScheduler.cancel(REMINDER_JOB_ID)
        if (!currentState().enabled) return
        val delay = OriginalReminderSchedule.nextDelay(
            readEvents(),
            System.currentTimeMillis(),
        ) ?: return
        val job = JobInfo.Builder(
            REMINDER_JOB_ID,
            ComponentName(appContext, OriginalReminderJobService::class.java),
        )
            .setMinimumLatency(delay)
            .setPersisted(true)
            .build()
        jobScheduler.schedule(job)
    }

    @OptIn(markerClass = [UnstableApi::class])
    private fun postNotification(count: Int) {
        createNotificationChannel()
        val openApp = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            0,
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val body = appContext.resources.getQuantityString(
            R.plurals.original_reminder_body,
            count,
            count,
        )
        val notification = Notification.Builder(appContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(appContext.getString(R.string.original_reminder_notification_title))
            .setContentText(body)
            .setStyle(Notification.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        appContext.getSystemService(NotificationManager::class.java)
            .notify(REMINDER_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            appContext.getString(R.string.original_reminder_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = appContext.getString(R.string.original_reminder_channel_description)
        }
        appContext.getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    private fun readEvents(): Set<String> =
        preferences.getStringSet(KEY_EVENTS, emptySet()).orEmpty().toSet()

    private companion object {
        val lock = Any()
        const val PREFERENCES_NAME = "original_reminders"
        const val KEY_ENABLED = "enabled"
        const val KEY_EVENTS = "copy_events"
        const val CHANNEL_ID = "original_review_reminders"
        const val REMINDER_JOB_ID = 0x56435052
        const val REMINDER_NOTIFICATION_ID = 0x564350
    }
}

internal object OriginalReminderSchedule {
    const val REMINDER_DELAY_MS = 30L * 24L * 60L * 60L * 1_000L

    fun dueEvents(events: Set<String>, now: Long): Set<String> = events
        .filterTo(mutableSetOf()) { event ->
            timestamp(event)?.let { it <= now - REMINDER_DELAY_MS } == true
        }

    fun nextDelay(events: Set<String>, now: Long): Long? {
        val nextTimestamp = events.mapNotNull(::timestamp).minOrNull() ?: return null
        return (nextTimestamp + REMINDER_DELAY_MS - now).coerceAtLeast(1L)
    }

    private fun timestamp(event: String): Long? =
        event.substringBefore('|').toLongOrNull()
}
