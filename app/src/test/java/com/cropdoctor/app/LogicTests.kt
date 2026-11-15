package com.cropdoctor.app

import com.cropdoctor.app.ai.Certainty
import com.cropdoctor.app.audio.SpeechScript
import com.cropdoctor.app.ai.ClassId
import com.cropdoctor.app.ai.ModelMeta
import com.cropdoctor.app.ai.ImageQuality
import com.cropdoctor.app.ai.QualityIssue
import com.cropdoctor.app.ai.Ranker
import com.cropdoctor.app.ai.Smoother
import com.cropdoctor.app.knowledge.KnowledgeBase
import com.cropdoctor.app.knowledge.Lang
import com.cropdoctor.app.knowledge.OfflineAdvisor
import com.cropdoctor.app.ocr.DoseUnit
import com.cropdoctor.app.ocr.LabelParser
import com.cropdoctor.app.weather.Forecast
import com.cropdoctor.app.weather.Hour
import com.cropdoctor.app.weather.Risk
import com.cropdoctor.app.weather.SprayAdvisor
import com.cropdoctor.app.weather.Verdict3
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LabelParserTests {
    private fun dose(s: String) = LabelParser.parse(s)

    @Test fun simpleMlPerLitre() { val d = dose("Dosage: 2 ml/L of water")!!; assertEquals(2.0, d.perLitre, 1e-9); assertEquals(DoseUnit.ML, d.unit) }
    @Test fun gramsPerLtr() { val d = dose("2gm/ltr")!!; assertEquals(2.0, d.perLitre, 1e-9); assertEquals(DoseUnit.G, d.unit) }
    @Test fun rangeUsesLowerValue() { assertEquals(2.0, dose("2-2.5 ml per lit")!!.perLitre, 1e-9) }
    @Test fun perTenLitres() { assertEquals(2.0, dose("20 ml per 10 litre")!!.perLitre, 1e-9) }
    @Test fun perAcreInWater() { assertEquals(2.5, dose("500 ml per acre in 200 litres of water")!!.perLitre, 1e-9) }
    @Test fun hindi() { val d = dose("2 मि.ली. प्रति लीटर")!!; assertEquals(2.0, d.perLitre, 1e-9); assertEquals(DoseUnit.ML, d.unit) }
    @Test fun hindiDigitsAndGram() { val d = dose("२.५ ग्राम प्रति लीटर पानी")!!; assertEquals(2.5, d.perLitre, 1e-9); assertEquals(DoseUnit.G, d.unit) }
    @Test fun perHectareOnlyIsIgnored() { assertNull(dose("1000 ml per hectare")) }
    @Test fun noDose() { assertNull(dose("Keep out of reach of children")) }
    @Test fun tankMath() { assertEquals("32 g", LabelParser.mix(dose("2 g/L")!!, 16.0).pretty()) }
    @Test fun bigAmountsConvert() { assertEquals("1 L", LabelParser.mix(dose("5 ml/L")!!, 200.0).pretty()) }
}

class RankerTests {
    private val labels = listOf("Background___not_a_leaf", "Tomato___early_blight", "Tomato___late_blight", "Potato___late_blight", "Rice___tungro")

    @Test fun cropFirstBeatsSplitMass() {
        // Tomato mass is split over two diseases (0.30 + 0.28 = 0.58) but potato has a single 0.34 class.
        val p = floatArrayOf(0.02f, 0.30f, 0.28f, 0.34f, 0.06f)
        val v = Ranker.rank(p, labels, 0.6f, 0.35f)
        assertEquals("Tomato___early_blight", v.classId)
    }

    @Test fun backgroundWins() {
        val p = floatArrayOf(0.7f, 0.1f, 0.1f, 0.05f, 0.05f)
        assertEquals(Certainty.NOT_A_LEAF, Ranker.rank(p, labels, 0.6f, 0.35f).certainty)
    }

    @Test fun certaintyLevels() {
        assertEquals(Certainty.CONFIDENT, Ranker.rank(floatArrayOf(0f, 0.9f, 0.05f, 0.03f, 0.02f), labels, 0.6f, 0.35f).certainty)
        assertEquals(Certainty.UNSURE, Ranker.rank(floatArrayOf(0f, 0.45f, 0.2f, 0.2f, 0.15f), labels, 0.6f, 0.35f).certainty)
        assertEquals(Certainty.UNKNOWN, Ranker.rank(floatArrayOf(0.1f, 0.25f, 0.2f, 0.25f, 0.2f), labels, 0.6f, 0.35f).certainty)
    }

    @Test fun smootherAverages() {
        val s = Smoother(2)
        s.push(floatArrayOf(1f, 0f)); val out = s.push(floatArrayOf(0f, 1f))
        assertEquals(0.5f, out[0], 1e-6f)
        val out2 = s.push(floatArrayOf(0f, 1f))   // window of 2 drops the first frame
        assertEquals(0f, out2[0], 1e-6f)
    }
}

class QualityTests {
    private fun solid(rgb: Int, n: Int = 64) = IntArray(n * n) { (0xFF shl 24) or rgb }

    @Test fun flatImageIsBlurry() {
        val r = ImageQuality.analyze(solid(0x40A040), 64, 64)
        assertTrue(QualityIssue.TOO_BLURRY in r.issues)
    }

    @Test fun darkImageFlagged() {
        val r = ImageQuality.analyze(solid(0x050505), 64, 64)
        assertTrue(QualityIssue.TOO_DARK in r.issues)
    }

    @Test fun sharpGreenTextureIsFine() {
        val n = 64
        val px = IntArray(n * n) { i -> val x = i % n; val y = i / n; if ((x / 2 + y / 2) % 2 == 0) 0xFF2E7D32.toInt() else 0xFF9CCC65.toInt() }
        val r = ImageQuality.analyze(px, n, n)
        assertTrue("issues=${r.issues}", r.ok)
        assertTrue(r.leafCoverage > 0.9f)
    }

    @Test fun greyImageHasNoLeaf() {
        val n = 64
        val px = IntArray(n * n) { i -> val v = if (((i % n) / 2 + (i / n) / 2) % 2 == 0) 90 else 170; (0xFF shl 24) or (v shl 16) or (v shl 8) or v }
        assertTrue(QualityIssue.LEAF_TOO_SMALL in ImageQuality.analyze(px, n, n).issues)
    }
}

class WeatherTests {
    private fun f(rain: Double = 0.0, wind: Double = 5.0, temp: Double = 28.0, rh: Double = 55.0, hours: Int = 30) =
        Forecast(List(hours) { Hour("2026-01-01T%02d:00".format(it % 24), temp, rh, wind, rain) }, 0)

    @Test fun calmDryIsGood() { assertEquals(Verdict3.GOOD, SprayAdvisor.advise(f()).verdict) }
    @Test fun rainMeansAvoid() { assertEquals(Verdict3.AVOID, SprayAdvisor.advise(f(rain = 70.0)).verdict) }
    @Test fun windMeansAvoid() { assertEquals(Verdict3.AVOID, SprayAdvisor.advise(f(wind = 25.0)).verdict) }
    @Test fun hotIsCaution() { assertEquals(Verdict3.CAUTION, SprayAdvisor.advise(f(temp = 38.0)).verdict) }
    @Test fun coolHumidIsHighRisk() { assertEquals(Risk.HIGH, SprayAdvisor.advise(f(temp = 18.0, rh = 92.0)).diseaseRisk) }
    @Test fun hotDryIsLowRisk() { assertEquals(Risk.LOW, SprayAdvisor.advise(f(temp = 36.0, rh = 40.0)).diseaseRisk) }

    @Test fun parsesOpenMeteoShape() {
        val json = """{"current":{"time":"2026-01-01T02:15"},"hourly":{"time":["2026-01-01T00:00","2026-01-01T01:00","2026-01-01T02:00","2026-01-01T03:00"],
            "temperature_2m":[20,20,21,22],"relative_humidity_2m":[80,80,81,82],"wind_speed_10m":[5,5,6,7],"precipitation_probability":[0,0,10,20]}}"""
        val fc = SprayAdvisor.parse(json)
        assertNotNull(fc)
        assertEquals(2, fc!!.nowIndex)
        assertEquals(4, fc.hours.size)
    }
}

/** Checks the real knowledge_base.json that ships in the app. */
class KnowledgeBaseTests {
    private val kb: KnowledgeBase = KnowledgeBase.parse(File("src/main/assets/knowledge_base.json").readText())

    @Test fun everyDiseaseIsCompleteAndBilingual() {
        var n = 0
        for (crop in kb.cropIds) for (id in kb.diseaseIds(crop)) {
            n++
            val d = kb.entry(id)!!
            val tag = id
            assertTrue("$tag name", d.name.en.isNotBlank() && d.name.hi.isNotBlank())
            assertTrue("$tag cause", d.cause.en.isNotBlank() && d.cause.hi.isNotBlank())
            assertTrue("$tag actions", d.actions.en.isNotEmpty() && d.actions.en.size == d.actions.hi.size)
            assertTrue("$tag organic", d.organic.en.isNotEmpty() && d.organic.en.size == d.organic.hi.size)
            assertTrue("$tag chemical", d.chemical.en.isNotEmpty() && d.chemical.en.size == d.chemical.hi.size)
            assertTrue("$tag prevent", d.prevent.en.isNotEmpty() && d.prevent.en.size == d.prevent.hi.size)
            assertTrue("$tag severity", d.severity in 1..3)
        }
        assertEquals(44, n)
    }

    @Test fun healthyAndUnknownNeverCrash() {
        assertEquals(0, kb.info("Tomato___healthy").severity)
        assertEquals(false, kb.info("Tomato___something_new").known)
    }

    @Test fun offlineAdvisorEnglish() {
        val a = OfflineAdvisor(kb)
        val ans = a.answer("how to treat tomato late blight", Lang.EN)
        assertEquals("Tomato___late_blight", ans.classId)
        assertTrue(ans.text.contains("Metalaxyl", ignoreCase = true))
    }

    @Test fun offlineAdvisorHindi() {
        val a = OfflineAdvisor(kb)
        assertEquals("Tomato___late_blight", a.answer("टमाटर पछेती झुलसा का इलाज", Lang.HI).classId)
        assertEquals("Rice___leaf_blast", a.answer("धान में झोंका रोग क्यों होता है", Lang.HI).classId)
    }

    @Test fun contextualQuestion() {
        val a = OfflineAdvisor(kb)
        val ans = a.answer("what is the organic option for this", Lang.EN, "Potato___late_blight")
        assertEquals("Potato___late_blight", ans.classId)
    }

    @Test fun cropOnlyListsDiseases() {
        val a = OfflineAdvisor(kb)
        val ans = a.answer("sugarcane problems", Lang.EN)
        assertNull(ans.classId)
        assertTrue(ans.text.contains("Red rot"))
    }
}

/** The model files in assets must agree with each other and with the knowledge base. */
class ModelAssetsTests {
    private val dir = File("src/main/assets")
    private val labels = File(dir, "labels.txt").readLines().map { it.trim() }.filter { it.isNotEmpty() }
    private val map = org.json.JSONObject(File(dir, "label_map.json").readText()).let { o -> o.keys().asSequence().associateWith { o.getString(it) } }
    private val kb: KnowledgeBase = KnowledgeBase.parse(File(dir, "knowledge_base.json").readText())

    @Test fun everyModelLabelIsMapped() {
        assertEquals(54, labels.size)
        for (l in labels) assertTrue("unmapped label $l", map.containsKey(l))
        assertEquals(labels.toSet(), map.keys)
    }

    @Test fun onlyTheRaspberryDuplicateIsMerged() {
        val merged = labels.map { map.getValue(it) }.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals(setOf("Raspberry___healthy"), merged)
    }

    @Test fun everyDiseaseClassHasAdviceInTheKnowledgeBase() {
        for (id in labels.map { map.getValue(it) }.distinct()) {
            if (ClassId.isBackground(id) || ClassId.isHealthy(id)) continue
            assertNotNull("no knowledge-base entry for $id", kb.entry(id))
        }
    }

    @Test fun exactlyOneBackgroundClass() {
        assertEquals(1, labels.map { map.getValue(it) }.distinct().count { ClassId.isBackground(it) })
    }

    @Test fun metaParsesAndThresholdsAreOrdered() {
        val m = com.google.gson.Gson().fromJson(File(dir, "model_meta.json").readText(), ModelMeta::class.java)
        assertTrue(m.confident_threshold > m.unsure_threshold && m.unsure_threshold > 0f)
        assertEquals(54, m.num_classes)
    }
}

class SmootherTests {
    @Test fun emaFollowsTheNewFrameGradually() {
        val s = com.cropdoctor.app.ai.EmaSmoother(0.35f)
        val first = s.push(floatArrayOf(1f, 0f))
        assertEquals(1f, first[0], 1e-6f)
        val second = s.push(floatArrayOf(0f, 1f))
        assertEquals(0.65f, second[0], 1e-5f)
        assertEquals(0.35f, second[1], 1e-5f)
        s.reset()
        assertEquals(0f, s.push(floatArrayOf(0f, 1f))[0], 1e-6f)
    }
}

class SpeechScriptTests {
    private val kb = KnowledgeBase.parse(File("src/main/assets/knowledge_base.json").readText())
    private val blight = kb.info("Tomato___late_blight")

    @Test fun confidentResultIsFullAndNeverSendsYouToAnExpertForNothing() {
        val t = SpeechScript.diagnosis(blight, Lang.EN, 0.93f, Certainty.CONFIDENT)
        for (part in listOf("Tomato", "Late blight", "93 percent", "Severity: severe", "spread fast", "Why it happens", "What it looks like",
            "What to do today", "Low-cost or organic options", "Chemical options", "gloves and a mask", "How to prevent it next time",
            "again in 3 days")) assertTrue("missing: $part\n$t", t.contains(part))
        assertTrue(t.length > 700)
        assertTrue("must not refer to an expert when the answer is given:\n$t", !t.contains("KVK") && !t.contains("agriculture"))
    }

    @Test fun hindiResultIsFullAndHasNoStockReferral() {
        val t = SpeechScript.diagnosis(blight, Lang.HI, 0.93f, Certainty.CONFIDENT)
        for (part in listOf("93 प्रतिशत", "गंभीरता", "आज क्या करें", "रासायनिक", "दस्ताने", "रोकथाम", "3 दिन बाद")) assertTrue("missing: $part", t.contains(part))
        assertTrue(!t.contains("KVK"))
        assertTrue("Hindi sentences end with a danda: $t", t.startsWith("टमाटर। पछेती झुलसा।"))
    }

    @Test fun referralIsSpokenWhenItIsReallyNeeded() {
        assertTrue(SpeechScript.diagnosis(blight, Lang.EN, 0.2f, Certainty.UNKNOWN).contains("KVK"))
        assertTrue(SpeechScript.diagnosis(blight, Lang.HI, 0.2f, Certainty.UNKNOWN).contains("KVK"))
        val unsure = SpeechScript.diagnosis(blight, Lang.EN, 0.5f, Certainty.UNSURE, alternatives = listOf("Tomato Early blight"))
        assertTrue(unsure.contains("Other possibilities: Tomato Early blight") && !unsure.contains("KVK"))
    }

    @Test fun problemWithNoSavedAdviceUsesAiAdviceOrAsksAnExpert() {
        val unknown = kb.info("Tomato___something_new")
        assertTrue(SpeechScript.diagnosis(unknown, Lang.EN, 0.9f, Certainty.CONFIDENT).contains("KVK"))
        val withAi = SpeechScript.diagnosis(unknown, Lang.EN, 0.9f, Certainty.CONFIDENT, aiAdvice = "Use neem oil every week.")
        assertTrue(withAi.contains("Use neem oil every week.") && !withAi.contains("KVK"))
    }

    @Test fun healthyLeafIsDescribedAsHealthy() {
        val t = SpeechScript.diagnosis(kb.info("Tomato___healthy"), Lang.EN, 0.9f, Certainty.CONFIDENT)
        assertTrue(t.contains("No disease signs") && !t.contains("Severity") && !t.contains("Why it happens"))
        assertEquals(1, Regex("No disease signs").findAll(t).count())                // said once, not twice
    }

    @Test fun labelReminderIsNotRepeated() {
        val t = SpeechScript.diagnosis(kb.info("Rice___leaf_blast"), Lang.EN, 0.9f, Certainty.CONFIDENT)
        assertTrue(!t.contains("Follow the label.") && t.contains("Use the dose printed on the product label"))
    }

    @Test fun aiAdviceAlreadyFetchedIsReadToo() {
        val t = SpeechScript.diagnosis(blight, Lang.EN, 0.9f, Certainty.CONFIDENT, aiAdvice = "1. Do today - Remove infected leaves.")
        assertTrue(t.contains("More advice.") && t.contains("Remove infected leaves"))
    }

    @Test fun unitsSymbolsAndRangesAreSpokenAsWords() {
        val s = SpeechScript.forSpeech("Mancozeb 2.5 g per litre, 250 ml, 2-3 years, 5%, 35°C, metalaxyl + mancozeb • done", Lang.EN)
        for (part in listOf("2.5 grams", "250 millilitres", "2 to 3 years", "5 percent", "35 degrees Celsius", "metalaxyl plus mancozeb")) assertTrue("$part in: $s", s.contains(part))
        assertTrue(!s.contains("•"))
        assertEquals(s, SpeechScript.forSpeech(s, Lang.EN))                       // saying it twice changes nothing
        val h = SpeechScript.forSpeech("मैंकोज़ेब 40 g, 7-10 दिन, 5%", Lang.HI)
        assertTrue(h.contains("40 ग्राम") && h.contains("7 से 10 दिन") && h.contains("5 प्रतिशत"))
    }

    @Test fun genericReferralClauseIsDroppedButSafetyWordsStay() {
        val en = SpeechScript.withoutReferral("Remove infected plants. Safety: wear gloves and a mask, never exceed the label dose, and confirm with your KVK.")
        assertTrue(en.contains("wear gloves and a mask") && en.contains("never exceed the label dose") && !en.contains("KVK"))
        val hi = SpeechScript.withoutReferral("संक्रमित पौधे हटाएँ। सावधानी: दस्ताने और मास्क पहनें, लेबल की मात्रा से ज़्यादा न डालें, और पक्का करने के लिए KVK से पूछें।")
        assertTrue(hi.contains("दस्ताने") && hi.contains("लेबल की मात्रा") && !hi.contains("KVK"))
        val sentence = SpeechScript.withoutReferral("Remove infected plants. Take a clear photo and show it to your nearest KVK / agriculture officer. Spray at dusk.")
        assertTrue(!sentence.contains("KVK") && sentence.contains("Spray at dusk"))
        assertEquals("Ask your KVK.", SpeechScript.withoutReferral("Ask your KVK."))   // an answer that is only a referral keeps it
    }

    @Test fun longTextIsCutIntoChunksTheEngineAccepts() {
        val long = SpeechScript.diagnosis(blight, Lang.EN, 0.9f, Certainty.CONFIDENT, aiAdvice = "x ".repeat(600))
        val chunks = SpeechScript.chunks(SpeechScript.forSpeech(long, Lang.EN), 400)
        assertTrue(chunks.size > 2 && chunks.all { it.length <= 400 && it.isNotBlank() })
        val oneBig = SpeechScript.chunks("word ".repeat(300).trim(), 400)
        assertTrue(oneBig.all { it.length <= 400 } && oneBig.joinToString(" ").split(" ").size == 300)
    }
}

class CaptureVerdictTests {
    private fun v(id: String?, c: Certainty) = com.cropdoctor.app.ai.Verdict(id, 0.8f, 0.9f, c, emptyList())

    @Test fun capturedPhotoKeepsTheAnswerTheFarmerWasLookingAt() {
        val seen = v("Tomato___late_blight", Certainty.CONFIDENT)
        var classified = false
        val r = com.cropdoctor.app.ai.pickVerdict(seen) { classified = true; v("Corn___common_rust", Certainty.CONFIDENT) }
        assertEquals("Tomato___late_blight", r.classId)
        assertTrue("the still photo must not be re-classified", !classified)
    }

    @Test fun anUnsureAnswerIsAlsoKept() {
        assertEquals("Rice___brown_spot", com.cropdoctor.app.ai.pickVerdict(v("Rice___brown_spot", Certainty.UNSURE)) { v("Rice___leaf_blast", Certainty.CONFIDENT) }.classId)
    }

    @Test fun withoutAUsableLiveAnswerTheStillIsClassified() {
        assertEquals("Corn___common_rust", com.cropdoctor.app.ai.pickVerdict(null) { v("Corn___common_rust", Certainty.CONFIDENT) }.classId)
        assertEquals("Corn___common_rust", com.cropdoctor.app.ai.pickVerdict(v(null, Certainty.NOT_A_LEAF)) { v("Corn___common_rust", Certainty.CONFIDENT) }.classId)
    }
}
