package dev.compatvideo.normalization

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

class MediaStoreVideoPublisher(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val contentResolver: ContentResolver = appContext.contentResolver
    private val recoveryPreferences = appContext.getSharedPreferences(
        RECOVERY_PREFERENCES,
        Context.MODE_PRIVATE,
    )

    suspend fun createPending(displayName: String): Uri = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, OUTPUT_MIME_TYPE)
            put(MediaStore.Video.Media.RELATIVE_PATH, OUTPUT_RELATIVE_PATH)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = contentResolver.insert(collection, values)
            ?: throw NormalizationException(
                "Android could not reserve a new Gallery item. Check that the device has free storage.",
            )
        if (!persistRecoveryMarker(uri)) {
            runCatching { contentResolver.delete(uri, null, null) }
            throw NormalizationException("The app could not record its pending output safely.")
        }
        uri
    }

    suspend fun copyIntoPending(
        source: File,
        destination: Uri,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val totalBytes = source.length().coerceAtLeast(1L)
        val output = contentResolver.openOutputStream(destination, "w")
            ?: throw NormalizationException("Android could not open the new Gallery item for writing.")
        source.inputStream().buffered(COPY_BUFFER_BYTES).use { input ->
            output.buffered(COPY_BUFFER_BYTES).use { target ->
                val buffer = ByteArray(COPY_BUFFER_BYTES)
                var copiedBytes = 0L
                while (true) {
                    currentCoroutineContext().ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    target.write(buffer, 0, count)
                    copiedBytes += count
                    onProgress(((copiedBytes * 100L) / totalBytes).toInt().coerceIn(0, 100))
                }
                target.flush()
            }
        }
    }

    suspend fun publish(uri: Uri) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        if (contentResolver.update(uri, values, null, null) != 1) {
            throw NormalizationException("Android could not finish publishing the verified Gallery copy.")
        }
        clearRecoveryMarker(uri)
    }

    suspend fun discard(uri: Uri) = withContext(Dispatchers.IO) {
        if (runCatching { contentResolver.delete(uri, null, null) }.isSuccess) {
            clearRecoveryMarker(uri)
        }
    }

    suspend fun discardInterruptedPendingOutput() = withContext(Dispatchers.IO) {
        val uri = recoveryPreferences.getString(PENDING_URI_KEY, null)?.let(Uri::parse) ?: return@withContext
        try {
            val isPending = contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media.IS_PENDING),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) == 1 else null
            }
            if (isPending == true) {
                contentResolver.delete(uri, null, null)
            }
            recoveryPreferences.edit { remove(PENDING_URI_KEY) }
        } catch (_: Exception) {
            // Keep the marker so a later launch can retry cleanup.
        }
    }

    private fun clearRecoveryMarker(uri: Uri) {
        if (recoveryPreferences.getString(PENDING_URI_KEY, null) == uri.toString()) {
            recoveryPreferences.edit { remove(PENDING_URI_KEY) }
        }
    }

    @SuppressLint("ApplySharedPref", "UseKtx")
    private fun persistRecoveryMarker(uri: Uri): Boolean {
        // This must reach disk before a pending MediaStore item can outlive the process.
        return recoveryPreferences.edit().putString(PENDING_URI_KEY, uri.toString()).commit()
    }

    companion object {
        val OUTPUT_RELATIVE_PATH = "${Environment.DIRECTORY_MOVIES}/vcp"
        private const val OUTPUT_MIME_TYPE = "video/mp4"
        private const val COPY_BUFFER_BYTES = 1024 * 1024
        private const val RECOVERY_PREFERENCES = "normalization_recovery"
        private const val PENDING_URI_KEY = "pending_output_uri"
    }
}
