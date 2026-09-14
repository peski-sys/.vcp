package dev.compatvideo.inspection

import android.net.Uri

fun interface MediaInspector {
    suspend fun inspect(uri: Uri): MediaInspection
}

class MediaInspectionException(
    val userFacingMessage: String,
    cause: Throwable? = null,
) : Exception(userFacingMessage, cause)
