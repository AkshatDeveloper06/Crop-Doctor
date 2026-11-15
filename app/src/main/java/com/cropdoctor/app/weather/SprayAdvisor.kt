package com.cropdoctor.app.weather

import android.annotation.SuppressLint
import android.content.Context
import android.location.LocationManager
import com.cropdoctor.app.knowledge.Bi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class Verdict3 { GOOD, CAUTION, AVOID }
enum class Risk { LOW, MEDIUM, HIGH }

data class Hour(val time: String, val tempC: Double, val humidity: Double, val windKmh: Double, val rainPct: Double)

data class Forecast(val hours: List<Hour>, val nowIndex: Int)

data class SprayAdvice(
    val verdict: Verdict3,
    val reasons: List<Bi>,
    val diseaseRisk: Risk,
    val riskNote: Bi,
)

/**
 * Optional online feature: spray timing + disease-weather risk from Open-Meteo (free, no key, no account).
 * The ONLY network call in the app, and it sends only approximate coordinates (never photos or personal data).
 * Offline, the card simply says the forecast is unavailable.
 */
object SprayAdvisor {

    /** Pure rule engine, unit tested. Looks at the next 6 hours for spraying and the next 24 for disease risk. */
    fun advise(f: Forecast): SprayAdvice {
        val next6 = f.hours.drop(f.nowIndex).take(6)
        val next24 = f.hours.drop(f.nowIndex).take(24)
        val reasons = mutableListOf<Bi>()
        var verdict = Verdict3.GOOD
        fun worsen(v: Verdict3) { if (v.ordinal > verdict.ordinal) verdict = v }

        val rain = next6.maxOfOrNull { it.rainPct } ?: 0.0
        val wind = next6.maxOfOrNull { it.windKmh } ?: 0.0
        val temp = next6.maxOfOrNull { it.tempC } ?: 25.0
        if (rain >= 50) {
            worsen(Verdict3.AVOID)
            reasons += Bi("Rain likely in the next 6 hours (${rain.toInt()}%): the spray would wash off.", "अगले 6 घंटे में बारिश की संभावना (${rain.toInt()}%): छिड़काव धुल जाएगा।")
        } else if (rain >= 30) {
            worsen(Verdict3.CAUTION)
            reasons += Bi("Some chance of rain (${rain.toInt()}%). Prefer a protected-from-rain window.", "बारिश की कुछ संभावना (${rain.toInt()}%)। बारिश-मुक्त समय चुनें।")
        }
        if (wind >= 20) {
            worsen(Verdict3.AVOID)
            reasons += Bi("Strong wind (${wind.toInt()} km/h): spray will drift away from the crop.", "तेज़ हवा (${wind.toInt()} किमी/घं): दवा फसल से दूर उड़ जाएगी।")
        } else if (wind >= 12) {
            worsen(Verdict3.CAUTION)
            reasons += Bi("Breezy (${wind.toInt()} km/h). Spray low and keep your back to the wind.", "हवा चल रही है (${wind.toInt()} किमी/घं)। नीचे से छिड़कें और हवा की दिशा में पीठ रखें।")
        }
        if (temp >= 35) {
            worsen(Verdict3.CAUTION)
            reasons += Bi("Very hot (${temp.toInt()}°C). Spray in early morning or evening to avoid leaf burn.", "बहुत गर्मी (${temp.toInt()}°C)। पत्ती जलने से बचने के लिए सुबह जल्दी या शाम को छिड़कें।")
        }
        if (reasons.isEmpty()) {
            reasons += Bi("Calm, dry weather for the next 6 hours. Good window; early morning or evening is best.", "अगले 6 घंटे मौसम शांत और सूखा है। छिड़काव का अच्छा समय; सुबह या शाम सबसे अच्छी।")
        }

        val rh = next24.map { it.humidity }.average().takeIf { !it.isNaN() } ?: 60.0
        val t = next24.map { it.tempC }.average().takeIf { !it.isNaN() } ?: 25.0
        val wetHours = next24.count { it.humidity >= 85 }
        val risk = when {
            rh >= 82 && t in 10.0..28.0 && wetHours >= 8 -> Risk.HIGH
            rh >= 72 && t in 8.0..32.0 -> Risk.MEDIUM
            else -> Risk.LOW
        }
        val note = when (risk) {
            Risk.HIGH -> Bi("Cool and humid for many hours: blights, mildews and blast spread fastest in this weather. Scout today.", "कई घंटे ठंडा और नम मौसम: झुलसा, मिल्ड्यू और ब्लास्ट ऐसे मौसम में सबसे तेज़ फैलते हैं। आज खेत देखें।")
            Risk.MEDIUM -> Bi("Moderate humidity: keep scouting every 3-4 days.", "मध्यम नमी: हर 3-4 दिन में खेत देखते रहें।")
            Risk.LOW -> Bi("Weather is not favourable for most fungal diseases right now.", "मौसम अभी ज़्यादातर फफूंद रोगों के अनुकूल नहीं है।")
        }
        return SprayAdvice(verdict, reasons, risk, note)
    }

    fun parse(json: String): Forecast? = runCatching {
        val o = JSONObject(json)
        val h = o.getJSONObject("hourly")
        val times = h.getJSONArray("time")
        val temp = h.getJSONArray("temperature_2m")
        val rh = h.getJSONArray("relative_humidity_2m")
        val wind = h.getJSONArray("wind_speed_10m")
        val rain = h.getJSONArray("precipitation_probability")
        val hours = (0 until times.length()).map {
            Hour(times.getString(it), temp.optDouble(it, 25.0), rh.optDouble(it, 60.0), wind.optDouble(it, 0.0), rain.optDouble(it, 0.0))
        }
        val nowPrefix = o.getJSONObject("current").getString("time").take(13)   // yyyy-MM-ddTHH
        val idx = hours.indexOfFirst { it.time.take(13) >= nowPrefix }.coerceAtLeast(0)
        Forecast(hours, idx)
    }.getOrNull()

    suspend fun fetch(lat: Double, lon: Double): Forecast? = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f".format(java.util.Locale.US, lat, lon) +
                    "&current=temperature_2m&hourly=temperature_2m,relative_humidity_2m,wind_speed_10m,precipitation_probability" +
                    "&forecast_days=2&timezone=auto"
            )
            val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 6000 }
            try {
                if (c.responseCode != 200) null else parse(c.inputStream.bufferedReader().readText())
            } finally { c.disconnect() }
        }.getOrNull()
    }

    /** Coarse last-known location (needs ACCESS_COARSE_LOCATION). Rounded to ~1 km before use. */
    @SuppressLint("MissingPermission")
    fun lastKnown(context: Context): Pair<Double, Double>? = runCatching {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val best = lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
        best?.let { it.latitude to it.longitude }
    }.getOrNull()
}
