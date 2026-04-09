package com.snapmark.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.util.Log

/**
 * Manages a persistent MediaProjection + VirtualDisplay session.
 * On Android 14+, a single MediaProjection instance can only create one VirtualDisplay,
 * so both are kept alive for the service lifetime. Each capture registers a one-shot
 * ImageReader listener to grab the latest frame.
 */
class ScreenCaptureManager(
    context: Context,
    resultCode: Int,
    data: Intent,
    private val backgroundHandler: Handler,
    private val onProjectionStopped: () -> Unit
) {
    companion object {
        private const val TAG = "ScreenCaptureManager"
        private const val VIRTUAL_DISPLAY_NAME = "SnapMarkCapture"
    }

    private val width: Int
    private val height: Int
    private val mediaProjection: MediaProjection
    private val imageReader: ImageReader
    private val virtualDisplay: VirtualDisplay

    private var released = false
    private var projectionStopped = false

    init {
        val metrics = context.resources.displayMetrics
        width = metrics.widthPixels
        height = metrics.heightPixels
        val dpi = metrics.densityDpi

        val projectionManager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection stopped externally")
                projectionStopped = true
                onProjectionStopped()
            }
        }, backgroundHandler)

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            VIRTUAL_DISPLAY_NAME, width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader.surface, null, backgroundHandler
        )
    }

    /**
     * Discards any frame currently in the ImageReader buffer so the next captureScreen()
     * call waits for a fresh frame from the compositor.
     */
    fun discardBufferedFrame() {
        imageReader.acquireLatestImage()?.close()
    }

    /**
     * Captures a single screenshot. First tries to grab the latest buffered frame;
     * if none available, registers a one-shot listener and waits (with timeout).
     */
    fun captureScreen(onCaptured: (Bitmap?) -> Unit) {
        if (released || projectionStopped) {
            Log.e(TAG, "Cannot capture: released=$released stopped=$projectionStopped")
            onCaptured(null)
            return
        }

        // Try to grab a frame already in the buffer
        val immediate = imageReader.acquireLatestImage()
        if (immediate != null) {
            val result = extractBitmap(immediate)
            immediate.close()
            onCaptured(result)
            return
        }

        // No frame buffered — register one-shot listener
        val callbackFired = java.util.concurrent.atomic.AtomicBoolean(false)

        imageReader.setOnImageAvailableListener({ reader ->
            if (!callbackFired.compareAndSet(false, true)) return@setOnImageAvailableListener
            reader.setOnImageAvailableListener(null, null)

            var image: android.media.Image? = null
            try {
                image = reader.acquireLatestImage()
                if (image == null) {
                    onCaptured(null)
                    return@setOnImageAvailableListener
                }
                val result = extractBitmap(image)
                onCaptured(result)
            } finally {
                image?.close()
            }
        }, backgroundHandler)

        // Timeout safety net
        backgroundHandler.postDelayed({
            if (callbackFired.compareAndSet(false, true)) {
                imageReader.setOnImageAvailableListener(null, null)
                Log.w(TAG, "Capture timed out")
                onCaptured(null)
            }
        }, 2000)
    }

    private fun extractBitmap(image: android.media.Image): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width

        val bitmapWidth = width + rowPadding / pixelStride
        val bitmap = Bitmap.createBitmap(bitmapWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)

        return if (rowPadding > 0) {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            bitmap.recycle()
            cropped
        } else {
            bitmap
        }
    }

    /**
     * Releases all resources. Safe to call multiple times.
     */
    fun release() {
        if (released) return
        released = true

        try {
            virtualDisplay.release()
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing VirtualDisplay", e)
        }
        try {
            if (!projectionStopped) {
                mediaProjection.stop()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaProjection", e)
        }
        try {
            imageReader.close()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing ImageReader", e)
        }
    }
}
