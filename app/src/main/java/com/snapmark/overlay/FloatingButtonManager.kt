package com.snapmark.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.WindowMetrics
import android.widget.TextView
import kotlin.math.abs

/**
 * Manages the floating overlay button: creation, display, drag, and click handling.
 */
class FloatingButtonManager(
    private val context: Context,
    private val onClick: () -> Unit
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val buttonSizePx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 56f, context.resources.displayMetrics
    ).toInt()
    private val marginPx = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, 16f, context.resources.displayMetrics
    ).toInt()

    private var isShowing = false
    private var initialized = false
    private lateinit var button: TextView
    private lateinit var buttonBackground: GradientDrawable
    private lateinit var params: WindowManager.LayoutParams

    fun show() {
        if (isShowing) return

        if (!initialized) {
            val (screenWidth, screenHeight) = getScreenSize()

            buttonBackground = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x66FFFFFF) // 40% opacity white background
            }
            button = TextView(context).apply {
                background = buttonBackground
                text = "\uD83D\uDCF7" // Camera emoji
                textSize = 24f
                gravity = android.view.Gravity.CENTER
                contentDescription = "SnapMark Capture Button"
            }

            params = WindowManager.LayoutParams(
                buttonSizePx,
                buttonSizePx,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = screenWidth - buttonSizePx - marginPx
                y = (screenHeight - buttonSizePx) / 2
            }

            setupTouchListener(screenWidth, screenHeight)
            initialized = true
        }

        windowManager.addView(button, params)
        isShowing = true
    }

    fun hide() {
        if (!isShowing) return
        windowManager.removeViewImmediate(button)
        isShowing = false
    }

    /**
     * Alias for show() — the disappear/reappear itself serves as visual feedback.
     */
    fun showWithFlash() {
        show()
    }

    private fun setupTouchListener(screenWidth: Int, screenHeight: Int) {
        val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

        button.setOnTouchListener(object : View.OnTouchListener {
            private var initialTouchX = 0f
            private var initialTouchY = 0f
            private var initialX = 0
            private var initialY = 0

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        initialX = params.x
                        initialY = params.y
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        params.x = (initialX + dx).coerceIn(0, screenWidth - buttonSizePx)
                        params.y = (initialY + dy).coerceIn(0, screenHeight - buttonSizePx)
                        windowManager.updateViewLayout(button, params)
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        val totalDistance = abs(event.rawX - initialTouchX) +
                            abs(event.rawY - initialTouchY)
                        if (totalDistance < touchSlop) {
                            onClick()
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun getScreenSize(): Pair<Int, Int> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics: WindowMetrics = windowManager.currentWindowMetrics
            val bounds = metrics.bounds
            Pair(bounds.width(), bounds.height())
        } else {
            @Suppress("DEPRECATION")
            val display = windowManager.defaultDisplay
            val size = android.graphics.Point()
            @Suppress("DEPRECATION")
            display.getSize(size)
            Pair(size.x, size.y)
        }
    }
}
