package dev.compatvideo.inspection

import android.media.MediaCodecInfo.CodecProfileLevel
import android.media.MediaFormat

object CodecLabels {
    fun videoProfile(mimeType: String, value: Int?): String? {
        value ?: return null
        return when (mimeType) {
            MediaFormat.MIMETYPE_VIDEO_HEVC -> when (value) {
                CodecProfileLevel.HEVCProfileMain -> "HEVC Main"
                CodecProfileLevel.HEVCProfileMain10 -> "HEVC Main 10"
                CodecProfileLevel.HEVCProfileMainStill -> "HEVC Main Still Picture"
                CodecProfileLevel.HEVCProfileMain10HDR10 -> "HEVC Main 10 HDR10"
                CodecProfileLevel.HEVCProfileMain10HDR10Plus -> "HEVC Main 10 HDR10+"
                else -> null
            }

            MediaFormat.MIMETYPE_VIDEO_AVC -> when (value) {
                CodecProfileLevel.AVCProfileBaseline -> "AVC Baseline"
                CodecProfileLevel.AVCProfileConstrainedBaseline -> "AVC Constrained Baseline"
                CodecProfileLevel.AVCProfileMain -> "AVC Main"
                CodecProfileLevel.AVCProfileExtended -> "AVC Extended"
                CodecProfileLevel.AVCProfileHigh -> "AVC High"
                CodecProfileLevel.AVCProfileHigh10 -> "AVC High 10"
                CodecProfileLevel.AVCProfileHigh422 -> "AVC High 4:2:2"
                CodecProfileLevel.AVCProfileHigh444 -> "AVC High 4:4:4"
                CodecProfileLevel.AVCProfileConstrainedHigh -> "AVC Constrained High"
                else -> null
            }

            MediaFormat.MIMETYPE_VIDEO_DOLBY_VISION -> when (value) {
                CodecProfileLevel.DolbyVisionProfileDvavPer -> "Dolby Vision dvav.per"
                CodecProfileLevel.DolbyVisionProfileDvavPen -> "Dolby Vision dvav.pen"
                CodecProfileLevel.DolbyVisionProfileDvheDer -> "Dolby Vision dvhe.der"
                CodecProfileLevel.DolbyVisionProfileDvheDen -> "Dolby Vision dvhe.den"
                CodecProfileLevel.DolbyVisionProfileDvheDtr -> "Dolby Vision dvhe.dtr"
                CodecProfileLevel.DolbyVisionProfileDvheStn -> "Dolby Vision dvhe.stn (Profile 8)"
                CodecProfileLevel.DolbyVisionProfileDvheDth -> "Dolby Vision dvhe.dth"
                CodecProfileLevel.DolbyVisionProfileDvheDtb -> "Dolby Vision dvhe.dtb"
                CodecProfileLevel.DolbyVisionProfileDvheSt -> "Dolby Vision dvhe.st"
                CodecProfileLevel.DolbyVisionProfileDvavSe -> "Dolby Vision dvav.se"
                CodecProfileLevel.DolbyVisionProfileDvav110 -> "Dolby Vision dvav.110"
                else -> null
            }

            else -> null
        }
    }

    fun videoLevel(mimeType: String, value: Int?): String? {
        value ?: return null
        if (mimeType != MediaFormat.MIMETYPE_VIDEO_HEVC) return null
        return when (value) {
            CodecProfileLevel.HEVCMainTierLevel1 -> "HEVC Main Tier Level 1"
            CodecProfileLevel.HEVCMainTierLevel2 -> "HEVC Main Tier Level 2"
            CodecProfileLevel.HEVCMainTierLevel21 -> "HEVC Main Tier Level 2.1"
            CodecProfileLevel.HEVCMainTierLevel3 -> "HEVC Main Tier Level 3"
            CodecProfileLevel.HEVCMainTierLevel31 -> "HEVC Main Tier Level 3.1"
            CodecProfileLevel.HEVCMainTierLevel4 -> "HEVC Main Tier Level 4"
            CodecProfileLevel.HEVCMainTierLevel41 -> "HEVC Main Tier Level 4.1"
            CodecProfileLevel.HEVCMainTierLevel5 -> "HEVC Main Tier Level 5"
            CodecProfileLevel.HEVCMainTierLevel51 -> "HEVC Main Tier Level 5.1"
            CodecProfileLevel.HEVCMainTierLevel52 -> "HEVC Main Tier Level 5.2"
            CodecProfileLevel.HEVCMainTierLevel6 -> "HEVC Main Tier Level 6"
            CodecProfileLevel.HEVCMainTierLevel61 -> "HEVC Main Tier Level 6.1"
            CodecProfileLevel.HEVCMainTierLevel62 -> "HEVC Main Tier Level 6.2"
            else -> null
        }
    }

    fun audioCodec(mimeType: String): String = when (mimeType) {
        MediaFormat.MIMETYPE_AUDIO_AAC -> "AAC"
        MediaFormat.MIMETYPE_AUDIO_OPUS -> "Opus"
        MediaFormat.MIMETYPE_AUDIO_VORBIS -> "Vorbis"
        MediaFormat.MIMETYPE_AUDIO_FLAC -> "FLAC"
        MediaFormat.MIMETYPE_AUDIO_MPEG -> "MP3"
        MediaFormat.MIMETYPE_AUDIO_RAW -> "PCM"
        MediaFormat.MIMETYPE_AUDIO_AC3 -> "Dolby Digital (AC-3)"
        MediaFormat.MIMETYPE_AUDIO_EAC3 -> "Dolby Digital Plus (E-AC-3)"
        else -> mimeType
    }

    fun channelLayout(channelCount: Int?): String? = when (channelCount) {
        null -> null
        1 -> "Mono"
        2 -> "Stereo (L R)"
        6 -> "5.1 channels"
        8 -> "7.1 channels"
        else -> "$channelCount channels"
    }

    fun colorStandard(value: Int?): ColorValue? = value?.let {
        ColorValue(
            rawValue = it,
            label = when (it) {
                MediaFormat.COLOR_STANDARD_BT709 -> "BT.709"
                MediaFormat.COLOR_STANDARD_BT601_PAL -> "BT.601 PAL"
                MediaFormat.COLOR_STANDARD_BT601_NTSC -> "BT.601 NTSC"
                MediaFormat.COLOR_STANDARD_BT2020 -> "BT.2020"
                else -> "Unrecognized ($it)"
            },
        )
    }

    fun colorTransfer(value: Int?): ColorValue? = value?.let {
        ColorValue(
            rawValue = it,
            label = when (it) {
                MediaFormat.COLOR_TRANSFER_LINEAR -> "Linear"
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO -> "SDR video"
                MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ / ST 2084"
                MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                else -> "Unrecognized ($it)"
            },
        )
    }

    fun colorRange(value: Int?): ColorValue? = value?.let {
        ColorValue(
            rawValue = it,
            label = when (it) {
                MediaFormat.COLOR_RANGE_LIMITED -> "Limited"
                MediaFormat.COLOR_RANGE_FULL -> "Full"
                else -> "Unrecognized ($it)"
            },
        )
    }
}
