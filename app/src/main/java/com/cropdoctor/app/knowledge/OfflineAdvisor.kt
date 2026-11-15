package com.cropdoctor.app.knowledge

import com.cropdoctor.app.ai.ClassId

data class Answer(val text: String, val classId: String? = null)

/**
 * Offline question answering over the knowledge base. No model, no network: it recognises the crop, the disease and
 * what the farmer wants to know (cause / treatment / organic / prevention / symptoms) in English or Hindi.
 */
class OfflineAdvisor(private val kb: KnowledgeBase) {

    private enum class Intent { CAUSE, TREAT, ORGANIC, PREVENT, SYMPTOMS, SPREAD, WHEN, OVERVIEW }

    private val cropWords = mapOf(
        "Tomato" to listOf("tomato", "tamatar", "टमाटर"),
        "Potato" to listOf("potato", "aloo", "alu", "आलू"),
        "Corn" to listOf("corn", "maize", "makka", "मक्का", "मकई"),
        "Rice" to listOf("rice", "paddy", "dhan", "chawal", "धान", "चावल"),
        "Sugarcane" to listOf("sugarcane", "sugar cane", "ganna", "गन्ना"),
        "Cotton" to listOf("cotton", "kapas", "कपास"),
        "Pepper" to listOf("pepper", "chilli", "chili", "capsicum", "mirch", "मिर्च", "शिमला"),
        "Grape" to listOf("grape", "angur", "अंगूर"),
        "Apple" to listOf("apple", "seb", "सेब"),
        "Citrus" to listOf("citrus", "orange", "lemon", "nimbu", "santra", "mosambi", "नींबू", "संतरा", "मौसमी"),
        "Cherry" to listOf("cherry", "चेरी"),
        "Peach" to listOf("peach", "aadu", "आड़ू", "आडू"),
        "Squash" to listOf("squash", "gourd", "pumpkin", "kaddu", "कद्दू", "स्क्वैश"),
        "Strawberry" to listOf("strawberry", "स्ट्रॉबेरी"),
        "Blueberry" to listOf("blueberry", "ब्लूबेरी"),
        "Raspberry" to listOf("raspberry", "रास्पबेरी"),
        "Soybean" to listOf("soybean", "soyabean", "soya", "सोयाबीन"),
    )

    // Words that help match a disease even when the farmer does not say its full name.
    private val diseaseWords = mapOf(
        "late_blight" to listOf("late blight", "पछेती", "पिछेती", "पछैती"),
        "early_blight" to listOf("early blight", "अगेती", "अगैती"),
        "bacterial_spot" to listOf("bacterial spot", "जीवाणु धब्बा", "बैक्टीरियल"),
        "leaf_mold" to listOf("mold", "mould", "फफूंद", "मोल्ड"),
        "septoria_leaf_spot" to listOf("septoria", "सेप्टोरिया"),
        "spider_mites" to listOf("mite", "spider", "माइट", "मकड़ी"),
        "target_spot" to listOf("target", "टारगेट"),
        "yellow_leaf_curl_virus" to listOf("curl", "yellow", "मरोड़", "पीला", "whitefly", "सफेद मक्खी"),
        "curl_virus" to listOf("curl", "मरोड़", "whitefly", "सफेद मक्खी"),
        "mosaic_virus" to listOf("mosaic", "मोज़ेक", "मोजेक"),
        "mosaic" to listOf("mosaic", "मोज़ेक", "मोजेक"),
        "common_rust" to listOf("rust", "रतुआ", "रस्ट"),
        "rust" to listOf("rust", "रतुआ", "रस्ट"),
        "northern_leaf_blight" to listOf("northern", "blight", "झुलसा"),
        "gray_leaf_spot" to listOf("gray", "grey", "भूरा धब्बा", "ग्रे"),
        "bacterial_leaf_blight" to listOf("bacterial blight", "blb", "जीवाणु झुलसा", "बैक्टीरियल"),
        "bacterial_leaf_streak" to listOf("streak", "धारी"),
        "brown_spot" to listOf("brown spot", "भूरा धब्बा", "भूरी चित्ती"),
        "leaf_blast" to listOf("blast", "झोंका", "ब्लास्ट"),
        "tungro" to listOf("tungro", "टुंग्रो"),
        "hispa" to listOf("hispa", "हिस्पा"),
        "dead_heart" to listOf("dead heart", "deadheart", "तना छेदक", "borer", "डेड हार्ट"),
        "downy_mildew" to listOf("downy", "मृदुरोमिल", "डाउनी"),
        "red_rot" to listOf("red rot", "लाल सड़न", "लाल सड़ा"),
        "yellow_leaf" to listOf("yellow leaf", "पीली पत्ती"),
        "alternaria_leaf_spot" to listOf("alternaria", "अल्टरनेरिया"),
        "wilt" to listOf("wilt", "murjha", "मुरझा", "उकठा"),
        "bacterial_blight" to listOf("bacterial blight", "जीवाणु झुलसा"),
        "black_rot" to listOf("black rot", "काली सड़न"),
        "esca" to listOf("esca", "measles", "एस्का"),
        "scab" to listOf("scab", "पपड़ी", "स्कैब"),
        "cedar_apple_rust" to listOf("cedar", "rust", "रतुआ", "केडार"),
        "powdery_mildew" to listOf("powdery", "mildew", "चूर्णिल", "सफेद चूर्ण", "खस्ता"),
        "citrus_greening" to listOf("greening", "hlb", "huanglongbing", "psylla", "हरापन", "सिल्ला"),
        "leaf_blight" to listOf("leaf blight", "isariopsis", "पर्ण झुलसा", "झुलसा"),
        "bacterial_panicle_blight" to listOf("panicle", "पैनिकल", "बाली"),
        "leaf_smut" to listOf("smut", "कंडुआ", "स्मट"),
        "leaf_scorch" to listOf("scorch", "झुलस", "स्कॉर्च"),
    )

    private val intentWords: Map<Intent, List<String>> = mapOf(
        Intent.CAUSE to listOf("why", "cause", "reason", "how does", "कारण", "क्यों", "वजह", "kyun", "kaaran"),
        Intent.ORGANIC to listOf("organic", "natural", "neem", "home", "cheap", "जैविक", "नीम", "घरेलू", "देसी", "jaivik"),
        Intent.PREVENT to listOf("prevent", "avoid", "stop", "protect", "next time", "रोकथाम", "बचाव", "रोकना", "बचाने"),
        Intent.SYMPTOMS to listOf("symptom", "sign", "look", "identify", "recogni", "लक्षण", "पहचान", "कैसे दिखता"),
        Intent.SPREAD to listOf("spread", "contagious", "infect", "फैल", "संक्रमण"),
        Intent.WHEN to listOf("when", "time", "how often", "interval", "कब", "समय", "कितने दिन"),
        Intent.TREAT to listOf("treat", "cure", "medicine", "spray", "dose", "fungicide", "pesticide", "remedy", "control", "इलाज", "दवा", "दवाई", "छिड़काव", "उपचार", "ilaj", "dawa"),
    )

    fun answer(question: String, lang: Lang, recentClassId: String? = null): Answer {
        val q = question.lowercase().trim()
        if (q.isEmpty()) return Answer(help(lang))

        general(q, lang)?.let { return it }

        val pointsAtScan = recentClassId != null && Regex("this|that|my plant|इस|यह|मेरी|मेरे").containsMatchIn(q)
        val crop = cropWords.entries.firstOrNull { (_, ws) -> ws.any { q.contains(it) } }?.key
            ?: if (pointsAtScan) ClassId.crop(recentClassId!!) else null
        var disease = findDisease(q, crop)
        if (disease == null && pointsAtScan) disease = recentClassId

        if (disease == null) {
            if (crop != null) {
                val names = kb.diseaseIds(crop).joinToString("\n") { "• " + kb.info(it).name.get(lang) }
                val head = if (lang == Lang.HI) "${kb.cropName(crop).get(lang)} में ये समस्याएँ मैं पहचान सकता हूँ:" else "I can help with these ${kb.cropName(crop).en} problems:"
                val tail = if (lang == Lang.HI) "किसी का नाम लिखकर पूछें, या पत्ती का फोटो स्कैन करें।" else "Ask about one by name, or scan a leaf photo."
                return Answer("$head\n$names\n\n$tail")
            }
            return Answer(help(lang))
        }

        val info = kb.info(disease)
        val intent = Intent.values().firstOrNull { it != Intent.OVERVIEW && intentWords[it]?.any { w -> q.contains(w) } == true } ?: Intent.OVERVIEW
        val title = "${info.cropName.get(lang)}: ${info.name.get(lang)}"
        val body = when (intent) {
            Intent.CAUSE -> info.cause.get(lang)
            Intent.SYMPTOMS -> info.symptoms.get(lang).ifBlank { info.cause.get(lang) }
            Intent.ORGANIC -> bullets(info.organic.get(lang), lang, "organic")
            Intent.PREVENT -> bullets(info.prevent.get(lang), lang, "prevent")
            Intent.TREAT -> bullets(info.actions.get(lang), lang, "actions") + "\n\n" +
                bullets(info.chemical.get(lang), lang, "chemical") + "\n\n" + safety(lang)
            Intent.SPREAD -> if (lang == Lang.HI) {
                if (info.spreadsFast) "यह रोग तेज़ी से फैल सकता है, खासकर नमी और बारिश में। प्रभावित पौधे जल्दी हटाएँ।"
                else "यह आम तौर पर धीरे फैलता है, फिर भी प्रभावित पत्तियाँ हटाएँ और निगरानी रखें।"
            } else {
                if (info.spreadsFast) "It can spread fast, especially in humid or rainy weather. Remove affected plants early."
                else "It usually spreads slowly, but remove affected leaves and keep watching."
            }
            Intent.WHEN -> if (lang == Lang.HI) "पहले छिड़काव के बाद ${info.followUpDays} दिन में दोबारा स्कैन करें। छिड़काव सुबह या शाम, हवा और बारिश के बिना करें।"
            else "Re-scan ${info.followUpDays} days after the first spray. Spray in the early morning or evening, with no wind or rain."
            Intent.OVERVIEW -> overview(info, lang)
        }
        return Answer("$title\n\n$body", disease)
    }

    private fun findDisease(q: String, crop: String?): String? {
        val pool = if (crop != null) kb.diseaseIds(crop) else kb.cropIds.flatMap { kb.diseaseIds(it) }
        var best: String? = null
        var bestScore = 0
        for (id in pool) {
            val d = ClassId.disease(id)
            var score = 0
            val info = kb.info(id)
            if (q.contains(info.name.en.lowercase())) score += 5
            if (info.name.hi.isNotBlank() && q.contains(info.name.hi)) score += 5
            diseaseWords[d]?.forEach { if (q.contains(it)) score += 2 }
            kb.entry(id)?.keywords?.forEach { if (q.contains(it.lowercase())) score += 1 }
            if (crop == null && score > 0) {
                // without a crop, a disease word alone is ambiguous: only accept strong matches
                if (score < 4) score = 0
            }
            if (score > bestScore) { bestScore = score; best = id }
        }
        return best
    }

    private fun overview(i: Info, lang: Lang): String {
        val sb = StringBuilder()
        sb.append(i.cause.get(lang)).append("\n\n")
        sb.append(bullets(i.actions.get(lang), lang, "actions"))
        return sb.toString()
    }

    private fun bullets(list: List<String>, lang: Lang, kind: String): String {
        if (list.isEmpty()) return if (lang == Lang.HI) "इसके लिए अभी जानकारी उपलब्ध नहीं है।" else "No information available for this yet."
        val head = when (kind) {
            "organic" -> if (lang == Lang.HI) "जैविक / कम लागत विकल्प:" else "Organic / low-cost options:"
            "prevent" -> if (lang == Lang.HI) "रोकथाम:" else "Prevention:"
            "chemical" -> if (lang == Lang.HI) "रासायनिक विकल्प (लेबल पढ़ें):" else "Chemical options (read the label):"
            else -> if (lang == Lang.HI) "आज क्या करें:" else "What to do today:"
        }
        return head + "\n" + list.joinToString("\n") { "• $it" }
    }

    private fun safety(lang: Lang) = if (lang == Lang.HI)
        "सावधानी: दस्ताने और मास्क पहनें, लेबल की मात्रा से ज़्यादा न डालें, और पक्का करने के लिए KVK से पूछें।"
    else "Safety: wear gloves and a mask, never exceed the label dose, and confirm with your KVK."

    private fun general(q: String, lang: Lang): Answer? {
        val hi = lang == Lang.HI
        return when {
            Regex("when.*spray|best time.*spray|spray.*time|कब.*छिड़काव|छिड़काव.*कब|छिड़काव.*समय").containsMatchIn(q) -> Answer(
                if (hi) "छिड़काव सुबह जल्दी या शाम को करें। तेज़ हवा, दोपहर की धूप या बारिश की संभावना (अगले 6 घंटे) में न करें। 'आज का मौसम' कार्ड देखें।"
                else "Spray early morning or evening. Avoid strong wind, midday sun, or rain expected in the next 6 hours. See the spray-weather card on the home screen.")
            Regex("safe|safety|gloves|mask|सुरक्षा|दस्ताने|सावधानी").containsMatchIn(q) -> Answer(safety(lang))
            Regex("how.*scan|how.*use|help|कैसे.*स्कैन|मदद").containsMatchIn(q) -> Answer(help(lang))
            else -> null
        }
    }

    fun help(lang: Lang) = if (lang == Lang.HI)
        "आप ऐसे पूछ सकते हैं:\n• टमाटर पछेती झुलसा का इलाज\n• धान में झोंका रोग क्यों होता है\n• आलू की बीमारी से बचाव\n• छिड़काव कब करें\n\nया पत्ती का फोटो स्कैन करें।"
    else "You can ask things like:\n• How to treat tomato late blight\n• Why does rice blast happen\n• How to prevent potato blight\n• When should I spray\n\nOr scan a leaf photo."
}
