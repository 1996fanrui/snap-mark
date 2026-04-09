package com.snapmark.storage

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves watermarked screenshots to the device gallery.
 * Uses MediaStore on API 29+ and direct file I/O on API 26-28.
 */
class ScreenshotSaver(private val context: Context) {

    companion object {
        private const val TAG = "ScreenshotSaver"
        private const val DIR_NAME = "SnapMark"

        internal fun generateFilename(date: Date): String =
            "SnapMark_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)}.jpg"

        internal fun generateDateDir(date: Date): String =
            SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
    }

    /**
     * @param onComplete callback with (success, pathOrError) — on success the second param is the
     *   saved file path/URI; on failure it is a human-readable error reason.
     */
    fun save(bitmap: Bitmap, onComplete: (success: Boolean, pathOrError: String?) -> Unit) {
        val now = Date()
        val filename = generateFilename(now)
        val dateDir = generateDateDir(now)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveWithMediaStore(bitmap, filename, dateDir, onComplete)
            } else {
                saveWithFileIO(bitmap, filename, dateDir, onComplete)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot", e)
            onComplete(false, e.message ?: "Unknown error")
        }
    }

    private fun saveWithMediaStore(
        bitmap: Bitmap,
        filename: String,
        dateDir: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$DIR_NAME/$dateDir/"
            )
        }

        val uri = context.contentResolver.insert(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values
        )
        if (uri == null) {
            Log.e(TAG, "Failed to create MediaStore entry")
            onComplete(false, "Failed to create MediaStore entry")
            return
        }

        context.contentResolver.openOutputStream(uri)?.use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        } ?: run {
            Log.e(TAG, "Failed to open output stream for uri: $uri")
            onComplete(false, "Failed to open output stream")
            return
        }

        onComplete(true, uri.toString())
    }

    @Suppress("DEPRECATION")
    private fun saveWithFileIO(
        bitmap: Bitmap,
        filename: String,
        dateDir: String,
        onComplete: (Boolean, String?) -> Unit
    ) {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            "$DIR_NAME/$dateDir"
        )
        if (!dir.exists() && !dir.mkdirs()) {
            Log.e(TAG, "Failed to create directory: ${dir.absolutePath}")
            onComplete(false, "Failed to create directory: ${dir.absolutePath}")
            return
        }

        val file = File(dir, filename)
        FileOutputStream(file).use { outputStream ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, outputStream)
        }

        // Notify media scanner so the file appears in the gallery
        MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        onComplete(true, file.absolutePath)
    }
}
