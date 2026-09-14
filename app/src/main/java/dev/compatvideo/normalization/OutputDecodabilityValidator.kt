package dev.compatvideo.normalization

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri

class OutputDecodabilityValidator(
    context: Context,
) {
    private val appContext = context.applicationContext

    fun validateFirstFrame(uri: Uri) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(appContext, uri)
            val frame = retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                VALIDATION_FRAME_SIZE,
                VALIDATION_FRAME_SIZE,
            ) ?: throw NormalizationException(
                "Android could not decode a frame from the new copy, so it was not added to Gallery.",
            )
            frame.recycle()
        } catch (exception: NormalizationException) {
            throw exception
        } catch (exception: Exception) {
            throw NormalizationException(
                "Android could not decode a frame from the new copy, so it was not added to Gallery.",
                exception,
            )
        } finally {
            retriever.release()
        }
    }

    private companion object {
        const val VALIDATION_FRAME_SIZE = 320
    }
}
