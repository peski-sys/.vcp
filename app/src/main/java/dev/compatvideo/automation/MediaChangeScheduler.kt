package dev.compatvideo.automation

import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Context
import android.provider.MediaStore

internal class MediaChangeScheduler(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val jobScheduler = appContext.getSystemService(JobScheduler::class.java)

    fun scheduleWatcher(): Boolean {
        val videoCollection = MediaStore.Video.Media.getContentUri(
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        val job = JobInfo.Builder(
            WATCHER_JOB_ID,
            ComponentName(appContext, MediaChangeJobService::class.java),
        )
            .addTriggerContentUri(
                JobInfo.TriggerContentUri(
                    videoCollection,
                    JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                ),
            )
            .setTriggerContentUpdateDelay(CONTENT_UPDATE_DELAY_MS)
            .setTriggerContentMaxDelay(CONTENT_MAX_DELAY_MS)
            .build()
        return jobScheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    fun isWatcherScheduled(): Boolean = jobScheduler.getPendingJob(WATCHER_JOB_ID) != null

    fun scheduleProcessor(): Boolean {
        val job = JobInfo.Builder(
            PROCESSOR_JOB_ID,
            ComponentName(appContext, AutomaticProcessingJobService::class.java),
        )
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .setBackoffCriteria(RETRY_BACKOFF_MS, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
            .build()
        return jobScheduler.schedule(job) == JobScheduler.RESULT_SUCCESS
    }

    fun cancelAll() {
        jobScheduler.cancel(WATCHER_JOB_ID)
        jobScheduler.cancel(PROCESSOR_JOB_ID)
    }

    private companion object {
        const val WATCHER_JOB_ID = 0x434F4D50
        const val PROCESSOR_JOB_ID = 0x434F4D51
        const val CONTENT_UPDATE_DELAY_MS = 5_000L
        const val CONTENT_MAX_DELAY_MS = 30_000L
        const val RETRY_BACKOFF_MS = 30_000L
    }
}
