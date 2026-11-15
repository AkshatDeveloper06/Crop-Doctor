package com.cropdoctor.app.ocr

import android.graphics.Bitmap
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.TextRecognition
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads pesticide labels on-device (ML Kit Devanagari model reads Hindi + English).
 * A dose is "locked" only after the same value is read on two consecutive frames, which filters out OCR flicker.
 */
class LabelReader(private val onDose: (Dose, String) -> Unit, private val onText: (String) -> Unit = {}) : ImageAnalysis.Analyzer {
    private val recognizer = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
    private val busy = AtomicBoolean(false)
    private var last: Dose? = null
    private var lastAt = 0L

    @androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
    override fun analyze(image: ImageProxy) {
        val now = System.currentTimeMillis()
        val media = image.image
        if (media == null || now - lastAt < 450 || !busy.compareAndSet(false, true)) {
            image.close(); return
        }
        lastAt = now
        val input = InputImage.fromMediaImage(media, image.imageInfo.rotationDegrees)
        recognizer.process(input)
            .addOnSuccessListener { result ->
                onText(result.text)
                // try per line first (cleanest), then the whole block of text
                val candidates = result.textBlocks.flatMap { b -> b.lines.map { it.text } } + result.text.replace('\n', ' ')
                var found: Pair<Dose, String>? = null
                for (c in candidates) {
                    val d = LabelParser.parse(c)
                    if (d != null) { found = d to c; break }
                }
                if (found != null) {
                    val p = last
                    if (p != null && p.unit == found.first.unit && Math.abs(p.perLitre - found.first.perLitre) < 0.01) {
                        onDose(found.first, found.second)
                    }
                    last = found.first
                } else last = null
            }
            .addOnCompleteListener { busy.set(false); image.close() }
    }

    /** One-shot read of a still image (gallery pick). */
    fun readStill(bitmap: Bitmap, done: (Dose?, String) -> Unit) {
        recognizer.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener { r ->
                val cands = r.textBlocks.flatMap { b -> b.lines.map { it.text } } + r.text.replace('\n', ' ')
                for (c in cands) LabelParser.parse(c)?.let { done(it, c); return@addOnSuccessListener }
                done(null, r.text)
            }
            .addOnFailureListener { done(null, "") }
    }

    fun close() = recognizer.close()
}
