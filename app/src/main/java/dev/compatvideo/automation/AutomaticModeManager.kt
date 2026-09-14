package dev.compatvideo.automation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AutomaticModeState(
    val supported: Boolean,
    val requested: Boolean,
    val hasFullVideoAccess: Boolean,
    val lastStatus: String?,
) {
    val active: Boolean
        get() = supported && requested && hasFullVideoAccess
}

class AutomaticModeManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val preferences = AutomaticModePreferences(appContext)
    private val scheduler = MediaChangeScheduler(appContext)

    suspend fun enable(): AutomaticModeState = withContext(Dispatchers.IO) {
        if (!AutomaticModePlatform.isSupported()) return@withContext currentState()
        if (!hasFullVideoAccess()) {
            preferences.recordPermissionRequired()
            return@withContext currentState()
        }
        val version = MediaStore.getVersion(appContext, MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val generation = MediaStore.getGeneration(
            appContext,
            MediaStore.VOLUME_EXTERNAL_PRIMARY,
        )
        preferences.enable(version, generation)
        if (!scheduler.scheduleWatcher()) {
            preferences.updateStatus("Android could not schedule automatic monitoring.")
        }
        currentState()
    }

    suspend fun disable(): AutomaticModeState = withContext(Dispatchers.IO) {
        scheduler.cancelAll()
        preferences.disable()
        currentState()
    }

    suspend fun reconcile(): AutomaticModeState = withContext(Dispatchers.IO) {
        val snapshot = preferences.snapshot()
        if (!snapshot.requested || !AutomaticModePlatform.isSupported()) {
            scheduler.cancelAll()
        } else if (!hasFullVideoAccess()) {
            scheduler.cancelAll()
            preferences.disable()
            preferences.recordPermissionRequired()
        } else if (!scheduler.isWatcherScheduled() && !scheduler.scheduleWatcher()) {
            preferences.updateStatus("Android could not schedule automatic monitoring.")
        }
        currentState()
    }

    fun recordPermissionRequired(): AutomaticModeState {
        preferences.recordPermissionRequired()
        return currentState()
    }

    fun currentState(): AutomaticModeState {
        val snapshot = preferences.snapshot()
        return AutomaticModeState(
            supported = AutomaticModePlatform.isSupported(),
            requested = snapshot.requested,
            hasFullVideoAccess = hasFullVideoAccess(),
            lastStatus = snapshot.lastStatus,
        )
    }

    fun requiredPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_VIDEO,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
        )
        else -> emptyArray()
    }

    private fun hasFullVideoAccess(): Boolean {
        val permission = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                Manifest.permission.READ_MEDIA_VIDEO
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
            else -> return false
        }
        return ContextCompat.checkSelfPermission(appContext, permission) ==
            PackageManager.PERMISSION_GRANTED
    }

}
