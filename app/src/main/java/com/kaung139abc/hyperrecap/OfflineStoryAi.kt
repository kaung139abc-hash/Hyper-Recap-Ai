package com.kaung139abc.hyperrecap

import android.content.Context
import com.llamatik.library.platform.LlamaBridge
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Small multilingual on-device story synthesis model.
 *
 * Qwen3-0.6B is Apache-2.0. The GGUF weights are downloaded once to app-private
 * storage; subsequent recaps can run offline. This is a practical first
 * generative layer, not a claim of cloud-model-level quality.
 */
object OfflineStoryAi {
    private const val MODEL_URL =
        "https://huggingface.co/Qwen/Qwen3-0.6B-GGUF/resolve/main/Qwen3-0.6B-Q4_K_M.gguf"
    private const val MODEL_FILE = "qwen3-0.6b-q4_k_m.gguf"
    private const val MIN_MODEL_BYTES = 100L * 1024L * 1024L
    private const val MIN_FREE_BYTES = 900L * 1024L * 1024L
    private const val CHUNK_CHARS = 2_200

    @Volatile private var modelReady = false
    private val modelLock = Any()

    fun summarize(
        context: Context,
        transcript: String,
        onProgress: (String) -> Unit = {}
    ): String {
        val clean = transcript.trim()
        require(clean.length >= 80) { "ဇာတ်လမ်း Transcript မလုံလောက်ပါ။" }
        val model = ensureModel(context.applicationContext, onProgress)
        ensureRuntime(model, onProgress)

        val chunks = splitTranscript(clean)
        val evidenceSummaries = ArrayList<String>(chunks.size)
        configureGeneration(180)
        chunks.forEachIndexed { index, chunk ->
            onProgress("Offline Story AI — ဇာတ်လမ်းအပိုင်း ${index + 1}/${chunks.size} ကို နားလည်နေသည်...")
            val prompt = chatPrompt(
                system = "You are a careful movie-story analyst. Understand events, characters, motivations, cause and effect, conflicts and turning points. Never invent events. Reply only in natural Burmese. If the source is English, translate its meaning into Burmese. /no_think",
                user = "Analyze this chronological movie transcript segment. Preserve important names and relationships. Return 3 to 5 concise Burmese bullet points describing only events supported by the transcript.\n\nTRANSCRIPT:\n$chunk"
            )
            val result = LlamaBridge.generate(prompt).trim()
            if (result.isNotBlank()) evidenceSummaries += result
        }
        if (evidenceSummaries.isEmpty()) throw IllegalStateException("Offline Story AI မှ အဖြေမရရှိပါ။")

        // Hierarchical reduction keeps a 2+ hour transcript within the model's
        // 4K-token context instead of concatenating every intermediate note.
        var storyNotes = evidenceSummaries
        var reductionRound = 1
        while (storyNotes.sumOf { it.length } > 2_600 && reductionRound <= 4) {
            val groups = groupNotes(storyNotes, 2_300)
            val reduced = ArrayList<String>(groups.size)
            groups.forEachIndexed { index, group ->
                onProgress("Offline Story AI — ရှည်လျားသောဇာတ်လမ်းကို အဆင့်လိုက်ပေါင်းစပ်နေသည် ($reductionRound / $index)...")
                val prompt = chatPrompt(
                    system = "You are a precise Burmese story editor. Merge related events, preserve chronology, characters, motivations and cause-and-effect. Never invent details. /no_think",
                    user = "Condense these chronological scene notes into 4 to 6 concise Burmese bullet points. Keep the major turning points and ending evidence.\n\nNOTES:\n$group"
                )
                val note = LlamaBridge.generate(prompt).trim()
                if (note.isNotBlank()) reduced += note
            }
            if (reduced.isEmpty()) break
            storyNotes = reduced
            reductionRound++
        }

        onProgress("Offline Story AI — ဇာတ်လမ်းအစ၊ အလယ်၊ အဆုံးကို ပေါင်းစပ်ရေးသားနေသည်...")
        configureGeneration(520)
        val finalPrompt = chatPrompt(
            system = "You are a professional Burmese movie recap writer. Reconstruct one coherent story from chronological evidence. Explain the setup, key characters, motivations, major turning points, climax and ending. Keep cause-and-effect clear. Do not add unsupported details. Output fluent natural Burmese narration, not bullet points. /no_think",
            user = "Write a coherent Burmese movie recap using all the following ordered scene summaries. Include the ending and avoid repeating events.\n\nSCENE SUMMARIES:\n" +
                storyNotes.joinToString("\n\n")
        )
        return LlamaBridge.generate(finalPrompt).trim().takeIf { it.length >= 80 }
            ?: throw IllegalStateException("Offline Story AI ၏ နောက်ဆုံး Recap မပြည့်စုံပါ။")
    }

    private fun groupNotes(notes: List<String>, maxChars: Int): List<String> {
        val groups = ArrayList<String>()
        val current = StringBuilder()
        for (note in notes) {
            if (current.isNotEmpty() && current.length + note.length + 2 > maxChars) {
                groups += current.toString()
                current.setLength(0)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(note)
        }
        if (current.isNotEmpty()) groups += current.toString()
        return groups
    }

    private fun ensureRuntime(model: File, onProgress: (String) -> Unit) {
        if (modelReady) return
        synchronized(modelLock) {
            if (modelReady) return
            onProgress("Offline Story AI Model ကို RAM ထဲသို့ တင်နေသည်။ ပထမအကြိမ် အချိန်ယူနိုင်ပါသည်...")
            configureGeneration(180)
            if (!LlamaBridge.initGenerateModel(model.absolutePath)) {
                throw IllegalStateException("Offline Story AI Model ကို ဖွင့်မရပါ။ ဖုန်း RAM/Model ဖိုင်ကို စစ်ဆေးပါ။")
            }
            modelReady = true
        }
    }

    private fun configureGeneration(maxTokens: Int) {
        LlamaBridge.updateGenerateParams(
            temperature = 0.25f,
            maxTokens = maxTokens,
            topP = 0.85f,
            topK = 35,
            repeatPenalty = 1.12f,
            contextLength = 4096,
            numThreads = 3,
            useMmap = true,
            flashAttention = false,
            batchSize = 256,
            gpuLayers = 0
        )
    }

    private fun ensureModel(context: Context, onProgress: (String) -> Unit): File {
        val directory = File(context.filesDir, "ai-models/qwen3-0.6b").apply { mkdirs() }
        val model = File(directory, MODEL_FILE)
        if (model.isFile && model.length() >= MIN_MODEL_BYTES) return model
        if (directory.usableSpace < MIN_FREE_BYTES) {
            throw IllegalStateException("Offline Story AI အတွက် အနည်းဆုံး 900 MB နေရာလွတ် လိုအပ်ပါသည်။")
        }
        val partial = File(directory, "$MODEL_FILE.part")
        partial.delete()
        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Hyper-Recap-AI-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Offline AI Model Download မအောင်မြင်ပါ (HTTP ${connection.responseCode})")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(partial).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (total > 0L) {
                            onProgress("Offline AI Model Download — ${(received * 100 / total).coerceIn(0, 100)}%")
                        } else {
                            onProgress("Offline AI Model Download — ${received / (1024 * 1024)} MB")
                        }
                    }
                    output.fd.sync()
                }
            }
            if (partial.length() < MIN_MODEL_BYTES || (total > 0L && partial.length() != total)) {
                throw IllegalStateException("Offline AI Model Download မပြည့်စုံပါ။")
            }
            val magic = ByteArray(4)
            FileInputStream(partial).use { input ->
                if (input.read(magic) != 4 || String(magic, Charsets.US_ASCII) != "GGUF") {
                    throw IllegalStateException("Download လုပ်ထားသော Model သည် GGUF ဖိုင်မဟုတ်ပါ။")
                }
            }
            if (model.exists()) model.delete()
            if (!partial.renameTo(model)) throw IllegalStateException("Offline AI Model ကို သိမ်းမရပါ။")
            return model
        } catch (error: Exception) {
            partial.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun splitTranscript(text: String): List<String> {
        val sentences = text.replace("\r", " ")
            .split(Regex("(?<=[။.!?])\\s*|\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val chunks = ArrayList<String>()
        val current = StringBuilder()
        for (sentence in sentences) {
            if (sentence.length > CHUNK_CHARS) {
                if (current.isNotEmpty()) {
                    chunks += current.toString()
                    current.setLength(0)
                }
                sentence.chunked(CHUNK_CHARS).forEach { chunks += it }
            } else {
                if (current.isNotEmpty() && current.length + sentence.length + 1 > CHUNK_CHARS) {
                    chunks += current.toString()
                    current.setLength(0)
                }
                if (current.isNotEmpty()) current.append('\n')
                current.append(sentence)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }

    private fun chatPrompt(system: String, user: String): String =
        "<|im_start|>system\n$system<|im_end|>\n" +
            "<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n"
}
