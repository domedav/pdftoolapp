package com.domedav.pdftoolapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max

class ImageCropperTest {

    // A helper method that replicates the math logic inside ImageCropper.cropPerspective
    // to calculate the destination size based on source corners and original dimensions.
    private fun calculateDestinationDimensions(
        origW: Float,
        origH: Float,
        p0x: Float, p0y: Float, // Top-Left
        p1x: Float, p1y: Float, // Top-Right
        p2x: Float, p2y: Float, // Bottom-Right
        p3x: Float, p3y: Float  // Bottom-Left
    ): Pair<Float, Float> {
        val x0 = p0x * origW
        val y0 = p0y * origH
        val x1 = p1x * origW
        val y1 = p1y * origH
        val x2 = p2x * origW
        val y2 = p2y * origH
        val x3 = p3x * origW
        val y3 = p3y * origH

        val w1 = Math.hypot((x1 - x0).toDouble(), (y1 - y0).toDouble())
        val w2 = Math.hypot((x2 - x3).toDouble(), (y2 - y3).toDouble())
        val destW = max(w1, w2).toFloat()

        val h1 = Math.hypot((x3 - x0).toDouble(), (y3 - y0).toDouble())
        val h2 = Math.hypot((x2 - x1).toDouble(), (y2 - y1).toDouble())
        val destH = max(h1, h2).toFloat()

        return Pair(destW, destH)
    }

    @Test
    fun testCalculateDestinationDimensions_PerfectRectangle() {
        val origW = 1000f
        val origH = 2000f

        // Selecting the entire original image
        val (destW, destH) = calculateDestinationDimensions(
            origW, origH,
            0f, 0f, // Top-Left
            1f, 0f, // Top-Right
            1f, 1f, // Bottom-Right
            0f, 1f  // Bottom-Left
        )

        assertEquals(1000f, destW, 0.01f)
        assertEquals(2000f, destH, 0.01f)
    }

    @Test
    fun testCalculateDestinationDimensions_HalfSizeCrop() {
        val origW = 1000f
        val origH = 1000f

        // Selecting a crop of size 500x500 in the center
        val (destW, destH) = calculateDestinationDimensions(
            origW, origH,
            0.25f, 0.25f, // Top-Left
            0.75f, 0.25f, // Top-Right
            0.75f, 0.75f, // Bottom-Right
            0.25f, 0.75f  // Bottom-Left
        )

        assertEquals(500f, destW, 0.01f)
        assertEquals(500f, destH, 0.01f)
    }

    @Test
    fun testCalculateDestinationDimensions_DistortedDocument() {
        val origW = 1200f
        val origH = 1600f

        // Simulate scanning a document laying at an angle
        // Top edge: (100, 150) to (1100, 100) -> width is approx 1001
        // Bottom edge: (50, 1550) to (1150, 1500) -> width is approx 1101
        // Left edge: (100, 150) to (50, 1550) -> height is approx 1400
        // Right edge: (1100, 100) to (1150, 1500) -> height is approx 1400
        val p0x = 100f / origW
        val p0y = 150f / origH
        
        val p1x = 1100f / origW
        val p1y = 100f / origH
        
        val p2x = 1150f / origW
        val p2y = 1500f / origH
        
        val p3x = 50f / origW
        val p3y = 1550f / origH

        val (destW, destH) = calculateDestinationDimensions(
            origW, origH,
            p0x, p0y,
            p1x, p1y,
            p2x, p2y,
            p3x, p3y
        )

        // Width should match the max of top edge (approx 1001) and bottom edge (approx 1101)
        assertTrue(destW > 1100f && destW < 1102f)
        // Height should match the max of left edge (approx 1400.89) and right edge (approx 1400.89)
        assertTrue(destH > 1400f && destH < 1402f)
    }
}
