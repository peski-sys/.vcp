package dev.compatvideo.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameTimingAnalyzerTest {
    @Test
    fun `recognizes constant sampled timing`() {
        val times = List(120) { index -> index * 16_667L }

        val result = FrameTimingAnalyzer.analyze(times, completeTrack = true)

        assertEquals(FrameRateClassification.CONSTANT_SAMPLED, result.classification)
        assertTrue(requireNotNull(result.averageFramesPerSecond) in 59.99..60.01)
        assertTrue(result.completeTrack)
    }

    @Test
    fun `recognizes variable sampled timing`() {
        var timestamp = 0L
        val times = buildList {
            repeat(120) { index ->
                add(timestamp)
                timestamp += if (index % 12 == 0) 18_333 else 16_667
            }
        }

        val result = FrameTimingAnalyzer.analyze(times, completeTrack = true)

        assertEquals(FrameRateClassification.VARIABLE_SAMPLED, result.classification)
        assertTrue(requireNotNull(result.minimumFramesPerSecond) < 55.0)
        assertTrue(requireNotNull(result.maximumFramesPerSecond) > 59.9)
    }

    @Test
    fun `reports insufficient data honestly`() {
        val result = FrameTimingAnalyzer.analyze(listOf(0L, 16_667L), completeTrack = true)

        assertEquals(FrameRateClassification.INSUFFICIENT_DATA, result.classification)
        assertEquals(null, result.averageFramesPerSecond)
    }
}
