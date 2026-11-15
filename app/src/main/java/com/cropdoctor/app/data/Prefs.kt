package com.cropdoctor.app.data

import android.content.Context
import com.cropdoctor.app.knowledge.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("cropdoctor", Context.MODE_PRIVATE)

    private val _lang = MutableStateFlow(Lang.of(sp.getString("lang", null)))
    val lang: StateFlow<Lang> = _lang.asStateFlow()

    val languageChosen: Boolean get() = sp.contains("lang")

    fun setLang(l: Lang) {
        sp.edit().putString("lang", l.code).apply()
        _lang.value = l
    }

    /** Lets Ask use the online helper when a key is built in and the phone is online. */
    var onlineAnswers: Boolean
        get() = sp.getBoolean("onlineAnswers", true)
        set(v) = sp.edit().putBoolean("onlineAnswers", v).apply()

    var lastFieldId: String?
        get() = sp.getString("lastField", null)
        set(v) = sp.edit().putString("lastField", v).apply()

    var lat: Double?
        get() = if (sp.contains("lat")) sp.getFloat("lat", 0f).toDouble() else null
        set(v) { if (v != null) sp.edit().putFloat("lat", v.toFloat()).apply() }

    var lon: Double?
        get() = if (sp.contains("lon")) sp.getFloat("lon", 0f).toDouble() else null
        set(v) { if (v != null) sp.edit().putFloat("lon", v.toFloat()).apply() }
}
