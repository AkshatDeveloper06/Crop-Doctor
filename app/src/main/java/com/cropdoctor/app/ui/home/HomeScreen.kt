package com.cropdoctor.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Grass
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.ai.ModelState
import com.cropdoctor.app.app
import com.cropdoctor.app.knowledge.Lang
import com.cropdoctor.app.ui.BigTile
import com.cropdoctor.app.ui.ModelMissingNotice
import com.cropdoctor.app.ui.OfflineBadge
import com.cropdoctor.app.ui.Photo
import com.cropdoctor.app.ui.SeverityChip
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr

@Composable
fun HomeScreen(
    onScan: () -> Unit, onFields: () -> Unit, onLabel: () -> Unit, onAsk: () -> Unit, onVoice: () -> Unit,
    onSettings: () -> Unit, onOpenScan: (String) -> Unit,
) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val scans by ctx.app.store.scans.collectAsState()
    var chooseLang by remember { mutableStateOf(!ctx.app.prefs.languageChosen) }
    val state = ctx.app.classifier.state

    Column(Modifier.statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("CropDoctor", "क्रॉपडॉक्टर"), style = MaterialTheme.typography.headlineMedium, color = CdColors.GreenDark)
                Text(tr("Your crop's doctor, in your pocket", "आपकी फसल का डॉक्टर, आपकी जेब में"), style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
            }
            LangToggle(lang) { ctx.app.prefs.setLang(it) }
            IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, tr("Settings", "सेटिंग"), tint = CdColors.Muted) }
        }
        Row { OfflineBadge() }

        if (state is ModelState.Missing) ModelMissingNotice(state.reason)

        Button(
            onClick = onScan, Modifier.fillMaxWidth().height(96.dp), shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CdColors.Green),
        ) {
            Icon(Icons.Filled.CameraAlt, null, Modifier.size(36.dp))
            Spacer(Modifier.width(14.dp))
            Column {
                Text(tr("Scan a leaf", "पत्ती स्कैन करें"), style = MaterialTheme.typography.titleLarge)
                Text(tr("Find the problem and the remedy", "समस्या और उपाय जानें"), style = MaterialTheme.typography.bodyMedium)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(tr("My fields", "मेरे खेत"), Icons.Filled.Grass, Modifier.weight(1f), onFields)
            BigTile(tr("Pesticide dose", "दवा की मात्रा"), Icons.Filled.Science, Modifier.weight(1f), onLabel)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BigTile(tr("Ask a question", "सवाल पूछें"), Icons.AutoMirrored.Filled.Chat, Modifier.weight(1f), onAsk)
            BigTile(tr("Ask by voice", "बोलकर पूछें"), Icons.Filled.Mic, Modifier.weight(1f), onVoice)
        }

        SprayCard()

        if (scans.isNotEmpty()) {
            Text(tr("Recent scans", "हाल के स्कैन"), style = MaterialTheme.typography.titleMedium)
            scans.take(5).forEach { s ->
                val info = s.classId?.let { ctx.app.kb.info(it) }
                Card(
                    Modifier.fillMaxWidth().clickable { onOpenScan(s.id) }, shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CdColors.Surface), border = BorderStroke(1.dp, CdColors.Outline),
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val bmp = remember(s.id) { ctx.app.store.loadPhoto(s, 200) }
                        Photo(bmp, Modifier.size(60.dp).background(CdColors.GreenLight, RoundedCornerShape(12.dp)))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(info?.name?.get(lang) ?: tr("Unknown", "अज्ञात"), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text("${info?.cropName?.get(lang) ?: ""} · ${(s.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
                        }
                        if (s.severity >= 0) SeverityChip(s.severity)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    if (chooseLang) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("भाषा चुनें / Choose language") },
            text = { Text("आप बाद में सेटिंग में बदल सकते हैं।\nYou can change this later in Settings.") },
            confirmButton = { TextButton(onClick = { ctx.app.prefs.setLang(Lang.HI); chooseLang = false }) { Text("हिन्दी") } },
            dismissButton = { TextButton(onClick = { ctx.app.prefs.setLang(Lang.EN); chooseLang = false }) { Text("English") } },
        )
    }
}

@Composable
private fun LangToggle(current: Lang, onPick: (Lang) -> Unit) {
    Row(Modifier.background(CdColors.GreenLight, RoundedCornerShape(50)).padding(3.dp)) {
        listOf(Lang.EN to "EN", Lang.HI to "हि").forEach { (l, label) ->
            val sel = l == current
            Box(
                Modifier.background(if (sel) CdColors.Green else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(50))
                    .clickable { onPick(l) }.padding(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(label, color = if (sel) androidx.compose.ui.graphics.Color.White else CdColors.GreenDark, style = MaterialTheme.typography.labelLarge) }
        }
    }
}
