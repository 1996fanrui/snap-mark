package com.snapmark

import android.Manifest
import android.app.Activity
import android.app.AppOpsManager
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Main entry point. Handles permission management and MediaProjection request flow.
 * After base permissions are granted, shows a "Start Capture" button that triggers
 * MediaProjection. Only after projection is granted does it start OverlayService
 * with the projection data.
 */
class MainActivity : Activity() {

    companion object {
        private const val STORAGE_PERMISSION_REQUEST_CODE = 100
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 101
    }

    private lateinit var container: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 96, 48, 48)
        }
        setContentView(container)

        // Request POST_NOTIFICATIONS on API 33+ (non-blocking)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }

    }


    override fun onResume() {
        super.onResume()
        updatePermissionUI()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_REQUEST_CODE) {
            updatePermissionUI()
        }
        // POST_NOTIFICATIONS denial is non-blocking, no action needed
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MEDIA_PROJECTION_REQUEST_CODE) {
            handleMediaProjectionResult(resultCode, data)
        }
    }

    private fun launchMediaProjectionRequest() {
        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        // On API 34+, lock to entire screen to skip the "single app / entire screen" chooser
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(
                android.media.projection.MediaProjectionConfig.createConfigForDefaultDisplay()
            )
        } else {
            projectionManager.createScreenCaptureIntent()
        }
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, MEDIA_PROJECTION_REQUEST_CODE)
    }

    private fun handleMediaProjectionResult(resultCode: Int, data: Intent?) {
        if (resultCode == RESULT_OK && data != null) {
            // Start OverlayService with MediaProjection data so it can show the floating button
            val serviceIntent = Intent(this, OverlayService::class.java).apply {
                putExtra(OverlayService.EXTRA_RESULT_CODE, resultCode)
                putExtra(OverlayService.EXTRA_DATA, data)
            }
            startForegroundService(serviceIntent)
            OverlayService.isRunning = true
        }
        updatePermissionUI()
    }

    /**
     * Checks all required permissions and either shows permission UI or starts the service.
     */
    private fun updatePermissionUI() {
        container.removeAllViews()

        val missingOverlay = !Settings.canDrawOverlays(this)
        val missingUsageStats = !hasUsageStatsPermission()
        val missingStorage = needsStoragePermission() && !hasStoragePermission()

        if (!missingOverlay && !missingUsageStats && !missingStorage) {
            // All base permissions granted — show Start Capture button for MediaProjection
            showReadyUI()
            return
        }

        // Show permission request UI
        if (missingOverlay) {
            addPermissionSection(
                "Snap Mark needs overlay permission to display the capture button.",
                "Grant Overlay Permission"
            ) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            }
        }

        if (missingUsageStats) {
            addPermissionSection(
                "Snap Mark needs usage stats permission to identify the current app.",
                "Grant Usage Stats Permission"
            ) {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }

        if (missingStorage) {
            addPermissionSection(
                "Snap Mark needs storage permission to save screenshots.",
                "Grant Storage Permission"
            ) {
                requestPermissions(
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun showReadyUI() {
        container.removeAllViews()

        val title = TextView(this).apply {
            text = "Snap Mark"
            textSize = 24f
            setPadding(0, 24, 0, 32)
        }
        container.addView(title)

        if (OverlayService.isRunning) {
            val status = TextView(this).apply {
                text = "Screen capture is active. The floating button is on screen — tap it to take screenshots."
                textSize = 16f
                setPadding(0, 0, 0, 16)
            }
            container.addView(status)

            val hint = TextView(this).apply {
                text = "You can leave this screen — the floating button stays on top."
                textSize = 14f
                setTextColor(0xFF888888.toInt())
                setPadding(0, 0, 0, 32)
            }
            container.addView(hint)

            val stopButton = Button(this).apply {
                text = "Stop Capture"
                setOnClickListener {
                    stopService(Intent(this@MainActivity, OverlayService::class.java))
                    OverlayService.isRunning = false
                    updatePermissionUI()
                }
            }
            container.addView(stopButton)
        } else {
            val status = TextView(this).apply {
                text = "All permissions granted. Tap the button below to enable screen capture."
                textSize = 16f
                setPadding(0, 0, 0, 24)
            }
            container.addView(status)

            val startButton = Button(this).apply {
                text = "Start Capture"
                setOnClickListener { launchMediaProjectionRequest() }
            }
            container.addView(startButton)
        }

        val hint = TextView(this).apply {
            text = "A floating button will appear after you grant screen capture permission."
            textSize = 14f
            setTextColor(0xFF888888.toInt())
            setPadding(0, 16, 0, 0)
        }
        container.addView(hint)
    }

    private fun addPermissionSection(
        explanation: String,
        buttonLabel: String,
        action: () -> Unit
    ) {
        val textView = TextView(this).apply {
            text = explanation
            textSize = 16f
            setPadding(0, 24, 0, 12)
        }
        container.addView(textView)

        val button = Button(this).apply {
            text = buttonLabel
            setOnClickListener { action() }
        }
        container.addView(button)
    }

    private fun hasUsageStatsPermission(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Storage runtime permission is only needed on API 26-28.
     * API 29+ uses scoped storage.
     */
    private fun needsStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT in Build.VERSION_CODES.O..Build.VERSION_CODES.P
    }

    private fun hasStoragePermission(): Boolean {
        return checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }
}

private const val MEDIA_PROJECTION_REQUEST_CODE = 200
