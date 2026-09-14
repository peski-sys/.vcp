package dev.compatvideo.normalization

import dev.compatvideo.inspection.ColorValue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputValidatorTest {
    @Test
    fun `accepts verified HEVC HLG base-layer output`() {
        val result = OutputValidator.validate(
            source = knownDolbyVisionFacts(),
            output = workingHlgFacts(),
        )

        assertTrue(result.problems.joinToString(), result.isValid)
    }

    @Test
    fun `rejects output that retains Dolby Vision configuration`() {
        val output = workingHlgFacts().copy(
            container = workingContainer().copy(
                configurationBoxes = listOf("hvcC", "dvvC"),
                dolbyVision = knownDolbyVisionFacts().container.dolbyVision,
            ),
        )

        val result = OutputValidator.validate(knownDolbyVisionFacts(), output)

        assertFalse(result.isValid)
        assertTrue(result.problems.any { it.contains("Dolby Vision") })
    }

    @Test
    fun `rejects output that loses HLG transfer signaling`() {
        val output = workingHlgFacts().copy(
            videoTracks = listOf(hevcTrack(colorTransfer = ColorValue(3, "SDR"))),
        )

        val result = OutputValidator.validate(knownDolbyVisionFacts(), output)

        assertFalse(result.isValid)
        assertTrue(result.problems.any { it.contains("HDR transfer") })
    }

    @Test
    fun `rejects output that changes audio channels`() {
        val output = workingHlgFacts(audioTracks = listOf(aacTrack(channelCount = 1)))

        val result = OutputValidator.validate(knownDolbyVisionFacts(), output)

        assertFalse(result.isValid)
        assertTrue(result.problems.any { it.contains("channel count") })
    }

    @Test
    fun `allows only small container duration drift`() {
        val output = workingHlgFacts().copy(
            videoTracks = listOf(hevcTrack(durationUs = 2_300_001)),
        )

        val result = OutputValidator.validate(knownDolbyVisionFacts(), output)

        assertFalse(result.isValid)
        assertTrue(result.problems.any { it.contains("duration") })
    }
}
