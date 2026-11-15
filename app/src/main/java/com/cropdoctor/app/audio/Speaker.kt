package com.cropdoctor.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import com.cropdoctor.app.knowledge.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Offline text-to-speech (Android's built-in engine). Hindi needs a Hindi voice installed on the phone.
 *
 * Long text is cut into sentence-sized chunks and queued, because the engine rejects input over its length limit (so long
 * advice used to be cut off). [speaking] stays true until the LAST chunk finishes, and a newer speak() or stop() makes
 * everything from the older one irrelevant.
 */
class Speaker(context: Context) {
    private var ready = false
    private var tts: TextToSpeech? = null
    private val _speaking = MutableStateFlow(false)
    val speaking: StateFlow<Boolean> = _speaking.asStateFlow()
    private val _missingVoice = MutableStateFlow<Lang?>(null)
    /** Set to the language whose voice is not installed, so the UI can tell the farmer how to install it. */
    val missingVoice: StateFlow<Lang?> = _missingVoice.asStateFlow()

    @Volatile private var generation = 0
    @Volatile private var lastId = ""

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { if (current(id)) _speaking.value = true }
                override fun onDone(id: String?) { if (current(id) && id == lastId) _speaking.value = false }
                @Deprecated("Deprecated in Java") override fun onError(id: String?) { if (current(id)) _speaking.value = false }
                override fun onStop(id: String?, interrupted: Boolean) { if (current(id)) _speaking.value = false }
            })
        }
    }

    private fun current(id: String?) = id != null && id.startsWith("cd-$generation-")

    /** Speaks `text` in the app language. Units, symbols and ranges are spoken as words. */
    fun speak(text: String, lang: Lang) {
        val t = tts ?: return
        if (!ready) return
        val locale = if (lang == Lang.HI) Locale("hi", "IN") else Locale.US
        val r = t.setLanguage(locale)
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            _missingVoice.value = lang
            return
        }
        _missingVoice.value = null
        val limit = minOf(TextToSpeech.getMaxSpeechInputLength() - 100, 400).coerceAtLeast(100)
        val chunks = SpeechScript.chunks(SpeechScript.forSpeech(text, lang), limit)
        if (chunks.isEmpty()) return
        generation++
        val gen = generation
        lastId = "cd-$gen-${chunks.size - 1}"
        t.setSpeechRate(0.92f)
        chunks.forEachIndexed { i, c ->
            t.speak(c, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, null, "cd-$gen-$i")
        }
    }

    /** For an answer that already contains the information: the stock "confirm with your KVK" clause is not spoken. */
    fun speakAnswer(text: String, lang: Lang) = speak(SpeechScript.withoutReferral(text), lang)

    fun stop() {
        generation++            // anything still reporting from the old queue is ignored
        tts?.stop()
        _speaking.value = false
    }

    fun shutdown() {
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
