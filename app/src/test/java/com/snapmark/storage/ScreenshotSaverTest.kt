package com.snapmark.storage

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Date

/**
 * Tests for screenshot file naming logic using actual ScreenshotSaver methods.
 */
class ScreenshotSaverTest {

    @Test
    fun fileNaming_matchesExpectedPattern() {
        val filename = ScreenshotSaver.generateFilename(Date())

        val pattern = Regex("^SnapMark_\\d{8}_\\d{6}\\.jpg$")
        assertTrue(
            "Filename '$filename' should match pattern SnapMark_<yyyyMMdd_HHmmss>.jpg",
            pattern.matches(filename)
        )
    }

    @Test
    fun dateDir_matchesExpectedPattern() {
        val dateDir = ScreenshotSaver.generateDateDir(Date())

        val pattern = Regex("^\\d{4}-\\d{2}-\\d{2}$")
        assertTrue(
            "Date directory '$dateDir' should match pattern yyyy-MM-dd",
            pattern.matches(dateDir)
        )
    }
}
