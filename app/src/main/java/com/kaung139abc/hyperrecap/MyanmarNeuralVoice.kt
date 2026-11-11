package com.kaung139abc.hyperrecap

import android.content.Context
import com.k2fsa.sherpa.onnx.GenerationConfig
import com.k2fsa.sherpa.onnx.OfflineTts
import com.k2fsa.sherpa.onnx.OfflineTtsConfig
import com.k2fsa.sherpa.onnx.OfflineTtsModelConfig
import com.k2fsa.sherpa.onnx.OfflineTtsVitsModelConfig
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL

/**
 * Optional offline Burmese neural voice based on Meta MMS-TTS (mya) converted to
 * sherpa-onnx VITS. The upstream MMS model is CC-BY-NC 4.0 (non-commercial use).
 */
object MyanmarNeuralVoice {
    private const val MODEL_ROOT = "https://huggingface.co/willwade/mms-tts-multilingual-models-onnx/resolve/main/mya/"
    private const val SAMPLE_RATE_FALLBACK = 22_050

    fun synthesize(
        context: Context,
        text: String,
        output: File,
        onProgress: (String) -> Unit = {}
    ) {
        val modelDir = ensureModel(context, onProgress)
        val config = OfflineTtsConfig(
            model = OfflineTtsModelConfig(
                vits = OfflineTtsVitsModelConfig(
                    model = File(modelDir, "model.onnx").absolutePath,
                    tokens = File(modelDir, "tokens.txt").absolutePath
                ),
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        val tts = OfflineTts(config = config)
        try {
            val sampleRate = tts.sampleRate().takeIf { it > 0 } ?: SAMPLE_RATE_FALLBACK
            val chunks = splitText(text, 180)
            if (chunks.isEmpty()) throw IllegalArgumentException("ဖတ်ရန် Recap စာသား မရှိပါ။")
            RandomAccessFile(output, "rw").use { wav ->
                wav.setLength(0)
                wav.write(ByteArray(44))
                var dataBytes = 0L
                chunks.forEachIndexed { index, chunk ->
                    val codepoints = chunk.codePoints().toArray()
                    val generated = tts.generateWithConfig(
                        chunk,
                        GenerationConfig(phonemeCodepoints = arrayOf(codepoints))
                    )
                    val samples = generated.samples
                    val bytes = ByteArray(samples.size * 2)
                    for (i in samples.indices) {
                        val pcm = (samples[i].coerceIn(-1f, 1f) * 32767f).toInt()
                        bytes[i * 2] = (pcm and 0xff).toByte()
                        bytes[i * 2 + 1] = ((pcm ushr 8) and 0xff).toByte()
                    }
                    wav.seek(44L + dataBytes)
                    wav.write(bytes)
                    dataBytes += bytes.size
                    // Add a short natural pause between sentences/chunks.
                    if (index < chunks.lastIndex) {
                        val silenceSamples = sampleRate / 6
                        val silenceBytes = ByteArray(silenceSamples * 2)
                        wav.seek(44L + dataBytes)
                        wav.write(silenceBytes)
                        dataBytes += silenceBytes.size
                    }
                    onProgress("မြန်မာ Neural Voice ဖန်တီးနေသည် — ${index + 1}/${chunks.size}")
                }
                if (dataBytes > 0xffffffffL - 36L) {
                    throw IllegalStateException("Neural WAV ဖိုင် အလွန်ကြီးနေပါသည်။")
                }
                wav.seek(0)
                wav.writeBytes("RIFF")
                writeIntLe(wav, (36L + dataBytes).toInt())
                wav.writeBytes("WAVE")
                wav.writeBytes("fmt ")
                writeIntLe(wav, 16)
                writeShortLe(wav, 1)
                writeShortLe(wav, 1)
                writeIntLe(wav, sampleRate)
                writeIntLe(wav, sampleRate * 2)
                writeShortLe(wav, 2)
                writeShortLe(wav, 16)
                wav.writeBytes("data")
                writeIntLe(wav, dataBytes.toInt())
            }
            onProgress("မြန်မာ Neural Voice WAV ဖန်တီးပြီးပါပြီ။")
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            tts.release()
        }
    }

    private fun ensureModel(context: Context, onProgress: (String) -> Unit): File {
        val dir = File(context.filesDir, "tts-models/mms-mya").apply { mkdirs() }
        val model = File(dir, "model.onnx")
        val tokens = File(dir, "tokens.txt")
        if (model.isFile && model.length() > 1_000_000L && tokens.isFile && tokens.length() > 100L) return dir
        if (dir.usableSpace < 250L * 1024L * 1024L) {
            throw IllegalStateException("မြန်မာ Neural Voice အတွက် 250 MB နေရာလွတ် လိုအပ်ပါသည်။")
        }
        download("$MODEL_ROOT/model.onnx", File(dir, "model.onnx.part"), "model.onnx", onProgress)
        download("$MODEL_ROOT/tokens.txt", File(dir, "tokens.txt.part"), "tokens.txt", onProgress)
        File(dir, "model.onnx.part").renameTo(model)
        File(dir, "tokens.txt.part").renameTo(tokens)
        if (!model.isFile || model.length() <= 1_000_000L || !tokens.isFile || tokens.length() <= 100L) {
            model.delete()
            tokens.delete()
            throw IllegalStateException("မြန်မာ Neural Voice Model ဖိုင် မပြည့်စုံပါ။")
        }
        return dir
    }

    private fun download(url: String, target: File, label: String, onProgress: (String) -> Unit) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Hyper-Recap-AI-Android")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("$label Download မအောင်မြင်ပါ (HTTP ${connection.responseCode})")
            }
            val total = connection.contentLengthLong
            connection.inputStream.use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(128 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (total > 0) onProgress("$label Download — ${(received * 100 / total).coerceIn(0, 100)}%")
                        else onProgress("$label Download — ${received / (1024 * 1024)} MB")
                    }
                }
            }
        } catch (e: Exception) {
            target.delete()
            throw e
        } finally {
            connection.disconnect()
        }
    }

    private fun splitText(text: String, maxChars: Int): List<String> {
        val sentences = text.replace("\r", " ")
            .split(Regex("(?<=[။.!?])\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length > maxChars) {
                result += current.toString()
                current = StringBuilder()
            }
            if (sentence.length <= maxChars) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            } else {
                if (current.isNotEmpty()) {
                    result += current.toString()
                    current = StringBuilder()
                }
                sentence.chunked(maxChars).forEach { result += it }
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun writeIntLe(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value ushr 8) and 0xff)
        file.write((value ushr 16) and 0xff)
        file.write((value ushr 24) and 0xff)
    }

    private fun writeShortLe(file: RandomAccessFile, value: Int) {
        file.write(value and 0xff)
        file.write((value ushr 8) and 0xff)
    }
}
