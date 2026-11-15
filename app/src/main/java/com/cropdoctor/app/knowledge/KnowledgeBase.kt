package com.cropdoctor.app.knowledge

import android.content.Context
import com.cropdoctor.app.ai.ClassId
import com.google.gson.Gson

enum class Lang(val code: String, val tag: String) {
    EN("en", "en-US"), HI("hi", "hi-IN");

    companion object {
        fun of(code: String?) = if (code == "hi") HI else EN
    }
}

/** A bilingual string. */
data class Bi(val en: String = "", val hi: String = "") {
    fun get(l: Lang) = if (l == Lang.HI && hi.isNotBlank()) hi else en
}

/** A bilingual list of bullet points. */
data class BiList(val en: List<String> = emptyList(), val hi: List<String> = emptyList()) {
    fun get(l: Lang) = if (l == Lang.HI && hi.isNotEmpty()) hi else en
}

data class Disease(
    val name: Bi = Bi(),
    val severity: Int = 2,                // 1 mild, 2 moderate, 3 severe (0 = healthy)
    val followUpDays: Int = 7,            // when to re-scan
    val spreadsFast: Boolean = false,
    val cause: Bi = Bi(),
    val symptoms: Bi = Bi(),
    val actions: BiList = BiList(),       // do today
    val organic: BiList = BiList(),
    val chemical: BiList = BiList(),
    val prevent: BiList = BiList(),
    val keywords: List<String> = emptyList(),   // extra words used by offline Q&A
)

internal data class KbFile(
    val crops: Map<String, Bi> = emptyMap(),
    val diseases: Map<String, Disease> = emptyMap(),
)

/** What the UI shows for any class id, including healthy and unknown ones. */
data class Info(
    val classId: String,
    val cropName: Bi,
    val name: Bi,
    val severity: Int,
    val followUpDays: Int,
    val spreadsFast: Boolean,
    val cause: Bi,
    val symptoms: Bi,
    val actions: BiList,
    val organic: BiList,
    val chemical: BiList,
    val prevent: BiList,
    val known: Boolean,
)

class KnowledgeBase internal constructor(private val kb: KbFile) {

    val cropIds: List<String> get() = kb.crops.keys.sorted()
    fun cropName(crop: String): Bi = kb.crops[crop] ?: Bi(crop, crop)
    fun diseaseIds(crop: String): List<String> =
        kb.diseases.keys.filter { ClassId.crop(it) == crop }.sorted()

    fun entry(classId: String): Disease? = kb.diseases[classId]

    fun info(classId: String): Info {
        val crop = ClassId.crop(classId)
        val cn = cropName(crop)
        if (ClassId.isHealthy(classId)) {
            return Info(
                classId, cn, Bi("Healthy ${cn.en} leaf", "स्वस्थ ${cn.hi.ifBlank { cn.en }} की पत्ती"),
                0, 14, false,
                Bi("No disease signs were found in this photo.", "इस फोटो में रोग के कोई लक्षण नहीं मिले।"),
                Bi("Leaf colour and texture look normal.", "पत्ती का रंग और बनावट सामान्य दिख रही है।"),
                BiList(
                    listOf("No treatment needed. Keep the same care routine.", "Check a few more leaves from different plants to be sure."),
                    listOf("उपचार की ज़रूरत नहीं। देखभाल पहले जैसी रखें।", "पक्का करने के लिए दूसरे पौधों की कुछ और पत्तियाँ भी जाँचें।"),
                ),
                BiList(), BiList(),
                BiList(
                    listOf("Scout the field every 5-7 days.", "Avoid overhead irrigation late in the day.", "Remove weeds that harbour pests."),
                    listOf("हर 5-7 दिन में खेत का निरीक्षण करें।", "शाम को ऊपर से सिंचाई न करें।", "कीट पनाह देने वाले खरपतवार हटाएँ।"),
                ),
                true,
            )
        }
        val d = kb.diseases[classId]
        if (d != null) {
            return Info(classId, cn, d.name, d.severity, d.followUpDays, d.spreadsFast, d.cause, d.symptoms,
                d.actions, d.organic, d.chemical, d.prevent, true)
        }
        val pretty = ClassId.disease(classId).replace('_', ' ').replaceFirstChar { it.uppercase() }
        return Info(
            classId, cn, Bi(pretty, pretty), 2, 5, false,
            Bi("This problem is not in the offline guide yet.", "यह समस्या अभी ऑफ़लाइन गाइड में नहीं है।"),
            Bi("", ""),
            BiList(
                listOf("Take a clear photo and show it to your nearest KVK / agriculture officer.", "Isolate or mark the affected plants."),
                listOf("साफ़ फोटो लेकर नज़दीकी कृषि विज्ञान केंद्र / कृषि अधिकारी को दिखाएँ।", "प्रभावित पौधों को अलग करें या निशान लगाएँ।"),
            ),
            BiList(), BiList(), BiList(), false,
        )
    }

    companion object {
        fun load(context: Context): KnowledgeBase {
            val text = context.assets.open("knowledge_base.json").bufferedReader().use { it.readText() }
            return parse(text)
        }

        fun parse(json: String): KnowledgeBase = KnowledgeBase(Gson().fromJson(json, KbFile::class.java))
    }
}
