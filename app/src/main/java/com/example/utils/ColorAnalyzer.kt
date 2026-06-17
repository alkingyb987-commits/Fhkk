package com.example.utils

import android.graphics.Color
import android.graphics.Rect
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import java.nio.ByteBuffer

/**
 * Data class representing an HSV color range.
 */
data class HsvRange(
    val name: String,
    val hueMin: Float,
    val hueMax: Float,
    val satMin: Float = 0.35f,
    val satMax: Float = 1.00f,
    val valMin: Float = 0.25f,
    val valMax: Float = 1.00f,
    val damage: Int
) {
    /**
     * Checks if a given HSV value falls within this range.
     */
    fun matches(h: Float, s: Float, v: Float): Boolean {
        // Handle Hue circular wrapping (e.g. 340 to 20 for Red)
        val hueMatches = if (hueMin <= hueMax) {
            h in hueMin..hueMax
        } else {
            h >= hueMin || h <= hueMax
        }
        return hueMatches && s in satMin..satMax && v in valMin..valMax
    }
}

class ColorAnalyzer(
    private val onColorSampled: (hue: Float, sat: Float, value: Float) -> Unit,
    private val onAnalysisCompleted: (hue: Float, s: Float, v: Float, matchDensityHead: Float, matchDensityBody: Float, matchDensityLimbs: Float) -> Unit
) : ImageAnalysis.Analyzer {

    companion object {
        const val ROI_SIZE = 50 // small fixed 50x50 box in the center
    }

    // Default target ranges (Red, Green, Blue)
    var headRange = HsvRange("Headshot (Red)", 340f, 20f, damage = 100)
    var bodyRange = HsvRange("Body (Green)", 80f, 150f, damage = 20)
    var limbsRange = HsvRange("Limbs (Blue)", 185f, 255f, damage = 10)

    override fun analyze(image: ImageProxy) {
        val width = image.width
        val height = image.height

        // Define our central bounding box
        val startX = (width - ROI_SIZE) / 2
        val startY = (height - ROI_SIZE) / 2

        if (image.format != android.graphics.ImageFormat.YUV_420_888) {
            image.close()
            return
        }

        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]

        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer

        val yRowStride = yPlane.rowStride
        val yPixelStride = yPlane.pixelStride
        val uRowStride = uPlane.rowStride
        val uPixelStride = uPlane.pixelStride
        val vRowStride = vPlane.rowStride
        val vPixelStride = vPlane.pixelStride

        var sumHue = 0f
        var sumSat = 0f
        var sumVal = 0f
        var validPixelCount = 0

        var headMatchPoints = 0
        var bodyMatchPoints = 0
        var limbsMatchPoints = 0

        val hsv = FloatArray(3)

        // Iterate through the ROI in steps of 2 to optimize performance even further
        val step = 2
        for (y in startY until (startY + ROI_SIZE) step step) {
            if (y >= height) break
            for (x in startX until (startX + ROI_SIZE) step step) {
                if (x >= width) break

                val yIndex = y * yRowStride + x * yPixelStride
                val uvX = x / 2
                val uvY = y / 2
                val uIndex = uvY * uRowStride + uvX * uPixelStride
                val vIndex = uvY * vRowStride + uvX * vPixelStride

                if (yIndex >= yBuffer.remaining()) continue
                if (uIndex >= uBuffer.remaining() || vIndex >= vBuffer.remaining()) continue

                val yVal = yBuffer.get(yIndex).toInt() and 0xFF
                val uVal = (uBuffer.get(uIndex).toInt() and 0xFF) - 128
                val vVal = (vBuffer.get(vIndex).toInt() and 0xFF) - 128

                // YUV to RGB Conversion
                var r = (yVal + 1.370705 * vVal).toInt()
                var g = (yVal - 0.337633 * uVal - 0.698001 * vVal).toInt()
                var b = (yVal + 1.732446 * uVal).toInt()

                r = r.coerceIn(0, 255)
                g = g.coerceIn(0, 255)
                b = b.coerceIn(0, 255)

                Color.RGBToHSV(r, g, b, hsv)
                val h = hsv[0]
                val s = hsv[1]
                val v = hsv[2]

                sumHue += h
                sumSat += s
                sumVal += v
                validPixelCount++

                // Count targets matched
                if (headRange.matches(h, s, v)) headMatchPoints++
                if (bodyRange.matches(h, s, v)) bodyMatchPoints++
                if (limbsRange.matches(h, s, v)) limbsMatchPoints++
            }
        }

        if (validPixelCount > 0) {
            val avgHue = sumHue / validPixelCount
            val avgSat = sumSat / validPixelCount
            val avgVal = sumVal / validPixelCount

            val densityHead = headMatchPoints.toFloat() / validPixelCount
            val densityBody = bodyMatchPoints.toFloat() / validPixelCount
            val densityLimbs = limbsMatchPoints.toFloat() / validPixelCount

            // Pass samples back to main thread
            onColorSampled(avgHue, avgSat, avgVal)
            onAnalysisCompleted(avgHue, avgSat, avgVal, densityHead, densityBody, densityLimbs)
        }

        image.close()
    }
}
