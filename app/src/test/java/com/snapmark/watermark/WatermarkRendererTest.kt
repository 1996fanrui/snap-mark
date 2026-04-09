package com.snapmark.watermark

import android.content.Context
import android.graphics.Bitmap
import android.util.DisplayMetrics
import android.util.TypedValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Tests for WatermarkRenderer output bitmap dimensions.
 */
@RunWith(RobolectricTestRunner::class)
class WatermarkRendererTest {

    @Test
    fun render_outputWidthMatchesInput() {
        val context = RuntimeEnvironment.getApplication()
        val renderer = WatermarkRenderer(context)
        val input = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)

        val result = renderer.render(input)

        assertEquals(1080, result.width)
        result.recycle()
    }

    @Test
    fun render_outputTallerThanInputByWatermarkHeight() {
        val context = RuntimeEnvironment.getApplication()
        val renderer = WatermarkRenderer(context)
        val inputHeight = 1920
        val input = Bitmap.createBitmap(1080, inputHeight, Bitmap.Config.ARGB_8888)

        val result = renderer.render(input)

        // Watermark height is 48dp; in Robolectric default density the exact pixel value
        // depends on the display metrics, but it must be positive
        val watermarkHeight = result.height - inputHeight
        assertTrue("Watermark height should be positive, was $watermarkHeight", watermarkHeight > 0)

        // Verify approximately 48dp: at mdpi (1.0) it's 48px, at other densities it scales
        val expectedHeight = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 48f, context.resources.displayMetrics
        ).toInt()
        assertEquals(expectedHeight, watermarkHeight)

        result.recycle()
    }
}
