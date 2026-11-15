package com.cropdoctor.app.ai

/** Class ids look like "Tomato___late_blight". */
object ClassId {
    fun crop(id: String) = id.substringBefore("___")
    fun disease(id: String) = id.substringAfter("___", "")
    fun isHealthy(id: String) = disease(id) == "healthy"
    fun isBackground(id: String) = crop(id) == "Background"
}

enum class Certainty { CONFIDENT, UNSURE, UNKNOWN, NOT_A_LEAF }

data class Alt(val classId: String, val p: Float)

data class Verdict(
    val classId: String?,           // null when NOT_A_LEAF / model missing
    val confidence: Float,          // model probability of this exact class
    val cropConfidence: Float,      // model probability mass of the whole crop
    val certainty: Certainty,
    val alternatives: List<Alt>,
)

/**
 * Crop-first ranking.
 *
 * Probability for one crop is often split across several look-alike diseases, so the top single class can lose to a
 * different crop whose mass sits on one class. We first decide the CROP by pooling all of its classes, then pick the
 * disease inside that crop. Thresholds come from model_meta.json; the model is not temperature-calibrated, so they are deliberately conservative.
 */
object Ranker {
    fun rank(probs: FloatArray, labels: List<String>, confident: Float, unsure: Float): Verdict {
        require(probs.size == labels.size) { "probs ${probs.size} vs labels ${labels.size}" }
        val bgIdx = labels.indexOfFirst { ClassId.isBackground(it) }
        val bgP = if (bgIdx >= 0) probs[bgIdx] else 0f

        val cropMass = HashMap<String, Float>()
        for (i in labels.indices) {
            if (i == bgIdx) continue
            val c = ClassId.crop(labels[i])
            cropMass[c] = (cropMass[c] ?: 0f) + probs[i]
        }
        val bestCrop = cropMass.maxByOrNull { it.value }
        if (bestCrop == null || bgP >= 0.5f || bgP >= bestCrop.value) {
            return Verdict(null, bgP, 0f, Certainty.NOT_A_LEAF, emptyList())
        }
        var best = -1
        for (i in labels.indices) {
            if (i == bgIdx || ClassId.crop(labels[i]) != bestCrop.key) continue
            if (best < 0 || probs[i] > probs[best]) best = i
        }
        val conf = probs[best]
        val alts = labels.indices
            .filter { it != best && it != bgIdx }
            .sortedByDescending { probs[it] }
            .take(3)
            .map { Alt(labels[it], probs[it]) }
        val certainty = when {
            conf >= confident -> Certainty.CONFIDENT
            conf >= unsure -> Certainty.UNSURE
            else -> Certainty.UNKNOWN
        }
        return Verdict(labels[best], conf, bestCrop.value, certainty, alts)
    }
}

/** Averages the last few frames so the live label does not flicker. */
class Smoother(private val window: Int = 5) {
    private val buf = ArrayDeque<FloatArray>()

    @Synchronized
    fun push(p: FloatArray): FloatArray {
        buf.addLast(p)
        while (buf.size > window) buf.removeFirst()
        val out = FloatArray(p.size)
        for (b in buf) for (i in out.indices) out[i] += b[i]
        for (i in out.indices) out[i] /= buf.size
        return out
    }

    @Synchronized
    fun reset() = buf.clear()
}

/** Exponential running average of the live probabilities: calm label, quick to follow a new leaf. */
class EmaSmoother(private val alpha: Float = 0.35f) {
    private var prev: FloatArray? = null

    @Synchronized
    fun push(p: FloatArray): FloatArray {
        val old = prev
        val out = if (old == null || old.size != p.size) p.copyOf() else FloatArray(p.size) { alpha * p[it] + (1 - alpha) * old[it] }
        prev = out
        return out.copyOf()
    }

    @Synchronized
    fun reset() { prev = null }
}

/**
 * Which verdict a captured photo is saved with. The farmer tapped capture while looking at a live answer, and that
 * answer (already smoothed over several frames) is what they expect to be saved. The still photo is classified only when
 * there was no usable live answer, for example if the tap came before the first frame was analysed.
 */
fun pickVerdict(live: Verdict?, classifyStill: () -> Verdict): Verdict =
    if (live != null && live.certainty != Certainty.NOT_A_LEAF) live else classifyStill()
