package dev.compatvideo.normalization

import dev.compatvideo.inspection.ContainerScanStatus
import dev.compatvideo.inspection.MediaFacts
import dev.compatvideo.inspection.VideoTrackInfo
import kotlin.math.abs

data class OutputValidationResult(
    val isValid: Boolean,
    val problems: List<String>,
)

object OutputValidator {
    fun validate(source: MediaFacts, output: MediaFacts): OutputValidationResult {
        val problems = buildList {
            if (output.source.sizeBytes == null || output.source.sizeBytes <= 0) {
                add("The output file is empty.")
            }
            if (output.container.scanStatus != ContainerScanStatus.COMPLETE) {
                add("The output MP4 container could not be checked completely.")
            }
            if (output.container.dolbyVision != null) {
                add("Dolby Vision configuration is still present.")
            }
            if (output.container.configurationBoxes.any { it in DOLBY_VISION_BOXES }) {
                add("A Dolby Vision configuration box is still present.")
            }
            if (output.container.sampleEntries.any { it in DOLBY_VISION_SAMPLE_ENTRIES }) {
                add("A Dolby Vision sample entry is still present.")
            }
            if ("hvcC" !in output.container.configurationBoxes) {
                add("The output has no HEVC configuration record.")
            }
            if ("hvc1" !in output.container.sampleEntries) {
                add("The output does not use the broadly compatible hvc1 sample entry.")
            }

            val sourceVideo = source.preferredHevcTrack()
            val outputHevcTracks = output.videoTracks.filter { it.mimeType == MIME_HEVC }
            val outputVideo = outputHevcTracks.singleOrNull()
            if (sourceVideo == null) {
                add("The source HEVC base layer is unavailable.")
            }
            if (outputHevcTracks.size != 1) {
                add("The output does not contain exactly one HEVC video track.")
            }
            if (output.videoTracks.any { it.mimeType == MIME_DOLBY_VISION }) {
                add("Android still identifies the output as Dolby Vision.")
            }
            if (sourceVideo != null && outputVideo != null) {
                compareVideo(sourceVideo, outputVideo).forEach(::add)
                if (outputVideo.bitDepth != 10) {
                    add("The output is not 10-bit HEVC.")
                }
                if (outputVideo.colorStandard?.label != "BT.2020") {
                    add("The output does not preserve BT.2020 color primaries.")
                }
                if (outputVideo.colorTransfer?.label != "HLG") {
                    add("The output does not preserve HLG transfer signaling.")
                }
            }

            if (output.audioTracks.size != source.audioTracks.size) {
                add("The number of audio tracks changed.")
            } else {
                source.audioTracks.zip(output.audioTracks).forEachIndexed { index, (before, after) ->
                    if (before.mimeType != after.mimeType) {
                        add("Audio track ${index + 1} codec changed.")
                    }
                    if (before.sampleRateHz != null && before.sampleRateHz != after.sampleRateHz) {
                        add("Audio track ${index + 1} sample rate changed.")
                    }
                    if (before.channelCount != null && before.channelCount != after.channelCount) {
                        add("Audio track ${index + 1} channel count changed.")
                    }
                    if (!durationsMatch(before.durationUs, after.durationUs)) {
                        add("Audio track ${index + 1} duration changed unexpectedly.")
                    }
                }
            }
        }
        return OutputValidationResult(
            isValid = problems.isEmpty(),
            problems = problems,
        )
    }

    private fun compareVideo(
        source: VideoTrackInfo,
        output: VideoTrackInfo,
    ): List<String> = buildList {
        if (source.width != output.width || source.height != output.height) {
            add("Video dimensions changed.")
        }
        if (source.rotationDegrees != output.rotationDegrees) {
            add("Video orientation changed.")
        }
        if (source.bitDepth != null && source.bitDepth != output.bitDepth) {
            add("Video bit depth changed.")
        }
        if (source.colorStandard != null && source.colorStandard?.label != output.colorStandard?.label) {
            add("Video color primaries changed or were lost.")
        }
        if (source.colorTransfer != null && source.colorTransfer?.label != output.colorTransfer?.label) {
            add("Video HDR transfer signaling changed or was lost.")
        }
        if (source.colorRange != null && source.colorRange?.label != output.colorRange?.label) {
            add("Video color range changed or was lost.")
        }
        if (!durationsMatch(source.durationUs, output.durationUs)) {
            add("Video duration changed unexpectedly.")
        }
    }

    private fun MediaFacts.preferredHevcTrack(): VideoTrackInfo? =
        videoTracks.firstOrNull { it.mimeType == MIME_HEVC }

    private fun durationsMatch(beforeUs: Long?, afterUs: Long?): Boolean {
        if (beforeUs == null || afterUs == null) return true
        return abs(beforeUs - afterUs) <= MAX_DURATION_DELTA_US
    }

    private const val MIME_HEVC = "video/hevc"
    private const val MIME_DOLBY_VISION = "video/dolby-vision"
    private const val MAX_DURATION_DELTA_US = 250_000L
    private val DOLBY_VISION_BOXES = setOf("dvcC", "dvvC", "dvwC")
    private val DOLBY_VISION_SAMPLE_ENTRIES = setOf("dvav", "dva1", "dvhe", "dvh1")
}
