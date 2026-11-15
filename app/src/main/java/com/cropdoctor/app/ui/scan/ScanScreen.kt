package com.cropdoctor.app.ui.scan

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cropdoctor.app.ai.Certainty
import com.cropdoctor.app.ai.ClassId
import com.cropdoctor.app.ai.QualityIssue
import com.cropdoctor.app.app
import com.cropdoctor.app.ui.Images
import com.cropdoctor.app.ui.ModelMissingNotice
import com.cropdoctor.app.ui.Pill
import com.cropdoctor.app.ui.severityBg
import com.cropdoctor.app.ui.severityColor
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@Composable
fun ScanScreen(onBack: () -> Unit, onResult: (String) -> Unit, startFieldId: String?) {
    val ctx = LocalContext.current
    val vm: ScanViewModel = viewModel()
    val scope = rememberCoroutineScope()
    var hasCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasCam = it }
    LaunchedEffect(Unit) { if (!hasCam) permLauncher.launch(Manifest.permission.CAMERA) }

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var fieldId by remember { mutableStateOf(startFieldId ?: ctx.app.prefs.lastFieldId) }
    val fields by ctx.app.store.fields.collectAsState()
    if (fieldId != null && fields.none { it.id == fieldId }) fieldId = null

    val notLeaf = tr("No leaf found. Fill the frame with one leaf and try again.", "पत्ती नहीं मिली। फ्रेम में एक पत्ती भरकर दोबारा कोशिश करें।")
    val noModel = tr("Disease model is not installed.", "रोग पहचान मॉडल जुड़ा नहीं है।")
    val readFail = tr("Could not read that photo.", "यह फोटो पढ़ नहीं सके।")
    val captureFail = tr("Capture failed. Try again.", "फोटो नहीं ली जा सकी। दोबारा कोशिश करें।")

    fun handle(bitmap: android.graphics.Bitmap?, guided: Boolean = false) {
        if (bitmap == null) { message = readFail; busy = false; vm.unfreeze(); return }
        scope.launch {
            when (val o = vm.diagnose(bitmap, fieldId, guided)) {
                is Outcome.Saved -> onResult(o.scanId)
                Outcome.NotALeaf -> { message = notLeaf; vm.unfreeze() }
                Outcome.ModelMissing -> { message = noModel; vm.unfreeze() }
            }
            busy = false
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            busy = true
            scope.launch { handle(kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Images.decode(ctx, uri) }) }
        }
    }

    if (!hasCam) {
        Column(Modifier.fillMaxSize().background(CdColors.Background).statusBarsPadding().padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr("Camera permission is needed to scan leaves.", "पत्ती स्कैन करने के लिए कैमरा अनुमति चाहिए।"), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.size(16.dp))
            Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text(tr("Allow camera", "कैमरा अनुमति दें")) }
            Spacer(Modifier.size(8.dp))
            Button(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Text(tr("Pick a photo instead", "फोटो गैलरी से चुनें")) }
        }
        return
    }

    val lifecycle = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    val imageCapture = remember { ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build() }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torch by remember { mutableStateOf(false) }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }

    DisposableEffect(lifecycle) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val listener = Runnable {
            val provider = future.get()
            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
            analysis.setAnalyzer(executor) { proxy: ImageProxy ->
                try {
                    val bmp = Images.rotate(proxy.toBitmap(), proxy.imageInfo.rotationDegrees)
                    vm.onFrame(bmp)
                } catch (_: Throwable) {
                } finally { proxy.close() }
            }
            provider.unbindAll()
            camera = provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
        }
        future.addListener(listener, ContextCompat.getMainExecutor(ctx))
        onDispose {
            runCatching { future.get().unbindAll() }
            vm.resetLive()
        }
    }
    LaunchedEffect(torch, camera) { camera?.cameraControl?.enableTorch(torch) }
    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    val live by vm.live.collectAsState()
    val kb = ctx.app.kb

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView({ previewView }, Modifier.fillMaxSize())

        // ----- guide frame, coloured by what the model sees
        val v = live.verdict
        val sev = v?.classId?.let { kb.info(it).severity } ?: -1
        val frameColor = when {
            v == null -> Color.White
            v.certainty == Certainty.NOT_A_LEAF -> Color.White.copy(alpha = 0.7f)
            v.certainty == Certainty.UNKNOWN -> Color(0xFFFBBF24)
            sev <= 0 -> Color(0xFF4ADE80)
            sev == 3 -> Color(0xFFF87171)
            else -> Color(0xFFFBBF24)
        }
        Canvas(Modifier.fillMaxSize()) {
            // The model reads the middle CROP_FRACTION of the camera frame. The preview shows the whole frame (fit, not
            // cropped), so the box is drawn at exactly the region the model reads. Before the first frame, use a default.
            val fw = live.frameW
            val fh = live.frameH
            val side = if (fw > 0 && fh > 0) {
                val scale = minOf(size.width / fw, size.height / fh)
                minOf(fw, fh) * com.cropdoctor.app.ai.CropClassifier.CROP_FRACTION * scale
            } else size.minDimension * 0.6f
            val tl = Offset((size.width - side) / 2f, (size.height - side) / 2f)
            drawRoundRect(frameColor, tl, Size(side, side), CornerRadius(28.dp.toPx()), style = Stroke(width = 4.dp.toPx()))
        }

        // ----- top bar
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, tr("Back", "वापस"), tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            FieldPicker(fieldId, onPick = { fieldId = it })
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = { torch = !torch }, modifier = Modifier.background(Color.Black.copy(alpha = 0.4f), CircleShape)) {
                Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, tr("Flash", "फ्लैश"), tint = Color.White)
            }
        }

        // ----- live label + hints
        Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 64.dp, start = 16.dp, end = 16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!vm.modelReady) {
                ModelMissingNotice(ctx.app.classifier.state.let { (it as? com.cropdoctor.app.ai.ModelState.Missing)?.reason ?: "" })
            } else if (v != null && v.classId != null && v.certainty != Certainty.UNKNOWN) {
                val info = kb.info(v.classId)
                val unsure = v.certainty == Certainty.UNSURE
                val lang = LocalLang.current
                Pill(
                    "${info.cropName.get(lang)} · ${info.name.get(lang)}  ${(v.confidence * 100).toInt()}%" + if (unsure) "  ?" else "",
                    severityBg(sev), severityColor(sev),
                )
            } else if (v?.certainty == Certainty.NOT_A_LEAF) {
                Pill(tr("Point at a leaf", "पत्ती की ओर कैमरा करें"), Color.White.copy(alpha = 0.9f), CdColors.Ink)
            } else if (v?.certainty == Certainty.UNKNOWN) {
                Pill(tr("Not sure yet… move closer", "अभी पक्का नहीं… थोड़ा पास जाएँ"), CdColors.AmberLight, CdColors.Amber)
            }
            if (busy) Pill(tr("Analysing this leaf…", "इस पत्ती की जाँच हो रही है…"), Color.Black.copy(alpha = 0.65f), Color.White)
            val q = live.quality
            if (q != null && q.issues.isNotEmpty() && !busy) {
                val hint = when (q.issues.first()) {
                    QualityIssue.TOO_BLURRY -> tr("Hold steady: image is blurry", "फोन स्थिर रखें: फोटो धुंधली है")
                    QualityIssue.TOO_DARK -> tr("Too dark: move to light or use flash", "बहुत अंधेरा: रोशनी में जाएँ या फ्लैश चालू करें")
                    QualityIssue.TOO_BRIGHT -> tr("Too bright: shade the leaf", "बहुत तेज़ रोशनी: पत्ती पर छाया करें")
                    QualityIssue.GLARE -> tr("Glare on the leaf: tilt slightly", "पत्ती पर चमक: थोड़ा तिरछा करें")
                    QualityIssue.LEAF_TOO_SMALL -> tr("Move closer: fill the frame with the leaf", "पास जाएँ: फ्रेम में पत्ती भरें")
                }
                Pill(hint, Color.Black.copy(alpha = 0.6f), Color.White)
            }
            message?.let { Pill(it, CdColors.RedLight, CdColors.Red, Modifier.clickable { message = null }) }
        }

        // ----- bottom controls
        Row(Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 28.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.size(56.dp).background(Color.Black.copy(alpha = 0.45f), CircleShape)) {
                Icon(Icons.Filled.PhotoLibrary, tr("Gallery", "गैलरी"), tint = Color.White)
            }
            Box(
                Modifier.size(84.dp).background(Color.White, CircleShape).padding(6.dp).background(CdColors.Green, CircleShape)
                    .clickable(enabled = !busy) {
                        vm.freeze()          // keep the answer the farmer is looking at
                        busy = true
                        message = null
                        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bmp = runCatching { Images.rotate(image.toBitmap(), image.imageInfo.rotationDegrees) }.getOrNull()
                                image.close()
                                scope.launch { handle(bmp, guided = true) }
                            }
                            override fun onError(e: ImageCaptureException) { scope.launch { message = captureFail; busy = false; vm.unfreeze() } }
                        })
                    },
                contentAlignment = Alignment.Center,
            ) { if (busy) CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp, modifier = Modifier.size(34.dp)) }
            Spacer(Modifier.size(56.dp))
        }
        Text(
            tr("Fill the frame with one leaf, then tap the green button", "फ्रेम में एक पत्ती भरें, फिर हरा बटन दबाएँ"),
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 124.dp),
            color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun FieldPicker(fieldId: String?, onPick: (String?) -> Unit) {
    val ctx = LocalContext.current
    val fields by ctx.app.store.fields.collectAsState()
    var open by remember { mutableStateOf(false) }
    val current = fields.firstOrNull { it.id == fieldId }
    Box {
        Pill(
            (current?.name ?: tr("No field", "कोई खेत नहीं")) + "  ▾",
            Color.Black.copy(alpha = 0.5f), Color.White, Modifier.clickable { open = true },
        )
        DropdownMenu(open, { open = false }) {
            DropdownMenuItem(text = { Text(tr("No field", "कोई खेत नहीं")) }, onClick = { onPick(null); open = false })
            fields.forEach { f ->
                DropdownMenuItem(
                    text = { Text("${f.name} · ${ctx.app.kb.cropName(f.crop).get(ctx.app.prefs.lang.value)}") },
                    onClick = { onPick(f.id); open = false },
                )
            }
        }
    }
}
