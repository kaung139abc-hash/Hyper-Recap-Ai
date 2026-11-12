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

    data class MyanmarVoice(val id: String, val name: String, val gender: String)

    /** Fetch only voices explicitly marked as supporting Burmese by the commercial TTS provider. */
    fun fetchCommercialMyanmarVoices(context: Context): List<MyanmarVoice> {
        val license = LicenseManager.savedKey(context)
        if (license.isBlank()) throw IllegalStateException("License Key ကို အရင် Activate လုပ်ပါ။")
        val payload = JSONObject().put("licenseKey", license)
            .put("deviceId", LicenseManager.deviceId(context)).toString()
        val response = postJson("/v1/ai/voices", payload)
        val array = response.optJSONArray("voices") ?: return emptyList()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id")
            if (id.isBlank()) null else MyanmarVoice(id, item.optString("name", "Myanmar Voice"), item.optString("gender", "Unknown"))
        }
    }

    /** Download licensed commercial Burmese neural narration as an MP3 audio track. */
    fun synthesizeCommercialMyanmarVoice(
        context: Context,
        text: String,
        voiceId: String,
        output: java.io.File,
        progress: (String) -> Unit = {}
    ) {
        require(text.trim().length >= 5 && text.length <= 4500) { "Neural Voice စာသားသည် ၅ မှ ၄၅၀၀ လုံးအတွင်း ဖြစ်ရမည်။" }
        require(voiceId.matches(Regex("[A-Za-z0-9_-]{5,100}"))) { "Voice ID မမှန်ပါ။" }
        val license = LicenseManager.savedKey(context)
        if (license.isBlank()) throw IllegalStateException("License Key ကို အရင် Activate လုပ်ပါ။")
        val payload = JSONObject().put("licenseKey", license)
            .put("deviceId", LicenseManager.deviceId(context))
            .put("text", text).put("voiceId", voiceId).toString()
        var connection: HttpURLConnection? = null
        try {
            progress("Commercial မြန်မာ Neural Voice ဖြင့် အသံဖန်တီးနေပါသည်...")
            connection = (URL(LicenseManager.SERVER_URL.trimEnd('/') + "/v1/ai/tts").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 30_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val stream = connection.errorStream
                val detail = stream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw IllegalStateException(detail.take(350))
            }
            output.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                output.outputStream().use { target ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        total += count
                    }
                    target.flush()
                    if (total < 1024L) throw IllegalStateException("Neural Voice MP3 ဖိုင် မပြည့်စုံပါ။")
                }
            }
            progress("Commercial မြန်မာ Neural Voice အသင့်ဖြစ်ပါပြီ။")
        } catch (error: Exception) {
            output.delete()
            throw error
        } finally {
            connection?.disconnect()
        }
    }

    fun analyzeVideoFrames(context: Context, frames: List<String>, frameTimesMs: List<Long> = emptyList()): String {
        if (frames.isEmpty() || frames.size > 2) throw IllegalArgumentException("Frame ၁ မှ ၂ ပုံအထိသာ ပို့နိုင်ပါသည်။")
        if (frameTimesMs.isNotEmpty() && frameTimesMs.size != frames.size) throw IllegalArgumentException("Frame အချိန်နှင့် ပုံအရေအတွက် မကိုက်ညီပါ။")
        val license = LicenseManager.savedKey(context)
        if (license.isBlank()) throw IllegalStateException("License Key ကို အရင် Activate လုပ်ပါ။")
        val payload = JSONObject().put("licenseKey", license).put("deviceId", LicenseManager.deviceId(context)).put("frames", org.json.JSONArray(frames)).put("frameTimesMs", org.json.JSONArray(frameTimesMs)).toString()
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

    /**
     * Cloudflare AI can briefly return 429/5xx while its inference capacity is busy.
     * Retry only transient failures; never retry license/authentication or bad-input errors.
     */
    private fun postJson(path: String, payload: String): JSONObject {
        val url = URL(LicenseManager.SERVER_URL.trimEnd('/') + path)
        var lastError: Exception? = null
        for (attempt in 0..2) {
            var connection: HttpURLConnection? = null
            try {
                connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 30_000
                    readTimeout = 180_000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                connection.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val json = JSONObject(stream?.bufferedReader()?.use { it.readText() } ?: "{}")
                if (status in 200..299) return json

                val reason = json.optString("error", "HTTP $status")
                if (status == 429 || status in 500..599) {
                    lastError = IllegalStateException(reason)
                    if (attempt < 2) {
                        val retryAfter = connection.getHeaderField("Retry-After")?.toLongOrNull()
                        val delayMs = (retryAfter?.times(1000L) ?: (1_000L shl attempt)).coerceIn(500L, 8_000L)
                        Thread.sleep(delayMs)
                        continue
                    }
                } else {
                    throw IllegalStateException(when (reason) {
                        "EXPIRED" -> "License သက်တမ်းကုန်သွားပါပြီ။"
                        "INVALID_OR_REVOKED" -> "License Key မမှန်ပါ သို့မဟုတ် ပိတ်ထားပါသည်။"
                        "DEVICE_NOT_ACTIVATED" -> "ဒီဖုန်းမှာ License မဖွင့်ရသေးပါ။"
                        else -> reason
                    })
                }
            } catch (e: java.io.IOException) {
                lastError = e
                if (attempt < 2) Thread.sleep(1_000L shl attempt)
            } finally {
                connection?.disconnect()
            }
        }
        throw IllegalStateException(
            "AI Server ကို ဆက်သွယ်မရသေးပါ။ ခဏစောင့်ပြီး ပြန်ကြိုးစားပါ။",
            lastError
        )
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
        // The license server rejects recap/translation inputs shorter than 30
        // characters. Avoid leaving a tiny trailing fragment as a separate request.
        if (chunks.size > 1 && chunks.last().length < 30) {
            val tail = chunks.removeAt(chunks.lastIndex)
            chunks[chunks.lastIndex] = chunks.last() + "\n" + tail
        }
        return chunks
    }
}