package dev.compatvideo.normalization

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import androidx.media3.transformer.DefaultMuxer
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer

/**
 * Delegates MP4 writing to Media3 while declaring HEVC—not Dolby Vision—as the supported video
 * representation. Media3 then selects the documented backward-compatible HEVC base layer for a
 * Profile 8 input instead of carrying its Dolby Vision sample declaration into the new container.
 */
@UnstableApi
internal class HevcBaseLayerMuxerFactory(
    private val delegate: Muxer.Factory = DefaultMuxer.Factory(),
) : Muxer.Factory {
    override fun create(path: String): Muxer = HevcBaseLayerMuxer(delegate.create(path))

    override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
        if (trackType == C.TRACK_TYPE_VIDEO) {
            ImmutableList.of(MimeTypes.VIDEO_H265)
        } else {
            delegate.getSupportedSampleMimeTypes(trackType)
        }

    override fun supportsWritingNegativeTimestampsInEditList(): Boolean =
        delegate.supportsWritingNegativeTimestampsInEditList()

    /**
     * The capability declaration above lets Transformer choose the HEVC fallback when Media3 can
     * identify it. This final boundary also normalizes a Dolby Vision track declaration before the
     * MP4 writer sees it. Profile 8.4's initialization data already contains the HEVC VPS/SPS/PPS,
     * so the default muxer writes hvc1 + hvcC and copies the encoded samples unchanged.
     */
    private class HevcBaseLayerMuxer(
        private val delegate: Muxer,
    ) : Muxer {
        override fun addTrack(format: Format): Int {
            val outputFormat = if (format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION) {
                format.buildUpon()
                    .setSampleMimeType(MimeTypes.VIDEO_H265)
                    .build()
            } else {
                format
            }
            return delegate.addTrack(outputFormat)
        }

        override fun writeSampleData(
            trackId: Int,
            byteBuffer: ByteBuffer,
            bufferInfo: BufferInfo,
        ) = delegate.writeSampleData(trackId, byteBuffer, bufferInfo)

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) =
            delegate.addMetadataEntry(metadataEntry)

        override fun close() = delegate.close()
    }
}
