package dev.compatvideo.automation

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import dev.compatvideo.inspection.AndroidMediaInspector
import dev.compatvideo.normalization.ManualVideoNormalizer
import dev.compatvideo.normalization.NormalizationPlanner
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

internal data class AutomaticScanResult(
    val shouldRetrySoon: Boolean,
)

@OptIn(markerClass = [UnstableApi::class])
internal class AutomaticMediaScanner(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val preferences = AutomaticModePreferences(appContext)
    private val inspector = AndroidMediaInspector(appContext)
    private val normalizer = ManualVideoNormalizer(appContext)

    suspend fun scan(): AutomaticScanResult = withContext(Dispatchers.IO) {
        if (!AutomaticModePlatform.isSupported()) {
            return@withContext AutomaticScanResult(false)
        }
        normalizer.recoverInterruptedWork()
        val initial = preferences.snapshot()
        if (!initial.requested) return@withContext AutomaticScanResult(false)

        val currentVersion = MediaStore.getVersion(
            appContext,
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        if (initial.mediaStoreVersion != currentVersion) {
            val currentGeneration = MediaStore.getGeneration(
                appContext,
                MediaStore.VOLUME_EXTERNAL_PRIMARY,
            )
            preferences.rebaseline(currentVersion, currentGeneration)
            return@withContext AutomaticScanResult(false)
        }

        val candidates = queryCandidates(initial.checkpointGeneration)
        var checkedCount = 0
        var createdCount = 0
        var failedCount = 0

        for (candidate in candidates) {
            coroutineContext.ensureActive()
            if (
                AutomaticCandidatePolicy.shouldInspect(
                    candidate = candidate,
                    baselineGeneration = initial.baselineGeneration,
                    ownPackageName = appContext.packageName,
                )
            ) {
                checkedCount++
                try {
                    val uri = ContentUris.withAppendedId(videoCollection(), candidate.id)
                    val inspection = inspector.inspect(uri)
                    if (NormalizationPlanner.plan(inspection.facts) != null) {
                        normalizer.normalize(uri, inspection.facts) { }
                        createdCount++
                    }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    // A single inaccessible or malformed video must not block all later additions.
                    // The manual picker remains available for a visible diagnosis and retry.
                    failedCount++
                }
            }
            preferences.updateCheckpoint(candidate.generationModified)
        }

        if (checkedCount > 0) {
            preferences.updateStatus(
                buildStatus(checkedCount, createdCount, failedCount),
            )
        }
        AutomaticScanResult(shouldRetrySoon = candidates.size == BATCH_LIMIT)
    }

    private fun queryCandidates(afterGeneration: Long): List<AutomaticVideoCandidate> {
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.MediaColumns.GENERATION_MODIFIED} > ? AND " +
                    "${MediaStore.MediaColumns.IS_PENDING} = 0",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(afterGeneration.toString()),
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SORT_COLUMNS,
                arrayOf(MediaStore.MediaColumns.GENERATION_MODIFIED),
            )
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_ASCENDING,
            )
            putInt(ContentResolver.QUERY_ARG_LIMIT, BATCH_LIMIT)
        }
        return contentResolver.query(
            videoCollection(),
            PROJECTION,
            queryArgs,
            null,
        )?.use(::readCandidates).orEmpty()
    }

    private fun readCandidates(cursor: Cursor): List<AutomaticVideoCandidate> {
        val idIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        val addedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.GENERATION_ADDED)
        val modifiedIndex = cursor.getColumnIndexOrThrow(
            MediaStore.MediaColumns.GENERATION_MODIFIED,
        )
        val ownerIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.OWNER_PACKAGE_NAME)
        val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
        return buildList {
            while (cursor.moveToNext()) {
                add(
                    AutomaticVideoCandidate(
                        id = cursor.getLong(idIndex),
                        generationAdded = cursor.getLong(addedIndex),
                        generationModified = cursor.getLong(modifiedIndex),
                        ownerPackageName = cursor.getString(ownerIndex),
                        relativePath = cursor.getString(pathIndex),
                    ),
                )
            }
        }
    }

    private fun videoCollection() = MediaStore.Video.Media.getContentUri(
        MediaStore.VOLUME_EXTERNAL_PRIMARY,
    )

    private fun buildStatus(
        checkedCount: Int,
        createdCount: Int,
        failedCount: Int,
    ): String = buildString {
        if (createdCount > 0) {
            append(createdCount)
            append(if (createdCount == 1) " compatible copy created." else " compatible copies created.")
        } else {
            append("No changes needed for ")
            append(checkedCount)
            append(if (checkedCount == 1) " new video." else " new videos.")
        }
        if (failedCount > 0) {
            append(" ")
            append(failedCount)
            append(if (failedCount == 1) " item needs" else " items need")
            append(" a manual check.")
        }
    }

    private companion object {
        const val BATCH_LIMIT = 24
        val PROJECTION = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.GENERATION_ADDED,
            MediaStore.MediaColumns.GENERATION_MODIFIED,
            MediaStore.MediaColumns.OWNER_PACKAGE_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
        )
    }
}
