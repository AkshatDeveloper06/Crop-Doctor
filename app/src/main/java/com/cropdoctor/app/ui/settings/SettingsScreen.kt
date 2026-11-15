package com.cropdoctor.app.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.ai.ModelState
import com.cropdoctor.app.app
import com.cropdoctor.app.knowledge.Lang
import com.cropdoctor.app.ui.ModelMissingNotice
import com.cropdoctor.app.ui.ScreenBar
import com.cropdoctor.app.ui.SectionCard
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr
import com.cropdoctor.app.work.Reminders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val scope = rememberCoroutineScope()
    var bench by remember { mutableStateOf<Map<String, Double>?>(null) }
    var running by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val notif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val state = ctx.app.classifier.state

    Column(Modifier.navigationBarsPadding()) {
        ScreenBar(tr("Settings", "सेटिंग"), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SectionCard(tr("Language", "भाषा")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(lang == Lang.EN, { ctx.app.prefs.setLang(Lang.EN) }, { Text("English") })
                    FilterChip(lang == Lang.HI, { ctx.app.prefs.setLang(Lang.HI) }, { Text("हिन्दी") })
                }
            }

            SectionCard(tr("Disease model", "रोग पहचान मॉडल")) {
                when (state) {
                    is ModelState.Missing -> ModelMissingNotice(state.reason)
                    is ModelState.Ready -> {
                        val m = state.meta
                        Row { Text(tr("Architecture", "आर्किटेक्चर") + ": ", color = CdColors.Muted); Text(m.architecture) }
                        Text("${tr("Classes", "वर्ग")}: ${state.labels.size - 1}   ·   ${tr("Size", "आकार")}: ${m.model_size_mb} MB   ·   ${m.precision}")
                        Text("${tr("Running on", "चल रहा है")}: ${state.delegate}")
                        m.val_accuracy?.let {
                            Text("${tr("Accuracy on the model's own validation set", "मॉडल के अपने सत्यापन सेट पर सटीकता")}: ${"%.1f".format(it * 100)}%")
                        }
                        Text(
                            tr("This is not a test on field photos, so real-world accuracy is lower. Always confirm with your KVK.", "यह खेत की फोटो पर परीक्षण नहीं है, इसलिए असली हालत में सटीकता कम होगी। हमेशा KVK से पुष्टि करें।"),
                            style = MaterialTheme.typography.labelMedium, color = CdColors.Muted,
                        )
                        OutlinedButton(
                            enabled = !running,
                            onClick = { running = true; scope.launch { bench = withContext(Dispatchers.Default) { ctx.app.classifier.benchmark() }; running = false } },
                            shape = RoundedCornerShape(14.dp),
                        ) { Text(if (running) tr("Measuring…", "माप रहा हूँ…") else tr("Benchmark this phone", "इस फोन पर गति मापें")) }
                        bench?.forEach { (k, v) -> Text("$k: ${"%.1f".format(v)} ms / ${tr("image", "चित्र")}", style = MaterialTheme.typography.bodyLarge) }
                    }
                }
            }

            SectionCard(tr("Reminders", "याद दिलाना")) {
                Text(tr("Re-check reminders are local notifications; no internet needed.", "दोबारा जाँच की याद स्थानीय सूचना है; इंटरनेट नहीं चाहिए।"), style = MaterialTheme.typography.bodyMedium)
                val title = tr("CropDoctor test reminder", "क्रॉपडॉक्टर परीक्षण याद")
                val body = tr("Reminders are working.", "याद दिलाना चालू है।")
                OutlinedButton(onClick = {
                    if (Build.VERSION.SDK_INT >= 33) notif.launch(Manifest.permission.POST_NOTIFICATIONS)
                    Reminders.schedule(ctx, "test", TimeUnit.MINUTES.toMillis(1), title, body)
                }, shape = RoundedCornerShape(14.dp)) { Text(tr("Send a test reminder in 1 minute", "1 मिनट में परीक्षण याद भेजें")) }
            }

            val onlineConfigured = ctx.app.online.isConfigured
            var onlineOn by remember { mutableStateOf(ctx.app.prefs.onlineAnswers) }
            if (onlineConfigured) SectionCard(tr("Online answers", "ऑनलाइन जवाब")) {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text(
                        tr("Use the internet for Ask answers and extra advice on the result screen when it is available. The saved guide is always the backup, and advice you fetched stays on this phone.", "इंटरनेट होने पर Ask के जवाब और नतीजे पर अतिरिक्त सलाह इंटरनेट से लें। सहेजी गाइड हमेशा बैकअप है, और ली गई सलाह इसी फोन में रहती है।"),
                        Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(onlineOn, { onlineOn = it; ctx.app.prefs.onlineAnswers = it })
                }
                Text(tr("Only your question text, or the crop and disease name, is sent. Photos, location and scan history are never sent.", "केवल आपका सवाल, या फसल और रोग का नाम भेजा जाता है। फोटो, स्थान और स्कैन इतिहास कभी नहीं भेजे जाते।"), style = MaterialTheme.typography.labelMedium, color = CdColors.Muted)
            }

            SectionCard(tr("Privacy", "गोपनीयता")) {
                Text(
                    if (onlineConfigured) tr("Leaf photos, scans and fields stay on this phone. Two optional features go online: the spray-weather card (approximate coordinates only) and AI advice (question text, or crop and disease name, only).", "पत्ती की फोटो, स्कैन और खेत इसी फोन में रहते हैं। दो वैकल्पिक सुविधाएँ ऑनलाइन जाती हैं: मौसम कार्ड (केवल मोटे निर्देशांक) और AI सलाह (केवल सवाल, या फसल और रोग का नाम)।")
                    else tr("Leaf photos, scans and fields stay on this phone. The only online feature is the optional spray-weather card, which sends approximate coordinates and nothing else.", "पत्ती की फोटो, स्कैन और खेत इसी फोन में रहते हैं। एकमात्र ऑनलाइन सुविधा मौसम कार्ड है, जो केवल मोटे निर्देशांक भेजता है, और कुछ नहीं।"),
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { confirmClear = true }, shape = RoundedCornerShape(14.dp)) { Text(tr("Delete all scan history", "सारा स्कैन इतिहास हटाएँ"), color = CdColors.Red) }
            }

            Text(tr("CropDoctor gives first-level advice and can be wrong. Always confirm with your KVK or agriculture officer and follow product labels.", "क्रॉपडॉक्टर प्रारंभिक सलाह देता है और गलत भी हो सकता है। हमेशा KVK या कृषि अधिकारी से पुष्टि करें और उत्पाद का लेबल मानें।"), style = MaterialTheme.typography.labelMedium, color = CdColors.Muted)
            Spacer(Modifier.width(1.dp))
        }
    }
    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(tr("Delete all scans?", "सभी स्कैन हटाएँ?")) },
            text = { Text(tr("Photos and results will be removed from this phone.", "फोटो और नतीजे इस फोन से हट जाएँगे।")) },
            confirmButton = { TextButton(onClick = { ctx.app.store.clearScans(); confirmClear = false }) { Text(tr("Delete", "हटाएँ"), color = CdColors.Red) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(tr("Cancel", "रद्द करें")) } },
        )
    }
}
