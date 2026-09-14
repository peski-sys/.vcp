package dev.compatvideo

import android.app.Application
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import dev.compatvideo.automation.AutomaticModeManager
import dev.compatvideo.automation.AutomaticModeState
import dev.compatvideo.inspection.AndroidMediaInspector
import dev.compatvideo.inspection.MediaInspection
import dev.compatvideo.inspection.MediaInspectionException
import dev.compatvideo.normalization.ManualVideoNormalizer
import dev.compatvideo.normalization.NormalizationException
import dev.compatvideo.normalization.NormalizationOutcome
import dev.compatvideo.normalization.NormalizationPlanner
import dev.compatvideo.normalization.NormalizationProgress
import dev.compatvideo.reminder.OriginalReminderManager
import dev.compatvideo.reminder.OriginalReminderState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface InspectionUiState {
    data object Empty : InspectionUiState
    data class Loading(val displayHint: String?) : InspectionUiState
    data class Loaded(val inspection: MediaInspection) : InspectionUiState
    data class Error(val message: String) : InspectionUiState
}

sealed interface NormalizationUiState {
    data object Idle : NormalizationUiState
    data class Running(val progress: NormalizationProgress) : NormalizationUiState
    data class Success(val outcome: NormalizationOutcome) : NormalizationUiState
    data class Error(val message: String) : NormalizationUiState
}

@UnstableApi
class InspectionViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle,
) : AndroidViewModel(application) {
    private val inspector = AndroidMediaInspector(application)
    private val normalizer = ManualVideoNormalizer(application)
    private val automaticModeManager = AutomaticModeManager(application)
    private val originalReminderManager = OriginalReminderManager(application)
    private val mutableState = MutableStateFlow<InspectionUiState>(InspectionUiState.Empty)
    val state: StateFlow<InspectionUiState> = mutableState.asStateFlow()
    private val mutableNormalizationState =
        MutableStateFlow<NormalizationUiState>(NormalizationUiState.Idle)
    val normalizationState: StateFlow<NormalizationUiState> = mutableNormalizationState.asStateFlow()
    private val mutableAutomaticModeState = MutableStateFlow(automaticModeManager.currentState())
    val automaticModeState: StateFlow<AutomaticModeState> = mutableAutomaticModeState.asStateFlow()
    private val mutableOriginalReminderState = MutableStateFlow(originalReminderManager.currentState())
    val originalReminderState: StateFlow<OriginalReminderState> =
        mutableOriginalReminderState.asStateFlow()

    private var inspectionJob: Job? = null
    private var normalizationJob: Job? = null
    private val recoveryJob: Job

    init {
        recoveryJob = viewModelScope.launch { normalizer.recoverInterruptedWork() }
        refreshAutomaticMode()
        refreshOriginalReminder()
        savedStateHandle.get<String>(SELECTED_URI_KEY)?.let { inspect(it.toUri()) }
    }

    fun refreshAutomaticMode() {
        viewModelScope.launch {
            mutableAutomaticModeState.value = automaticModeManager.reconcile()
        }
    }

    fun enableAutomaticMode() {
        viewModelScope.launch {
            mutableAutomaticModeState.value = automaticModeManager.enable()
        }
    }

    fun disableAutomaticMode() {
        viewModelScope.launch {
            mutableAutomaticModeState.value = automaticModeManager.disable()
        }
    }

    fun automaticPermissionWasNotGranted() {
        mutableAutomaticModeState.value = automaticModeManager.recordPermissionRequired()
    }

    fun requiredAutomaticPermissions(): Array<String> =
        automaticModeManager.requiredPermissions()

    fun refreshOriginalReminder() {
        mutableOriginalReminderState.value = originalReminderManager.reconcile()
    }

    fun setOriginalReminderEnabled(enabled: Boolean) {
        mutableOriginalReminderState.value = if (enabled) {
            originalReminderManager.enable()
        } else {
            originalReminderManager.disable()
        }
    }

    fun requiredReminderPermission(): String? = originalReminderManager.requiredPermission()

    fun inspect(uri: Uri) {
        normalizationJob?.cancel()
        mutableNormalizationState.value = NormalizationUiState.Idle
        savedStateHandle[SELECTED_URI_KEY] = uri.toString()
        inspectionJob?.cancel()
        inspectionJob = viewModelScope.launch {
            mutableState.value = InspectionUiState.Loading(uri.lastPathSegment)
            mutableState.value = try {
                val result = withContext(Dispatchers.IO) { inspector.inspect(uri) }
                InspectionUiState.Loaded(result)
            } catch (exception: MediaInspectionException) {
                InspectionUiState.Error(exception.userFacingMessage)
            } catch (_: SecurityException) {
                savedStateHandle.remove<String>(SELECTED_URI_KEY)
                InspectionUiState.Error("Access to this video expired. Select it again to grant read access.")
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                InspectionUiState.Error("The selected video could not be inspected safely. The file was not changed.")
            }
        }
    }

    fun createCompatibleCopy() {
        if (normalizationJob?.isActive == true) return
        val loaded = mutableState.value as? InspectionUiState.Loaded ?: return
        val uri = savedStateHandle.get<String>(SELECTED_URI_KEY)?.toUri() ?: return
        if (NormalizationPlanner.plan(loaded.inspection.facts) == null) {
            mutableNormalizationState.value = NormalizationUiState.Error(
                "This video is outside the exact lossless case supported by this version. Nothing was changed.",
            )
            return
        }

        normalizationJob = viewModelScope.launch {
            recoveryJob.join()
            mutableNormalizationState.value = NormalizationUiState.Running(
                NormalizationProgress(
                    stage = dev.compatvideo.normalization.NormalizationStage.REMUXING,
                    percent = null,
                ),
            )
            mutableNormalizationState.value = try {
                val outcome = normalizer.normalize(uri, loaded.inspection.facts) { progress ->
                    mutableNormalizationState.value = NormalizationUiState.Running(progress)
                }
                NormalizationUiState.Success(outcome)
            } catch (exception: NormalizationException) {
                NormalizationUiState.Error(exception.userFacingMessage)
            } catch (exception: CancellationException) {
                throw exception
            } catch (_: Exception) {
                NormalizationUiState.Error(
                    "The compatible copy could not be completed. The original is unchanged.",
                )
            }
        }
    }

    fun cancelNormalization() {
        normalizationJob?.cancel()
        mutableNormalizationState.value = NormalizationUiState.Idle
    }

    private companion object {
        const val SELECTED_URI_KEY = "selected_uri"
    }
}
