package com.cropdoctor.app.online

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cropdoctor.app.BuildConfig
import com.cropdoctor.app.ai.ClassId
import com.cropdoctor.app.knowledge.Info
import com.cropdoctor.app.knowledge.Lang
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Advice already fetched once, kept on the phone so it still shows offline later. */
class AiCache(context: Context) {
    private val sp = context.getSharedPreferences("ai_cache", Context.MODE_PRIVATE)
    fun get(classId: String, lang: Lang): String? = sp.getString("$classId|${lang.code}", null)?.takeIf { it.isNotBlank() }
    fun put(classId: String, lang: Lang, text: String) = sp.edit().putString("$classId|${lang.code}", text).apply()
}

/**
 * Optional online helper. The offline guide and model are always the safety net: if there is no key, no validated
 * connection, the user turned it off, or the request fails for any reason, the caller simply shows the offline result.
 *
 * Used in two places:
 *  - Ask: free-form questions, grounded in the app's own guide entry.
 *  - Result: extra advice for the diagnosed problem, and full advice for a problem the saved guide has no entry for.
 *
 * Privacy: only the question text, the app language, the crop and disease names and (when relevant) the text of one
 * guide entry are sent. Never photos, location or scan history.
 */
class OnlineAdvisor(private val context: Context) {

    private class Turn(val role: String, val content: String)

    private val history = ArrayList<Turn>()
    private val cache = AiCache(context)

    /** True when the build contains an API key. */
    val isConfigured: Boolean get() = BuildConfig.DEEPSEEK_API_KEY.isNotBlank()

    /** True only when Android has verified the connection actually reaches the internet (not just a Wi-Fi link). */
    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    // ------------------------------------------------------------------ Ask

    /** Returns the model's reply, or a failure the caller should treat as "use the offline answer". */
    suspend fun ask(question: String, lang: Lang, grounding: Info?): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            history += Turn("user", question)
            val messages = JSONArray().put(msg("system", system(lang, grounding)))
            history.takeLast(MAX_TURNS).forEach { messages.put(msg(it.role, it.content)) }
            val reply = clean(complete(messages, maxTokens = 450, temperature = 0.3))
            check(reply.isNotBlank()) { "empty reply" }
            history += Turn("assistant", reply)
            reply
        }.onFailure {
            // do not keep a question that never got an answer
            if (history.lastOrNull()?.role == "user") history.removeAt(history.lastIndex)
        }
    }

    fun resetConversation() = history.clear()

    // ------------------------------------------------------------------ Result

    fun cachedInsight(classId: String, lang: Lang): String? = cache.get(classId, lang)

    /**
     * Extra, practical advice for the diagnosed problem under four fixed headings, in the farmer's language. When the
     * saved guide has an entry it is passed along so the answer stays consistent with it; when it has none (a problem
     * the guide does not cover yet) the model is asked to write complete advice. The result is cached.
     */
    suspend fun insight(classId: String, info: Info, lang: Lang): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val crop = if (info.known) info.cropName.en else ClassId.crop(classId).replace('_', ' ')
            val problem = if (info.known) info.name.en else ClassId.disease(classId).replace('_', ' ')
            val healthy = ClassId.isHealthy(classId)
            val headings = if (lang == Lang.HI)
                listOf("1. आज करें", "2. सस्ता या जैविक उपाय", "3. आगे से बचाव", "4. सुरक्षा और खर्च")
            else listOf("1. Do today", "2. Low-cost or organic option", "3. Prevent it next time", "4. Safety and cost")
            val ask = if (healthy)
                "A phone scan found a healthy $crop leaf. Give 3 short tips to keep the crop healthy, each line starting with \"-\"."
            else
                "A phone scan found \"$problem\" on a $crop leaf. Give guidance under exactly these 4 headings, written exactly " +
                    "as shown, each followed by 2 or 3 short lines that start with \"-\":\n" + headings.joinToString("\n")
            val messages = JSONArray()
                .put(msg("system", system(lang, if (info.known) info else null)))
                .put(msg("user", ask))
            val text = clean(complete(messages, maxTokens = 520, temperature = 0.3))
            check(text.isNotBlank()) { "empty reply" }
            cache.put(classId, lang, text)
            text
        }
    }

    // ------------------------------------------------------------------ plumbing

    private fun complete(messages: JSONArray, maxTokens: Int, temperature: Double): String {
        val body = JSONObject()
            .put("model", MODEL)
            .put("messages", messages)
            .put("temperature", temperature)
            .put("max_tokens", maxTokens)
        val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer ${BuildConfig.DEEPSEEK_API_KEY}")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            if (code !in 200..299) error("HTTP $code")
            val text = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            return JSONObject(text).getJSONArray("choices").getJSONObject(0)
                .getJSONObject("message").getString("content").trim()
        } finally {
            conn.disconnect()
        }
    }

    /** The model is told not to use markdown, but strip the usual symbols in case it does. */
    private fun clean(s: String) = s.replace(Regex("[*#`]+"), "").replace(Regex("\n{3,}"), "\n\n").trim()

    private fun system(lang: Lang, g: Info?): String {
        val language = if (lang == Lang.HI) "simple Hindi in Devanagari script" else "plain, simple English"
        val sb = StringBuilder()
        sb.append("You are CropDoctor's assistant for small farmers in India. ")
        sb.append("Answer in ").append(language).append(", as short plain sentences. ")
        sb.append("Do not use markdown symbols. Start with the most useful action; low-cost and organic options come before chemicals. ")
        sb.append("Only discuss crops, plant health, spraying and farm safety; politely decline anything else. ")
        sb.append("For any chemical, say to wear gloves and a mask and to follow the dose on the product label. ")
        sb.append("Do not state a dose unless it appears in the reference below, or the reference is missing and you are sure of it. ")
        sb.append("If you are unsure, say so and suggest the nearest Krishi Vigyan Kendra (KVK).")
        if (g != null) {
            sb.append("\n\nReference from the app's own guide for ${g.cropName.en} - ${g.name.en}. Prefer it over your own memory:\n")
            sb.append("Cause: ").append(g.cause.en).append('\n')
            sb.append("What to do: ").append(g.actions.en.joinToString("; ")).append('\n')
            sb.append("Organic: ").append(g.organic.en.joinToString("; ")).append('\n')
            sb.append("Chemical: ").append(g.chemical.en.joinToString("; ")).append('\n')
            sb.append("Prevention: ").append(g.prevent.en.joinToString("; "))
        }
        return sb.toString()
    }

    private fun msg(role: String, content: String) = JSONObject().put("role", role).put("content", content)

    private companion object {
        const val ENDPOINT = "https://api.deepseek.com/chat/completions"
        const val MODEL = "deepseek-chat"
        const val MAX_TURNS = 8
    }
}
