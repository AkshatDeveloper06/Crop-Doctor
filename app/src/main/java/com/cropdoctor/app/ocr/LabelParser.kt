package com.cropdoctor.app.ocr

import java.util.Locale

enum class DoseUnit(val short: String) { ML("ml"), G("g") }

/** A dose normalised to "amount per 1 litre of spray water". */
data class Dose(val perLitre: Double, val unit: DoseUnit)

data class Mix(val amount: Double, val unit: DoseUnit, val tankLitres: Double) {
    /** "32 g" / "1.5 kg" / "250 ml" / "1.2 L" */
    fun pretty(): String {
        val a = amount
        return when {
            unit == DoseUnit.G && a >= 1000 -> "${fmt(a / 1000)} kg"
            unit == DoseUnit.ML && a >= 1000 -> "${fmt(a / 1000)} L"
            else -> "${fmt(a)} ${unit.short}"
        }
    }
}

private fun fmt(v: Double): String {
    val r = Math.round(v * 10) / 10.0
    return if (r == Math.floor(r)) r.toLong().toString() else String.format(Locale.US, "%.1f", r)
}

object LabelParser {
    private const val NUM = """(\d+(?:\.\d+)?)"""
    private const val RANGE = """(?:\s*(?:-|–|—|to|से)\s*(\d+(?:\.\d+)?))?"""
    private const val UNIT = """(ml|g)"""
    private const val PER = """\s*(?:/|per|प्रति|in|के लिए|हर|each)?\s*"""
    private const val LITRE = """l\b"""

    // "2 ml / 1 l", "2-2.5 g per 1 l", "20 ml per 10 l"
    private val perLitre = Regex("$NUM$RANGE\\s*$UNIT$PER(?:$NUM\\s*)?$LITRE")

    // "500 ml per acre in 200 l"  (hectare-only doses are intentionally NOT converted)
    private val perAcre = Regex("$NUM$RANGE\\s*$UNIT\\s*(?:/|per|प्रति)\\s*(?:acre|ac|एकड़|एकड)\\b.{0,25}?$NUM\\s*$LITRE")

    /** Normalises OCR text: lower-case, Devanagari digits -> ASCII, unit words -> ml / g / l. */
    fun normalise(raw: String): String {
        var s = raw.lowercase(Locale.ROOT)
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '०'..'९') ('0' + (ch - '०')) else ch)
        s = sb.toString()
        s = s.replace(Regex("(\\d),(\\d)"), "$1.$2")               // 2,5 -> 2.5
        s = s.replace(Regex("(?<=\\d)\\s*(?=[a-zA-Zऀ-ॿ])"), " ")
        // order matters: millilitre before litre
        s = s.replace(Regex("(?:\\bm\\.?\\s?l\\.?\\b|मि\\.?\\s?ली\\.?|मिली|मिलि|मि\\.ली|millilit(?:re|er)s?|\\bcc\\b)"), " ml ")
        s = s.replace(Regex("(?:ग्राम|ग्रा\\.?|\\bgms?\\b|\\bgrams?\\b|\\bgr\\b|\\bg\\.\\b)"), " g ")
        s = s.replace(Regex("(?:लीटर|लिटर|\\blitres?\\b|\\bliters?\\b|\\bltrs?\\b|\\blit\\b|\\bltr\\b|\\bli\\b|\\bltr\\.|\\bl\\.)"), " l ")
        s = s.replace("/", " / ")
        return s.replace(Regex("\\s+"), " ").trim()
    }

    /** Returns the first dose found in the text, or null. When a range is given the LOWER value is used (safer). */
    fun parse(raw: String): Dose? {
        val s = normalise(raw)

        perAcre.find(s)?.let { m ->
            val amt = m.groupValues[1].toDoubleOrNull()
            val water = m.groupValues[4].toDoubleOrNull()
            val unit = unitOf(m.groupValues[3])
            if (amt != null && water != null && water > 0 && unit != null) return Dose(amt / water, unit)
        }
        for (m in perLitre.findAll(s)) {
            val amt = m.groupValues[1].toDoubleOrNull() ?: continue
            val unit = unitOf(m.groupValues[3]) ?: continue
            val litres = m.groupValues[4].toDoubleOrNull() ?: 1.0
            if (litres <= 0 || litres > 1000) continue
            val perL = amt / litres
            if (perL in 0.05..50.0) return Dose(perL, unit)      // sanity: ignore wild OCR junk
        }
        return null
    }

    private fun unitOf(s: String) = when (s) { "ml" -> DoseUnit.ML; "g" -> DoseUnit.G; else -> null }

    fun mix(dose: Dose, tankLitres: Double) = Mix(dose.perLitre * tankLitres, dose.unit, tankLitres)
}
