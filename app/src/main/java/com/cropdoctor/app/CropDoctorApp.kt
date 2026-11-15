package com.cropdoctor.app

import android.app.Application
import android.content.Context
import com.cropdoctor.app.ai.CropClassifier
import com.cropdoctor.app.audio.Speaker
import com.cropdoctor.app.data.Prefs
import com.cropdoctor.app.data.Store
import com.cropdoctor.app.knowledge.KnowledgeBase
import com.cropdoctor.app.knowledge.OfflineAdvisor
import com.cropdoctor.app.online.OnlineAdvisor
import com.cropdoctor.app.work.Reminders

/** Tiny service locator: one instance of each heavy object for the whole process. */
class CropDoctorApp : Application() {
    lateinit var prefs: Prefs; private set
    lateinit var store: Store; private set
    lateinit var kb: KnowledgeBase; private set
    lateinit var advisor: OfflineAdvisor; private set
    val online: OnlineAdvisor by lazy { OnlineAdvisor(this) }
    val classifier: CropClassifier by lazy { CropClassifier(this) }
    val speaker: Speaker by lazy { Speaker(this) }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        store = Store(this)
        kb = KnowledgeBase.load(this)
        advisor = OfflineAdvisor(kb)
        Reminders.createChannel(this)
    }
}

val Context.app: CropDoctorApp get() = applicationContext as CropDoctorApp
