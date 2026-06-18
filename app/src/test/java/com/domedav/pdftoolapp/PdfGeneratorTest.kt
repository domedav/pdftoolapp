package com.domedav.pdftoolapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PdfGeneratorTest {

    @Test
    fun testFileNameGenerationLogic() {
        // Test the logic that would be inside createPdfFile
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "PDF_$timestamp.pdf"
        
        assertNotNull(fileName)
        assertTrue(fileName.startsWith("PDF_"))
        assertTrue(fileName.endsWith(".pdf"))
    }
    
    @Test
    fun testDirectoryCreationLogic() {
        val testDir = File("build/tmp/test_dir")
        if (!testDir.exists()) {
            val created = testDir.mkdirs()
            assertTrue(created)
        }
        assertTrue(testDir.exists())
        testDir.deleteRecursively()
    }

    @Test
    fun testQualityIndexMapping() {
        // Assert quality settings mapped to dimensions
        assertEquals(3, Consts.QUALITY_VALUES.size)
        assertEquals(480, Consts.QUALITY_VALUES[0])
        assertEquals(720, Consts.QUALITY_VALUES[1])
        assertEquals(1920, Consts.QUALITY_VALUES[2])
    }

    @Test
    fun testMaxDimensionLookupLogic() {
        val defaultMaxDim = 2048
        
        // Compact (Low)
        val lowDim = Consts.QUALITY_VALUES.getOrNull(0) ?: defaultMaxDim
        assertEquals(480, lowDim)

        // Standard (Medium)
        val medDim = Consts.QUALITY_VALUES.getOrNull(1) ?: defaultMaxDim
        assertEquals(720, medDim)

        // Best (High)
        val highDim = Consts.QUALITY_VALUES.getOrNull(2) ?: defaultMaxDim
        assertEquals(1920, highDim)

        // Out of bounds fallback
        val fallbackDim = Consts.QUALITY_VALUES.getOrNull(99) ?: defaultMaxDim
        assertEquals(defaultMaxDim, fallbackDim)
    }
}
