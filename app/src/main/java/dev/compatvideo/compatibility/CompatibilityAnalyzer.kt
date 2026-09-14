package dev.compatvideo.compatibility

import android.media.MediaFormat
import dev.compatvideo.inspection.AssessmentConfidence
import dev.compatvideo.inspection.CompatibilityAssessment
import dev.compatvideo.inspection.CompatibilityVerdict
import dev.compatvideo.inspection.ContainerScanStatus
import dev.compatvideo.inspection.MediaFacts

object CompatibilityAnalyzer {
    fun analyze(facts: MediaFacts): CompatibilityAssessment {
        val dolbyVision = facts.container.dolbyVision
        if (
            dolbyVision?.profile == 8 &&
            dolbyVision.rpuPresent &&
            dolbyVision.baseLayerPresent &&
            !dolbyVision.enhancementLayerPresent &&
            dolbyVision.baseLayerSignalCompatibilityId == 4
        ) {
            return CompatibilityAssessment(
                verdict = CompatibilityVerdict.NORMALIZATION_RECOMMENDED,
                title = "Known Pixel compatibility trigger detected",
                explanation = buildString {
                    append("This video carries Dolby Vision Profile 8 dynamic metadata over a backward-compatible base layer. ")
                    append("That signaling matches the iPhone HDR files confirmed to play but lose thumbnails and editing in Google Photos on the test Pixel 10.")
                },
                evidence = buildList {
                    add("${dolbyVision.codecString} in ${dolbyVision.boxType}")
                    add("RPU present: yes; base layer present: yes")
                    add("Base-layer compatibility ID 4: HLG-compatible")
                },
                suggestedStrategy = "Lossless remux of the HEVC/HLG base layer while omitting the incompatible Dolby Vision container declaration. Keep the original.",
                confidence = AssessmentConfidence.CONFIRMED_ON_TEST_DEVICE,
            )
        }

        val platformReportsDolbyVision = facts.videoTracks.any {
            it.mimeType == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION
        }
        val dolbySampleEntry = facts.container.sampleEntries.any {
            it in setOf("dvav", "dva1", "dvhe", "dvh1")
        }
        if (dolbyVision != null || platformReportsDolbyVision || dolbySampleEntry) {
            return CompatibilityAssessment(
                verdict = CompatibilityVerdict.INCONCLUSIVE,
                title = "Dolby Vision detected; profile needs review",
                explanation = "Dolby Vision is present, but it is not the exact Profile 8 base-layer case currently proven on the test device.",
                evidence = buildList {
                    dolbyVision?.let { add("${it.codecString} in ${it.boxType}") }
                    if (platformReportsDolbyVision) add("Android extractor MIME: video/dolby-vision")
                    if (dolbySampleEntry) add("Dolby Vision sample entry is present")
                },
                suggestedStrategy = "Do not modify automatically. Preserve the original and add a format-specific test before normalization.",
                confidence = AssessmentConfidence.LIMITED,
            )
        }

        val mainVideo = facts.videoTracks.firstOrNull()
        if (mainVideo == null) {
            return CompatibilityAssessment(
                verdict = CompatibilityVerdict.INCONCLUSIVE,
                title = "No video stream found",
                explanation = "The selected item did not expose a video track through Android's media extractor.",
                evidence = emptyList(),
                suggestedStrategy = "Leave the item untouched.",
                confidence = AssessmentConfidence.HIGH,
            )
        }

        val scanIncomplete = facts.container.scanStatus in setOf(
            ContainerScanStatus.UNAVAILABLE,
            ContainerScanStatus.METADATA_TOO_LARGE,
        )
        if (scanIncomplete) {
            return CompatibilityAssessment(
                verdict = CompatibilityVerdict.INCONCLUSIVE,
                title = "Inspection is incomplete",
                explanation = "Android exposed the media tracks, but the container-level Dolby Vision check could not be completed.",
                evidence = listOfNotNull(
                    "Video MIME: ${mainVideo.mimeType}",
                    mainVideo.profileName,
                ),
                suggestedStrategy = "Do nothing automatically. Re-select a local copy if this file shows the thumbnail/editing failure.",
                confidence = AssessmentConfidence.LIMITED,
            )
        }

        val hlgBaseControl = mainVideo.mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC &&
            mainVideo.bitDepth == 10 &&
            mainVideo.colorTransfer?.label == "HLG" &&
            facts.container.configurationBoxes.none { it in setOf("dvcC", "dvvC", "dvwC") }
        if (hlgBaseControl) {
            return CompatibilityAssessment(
                verdict = CompatibilityVerdict.NO_KNOWN_TRIGGER,
                title = "No known trigger detected",
                explanation = "This is 10-bit HEVC/HLG without a Dolby Vision container declaration—the same base format that worked in the controlled Pixel 10 test.",
                evidence = listOf(
                    "HEVC 10-bit",
                    "HLG transfer",
                    "No Dolby Vision configuration box found",
                ),
                suggestedStrategy = "Do nothing. Avoid an unnecessary remux or transcode.",
                confidence = AssessmentConfidence.CONFIRMED_ON_TEST_DEVICE,
            )
        }

        return CompatibilityAssessment(
            verdict = CompatibilityVerdict.NO_KNOWN_TRIGGER,
            title = "No known trigger detected",
            explanation = "The specific Dolby Vision Profile 8 trigger proven in the Pixel 10 test was not found. This is not a universal Google Photos compatibility guarantee.",
            evidence = listOfNotNull(
                "Video MIME: ${mainVideo.mimeType}",
                mainVideo.profileName,
            ),
            suggestedStrategy = "Do nothing unless the file actually shows the thumbnail/editing failure.",
            confidence = AssessmentConfidence.HIGH,
        )
    }
}
