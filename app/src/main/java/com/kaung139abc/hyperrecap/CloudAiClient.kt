package com.kaung139abc.hyperrecap

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object CloudAiClient {
    private const val MAX_RECAP_CHARS = 20_000
    private const val MAX_TRANSLATE_CHARS = 5_000

    /** Multi-pass synthesis keeps long-film drafts coherent instead of returning a raw concatenation. */
    fun generateBurmeseRecap(context: Context, source: String, visualContext: String = "", progress: (String) -> Unit = {}): String {
        val chunks = splitText(source, MAX_RECAP_CHARS)
        if (chunks.isEmpty()) throw IllegalArgumentException("Recap လုပ်ရန် Transcript မရှိပါ။")
        var level = chunks.mapIndexed { index, chunk ->
            progress("ဇာတ်လမ်းအပိုင်း ${index + 1}/${chunks.size} ကို အကျဉ်းချုပ်နေသည်...")
            request(context, "/v1/ai/recap", chunk, visualContext).getString("recap").trim()
        }.filter { it.isNotBlank() }
        if (level.isEmpty()) throw IllegalStateException("AI Recap ရလဒ် မရရှိပါ။")

        var round = 1
        while (level.size > 1 && round <= 8) {
            val merged = level.mapIndexed { index, draft -> "[အပိုင်း ${index + 1}]\n$draft" }.joinToString("\n\n")
            val mergeChunks = splitText(merged, MAX_RECAP_CHARS)
            val next = mergeChunks.mapIndexed { index, chunk ->
                progress("ဇာတ်လမ်းအပိုင်းများကို ပေါင်းစည်းနေသည် — အဆင့် $round (${index + 1}/${mergeChunks.size})")
                request(context, "/v1/ai/recap", chunk, visualContext).getString("recap").trim()
            }.filter { it.isNotBlank() }
            if (next.isEmpty()) throw IllegalStateException("AI Recap ပေါင်းစည်းမှု မအောင်မြင်ပါ။")
            if (next.size >= level.size && next.joinToString("").length >= merged.length) return next.joinToString("\n\n")
            level = next
            round++
        }
        return level.joinToString("\n\n")
    }

    fun analyzeVideoFrames(context: Context, frames: List<String>): String {
        if (frames.isEmpty() || frames.size > 2) throw IllegalArgumentException("Frame ၁ မှ ၂ ပုံအထိသာ ပို့နိုင်ပါသည်။")
        val license = LicenseManager.savedKey(context)
        if (license.isBlank()) throw IllegalStateException("License Key ကို အရင် Activate လုပ်ပါ။")
        val payload = JSONObject().put("licenseKey", license).put("deviceId", LicenseManager.deviceId(context)).put("frames", org.json.JSONArray(frames)).toString()
        return postJson("/v1/ai/vision", payload).getString("description")
    }

    fun translateEnglishToMyanmar(context: Context, source: String, progress: (String) -> Unit = {}): String {
        val chunks = splitText(source, MAX_TRANSLATE_CHARS)
        if (chunks.isEmpty()) throw IllegalArgumentException("ဘာသာပြန်ရန် စာသား မရှိပါ။")
        return chunks.mapIndexed { index, chunk ->
            progress("English → Myanmar ဘာသာပြန်နေသည် — ${index + 1}/${chunks.size}")
            request(context, "/v1/ai/translate", chunk).getString("translation")
        }.joinToString("\n\n")
    }

    private fun request(context: Context, path: String, text: String, visualContext: String = ""): JSONObject {
        val license = LicenseManager.savedKey(context)
        if (license.isBlank()) throw IllegalStateException("License Key ကို အရင် Activate လုပ်ပါ။")
        val payload = JSONObject().put("licenseKey", license).put("deviceId", LicenseManager.deviceId(context)).put("text", text).put("visualContext", visualContext).toString()
        return postJson(path, payload)
    }

    private fun postJson(path: String, payload: String): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LicenseManager.SERVER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"; connectTimeout = 30_000; readTimeout = 180_000; doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val json = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
            if (status !in 200..299) {
                val reason = json.optString("error", "HTTP $status")
                throw IllegalStateException(when (reason) {
                    "EXPIRED" -> "License သက်တမ်းကုန်သွားပါပြီ။"
                    "INVALID_OR_REVOKED" -> "License Key မမှန်ပါ သို့မဟုတ် ပိတ်ထားပါသည်။"
                    "DEVICE_NOT_ACTIVATED" -> "ဒီဖုန်းမှာ License မဖွင့်ရသေးပါ။"
                    else -> reason
                })
            }
            return json
        } finally { connection?.disconnect() }
    }

    private fun splitText(text: String, maxChars: Int): List<String> {
        val clean = text.replace("\r", " ").trim()
        if (clean.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var start = 0
        while (start < clean.length) {
            var end = (start + maxChars).coerceAtMost(clean.length)
            if (end < clean.length) {
                val boundary = clean.lastIndexOfAny(charArrayOf('\n', '။', '.', '!', '?', ' '), end - 1)
                if (boundary > start + maxChars / 2) end = boundary + 1
            }
            val part = clean.substring(start, end).trim()
            if (part.isNotBlank()) chunks += part
            start = end.coerceAtLeast(start + 1)
        }
        return chunks
    }
}