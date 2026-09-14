package dev.compatvideo.inspection

data class MediaFacts(
    val source: SourceInfo,
    val container: ContainerInfo,
    val videoTracks: List<VideoTrackInfo>,
    val audioTracks: List<AudioTrackInfo>,
    val otherTrackCount: Int,
    val notes: List<String>,
)

data class MediaInspection(
    val facts: MediaFacts,
    val assessment: CompatibilityAssessment,
)

data class SourceInfo(
    val displayName: String,
    val mimeType: String?,
    val sizeBytes: Long?,
    val uriAuthority: String?,
    val durationMs: Long?,
    val overallBitRate: Long?,
    val videoFrameCount: Long?,
    val width: Int?,
    val height: Int?,
    val dateAddedEpochSeconds: Long?,
    val dateModifiedEpochSeconds: Long?,
    val relativePath: String?,
    val generationAdded: Long?,
    val generationModified: Long?,
)

data class ContainerInfo(
    val extractorMimeType: String?,
    val majorBrand: String?,
    val minorVersion: Long?,
    val compatibleBrands: List<String>,
    val sampleEntries: List<String>,
    val configurationBoxes: List<String>,
    val dolbyVision: DolbyVisionConfig?,
    val hevcConfigurations: List<HevcConfig>,
    val scanStatus: ContainerScanStatus,
)

enum class ContainerScanStatus {
    COMPLETE,
    NOT_ISO_BASE_MEDIA,
    UNAVAILABLE,
    METADATA_TOO_LARGE,
}

data class DolbyVisionConfig(
    val boxType: String,
    val versionMajor: Int,
    val versionMinor: Int,
    val profile: Int,
    val level: Int,
    val rpuPresent: Boolean,
    val enhancementLayerPresent: Boolean,
    val baseLayerPresent: Boolean,
    val baseLayerSignalCompatibilityId: Int?,
) {
    val codecString: String
        get() = "dvhe.%02d.%02d".format(profile, level)

    val isHlgCompatibleProfile8: Boolean
        get() = profile == 8 && baseLayerSignalCompatibilityId == 4
}

data class HevcConfig(
    val boxType: String,
    val profileIdc: Int,
    val levelIdc: Int,
    val chromaFormatIdc: Int,
    val bitDepthLuma: Int,
    val bitDepthChroma: Int,
)

data class VideoTrackInfo(
    val index: Int,
    val mimeType: String,
    val codecString: String?,
    val profileValue: Int?,
    val profileName: String?,
    val levelValue: Int?,
    val levelName: String?,
    val width: Int?,
    val height: Int?,
    val rotationDegrees: Int?,
    val frameRate: Double?,
    val timing: FrameTimingInfo?,
    val bitRate: Long?,
    val durationUs: Long?,
    val bitDepth: Int?,
    val colorStandard: ColorValue?,
    val colorTransfer: ColorValue?,
    val colorRange: ColorValue?,
    val hasHdrStaticInfo: Boolean,
    val hasHdr10PlusInfo: Boolean,
)

data class AudioTrackInfo(
    val index: Int,
    val mimeType: String,
    val codecName: String,
    val codecString: String?,
    val profileValue: Int?,
    val sampleRateHz: Int?,
    val channelCount: Int?,
    val channelLayout: String?,
    val bitRate: Long?,
    val durationUs: Long?,
    val language: String?,
)

data class ColorValue(
    val rawValue: Int,
    val label: String,
)

data class FrameTimingInfo(
    val classification: FrameRateClassification,
    val averageFramesPerSecond: Double?,
    val minimumFramesPerSecond: Double?,
    val maximumFramesPerSecond: Double?,
    val sampledFrameCount: Int,
    val completeTrack: Boolean,
)

enum class FrameRateClassification {
    CONSTANT_SAMPLED,
    VARIABLE_SAMPLED,
    INSUFFICIENT_DATA,
}

data class CompatibilityAssessment(
    val verdict: CompatibilityVerdict,
    val title: String,
    val explanation: String,
    val evidence: List<String>,
    val suggestedStrategy: String,
    val confidence: AssessmentConfidence,
)

enum class CompatibilityVerdict {
    NORMALIZATION_RECOMMENDED,
    NO_KNOWN_TRIGGER,
    INCONCLUSIVE,
}

enum class AssessmentConfidence {
    CONFIRMED_ON_TEST_DEVICE,
    HIGH,
    LIMITED,
}
