package dev.compatvideo.normalization

import android.net.Uri

data class NormalizationPlan(
    val outputDisplayName: String,
)

enum class NormalizationStage {
    REMUXING,
    SAVING,
    VALIDATING,
}

data class NormalizationProgress(
    val stage: NormalizationStage,
    val percent: Int?,
)

data class NormalizationOutcome(
    val outputUri: Uri,
    val outputDisplayName: String,
    val relativePath: String,
    val summary: String,
)

class NormalizationException(
    val userFacingMessage: String,
    cause: Throwable? = null,
) : Exception(userFacingMessage, cause)
