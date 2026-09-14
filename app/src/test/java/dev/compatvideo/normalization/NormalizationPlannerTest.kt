package dev.compatvideo.normalization

import dev.compatvideo.inspection.ColorValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NormalizationPlannerTest {
    @Test
    fun `plans exact profile 8 HLG base-layer stream copy`() {
        val plan = NormalizationPlanner.plan(knownDolbyVisionFacts())

        assertEquals("IMG_6995_compatible.mp4", plan?.outputDisplayName)
    }

    @Test
    fun `rejects unproven profile 8 compatibility id`() {
        assertNull(NormalizationPlanner.plan(knownDolbyVisionFacts(compatibilityId = 1)))
    }

    @Test
    fun `rejects enhancement-layer Dolby Vision`() {
        assertNull(
            NormalizationPlanner.plan(
                knownDolbyVisionFacts(enhancementLayerPresent = true),
            ),
        )
    }

    @Test
    fun `rejects multiple audio tracks until preservation is supported`() {
        val facts = knownDolbyVisionFacts(audioTracks = listOf(aacTrack(), aacTrack()))

        assertNull(NormalizationPlanner.plan(facts))
    }

    @Test
    fun `rejects base layer whose HLG signaling is missing`() {
        val facts = knownDolbyVisionFacts().copy(
            videoTracks = listOf(hevcTrack(colorTransfer = ColorValue(3, "SDR"))),
        )

        assertNull(NormalizationPlanner.plan(facts))
    }

    @Test
    fun `uses a safe fallback for an unusable filename`() {
        assertEquals(
            "video_compatible.mp4",
            NormalizationPlanner.compatibleOutputName("<>:\\/?*.mov"),
        )
    }
}
