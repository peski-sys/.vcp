package dev.compatvideo.automation

internal data class AutomaticVideoCandidate(
    val id: Long,
    val generationAdded: Long,
    val generationModified: Long,
    val ownerPackageName: String?,
    val relativePath: String?,
)

internal object AutomaticCandidatePolicy {
    fun shouldInspect(
        candidate: AutomaticVideoCandidate,
        baselineGeneration: Long,
        ownPackageName: String,
    ): Boolean {
        if (candidate.generationAdded <= baselineGeneration) return false
        if (candidate.ownerPackageName == ownPackageName) return false
        val normalizedPath = candidate.relativePath?.trimEnd('/')
        if (OUTPUT_DIRECTORIES.any { normalizedPath.equals(it, ignoreCase = true) }) return false
        return true
    }

    private val OUTPUT_DIRECTORIES = setOf("Movies/vcp", "Movies/Compat Video")
}
