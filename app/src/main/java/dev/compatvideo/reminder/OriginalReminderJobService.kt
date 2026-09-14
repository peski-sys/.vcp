package dev.compatvideo.reminder

import android.app.job.JobParameters
import android.app.job.JobService

class OriginalReminderJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        OriginalReminderManager(this).deliverDueReminder()
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = true
}
