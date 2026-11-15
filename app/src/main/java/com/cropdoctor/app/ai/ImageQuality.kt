package com.cropdoctor.app.ai

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class QualityIssue { TOO_BLURRY, TOO_DARK, TOO_BRIGHT, GLARE, LEAF_TOO_SMALL }

data class QualityReport(
    val sharpness: Float,        // variance of the Laplacian on a 160px grayscale copy (higher = sharper)
    val brightness: Float,       // mean luminance 0..255
    val glare: Float,            // fraction of near-white pixels
    val leafCoverage: Float,     // fraction of plant-coloured pixels
    val issues: List<QualityIssue>,
) {
    val ok get() = issues.isEmpty()
}

/**
 * Cheap pre-checks so a bad photo is rejected BEFORE we show a confident-looking wrong diagnosis.
 * The numeric thresholds are starting points; tune them with a few real phone photos (see README).
 */
object ImageQuality {
    const val BLUR_MIN = 35f
    const val DARK_MAX = 60f
    const val BRIGHT_MIN = 215f
    const val GLARE_MAX = 0.12f
    const val LEAF_MIN = 0.12f
    private const val SIDE = 160

    fun analyze(bitmap: Bitmap): QualityReport {
        val side = min(bitmap.width, bitmap.height)
        val sq = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val small = Bitmap.createScaledBitmap(sq, SIDE, SIDE, true)
        val px = IntArray(SIDE * SIDE)
        small.getPixels(px, 0, SIDE, 0, 0, SIDE, SIDE)
        if (small !== sq && small !== bitmap) small.recycle()
        if (sq !== bitmap) sq.recycle()
        return analyze(px, SIDE, SIDE)
    }

    /** Pure function (ARGB pixels) so it can be unit tested without Android. */
    fun analyze(px: IntArray, w: Int, h: Int): QualityReport {
        val gray = FloatArray(w * h)
        var sum = 0.0
        var glare = 0
        var leaf = 0
        for (i in px.indices) {
            val r = (px[i] shr 16) and 0xFF
            val g = (px[i] shr 8) and 0xFF
            val b = px[i] and 0xFF
            val y = 0.299f * r + 0.587f * g + 0.114f * b
            gray[i] = y
            sum += y
            if (r > 245 && g > 245 && b > 245) glare++
            if (isPlantColour(r, g, b)) leaf++
        }
        val n = px.size.toFloat()
        val mean = (sum / n).toFloat()

        // variance of the 4-neighbour Laplacian
        var lSum = 0.0
        var lSq = 0.0
        var cnt = 0
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val c = gray[y * w + x]
            val l = gray[(y - 1) * w + x] + gray[(y + 1) * w + x] + gray[y * w + x - 1] + gray[y * w + x + 1] - 4 * c
            lSum += l
            lSq += l * l
            cnt++
        }
        val variance = if (cnt > 0) (lSq / cnt - (lSum / cnt) * (lSum / cnt)).toFloat() else 0f

        val issues = buildList {
            if (variance < BLUR_MIN) add(QualityIssue.TOO_BLURRY)
            if (mean < DARK_MAX) add(QualityIssue.TOO_DARK)
            if (mean > BRIGHT_MIN) add(QualityIssue.TOO_BRIGHT)
            if (glare / n > GLARE_MAX && mean <= BRIGHT_MIN) add(QualityIssue.GLARE)
            if (leaf / n < LEAF_MIN) add(QualityIssue.LEAF_TOO_SMALL)
        }
        return QualityReport(variance, mean, glare / n, leaf / n, issues)
    }

    /** Green, yellow-green, yellow or brown-ish: hue 20..170 degrees with some saturation and brightness. */
    fun isPlantColour(r: Int, g: Int, b: Int): Boolean {
        val mx = max(r, max(g, b)).toFloat()
        val mn = min(r, min(g, b)).toFloat()
        val d = mx - mn
        if (mx < 40f || d / max(mx, 1f) < 0.18f) return false
        val hue = when (mx) {
            r.toFloat() -> 60f * (((g - b) / d) % 6f)
            g.toFloat() -> 60f * (((b - r) / d) + 2f)
            else -> 60f * (((r - g) / d) + 4f)
        }.let { if (it < 0) it + 360f else it }
        return hue in 20f..170f
    }

    @Suppress("unused")
    private fun absf(v: Float) = abs(v)
}
