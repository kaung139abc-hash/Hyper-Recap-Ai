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
    private const val MAX_MODEL_BYTES = 800L * 1024L * 1024L
    private const val MIN_FREE_BYTES = 900L * 1024L * 1024L
    private const val CHUNK_CHARS = 2_200

    @Volatile private var modelReady = false
    private val modelLock = Any()

    @Synchronized
    fun summarize(
        context: Context,
        transcript: String,
        onProgress: (String) -> Unit = {}
    ): String {
        val clean = transcript.trim()
        require(clean.length >= 80) { "ဇာတ်လမ်း Transcript မလုံလောက်ပါ။" }
        val model = ensureModel(context.applicationContext, onProgress)
        ensureRuntime(model, context.applicationContext, onProgress)

        try {
            val chunks = splitTranscript(clean)
            if (chunks.isEmpty()) throw IllegalStateException("Transcript ကို အပိုင်းခွဲမရပါ။")
            // A two-hour movie can create thousands of small transcript fragments.
            // Keep a visible, bounded progress counter and check cancellation between
            // inference calls so Android lifecycle cancellation can stop long jobs.
            val evidenceSummaries = ArrayList<String>(chunks.size)
            configureGeneration(180)
            chunks.forEachIndexed { index, chunk ->
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Offline Story AI လုပ်ဆောင်မှုကို ရပ်ဆိုင်းလိုက်ပါပြီ။")
                }
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
                if (groups.size >= storyNotes.size) {
                    throw IllegalStateException("ဇာတ်လမ်းအချက်အလက်များကို Model context အတွင်း အကျဉ်းချုပ်မရပါ။ Transcript ကို အပိုင်းခွဲ၍ Recap လုပ်ပါ။")
                }
                val reduced = ArrayList<String>(groups.size)
                groups.forEachIndexed { index, group ->
                    if (Thread.currentThread().isInterrupted) {
                        throw InterruptedException("Offline Story AI အဆင့်လိုက်ပေါင်းစပ်မှုကို ရပ်ဆိုင်းလိုက်ပါပြီ။")
                    }
                    onProgress("Offline Story AI — ရှည်လျားသောဇာတ်လမ်းကို အဆင့်လိုက်ပေါင်းစပ်နေသည် ($reductionRound / ${index + 1})...")
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
    
            if (storyNotes.sumOf { it.length } > 2_600) {
                throw IllegalStateException("ဇာတ်လမ်းအကျဉ်းချုပ်သည် Offline AI context ထက် ရှည်နေပါသည်။ Recap ကို အပိုင်းခွဲပြီး လုပ်ဆောင်ပါ။")
            }
            if (Thread.currentThread().isInterrupted) {
                throw InterruptedException("Offline Story AI နောက်ဆုံးရေးသားမှုကို ရပ်ဆိုင်းလိုက်ပါပြီ။")
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
        } finally {
            synchronized(modelLock) {
                if (modelReady) {
                    try {
                        LlamaBridge.shutdown()
                    } finally {
                        modelReady = false
                    }
                }
            }
        }
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

    private fun ensureRuntime(model: File, context: Context, onProgress: (String) -> Unit) {
        if (modelReady) return
        synchronized(modelLock) {
            if (modelReady) return
            // Check current free RAM after Whisper processing; do not risk an app crash
            // while mapping the local GGUF model on a memory-constrained device.
            val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
            val memory = android.app.ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memory)
            val minimumAvailableRam = 480L * 1024L * 1024L
            if (memory.lowMemory || memory.availMem < minimumAvailableRam) {
                throw IllegalStateException(
                    "Offline Story AI အတွက် လက်ရှိ RAM မလုံလောက်ပါ။ App မပိတ်သွားစေရန် Model မတင်သေးပါ။ " +
                        "အခြား App များပိတ်ပြီး ပြန်စမ်းပါ။ (လွတ်နေသော RAM " +
                        (memory.availMem / (1024 * 1024)).toString() + " MB)"
                )
            }
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
        if (isValidModelFile(model)) return model
        if (model.exists() && !model.delete()) {
            throw IllegalStateException("ပျက်စီးနေသော Offline AI Model ဖိုင်ကို ဖယ်ရှားမရပါ။")
        }
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
                        received += count
                        if (received > MAX_MODEL_BYTES || (total > 0L && total > MAX_MODEL_BYTES)) {
                            throw IllegalStateException("Offline AI Model ဖိုင်သည် 800 MB ကန့်သတ်ချက်ထက် ကြီးနေပါသည်။")
                        }
                        output.write(buffer, 0, count)
                        if (total > 0L) {
                            onProgress("Offline AI Model Download — ${(received * 100 / total).coerceIn(0, 100)}%")
                        } else {
                            onProgress("Offline AI Model Download — ${received / (1024 * 1024)} MB")
                        }
                    }
                    output.fd.sync()
                }
            }
            if (partial.length() < MIN_MODEL_BYTES || partial.length() > MAX_MODEL_BYTES ||
                (total > 0L && partial.length() != total)) {
                throw IllegalStateException("Offline AI Model Download မပြည့်စုံပါ သို့မဟုတ် အရွယ်အစားမမှန်ပါ။")
            }
            if (!isValidModelFile(partial)) {
                throw IllegalStateException("Download လုပ်ထားသော Model သည် မှန်ကန်သော GGUF ဖိုင်မဟုတ်ပါ။")
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

    private fun isValidModelFile(file: File): Boolean {
        if (!file.isFile || file.length() !in MIN_MODEL_BYTES..MAX_MODEL_BYTES) return false
        return try {
            FileInputStream(file).use { input ->
                val magic = ByteArray(4)
                input.read(magic) == 4 && String(magic, Charsets.US_ASCII) == "GGUF"
            }
        } catch (_: Exception) {
            false
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
                chunks += splitLongSentenceAtSafeBoundaries(sentence)
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

    /**
     * Split unusually long Burmese transcript runs without cutting a Unicode
     * grapheme (base consonant + medials/diacritics). Prefer whitespace near the
     * end of each chunk, but still make progress for unspaced text.
     */
    private fun splitLongSentenceAtSafeBoundaries(sentence: String): List<String> {
        val iterator = java.text.BreakIterator.getCharacterInstance(java.util.Locale("my"))
        iterator.setText(sentence)
        val clusters = ArrayList<String>()
        var start = iterator.first()
        var end = iterator.next()
        while (end != java.text.BreakIterator.DONE) {
            clusters += sentence.substring(start, end)
            start = end
            end = iterator.next()
        }

        val result = ArrayList<String>()
        var offset = 0
        while (offset < clusters.size) {
            val hardEnd = (offset + CHUNK_CHARS).coerceAtMost(clusters.size)
            var cut = hardEnd
            if (hardEnd < clusters.size) {
                val preferredFloor = offset + (CHUNK_CHARS * 2 / 3)
                val whitespace = (hardEnd - 1 downTo preferredFloor)
                    .firstOrNull { clusters[it].isBlank() }
                if (whitespace != null) cut = whitespace + 1
            }
            if (cut <= offset) cut = hardEnd
            val part = clusters.subList(offset, cut).joinToString("").trim()
            if (part.isNotBlank()) result += part
            offset = cut
        }
        return result
    }

    private fun chatPrompt(system: String, user: String): String =
        "<|im_start|>system\n$system<|im_end|>\n" +
            "<|im_start|>user\n$user<|im_end|>\n" +
            "<|im_start|>assistant\n"
}
