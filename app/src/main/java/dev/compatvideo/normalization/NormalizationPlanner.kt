package dev.compatvideo.normalization

import dev.compatvideo.inspection.ContainerScanStatus
import dev.compatvideo.inspection.MediaFacts

object NormalizationPlanner {
    fun plan(facts: MediaFacts): NormalizationPlan? {
        val dolbyVision = facts.container.dolbyVision ?: return null
        val baseLayer = facts.videoTracks.firstOrNull { it.mimeType == MIME_HEVC }
        val exactKnownCase = facts.container.scanStatus == ContainerScanStatus.COMPLETE &&
            dolbyVision.profile == 8 &&
            dolbyVision.rpuPresent &&
            dolbyVision.baseLayerPresent &&
            !dolbyVision.enhancementLayerPresent &&
            dolbyVision.baseLayerSignalCompatibilityId == 4 &&
            "hvc1" in facts.container.sampleEntries &&
            "hvcC" in facts.container.configurationBoxes &&
            baseLayer?.bitDepth == 10 &&
            baseLayer.colorStandard?.label == "BT.2020" &&
            baseLayer.colorTransfer?.label == "HLG" &&
            facts.audioTracks.size <= 1 &&
            facts.audioTracks.all { it.mimeType == MIME_AAC }
        if (!exactKnownCase) return null

        return NormalizationPlan(
            outputDisplayName = compatibleOutputName(facts.source.displayName),
        )
    }

    fun compatibleOutputName(sourceDisplayName: String): String {
        val withoutExtension = sourceDisplayName.substringBeforeLast('.', sourceDisplayName)
        val safeBase = withoutExtension
            .map { character ->
                when {
                    character.code < 32 -> '_'
                    character in INVALID_FILENAME_CHARACTERS -> '_'
                    else -> character
                }
            }
            .joinToString(separator = "")
            .replace(Regex("_+"), "_")
            .trim(' ', '.', '_')
            .take(MAX_BASE_NAME_LENGTH)
            .ifBlank { "video" }
        return "${safeBase}_compatible.mp4"
    }

    private const val MIME_HEVC = "video/hevc"
    private const val MIME_AAC = "audio/mp4a-latm"
    private const val MAX_BASE_NAME_LENGTH = 120
    private val INVALID_FILENAME_CHARACTERS = setOf('/', '\\', ':', '*', '?', '"', '<', '>', '|')
}
