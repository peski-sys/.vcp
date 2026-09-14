package dev.compatvideo.normalization

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import dev.compatvideo.inspection.AndroidMediaInspector
import dev.compatvideo.inspection.MediaFacts
import dev.compatvideo.reminder.OriginalReminderManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@UnstableApi
class ManualVideoNormalizer(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val inspector = AndroidMediaInspector(appContext)
    private val publisher = MediaStoreVideoPublisher(appContext)
    private val decodabilityValidator = OutputDecodabilityValidator(appContext)

    suspend fun recoverInterruptedWork() = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            publisher.discardInterruptedPendingOutput()
            val directory = File(appContext.cacheDir, STAGING_DIRECTORY)
            directory.listFiles { file ->
                file.isFile && file.name.startsWith(STAGING_PREFIX) && file.extension == "mp4"
            }?.forEach { file -> runCatching { file.delete() } }
        }
    }

    suspend fun normalize(
        inputUri: android.net.Uri,
        sourceFacts: MediaFacts,
        onProgress: (NormalizationProgress) -> Unit,
    ): NormalizationOutcome = operationMutex.withLock {
        val plan = NormalizationPlanner.plan(sourceFacts)
            ?: throw NormalizationException(
                "This video is outside the exact lossless case supported by this version. Nothing was changed.",
            )
        val stagingFile = withContext(Dispatchers.IO) { createStagingPath() }
        var pendingUri: android.net.Uri? = null
        try {
            onProgress(NormalizationProgress(NormalizationStage.REMUXING, null))
            val exportResult = withContext(Dispatchers.Main.immediate) {
                exportBaseLayer(inputUri, stagingFile, onProgress)
            }
            verifyStreamCopy(exportResult, sourceFacts, stagingFile)

            onProgress(NormalizationProgress(NormalizationStage.SAVING, 0))
            val stagedUri = publisher.createPending(plan.outputDisplayName)
            pendingUri = stagedUri
            publisher.copyIntoPending(stagingFile, stagedUri) { percent ->
                onProgress(NormalizationProgress(NormalizationStage.SAVING, percent))
            }

            onProgress(NormalizationProgress(NormalizationStage.VALIDATING, null))
            val outputInspection = withContext(Dispatchers.IO) { inspector.inspect(stagedUri) }
            val validation = OutputValidator.validate(sourceFacts, outputInspection.facts)
            if (!validation.isValid) {
                throw NormalizationException(
                    "The new copy failed verification: ${validation.problems.joinToString(separator = " ")} It was discarded.",
                )
            }
            withContext(Dispatchers.IO) {
                decodabilityValidator.validateFirstFrame(stagedUri)
            }
            publisher.publish(stagedUri)
            val publishedUri = stagedUri
            pendingUri = null
            runCatching { OriginalReminderManager(appContext).recordCompatibleCopy() }

            NormalizationOutcome(
                outputUri = publishedUri,
                outputDisplayName = plan.outputDisplayName,
                relativePath = MediaStoreVideoPublisher.OUTPUT_RELATIVE_PATH,
                summary = "Verified lossless stream copy: HEVC Main 10/HLG and AAC were not re-encoded, the Dolby Vision container declaration was omitted, and Android decoded the first frame.",
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: NormalizationException) {
            throw exception
        } catch (exception: Exception) {
            throw NormalizationException(
                "The compatible copy could not be completed. Any incomplete output was removed and the original is unchanged.",
                exception,
            )
        } finally {
            withContext(NonCancellable + Dispatchers.IO) {
                pendingUri?.let { publisher.discard(it) }
                runCatching { stagingFile.delete() }
            }
        }
    }

    private fun createStagingPath(): File {
        val directory = File(appContext.cacheDir, STAGING_DIRECTORY)
        if (!directory.exists() && !directory.mkdirs()) {
            throw NormalizationException("The app could not create private temporary storage.")
        }
        return File(directory, "${STAGING_PREFIX}${UUID.randomUUID()}.mp4")
    }

    private fun verifyStreamCopy(
        result: ExportResult,
        sourceFacts: MediaFacts,
        stagingFile: File,
    ) {
        if (result.videoConversionProcess != ExportResult.CONVERSION_PROCESS_TRANSMUXED) {
            throw NormalizationException(
                "This device required video re-encoding, so the output was rejected to protect quality.",
            )
        }
        if (
            sourceFacts.audioTracks.isNotEmpty() &&
            result.audioConversionProcess != ExportResult.CONVERSION_PROCESS_TRANSMUXED
        ) {
            throw NormalizationException(
                "This device required audio re-encoding, so the output was rejected to protect quality.",
            )
        }
        // ExportResult.videoMimeType is explicitly nullable/unknown in Media3. The staged-file
        // validator below is authoritative and requires an actual HEVC hvc1 track plus hvcC.
        if (!stagingFile.isFile || stagingFile.length() <= 0L) {
            throw NormalizationException("The remux produced no readable output.")
        }
    }

    private suspend fun exportBaseLayer(
        inputUri: android.net.Uri,
        outputFile: File,
        onProgress: (NormalizationProgress) -> Unit,
    ): ExportResult = suspendCancellableCoroutine { continuation ->
        var progressJob: Job? = null
        lateinit var transformer: Transformer
        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                progressJob?.cancel()
                if (continuation.isActive) continuation.resume(exportResult)
            }

            override fun onError(
                composition: Composition,
                exportResult: ExportResult,
                exportException: ExportException,
            ) {
                progressJob?.cancel()
                if (continuation.isActive) continuation.resumeWithException(exportException)
            }
        }
        transformer = Transformer.Builder(appContext)
            .setVideoMimeType(MimeTypes.VIDEO_H265)
            .setMuxerFactory(HevcBaseLayerMuxerFactory())
            .setUsePlatformDiagnostics(false)
            .addListener(listener)
            .build()

        continuation.invokeOnCancellation {
            progressJob?.cancel()
            if (Looper.myLooper() == transformer.applicationLooper) {
                transformer.cancel()
            } else {
                Handler(transformer.applicationLooper).post { transformer.cancel() }
            }
        }

        try {
            transformer.start(MediaItem.fromUri(inputUri), outputFile.absolutePath)
            progressJob = CoroutineScope(continuation.context).launch {
                val holder = ProgressHolder()
                while (isActive && continuation.isActive) {
                    val state = transformer.getProgress(holder)
                    val percent = if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        holder.progress.coerceIn(0, 100)
                    } else {
                        null
                    }
                    onProgress(NormalizationProgress(NormalizationStage.REMUXING, percent))
                    delay(PROGRESS_POLL_INTERVAL_MS)
                }
            }
        } catch (exception: Exception) {
            progressJob?.cancel()
            if (continuation.isActive) continuation.resumeWithException(exception)
        }
    }

    private companion object {
        val operationMutex = Mutex()
        const val STAGING_DIRECTORY = "normalization"
        const val STAGING_PREFIX = "compat-video-"
        const val PROGRESS_POLL_INTERVAL_MS = 300L
    }
}
