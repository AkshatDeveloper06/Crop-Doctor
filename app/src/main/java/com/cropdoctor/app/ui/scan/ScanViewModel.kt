package com.cropdoctor.app.ui.scan

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import com.cropdoctor.app.CropDoctorApp
import com.cropdoctor.app.ai.Certainty
import com.cropdoctor.app.ai.ImageQuality
import com.cropdoctor.app.ai.QualityReport
import com.cropdoctor.app.ai.EmaSmoother
import com.cropdoctor.app.ai.Verdict
import com.cropdoctor.app.ai.pickVerdict
import com.cropdoctor.app.data.AltRec
import com.cropdoctor.app.data.ScanRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LiveState(val verdict: Verdict? = null, val quality: QualityReport? = null, val frameW: Int = 0, val frameH: Int = 0)

sealed interface Outcome {
    data class Saved(val scanId: String) : Outcome
    data object NotALeaf : Outcome
    data object ModelMissing : Outcome
}

class ScanViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CropDoctorApp
    private val smoother = EmaSmoother(0.35f)
    private val _live = MutableStateFlow(LiveState())
    val live: StateFlow<LiveState> = _live.asStateFlow()
    private var lastFrame = 0L

    // The live answer at the moment the farmer taps capture. While a capture is being processed the live label is frozen
    // and this answer is what gets saved, so the result matches what was on screen when the button was pressed.
    @Volatile private var frozen = false
    @Volatile private var snapshot: Verdict? = null
    @Volatile private var lastVerdict: Verdict? = null
    @Volatile private var lastVerdictAt = 0L

    val modelReady get() = app.classifier.ready != null

    /** Called from the camera analyzer thread. Throttled to ~5 fps; stale frames are dropped by CameraX. */
    fun onFrame(bitmap: Bitmap) {
        val now = System.currentTimeMillis()
        if (frozen || now - lastFrame < 200 || app.classifier.ready == null) return
        lastFrame = now
        runCatching {
            val q = ImageQuality.analyze(bitmap)
            val p = smoother.push(app.classifier.classify(bitmap))
            val v = app.classifier.verdict(p)
            if (frozen) return@runCatching              // a capture started while this frame was being analysed
            lastVerdict = v
            lastVerdictAt = now
            _live.value = LiveState(v, q, bitmap.width, bitmap.height)
        }
    }

    /** Call when the capture button is pressed: remember what the farmer is looking at and stop updating the label. */
    fun freeze() {
        snapshot = lastVerdict?.takeIf { System.currentTimeMillis() - lastVerdictAt < 2000 }
        frozen = true
    }

    /** Call when a capture did not lead to a saved scan, so the live label resumes. */
    fun unfreeze() {
        frozen = false
        snapshot = null
        smoother.reset()
    }

    fun resetLive() { unfreeze(); lastVerdict = null; _live.value = LiveState() }

    /** Full-quality diagnosis of a captured/picked photo: mirrored-image averaging + quality notes + save. */
    suspend fun diagnose(bitmap: Bitmap, fieldId: String?, guided: Boolean = false): Outcome = withContext(Dispatchers.Default) {
        val clf = app.classifier
        if (clf.ready == null) return@withContext Outcome.ModelMissing
        val quality = ImageQuality.analyze(bitmap)
        val verdict = if (guided) pickVerdict(snapshot) { clf.verdict(clf.classify(bitmap, camera = true)) }
        else clf.verdict(clf.classify(bitmap, camera = false))
        if (verdict.certainty == Certainty.NOT_A_LEAF) return@withContext Outcome.NotALeaf

        val id = app.store.newId()
        val file = app.store.savePhoto(id, bitmap)
        val severity = verdict.classId?.let { app.kb.info(it).severity } ?: -1
        app.store.addScan(
            ScanRecord(
                id = id, fieldId = fieldId, timestamp = System.currentTimeMillis(), imageFile = file,
                classId = verdict.classId, confidence = verdict.confidence, certainty = verdict.certainty,
                severity = severity,
                alternatives = verdict.alternatives.map { AltRec(it.classId, it.p) },
                qualityNotes = quality.issues.map { it.name },
            ),
        )
        fieldId?.let { app.prefs.lastFieldId = it }
        Outcome.Saved(id)
    }
}
