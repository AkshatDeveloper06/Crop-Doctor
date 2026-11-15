package com.cropdoctor.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.cropdoctor.app.app
import com.cropdoctor.app.ui.Bullets
import com.cropdoctor.app.ui.Pill
import com.cropdoctor.app.ui.SectionCard
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.t
import com.cropdoctor.app.ui.theme.tr
import com.cropdoctor.app.weather.Forecast
import com.cropdoctor.app.weather.Risk
import com.cropdoctor.app.weather.SprayAdvisor
import com.cropdoctor.app.weather.Verdict3

private sealed interface WState {
    data object NeedLocation : WState
    data object Loading : WState
    data object Unavailable : WState
    data class Ready(val f: Forecast) : WState
}

/**
 * Spray-window + disease-weather risk. The only online feature: it silently degrades to a short message when offline.
 */
@Composable
fun SprayCard() {
    val ctx = LocalContext.current
    val prefs = ctx.app.prefs
    var state by remember { mutableStateOf<WState>(WState.Loading) }
    var attempt by remember { mutableStateOf(0) }

    fun hasLoc() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { attempt++ }

    LaunchedEffect(attempt) {
        state = WState.Loading
        var lat = prefs.lat
        var lon = prefs.lon
        if (hasLoc()) {
            SprayAdvisor.lastKnown(ctx)?.let { (a, b) -> lat = a; lon = b; prefs.lat = a; prefs.lon = b }
        }
        if (lat == null || lon == null) { state = WState.NeedLocation; return@LaunchedEffect }
        val f = SprayAdvisor.fetch(lat!!, lon!!)
        state = if (f == null) WState.Unavailable else WState.Ready(f)
    }

    SectionCard(tr("Spray weather", "छिड़काव का मौसम"), Icons.Filled.WaterDrop) {
        when (val s = state) {
            WState.Loading -> Row { CircularProgressIndicator(Modifier.width(22.dp), strokeWidth = 2.dp) }
            WState.NeedLocation -> {
                Text(tr("See if it is a good time to spray, and whether the weather favours disease. Uses your approximate location and internet; nothing else is shared.",
                    "जानें कि छिड़काव का समय ठीक है या नहीं और मौसम रोग के अनुकूल है या नहीं। आपकी मोटी लोकेशन और इंटरनेट का उपयोग होता है; और कुछ साझा नहीं होता।"), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { if (hasLoc()) attempt++ else launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) {
                    Text(tr("Check weather", "मौसम देखें"))
                }
            }
            WState.Unavailable -> {
                Text(tr("Forecast unavailable (offline?). Everything else in the app still works.", "पूर्वानुमान उपलब्ध नहीं (इंटरनेट नहीं?)। ऐप की बाकी सब सुविधाएँ चालू हैं।"), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { attempt++ }) { Text(tr("Try again", "दोबारा कोशिश करें")) }
            }
            is WState.Ready -> {
                val a = SprayAdvisor.advise(s.f)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row {
                        when (a.verdict) {
                            Verdict3.GOOD -> Pill(tr("Good time to spray", "छिड़काव का अच्छा समय"), CdColors.GreenLight, CdColors.GreenDark)
                            Verdict3.CAUTION -> Pill(tr("Spray with care", "सावधानी से छिड़कें"), CdColors.AmberLight, CdColors.Amber)
                            Verdict3.AVOID -> Pill(tr("Do not spray now", "अभी छिड़काव न करें"), CdColors.RedLight, CdColors.Red)
                        }
                        Spacer(Modifier.width(8.dp))
                        when (a.diseaseRisk) {
                            Risk.HIGH -> Pill(tr("Disease risk: high", "रोग जोखिम: अधिक"), CdColors.RedLight, CdColors.Red)
                            Risk.MEDIUM -> Pill(tr("Disease risk: medium", "रोग जोखिम: मध्यम"), CdColors.AmberLight, CdColors.Amber)
                            Risk.LOW -> Pill(tr("Disease risk: low", "रोग जोखिम: कम"), CdColors.GreenLight, CdColors.GreenDark)
                        }
                    }
                    Bullets(a.reasons.map { it.t() })
                    Text(a.riskNote.t(), style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted)
                }
            }
        }
    }
}
