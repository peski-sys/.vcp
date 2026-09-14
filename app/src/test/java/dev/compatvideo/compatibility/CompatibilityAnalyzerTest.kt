package dev.compatvideo.compatibility

import dev.compatvideo.inspection.AudioTrackInfo
import dev.compatvideo.inspection.AssessmentConfidence
import dev.compatvideo.inspection.ColorValue
import dev.compatvideo.inspection.CompatibilityVerdict
import dev.compatvideo.inspection.ContainerInfo
import dev.compatvideo.inspection.ContainerScanStatus
import dev.compatvideo.inspection.DolbyVisionConfig
import dev.compatvideo.inspection.MediaFacts
import dev.compatvideo.inspection.SourceInfo
import dev.compatvideo.inspection.VideoTrackInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompatibilityAnalyzerTest {
    @Test
    fun `recommends lossless normalization for proven profile 8 point 4 case`() {
        val facts = baseFacts().copy(
            container = baseContainer().copy(
                configurationBoxes = listOf("hvcC", "dvvC"),
                dolbyVision = DolbyVisionConfig(
                    boxType = "dvvC",
                    versionMajor = 1,
                    versionMinor = 0,
                    profile = 8,
                    level = 5,
                    rpuPresent = true,
                    enhancementLayerPresent = false,
                    baseLayerPresent = true,
                    baseLayerSignalCompatibilityId = 4,
                ),
            ),
        )

        val assessment = CompatibilityAnalyzer.analyze(facts)

        assertEquals(CompatibilityVerdict.NORMALIZATION_RECOMMENDED, assessment.verdict)
        assertEquals(AssessmentConfidence.CONFIRMED_ON_TEST_DEVICE, assessment.confidence)
        assertTrue(assessment.suggestedStrategy.contains("Lossless remux"))
        assertTrue(assessment.suggestedStrategy.contains("Keep the original"))
    }

    @Test
    fun `does nothing to working ten bit HLG base control`() {
        val facts = baseFacts()

        val assessment = CompatibilityAnalyzer.analyze(facts)

        assertEquals(CompatibilityVerdict.NO_KNOWN_TRIGGER, assessment.verdict)
        assertEquals(AssessmentConfidence.CONFIRMED_ON_TEST_DEVICE, assessment.confidence)
        assertTrue(assessment.suggestedStrategy.startsWith("Do nothing"))
    }

    @Test
    fun `does not recommend unproven non HLG profile 8`() {
        val facts = baseFacts().copy(
            container = baseContainer().copy(
                configurationBoxes = listOf("hvcC", "dvvC"),
                dolbyVision = DolbyVisionConfig(
                    boxType = "dvvC",
                    versionMajor = 1,
                    versionMinor = 0,
                    profile = 8,
                    level = 5,
                    rpuPresent = true,
                    enhancementLayerPresent = false,
                    baseLayerPresent = true,
                    baseLayerSignalCompatibilityId = 1,
                ),
            ),
        )

        val assessment = CompatibilityAnalyzer.analyze(facts)

        assertEquals(CompatibilityVerdict.INCONCLUSIVE, assessment.verdict)
    }

    @Test
    fun `does not claim compatibility when container scan is unavailable`() {
        val facts = baseFacts().copy(
            container = baseContainer().copy(scanStatus = ContainerScanStatus.UNAVAILABLE),
        )

        val assessment = CompatibilityAnalyzer.analyze(facts)

        assertEquals(CompatibilityVerdict.INCONCLUSIVE, assessment.verdict)
        assertEquals(AssessmentConfidence.LIMITED, assessment.confidence)
    }

    private fun baseFacts() = MediaFacts(
        source = SourceInfo(
            displayName = "test.mov",
            mimeType = "video/quicktime",
            sizeBytes = 1_000,
            uriAuthority = "media",
            durationMs = 2_000,
            overallBitRate = 10_000_000,
            videoFrameCount = 120,
            width = 1_920,
            height = 1_080,
            dateAddedEpochSeconds = null,
            dateModifiedEpochSeconds = null,
            relativePath = null,
            generationAdded = null,
            generationModified = null,
        ),
        container = baseContainer(),
        videoTracks = listOf(
            VideoTrackInfo(
                index = 0,
                mimeType = "video/hevc",
                codecString = null,
                profileValue = 2,
                profileName = "HEVC Main 10",
                levelValue = null,
                levelName = null,
                width = 1_920,
                height = 1_080,
                rotationDegrees = 90,
                frameRate = 60.0,
                timing = null,
                bitRate = 10_000_000,
                durationUs = 2_000_000,
                bitDepth = 10,
                colorStandard = ColorValue(6, "BT.2020"),
                colorTransfer = ColorValue(7, "HLG"),
                colorRange = ColorValue(2, "Limited"),
                hasHdrStaticInfo = false,
                hasHdr10PlusInfo = false,
            ),
        ),
        audioTracks = emptyList<AudioTrackInfo>(),
        otherTrackCount = 0,
        notes = emptyList(),
    )

    private fun baseContainer() = ContainerInfo(
        extractorMimeType = "video/quicktime",
        majorBrand = "qt  ",
        minorVersion = 0,
        compatibleBrands = listOf("qt  "),
        sampleEntries = listOf("hvc1"),
        configurationBoxes = listOf("hvcC"),
        dolbyVision = null,
        hevcConfigurations = emptyList(),
        scanStatus = ContainerScanStatus.COMPLETE,
    )
}
