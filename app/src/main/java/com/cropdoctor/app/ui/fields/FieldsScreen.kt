package com.cropdoctor.app.ui.fields

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingFlat
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.app
import com.cropdoctor.app.data.FieldPlot
import com.cropdoctor.app.data.ScanRecord
import com.cropdoctor.app.data.Trend
import com.cropdoctor.app.ui.Photo
import com.cropdoctor.app.ui.ScreenBar
import com.cropdoctor.app.ui.SectionCard
import com.cropdoctor.app.ui.SeverityChip
import com.cropdoctor.app.ui.severityColor
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr
import java.text.DateFormat
import java.util.Date

@Composable
fun FieldsScreen(onBack: () -> Unit, onOpen: (String) -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val fields by ctx.app.store.fields.collectAsState()
    val scans by ctx.app.store.scans.collectAsState()
    var adding by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().navigationBarsPadding()) {
        Column {
            ScreenBar(tr("My fields", "मेरे खेत"), onBack)
            if (fields.isEmpty()) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(tr("Add each field or plot you grow, then attach scans to it. You will see whether it is getting better or worse over time.", "अपना हर खेत या प्लॉट जोड़ें और स्कैन उससे जोड़ें। समय के साथ सुधार या बिगाड़ दिखेगा।"), style = MaterialTheme.typography.bodyLarge)
                }
            }
            LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(fields, key = { it.id }) { f ->
                    val mine = scans.filter { it.fieldId == f.id }
                    val last = mine.firstOrNull { it.severity >= 0 }
                    val trend = ctx.app.store.trend(f.id)
                    Card(
                        Modifier.fillMaxWidth().clickable { onOpen(f.id) }, shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = CdColors.Surface), border = BorderStroke(1.dp, CdColors.Outline),
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, style = MaterialTheme.typography.titleMedium)
                                Text("${ctx.app.kb.cropName(f.crop).get(lang)} · ${f.areaAcres} ${tr("acre", "एकड़")} · ${mine.size} ${tr("scans", "स्कैन")}", style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
                                if (last?.classId != null) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(ctx.app.kb.info(last.classId).name.get(lang), style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                if (last != null) SeverityChip(last.severity)
                                TrendBadge(trend)
                            }
                        }
                    }
                }
                item { Spacer(Modifier.height(88.dp)) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { adding = true },
            icon = { Icon(Icons.Filled.Add, null) }, text = { Text(tr("Add field", "खेत जोड़ें")) },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = CdColors.Green, contentColor = Color.White,
        )
    }
    if (adding) AddFieldDialog(onDismiss = { adding = false })
}

@Composable
fun TrendBadge(t: Trend) {
    when (t) {
        Trend.NEW -> {}
        Trend.BETTER -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TrendingDown, null, tint = CdColors.Green, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
            Text(tr("Improving", "सुधार"), style = MaterialTheme.typography.labelMedium, color = CdColors.Green)
        }
        Trend.SAME -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TrendingFlat, null, tint = CdColors.Muted, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
            Text(tr("No change", "कोई बदलाव नहीं"), style = MaterialTheme.typography.labelMedium, color = CdColors.Muted)
        }
        Trend.WORSE -> Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.TrendingUp, null, tint = CdColors.Red, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp))
            Text(tr("Getting worse", "बिगड़ रहा"), style = MaterialTheme.typography.labelMedium, color = CdColors.Red)
        }
    }
}

@Composable
private fun AddFieldDialog(onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val kb = ctx.app.kb
    var name by remember { mutableStateOf("") }
    var crop by remember { mutableStateOf(kb.cropIds.first()) }
    var acres by remember { mutableStateOf("1") }
    var open by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("New field", "नया खेत")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(tr("Name (e.g. North plot)", "नाम (जैसे उत्तर वाला खेत)")) }, singleLine = true)
                Box {
                    OutlinedButton(onClick = { open = true }, Modifier.fillMaxWidth()) { Text(kb.cropName(crop).get(lang)) }
                    DropdownMenu(open, { open = false }) {
                        kb.cropIds.forEach { c -> DropdownMenuItem(text = { Text(kb.cropName(c).get(lang)) }, onClick = { crop = c; open = false }) }
                    }
                }
                OutlinedTextField(acres, { acres = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) }, label = { Text(tr("Area (acre)", "क्षेत्रफल (एकड़)")) }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                ctx.app.store.addField(name.ifBlank { kb.cropName(crop).get(lang) }, crop, acres.toDoubleOrNull() ?: 1.0)
                onDismiss()
            }) { Text(tr("Save", "सहेजें")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Cancel", "रद्द करें")) } },
    )
}

@Composable
fun FieldDetailScreen(fieldId: String, onBack: () -> Unit, onScan: (String) -> Unit, onOpenScan: (String) -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val fields by ctx.app.store.fields.collectAsState()
    val scans by ctx.app.store.scans.collectAsState()
    val f = fields.firstOrNull { it.id == fieldId }
    if (f == null) { ScreenBar(tr("Field", "खेत"), onBack); return }
    val mine = scans.filter { it.fieldId == fieldId }
    var confirm by remember { mutableStateOf(false) }

    Column(Modifier.navigationBarsPadding()) {
        ScreenBar(f.name, onBack) {
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, tr("Delete field", "खेत हटाएँ"), tint = CdColors.Muted) }
        }
        LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(ctx.app.kb.cropName(f.crop).get(lang), style = MaterialTheme.typography.titleLarge)
                        Text("${f.areaAcres} ${tr("acre", "एकड़")}", style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
                    }
                    TrendBadge(ctx.app.store.trend(f.id))
                }
            }
            item { Button(onClick = { onScan(f.id) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) { Text(tr("Scan a leaf in this field", "इस खेत की पत्ती स्कैन करें")) } }
            item {
                SectionCard(tr("Health over time", "समय के साथ सेहत")) {
                    HealthChart(mine.filter { it.severity >= 0 }.sortedBy { it.timestamp })
                    Text(tr("Higher on the chart = more severe. A line going down means improving. Each dot is one scan.", "चार्ट में ऊपर = ज़्यादा गंभीर। रेखा नीचे जाए तो सुधार। हर बिंदु एक स्कैन है।"), style = MaterialTheme.typography.labelMedium, color = CdColors.Muted)
                }
            }
            items(mine, key = { it.id }) { s ->
                val i = s.classId?.let { ctx.app.kb.info(it) }
                Row(Modifier.fillMaxWidth().clickable { onOpenScan(s.id) }, verticalAlignment = Alignment.CenterVertically) {
                    val bmp = remember(s.id) { ctx.app.store.loadPhoto(s, 200) }
                    Photo(bmp, Modifier.size(56.dp).then(Modifier))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(i?.name?.get(lang) ?: tr("Unknown", "अज्ञात"), style = MaterialTheme.typography.titleMedium)
                        Text(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.timestamp)), style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
                    }
                    if (s.severity >= 0) SeverityChip(s.severity)
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text(tr("Delete this field?", "यह खेत हटाएँ?")) },
            text = { Text(tr("Its scans stay in your history.", "इसके स्कैन इतिहास में बने रहेंगे।")) },
            confirmButton = { TextButton(onClick = { ctx.app.store.deleteField(f.id); confirm = false; onBack() }) { Text(tr("Delete", "हटाएँ"), color = CdColors.Red) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text(tr("Cancel", "रद्द करें")) } },
        )
    }
}

/** Severity (0 healthy .. 3 severe) of each scan as a line with coloured dots. */
@Composable
private fun HealthChart(points: List<ScanRecord>) {
    if (points.size < 2) {
        Text(tr("Scan this field at least twice to see a trend.", "रुझान देखने के लिए इस खेत को कम से कम दो बार स्कैन करें।"), style = MaterialTheme.typography.bodyMedium)
        return
    }
    val line = CdColors.Outline
    Canvas(Modifier.fillMaxWidth().height(120.dp)) {
        val padX = 16.dp.toPx(); val padY = 12.dp.toPx()
        val w = size.width - 2 * padX; val h = size.height - 2 * padY
        for (lvl in 0..3) {
            val y = padY + h * (lvl / 3f)
            drawLine(line, Offset(padX, y), Offset(size.width - padX, y), 1.dp.toPx())
        }
        val pts = points.mapIndexed { i, s -> Offset(padX + w * i / (points.size - 1), padY + h * (1f - s.severity.coerceIn(0, 3) / 3f)) }
        val path = Path().apply { pts.forEachIndexed { i, o -> if (i == 0) moveTo(o.x, o.y) else lineTo(o.x, o.y) } }
        drawPath(path, CdColors.Muted, style = Stroke(2.dp.toPx()))
        pts.forEachIndexed { i, o -> drawCircle(severityColor(points[i].severity), 6.dp.toPx(), o) }
    }
}
