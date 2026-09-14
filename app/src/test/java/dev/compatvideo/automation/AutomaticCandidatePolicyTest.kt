package dev.compatvideo.automation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticCandidatePolicyTest {
    @Test
    fun `accepts only video added after opt-in baseline`() {
        assertTrue(
            AutomaticCandidatePolicy.shouldInspect(
                candidate = candidate(generationAdded = 101),
                baselineGeneration = 100,
                ownPackageName = OWN_PACKAGE,
            ),
        )
        assertFalse(
            AutomaticCandidatePolicy.shouldInspect(
                candidate = candidate(generationAdded = 100),
                baselineGeneration = 100,
                ownPackageName = OWN_PACKAGE,
            ),
        )
    }

    @Test
    fun `rejects output owned by this app`() {
        assertFalse(
            AutomaticCandidatePolicy.shouldInspect(
                candidate = candidate(ownerPackageName = OWN_PACKAGE),
                baselineGeneration = 100,
                ownPackageName = OWN_PACKAGE,
            ),
        )
    }

    @Test
    fun `rejects compatible output directory regardless of trailing slash`() {
        assertFalse(
            AutomaticCandidatePolicy.shouldInspect(
                candidate = candidate(relativePath = "Movies/vcp/"),
                baselineGeneration = 100,
                ownPackageName = OWN_PACKAGE,
            ),
        )
        assertFalse(
            AutomaticCandidatePolicy.shouldInspect(
                candidate = candidate(relativePath = "Movies/Compat Video/"),
                baselineGeneration = 100,
                ownPackageName = OWN_PACKAGE,
            ),
        )
    }

    @Test
    fun `allows another app new video outside output directory`() {
        assertTrue(
            AutomaticCandidatePolicy.shouldInspect(
                candidate = candidate(
                    ownerPackageName = "com.example.transfer",
                    relativePath = "Download/Blip/",
                ),
                baselineGeneration = 100,
                ownPackageName = OWN_PACKAGE,
            ),
        )
    }

    private fun candidate(
        generationAdded: Long = 101,
        ownerPackageName: String? = "com.example.transfer",
        relativePath: String? = "Download/",
    ) = AutomaticVideoCandidate(
        id = 7,
        generationAdded = generationAdded,
        generationModified = 102,
        ownerPackageName = ownerPackageName,
        relativePath = relativePath,
    )

    private companion object {
        const val OWN_PACKAGE = "dev.compatvideo.debug"
    }
}
