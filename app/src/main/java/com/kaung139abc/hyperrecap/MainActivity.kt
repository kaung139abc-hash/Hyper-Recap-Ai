package com.kaung139abc.hyperrecap

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.view.Gravity
import android.widget.*
import java.io.*
import java.util.Locale
import java.util.UUID

/**
 * Voice Studio foundation.
 * Android system TTS is used as an honest fallback; it is not advertised as neural cloning.
 */
class MainActivity : Activity(), TextToSpeech.OnInitListener {
    private var engine: TextToSpeech? = null
    private var ready = false
    private var voices: List<Voice> = emptyList()
    private lateinit var editor: EditText
    private lateinit var voicePicker: Spinner
    private lateinit var rate: SeekBar
    private lateinit var pitch: SeekBar
    private lateinit var status: TextView
    private lateinit var referenceLabel: TextView
    private lateinit var exportButton: Button
    private var exportChunks: List<String> = emptyList()
    private var exportFiles = mutableListOf<File>()
    private var exportIndex = 0
    private var exportDestination: Uri? = null
    private var referenceSampleName = "မရွေးရသေးပါ"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        engine = TextToSpeech(this, this)
    }

    private fun buildUi() {
        val bg = 0xFF0D1220.toInt()
        val panel = 0xFF19243A.toInt()
        val white = 0xFFF4F6FF.toInt()
        val muted = 0xFFABB8D0.toInt()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 28, 20, 24)
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        fun title(text: String, size: Float = 16f, color: Int = white) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            setPadding(0, 12, 0, 8)
        }
        fun button(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            setOnClickListener { action() }
        }
        content.addView(title("VOICE STUDIO AI", 27f, 0xFF9FE9E0.toInt()))
        content.addView(title("LOCAL TEXT-TO-SPEECH  •  VOICE WORKSPACE", 11f, muted))
        content.addView(title("စာသားထည့်ရန်"))
        editor = EditText(this).apply {
            hint = "ဒီနေရာမှာ ပုံပြင်၊ ဇာတ်ညွှန်း သို့မဟုတ် စာသားကို ထည့်ပါ..."
            setHintTextColor(muted); setTextColor(white); textSize = 16f
            gravity = Gravity.TOP or Gravity.START
            minLines = 8; setPadding(14, 14, 14, 14)
            setBackgroundColor(panel)
        }
        content.addView(editor, LinearLayout.LayoutParams(-1, -2))
        content.addView(title("Voice Library", 18f, 0xFF9FE9E0.toInt()))
        voicePicker = Spinner(this)
        content.addView(voicePicker)
        content.addView(title("အသံအမြန်နှုန်း", 13f, muted))
        rate = SeekBar(this).apply { max = 150; progress = 50 }
        content.addView(rate)
        content.addView(title("အသံ Pitch", 13f, muted))
        pitch = SeekBar(this).apply { max = 150; progress = 50 }
        content.addView(pitch)
        content.addView(button("▶  အသံနားထောင်ရန်") { speakText() })
        content.addView(button("■  အသံရပ်ရန်") { engine?.stop(); status.text = "အသံရပ်လိုက်ပါပြီ။" })
        exportButton = button("WAV အသံဖိုင် ထုတ်ရန်") { beginExport() }
        content.addView(exportButton)
        content.addView(title("Voice Clone", 18f, 0xFF9FE9E0.toInt()))
        referenceLabel = title("Reference အသံဖိုင်: $referenceSampleName", 12f, muted)
        content.addView(referenceLabel)
        content.addView(button("ကိုယ်ပိုင် / ခွင့်ပြုချက်ရှိသော အသံဖိုင် ရွေးရန်") { chooseReferenceAudio() })
        content.addView(title("မှတ်ချက် — အသံဖိုင်ကို Reference အဖြစ်သာ လက်ရှိသိမ်းထားသည်။ တကယ့် Voice Clone Engine မပေါင်းစည်းရသေးပါ။", 12f, 0xFFFFD28A.toInt()))
        content.addView(title("Model Manager", 18f, 0xFF9FE9E0.toInt()))
        content.addView(title("TTS အသံများသည် ဖုန်းတွင် install လုပ်ထားသော Speech Engine ပေါ်မူတည်သည်။ မြန်မာ Neural Model နှင့် အမှန်တကယ် Voice Cloning ကို သီးခြား License/Quality စစ်ဆေးပြီးမှ ထည့်မည်။", 12f, muted))
        content.addView(button("ဖုန်း၏ TTS Voice Data Settings ဖွင့်ရန်") {
            try { startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA)) }
            catch (_: Exception) { status.text = "Settings > Text-to-speech output မှ အသံများကို စစ်ဆေးပါ။" }
        })
        status = title("TTS Engine စတင်ဖွင့်နေပါသည်...", 13f, 0xFF9FE9E0.toInt())
        content.addView(status)
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    override fun onInit(result: Int) {
        if (result != TextToSpeech.SUCCESS) {
            status.text = "ဖုန်း၏ Text-to-Speech Engine စတင်မရပါ။ TTS Engine တစ်ခု install လုပ်ပါ။"
            return
        }
        ready = true
        val tts = engine ?: return
        tts.language = Locale.getDefault()
        voices = (tts.voices ?: emptySet()).sortedWith(compareBy<Voice>({ it.locale.displayName }, { it.name }))
        val labels = voices.map { voice ->
            val network = if (voice.isNetworkConnectionRequired) " • Online" else " • Offline"
            "${voice.locale.displayName} — ${voice.name}$network"
        }
        voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        status.text = "TTS အသင့်ဖြစ်ပါပြီ။ ဖုန်းတွင် ရရှိနိုင်သော Voice ${voices.size} ခု တွေ့သည်။"
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { }
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("export-") == true) runOnUiThread { exportNextChunk() }
            }
            @Deprecated("Deprecated by Android")
            override fun onError(utteranceId: String?) {
                runOnUiThread { failExport("TTS အသံထုတ်ရာတွင် Error ဖြစ်နေပါသည်။") }
            }
            override fun onError(utteranceId: String?, errorCode: Int) {
                runOnUiThread { failExport("TTS Error Code: $errorCode") }
            }
        })
    }

    private fun applyVoice(): Boolean {
        val tts = engine ?: return false
        if (!ready) { status.text = "TTS Engine မပြင်ဆင်ရသေးပါ။"; return false }
        val index = voicePicker.selectedItemPosition
        if (index in voices.indices) {
            val selected = voices[index]
            val result = tts.setVoice(selected)
            if (result != TextToSpeech.SUCCESS) {
                status.text = "ရွေးထားသော Voice ကို အသုံးပြုမရပါ။"
                return false
            }
        }
        tts.setSpeechRate(0.5f + rate.progress / 100f)
        tts.setPitch(0.5f + pitch.progress / 100f)
        return true
    }

    private fun splitText(source: String, limit: Int = 2200): List<String> {
        val normalized = source.replace("\r", "").trim()
        if (normalized.isEmpty()) return emptyList()
        val sentences = normalized.split(Regex("(?<=[။.!?])\\s+|\\n+")).filter { it.isNotBlank() }
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in sentences) {
            if (current.isNotEmpty() && current.length + sentence.length + 1 > limit) {
                result += current.toString(); current = StringBuilder()
            }
            if (sentence.length <= limit) {
                if (current.isNotEmpty()) current.append(' ')
                current.append(sentence)
            } else {
                var offset = 0
                while (offset < sentence.length) {
                    val end = (offset + limit).coerceAtMost(sentence.length)
                    result += sentence.substring(offset, end)
                    offset = end
                }
            }
        }
        if (current.isNotEmpty()) result += current.toString()
        return result
    }

    private fun speakText() {
        if (!applyVoice()) return
        val chunks = splitText(editor.text.toString())
        if (chunks.isEmpty()) { status.text = "ဖတ်ရန် စာသားထည့်ပါ။"; return }
        engine?.stop()
        chunks.forEachIndexed { index, chunk ->
            val mode = if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            engine?.speak(chunk, mode, Bundle(), "speak-$index-${System.nanoTime()}")
        }
        status.text = "စာသား ${chunks.size} ပိုင်းကို အသံဖတ်နေပါသည်။"
    }

    private fun beginExport() {
        if (!applyVoice()) return
        exportChunks = splitText(editor.text.toString())
        if (exportChunks.isEmpty()) { status.text = "အသံထုတ်ရန် စာသားထည့်ပါ။"; return }
        exportFiles.forEach { it.delete() }; exportFiles.clear()
        exportDestination = null
        exportIndex = 0
        exportButton.isEnabled = false
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "VoiceStudio-Narration.wav")
        }
        startActivityForResult(intent, REQUEST_EXPORT)
    }

    private fun exportNextChunk() {
        if (exportIndex >= exportChunks.size) {
            try {
                val merged = File(cacheDir, "voice-export-${System.currentTimeMillis()}.wav")
                mergeWaveFiles(exportFiles, merged)
                val destination = exportDestination ?: throw IllegalStateException("သိမ်းမည့်နေရာ မရွေးထားပါ။")
                contentResolver.openOutputStream(destination)?.use { output -> merged.inputStream().use { it.copyTo(output) } }
                    ?: throw IllegalStateException("Output file ဖွင့်မရပါ။")
                merged.delete()
                exportFiles.forEach { it.delete() }; exportFiles.clear()
                exportButton.isEnabled = true
                status.text = "WAV ဖိုင် အောင်မြင်စွာ သိမ်းပြီးပါပြီ။ ${exportChunks.size} ပိုင်း။"
            } catch (e: Exception) { failExport(e.localizedMessage ?: "WAV ဖိုင်ပေါင်းစည်းမရပါ။") }
            return
        }
        val chunk = exportChunks[exportIndex]
        val file = File(cacheDir, "tts-${UUID.randomUUID()}.wav")
        exportFiles += file
        val id = "export-$exportIndex"
        val result = engine?.synthesizeToFile(chunk, Bundle(), file, id) ?: TextToSpeech.ERROR
        if (result != TextToSpeech.SUCCESS) { failExport("TTS က အသံဖိုင်ထုတ်လုပ်မှုကို လက်မခံပါ။"); return }
        status.text = "WAV ထုတ်နေသည် — ${exportIndex + 1}/${exportChunks.size}"
        exportIndex++
    }

    private fun mergeWaveFiles(files: List<File>, destination: File) {
        if (files.isEmpty()) throw IllegalStateException("အသံအပိုင်း မရှိပါ။")
        data class WavePart(val format: ByteArray, val payload: ByteArray)
        val parts = files.map { file ->
            val bytes = file.readBytes()
            if (bytes.size < 44 || String(bytes, 0, 4) != "RIFF" || String(bytes, 8, 4) != "WAVE")
                throw IllegalStateException("TTS Engine က standard WAV ဖိုင် မထုတ်ပေးပါ။")
            var offset = 12
            var format: ByteArray? = null
            var payload: ByteArray? = null
            while (offset + 8 <= bytes.size) {
                val id = String(bytes, offset, 4)
                val size = readIntLe(bytes, offset + 4)
                if (size < 0 || offset + 8L + size > bytes.size)
                    throw IllegalStateException("WAV chunk ပျက်နေပါသည်။")
                when (id) {
                    "fmt " -> {
                        if (size < 16) throw IllegalStateException("WAV format header မပြည့်စုံပါ။")
                        format = bytes.copyOfRange(offset + 8, offset + 24)
                    }
                    "data" -> payload = bytes.copyOfRange(offset + 8, offset + 8 + size)
                }
                offset += 8 + size + (size and 1)
            }
            val fmt = format ?: throw IllegalStateException("WAV format chunk မတွေ့ပါ။")
            val data = payload ?: throw IllegalStateException("WAV data chunk မတွေ့ပါ။")
            if (readIntLe(fmt, 0) != 1)
                throw IllegalStateException("လက်ရှိ WAV ပေါင်းစည်းမှုသည် PCM အသံဖိုင်ကိုသာ ထောက်ပံ့သည်။")
            WavePart(fmt, data)
        }
        val firstFormat = parts.first().format
        if (parts.any { !it.format.contentEquals(firstFormat) })
            throw IllegalStateException("အသံအပိုင်းများ၏ sample rate/channel format မတူညီပါ။")
        val total = parts.sumOf { it.payload.size.toLong() }
        if (total > Int.MAX_VALUE - 36) throw IllegalStateException("WAV ဖိုင် အလွန်ကြီးနေပါသည်။")
        DataOutputStream(BufferedOutputStream(FileOutputStream(destination))).use { out ->
            out.writeBytes("RIFF"); writeIntLe(out, 36 + total.toInt())
            out.writeBytes("WAVE"); out.writeBytes("fmt "); writeIntLe(out, 16)
            out.write(firstFormat)
            out.writeBytes("data"); writeIntLe(out, total.toInt())
            parts.forEach { out.write(it.payload) }
        }
    }

    private fun readIntLe(b: ByteArray, at: Int): Int =
        (b[at].toInt() and 255) or ((b[at+1].toInt() and 255) shl 8) or
            ((b[at+2].toInt() and 255) shl 16) or ((b[at+3].toInt() and 255) shl 24)

    private fun writeIntLe(out: DataOutputStream, value: Int) {
        out.writeByte(value and 255); out.writeByte((value ushr 8) and 255)
        out.writeByte((value ushr 16) and 255); out.writeByte((value ushr 24) and 255)
    }

    private fun failExport(message: String) {
        exportFiles.forEach { it.delete() }; exportFiles.clear()
        exportButton.isEnabled = true
        status.text = "WAV ထုတ်မရပါ: $message"
    }

    private fun chooseReferenceAudio() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"
        }
        startActivityForResult(intent, REQUEST_REFERENCE)
    }

    @Deprecated("Use Activity Result APIs in a later cleanup")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) {
            if (requestCode == REQUEST_EXPORT) {
                exportButton.isEnabled = true
                exportFiles.forEach { it.delete() }; exportFiles.clear()
                status.text = "WAV သိမ်းဆည်းမှုကို ပယ်ဖျက်လိုက်ပါပြီ။"
            }
            return
        }
        val uri = data.data!!
        if (requestCode == REQUEST_REFERENCE) {
            try {
                val target = File(filesDir, "voice-reference-${System.currentTimeMillis()}.audio")
                contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } }
                    ?: throw IllegalStateException("အသံဖိုင်ဖတ်မရပါ။")
                referenceSampleName = "သိမ်းပြီးပါပြီ (${target.length() / 1024} KB)"
                referenceLabel.text = "Reference အသံဖိုင်: $referenceSampleName"
                status.text = "Reference အသံဖိုင်ကို Local Storage ထဲသိမ်းပြီးပါပြီ။ လက်ရှိတွင် Clone မလုပ်သေးပါ။"
            } catch (e: Exception) { status.text = "Reference အသံသိမ်းမရပါ: ${e.localizedMessage}" }
        } else if (requestCode == REQUEST_EXPORT) {
            exportDestination = uri
            exportNextChunk()
        }
    }

    override fun onDestroy() {
        engine?.stop()
        engine?.shutdown()
        exportFiles.forEach { it.delete() }
        super.onDestroy()
    }

    companion object {
        private const val REQUEST_EXPORT = 4101
        private const val REQUEST_REFERENCE = 4102
    }
}
