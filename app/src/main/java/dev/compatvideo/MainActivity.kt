package dev.compatvideo

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePaddingRelative
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.media3.common.util.UnstableApi
import dev.compatvideo.automation.AutomaticModeState
import dev.compatvideo.databinding.ActivityMainBinding
import dev.compatvideo.inspection.CompatibilityVerdict
import dev.compatvideo.normalization.NormalizationStage
import dev.compatvideo.normalization.NormalizationPlanner
import dev.compatvideo.reminder.OriginalReminderState
import kotlinx.coroutines.launch

@UnstableApi
class MainActivity : ComponentActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: InspectionViewModel by viewModels()
    private var currentOutputUri: Uri? = null
    private var canNormalize = false
    private var currentNormalizationState: NormalizationUiState = NormalizationUiState.Idle
    private var currentAutomaticModeState: AutomaticModeState? = null
    private var renderingSwitches = false

    private val videoPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@registerForActivityResult
        runCatching {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        viewModel.inspect(uri)
    }

    private val automaticPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ ->
        viewModel.enableAutomaticMode()
    }

    private val reminderPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.setOriginalReminderEnabled(true)
        } else {
            viewModel.setOriginalReminderEnabled(false)
            Toast.makeText(
                this,
                R.string.original_reminder_permission_denied,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemInsets()

        binding.selectVideoButton.setOnClickListener { openVideoPicker() }
        binding.selectAnotherButton.setOnClickListener { openVideoPicker() }
        binding.createCompatibleButton.setOnClickListener { viewModel.createCompatibleCopy() }
        binding.cancelNormalizationButton.setOnClickListener { viewModel.cancelNormalization() }
        binding.retryNormalizationButton.setOnClickListener { viewModel.createCompatibleCopy() }
        binding.openCompatibleButton.setOnClickListener { openCompatibleCopy() }
        binding.automaticModeSwitch.setOnCheckedChangeListener { _, checked ->
            if (!renderingSwitches) handleAutomaticModeToggle(checked)
        }
        binding.originalReminderSwitch.setOnCheckedChangeListener { _, checked ->
            if (!renderingSwitches) handleReminderToggle(checked)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { viewModel.state.collect(::render) }
                launch { viewModel.normalizationState.collect(::renderNormalization) }
                launch { viewModel.automaticModeState.collect(::renderAutomaticMode) }
                launch { viewModel.originalReminderState.collect(::renderOriginalReminder) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshAutomaticMode()
        viewModel.refreshOriginalReminder()
    }

    private fun handleAutomaticModeToggle(enable: Boolean) {
        val state = currentAutomaticModeState ?: return
        when {
            !enable -> viewModel.disableAutomaticMode()
            !state.supported -> Unit
            state.hasFullVideoAccess -> viewModel.enableAutomaticMode()
            else -> {
                val permissions = viewModel.requiredAutomaticPermissions()
                if (permissions.isEmpty()) {
                    viewModel.automaticPermissionWasNotGranted()
                } else {
                    automaticPermissionRequest.launch(permissions)
                }
            }
        }
    }

    private fun handleReminderToggle(enable: Boolean) {
        if (!enable) {
            viewModel.setOriginalReminderEnabled(false)
            return
        }
        val permission = viewModel.requiredReminderPermission()
        if (permission == null) {
            viewModel.setOriginalReminderEnabled(true)
        } else {
            renderingSwitches = true
            binding.originalReminderSwitch.isChecked = false
            renderingSwitches = false
            reminderPermissionRequest.launch(permission)
        }
    }

    private fun renderAutomaticMode(state: AutomaticModeState) {
        currentAutomaticModeState = state
        val primaryMessage = when {
            !state.supported -> getString(R.string.automatic_mode_unsupported)
            state.active -> getString(R.string.automatic_mode_on)
            !state.hasFullVideoAccess -> getString(R.string.automatic_mode_permission)
            else -> getString(R.string.automatic_mode_off)
        }
        binding.automaticModeBody.text = state.lastStatus?.takeIf { state.active }?.let { status ->
            getString(R.string.automatic_mode_status, primaryMessage, status)
        } ?: primaryMessage
        renderingSwitches = true
        binding.automaticModeSwitch.isChecked = state.active
        binding.automaticModeSwitch.isEnabled = state.supported
        renderingSwitches = false
    }

    private fun renderOriginalReminder(state: OriginalReminderState) {
        renderingSwitches = true
        binding.originalReminderSwitch.isChecked = state.enabled
        renderingSwitches = false
    }

    private fun openVideoPicker() {
        videoPicker.launch(arrayOf("video/*"))
    }

    override fun onStop() {
        if (!isChangingConfigurations && currentNormalizationState is NormalizationUiState.Running) {
            viewModel.cancelNormalization()
        }
        super.onStop()
    }

    private fun render(state: InspectionUiState) {
        binding.loadingState.isVisible = state is InspectionUiState.Loading
        binding.errorState.isVisible = state is InspectionUiState.Error
        binding.resultContent.isVisible = state is InspectionUiState.Loaded
        canNormalize = state is InspectionUiState.Loaded &&
            state.inspection.assessment.verdict == CompatibilityVerdict.NORMALIZATION_RECOMMENDED &&
            NormalizationPlanner.plan(state.inspection.facts) != null
        binding.selectVideoButton.isVisible = state !is InspectionUiState.Loaded
        binding.selectVideoButton.isEnabled = state !is InspectionUiState.Loading
        binding.manualIntro.isVisible = state is InspectionUiState.Empty

        when (state) {
            InspectionUiState.Empty -> Unit
            is InspectionUiState.Loading -> Unit
            is InspectionUiState.Error -> {
                binding.errorMessage.text = state.message
            }

            is InspectionUiState.Loaded -> renderInspection(state)
        }
        renderNormalization(currentNormalizationState)
    }

    private fun renderInspection(state: InspectionUiState.Loaded) {
        binding.selectedFileName.text = state.inspection.facts.source.displayName
        val (title, body) = when (state.inspection.assessment.verdict) {
            CompatibilityVerdict.NORMALIZATION_RECOMMENDED -> Pair(
                R.string.assessment_fix_title,
                R.string.assessment_fix_body,
            )
            CompatibilityVerdict.NO_KNOWN_TRIGGER -> Pair(
                R.string.assessment_clear_title,
                R.string.assessment_clear_body,
            )
            CompatibilityVerdict.INCONCLUSIVE -> Pair(
                R.string.assessment_inconclusive_title,
                R.string.assessment_inconclusive_body,
            )
        }
        binding.assessmentTitle.setText(title)
        binding.assessmentBody.setText(body)
    }

    private fun renderNormalization(state: NormalizationUiState) {
        currentNormalizationState = state
        val isRunning = state is NormalizationUiState.Running
        binding.normalizationOffer.isVisible = canNormalize && state is NormalizationUiState.Idle
        binding.normalizationStatusPanel.isVisible = state !is NormalizationUiState.Idle
        binding.normalizationProgress.isVisible = isRunning
        binding.cancelNormalizationButton.isVisible = isRunning
        binding.openCompatibleButton.isVisible = state is NormalizationUiState.Success
        binding.retryNormalizationButton.isVisible = state is NormalizationUiState.Error
        binding.selectAnotherButton.isEnabled = !isRunning

        if (isRunning) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        when (state) {
            NormalizationUiState.Idle -> currentOutputUri = null
            is NormalizationUiState.Running -> {
                currentOutputUri = null
                val (title, body) = when (state.progress.stage) {
                    NormalizationStage.REMUXING -> R.string.remuxing_title to R.string.remuxing_body
                    NormalizationStage.SAVING -> R.string.saving_title to R.string.saving_body
                    NormalizationStage.VALIDATING -> R.string.validating_title to R.string.validating_body
                }
                binding.normalizationStatusTitle.setText(title)
                binding.normalizationStatusBody.text = buildString {
                    append(getString(body))
                    state.progress.percent?.let { append(" $it%") }
                }
                binding.normalizationProgress.isIndeterminate = state.progress.percent == null
                state.progress.percent?.let { binding.normalizationProgress.progress = it }
            }

            is NormalizationUiState.Success -> {
                currentOutputUri = state.outcome.outputUri
                binding.normalizationStatusTitle.setText(R.string.normalization_success_title)
                binding.normalizationStatusBody.text = getString(
                    R.string.normalization_success_body,
                    state.outcome.outputDisplayName,
                )
            }

            is NormalizationUiState.Error -> {
                currentOutputUri = null
                binding.normalizationStatusTitle.setText(R.string.normalization_error_title)
                binding.normalizationStatusBody.text = getString(
                    R.string.normalization_error_body,
                    state.message,
                )
            }
        }
    }

    private fun openCompatibleCopy() {
        val uri = currentOutputUri ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/mp4")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.no_video_app, Toast.LENGTH_SHORT).show()
        }
    }

    private fun applySystemInsets() {
        val initialStart = binding.rootContent.paddingStart
        val initialTop = binding.rootContent.paddingTop
        val initialEnd = binding.rootContent.paddingEnd
        val initialBottom = binding.rootContent.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val bars = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
            )
            val isRightToLeft = binding.rootContent.layoutDirection == View.LAYOUT_DIRECTION_RTL
            binding.rootContent.updatePaddingRelative(
                start = initialStart + if (isRightToLeft) bars.right else bars.left,
                top = initialTop + bars.top,
                end = initialEnd + if (isRightToLeft) bars.left else bars.right,
                bottom = initialBottom + bars.bottom,
            )
            windowInsets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }
}
