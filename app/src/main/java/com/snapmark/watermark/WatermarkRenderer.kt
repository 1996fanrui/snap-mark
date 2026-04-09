package com.snapmark.watermark

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.os.Process
import android.util.Log
import android.util.TypedValue
import com.snapmark.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders a watermark bar at the bottom of a screenshot showing the source app and timestamp.
 */
class WatermarkRenderer(private val context: Context) {

    companion object {
        private const val TAG = "WatermarkRenderer"
        private const val WATERMARK_HEIGHT_DP = 48f
        private const val TEXT_SIZE_SP = 14f
        private const val USAGE_QUERY_INTERVAL_MS = 30000L
        private const val UNKNOWN_APP = "[Unknown]"
    }

    /**
     * Renders a watermark bar at the bottom of the screenshot.
     * The original bitmap is recycled after rendering.
     */
    fun render(screenshot: Bitmap, appName: String = detectForegroundApp()): Bitmap {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
        val watermarkText = context.getString(R.string.watermark_format, appName, timestamp)

        val watermarkHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, WATERMARK_HEIGHT_DP, context.resources.displayMetrics
        ).toInt()

        val resultBitmap = Bitmap.createBitmap(
            screenshot.width,
            screenshot.height + watermarkHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(resultBitmap)

        canvas.drawBitmap(screenshot, 0f, 0f, null)

        val bgPaint = Paint().apply {
            color = Color.argb(180, 0, 0, 0)
            style = Paint.Style.FILL
        }
        canvas.drawRect(
            0f,
            screenshot.height.toFloat(),
            screenshot.width.toFloat(),
            (screenshot.height + watermarkHeight).toFloat(),
            bgPaint
        )

        val textPaint = Paint().apply {
            color = Color.WHITE
            textSize = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP, context.resources.displayMetrics
            )
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        val textX = screenshot.width / 2f
        val textY = screenshot.height + watermarkHeight / 2f -
            (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(watermarkText, textX, textY, textPaint)

        screenshot.recycle()
        return resultBitmap
    }

    /**
     * Detects the foreground app using UsageStatsManager.
     *
     * Uses queryEvents() to find the last ACTIVITY_RESUMED event (excluding SnapMark itself
     * and system packages like systemui/launcher). Falls back to queryUsageStats() for OEMs
     * where queryEvents() returns empty for third-party apps.
     *
     * Requires PACKAGE_USAGE_STATS permission and QUERY_ALL_PACKAGES (Android 11+ package
     * visibility restriction — without it, getApplicationInfo() throws NameNotFoundException
     * even for installed packages).
     */
    fun detectForegroundApp(): String {
        try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName
            )
            if (mode != AppOpsManager.MODE_ALLOWED) {
                Log.w(TAG, "Usage stats permission not granted")
                return UNKNOWN_APP
            }

            val usageStatsManager =
                context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val endTime = System.currentTimeMillis()
            val startTime = endTime - USAGE_QUERY_INTERVAL_MS

            val events = usageStatsManager.queryEvents(startTime, endTime)
            var lastPackageName: String? = null
            val event = UsageEvents.Event()

            val targetEventType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                UsageEvents.Event.ACTIVITY_RESUMED
            } else {
                @Suppress("DEPRECATION")
                UsageEvents.Event.MOVE_TO_FOREGROUND
            }

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                if (event.eventType == targetEventType &&
                    !isExcludedPackage(event.packageName)) {
                    lastPackageName = event.packageName
                }
            }

            // Fallback: queryUsageStats works on some OEMs where queryEvents returns empty
            if (lastPackageName == null) {
                lastPackageName = detectViaUsageStats(usageStatsManager, startTime, endTime)
            }

            if (lastPackageName == null) {
                Log.w(TAG, "No foreground app found via events or stats")
                return UNKNOWN_APP
            }

            return try {
                val appInfo = context.packageManager.getApplicationInfo(lastPackageName, 0)
                context.packageManager.getApplicationLabel(appInfo).toString()
            } catch (e: Exception) {
                // Fall back to package name if label lookup fails
                lastPackageName
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to detect foreground app", e)
            return UNKNOWN_APP
        }
    }

    private fun isExcludedPackage(pkg: String): Boolean {
        return pkg == "android" ||
            pkg.startsWith("com.android.systemui") ||
            pkg.startsWith("com.android.permissioncontroller") ||
            pkg.startsWith("com.google.android.permissioncontroller") ||
            pkg.startsWith("com.oplus.securitypermission") ||
            pkg.startsWith("com.coloros.securepay")
    }

    private fun detectViaUsageStats(
        usageStatsManager: UsageStatsManager,
        startTime: Long,
        endTime: Long
    ): String? {
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST, startTime, endTime
        ) ?: return null

        return stats
            .filter { !isExcludedPackage(it.packageName) }
            .maxByOrNull { it.lastTimeUsed }
            ?.packageName
    }
}
