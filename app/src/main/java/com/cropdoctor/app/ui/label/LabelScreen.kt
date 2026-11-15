package com.cropdoctor.app.ui.label

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.cropdoctor.app.app
import com.cropdoctor.app.knowledge.Lang
import com.cropdoctor.app.ocr.Dose
import com.cropdoctor.app.ocr.DoseUnit
import com.cropdoctor.app.ocr.LabelParser
import com.cropdoctor.app.ocr.LabelReader
import com.cropdoctor.app.ui.Images
import com.cropdoctor.app.ui.Notice
import com.cropdoctor.app.ui.Pill
import com.cropdoctor.app.ui.ScreenBar
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors

/**
 * Pesticide label scanner + spray-tank dose calculator, fully on-device.
 * Step 1: aim at the line that says how much to mix per litre. Step 2: pick the tank size.
 */
@Composable
fun LabelScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    var dose by remember { mutableStateOf<Dose?>(null) }
    var source by remember { mutableStateOf("") }
    var manual by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(CdColors.Background)) {
        ScreenBar(tr("Pesticide dose", "दवा की मात्रा"), onBack)
        if (dose == null) {
            if (manual) ManualEntry(onDose = { dose = it; source = "" }, onCancel = { manual = false })
            else Scanner(onDose = { d, line -> dose = d; source = line }, onManual = { manual = true })
        } else {
            TankStep(dose!!, source, lang, onReset = { dose = null; manual = false })
        }
    }
}

@Composable
private fun Scanner(onDose: (Dose, String) -> Unit, onManual: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val perm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { if (!hasCam) perm.launch(Manifest.permission.CAMERA) }
    var seen by remember { mutableStateOf("") }
    var notFound by remember { mutableStateOf(false) }

    val reader = remember { LabelReader(onDose = { d, line -> onDose(d, line) }, onText = { seen = it }) }
    DisposableEffect(Unit) { onDispose { reader.close() } }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val bmp = withContext(Dispatchers.IO) { Images.decode(ctx, uri, 2000) }
            if (bmp != null) reader.readStill(bmp) { d, line -> if (d != null) onDose(d, line) else notFound = true }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("1. Point at the line that says how much to mix per litre, e.g. \"2 ml per litre\".", "1. उस लाइन की ओर कैमरा करें जिसमें प्रति लीटर मात्रा लिखी हो, जैसे \"2 मि.ली. प्रति लीटर\"।"), style = MaterialTheme.typography.titleMedium)
        if (hasCam) {
            val lifecycle = LocalLifecycleOwner.current
            val executor = remember { Executors.newSingleThreadExecutor() }
            val pv = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
            DisposableEffect(lifecycle) {
                val future = ProcessCameraProvider.getInstance(ctx)
                future.addListener({
                    val provider = future.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = pv.surfaceProvider }
                    val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
                    analysis.setAnalyzer(executor, reader)
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
                }, ContextCompat.getMainExecutor(ctx))
                onDispose { runCatching { future.get().unbindAll() }; executor.shutdown() }
            }
            Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(20.dp))) {
                AndroidView({ pv }, Modifier.fillMaxSize())
                Pill(
                    if (seen.isBlank()) tr("Looking for text…", "लिखावट खोज रहा हूँ…") else tr("Reading… hold steady", "पढ़ रहा हूँ… फोन स्थिर रखें"),
                    Color.Black.copy(alpha = 0.55f), Color.White, Modifier.align(Alignment.TopCenter).padding(10.dp),
                )
            }
        } else {
            Notice(tr("Camera permission is needed to read the label. You can also pick a photo or type the dose.", "लेबल पढ़ने के लिए कैमरा अनुमति चाहिए। आप फोटो चुन सकते हैं या मात्रा खुद लिख सकते हैं।"))
            Spacer(Modifier.weight(1f))
        }
        if (notFound) Notice(tr("No dose found in that photo. Try a closer, sharper photo of the dose line.", "इस फोटो में मात्रा नहीं मिली। मात्रा वाली लाइन की पास से, साफ़ फोटो लें।"), bad = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { notFound = false; gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text(tr("Photo", "फोटो"))
            }
            OutlinedButton(onClick = onManual, Modifier.weight(1f), shape = RoundedCornerShape(14.dp)) { Text(tr("Type the dose", "मात्रा खुद लिखें")) }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun ManualEntry(onDose: (Dose) -> Unit, onCancel: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var unit by remember { mutableStateOf(DoseUnit.ML) }
    Column(Modifier.padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(tr("Type the dose from the label (per 1 litre of water)", "लेबल से प्रति 1 लीटर पानी की मात्रा लिखें"), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(amount, { amount = it.filter { c -> c.isDigit() || c == '.' }.take(6) }, label = { Text(tr("Amount", "मात्रा")) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(unit == DoseUnit.ML, { unit = DoseUnit.ML }, { Text("ml " + tr("per litre", "प्रति लीटर")) })
            FilterChip(unit == DoseUnit.G, { unit = DoseUnit.G }, { Text("g " + tr("per litre", "प्रति लीटर")) })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { amount.toDoubleOrNull()?.takeIf { it > 0 }?.let { onDose(Dose(it, unit)) } }, shape = RoundedCornerShape(14.dp)) { Text(tr("Next", "आगे")) }
            OutlinedButton(onClick = onCancel, shape = RoundedCornerShape(14.dp)) { Text(tr("Back to camera", "कैमरे पर वापस")) }
        }
    }
}

@Composable
private fun TankStep(dose: Dose, line: String, lang: Lang, onReset: () -> Unit) {
    val ctx = LocalContext.current
    val speaker = ctx.app.speaker
    val presets = listOf(10.0, 15.0, 16.0, 20.0, 200.0)
    var tank by remember { mutableStateOf(16.0) }
    var other by remember { mutableStateOf(false) }
    var otherText by remember { mutableStateOf("") }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(tr("Dose read", "पढ़ी गई मात्रा"), style = MaterialTheme.typography.titleMedium, color = CdColors.Muted)
        Text("${LabelParser.mix(dose, 1.0).pretty()} " + tr("per litre of water", "प्रति लीटर पानी"), style = MaterialTheme.typography.headlineMedium)
        if (line.isNotBlank()) Text("“${line.take(80)}”", style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)

        Text(tr("2. Spray-tank size", "2. स्प्रे टंकी का आकार"), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            presets.forEach { p ->
                FilterChip(!other && tank == p, { other = false; tank = p }, { Text("${p.toInt()} L") })
            }
        }
        FilterChip(other, { other = true }, { Text(tr("Other size", "दूसरा आकार")) })
        if (other) OutlinedTextField(otherText, { otherText = it.filter { c -> c.isDigit() || c == '.' }.take(5); otherText.toDoubleOrNull()?.takeIf { v -> v > 0 }?.let { v -> tank = v } }, label = { Text(tr("Litres", "लीटर")) }, singleLine = true)

        val mix = LabelParser.mix(dose, tank)
        val spoken = if (lang == Lang.HI) "${tank.toInt()} लीटर की टंकी में ${mix.pretty()} दवा डालें।" else "Add ${mix.pretty()} of product to a ${if (tank == tank.toInt().toDouble()) tank.toInt() else tank} litre tank."
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(CdColors.GreenLight).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(tr("Add", "डालें"), style = MaterialTheme.typography.titleMedium, color = CdColors.GreenDark)
            Text(mix.pretty(), fontSize = 52.sp, fontWeight = FontWeight.ExtraBold, color = CdColors.GreenDark)
            Text(tr("of product for a ${if (tank == tank.toInt().toDouble()) tank.toInt() else tank} L tank", "दवा, ${if (tank == tank.toInt().toDouble()) tank.toInt() else tank} लीटर की टंकी के लिए"), style = MaterialTheme.typography.titleMedium, color = CdColors.GreenDark)
        }
        Button(onClick = { speaker.speak(spoken, lang) }, Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, null); Spacer(Modifier.width(8.dp)); Text(tr("Say it aloud", "बोलकर सुनाएँ"))
        }
        Notice(tr("Safety: wear gloves and a mask, mix in clean water, never exceed the label dose, and keep children and animals away. Confirm with your KVK if unsure.", "सुरक्षा: दस्ताने और मास्क पहनें, साफ़ पानी में मिलाएँ, लेबल से ज़्यादा मात्रा न डालें और बच्चों व पशुओं को दूर रखें। शंका हो तो KVK से पूछें।"))
        OutlinedButton(onClick = onReset, Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)) { Text(tr("Scan another label", "दूसरा लेबल स्कैन करें")) }
    }
}
