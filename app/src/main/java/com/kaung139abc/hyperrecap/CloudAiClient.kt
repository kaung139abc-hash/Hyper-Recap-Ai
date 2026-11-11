package com.kaung139abc.hyperrecap

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional online AI features routed through the user's own Cloudflare Worker.
 * Local Whisper and extractive recap continue to work without this service.
 */
object CloudAiClient {
    private const val MAX_RECAP_CHARS = 20_000
    private const val MAX_TRANSLATE_CHARS = 5_000

    fun generateBurmeseRecap(context: Context, source: String, progress: (String) -> Unit = {}): String {
        val chunks = splitText(source, MAX_RECAP_CHARS)
        if (chunks.isEmpty()) throw IllegalArgumentException("Recap လုပ်ရန် Transcript မရှိပါ။")
        val drafts = chunks.mapIndexed { index, chunk ->
            progress("Cloud AI Recap ဖန်တီးနေသည် — ${index + 1}/${chunks.size}")
            request(context, "/v1/ai/recap", chunk).getString("recap")
        }
        if (drafts.size == 1) return drafts.first()
        val combined = drafts.joinToString("\n\n")
        if (combined.length <= MAX_RECAP_CHARS) {
            progress("Recap အပိုင်းများကို နောက်ဆုံးတစ်ကြိမ် ပေါင်းစည်းနေသည်...")
            return request(context, "/v1/ai/recap", combined).getString("recap")
        }
        return combined
    }

    fun translateEnglishToMyanmar(context: Context, source: String, progress: (String) -> Unit = {}): String {
        val chunks = splitText(source, MAX_TRANSLATE_CHARS)
        if (chunks.isEmpty()) throw IllegalArgumentException("ဘာသာပြန်ရန် စာသား မရှိပါ။")
        return chunks.mapIndexed { index, chunk ->
            progress("English → Myanmar ဘာသာပြန်နေသည် — ${index + 1}/${chunks.size}")
            request(context, "/v1/ai/translate", chunk).getString("translation")
        }.joinToString("\n\n")
    }

    private fun request(context: Context, path: String, text: String): JSONObject {
        val license = LicenseManager.savedKey(context)
        if (license.isBlank()) throw IllegalStateException("License Key ကို အရင် Activate လုပ်ပါ။")
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(LicenseManager.SERVER_URL.trimEnd('/') + path).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 120_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            val payload = JSONObject()
                .put("licenseKey", license)
                .put("deviceId", LicenseManager.deviceId(context))
                .put("text", text)
                .toString()
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader()?.use { it.readText() } ?: "{}"
            val json = JSONObject(raw)
            if (status !in 200..299) {
                val reason = json.optString("error", "HTTP $status")
                throw IllegalStateException(when(reason) {
                    "EXPIRED" -> "License သက်တမ်းကုန်သွားပါပြီ။"
                    "INVALID_OR_REVOKED" -> "License Key မမှန်ပါ သို့မဟုတ် ပိတ်ထားပါသည်။"
                    "DEVICE_NOT_ACTIVATED" -> "ဒီဖုန်းမှာ License မဖွင့်ရသေးပါ။"
                    else -> reason
                })
            }
            return json
        } finally {
            connection?.disconnect()
        }
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
