package com.cropdoctor.app.ui.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.cropdoctor.app.app
import com.cropdoctor.app.knowledge.Info
import com.cropdoctor.app.ui.SectionCard
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.LocalLang
import com.cropdoctor.app.ui.theme.tr
import kotlinx.coroutines.launch

/**
 * Extra advice from the online helper for the diagnosed problem. Hidden when no key is built in or the user turned it
 * off. Whatever was fetched once is kept on the phone and shown offline later. When the saved guide has no entry for
 * the problem, the advice is fetched automatically as soon as the phone is online.
 */
@Composable
fun AiAdviceCard(classId: String, info: Info) {
    val ctx = LocalContext.current
    val lang = LocalLang.current
    val online = ctx.app.online
    val scope = rememberCoroutineScope()

    var text by remember(classId, lang) { mutableStateOf(online.cachedInsight(classId, lang)) }
    var loading by remember(classId, lang) { mutableStateOf(false) }
    var failed by remember(classId, lang) { mutableStateOf(false) }

    val enabled = online.isConfigured && ctx.app.prefs.onlineAnswers
    if (!enabled && text == null) return
    val canFetch = enabled && online.isOnline()

    fun fetch() {
        if (loading) return
        loading = true
        failed = false
        scope.launch {
            online.insight(classId, info, lang).fold(
                onSuccess = { text = it },
                onFailure = { failed = true },
            )
            loading = false
        }
    }

    LaunchedEffect(classId, lang) {
        if (text == null && !info.known && canFetch) fetch()
    }

    SectionCard(tr("More advice from AI", "AI से और सलाह"), Icons.Filled.AutoAwesome, CdColors.Green) {
        val shown = text
        when {
            shown != null -> {
                Text(shown, style = MaterialTheme.typography.bodyLarge)
                Text(
                    tr("AI-generated. Check with your KVK and follow the product label.", "AI द्वारा बनाया गया। KVK से पूछें और उत्पाद का लेबल मानें।"),
                    style = MaterialTheme.typography.labelMedium, color = CdColors.Muted,
                )
                if (canFetch && !loading) TextButton(onClick = { fetch() }) { Text(tr("Refresh", "दोबारा लें")) }
            }
            loading -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = CdColors.Green)
                Text(tr("Getting advice…", "सलाह ला रहे हैं…"), style = MaterialTheme.typography.bodyMedium)
            }
            canFetch -> {
                Text(
                    tr("Get more detailed, step-by-step advice for this problem. Only the crop and disease name are sent.", "इस समस्या के लिए और विस्तृत, चरण-दर-चरण सलाह पाएँ। केवल फसल और रोग का नाम भेजा जाता है।"),
                    style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted,
                )
                Button(onClick = { fetch() }, shape = RoundedCornerShape(14.dp)) {
                    Icon(Icons.Filled.AutoAwesome, null)
                    Spacer(Modifier.width(6.dp))
                    Text(tr("Get AI advice", "AI सलाह पाएँ"))
                }
                if (failed) Text(tr("Could not reach the AI service. Try again later.", "AI सेवा से संपर्क नहीं हो सका। बाद में दोबारा कोशिश करें।"), style = MaterialTheme.typography.labelMedium, color = CdColors.Red)
            }
            else -> Text(
                tr("Connect to the internet to get more advice for this problem.", "इस समस्या की और सलाह के लिए इंटरनेट से जुड़ें।"),
                style = MaterialTheme.typography.bodyMedium, color = CdColors.Muted,
            )
        }
    }
}
