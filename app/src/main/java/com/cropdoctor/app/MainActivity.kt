package com.cropdoctor.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cropdoctor.app.ui.AppNav
import com.cropdoctor.app.ui.theme.CdColors
import com.cropdoctor.app.ui.theme.CropDoctorTheme
import com.cropdoctor.app.work.Reminders

class MainActivity : ComponentActivity() {
    private var pendingScan by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingScan = intent?.getStringExtra(Reminders.EXTRA_SCAN)
        // Load the model off the main thread so the first frame is not delayed.
        Thread { app.classifier }.start()
        setContent {
            val lang by app.prefs.lang.collectAsState()
            CropDoctorTheme(lang) {
                Box(Modifier.fillMaxSize().background(CdColors.Background)) {
                    AppNav(pendingScan = pendingScan, onPendingHandled = { pendingScan = null })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingScan = intent.getStringExtra(Reminders.EXTRA_SCAN)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) app.speaker.stop()
    }
}
