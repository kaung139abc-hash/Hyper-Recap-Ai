package com.kaung139abc.hyperrecap

import android.content.Context
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.floor

/**
 * Multilingual Whisper Base, quantized to int8, running locally through sherpa-onnx.
 * The model is downloaded once (about 208 MB) and reused offline.
 * Audio is fed in short windows so a long movie is never loaded into RAM at once.
 */
object WhisperTranscriber {
    private const val MODEL_ARCHIVE =
        "https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-base.tar.bz2"
    private const val MODEL_DIR = "sherpa-onnx-whisper-base"
    private const val SAMPLE_RATE = 16_000
    private const val WINDOW_SECONDS = 28
    private const val OVERLAP_SECONDS = 1

    data class Segment(val startMs: Long, val endMs: Long, val text: String)

    fun transcribeVideoAudio(
        context: Context,
        wavFile: File,
        onProgress: (String) -> Unit = {}
    ): List<Segment> {
        val model = ensureModel(context, onProgress)
        onProgress("Whisper Base ကို စတင်နေပါသည်...")
        val config = OfflineRecognizerConfig(
            modelConfig = OfflineModelConfig(
                whisper = OfflineWhisperModelConfig(
                    encoder = File(model, "base-encoder.int8.onnx").absolutePath,
                    decoder = File(model, "base-decoder.int8.onnx").absolutePath,
                    language = "auto",
                    task = "transcribe",
                    enableSegmentTimestamps = true
                ),
                tokens = File(model, "base-tokens.txt").absolutePath,
                modelType = "whisper",
                numThreads = 2,
                debug = false,
                provider = "cpu"
            )
        )
        val recognizer = OfflineRecognizer(config = config)
        try {
            return transcribeWavInWindows(wavFile, recognizer, onProgress)
        } finally {
            recognizer.release()
        }
    }

    private fun ensureModel(context: Context, onProgress: (String) -> Unit): File {
        val root = File(context.filesDir, "asr-models").apply { mkdirs() }
        val modelDir = File(root, MODEL_DIR)
        val encoder = File(modelDir, "base-encoder.int8.onnx")
        val decoder = File(modelDir, "base-decoder.int8.onnx")
        val tokens = File(modelDir, "base-tokens.txt")
        if (encoder.isFile && decoder.isFile && tokens.isFile) return modelDir

        if (root.usableSpace < 650L * 1024L * 1024L) {
            throw IllegalStateException("Whisper Model အတွက် အနည်းဆုံး 650 MB နေရာလွတ် လိုအပ်ပါသည်။")
        }
        val archive = File(root, "whisper-base.tar.bz2.part")
        val connection = (URL(MODEL_ARCHIVE).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 60_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Hyper-Recap-AI-Android")
        }
        try {
            connection.connect()
            val total = connection.contentLengthLong
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException("Whisper Model Download မအောင်မြင်ပါ (HTTP ${connection.responseCode})")
            }
            connection.inputStream.use { input ->
                FileOutputStream(archive).use { output ->
                    val buffer = ByteArray(256 * 1024)
                    var received = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        received += count
                        if (total > 0) onProgress("Whisper Model Download — ${(received * 100 / total).coerceIn(0, 100)}%")
                        else onProgress("Whisper Model Download — ${received / (1024 * 1024)} MB")
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        if (!archive.isFile || archive.length() < 50L * 1024L * 1024L) {
            archive.delete()
            throw IllegalStateException("Whisper Model ဖိုင် မပြည့်စုံပါ။ Internet ပြန်စစ်ပြီး ထပ်လုပ်ပါ။")
        }

        val staging = File(root, "$MODEL_DIR.installing")
        staging.deleteRecursively()
        staging.mkdirs()
        try {
            onProgress("Whisper Model ကို ဖြည်နေပါသည်...")
            TarArchiveInputStream(
                BZip2CompressorInputStream(BufferedInputStream(FileInputStream(archive)))
            ).use { tar ->
                while (true) {
                    val entry = tar.nextTarEntry ?: break
                    val output = File(staging, entry.name)
                    val canonicalRoot = staging.canonicalPath + File.separator
                    if (!output.canonicalPath.startsWith(canonicalRoot)) {
                        throw IllegalStateException("Model archive ထဲတွင် မမှန်ကန်သော path ပါဝင်နေပါသည်။")
                    }
                    if (entry.isDirectory) {
                        output.mkdirs()
                    } else if (entry.isFile) {
                        output.parentFile?.mkdirs()
                        FileOutputStream(output).use { tar.copyTo(it) }
                    }
                }
            }
            val actualDir = File(staging, MODEL_DIR)
            val source = if (actualDir.isDirectory) actualDir else staging
            val required = listOf("base-encoder.int8.onnx", "base-decoder.int8.onnx", "base-tokens.txt")
            if (required.any { !File(source, it).isFile }) {
                throw IllegalStateException("Whisper Model ထဲတွင် လိုအပ်သော ONNX ဖိုင်များ မတွေ့ပါ။")
            }
            modelDir.deleteRecursively()
            if (source == staging) {
                if (!staging.renameTo(modelDir)) throw IllegalStateException("Model folder ပြောင်းမရပါ။")
            } else {
                if (!actualDir.renameTo(modelDir)) throw IllegalStateException("Model folder ပြောင်းမရပါ။")
            }
            onProgress("Whisper Model အသင့်ဖြစ်ပါပြီ။ နောက်တစ်ကြိမ်တွင် ထပ် Download လုပ်ရန် မလိုပါ။")
            return modelDir
        } catch (e: Exception) {
            modelDir.deleteRecursively()
            throw e
        } finally {
            archive.delete()
            staging.deleteRecursively()
        }
    }

    private data class WavInfo(
        val sampleRate: Int,
        val channels: Int,
        val dataOffset: Long,
        val dataBytes: Long
    )

    private fun transcribeWavInWindows(
        wav: File,
        recognizer: OfflineRecognizer,
        onProgress: (String) -> Unit
    ): List<Segment> {
        val info = readWavInfo(wav)
        val bytesPerFrame = info.channels * 2
        val totalFrames = info.dataBytes / bytesPerFrame
        val durationMs = totalFrames * 1000L / info.sampleRate
        if (durationMs <= 0L) throw IllegalStateException("အသံဒေတာ မရှိပါ။")

        val segments = mutableListOf<Segment>()
        val windowMs = WINDOW_SECONDS * 1000L
        val overlapMs = OVERLAP_SECONDS * 1000L
        var startMs = 0L
        var index = 0
        RandomAccessFile(wav, "r").use { file ->
            while (startMs < durationMs) {
                val endMs = (startMs + windowMs).coerceAtMost(durationMs)
                val firstFrame = startMs * info.sampleRate / 1000L
                val lastFrame = endMs * info.sampleRate / 1000L
                val frameCount = (lastFrame - firstFrame).coerceAtLeast(0L)
                if (frameCount == 0L) break
                val pcmBytes = ByteArray((frameCount * bytesPerFrame).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                file.seek(info.dataOffset + firstFrame * bytesPerFrame)
                file.readFully(pcmBytes)
                val mono = FloatArray(frameCount.toInt()) { frame ->
                    var sum = 0
                    val base = frame * bytesPerFrame
                    for (channel in 0 until info.channels) {
                        val lo = pcmBytes[base + channel * 2].toInt() and 0xff
                        val hi = pcmBytes[base + channel * 2 + 1].toInt()
                        sum += (hi shl 8) or lo
                    }
                    (sum.toFloat() / info.channels / 32768f).coerceIn(-1f, 1f)
                }
                val samples = resample(mono, info.sampleRate, SAMPLE_RATE)
                val stream = recognizer.createStream()
                val text = try {
                    stream.acceptWaveform(samples, SAMPLE_RATE)
                    recognizer.decode(stream)
                    recognizer.getResult(stream).text.trim()
                } finally {
                    stream.release()
                }
                if (text.isNotBlank()) {
                    val pieces = splitSubtitleText(text, 88)
                    val weights = pieces.map { it.length.coerceAtLeast(1) }
                    val totalWeight = weights.sum().coerceAtLeast(1)
                    val windowDuration = endMs - startMs
                    var cueStart = startMs
                    pieces.forEachIndexed { pieceIndex, piece ->
                        val cueEnd = if (pieceIndex == pieces.lastIndex) endMs else {
                            (cueStart + (windowDuration * weights[pieceIndex] / totalWeight))
                                .coerceAtMost(endMs)
                        }
                        if (cueEnd > cueStart) segments += Segment(cueStart, cueEnd, piece)
                        cueStart = cueEnd
                    }
                }
                index++
                onProgress("Whisper Speech-to-Text — ${(endMs * 100 / durationMs).coerceIn(0, 100)}% (အပိုင်း $index)")
                if (endMs >= durationMs) break
                startMs = (endMs - overlapMs).coerceAtLeast(startMs + 1L)
            }
        }
        return segments
    }

    private fun splitSubtitleText(text: String, maxChars: Int): List<String> {
        val sentences = text.split(Regex("(?<=[။.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        for (sentence in sentences) {
            if (sentence.length <= maxChars) {
                result += sentence
                continue
            }
            val iterator = java.text.BreakIterator.getCharacterInstance(Locale("my"))
            iterator.setText(sentence)
            var part = StringBuilder()
            var start = iterator.first()
            var end = iterator.next()
            while (end != java.text.BreakIterator.DONE) {
                val cluster = sentence.substring(start, end)
                if (part.length + cluster.length > maxChars && part.isNotEmpty()) {
                    result += part.toString().trim()
                    part = StringBuilder()
                }
                part.append(cluster)
                start = end
                end = iterator.next()
            }
            if (part.isNotEmpty()) result += part.toString().trim()
        }
        return result.filter { it.isNotBlank() }
    }

    private fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate) return input
        val outputSize = (input.size.toLong() * targetRate / sourceRate).coerceAtLeast(1L)
        if (outputSize > Int.MAX_VALUE) throw IllegalStateException("Audio window အလွန်ရှည်နေပါသည်။")
        val output = FloatArray(outputSize.toInt())
        val scale = sourceRate.toDouble() / targetRate
        for (i in output.indices) {
            val position = i * scale
            val left = floor(position).toInt().coerceIn(0, input.lastIndex)
            val right = (left + 1).coerceAtMost(input.lastIndex)
            val fraction = (position - left).toFloat()
            output[i] = input[left] * (1f - fraction) + input[right] * fraction
        }
        return output
    }

    private fun readWavInfo(file: File): WavInfo {
        RandomAccessFile(file, "r").use { input ->
            if (readAscii(input, 4) != "RIFF") throw IllegalArgumentException("WAV RIFF header မမှန်ပါ။")
            readIntLe(input)
            if (readAscii(input, 4) != "WAVE") throw IllegalArgumentException("WAV format မမှန်ပါ။")
            var sampleRate = 0
            var channels = 0
            var bits = 0
            var encoding = 0
            var dataOffset = -1L
            var dataBytes = 0L
            while (input.filePointer + 8 <= input.length()) {
                val id = readAscii(input, 4)
                val size = readIntLe(input).toLong() and 0xffffffffL
                val chunkStart = input.filePointer
                when (id) {
                    "fmt " -> {
                        encoding = readShortLe(input)
                        channels = readShortLe(input)
                        sampleRate = readIntLe(input)
                        readIntLe(input)
                        readShortLe(input)
                        bits = readShortLe(input)
                    }
                    "data" -> {
                        dataOffset = chunkStart
                        dataBytes = size
                    }
                }
                input.seek(chunkStart + size + (size and 1L))
                if (dataOffset >= 0 && sampleRate > 0 && channels > 0) break
            }
            if (encoding != 1 || bits != 16 || sampleRate <= 0 || channels !in 1..2 || dataOffset < 0) {
                throw IllegalArgumentException("Whisper အတွက် PCM 16-bit WAV (mono/stereo) လိုအပ်ပါသည်။")
            }
            return WavInfo(sampleRate, channels, dataOffset, dataBytes)
        }
    }

    private fun readAscii(input: RandomAccessFile, count: Int): String {
        val bytes = ByteArray(count)
        input.readFully(bytes)
        return String(bytes, Charsets.US_ASCII)
    }

    private fun readIntLe(input: RandomAccessFile): Int {
        val a = input.read()
        val b = input.read()
        val c = input.read()
        val d = input.read()
        if (d < 0) throw IllegalStateException("WAV ဖိုင် မပြည့်စုံပါ။")
        return a or (b shl 8) or (c shl 16) or (d shl 24)
    }

    private fun readShortLe(input: RandomAccessFile): Int {
        val a = input.read()
        val b = input.read()
        if (b < 0) throw IllegalStateException("WAV ဖိုင် မပြည့်စုံပါ။")
        return a or (b shl 8)
    }
}
