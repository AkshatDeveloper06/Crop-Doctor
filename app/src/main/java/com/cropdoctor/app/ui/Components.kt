package com.cropdoctor.app.ui

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.tr

fun severityColor(sev: Int): Color = when (sev) {
    0 -> CdColors.Green
    1 -> CdColors.Amber
    2 -> CdColors.Amber
    3 -> CdColors.Red
    else -> CdColors.Muted
}

fun severityBg(sev: Int): Color = when (sev) {
    0 -> CdColors.GreenLight
    1, 2 -> CdColors.AmberLight
    3 -> CdColors.RedLight
    else -> Color(0xFFE2E8F0)
}

@Composable
fun severityLabel(sev: Int): String = when (sev) {
    0 -> tr("Healthy", "स्वस्थ")
    1 -> tr("Mild", "हल्का")
    2 -> tr("Moderate", "मध्यम")
    3 -> tr("Severe", "गंभीर")
    else -> tr("Unknown", "अज्ञात")
}

@Composable
fun SeverityChip(sev: Int, modifier: Modifier = Modifier) {
    Box(
        modifier.clip(RoundedCornerShape(50)).background(severityBg(sev)).padding(horizontal = 12.dp, vertical = 5.dp),
    ) { Text(severityLabel(sev), color = severityColor(sev), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenBar(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1) },
        navigationIcon = {
            if (onBack != null) IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Back", "वापस"))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = CdColors.Background),
    )
}

@Composable
fun SectionCard(title: String, icon: ImageVector? = null, accent: Color = CdColors.Green, content: @Composable ColumnScope.() -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CdColors.Surface),
        border = BorderStroke(1.dp, CdColors.Outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(title, style = MaterialTheme.typography.titleMedium, color = accent)
            }
            content()
        }
    }
}

@Composable
fun Bullets(items: List<String>) {
    items.forEach {
        Row {
            Text("•", style = MaterialTheme.typography.bodyLarge, color = CdColors.Green, modifier = Modifier.width(18.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
fun Photo(bitmap: Bitmap?, modifier: Modifier = Modifier) {
    if (bitmap == null) {
        Box(modifier.background(CdColors.GreenLight), contentAlignment = Alignment.Center) { Text("🍃") }
    } else {
        Image(bitmap.asImageBitmap(), null, modifier, contentScale = ContentScale.Crop)
    }
}

@Composable
fun Pill(text: String, bg: Color, fg: Color, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun OfflineBadge() {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(CdColors.GreenLight).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.CloudOff, null, tint = CdColors.GreenDark, modifier = Modifier.size(15.dp))
        Spacer(Modifier.width(5.dp))
        Text(tr("Works offline", "बिना इंटरनेट चलता है"), style = MaterialTheme.typography.labelMedium, color = CdColors.GreenDark)
    }
}

@Composable
fun Notice(text: String, modifier: Modifier = Modifier, bad: Boolean = false) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(if (bad) CdColors.RedLight else CdColors.AmberLight).padding(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Warning, null, tint = if (bad) CdColors.Red else CdColors.Amber, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = CdColors.Ink)
    }
}

@Composable
fun BigTile(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = CdColors.Surface),
        border = BorderStroke(1.dp, CdColors.Outline),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(46.dp).clip(CircleShape).background(CdColors.GreenLight), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = CdColors.Green, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 2)
        }
    }
}

/** Shown when model.tflite has not been added yet. */
@Composable
fun ModelMissingNotice(reason: String) {
    Notice(
        tr(
            "The disease model could not be loaded ($reason). Reinstall the app; if it keeps happening, the model files in the app are damaged.",
            "रोग पहचान मॉडल लोड नहीं हो सका ($reason)। ऐप दोबारा इंस्टॉल करें; बार-बार हो तो ऐप में मॉडल फ़ाइलें खराब हैं।",
        ),
        bad = true,
    )
}
