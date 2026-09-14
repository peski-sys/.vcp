package dev.compatvideo.automation

import android.app.job.JobParameters
import android.app.job.JobService
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** A tiny content-triggered job. It only queues bounded processing and immediately goes idle. */
class MediaChangeJobService : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        val state = AutomaticModeManager(this).currentState()
        if (state.active) {
            val scheduler = MediaChangeScheduler(this)
            scheduler.scheduleProcessor()
            // A content-triggered job is consumed when it fires and cannot be persisted. Re-arm it
            // for the next MediaStore change, as required by JobScheduler's contract.
            scheduler.scheduleWatcher()
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false
}

@OptIn(markerClass = [UnstableApi::class])
class AutomaticProcessingJobService : JobService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runningJob: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val manager = AutomaticModeManager(this)
        if (!manager.currentState().active) return false

        runningJob = serviceScope.launch {
            var retry = false
            try {
                retry = AutomaticMediaScanner(this@AutomaticProcessingJobService)
                    .scan()
                    .shouldRetrySoon
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                AutomaticModePreferences(this@AutomaticProcessingJobService).updateStatus(
                    "Automatic checking was interrupted; Android will retry.",
                )
                retry = true
            }
            if (manager.currentState().active) {
                MediaChangeScheduler(this@AutomaticProcessingJobService).scheduleWatcher()
            }
            jobFinished(params, retry)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJob?.cancel()
        runningJob = null
        return AutomaticModeManager(this).currentState().active
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
