package dev.compatvideo.normalization

import dev.compatvideo.inspection.AudioTrackInfo
import dev.compatvideo.inspection.ColorValue
import dev.compatvideo.inspection.ContainerInfo
import dev.compatvideo.inspection.ContainerScanStatus
import dev.compatvideo.inspection.DolbyVisionConfig
import dev.compatvideo.inspection.MediaFacts
import dev.compatvideo.inspection.SourceInfo
import dev.compatvideo.inspection.VideoTrackInfo

internal fun knownDolbyVisionFacts(
    compatibilityId: Int = 4,
    enhancementLayerPresent: Boolean = false,
    audioTracks: List<AudioTrackInfo> = listOf(aacTrack()),
): MediaFacts = workingHlgFacts(audioTracks = audioTracks).copy(
    source = sourceInfo(displayName = "IMG:6995?.MOV"),
    container = workingContainer().copy(
        configurationBoxes = listOf("hvcC", "dvvC"),
        dolbyVision = DolbyVisionConfig(
            boxType = "dvvC",
            versionMajor = 1,
            versionMinor = 0,
            profile = 8,
            level = 5,
            rpuPresent = true,
            enhancementLayerPresent = enhancementLayerPresent,
            baseLayerPresent = true,
            baseLayerSignalCompatibilityId = compatibilityId,
        ),
    ),
)

internal fun workingHlgFacts(
    audioTracks: List<AudioTrackInfo> = listOf(aacTrack()),
): MediaFacts = MediaFacts(
    source = sourceInfo(displayName = "IMG_6995_compatible.mp4"),
    container = workingContainer(),
    videoTracks = listOf(hevcTrack()),
    audioTracks = audioTracks,
    otherTrackCount = 0,
    notes = emptyList(),
)

internal fun sourceInfo(
    displayName: String,
    sizeBytes: Long? = 8_000_000L,
) = SourceInfo(
    displayName = displayName,
    mimeType = "video/mp4",
    sizeBytes = sizeBytes,
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
)

internal fun workingContainer() = ContainerInfo(
    extractorMimeType = "video/mp4",
    majorBrand = "isom",
    minorVersion = 512,
    compatibleBrands = listOf("isom", "iso2", "mp41"),
    sampleEntries = listOf("hvc1"),
    configurationBoxes = listOf("hvcC"),
    dolbyVision = null,
    hevcConfigurations = emptyList(),
    scanStatus = ContainerScanStatus.COMPLETE,
)

internal fun hevcTrack(
    colorTransfer: ColorValue? = ColorValue(7, "HLG"),
    rotationDegrees: Int? = 90,
    durationUs: Long? = 2_000_000,
) = VideoTrackInfo(
    index = 0,
    mimeType = "video/hevc",
    codecString = null,
    profileValue = 2,
    profileName = "HEVC Main 10",
    levelValue = null,
    levelName = null,
    width = 1_920,
    height = 1_080,
    rotationDegrees = rotationDegrees,
    frameRate = 60.0,
    timing = null,
    bitRate = 10_000_000,
    durationUs = durationUs,
    bitDepth = 10,
    colorStandard = ColorValue(6, "BT.2020"),
    colorTransfer = colorTransfer,
    colorRange = ColorValue(2, "Limited"),
    hasHdrStaticInfo = false,
    hasHdr10PlusInfo = false,
)

internal fun aacTrack(
    channelCount: Int? = 2,
    durationUs: Long? = 2_000_000,
) = AudioTrackInfo(
    index = 1,
    mimeType = "audio/mp4a-latm",
    codecName = "AAC",
    codecString = null,
    profileValue = 2,
    sampleRateHz = 48_000,
    channelCount = channelCount,
    channelLayout = "Stereo (L R)",
    bitRate = 192_000,
    durationUs = durationUs,
    language = "und",
)
