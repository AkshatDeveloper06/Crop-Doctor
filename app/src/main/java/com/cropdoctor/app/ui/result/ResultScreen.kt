package com.cropdoctor.app.ui.result

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.ai.Certainty
import com.cropdoctor.app.audio.SpeechScript
import com.cropdoctor.app.ai.ClassId
import com.cropdoctor.app.ai.CoverageGap
import com.cropdoctor.app.app
import com.cropdoctor.app.knowledge.Info
import com.cropdoctor.app.knowledge.Lang
import com.cropdoctor.app.report.ReportPdf
import com.cropdoctor.app.ui.Bullets
import com.cropdoctor.app.ui.Notice
import com.cropdoctor.app.ui.Photo
import com.cropdoctor.app.ui.ScreenBar
import com.cropdoctor.app.ui.SectionCard
import com.cropdoctor.app.ui.SeverityChip
import com.cropdoctor.app.ui.home.SprayCard
import com.cropdoctor.app.ui.severityColor
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.t
import com.cropdoctor.app.ui.theme.tr
import com.cropdoctor.app.work.Reminders
import java.util.concurrent.TimeUnit

@Composable
fun ResultScreen(scanId: String, onBack: () -> Unit, onAsk: (String) -> Unit, onDose: () -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val scans by ctx.app.store.scans.collectAsState()
    val rec = scans.firstOrNull { it.id == scanId }
    if (rec == null) {
        Column(Modifier.padding(24.dp)) { ScreenBar(tr("Result", "नतीजा"), onBack); Text(tr("This scan no longer exists.", "यह स्कैन अब मौजूद नहीं है।")) }
        return
    }
    val kb = ctx.app.kb
    val info: Info? = rec.classId?.let { kb.info(it) }
    val photo = remember(rec.id) { ctx.app.store.loadPhoto(rec) }
    val speaker = ctx.app.speaker
    val speaking by speaker.speaking.collectAsState()
    val missingVoice by speaker.missingVoice.collectAsState()
    var reminderSet by remember(rec.id) { mutableStateOf(rec.reminderDays) }
    val fieldName = ctx.app.store.field(rec.fieldId)?.name

    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(rec.id) { speaker.stop() }

    Column(Modifier.background(CdColors.Background)) {
        ScreenBar(tr("Diagnosis", "निदान"), onBack) {
            IconButton(onClick = { ctx.app.store.deleteScan(rec.id); Reminders.cancel(ctx, rec.id); onBack() }) {
                Icon(Icons.Filled.Delete, tr("Delete", "हटाएँ"), tint = CdColors.Muted)
            }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Photo(photo, Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(20.dp)))

            if (info == null) {
                Notice(tr("No diagnosis could be made. Retake the photo in daylight with one leaf filling the frame.", "निदान नहीं हो सका। दिन की रोशनी में एक पत्ती फ्रेम में भरकर फोटो दोबारा लें।"), bad = true)
                return@Column
            }

            // ---- headline
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(info.cropName.t(), style = MaterialTheme.typography.titleMedium, color = CdColors.Muted)
                Text(info.name.t(), style = MaterialTheme.typography.headlineMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SeverityChip(info.severity)
                    if (info.spreadsFast) Text(tr("Spreads fast", "तेज़ी से फैलता है"), style = MaterialTheme.typography.labelMedium, color = CdColors.Red)
                    if (fieldName != null) Text("· $fieldName", style = MaterialTheme.typography.labelMedium, color = CdColors.Muted)
                }
                val pct = (rec.confidence * 100).toInt()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LinearProgressIndicator(
                        progress = { rec.confidence.coerceIn(0f, 1f) },
                        modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)),
                        color = severityColor(info.severity), trackColor = CdColors.Outline,
                        gapSize = 0.dp, drawStopIndicator = {},
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("$pct%", style = MaterialTheme.typography.titleMedium)
                }
            }

            // ---- honesty
            when (rec.certainty) {
                Certainty.UNSURE -> Notice(tr("I am not fully sure. Retake in daylight with one leaf filling the frame, and check the other possibilities below.", "मैं पूरी तरह पक्का नहीं हूँ। दिन की रोशनी में एक पत्ती भरकर फोटो दोबारा लें और नीचे दूसरी संभावनाएँ देखें।"))
                Certainty.UNKNOWN -> Notice(tr("Low confidence: treat this as a guess. Show the leaf to your nearest KVK / agriculture officer.", "विश्वास कम है: इसे अंदाज़ा मानें। पत्ती नज़दीकी KVK / कृषि अधिकारी को दिखाएँ।"), bad = true)
                else -> {}
            }
            if (rec.qualityNotes.isNotEmpty()) {
                Notice(qualityText(rec.qualityNotes.first()))
            }
            when (ctx.app.classifier.ready?.gaps?.get(ClassId.crop(rec.classId ?: ""))) {
                CoverageGap.NO_HEALTHY_CLASS -> Notice(tr("For this crop the model learned only diseased leaves, so it cannot confirm a leaf is healthy. Treat this as a possibility and check with your KVK.", "इस फसल के लिए मॉडल ने केवल रोगी पत्तियाँ सीखी हैं, इसलिए यह स्वस्थ पत्ती की पुष्टि नहीं कर सकता। इसे संभावना मानें और KVK से पूछें।"))
                CoverageGap.ONLY_HEALTHY_CLASS -> Notice(tr("For this crop the model learned only healthy leaves, so it cannot detect diseases. Do not rely on a healthy result.", "इस फसल के लिए मॉडल ने केवल स्वस्थ पत्तियाँ सीखी हैं, इसलिए यह रोग नहीं पहचान सकता। 'स्वस्थ' नतीजे पर भरोसा न करें।"))
                null -> {}
            }

            // ---- actions row
            val spokenQuality = rec.qualityNotes.firstOrNull()?.let { qualityText(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = {
                        if (speaking) speaker.stop() else {
                            val alternatives = rec.alternatives.take(2).map { a -> kb.info(a.classId).let { i -> "${i.cropName.get(lang)} ${i.name.get(lang)}" } }
                            speaker.speak(
                                SpeechScript.diagnosis(
                                    info = info, lang = lang, confidence = rec.confidence, certainty = rec.certainty,
                                    gap = ctx.app.classifier.ready?.gaps?.get(ClassId.crop(info.classId)),
                                    qualityNote = spokenQuality, alternatives = alternatives,
                                    aiAdvice = ctx.app.online.cachedInsight(info.classId, lang),
                                ),
                                lang,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(if (speaking) Icons.Filled.Stop else Icons.AutoMirrored.Filled.VolumeUp, null)
                    Spacer(Modifier.width(6.dp))
                    Text(if (speaking) tr("Stop", "रोकें") else tr("Listen", "सुनें"))
                }
                val reportLabels = ReportPdf.Labels(
                    confidence = tr("Confidence", "विश्वास"), why = tr("Why it happens", "ऐसा क्यों होता है"),
                    doNow = tr("What to do today", "आज क्या करें"), organic = tr("Organic / low-cost options", "जैविक / कम लागत विकल्प"),
                    chemical = tr("Chemical options (read the label)", "रासायनिक विकल्प (लेबल पढ़ें)"), prevent = tr("Prevention", "रोकथाम"),
                    disclaimer = tr("First-level advisory generated on the phone. Confirm doses with your KVK or agriculture officer and follow the product label.", "यह फोन पर बना प्रारंभिक सुझाव है। मात्रा की पुष्टि KVK या कृषि अधिकारी से करें और उत्पाद का लेबल मानें।"),
                )
                val reportNote = when (rec.certainty) {
                    Certainty.UNSURE -> tr("Not fully sure", "पूरी तरह पक्का नहीं")
                    Certainty.UNKNOWN -> tr("Low confidence", "कम विश्वास")
                    else -> null
                }
                OutlinedButton(
                    onClick = {
                        val f = ReportPdf.build(ctx, info, (rec.confidence * 100).toInt(), reportNote, photo, fieldName, rec.timestamp, lang, reportLabels)
                        ctx.startActivity(ReportPdf.shareIntent(ctx, f))
                    },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Filled.Share, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Share report", "रिपोर्ट भेजें"))
                }
            }
            if (missingVoice != null) {
                Notice(tr("No voice installed for this language. Install it in Settings → Text-to-speech.", "इस भाषा की आवाज़ फोन में नहीं है। सेटिंग → टेक्स्ट-टू-स्पीच में इंस्टॉल करें।"))
            }

            // ---- follow-up reminder
            if (info.severity > 0) {
                SectionCard(tr("Re-check reminder", "दोबारा जाँच की याद"), Icons.Filled.Alarm, CdColors.Green) {
                    if (reminderSet != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.TaskAlt, null, tint = CdColors.Green)
                            Spacer(Modifier.width(8.dp))
                            Text(tr("I will remind you in $reminderSet days to scan again.", "मैं $reminderSet दिन बाद दोबारा स्कैन करने की याद दिलाऊँगा।"), style = MaterialTheme.typography.bodyLarge)
                        }
                    } else {
                        Text(tr("Scan this plant again in ${info.followUpDays} days to see if the treatment is working.", "इलाज असर कर रहा है या नहीं, यह जानने के लिए ${info.followUpDays} दिन बाद फिर स्कैन करें।"), style = MaterialTheme.typography.bodyLarge)
                        val rTitle = tr("Time to re-check your crop", "फसल की दोबारा जाँच का समय")
                        val rText = tr("${info.cropName.en}: ${info.name.en}. Scan again to see if it is improving.", "${info.cropName.hi}: ${info.name.hi}। सुधार देखने के लिए फिर स्कैन करें।")
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                            val days = info.followUpDays
                            Reminders.schedule(ctx, rec.id, TimeUnit.DAYS.toMillis(days.toLong()), rTitle, rText)
                            ctx.app.store.updateScan(rec.id) { it.copy(reminderDays = days) }
                            reminderSet = days
                        }, shape = RoundedCornerShape(14.dp)) { Text(tr("Remind me in ${info.followUpDays} days", "${info.followUpDays} दिन में याद दिलाएँ")) }
                    }
                }
            }

            // ---- content
            info.cause.t().takeIf { it.isNotBlank() }?.let { c ->
                SectionCard(tr("Why it happens", "ऐसा क्यों होता है"), Icons.Filled.Visibility) {
                    Text(c, style = MaterialTheme.typography.bodyLarge)
                    info.symptoms.t().takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted) }
                }
            }
            if (info.actions.get(lang).isNotEmpty()) SectionCard(tr("What to do today", "आज क्या करें"), Icons.Filled.TaskAlt) { Bullets(info.actions.get(lang)) }
            if (info.organic.get(lang).isNotEmpty()) SectionCard(tr("Organic / low-cost options", "जैविक / कम लागत विकल्प"), Icons.Filled.Eco) { Bullets(info.organic.get(lang)) }
            if (info.chemical.get(lang).isNotEmpty()) {
                SectionCard(tr("Chemical options", "रासायनिक विकल्प"), Icons.Filled.Science, CdColors.Amber) {
                    Bullets(info.chemical.get(lang))
                    Text(tr("Doses are typical label values. Always read your product's label and confirm with your KVK. Wear gloves and a mask.", "मात्राएँ सामान्य लेबल मान हैं। अपने उत्पाद का लेबल पढ़ें और KVK से पुष्टि करें। दस्ताने और मास्क पहनें।"), style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
                    OutlinedButton(onClick = onDose, shape = RoundedCornerShape(14.dp)) {
                        Icon(Icons.Filled.Calculate, null); Spacer(Modifier.width(6.dp)); Text(tr("Scan label & calculate dose", "लेबल स्कैन करें और मात्रा निकालें"))
                    }
                }
            }
            if (info.prevent.get(lang).isNotEmpty()) SectionCard(tr("Prevent it next time", "अगली बार कैसे रोकें"), Icons.Filled.Shield) { Bullets(info.prevent.get(lang)) }

            AiAdviceCard(info.classId, info)

            if (rec.severity > 0) SprayCard()

            if (rec.alternatives.isNotEmpty() && rec.certainty != Certainty.CONFIDENT) {
                SectionCard(tr("Other possibilities", "दूसरी संभावनाएँ"), null, CdColors.Muted) {
                    rec.alternatives.forEach {
                        val i = kb.info(it.classId)
                        Text("• ${i.cropName.get(lang)}: ${i.name.get(lang)}  (${(it.p * 100).toInt()}%)", style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            OutlinedButton(onClick = { onAsk(rec.classId ?: "") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.AutoMirrored.Filled.Chat, null); Spacer(Modifier.width(8.dp)); Text(tr("Ask a question about this", "इस बारे में सवाल पूछें"))
            }
            Text(
                tr("First-level advice from an on-device model. It can be wrong; confirm with your KVK or agriculture officer.", "यह फोन के मॉडल का प्रारंभिक सुझाव है। यह गलत भी हो सकता है; KVK या कृषि अधिकारी से पुष्टि करें।"),
                style = MaterialTheme.typography.labelMedium, color = CdColors.Muted,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun qualityText(name: String): String = when (name) {
    "TOO_BLURRY" -> tr("The photo is blurry, which lowers accuracy. Hold steady and retake.", "फोटो धुंधली है, इससे सटीकता घटती है। फोन स्थिर रखकर दोबारा लें।")
    "TOO_DARK" -> tr("The photo is dark. Retake in daylight.", "फोटो अंधेरी है। दिन की रोशनी में दोबारा लें।")
    "TOO_BRIGHT" -> tr("The photo is over-bright. Shade the leaf and retake.", "फोटो बहुत तेज़ रोशनी में है। पत्ती पर छाया करके दोबारा लें।")
    "GLARE" -> tr("There is glare on the leaf. Tilt and retake.", "पत्ती पर चमक है। थोड़ा तिरछा करके दोबारा लें।")
    else -> tr("The leaf is small in the photo. Move closer and retake.", "फोटो में पत्ती छोटी है। पास जाकर दोबारा लें।")
}
