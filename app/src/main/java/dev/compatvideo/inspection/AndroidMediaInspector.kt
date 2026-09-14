package dev.compatvideo.inspection

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import dev.compatvideo.compatibility.CompatibilityAnalyzer

class AndroidMediaInspector(
    context: Context,
) : MediaInspector {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val isoBmffReader = IsoBmffReader(contentResolver)

    override suspend fun inspect(uri: Uri): MediaInspection {
        val retriever = readRetrieverMetadata(uri)
        val source = readSourceInfo(uri, retriever)
        val container = isoBmffReader.inspect(uri).copy(
            extractorMimeType = retriever.mimeType,
        )
        val tracks = readTracks(uri, retriever, container)
        if (tracks.video.isEmpty()) {
            throw MediaInspectionException("Android did not find a video stream in the selected item.")
        }

        val notes = buildList {
            add("Track values are reported by Android's MediaExtractor; unavailable fields are shown honestly rather than guessed.")
            add("Frame-rate mode is estimated from at most $MAX_TIMING_SAMPLES presentation timestamps; encoded frames are not decoded.")
            when (container.scanStatus) {
                ContainerScanStatus.COMPLETE -> add("MP4/MOV box scan completed without reading encoded video payloads.")
                ContainerScanStatus.NOT_ISO_BASE_MEDIA -> add("This is not an ISO-BMFF/MP4/MOV container, so MP4 box details do not apply.")
                ContainerScanStatus.UNAVAILABLE -> add("The provider did not expose seekable container data; Dolby Vision box detection may be incomplete.")
                ContainerScanStatus.METADATA_TOO_LARGE -> add("The MP4/MOV metadata box exceeded the safe in-memory inspection limit.")
            }
            if (tracks.video.any { it.timing?.completeTrack == false }) {
                add("Frame timing was sampled, not scanned across the entire track.")
            }
            if (
                container.dolbyVision != null &&
                tracks.video.any { it.mimeType == MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION } &&
                tracks.video.any { it.mimeType == MediaFormat.MIMETYPE_VIDEO_HEVC }
            ) {
                add("Android exposed Dolby Vision and backward-compatible HEVC logical views of this Profile 8 stream; these do not necessarily represent two separately encoded videos.")
            }
        }

        val facts = MediaFacts(
            source = source,
            container = container,
            videoTracks = tracks.video,
            audioTracks = tracks.audio,
            otherTrackCount = tracks.otherCount,
            notes = notes,
        )
        return MediaInspection(
            facts = facts,
            assessment = CompatibilityAnalyzer.analyze(facts),
        )
    }

    private fun readSourceInfo(uri: Uri, retriever: RetrieverMetadata): SourceInfo {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Selected video"
        var sizeBytes: Long? = null
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.stringOrNull(OpenableColumns.DISPLAY_NAME)?.let { displayName = it }
                    sizeBytes = cursor.longOrNull(OpenableColumns.SIZE)
                }
            }
        }

        var mediaDuration: Long? = null
        var mediaWidth: Int? = null
        var mediaHeight: Int? = null
        var dateAdded: Long? = null
        var dateModified: Long? = null
        var relativePath: String? = null
        var generationAdded: Long? = null
        var generationModified: Long? = null

        if (uri.authority == MediaStore.AUTHORITY) {
            val projection = buildList {
                add(MediaStore.MediaColumns.DURATION)
                add(MediaStore.MediaColumns.WIDTH)
                add(MediaStore.MediaColumns.HEIGHT)
                add(MediaStore.MediaColumns.DATE_ADDED)
                add(MediaStore.MediaColumns.DATE_MODIFIED)
                add(MediaStore.MediaColumns.RELATIVE_PATH)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    add(MediaStore.MediaColumns.GENERATION_ADDED)
                    add(MediaStore.MediaColumns.GENERATION_MODIFIED)
                }
            }.toTypedArray()
            runCatching {
                contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        mediaDuration = cursor.longOrNull(MediaStore.MediaColumns.DURATION)
                        mediaWidth = cursor.intOrNull(MediaStore.MediaColumns.WIDTH)
                        mediaHeight = cursor.intOrNull(MediaStore.MediaColumns.HEIGHT)
                        dateAdded = cursor.longOrNull(MediaStore.MediaColumns.DATE_ADDED)
                        dateModified = cursor.longOrNull(MediaStore.MediaColumns.DATE_MODIFIED)
                        relativePath = cursor.stringOrNull(MediaStore.MediaColumns.RELATIVE_PATH)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            generationAdded = cursor.longOrNull(MediaStore.MediaColumns.GENERATION_ADDED)
                            generationModified = cursor.longOrNull(MediaStore.MediaColumns.GENERATION_MODIFIED)
                        }
                    }
                }
            }
        }

        if (sizeBytes == null) {
            sizeBytes = runCatching {
                contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                    descriptor.statSize.takeIf { it >= 0 }
                }
            }.getOrNull()
        }

        return SourceInfo(
            displayName = displayName,
            mimeType = contentResolver.getType(uri) ?: retriever.mimeType,
            sizeBytes = sizeBytes,
            uriAuthority = uri.authority,
            durationMs = mediaDuration ?: retriever.durationMs,
            overallBitRate = retriever.overallBitRate,
            videoFrameCount = retriever.videoFrameCount,
            width = mediaWidth ?: retriever.width,
            height = mediaHeight ?: retriever.height,
            dateAddedEpochSeconds = dateAdded,
            dateModifiedEpochSeconds = dateModified,
            relativePath = relativePath,
            generationAdded = generationAdded,
            generationModified = generationModified,
        )
    }

    private fun readRetrieverMetadata(uri: Uri): RetrieverMetadata {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            return RetrieverMetadata(
                mimeType = retriever.string(MediaMetadataRetriever.METADATA_KEY_MIMETYPE),
                durationMs = retriever.long(MediaMetadataRetriever.METADATA_KEY_DURATION),
                width = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH),
                height = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT),
                rotationDegrees = retriever.int(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION),
                captureFrameRate = retriever.double(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE),
                overallBitRate = retriever.long(MediaMetadataRetriever.METADATA_KEY_BITRATE),
                videoFrameCount = retriever.long(MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT),
                colorStandard = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.int(MediaMetadataRetriever.METADATA_KEY_COLOR_STANDARD)
                } else {
                    null
                },
                colorTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.int(MediaMetadataRetriever.METADATA_KEY_COLOR_TRANSFER)
                } else {
                    null
                },
                colorRange = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    retriever.int(MediaMetadataRetriever.METADATA_KEY_COLOR_RANGE)
                } else {
                    null
                },
            )
        } catch (exception: Exception) {
            throw MediaInspectionException(
                "Android could not open this item as local media. Try selecting the downloaded/local copy.",
                exception,
            )
        } finally {
            retriever.release()
        }
    }

    private fun readTracks(
        uri: Uri,
        retriever: RetrieverMetadata,
        container: ContainerInfo,
    ): TrackCollection {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(appContext, uri, null)
            val video = mutableListOf<VideoTrackInfo>()
            val audio = mutableListOf<AudioTrackInfo>()
            var otherCount = 0

            repeat(extractor.trackCount) { index ->
                val format = extractor.getTrackFormat(index)
                val mimeType = format.string(MediaFormat.KEY_MIME) ?: "unknown"
                when {
                    mimeType.startsWith("video/") -> video += readVideoTrack(
                        uri = uri,
                        index = index,
                        format = format,
                        retriever = retriever,
                        container = container,
                    )

                    mimeType.startsWith("audio/") -> audio += readAudioTrack(index, format, mimeType)
                    else -> otherCount += 1
                }
            }
            return TrackCollection(video, audio, otherCount)
        } catch (exception: MediaInspectionException) {
            throw exception
        } catch (exception: Exception) {
            throw MediaInspectionException(
                "Android's media extractor could not read the selected video's tracks.",
                exception,
            )
        } finally {
            extractor.release()
        }
    }

    private fun readVideoTrack(
        uri: Uri,
        index: Int,
        format: MediaFormat,
        retriever: RetrieverMetadata,
        container: ContainerInfo,
    ): VideoTrackInfo {
        val mimeType = format.string(MediaFormat.KEY_MIME) ?: "video/unknown"
        val profile = format.int(MediaFormat.KEY_PROFILE)
        val level = format.int(MediaFormat.KEY_LEVEL)
        val hevcConfiguration = container.hevcConfigurations.firstOrNull()
        val inferredBitDepth = when {
            hevcConfiguration != null -> hevcConfiguration.bitDepthLuma
            profile in setOf(
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10,
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10,
                android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus,
            ) -> 10
            mimeType == MediaFormat.MIMETYPE_VIDEO_AVC -> 8
            else -> null
        }

        return VideoTrackInfo(
            index = index,
            mimeType = mimeType,
            codecString = format.codecsString()
                ?: container.dolbyVision?.codecString,
            profileValue = profile,
            profileName = CodecLabels.videoProfile(mimeType, profile),
            levelValue = level,
            levelName = CodecLabels.videoLevel(mimeType, level),
            width = format.int(MediaFormat.KEY_WIDTH) ?: retriever.width,
            height = format.int(MediaFormat.KEY_HEIGHT) ?: retriever.height,
            rotationDegrees = format.int(MediaFormat.KEY_ROTATION) ?: retriever.rotationDegrees,
            frameRate = format.number(MediaFormat.KEY_FRAME_RATE)?.toDouble()
                ?: retriever.captureFrameRate,
            timing = readFrameTiming(uri, index),
            bitRate = format.number(MediaFormat.KEY_BIT_RATE)?.toLong(),
            durationUs = format.number(MediaFormat.KEY_DURATION)?.toLong()
                ?: retriever.durationMs?.times(1_000),
            bitDepth = inferredBitDepth,
            colorStandard = CodecLabels.colorStandard(
                format.int(MediaFormat.KEY_COLOR_STANDARD) ?: retriever.colorStandard,
            ),
            colorTransfer = CodecLabels.colorTransfer(
                format.int(MediaFormat.KEY_COLOR_TRANSFER) ?: retriever.colorTransfer,
            ),
            colorRange = CodecLabels.colorRange(
                format.int(MediaFormat.KEY_COLOR_RANGE) ?: retriever.colorRange,
            ),
            hasHdrStaticInfo = format.containsKey(MediaFormat.KEY_HDR_STATIC_INFO),
            hasHdr10PlusInfo = format.containsKey(MediaFormat.KEY_HDR10_PLUS_INFO),
        )
    }

    private fun readAudioTrack(
        index: Int,
        format: MediaFormat,
        mimeType: String,
    ) = AudioTrackInfo(
        index = index,
        mimeType = mimeType,
        codecName = CodecLabels.audioCodec(mimeType),
        codecString = format.codecsString(),
        profileValue = format.int(MediaFormat.KEY_AAC_PROFILE)
            ?: format.int(MediaFormat.KEY_PROFILE),
        sampleRateHz = format.int(MediaFormat.KEY_SAMPLE_RATE),
        channelCount = format.int(MediaFormat.KEY_CHANNEL_COUNT),
        channelLayout = CodecLabels.channelLayout(format.int(MediaFormat.KEY_CHANNEL_COUNT)),
        bitRate = format.number(MediaFormat.KEY_BIT_RATE)?.toLong(),
        durationUs = format.number(MediaFormat.KEY_DURATION)?.toLong(),
        language = format.string(MediaFormat.KEY_LANGUAGE),
    )

    private fun readFrameTiming(uri: Uri, trackIndex: Int): FrameTimingInfo? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(appContext, uri, null)
            extractor.selectTrack(trackIndex)
            extractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val timestamps = ArrayList<Long>(MAX_TIMING_SAMPLES)
            var complete = false
            while (timestamps.size < MAX_TIMING_SAMPLES) {
                val sampleTime = extractor.sampleTime
                if (sampleTime < 0) {
                    complete = true
                    break
                }
                timestamps += sampleTime
                if (!extractor.advance()) {
                    complete = true
                    break
                }
            }
            if (timestamps.isEmpty()) {
                null
            } else {
                FrameTimingAnalyzer.analyze(timestamps, complete)
            }
        } catch (_: Exception) {
            null
        } finally {
            extractor.release()
        }
    }

    private fun MediaMetadataRetriever.string(key: Int): String? =
        extractMetadata(key)?.takeIf { it.isNotBlank() }

    private fun MediaMetadataRetriever.int(key: Int): Int? = string(key)?.toIntOrNull()

    private fun MediaMetadataRetriever.long(key: Int): Long? = string(key)?.toLongOrNull()

    private fun MediaMetadataRetriever.double(key: Int): Double? = string(key)?.toDoubleOrNull()

    private fun MediaFormat.string(key: String): String? =
        if (containsKey(key)) runCatching { getString(key) }.getOrNull() else null

    private fun MediaFormat.int(key: String): Int? =
        if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null

    private fun MediaFormat.number(key: String): Number? =
        if (containsKey(key)) runCatching { getNumber(key) }.getOrNull() else null

    private fun MediaFormat.codecsString(): String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        string(MediaFormat.KEY_CODECS_STRING)
    } else {
        null
    }

    private fun Cursor.columnIndex(name: String): Int? = getColumnIndex(name).takeIf { it >= 0 }

    private fun Cursor.stringOrNull(name: String): String? = columnIndex(name)?.let { index ->
        if (isNull(index)) null else getString(index)
    }

    private fun Cursor.longOrNull(name: String): Long? = columnIndex(name)?.let { index ->
        if (isNull(index)) null else getLong(index)
    }

    private fun Cursor.intOrNull(name: String): Int? = columnIndex(name)?.let { index ->
        if (isNull(index)) null else getInt(index)
    }

    private data class RetrieverMetadata(
        val mimeType: String?,
        val durationMs: Long?,
        val width: Int?,
        val height: Int?,
        val rotationDegrees: Int?,
        val captureFrameRate: Double?,
        val overallBitRate: Long?,
        val videoFrameCount: Long?,
        val colorStandard: Int?,
        val colorTransfer: Int?,
        val colorRange: Int?,
    )

    private data class TrackCollection(
        val video: List<VideoTrackInfo>,
        val audio: List<AudioTrackInfo>,
        val otherCount: Int,
    )

    private companion object {
        const val MAX_TIMING_SAMPLES = 600
    }
}
