package dev.compatvideo.inspection

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets

/**
 * Small ISO-BMFF metadata parser. It intentionally reads box headers and the `moov` box only;
 * encoded samples in `mdat` are never loaded into memory.
 */
object IsoBmffParser {
    private val interestingTypes = setOf(
        "avc1",
        "avc3",
        "hvc1",
        "hev1",
        "dvav",
        "dva1",
        "dvhe",
        "dvh1",
        "avcC",
        "hvcC",
        "dvcC",
        "dvvC",
        "dvwC",
    )

    data class Ftyp(
        val majorBrand: String,
        val minorVersion: Long,
        val compatibleBrands: List<String>,
    )

    data class MoovMetadata(
        val sampleEntries: List<String>,
        val configurationBoxes: List<String>,
        val dolbyVision: DolbyVisionConfig?,
        val hevcConfigurations: List<HevcConfig>,
    )

    fun parseFtypPayload(payload: ByteArray): Ftyp? {
        if (payload.size < 8) return null
        val majorBrand = fourCc(payload, 0) ?: return null
        val minorVersion = uint32(payload, 4)
        val compatibleBrands = buildList {
            var offset = 8
            while (offset + 4 <= payload.size) {
                fourCc(payload, offset)?.let(::add)
                offset += 4
            }
        }
        return Ftyp(majorBrand, minorVersion, compatibleBrands)
    }

    fun scanMoov(moovBytes: ByteArray): MoovMetadata {
        val sampleEntries = linkedSetOf<String>()
        val configurationBoxes = linkedSetOf<String>()
        val hevcConfigurations = mutableListOf<HevcConfig>()
        var dolbyVision: DolbyVisionConfig? = null

        var typeOffset = 4
        while (typeOffset + 4 <= moovBytes.size) {
            val type = fourCc(moovBytes, typeOffset)
            if (type in interestingTypes) {
                val boxStart = typeOffset - 4
                val boxSize = uint32(moovBytes, boxStart)
                val boxEnd = boxStart + boxSize
                val validBox = boxSize >= 8 && boxEnd <= moovBytes.size.toLong()
                if (validBox) {
                    val payloadStart = typeOffset + 4
                    val payloadLength = (boxSize - 8).toInt()
                    when (type) {
                        "avc1", "avc3", "hvc1", "hev1", "dvav", "dva1", "dvhe", "dvh1" -> {
                            sampleEntries += type
                        }

                        "avcC", "hvcC", "dvcC", "dvvC", "dvwC" -> {
                            configurationBoxes += type
                            when (type) {
                                "dvcC", "dvvC", "dvwC" -> if (dolbyVision == null) {
                                    dolbyVision = parseDolbyVisionConfig(
                                        type,
                                        moovBytes,
                                        payloadStart,
                                        payloadLength,
                                    )
                                }

                                "hvcC" -> parseHevcConfig(
                                    type,
                                    moovBytes,
                                    payloadStart,
                                    payloadLength,
                                )?.let(hevcConfigurations::add)
                            }
                        }
                    }
                }
            }
            typeOffset += 1
        }

        return MoovMetadata(
            sampleEntries = sampleEntries.toList(),
            configurationBoxes = configurationBoxes.toList(),
            dolbyVision = dolbyVision,
            hevcConfigurations = hevcConfigurations.distinct(),
        )
    }

    private fun parseDolbyVisionConfig(
        type: String,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): DolbyVisionConfig? {
        if (length < 4 || offset < 0 || offset + length > bytes.size) return null

        val versionMajor = bytes[offset].toUnsignedInt()
        val versionMinor = bytes[offset + 1].toUnsignedInt()
        val profileAndLevelHighBit = bytes[offset + 2].toUnsignedInt()
        val levelAndFlags = bytes[offset + 3].toUnsignedInt()
        val profile = profileAndLevelHighBit ushr 1
        val level = ((profileAndLevelHighBit and 0x01) shl 5) or (levelAndFlags ushr 3)
        val compatibilityId = if (length >= 5) bytes[offset + 4].toUnsignedInt() ushr 4 else null

        return DolbyVisionConfig(
            boxType = type,
            versionMajor = versionMajor,
            versionMinor = versionMinor,
            profile = profile,
            level = level,
            rpuPresent = levelAndFlags and 0x04 != 0,
            enhancementLayerPresent = levelAndFlags and 0x02 != 0,
            baseLayerPresent = levelAndFlags and 0x01 != 0,
            baseLayerSignalCompatibilityId = compatibilityId,
        )
    }

    private fun parseHevcConfig(
        type: String,
        bytes: ByteArray,
        offset: Int,
        length: Int,
    ): HevcConfig? {
        if (length < 19 || offset < 0 || offset + length > bytes.size) return null
        if (bytes[offset].toUnsignedInt() != 1) return null

        return HevcConfig(
            boxType = type,
            profileIdc = bytes[offset + 1].toUnsignedInt() and 0x1F,
            levelIdc = bytes[offset + 12].toUnsignedInt(),
            chromaFormatIdc = bytes[offset + 16].toUnsignedInt() and 0x03,
            bitDepthLuma = 8 + (bytes[offset + 17].toUnsignedInt() and 0x07),
            bitDepthChroma = 8 + (bytes[offset + 18].toUnsignedInt() and 0x07),
        )
    }

    private fun fourCc(bytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset + 4 > bytes.size) return null
        for (index in offset until offset + 4) {
            val value = bytes[index].toUnsignedInt()
            if (value !in 0x20..0x7E) return null
        }
        return String(bytes, offset, 4, StandardCharsets.ISO_8859_1)
    }

    private fun uint32(bytes: ByteArray, offset: Int): Long {
        if (offset < 0 || offset + 4 > bytes.size) return -1
        return ByteBuffer.wrap(bytes, offset, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int
            .toLong() and 0xFFFF_FFFFL
    }

    private fun Byte.toUnsignedInt(): Int = toInt() and 0xFF
}
