package dev.compatvideo.inspection

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets

class IsoBmffReader(
    private val contentResolver: ContentResolver,
) {
    fun inspect(uri: Uri): ContainerInfo {
        return try {
            val descriptor = contentResolver.openFileDescriptor(uri, "r") ?: return unavailable()
            val reportedSize = descriptor.statSize
            ParcelFileDescriptor.AutoCloseInputStream(descriptor).channel.use { channel ->
                inspectChannel(channel, reportedSize)
            }
        } catch (_: Exception) {
            unavailable()
        }
    }

    private fun inspectChannel(channel: FileChannel, reportedSize: Long): ContainerInfo {
        val fileSize = when {
            reportedSize > 0 -> reportedSize
            else -> runCatching { channel.size() }.getOrDefault(-1L)
        }
        if (fileSize < BOX_HEADER_BYTES) return unavailable()

        var ftyp: IsoBmffParser.Ftyp? = null
        var moov: IsoBmffParser.MoovMetadata? = null
        var offset = 0L
        var boxCount = 0
        var oversizedMoov = false

        while (offset + BOX_HEADER_BYTES <= fileSize && boxCount < MAX_TOP_LEVEL_BOXES) {
            val header = readAt(channel, offset, EXTENDED_BOX_HEADER_BYTES) ?: break
            val size32 = header.uint32(0)
            val type = header.fourCc(4) ?: break
            val headerSize: Int
            val boxSize: Long
            when (size32) {
                0L -> {
                    headerSize = BOX_HEADER_BYTES
                    boxSize = fileSize - offset
                }

                1L -> {
                    if (header.size < EXTENDED_BOX_HEADER_BYTES) break
                    headerSize = EXTENDED_BOX_HEADER_BYTES
                    boxSize = header.int64(8)
                }

                else -> {
                    headerSize = BOX_HEADER_BYTES
                    boxSize = size32
                }
            }
            if (boxSize < headerSize || boxSize > fileSize - offset) break

            val payloadSize = boxSize - headerSize
            when (type) {
                "ftyp" -> if (payloadSize in 8..MAX_FTYP_BYTES.toLong()) {
                    readAt(channel, offset + headerSize, payloadSize.toInt())
                        ?.let(IsoBmffParser::parseFtypPayload)
                        ?.let { ftyp = it }
                }

                "moov" -> if (boxSize <= MAX_MOOV_BYTES) {
                    readAt(channel, offset, boxSize.toInt())
                        ?.let(IsoBmffParser::scanMoov)
                        ?.let { moov = it }
                } else {
                    oversizedMoov = true
                }
            }

            offset += boxSize
            boxCount += 1
        }

        if (ftyp == null && moov == null) {
            return ContainerInfo(
                extractorMimeType = null,
                majorBrand = null,
                minorVersion = null,
                compatibleBrands = emptyList(),
                sampleEntries = emptyList(),
                configurationBoxes = emptyList(),
                dolbyVision = null,
                hevcConfigurations = emptyList(),
                scanStatus = ContainerScanStatus.NOT_ISO_BASE_MEDIA,
            )
        }

        return ContainerInfo(
            extractorMimeType = null,
            majorBrand = ftyp?.majorBrand,
            minorVersion = ftyp?.minorVersion,
            compatibleBrands = ftyp?.compatibleBrands.orEmpty(),
            sampleEntries = moov?.sampleEntries.orEmpty(),
            configurationBoxes = moov?.configurationBoxes.orEmpty(),
            dolbyVision = moov?.dolbyVision,
            hevcConfigurations = moov?.hevcConfigurations.orEmpty(),
            scanStatus = when {
                oversizedMoov -> ContainerScanStatus.METADATA_TOO_LARGE
                moov == null -> ContainerScanStatus.UNAVAILABLE
                else -> ContainerScanStatus.COMPLETE
            },
        )
    }

    private fun unavailable() = ContainerInfo(
        extractorMimeType = null,
        majorBrand = null,
        minorVersion = null,
        compatibleBrands = emptyList(),
        sampleEntries = emptyList(),
        configurationBoxes = emptyList(),
        dolbyVision = null,
        hevcConfigurations = emptyList(),
        scanStatus = ContainerScanStatus.UNAVAILABLE,
    )

    private fun readAt(channel: FileChannel, offset: Long, requestedBytes: Int): ByteArray? {
        if (requestedBytes <= 0) return ByteArray(0)
        val buffer = ByteBuffer.allocate(requestedBytes)
        var readOffset = offset
        while (buffer.hasRemaining()) {
            val read = channel.read(buffer, readOffset)
            if (read < 0) break
            if (read == 0) return null
            readOffset += read
        }
        if (buffer.position() < minOf(requestedBytes, BOX_HEADER_BYTES)) return null
        return buffer.array().copyOf(buffer.position())
    }

    private fun ByteArray.uint32(offset: Int): Long =
        ByteBuffer.wrap(this, offset, 4).order(ByteOrder.BIG_ENDIAN).int.toLong() and 0xFFFF_FFFFL

    private fun ByteArray.int64(offset: Int): Long =
        ByteBuffer.wrap(this, offset, 8).order(ByteOrder.BIG_ENDIAN).long

    private fun ByteArray.fourCc(offset: Int): String? {
        if (offset < 0 || offset + 4 > size) return null
        return String(this, offset, 4, StandardCharsets.ISO_8859_1)
    }

    private companion object {
        const val BOX_HEADER_BYTES = 8
        const val EXTENDED_BOX_HEADER_BYTES = 16
        const val MAX_FTYP_BYTES = 4 * 1024
        const val MAX_MOOV_BYTES = 32L * 1024 * 1024
        const val MAX_TOP_LEVEL_BOXES = 10_000
    }
}
