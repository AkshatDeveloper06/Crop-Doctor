package com.cropdoctor.app.ui.ask

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.app
import com.cropdoctor.app.knowledge.Lang
import com.cropdoctor.app.ui.ScreenBar
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr
import kotlinx.coroutines.launch
import java.util.Locale

private enum class Src { USER, OFFLINE, ONLINE, THINKING }

private data class Msg(val fromUser: Boolean, val text: String, val src: Src = if (fromUser) Src.USER else Src.OFFLINE)

/**
 * Question answering by voice or text, in Hindi or English. Offline it answers from the on-device knowledge base.
 * When a key is built in, the setting is on and the phone is online, it asks the online helper first (grounded in the
 * same knowledge-base entry) and quietly falls back to the offline answer if anything goes wrong.
 */
@Composable
fun AskScreen(contextClassId: String?, startWithMic: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val msgs = remember { mutableStateListOf<Msg>() }
    var input by remember { mutableStateOf("") }
    val list = rememberLazyListState()
    val speaker = ctx.app.speaker
    val recent = contextClassId?.takeIf { it.isNotBlank() }

    val scope = rememberCoroutineScope()
    val online = ctx.app.online
    var onlineReady by remember { mutableStateOf(false) }
    LaunchedEffect(msgs.size) { onlineReady = ctx.app.prefs.onlineAnswers && online.isConfigured && online.isOnline() }

    fun ask(q: String) {
        if (q.isBlank()) return
        val question = q.trim()
        input = ""
        msgs += Msg(true, question)
        val offline = ctx.app.advisor.answer(question, lang, recent)
        val canOnline = ctx.app.prefs.onlineAnswers && online.isConfigured && online.isOnline()
        if (!canOnline) {
            msgs += Msg(false, offline.text)
            speaker.speakAnswer(offline.text, lang)
            return
        }
        msgs += Msg(false, "…", Src.THINKING)
        val slot = msgs.lastIndex
        scope.launch {
            val grounding = (offline.classId ?: recent)?.let { ctx.app.kb.info(it) }?.takeIf { it.known }
            val r = online.ask(question, lang, grounding)
            val reply = r.getOrNull()?.let { Msg(false, it, Src.ONLINE) } ?: Msg(false, offline.text, Src.OFFLINE)
            if (slot in msgs.indices) msgs[slot] = reply
            speaker.speakAnswer(reply.text, lang)
        }
    }

    val voice = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) {
            r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { ask(it) }
        }
    }
    val startMic = {
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, if (lang == Lang.HI) "hi-IN" else Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, if (lang == Lang.HI) "बोलिए…" else "Speak now…")
        }
        runCatching { voice.launch(i) }
    }

    LaunchedEffect(Unit) {
        msgs += Msg(false, ctx.app.advisor.help(lang))
        if (startWithMic) startMic()
    }
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size - 1) }

    Column(Modifier.fillMaxSize().background(CdColors.Background).imePadding()) {
        ScreenBar(tr("Ask CropDoctor", "क्रॉपडॉक्टर से पूछें"), onBack)
        Text(
            if (onlineReady) tr("Online: answers use the internet, with the saved guide as a backup.", "ऑनलाइन: जवाब इंटरनेट से आते हैं, सहेजी गाइड बैकअप है।")
            else tr("Answers come from the guide saved on your phone: no internet needed.", "जवाब आपके फोन में सहेजी गाइड से आते हैं: इंटरनेट की ज़रूरत नहीं।"),
            Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.labelMedium, color = CdColors.Muted,
        )
        LazyColumn(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 8.dp), state = list, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(msgs) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.fromUser) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 320.dp).clip(RoundedCornerShape(18.dp))
                            .background(if (m.fromUser) CdColors.Green else CdColors.Surface)
                            .clickable(enabled = !m.fromUser) { speaker.speakAnswer(m.text, lang) }
                            .padding(12.dp),
                    ) {
                        Column {
                            Text(m.text, style = MaterialTheme.typography.bodyLarge, color = if (m.fromUser) androidx.compose.ui.graphics.Color.White else CdColors.Ink)
                            if (!m.fromUser) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.VolumeUp, null, tint = CdColors.Muted, modifier = Modifier.width(16.dp))
                                val label = when (m.src) {
                                    Src.ONLINE -> tr("AI answer, verify with your KVK · tap to hear", "AI जवाब, KVK से पुष्टि करें · सुनने के लिए दबाएँ")
                                    Src.THINKING -> tr("Thinking…", "सोच रहा हूँ…")
                                    else -> tr("Offline answer · tap to hear", "ऑफ़लाइन जवाब · सुनने के लिए दबाएँ")
                                }
                                Text(" $label", style = MaterialTheme.typography.labelMedium, color = CdColors.Muted)
                            }
                        }
                    }
                }
            }
        }
        val chips = listOf(
            tr("How to treat tomato late blight", "टमाटर पछेती झुलसा का इलाज"),
            tr("Why does rice blast happen", "धान में झोंका रोग क्यों होता है"),
            tr("When should I spray", "छिड़काव कब करें"),
            tr("Organic option for potato blight", "आलू झुलसा का जैविक उपाय"),
        )
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chips.forEach { c -> AssistChip(onClick = { ask(c) }, label = { Text(c) }) }
        }
        Row(Modifier.padding(12.dp).navigationBarsPadding(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                input, { input = it }, Modifier.weight(1f), shape = RoundedCornerShape(26.dp), singleLine = true,
                placeholder = { Text(tr("Type your question…", "अपना सवाल लिखें…")) },
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { if (input.isBlank()) startMic() else ask(input) }, modifier = Modifier.background(CdColors.Green, CircleShape)) {
                Icon(if (input.isBlank()) Icons.Filled.Mic else Icons.AutoMirrored.Filled.Send, tr("Send or speak", "भेजें या बोलें"), tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }
}
