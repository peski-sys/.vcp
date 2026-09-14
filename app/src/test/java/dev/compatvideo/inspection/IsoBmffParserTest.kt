package dev.compatvideo.inspection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

class IsoBmffParserTest {
    @Test
    fun `parses quicktime ftyp brands`() {
        val payload = "qt  ".ascii() + uint32(512) + "qt  ".ascii()

        val ftyp = IsoBmffParser.parseFtypPayload(payload)

        assertNotNull(ftyp)
        assertEquals("qt  ", ftyp?.majorBrand)
        assertEquals(512L, ftyp?.minorVersion)
        assertEquals(listOf("qt  "), ftyp?.compatibleBrands)
    }

    @Test
    fun `parses iPhone profile 8 point 4 Dolby Vision configuration`() {
        val dolbyPayload = byteArrayOf(
            1,
            0,
            0x10,
            0x2D,
            0x40,
        )
        val hvcPayload = ByteArray(23).apply {
            this[0] = 1
            this[1] = 2
            this[12] = 123
            this[16] = 1
            this[17] = 2
            this[18] = 2
        }
        val moov = box(
            "moov",
            box("hvc1", ByteArray(8)) +
                box("hvcC", hvcPayload) +
                box("dvvC", dolbyPayload),
        )

        val metadata = IsoBmffParser.scanMoov(moov)
        val dolby = requireNotNull(metadata.dolbyVision)

        assertEquals(listOf("hvc1"), metadata.sampleEntries)
        assertEquals(listOf("hvcC", "dvvC"), metadata.configurationBoxes)
        assertEquals("dvhe.08.05", dolby.codecString)
        assertEquals(8, dolby.profile)
        assertEquals(5, dolby.level)
        assertTrue(dolby.rpuPresent)
        assertTrue(dolby.baseLayerPresent)
        assertFalse(dolby.enhancementLayerPresent)
        assertEquals(4, dolby.baseLayerSignalCompatibilityId)
        assertTrue(dolby.isHlgCompatibleProfile8)
        assertEquals(10, metadata.hevcConfigurations.single().bitDepthLuma)
        assertEquals(10, metadata.hevcConfigurations.single().bitDepthChroma)
    }

    @Test
    fun `ignores unvalidated fourcc text`() {
        val invalidMarker = byteArrayOf(0, 0, 0, 120) + "dvvC".ascii() + ByteArray(2)

        val metadata = IsoBmffParser.scanMoov(invalidMarker)

        assertEquals(null, metadata.dolbyVision)
        assertTrue(metadata.configurationBoxes.isEmpty())
    }

    private fun box(type: String, payload: ByteArray): ByteArray =
        uint32(payload.size + 8) + type.ascii() + payload

    private fun uint32(value: Int): ByteArray = ByteBuffer.allocate(4)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(value)
        .array()

    private fun String.ascii(): ByteArray = toByteArray(StandardCharsets.ISO_8859_1)
}
