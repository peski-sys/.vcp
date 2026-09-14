package dev.compatvideo.normalization

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.Metadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.muxer.BufferInfo
import androidx.media3.muxer.Muxer
import com.google.common.collect.ImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer

@UnstableApi
class HevcBaseLayerMuxerFactoryTest {
    @Test
    fun `advertises HEVC but not Dolby Vision for video`() {
        val supported = HevcBaseLayerMuxerFactory()
            .getSupportedSampleMimeTypes(C.TRACK_TYPE_VIDEO)

        assertEquals(listOf(MimeTypes.VIDEO_H265), supported)
        assertFalse(supported.contains(MimeTypes.VIDEO_DOLBY_VISION))
    }

    @Test
    fun `keeps standard muxer AAC support`() {
        val supported = HevcBaseLayerMuxerFactory()
            .getSupportedSampleMimeTypes(C.TRACK_TYPE_AUDIO)

        assertTrue(supported.contains(MimeTypes.AUDIO_AAC))
    }

    @Test
    fun `rewrites Dolby Vision track declaration before delegating`() {
        val recordingMuxer = RecordingMuxer()
        val factory = HevcBaseLayerMuxerFactory(RecordingMuxerFactory(recordingMuxer))
        val muxer = factory.create("unused.mp4")

        muxer.addTrack(
            Format.Builder()
                .setSampleMimeType(MimeTypes.VIDEO_DOLBY_VISION)
                .setCodecs("dvhe.08.05")
                .build(),
        )

        assertEquals(MimeTypes.VIDEO_H265, recordingMuxer.addedFormat?.sampleMimeType)
    }

    @Test
    fun `does not alter audio track declaration`() {
        val recordingMuxer = RecordingMuxer()
        val factory = HevcBaseLayerMuxerFactory(RecordingMuxerFactory(recordingMuxer))
        val muxer = factory.create("unused.mp4")

        muxer.addTrack(Format.Builder().setSampleMimeType(MimeTypes.AUDIO_AAC).build())

        assertEquals(MimeTypes.AUDIO_AAC, recordingMuxer.addedFormat?.sampleMimeType)
    }

    private class RecordingMuxerFactory(
        private val muxer: Muxer,
    ) : Muxer.Factory {
        override fun create(path: String): Muxer = muxer

        override fun getSupportedSampleMimeTypes(trackType: Int): ImmutableList<String> =
            ImmutableList.of(MimeTypes.AUDIO_AAC, MimeTypes.VIDEO_DOLBY_VISION)
    }

    private class RecordingMuxer : Muxer {
        var addedFormat: Format? = null

        override fun addTrack(format: Format): Int {
            addedFormat = format
            return 0
        }

        override fun writeSampleData(
            trackId: Int,
            byteBuffer: ByteBuffer,
            bufferInfo: BufferInfo,
        ) = Unit

        override fun addMetadataEntry(metadataEntry: Metadata.Entry) = Unit

        override fun close() = Unit
    }
}
