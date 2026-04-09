package com.snapmark

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.snapmark.capture.ScreenCaptureManager
import com.snapmark.overlay.FloatingButtonManager
import com.snapmark.storage.ScreenshotSaver
import com.snapmark.watermark.WatermarkRenderer

/**
 * Foreground service for the overlay floating button and screenshot capture.
 */
class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
        private const val CHANNEL_ID = "snap_mark_overlay"
        private const val NOTIFICATION_ID = 1

        @Volatile
        var isRunning = false
    }

    private lateinit var handlerThread: HandlerThread
    private lateinit var bgHandler: Handler
    private lateinit var floatingButtonManager: FloatingButtonManager
    private lateinit var watermarkRenderer: WatermarkRenderer
    private lateinit var screenshotSaver: ScreenshotSaver

    // Created in onStartCommand when MediaProjection data arrives
    private var screenCaptureManager: ScreenCaptureManager? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        handlerThread = HandlerThread("SnapMarkBg").also { it.start() }
        bgHandler = Handler(handlerThread.looper)
        floatingButtonManager = FloatingButtonManager(this) { onFloatingButtonClick() }
        watermarkRenderer = WatermarkRenderer(this)
        screenshotSaver = ScreenshotSaver(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()

        // MediaProjection data is required to show the floating button
        val hasProjectionData = intent?.hasExtra(EXTRA_RESULT_CODE) == true &&
            intent.hasExtra(EXTRA_DATA)

        if (!hasProjectionData) {
            // No projection data — start foreground to satisfy API contract, then stop
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            stopSelf()
            return START_NOT_STICKY
        }

        val resultCode = intent!!.getIntExtra(EXTRA_RESULT_CODE, 0)
        @Suppress("DEPRECATION")
        val data: Intent = intent.getParcelableExtra(EXTRA_DATA)!!

        // Start foreground with both SPECIAL_USE and MEDIA_PROJECTION types
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        screenCaptureManager = ScreenCaptureManager(
            this, resultCode, data, bgHandler
        ) {
            // Called when MediaProjection is stopped externally
            Handler(Looper.getMainLooper()).post { stopSelf() }
        }

        floatingButtonManager.show()
        isRunning = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        isRunning = false
        screenCaptureManager?.release()
        floatingButtonManager.hide()
        handlerThread.quitSafely()
        super.onDestroy()
    }

    private fun onFloatingButtonClick() {
        performCapture()
    }

    private fun performCapture() {
        val capture = screenCaptureManager ?: return
        val t0 = System.currentTimeMillis()

        val appName = watermarkRenderer.detectForegroundApp()
        val t1 = System.currentTimeMillis()
        Log.i(TAG, "PERF detectForegroundApp: ${t1 - t0}ms")

        floatingButtonManager.hide()
        val t2 = System.currentTimeMillis()
        Log.i(TAG, "PERF hide: ${t2 - t1}ms")

        val hideTime = System.currentTimeMillis()
        val restored = java.util.concurrent.atomic.AtomicBoolean(false)
        val mainHandler = Handler(Looper.getMainLooper())

        // Upper bound: button reappears at most 300ms after hide
        mainHandler.postDelayed({
            if (restored.compareAndSet(false, true)) {
                floatingButtonManager.showWithFlash()
            }
        }, 300)

        bgHandler.post {
            capture.captureScreen { bitmap ->
                if (bitmap == null) {
                    mainHandler.post {
                        if (restored.compareAndSet(false, true)) {
                            floatingButtonManager.show()
                        }
                    }
                    return@captureScreen
                }
                val watermarked = watermarkRenderer.render(bitmap, appName)
                screenshotSaver.save(watermarked) { _, _ ->
                    // Lower bound: ensure button stays hidden for at least 200ms
                    val elapsed = System.currentTimeMillis() - hideTime
                    val remaining = (200 - elapsed).coerceAtLeast(0)
                    mainHandler.postDelayed({
                        if (restored.compareAndSet(false, true)) {
                            floatingButtonManager.showWithFlash()
                        }
                    }, remaining)
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Snap Mark Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Notification for Snap Mark overlay service"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Snap Mark")
            .setContentText("Snap Mark is running")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }
}
