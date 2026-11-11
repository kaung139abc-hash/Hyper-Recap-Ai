package com.kaung139abc.hyperrecap

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaFormat
import android.media.MediaCodec
import java.nio.ByteBuffer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.speech.tts.UtteranceProgressListener
import android.os.Bundle
import android.provider.Settings
import android.net.Uri
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale
import java.io.File
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer

class MainActivity : Activity() {
    private lateinit var selectedInfo: TextView
    private lateinit var status: TextView
    private lateinit var linkInput: EditText
    private lateinit var transcriptInput: EditText
    private lateinit var recapOutput: TextView
    private var latestRecap: String = ""
    private var latestSrt: String = ""
    private var importedSrtCues: List<SrtTranscript.Cue> = emptyList()
    private var importedSrtSource: String = ""
    private var tts: TextToSpeech? = null
    private var availableVoices: List<Voice> = emptyList()
    private lateinit var voicePicker: Spinner
    private lateinit var speechRate: SeekBar
    private lateinit var speechPitch: SeekBar
    private lateinit var voiceInfo: TextView
    private var pendingAudioUri: Uri? = null
    private var pendingAudioFile: File? = null
    private var selectedVideoUri: Uri? = null
    private var pendingExtractedAudio: File? = null
    private var pendingDecodedWav: File? = null
    @Volatile private var transcriptionInProgress = false
    private var pendingSubtitleDestination: Uri? = null
    private var activeTransformer: Transformer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
        initializeSpeech()
        handleSharedLink(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleSharedLink(intent)
    }

    private fun handleSharedLink(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!shared.isNullOrBlank() && ::linkInput.isInitialized) {
                val detected = SocialLinkSupport.extract(shared)
                linkInput.setText(detected?.uri?.toString() ?: shared.trim())
            }
        }
    }

    private fun openSocialLink(raw: String) {
        val link = SocialLinkSupport.extract(raw)
        if (link == null || link.platform == SocialLinkSupport.Platform.UNKNOWN) {
            status.text = "YouTube, Facebook, TikTok သို့မဟုတ် Instagram Link မှန်ကန်စွာ ထည့်ပါ။"
            return
        }
        try {
            startActivity(SocialLinkSupport.openIntent(link))
            status.text = "${link.platform.label} ကို ဖွင့်လိုက်ပါပြီ။ Platform က ခွင့်ပြုထားသောနည်းဖြင့် Video ကို သိမ်းပြီးနောက် ဖုန်းထဲမှ Video ရွေးပါ။ Login/DRM ကန့်သတ်ချက်များကို ကျော်ဖြတ်ခြင်း မလုပ်ပါ။"
        } catch (e: Exception) {
            status.text = "Link ဖွင့်မရပါ: ${e.localizedMessage ?: "Browser မတွေ့ပါ"}"
        }
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 36, 24, 24)
            setBackgroundColor(0xFF10131B.toInt())
        }
        fun label(text: String, size: Float, color: Int = 0xFFFFFFFF.toInt()) =
            TextView(this).apply {
                this.text = text
                textSize = size
                setTextColor(color)
                gravity = Gravity.CENTER
            }

        root.addView(label("HYPER RECAP AI", 27f, 0xFF8EA5FF.toInt()))
        root.addView(label("Local Movie Recap Studio", 15f, 0xFFB8C0D9.toInt()))
        root.addView(space(22))

        linkInput = EditText(this).apply {
            hint = "Video link ကို ဒီမှာ Paste လုပ်ပါ"
            setHintTextColor(0xFF9AA4BC.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 15f
            setSingleLine(true)
            setPadding(14, 12, 14, 12)
            setBackgroundColor(0xFF252B3A.toInt())
        }
        root.addView(linkInput, matchWidth())

        val linkButton = Button(this).apply {
            text = "Direct Video Link Download"
            setOnClickListener { downloadDirectVideo(linkInput.text.toString().trim()) }
        }
        root.addView(linkButton, matchWidth())
        root.addView(Button(this).apply {
            text = "YouTube / Facebook / TikTok / Instagram Link ဖွင့်ရန်"
            setOnClickListener { openSocialLink(linkInput.text.toString()) }
        }, matchWidth())

        val choose = Button(this).apply {
            text = "ဖုန်းထဲက Video ရွေးရန်"
            setOnClickListener { chooseVideo() }
        }
        root.addView(choose, matchWidth())
        root.addView(Button(this).apply {
            text = "Video အသံကို WAV (PCM) ပြောင်းထုတ်ရန်"
            setOnClickListener { decodeSelectedAudio() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "Whisper Offline Speech-to-Text (မြန်မာအပါအဝင်)"
            setOnClickListener { transcribeSelectedVideo() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "မူရင်း Audio Track ကို M4A ထုတ်ရန်"
            setOnClickListener { extractSelectedAudio() }
        }, matchWidth())

        selectedInfo = label("Video မရွေးရသေးပါ", 16f)
        selectedInfo.setPadding(0, 24, 0, 16)
        root.addView(selectedInfo)

        root.addView(label("OFFLINE TEXT RECAP", 18f, 0xFF8EA5FF.toInt()))
        root.addView(label("Transcript / ဇာတ်လမ်းစာသားကို Paste လုပ်ပါ", 13f, 0xFFB8C0D9.toInt()))
        transcriptInput = EditText(this).apply {
            hint = "စာသားထည့်ရန်..."
            setHintTextColor(0xFF9AA4BC.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            minLines = 5
            gravity = Gravity.TOP
            setPadding(14, 12, 14, 12)
            setBackgroundColor(0xFF252B3A.toInt())
        }
        root.addView(transcriptInput, matchWidth())
        root.addView(Button(this).apply {
            text = "TXT / SRT Transcript ဖိုင်ထည့်ရန်"
            setOnClickListener {
                val pick = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("text/plain", "application/x-subrip", "text/srt"))
                }
                startActivityForResult(pick, REQUEST_TRANSCRIPT)
            }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "Generate Offline Recap"
            setOnClickListener { generateRecap() }
        }, matchWidth())
        recapOutput = label("Recap ရလဒ် ဒီနေရာမှာ ပေါ်လာပါမည်", 14f)
        recapOutput.gravity = Gravity.START
        root.addView(recapOutput, matchWidth())
        root.addView(Button(this).apply {
            text = "Recap TXT သိမ်းရန်"
            setOnClickListener { saveRecap() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "Recap Subtitle SRT ထုတ်ရန်"
            setOnClickListener { saveSubtitles() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "မြန်မာစာတန်းထိုး MP4 ထုတ်ရန် (Video ထဲ တစ်ခါတည်းထည့်)"
            setOnClickListener { requestSubtitleMp4Destination() }
        }, matchWidth())

        root.addView(space(18))
        root.addView(label("VOICE STUDIO", 18f, 0xFF8EA5FF.toInt()))
        root.addView(label("ဖုန်းထဲတွင် ရရှိနိုင်သော အသံများကို ရွေးချယ်ပါ", 13f, 0xFFB8C0D9.toInt()))
        voicePicker = Spinner(this)
        root.addView(voicePicker, matchWidth())
        voiceInfo = label("အသံစာရင်း ဖတ်နေပါသည်...", 12f, 0xFFB8C0D9.toInt())
        root.addView(voiceInfo, matchWidth())
        root.addView(label("အသံမြန်နှုန်း", 13f, 0xFFFFFFFF.toInt()))
        speechRate = SeekBar(this).apply { max = 150; progress = 50 }
        root.addView(speechRate, matchWidth())
        root.addView(label("အသံအနိမ့်အမြင့်", 13f, 0xFFFFFFFF.toInt()))
        speechPitch = SeekBar(this).apply { max = 150; progress = 50 }
        root.addView(speechPitch, matchWidth())
        root.addView(Button(this).apply {
            text = "ရွေးထားသောအသံဖြင့် Recap ဖတ်ရန်"
            setOnClickListener { speakRecap() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "အသံစမ်းနားထောင်ရန်"
            setOnClickListener { previewVoice() }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "အသံရပ်ရန်"
            setOnClickListener {
                tts?.stop()
                status.text = "အသံဖတ်ခြင်း ရပ်လိုက်ပါပြီ။"
            }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "ဖုန်း၏ TTS အသံအသစ်များ ထည့်သွင်းရန်"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (_: Exception) {
                    status.text = "Settings > Accessibility > Text-to-speech output ကို ဖွင့်ပါ။"
                }
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "Recap အသံဖိုင် WAV သိမ်းရန်"
            setOnClickListener { saveRecapAudio() }
        }, matchWidth())

        status = label("အင်တာနက်မလို၊ API key မလို၊ daily quota မရှိသော local text recap", 13f, 0xFFB8C0D9.toInt())
        root.addView(status)

        val note = label(
            "မှတ်ချက် — Whisper multilingual Base int8 Model ကို ပထမဆုံးအသုံးပြုချိန်တွင် Internet ဖြင့် 208 MB ခန့် Download လုပ်ရမည်။ ထို့နောက် Speech-to-Text ကို Offline လုပ်နိုင်သည်။ YouTube/Facebook/TikTok/Instagram Link များကို Official App/Browser ဖွင့်ပေးခြင်းသာ ရရှိပြီး Platform က ခွင့်ပြုသော Video ဖိုင်ကို ဖုန်းထဲသိမ်းပြီးမှ ရွေးပါ။ MP4 Subtitle Export သည် Video ကို ပြန် Encode လုပ်သောကြောင့် အချိန်နှင့် Battery ပိုသုံးနိုင်သည်။",
            13f, 0xFFFFD28A.toInt()
        )
        note.setPadding(0, 28, 0, 0)
        root.addView(note)
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            setBackgroundColor(0xFF10131B.toInt())
            addView(root, android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        setContentView(scroll)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }
    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )


    override fun onResume() {
        super.onResume()
        // Refresh voice data after returning from Android TTS settings.
        if (tts != null && ::voicePicker.isInitialized) {
            try {
                val voices = tts?.voices ?: emptySet()
                val excluded = listOf("thiha", "nilar", "သီဟ", "နီလာ")
                availableVoices = voices.filter { voice ->
                    val identity = (voice.name + " " + voice.locale.displayName).lowercase(Locale.ROOT)
                    excluded.none { identity.contains(it) }
                }.sortedWith(compareBy<Voice>(
                    { if (it.locale.language == "my") 0 else 1 },
                    { -it.quality }, { it.locale.displayName }, { it.name }
                ))
                val labels = availableVoices.map { voice ->
                    val mode = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                    "${voice.locale.displayName} — ${voice.name} — $mode — Quality ${voice.quality}"
                }
                voicePicker.adapter = ArrayAdapter(
                    this, android.R.layout.simple_spinner_dropdown_item,
                    if (labels.isEmpty()) listOf("အသုံးပြုနိုင်သော အသံမရှိပါ") else labels
                )
                if (labels.isNotEmpty()) {
                    voicePicker.setSelection(0)
                    tts?.voice = availableVoices[0]
                    voiceInfo.text = "အသံ ${labels.size} မျိုး ရရှိသည်။ Settings မှ ပြန်လာတိုင်း စာရင်းကို အသစ်ပြန်ဖတ်ထားသည်။"
                } else {
                    voiceInfo.text = "TTS အသံမတွေ့ပါ။ Android Text-to-speech Settings မှ Voice data ထည့်ပါ။"
                }
            } catch (e: Exception) {
                voiceInfo.text = "အသံစာရင်း ပြန်ဖတ်မရပါ: ${e.localizedMessage ?: "Error"}"
            }
        }
    }

    private fun initializeSpeech() {
        tts = TextToSpeech(this) { result ->
            if (result != TextToSpeech.SUCCESS) {
                voiceInfo.text = "ဖုန်း၏ Text-to-Speech Engine ကို စတင်မရပါ။ Android Settings မှ TTS engine/voice data ကို စစ်ဆေးပါ။"
                return@TextToSpeech
            }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onError(utteranceId: String?) {
                    runOnUiThread { status.text = "အသံဖိုင်ဖန်တီးရာတွင် အမှားဖြစ်နေပါသည်။" }
                    pendingAudioFile?.delete()
                    pendingAudioFile = null
                    pendingAudioUri = null
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId != "hyper_recap_audio_export") return
                    val sourceFile = pendingAudioFile
                    val destination = pendingAudioUri
                    if (sourceFile == null || destination == null) return
                    Thread {
                        try {
                            contentResolver.openOutputStream(destination)?.use { output ->
                                sourceFile.inputStream().use { input -> input.copyTo(output) }
                            } ?: throw IllegalStateException("Output stream unavailable")
                            runOnUiThread { status.text = "Recap အသံဖိုင် WAV အဖြစ် သိမ်းပြီးပါပြီ။" }
                        } catch (e: Exception) {
                            runOnUiThread { status.text = "အသံဖိုင် သိမ်းမရပါ: ${e.localizedMessage ?: "Error"}" }
                        } finally {
                            sourceFile.delete()
                            pendingAudioFile = null
                            pendingAudioUri = null
                        }
                    }.start()
                }
            })
            try {
                val excluded = listOf("thiha", "nilar", "သီဟ", "နီလာ")
                availableVoices = (tts?.voices ?: emptySet())
                    .filter { voice ->
                        val identity = (voice.name + " " + voice.locale.displayName).lowercase(Locale.ROOT)
                        excluded.none { identity.contains(it) }
                    }
                    .sortedWith(compareBy<Voice>({ if (it.locale.language == "my") 0 else 1 }, { -it.quality }, { it.locale.displayName }, { it.name }))
                val labels = availableVoices.map { voice ->
                    val mode = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                    "${voice.locale.displayName} — ${voice.name} — $mode — Quality ${voice.quality}"
                }
                if (labels.isEmpty()) {
                    voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("အသုံးပြုနိုင်သော အသံမရှိပါ"))
                    voiceInfo.text = "ဒီဖုန်းမှာ ရွေးချယ်နိုင်တဲ့ TTS အသံ မတွေ့ပါ။ Settings > Text-to-speech မှာ Voice data ထည့်သွင်းပါ။"
                } else {
                    voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
                    voicePicker.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                        override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                        override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                            try {
                                tts?.voice = availableVoices[position]
                                val v = availableVoices[position]
                                voiceInfo.text = "ရွေးထားသည်: ${v.locale.displayName} | ${if (v.isNetworkConnectionRequired) "Online voice (Internet လိုနိုင်)" else "Offline voice"} | Quality ${v.quality}"
                            } catch (e: Exception) {
                                voiceInfo.text = "ဒီအသံကို ရွေးမရပါ: ${e.localizedMessage ?: "Error"}"
                            }
                        }
                    }
                    voiceInfo.text = "အသံ ${labels.size} မျိုး တွေ့ရှိသည်။ မြန်မာအသံရှိပါက စာရင်းအပေါ်ဆုံးတွင် ဦးစားပေးထားသည်။ Quality မြင့်သောအသံများကိုလည်း ဦးစားပေးထားသည်။"
                }
            } catch (e: Exception) {
                voiceInfo.text = "အသံစာရင်း ဖတ်မရပါ: ${e.localizedMessage ?: "Error"}"
            }
        }
    }


    private fun saveRecapAudio() {
        if (latestRecap.isBlank()) {
            status.text = "အရင်ဆုံး Recap Generate လုပ်ပါ။"
            return
        }
        if (tts == null || availableVoices.isEmpty()) {
            status.text = "အသုံးပြုနိုင်သော Voice မရှိသေးပါ။"
            return
        }
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "HyperRecap.wav")
        }
        startActivityForResult(saveIntent, REQUEST_SAVE_AUDIO)
    }

    private fun synthesizeRecapAudio(destination: Uri) {
        val engine = tts ?: return
        try {
            engine.setSpeechRate(0.5f + speechRate.progress / 100f)
            engine.setPitch(0.5f + speechPitch.progress / 100f)
            val file = File(cacheDir, "hyper_recap_${System.currentTimeMillis()}.wav")
            pendingAudioUri = destination
            pendingAudioFile = file
            val result = engine.synthesizeToFile(latestRecap, Bundle(), file, "hyper_recap_audio_export")
            if (result != TextToSpeech.SUCCESS) {
                file.delete()
                pendingAudioUri = null
                pendingAudioFile = null
                status.text = "ဒီ TTS Engine က အသံဖိုင်ထုတ်ပေးမှုကို မပံ့ပိုးပါ။"
            } else {
                status.text = "အသံဖိုင် WAV ဖန်တီးနေပါသည်။ ခဏစောင့်ပါ..."
            }
        } catch (e: Exception) {
            pendingAudioFile?.delete()
            pendingAudioFile = null
            pendingAudioUri = null
            status.text = "အသံဖိုင်ဖန်တီးမရပါ: ${e.localizedMessage ?: "Error"}"
        }
    }

    private fun speakRecap() {
        if (latestRecap.isBlank()) {
            status.text = "အရင်ဆုံး Recap Generate လုပ်ပါ။"
            return
        }
        val engine = tts
        if (engine == null || availableVoices.isEmpty()) {
            status.text = "အသံ Engine သို့မဟုတ် Voice data မရရှိသေးပါ။"
            return
        }
        engine.setSpeechRate(0.5f + speechRate.progress / 100f)
        engine.setPitch(0.5f + speechPitch.progress / 100f)
        engine.speak(latestRecap, TextToSpeech.QUEUE_FLUSH, null, "hyper_recap_speech")
        status.text = "ရွေးထားသောအသံဖြင့် Recap ဖတ်နေပါသည်။"
    }

    private fun previewVoice() {
        val engine = tts
        if (engine == null || availableVoices.isEmpty()) {
            status.text = "အသုံးပြုနိုင်သော Voice မရှိသေးပါ။"
            return
        }
        engine.setSpeechRate(0.5f + speechRate.progress / 100f)
        engine.setPitch(0.5f + speechPitch.progress / 100f)
        engine.speak("မင်္ဂလာပါ။ Hyper Recap AI အသံစမ်းသပ်မှု ဖြစ်ပါတယ်။ ရွေးချယ်ထားတဲ့ အသံနဲ့ စာသားကို ဖတ်ပြပါမယ်။", TextToSpeech.QUEUE_FLUSH, null, "hyper_recap_preview")
        status.text = "အသံစမ်းသပ်နေပါသည်။"
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private fun generateRecap() {
        val source = transcriptInput.text.toString().trim()
        if (source.length < 80) {
            status.text = "Transcript စာသား အနည်းဆုံး စာလုံး ၈၀ ခန့် ထည့်ပါ။"
            return
        }
        val sourceCues = importedSrtCues
        if (sourceCues.isNotEmpty() && source == importedSrtSource) {
            val selectedCues = SrtTranscript.summarize(sourceCues)
            latestRecap = selectedCues.joinToString("\n\n") { it.text }
            latestSrt = SrtTranscript.toSrt(selectedCues)
            status.text = "Source-timed SRT Recap ပြီးပါပြီ။ Recap စာသားနှင့် SRT တို့သည် မူရင်း Video timestamp များအတိုင်း ကိုက်ညီသည်။"
        } else {
            latestRecap = OfflineRecap.summarize(source)
            latestSrt = SubtitleDraft.fromNarration(latestRecap)
            status.text = "Offline Recap ပြီးပါပြီ။ SRT သည် narration အတွက် ခန့်မှန်း timing ဖြစ်သည်။ မူရင်း Video နှင့် sync လုပ်ရန် SRT ဖိုင်ကို import လုပ်ပြီး စာသားမပြောင်းဘဲ Generate လုပ်ပါ။"
        }
        recapOutput.text = latestRecap
    }


    private fun requestSubtitleMp4Destination() {
        if (selectedVideoUri == null) {
            status.text = "အရင်ဆုံး Video ရွေးပါ။"
            return
        }
        if (latestSrt.isBlank()) {
            status.text = "အရင်ဆုံး Whisper Transcription လုပ်ပါ သို့မဟုတ် SRT Recap Generate လုပ်ပါ။"
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"
            putExtra(Intent.EXTRA_TITLE, "HyperRecap-Subtitled.mp4")
        }
        startActivityForResult(intent, REQUEST_SAVE_OVERLAY_MP4)
    }

    @OptIn(UnstableApi::class)
    private fun startSubtitleMp4Export(destination: Uri) {
        val source = selectedVideoUri ?: run {
            status.text = "Video မရွေးရသေးပါ။"
            return
        }
        val cues = SrtTranscript.parse(latestSrt)
        if (cues.isEmpty()) {
            status.text = "အသုံးပြုနိုင်သော SRT Subtitle မတွေ့ပါ။"
            return
        }
        if (activeTransformer != null) {
            status.text = "Subtitle MP4 Export လုပ်ဆောင်နေဆဲပါ။"
            return
        }
        val output = File(cacheDir, "hyper_subtitled_" + System.currentTimeMillis() + ".mp4")
        try {
            var width = 1280
            var height = 720
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(this, source)
                width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: width
                height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: height
                val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                if (rotation == 90 || rotation == 270) {
                    val temp = width
                    width = height
                    height = temp
                }
            } finally {
                retriever.release()
            }
            val overlay = SubtitleBurnIn.createEffect(width.coerceIn(320, 3840), height.coerceIn(320, 3840), cues)
            val edited = EditedMediaItem.Builder(MediaItem.fromUri(source))
                .setEffects(Effects(emptyList(), listOf(overlay)))
                .build()
            val sequence = EditedMediaItemSequence.Builder(listOf(edited)).build()
            val composition = Composition.Builder(sequence).build()
            output.createNewFile()
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Thread {
                            try {
                                contentResolver.openOutputStream(destination)?.use { target ->
                                    output.inputStream().use { input -> input.copyTo(target) }
                                } ?: throw IllegalStateException("MP4 သိမ်းမည့်နေရာ ဖွင့်မရပါ။")
                                runOnUiThread {
                                    status.text = "မြန်မာ Subtitle ကို Video ထဲ Burn-in လုပ်ပြီး MP4 သိမ်းပြီးပါပြီ။"
                                }
                            } catch (e: Exception) {
                                runOnUiThread { status.text = "MP4 သိမ်းမရပါ: " + (e.localizedMessage ?: "Error") }
                            } finally {
                                output.delete()
                                activeTransformer = null
                                pendingSubtitleDestination = null
                            }
                        }.start()
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        output.delete()
                        activeTransformer = null
                        pendingSubtitleDestination = null
                        runOnUiThread {
                            status.text = "Subtitle MP4 Export မအောင်မြင်ပါ: " + (exportException.localizedMessage ?: "Encoding error")
                        }
                    }
                })
                .build()
            activeTransformer = transformer
            pendingSubtitleDestination = destination
            status.text = "Subtitle ကို Video frame တစ်ခုချင်းစီထဲ ထည့်ပြီး MP4 encode လုပ်နေပါသည်။ Video ကြာလျှင် အချိန်ကြာနိုင်သည်..."
            transformer.start(composition, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            activeTransformer = null
            status.text = "Subtitle MP4 စတင်မရပါ: " + (e.localizedMessage ?: "Export error")
        }
    }

    private fun saveSubtitles() {
        if (latestSrt.isBlank()) {
            status.text = "အရင်ဆုံး Recap Generate လုပ်ပါ။"
            return
        }
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/x-subrip"
            putExtra(Intent.EXTRA_TITLE, "HyperRecap-Subtitles.srt")
        }
        startActivityForResult(saveIntent, REQUEST_SAVE_SRT)
    }

    private fun saveRecap() {
        if (latestRecap.isBlank()) {
            status.text = "အရင်ဆုံး Recap Generate လုပ်ပါ။"
            return
        }
        val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "HyperRecap.txt")
        }
        startActivityForResult(saveIntent, REQUEST_SAVE)
    }

    private fun downloadDirectVideo(raw: String) {
        val social = SocialLinkSupport.extract(raw)
        if (social != null && social.platform != SocialLinkSupport.Platform.UNKNOWN) {
            openSocialLink(raw)
            return
        }
        val uri = try { Uri.parse(raw.trim()) } catch (_: Exception) { null }
        if (uri == null || uri.scheme !in listOf("http", "https") || uri.host.isNullOrBlank()) {
            status.text = "မှန်ကန်သော http/https link ထည့်ပါ"
            return
        }
        val path = uri.path?.lowercase(Locale.ROOT).orEmpty()
        val supported = listOf(".mp4", ".mkv", ".webm", ".mov", ".m4v").any { path.endsWith(it) }
        if (!supported) {
            status.text = "ဤ URL သည် တိုက်ရိုက် Video ဖိုင်မဟုတ်နိုင်ပါ။ ဖုန်းထဲသို့ Video ကို အရင်သိမ်းပြီး Video ရွေးရန်ကို နှိပ်ပါ။"
            return
        }
        try {
            val request = DownloadManager.Request(uri)
                .setTitle("Hyper Recap AI — Video")
                .setDescription("Downloading direct video file")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, "HyperRecap_${System.currentTimeMillis()}.${path.substringAfterLast('.')}")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
            val manager = getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            manager.enqueue(request)
            status.text = "Download စတင်ပါပြီ။ ပြီးသွားလျှင် ဖုန်းထဲက Video ရွေးရန်ကို နှိပ်ပါ။"
        } catch (e: Exception) {
            status.text = "Download မစနိုင်ပါ: ${e.localizedMessage ?: "Error"}"
        }
    }

    /** Extracts/remuxes the existing audio stream; this is preparation, not speech recognition. */
    /** Decode video audio incrementally to PCM WAV for a future local ASR engine. */
    private fun decodeSelectedAudio() {
        val sourceUri = selectedVideoUri
        if (sourceUri == null) { status.text = "အရင်ဆုံး Video ရွေးပါ။"; return }
        status.text = "Video အသံကို PCM WAV ပြောင်းနေပါသည်။ ဖိုင်ရှည်လျှင် အချိန်ကြာနိုင်သည်..."
        Thread {
            val output = File(cacheDir, "hyper_pcm_" + System.currentTimeMillis() + ".wav")
            try {
                AudioDecoder.decodeToWav(this, sourceUri, output) { message ->
                    runOnUiThread { status.text = message }
                }
                pendingDecodedWav = output
                runOnUiThread {
                    val save = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "audio/wav"
                        putExtra(Intent.EXTRA_TITLE, "HyperRecap-Audio-PCM.wav")
                    }
                    startActivityForResult(save, REQUEST_SAVE_DECODED_WAV)
                    status.text = "PCM WAV ပြောင်းပြီးပါပြီ။ သိမ်းမည့်နေရာရွေးပါ။"
                }
            } catch (e: Exception) {
                output.delete()
                runOnUiThread { status.text = "PCM WAV ပြောင်းမရပါ: " + (e.localizedMessage ?: "Unsupported audio") }
            }
        }.start()
    }

    private fun transcribeSelectedVideo() {
        val sourceUri = selectedVideoUri
        if (sourceUri == null) {
            status.text = "အရင်ဆုံး Video ရွေးပါ။"
            return
        }
        if (transcriptionInProgress) {
            status.text = "Whisper Speech-to-Text လုပ်ဆောင်နေဆဲပါ။"
            return
        }
        transcriptionInProgress = true
        status.text = "Whisper အတွက် Video အသံကို PCM ပြောင်းနေပါသည်..."
        Thread {
            val wav = File(cacheDir, "hyper_whisper_${System.currentTimeMillis()}.wav")
            try {
                AudioDecoder.decodeToWav(this, sourceUri, wav) { message ->
                    runOnUiThread { status.text = message }
                }
                val segments = WhisperTranscriber.transcribeVideoAudio(this, wav) { message ->
                    runOnUiThread { status.text = message }
                }
                if (segments.isEmpty()) {
                    throw IllegalStateException("စကားပြောသံ မတွေ့ပါ။ Video Audio Track ကို စစ်ဆေးပါ။")
                }
                val cues = segments.map { SrtTranscript.Cue(it.startMs, it.endMs, it.text) }
                val transcript = cues.joinToString("\n") { it.text }
                runOnUiThread {
                    importedSrtCues = cues
                    importedSrtSource = transcript
                    transcriptInput.setText(transcript)
                    latestSrt = SrtTranscript.toSrt(cues)
                    recapOutput.text = "Whisper Transcript ရရှိပါပြီ။ Generate Offline Recap ကို နှိပ်ပါ။"
                    status.text = "Whisper Offline Transcription ပြီးပါပြီ။ ${cues.size} အပိုင်း ရရှိသည်။ မူရင်း Video အချိန်အပိုင်းအခြားများကို SRT တွင် ထိန်းသိမ်းထားသည်။"
                    if (transcript.length >= 80) generateRecap()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Whisper Transcription မအောင်မြင်ပါ: ${e.localizedMessage ?: "Model/Audio error"}"
                }
            } finally {
                wav.delete()
                transcriptionInProgress = false
            }
        }.start()
    }

    private fun extractSelectedAudio() {
        val sourceUri = selectedVideoUri
        if (sourceUri == null) { status.text = "အရင်ဆုံး Video ရွေးပါ။"; return }
        status.text = "Video Audio Track ထုတ်ယူနေပါသည်..."
        Thread {
            var extractorToRelease: MediaExtractor? = null
            var muxerToRelease: MediaMuxer? = null
            val output = File(cacheDir, "hyper_audio_" + System.currentTimeMillis() + ".m4a")
            try {
                val ex = MediaExtractor(); extractorToRelease = ex
                contentResolver.openFileDescriptor(sourceUri, "r")?.use { ex.setDataSource(it.fileDescriptor) }
                    ?: throw IllegalStateException("Video ဖိုင်ဖွင့်မရပါ။")
                var track = -1
                var format: MediaFormat? = null
                for (i in 0 until ex.trackCount) {
                    val candidate = ex.getTrackFormat(i)
                    if ((candidate.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) { track = i; format = candidate; break }
                }
                if (track < 0 || format == null) throw IllegalStateException("Audio Track မတွေ့ပါ။")
                ex.selectTrack(track)
                val mx = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4); muxerToRelease = mx
                val outTrack = mx.addTrack(format)
                mx.start()
                val capacity = if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE).coerceAtLeast(262144) else 1048576
                val buffer = ByteBuffer.allocateDirect(capacity)
                val info = MediaCodec.BufferInfo()
                while (true) {
                    buffer.clear()
                    val size = ex.readSampleData(buffer, 0)
                    if (size < 0) break
                    info.offset = 0; info.size = size; info.presentationTimeUs = ex.sampleTime; info.flags = ex.sampleFlags
                    mx.writeSampleData(outTrack, buffer, info)
                    if (!ex.advance()) break
                }
                mx.stop(); mx.release(); muxerToRelease = null
                ex.release(); extractorToRelease = null
                if (!output.exists() || output.length() == 0L) throw IllegalStateException("Audio data မရပါ။")
                pendingExtractedAudio = output
                runOnUiThread {
                    val save = Intent(Intent.ACTION_CREATE_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "audio/mp4"; putExtra(Intent.EXTRA_TITLE, "HyperRecap-Audio.m4a") }
                    startActivityForResult(save, REQUEST_SAVE_EXTRACTED_AUDIO)
                    status.text = "Audio Track ထုတ်ယူပြီးပါပြီ။ သိမ်းမည့်နေရာရွေးပါ။"
                }
            } catch (e: Exception) {
                output.delete()
                runOnUiThread { status.text = "Audio ထုတ်ယူမရပါ: " + (e.localizedMessage ?: "Unsupported format") }
            } finally { try { muxerToRelease?.stop() } catch (_: Exception) {}; try { muxerToRelease?.release() } catch (_: Exception) {}; try { extractorToRelease?.release() } catch (_: Exception) {} }
        }.start()
    }
    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    @Deprecated("Deprecated API retained for compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQUEST_SAVE_AUDIO) {
            synthesizeRecapAudio(uri)
            return
        }
        if (requestCode == REQUEST_SAVE_OVERLAY_MP4) {
            startSubtitleMp4Export(uri)
            return
        }
        if (requestCode == REQUEST_TRANSCRIPT) {
            Thread {
                try {
                    val imported = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                        ?: throw IllegalStateException("ဖိုင်ဖတ်မရပါ။")
                    val cues = SrtTranscript.parse(imported)
                    val cleaned = if (cues.isNotEmpty()) cues.joinToString("\n") { it.text } else imported
                        .replace(Regex("(?m)^\\s*\\d+\\s*$"), "")
                        .replace(Regex("(?m)^\\s*\\d{1,2}:\\d{2}:\\d{2}[,.]\\d{3}\\s*-->.*$"), "")
                        .replace(Regex("(?m)^\\s*\\d{1,2}:\\d{2}[,.]\\d{3}\\s*-->.*$"), "")
                        .replace(Regex("<[^>]+>"), "")
                        .lines().filter { it.isNotBlank() }.joinToString("\n").trim()
                    if (cleaned.length < 20) throw IllegalStateException("ဖိုင်ထဲတွင် အသုံးပြုနိုင်သော စာသား မလုံလောက်ပါ။")
                    runOnUiThread {
                        importedSrtCues = cues
                        importedSrtSource = cleaned
                        transcriptInput.setText(cleaned)
                        status.text = if (cues.isNotEmpty())
                            "SRT ဖိုင်ထည့်ပြီးပါပြီ။ ${cues.size} cues ၏ မူရင်း timestamp များကို ထိန်းသိမ်းထားသည်။"
                        else "Transcript ဖိုင်ကို ထည့်ပြီးပါပြီ။ စာသား ${cleaned.length} လုံး ရှိသည်။"
                    }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Transcript ဖိုင်ဖတ်မရပါ: ${e.localizedMessage ?: "Error"}" }
                }
            }.start()
            return
        }
        if (requestCode == REQUEST_SAVE_DECODED_WAV) {
            val source = pendingDecodedWav ?: return
            Thread {
                try {
                    contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                        ?: throw IllegalStateException("Output stream unavailable")
                    runOnUiThread { status.text = "PCM WAV ဖိုင် သိမ်းပြီးပါပြီ။ ဒီဖိုင်ကို နောက်တစ်ဆင့် Local Speech-to-Text Engine မှာ အသုံးပြုနိုင်ပါမည်။" }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "WAV ဖိုင် သိမ်းမရပါ: " + (e.localizedMessage ?: "Error") }
                } finally { source.delete(); pendingDecodedWav = null }
            }.start()
            return
        }
        if (requestCode == REQUEST_SAVE_EXTRACTED_AUDIO) {
            val source = pendingExtractedAudio ?: return
            Thread {
                try {
                    contentResolver.openOutputStream(uri)?.use { output -> source.inputStream().use { it.copyTo(output) } }
                        ?: throw IllegalStateException("Output stream unavailable")
                    runOnUiThread { status.text = "Video Audio Track ကို M4A ဖိုင်အဖြစ် သိမ်းပြီးပါပြီ။" }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "အသံဖိုင် သိမ်းမရပါ: " + (e.localizedMessage ?: "Error") }
                } finally { source.delete(); pendingExtractedAudio = null }
            }.start()
            return
        }
        if (requestCode == REQUEST_SAVE_SRT) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(latestSrt.toByteArray(Charsets.UTF_8)) }
                    ?: throw IllegalStateException("Output stream unavailable")
                status.text = "SRT Subtitle သိမ်းပြီးပါပြီ။ Timing သည် ခန့်မှန်းထားသော narration timing ဖြစ်ပြီး မူရင်းရုပ်ရှင်နှင့် အလိုအလျောက် sync မဖြစ်သေးပါ။"
            } catch (e: Exception) {
                status.text = "SRT သိမ်းမရပါ: " + (e.localizedMessage ?: "Error")
            }
            return
        }
        if (requestCode == REQUEST_SAVE) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(latestRecap.toByteArray(Charsets.UTF_8)) }
                status.text = "TXT ဖိုင်အဖြစ် သိမ်းပြီးပါပြီ။"
            } catch (e: Exception) {
                status.text = "ဖိုင်သိမ်းမရပါ: " + (e.localizedMessage ?: "Error")
            }
            return
        }
        if (requestCode != REQUEST_VIDEO) return
        selectedVideoUri = uri
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { }
        val name = queryName(uri) ?: "ရွေးထားသော Video"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            selectedInfo.text = "$name\nကြာချိန်: ${formatDuration(duration)}"
            status.text = "Video ရွေးပြီးပါပြီ။ Transcript ကို ထည့်ပြီး Offline Recap လုပ်ပါ။"
        } catch (e: Exception) {
            selectedInfo.text = name
            status.text = "Video metadata ဖတ်မရပါ: ${e.localizedMessage ?: "Unknown error"}"
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun queryName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getString(0) else null
        } catch (_: Exception) { null } finally { cursor?.close() }
    }

    private fun formatDuration(ms: Long): String {
        val t = ms / 1000
        return if (t >= 3600) String.format(Locale.US, "%d:%02d:%02d", t / 3600, (t % 3600) / 60, t % 60)
        else String.format(Locale.US, "%02d:%02d", t / 60, t % 60)
    }

    companion object { private const val REQUEST_TRANSCRIPT = 1007; private const val REQUEST_VIDEO = 1001; private const val REQUEST_SAVE = 1002; private const val REQUEST_SAVE_AUDIO = 1003; private const val REQUEST_SAVE_EXTRACTED_AUDIO = 1004; private const val REQUEST_SAVE_DECODED_WAV = 1005; private const val REQUEST_SAVE_SRT = 1006; private const val REQUEST_SAVE_OVERLAY_MP4 = 1008 }
}
