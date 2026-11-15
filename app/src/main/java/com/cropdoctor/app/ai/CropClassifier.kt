package com.cropdoctor.app.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import android.util.Log
import com.cropdoctor.app.BuildConfig
import com.google.gson.Gson
import org.json.JSONObject
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.CompatibilityList
import org.tensorflow.lite.gpu.GpuDelegate
import java.io.Closeable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

/** assets/model_meta.json. Every field has a default so a partial file still loads. */
data class ModelMeta(
    val architecture: String = "unknown",
    val input_size: Int = 0,                 // filled in from the model itself
    val num_classes: Int = 0,
    val confident_threshold: Float = 0.7f,
    val unsure_threshold: Float = 0.4f,
    val precision: String = "",
    val model_size_mb: Double = 0.0,
    val val_accuracy: Double? = null,
    val val_accuracy_float: Double? = null,
    val note: String = "",
)

/** Crops the model cannot judge both ways, so the result screen can say so instead of over-claiming. */
enum class CoverageGap { NO_HEALTHY_CLASS, ONLY_HEALTHY_CLASS }

sealed interface ModelState {
    data class Ready(
        val meta: ModelMeta,
        val labels: List<String>,                  // class ids, e.g. "Tomato___late_blight"
        val delegate: String,
        val gaps: Map<String, CoverageGap> = emptyMap(),
    ) : ModelState

    data class Missing(val reason: String) : ModelState
}

/**
 * On-device classifier, loaded from assets only (no network):
 *   model.tflite     any MobileNet-style image classifier; the input size and uint8 / float32 input are read from the
 *                    model itself, pixels are fed as raw 0..255 RGB (rescaling is inside the model)
 *   labels.txt       the model's own output order, one label per line
 *   label_map.json   optional: model label -> app class id ("Crop___disease_id"); several labels may share one id and
 *                    their probabilities are added up
 *   model_meta.json  thresholds and the numbers shown in Settings
 * Everything the rest of the app sees is expressed in app class ids.
 */
class CropClassifier(private val context: Context) : Closeable {
    val state: ModelState
    private var interpreter: Interpreter? = null
    private var gpu: GpuDelegate? = null
    private val lock = Any()

    private var inSize = 0
    private var inUint8 = false
    private var input: ByteBuffer? = null
    private var pixels: IntArray? = null
    private var outBuf: ByteBuffer? = null
    private var outType = DataType.FLOAT32
    private var outScale = 1f
    private var outZero = 0
    private var nRaw = 0
    private var groupOf = IntArray(0)            // raw output index -> index in `ids`
    private var ids: List<String> = emptyList()  // app class ids, unique
    private var rawLabels: List<String> = emptyList()
    private var dbgCalls = 0

    init {
        state = try {
            val raw = context.assets.open("labels.txt").bufferedReader().readLines().map { it.trim() }.filter { it.isNotEmpty() }
            val meta = runCatching {
                Gson().fromJson(context.assets.open("model_meta.json").bufferedReader().readText(), ModelMeta::class.java)
            }.getOrNull() ?: ModelMeta()
            val map = runCatching {
                val o = JSONObject(context.assets.open("label_map.json").bufferedReader().readText())
                o.keys().asSequence().associateWith { o.getString(it) }
            }.getOrDefault(emptyMap())
            val mapped = raw.map { map[it] ?: it }
            ids = mapped.distinct()
            groupOf = IntArray(raw.size) { ids.indexOf(mapped[it]) }
            nRaw = raw.size
            rawLabels = raw

            val model = mapModel()
            var (interp, delegateName) = createInterpreter(model)
            interpreter = interp
            prepareBuffers(interp)
            check(nRaw == outputClasses(interp)) { "model outputs ${outputClasses(interp)} classes but labels.txt has $nRaw" }
            // The first GPU run compiles shaders and can fail on some drivers: find out now, not on the first photo.
            try {
                runRaw(interp)
            } catch (t: Throwable) {
                if (delegateName != "GPU") throw t
                interp.close(); gpu?.close(); gpu = null
                interp = createInterpreter(model, forceCpu = true).first
                interpreter = interp
                delegateName = "CPU"
                runRaw(interp)
            }
            ModelState.Ready(meta.copy(num_classes = ids.size, input_size = inSize), ids, delegateName, coverageGaps(ids))
        } catch (e: java.io.FileNotFoundException) {
            ModelState.Missing("model.tflite / labels.txt not found in assets")
        } catch (e: Throwable) {
            ModelState.Missing(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun mapModel(): MappedByteBuffer {
        val fd = context.assets.openFd("model.tflite")
        fd.use {
            java.io.FileInputStream(it.fileDescriptor).channel.use { ch ->
                return ch.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
            }
        }
    }

    private fun createInterpreter(model: MappedByteBuffer, forceCpu: Boolean = false): Pair<Interpreter, String> {
        if (!forceCpu) {
            try {
                if (!CompatibilityList().use { it.isDelegateSupportedOnThisDevice }) throw IllegalStateException("GPU delegate not supported here")
                val g = GpuDelegate()
                val interp = Interpreter(model, Interpreter.Options().addDelegate(g))
                gpu = g
                return interp to "GPU"
            } catch (_: Throwable) {
                // fall through to CPU
            }
        }
        val interp = Interpreter(model, Interpreter.Options().setNumThreads(4))
        return interp to "CPU"
    }

    private fun outputClasses(interp: Interpreter) = interp.getOutputTensor(0).shape().last()

    /** Reads the input/output layout from the model, so a different size or a quantised model just works. */
    private fun prepareBuffers(interp: Interpreter) {
        val inT = interp.getInputTensor(0)
        inSize = inT.shape()[1]
        inUint8 = inT.dataType() == DataType.UINT8
        input = ByteBuffer.allocateDirect(inSize * inSize * 3 * if (inUint8) 1 else 4).order(ByteOrder.nativeOrder())
        pixels = IntArray(inSize * inSize)
        val outT = interp.getOutputTensor(0)
        outType = outT.dataType()
        outScale = outT.quantizationParams().scale
        outZero = outT.quantizationParams().zeroPoint
        outBuf = ByteBuffer.allocateDirect(outputClasses(interp) * if (outType == DataType.FLOAT32) 4 else 1).order(ByteOrder.nativeOrder())
    }

    private fun coverageGaps(classIds: List<String>): Map<String, CoverageGap> {
        val healthy = HashSet<String>()
        val disease = HashSet<String>()
        for (id in classIds) {
            if (ClassId.isBackground(id)) continue
            (if (ClassId.isHealthy(id)) healthy else disease) += ClassId.crop(id)
        }
        val out = HashMap<String, CoverageGap>()
        for (c in healthy + disease) {
            if (c !in healthy) out[c] = CoverageGap.NO_HEALTHY_CLASS
            else if (c !in disease) out[c] = CoverageGap.ONLY_HEALTHY_CLASS
        }
        return out
    }

    val ready: ModelState.Ready? get() = state as? ModelState.Ready

    /**
     * Probabilities over the app class ids for one image. Call off the main thread.
     *  camera = true  : a camera frame or photo. It is brought down to live-frame size, then the middle CROP_FRACTION of
     *                   it is sampled nearest-neighbour into the model input (this is how the model reads best).
     *  camera = false : a picked gallery photo: the whole centre square, smoothly resized.
     * An image with almost no plant-coloured pixels is reported as "not a leaf".
     */
    fun classify(bitmap: Bitmap, camera: Boolean = true): FloatArray {
        ready ?: error("model not loaded")
        synchronized(lock) {
            val plant = if (camera) fillCamera(bitmap) else fillSquare(bitmap)
            val bg = ids.indexOfFirst { ClassId.isBackground(it) }
            if (bg >= 0 && plant < MIN_PLANT_FRACTION) return FloatArray(ids.size).also { it[bg] = 1f }
            val interp = interpreter ?: error("closed")
            val raw = runRaw(interp)
            if (BuildConfig.DEBUG) debugLog(raw)
            return collapse(normalise(raw))
        }
    }

    fun verdict(probs: FloatArray): Verdict {
        val r = ready ?: return Verdict(null, 0f, 0f, Certainty.UNKNOWN, emptyList())
        return Ranker.rank(probs, r.labels, r.meta.confident_threshold, r.meta.unsure_threshold)
    }

    /** Debug builds only: every 8th inference, log the model's raw top-3. */
    private fun debugLog(raw: FloatArray) {
        if (++dbgCalls % 8 != 1) return
        val top = raw.indices.sortedByDescending { raw[it] }.take(3).joinToString(" | ") { "${rawLabels[it]} %.2f".format(raw[it]) }
        Log.d("CDModel", top)
    }

    /** Average latency in ms for `runs` inferences with the active delegate, plus the CPU-only figure for comparison. */
    fun benchmark(runs: Int = 30): Map<String, Double> {
        val r = ready ?: return emptyMap()
        val result = LinkedHashMap<String, Double>()
        val dummy = Bitmap.createBitmap(max(inSize, 64), max(inSize, 64), Bitmap.Config.ARGB_8888)
        dummy.eraseColor(0xFF4C8C3A.toInt())
        repeat(3) { classify(dummy) }
        var t0 = SystemClock.elapsedRealtimeNanos()
        repeat(runs) { classify(dummy) }
        result[r.delegate] = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6 / runs

        if (r.delegate == "GPU") {
            runCatching {
                synchronized(lock) {
                    val cpu = Interpreter(mapModel(), Interpreter.Options().setNumThreads(4))
                    repeat(3) { runRaw(cpu) }
                    t0 = SystemClock.elapsedRealtimeNanos()
                    repeat(runs) { runRaw(cpu) }
                    result["CPU"] = (SystemClock.elapsedRealtimeNanos() - t0) / 1e6 / runs
                    cpu.close()
                }
            }
        }
        dummy.recycle()
        return result
    }

    /** Green or yellow-green: green clearly above blue and not far below red. Brown, grey, white and blue fail. */
    private fun looksLikePlant(r: Int, g: Int, b: Int) = g >= r * 0.9f && g - b > 20 && g > 40

    private fun put(buf: ByteBuffer, r: Int, g: Int, b: Int) {
        if (inUint8) buf.put(r.toByte()).put(g.toByte()).put(b.toByte())
        else buf.putFloat(r.toFloat()).putFloat(g.toFloat()).putFloat(b.toFloat())
    }

    /** Brings a big camera photo down to roughly live-frame size (area-averaged, not aliased). */
    private fun limit(src: Bitmap, maxSide: Int): Bitmap {
        var cur = src
        while (max(cur.width, cur.height) >= maxSide * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        val longest = max(cur.width, cur.height)
        if (longest > maxSide) {
            val k = maxSide.toFloat() / longest
            val next = Bitmap.createScaledBitmap(cur, (cur.width * k).toInt().coerceAtLeast(1), (cur.height * k).toInt().coerceAtLeast(1), true)
            if (cur !== src) cur.recycle()
            cur = next
        }
        return cur
    }

    /**
     * Camera input: the middle CROP_FRACTION of the frame, one source pixel per model pixel (nearest neighbour).
     * Returns the share of sampled pixels that look like plant tissue.
     */
    private fun fillCamera(src: Bitmap): Float {
        val s = inSize
        val b = limit(src, LIVE_MAX_SIDE)
        val w = b.width
        val h = b.height
        val all = IntArray(w * h)
        b.getPixels(all, 0, w, 0, 0, w, h)
        if (b !== src) b.recycle()
        val side = (min(w, h) * CROP_FRACTION).toInt().coerceAtLeast(1)
        val x0 = (w - side) / 2f
        val y0 = (h - side) / 2f
        val step = side.toFloat() / s
        val buf = input!!
        buf.rewind()
        var plant = 0
        for (oy in 0 until s) {
            val sy = (y0 + (oy + 0.5f) * step).toInt().coerceIn(0, h - 1)
            for (ox in 0 until s) {
                val sx = (x0 + (ox + 0.5f) * step).toInt().coerceIn(0, w - 1)
                val p = all[sy * w + sx]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val bl = p and 0xFF
                if (looksLikePlant(r, g, bl)) plant++
                put(buf, r, g, bl)
            }
        }
        return plant / (s * s).toFloat()
    }

    /** Gallery input: the whole centre square, smoothly resized. Returns the plant-coloured share. */
    private fun fillSquare(src: Bitmap): Float {
        val s = inSize
        val side = min(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = shrink(sq, s)
        val px = pixels!!
        scaled.getPixels(px, 0, s, 0, 0, s, s)
        if (scaled !== sq) scaled.recycle()
        if (sq !== src) sq.recycle()
        val buf = input!!
        buf.rewind()
        var plant = 0
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (looksLikePlant(r, g, b)) plant++
            put(buf, r, g, b)
        }
        return plant / (s * s).toFloat()
    }

    /**
     * Resizes the square crop to s x s. A single bilinear resize from a big photo samples only 4 source pixels per
     * output pixel and aliases badly, so halve repeatedly (2x2 averaging) and only then do the final small resize.
     */
    private fun shrink(sq: Bitmap, s: Int): Bitmap {
        var cur = sq
        while (cur.width >= s * 2) {
            val next = Bitmap.createScaledBitmap(cur, cur.width / 2, cur.height / 2, true)
            if (cur !== sq) cur.recycle()
            cur = next
        }
        if (cur.width == s) return cur
        val out = Bitmap.createScaledBitmap(cur, s, s, true)
        if (cur !== sq) cur.recycle()
        return out
    }

    /** Runs the model on whatever is in the input buffer and decodes the output (float or quantised) to floats. */
    private fun runRaw(interp: Interpreter): FloatArray {
        val ib = input!!
        val ob = outBuf!!
        ib.rewind(); ob.rewind()
        interp.run(ib, ob)
        ob.rewind()
        return FloatArray(nRaw) {
            when (outType) {
                DataType.FLOAT32 -> ob.float
                DataType.UINT8 -> ((ob.get().toInt() and 0xFF) - outZero) * outScale
                else -> (ob.get().toInt() - outZero) * outScale
            }
        }
    }

    /** Probabilities pass through (re-scaled to sum to 1); logits (anything outside 0..1) are soft-maxed. */
    private fun normalise(v: FloatArray): FloatArray {
        if (v.any { it < 0f || it > 1.0001f }) {
            val mx = v.max()
            var tot = 0f
            for (i in v.indices) { v[i] = exp(v[i] - mx); tot += v[i] }
            for (i in v.indices) v[i] /= max(tot, 1e-9f)
        } else {
            val sum = v.sum()
            if (sum > 0f) for (i in v.indices) v[i] /= sum
        }
        return v
    }

    /** Adds up model labels that share one app class id. */
    private fun collapse(raw: FloatArray): FloatArray {
        val out = FloatArray(ids.size)
        for (i in raw.indices) out[groupOf[i]] += raw[i]
        return out
    }

    companion object {
        /** The model reads the middle 72% of the camera frame; the scan screen's guide box is drawn to match. */
        const val CROP_FRACTION = 0.72f
        /** Live camera frames are about this size; bigger photos are brought down to it first. */
        private const val LIVE_MAX_SIDE = 640
        /** Less plant-coloured area than this in the sampled region means "no leaf here". */
        private const val MIN_PLANT_FRACTION = 0.12f
    }

    override fun close() {
        synchronized(lock) {
            interpreter?.close(); interpreter = null
            gpu?.close(); gpu = null
        }
    }
}
