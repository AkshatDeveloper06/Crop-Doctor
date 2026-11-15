package com.cropdoctor.app.audio

import com.cropdoctor.app.ai.Certainty
import com.cropdoctor.app.ai.CoverageGap
import com.cropdoctor.app.knowledge.Info
import com.cropdoctor.app.knowledge.Lang

/**
 * Everything the app says aloud. Pure text logic with no Android dependency, so it is unit tested.
 *
 *  - [diagnosis] writes the full spoken version of a result: what it is, how sure the app is, why it happens, what it looks like,
 *    what to do today, organic and chemical options, prevention, when to re-check, and any AI advice already fetched.
 *    A referral to a KVK officer is spoken only when it is really needed (low confidence, or no saved advice).
 *  - [forSpeech] turns written text into text a speech engine reads well: units, percentages, ranges, symbols.
 *  - [withoutReferral] drops the stock "confirm with your KVK" clause from an answer that already contains the information.
 *  - [chunks] cuts text at sentence boundaries so no single piece exceeds the engine's input limit.
 */
object SpeechScript {

    /** A closing "Follow the label." in a chemical entry; the safety sentence we add afterwards says it once. */
    private val trailingLabelNote = Regex(
        "(?i)\\s*\\b(?:follow|read|see)(?: the)? (?:product )?label[.।]?\\s*\$|\\s*लेबल (?:देखें|पढ़ें|का पालन करें)[।.]?\\s*\$",
    )

    // ------------------------------------------------------------------ the full diagnosis
    fun diagnosis(
        info: Info,
        lang: Lang,
        confidence: Float,
        certainty: Certainty,
        gap: CoverageGap? = null,
        qualityNote: String? = null,
        alternatives: List<String> = emptyList(),
        aiAdvice: String? = null,
    ): String {
        val hi = lang == Lang.HI
        val sb = StringBuilder()
        fun say(s: String?) {
            val t = s?.trim().orEmpty()
            if (t.isEmpty()) return
            sb.append(t).append(if (t.last() in ".!?।:") " " else if (hi) "। " else ". ")
        }
        fun section(title: String, items: List<String>) {
            val list = items.map { it.trim() }.filter { it.isNotEmpty() }
            if (list.isEmpty()) return
            say(title)
            list.forEach { say(it) }
        }

        say(info.cropName.get(lang))
        say(info.name.get(lang))

        // ---- no saved advice: say so, use AI advice if we have it, otherwise point to an expert (a real reason to)
        if (!info.known) {
            say(if (hi) "इस समस्या की सहेजी हुई सलाह मेरे पास नहीं है।" else "I do not have saved advice for this problem.")
            if (!aiAdvice.isNullOrBlank()) {
                say(aiAdvice)
            } else {
                say(if (hi) "कृपया नज़दीकी KVK या कृषि अधिकारी से पूछें।" else "Please ask your nearest KVK or agriculture officer.")
            }
            return sb.toString().trim()
        }

        // ---- how sure we are
        val pct = (confidence * 100).toInt().coerceIn(0, 100)
        say(
            when (certainty) {
                Certainty.UNKNOWN -> if (hi) "मुझे केवल $pct प्रतिशत भरोसा है, इसलिए इसे अंदाज़ा मानें। कृपया पत्ती नज़दीकी KVK या कृषि अधिकारी को दिखाएँ।"
                else "I am only $pct percent sure, so treat this as a guess. Please show the leaf to your nearest KVK or agriculture officer."
                Certainty.UNSURE -> if (hi) "मुझे $pct प्रतिशत भरोसा है, इसलिए इसे संभावित नतीजा मानें।"
                else "I am $pct percent sure, so treat this as a likely answer."
                else -> if (hi) "मुझे $pct प्रतिशत भरोसा है।" else "I am $pct percent sure."
            },
        )
        if (certainty != Certainty.CONFIDENT && alternatives.isNotEmpty()) {
            say((if (hi) "दूसरी संभावनाएँ: " else "Other possibilities: ") + alternatives.joinToString(", "))
        }
        when (gap) {
            CoverageGap.NO_HEALTHY_CLASS -> say(
                if (hi) "इस फसल के लिए मैंने केवल रोगी पत्तियाँ सीखी हैं, इसलिए मैं स्वस्थ पत्ती की पुष्टि नहीं कर सकता।"
                else "For this crop I only learned diseased leaves, so I cannot confirm that a leaf is healthy.",
            )
            CoverageGap.ONLY_HEALTHY_CLASS -> say(
                if (hi) "इस फसल के लिए मैंने केवल स्वस्थ पत्तियाँ सीखी हैं, इसलिए मैं इसके रोग नहीं पहचान सकता।"
                else "For this crop I only learned healthy leaves, so I cannot detect its diseases.",
            )
            null -> {}
        }
        say(qualityNote)

        // ---- the problem
        val healthy = info.severity <= 0
        if (healthy) {
            say(if (hi) "रोग के कोई लक्षण नहीं मिले।" else "No disease signs were found.")
        } else {
            val sev = when (info.severity) {
                1 -> if (hi) "हल्का" else "mild"
                2 -> if (hi) "मध्यम" else "moderate"
                else -> if (hi) "गंभीर" else "severe"
            }
            say(if (hi) "गंभीरता: $sev।" else "Severity: $sev.")
            if (info.spreadsFast) say(if (hi) "यह रोग तेज़ी से फैल सकता है।" else "This disease can spread fast.")
        }
        if (!healthy && info.cause.get(lang).isNotBlank()) {
            say(if (hi) "ऐसा क्यों होता है।" else "Why it happens.")
            say(info.cause.get(lang))
        }
        if (!healthy && info.symptoms.get(lang).isNotBlank()) {
            say(if (hi) "यह कैसा दिखता है।" else "What it looks like.")
            say(info.symptoms.get(lang))
        }

        // ---- what to do
        section(if (hi) "आज क्या करें।" else "What to do today.", info.actions.get(lang))
        section(if (hi) "जैविक या कम लागत वाले उपाय।" else "Low-cost or organic options.", info.organic.get(lang))
        // a bare "Follow the label." item would repeat the safety sentence that follows, so leave it out
        val chemicals = info.chemical.get(lang).map { it.replace(trailingLabelNote, "").trim() }.filter { it.isNotEmpty() }
        if (chemicals.isNotEmpty()) {
            section(if (hi) "रासायनिक विकल्प।" else "Chemical options.", chemicals)
            say(
                if (hi) "दवा की मात्रा उत्पाद के लेबल के अनुसार ही डालें, और दस्ताने व मास्क पहनें।"
                else "Use the dose printed on the product label, and wear gloves and a mask.",
            )
        }
        section(
            if (healthy) (if (hi) "फसल को स्वस्थ रखने के लिए।" else "To keep the crop healthy.")
            else (if (hi) "अगली बार रोकथाम कैसे करें।" else "How to prevent it next time."),
            info.prevent.get(lang),
        )

        // ---- follow up
        if (info.followUpDays > 0) {
            val n = info.followUpDays
            say(
                if (healthy) (if (hi) "$n दिन बाद फिर जाँच करें।" else "Check again in $n days.")
                else (if (hi) "$n दिन बाद पौधे को दोबारा स्कैन करें, ताकि पता चले कि इलाज असर कर रहा है या नहीं।"
                else "Scan the plant again in $n days to see whether the treatment is working."),
            )
        }

        if (!aiAdvice.isNullOrBlank()) {
            say(if (hi) "और सलाह।" else "More advice.")
            say(aiAdvice)
        }
        return sb.toString().trim()
    }

    // ------------------------------------------------------------------ make written text speakable
    fun forSpeech(text: String, lang: Lang): String {
        val hi = lang == Lang.HI
        var s = text
        s = s.replace(Regex("[•*#`]+"), " ").replace("—", ", ").replace("–", "-")
        // ranges: "2-3 years" -> "2 to 3 years"
        s = s.replace(Regex("(\\d)\\s*-\\s*(\\d)"), "$1 " + (if (hi) "से" else "to") + " $2")
        // units after a number
        s = s.replace(Regex("(?<=\\d)\\s*(?:g|gm|gms)\\b"), if (hi) " ग्राम" else " grams")
        s = s.replace(Regex("(?<=\\d)\\s*ml\\b", RegexOption.IGNORE_CASE), if (hi) " मिली" else " millilitres")
        s = s.replace(Regex("(?<=\\d)\\s*kg\\b", RegexOption.IGNORE_CASE), if (hi) " किलो" else " kilograms")
        s = s.replace(Regex("(?<=\\d)\\s*[lL]\\b"), if (hi) " लीटर" else " litres")
        s = s.replace(Regex("\\s*%"), if (hi) " प्रतिशत" else " percent")
        s = s.replace(Regex("\\s*°\\s*C\\b"), if (hi) " डिग्री सेल्सियस" else " degrees Celsius")
        s = s.replace(Regex("\\s\\+\\s"), if (hi) " और " else " plus ")
        // "A / B" between words -> "A or B" (digits keep their slash)
        s = s.replace(Regex("(?<=[A-Za-z\\u0900-\\u097F])\\s*/\\s*(?=[A-Za-z\\u0900-\\u097F])"), if (hi) " या " else " or ")
        return s.replace(Regex("[ \\t]+"), " ").replace(Regex(" +([.,।!?])"), "$1").trim()
    }

    // ------------------------------------------------------------------ answers that already contain the information
    private val referralClause = listOf(
        Regex(
            ",?\\s*(?:and\\s+)?(?:always\\s+)?(?:confirm|check|ask|verify)\\s+(?:it\\s+|this\\s+|doses\\s+)?(?:with|from)\\s+(?:your\\s+|the\\s+)?(?:nearest\\s+)?" +
                "(?:KVK|Krishi Vigyan Kendra|agriculture (?:officer|centre|center))(?:\\s*(?:/|or)\\s*(?:an?\\s+|your\\s+)?(?:agriculture (?:officer|centre|center)|KVK))?",
            RegexOption.IGNORE_CASE,
        ),
        Regex(
            ",?\\s*(?:और\\s+)?(?:पक्का करने के लिए\\s+|पुष्टि के लिए\\s+)?(?:नज़दीकी\\s+|अपने\\s+)?(?:KVK|कृषि अधिकारी|कृषि केंद्र)" +
                "(?:\\s*(?:/|या)\\s*(?:KVK|कृषि अधिकारी|कृषि केंद्र))?\\s*(?:से|को)\\s*(?:पूछें|पूछ लें|पुष्टि करें|दिखाएँ)",
        ),
    )
    private val referralWord = Regex("(?i)(KVK|Krishi Vigyan|agriculture (?:officer|centre|center)|कृषि अधिकारी|कृषि केंद्र)")
    private val referralVerb = Regex("(?i)(confirm|ask|check|show|consult|visit|contact|verify|पुष्टि|पूछ|दिखा|संपर्क|सलाह|जाँच)")

    /**
     * Removes the generic "confirm with your KVK" part of an answer. A written safety line keeps its useful words
     * ("wear gloves and a mask, never exceed the label dose"). A sentence that is only a referral is dropped, but never
     * when it would leave less than two sentences: an answer that is mostly a referral keeps it.
     */
    fun withoutReferral(text: String): String {
        var s = text
        for (r in referralClause) s = s.replace(r, "")
        val sentences = splitSentences(s)
        val kept = sentences.filterNot { referralWord.containsMatchIn(it) && referralVerb.containsMatchIn(it) }
        return if (kept.size >= 2) kept.joinToString(" ") else s
    }

    // ------------------------------------------------------------------ chunks for the speech engine
    fun chunks(text: String, max: Int = 400): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotBlank()) out += cur.toString().trim(); cur.clear() }
        for (p in splitSentences(text)) {
            if (p.length > max) { flush(); out += splitLong(p, max); continue }
            if (cur.length + p.length + 1 > max) flush()
            if (cur.isNotEmpty()) cur.append(' ')
            cur.append(p)
        }
        flush()
        return out
    }

    private fun splitSentences(t: String): List<String> =
        t.split(Regex("(?<=[.!?।])\\s+|\\n+")).map { it.trim() }.filter { it.isNotEmpty() }

    private fun splitLong(p: String, max: Int): List<String> {
        val out = ArrayList<String>()
        var rest = p
        while (rest.length > max) {
            val comma = rest.lastIndexOf(", ", max)
            val cut = when {
                comma > max / 2 -> comma + 1
                rest.lastIndexOf(' ', max) > 0 -> rest.lastIndexOf(' ', max)
                else -> max
            }
            out += rest.substring(0, cut).trim()
            rest = rest.substring(cut).trimStart(',', ' ')
        }
        if (rest.isNotBlank()) out += rest
        return out
    }
}
