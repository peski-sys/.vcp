package dev.compatvideo.automation

import android.content.Context
import androidx.core.content.edit

internal data class AutomaticModeSnapshot(
    val requested: Boolean,
    val mediaStoreVersion: String?,
    val baselineGeneration: Long,
    val checkpointGeneration: Long,
    val lastStatus: String?,
)

internal class AutomaticModePreferences(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun snapshot(): AutomaticModeSnapshot = AutomaticModeSnapshot(
        requested = preferences.getBoolean(KEY_REQUESTED, false),
        mediaStoreVersion = preferences.getString(KEY_MEDIASTORE_VERSION, null),
        baselineGeneration = preferences.getLong(KEY_BASELINE_GENERATION, 0L),
        checkpointGeneration = preferences.getLong(KEY_CHECKPOINT_GENERATION, 0L),
        lastStatus = compactStatus(preferences.getString(KEY_LAST_STATUS, null)),
    )

    fun enable(
        mediaStoreVersion: String,
        currentGeneration: Long,
    ) {
        preferences.edit(commit = true) {
            putBoolean(KEY_REQUESTED, true)
            putString(KEY_MEDIASTORE_VERSION, mediaStoreVersion)
            putLong(KEY_BASELINE_GENERATION, currentGeneration)
            putLong(KEY_CHECKPOINT_GENERATION, currentGeneration)
            putString(KEY_LAST_STATUS, STATUS_READY)
        }
    }

    fun disable() {
        preferences.edit(commit = true) {
            putBoolean(KEY_REQUESTED, false)
            remove(KEY_MEDIASTORE_VERSION)
            remove(KEY_BASELINE_GENERATION)
            remove(KEY_CHECKPOINT_GENERATION)
            remove(KEY_LAST_STATUS)
        }
    }

    fun rebaseline(
        mediaStoreVersion: String,
        currentGeneration: Long,
    ) {
        preferences.edit(commit = true) {
            putString(KEY_MEDIASTORE_VERSION, mediaStoreVersion)
            putLong(KEY_BASELINE_GENERATION, currentGeneration)
            putLong(KEY_CHECKPOINT_GENERATION, currentGeneration)
            putString(KEY_LAST_STATUS, "Media library refreshed. Ready.")
        }
    }

    fun updateCheckpoint(generation: Long) {
        preferences.edit(commit = true) {
            putLong(KEY_CHECKPOINT_GENERATION, generation)
        }
    }

    fun updateStatus(status: String) {
        preferences.edit { putString(KEY_LAST_STATUS, status) }
    }

    fun recordPermissionRequired() {
        preferences.edit {
            putString(
                KEY_LAST_STATUS,
                "Automatic mode is paused until full video access is granted.",
            )
        }
    }

    private fun compactStatus(status: String?): String? = when (status) {
        null, "Automatic mode is off." -> null
        "Watching for newly added videos; existing videos are skipped." -> STATUS_READY
        else -> OLD_CREATED_STATUS.matchEntire(status)?.let { match ->
            val count = match.groupValues[1]
            "$count compatible ${if (count == "1") "copy" else "copies"} created."
        } ?: status
    }

    private companion object {
        const val PREFERENCES_NAME = "automatic_mode"
        const val KEY_REQUESTED = "requested"
        const val KEY_MEDIASTORE_VERSION = "mediastore_version"
        const val KEY_BASELINE_GENERATION = "baseline_generation"
        const val KEY_CHECKPOINT_GENERATION = "checkpoint_generation"
        const val KEY_LAST_STATUS = "last_status"
        const val STATUS_READY = "Ready."
        val OLD_CREATED_STATUS = Regex("Created (\\d+) compatible cop(?:y|ies); originals kept\\.")
    }
}
