package com.kaung139abc.hyperrecap

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.graphics.Bitmap
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaFormat
import android.media.MediaCodec
import android.media.MediaPlayer
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
import android.widget.ImageView
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
    private var latestHighlightCues: List<SrtTranscript.Cue> = emptyList()
    @Volatile private var latestVisualContext: String = ""
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
    private var pendingNarratedMp4Destination: Uri? = null
    private var pendingNarrationWav: File? = null
    private var pendingNarrationChunks: List<String> = emptyList()
    private var pendingNarrationChunkFiles: MutableList<File> = mutableListOf()
    private var pendingNarrationChunkIndex: Int = 0
    private var activeTransformer: Transformer? = null
    private var latestMyanmarNeuralWav: File? = null
    private var neuralPlayer: MediaPlayer? = null
    @Volatile private var neuralVoiceInProgress = false

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

        fun sectionTitle(text: String) = TextView(this).apply {
            this.text = text
            textSize = 13f
            setTextColor(0xFF9FE9E0.toInt())
            typeface = android.graphics.Typeface.create("sans-serif-bold", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.06f
            gravity = Gravity.START or Gravity.CENTER_VERTICAL
            setPadding(14, 13, 14, 13)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF121B2D.toInt())
                cornerRadius = 13f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xFF354565.toInt())
            }
        }

        val brandHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(14, 14, 14, 14)
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFF151D30.toInt())
                cornerRadius = 22f * resources.displayMetrics.density
                setStroke((1 * resources.displayMetrics.density).toInt().coerceAtLeast(1), 0xFF46527A.toInt())
            }
        }
        brandHeader.addView(ImageView(this).apply {
            setImageResource(R.drawable.hyper_recap_logo)
            contentDescription = "Hyper Recap AI Logo"
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }, LinearLayout.LayoutParams((76 * resources.displayMetrics.density).toInt(), (76 * resources.displayMetrics.density).toInt()))
        val brandText = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding((14 * resources.displayMetrics.density).toInt(), 0, 0, 0)
        }
        brandText.addView(label("HYPER RECAP AI", 23f, 0xFFC5BCFF.toInt()).apply {
            gravity = Gravity.START
            typeface = android.graphics.Typeface.create("sans-serif-black", android.graphics.Typeface.NORMAL)
            letterSpacing = 0.035f
        })
        brandText.addView(label("RECAP  •  TRANSLATE  •  DUB", 10f, 0xFFB8C0D9.toInt()).apply {
            gravity = Gravity.START
        })
        brandHeader.addView(brandText, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        root.addView(brandHeader, matchWidth())
        root.addView(space(10))
        root.addView(sectionTitle("01  •  LICENSE & ACCOUNT"), matchWidth())
        val licenseKeyInput = EditText(this).apply {
            hint = "License Key ထည့်ပါ"
            setHintTextColor(0xFF9AA4BC.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setSingleLine(true)
            setPadding(14, 12, 14, 12)
            setBackgroundColor(0xFF252B3A.toInt())
            setText(LicenseManager.savedKey(this@MainActivity))
        }
        root.addView(licenseKeyInput, matchWidth())
        val licenseStatus = label("License မစစ်ဆေးရသေးပါ", 12f, 0xFFFFD28A.toInt())
        root.addView(licenseStatus, matchWidth())
        root.addView(Button(this).apply {
            text = "Activate / Verify License"
            setOnClickListener {
                val key = licenseKeyInput.text.toString().trim()
                if (key.isBlank()) {
                    licenseStatus.text = "License Key ထည့်ပါ။"
                } else {
                    licenseStatus.text = "License စစ်ဆေးနေပါသည်..."
                    Thread {
                        val result = LicenseManager.activate(this@MainActivity, key)
                        runOnUiThread {
                            licenseStatus.text = if (result.valid) "✓ ${result.message} — ${result.plan}" else "✕ ${result.message}"
                        }
                    }.start()
                }
            }
        }, matchWidth())
        root.addView(space(16))

        root.addView(sectionTitle("02  •  VIDEO SOURCE"), matchWidth())
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
            setOnClickListener { requireLicense { downloadDirectVideo(linkInput.text.toString().trim()) } }
        }
        root.addView(linkButton, matchWidth())
        root.addView(Button(this).apply {
            text = "YouTube / Facebook / TikTok / Instagram Link ဖွင့်ရန်"
            setOnClickListener { openSocialLink(linkInput.text.toString()) }
        }, matchWidth())

        root.addView(sectionTitle("03  •  AUTOMATIC RECAP"), matchWidth())
        val choose = Button(this).apply {
            text = "ဖုန်းထဲက Video ရွေးရန်"
            setOnClickListener { chooseVideo() }
        }
        root.addView(choose, matchWidth())
        root.addView(Button(this).apply {
            text = "✨ AUTO RECAP — Video မှ ဇာတ်လမ်းအကျဉ်း အလိုအလျောက်ထုတ်ရန်"
            setOnClickListener {
                if (selectedVideoUri == null) {
                    status.text = "အရင်ဆုံး ဖုန်းထဲက Video ရွေးပါ။"
                    android.widget.Toast.makeText(this@MainActivity, status.text, android.widget.Toast.LENGTH_LONG).show()
                } else {
                    transcribeSelectedVideo()
                }
            }
        }, matchWidth())
        root.addView(label("Video ရွေးပြီး AUTO RECAP ကိုနှိပ်ရုံပါ။ အသံကို စာသားပြောင်းပြီး Recap ကို အလိုအလျောက်ဖန်တီးပေးမည်။", 12f, 0xFF20C9B5.toInt()))

        selectedInfo = label("Video မရွေးရသေးပါ", 16f)
        selectedInfo.setPadding(0, 24, 0, 16)
        root.addView(selectedInfo)

        root.addView(sectionTitle("04  •  AI TOOLS"), matchWidth())
        root.addView(Button(this).apply {
            text = "Cloud AI Generative Movie Recap (Internet လိုအပ်)"
            setOnClickListener { requireLicense { generateCloudAiRecap() } }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "English → Myanmar AI Translation (Internet လိုအပ်)"
            setOnClickListener { requireLicense { translateTranscriptToMyanmar() } }
        }, matchWidth())
        root.addView(sectionTitle("05  •  RECAP RESULT & EXPORT"), matchWidth())
        // Internal transcript buffer: populated automatically by Whisper; not shown as a required manual input.
        transcriptInput = EditText(this)
        root.addView(label("YOUR RECAP", 18f, 0xFF8EA5FF.toInt()))
        recapOutput = label("Video ကိုရွေးပြီး AUTO RECAP ကိုနှိပ်ပါ။ ရလဒ် ဒီနေရာမှာ ပေါ်လာပါမည်။", 14f)
        recapOutput.gravity = Gravity.START
        root.addView(recapOutput, matchWidth())
        root.addView(Button(this).apply {
            text = "Recap TXT သိမ်းရန်"
            setOnClickListener { requireLicense { saveRecap() } }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "Recap Subtitle SRT ထုတ်ရန်"
            setOnClickListener { requireLicense { saveSubtitles() } }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "မြန်မာစာတန်းထိုး MP4 ထုတ်ရန် (Video ထဲ တစ်ခါတည်းထည့်)"
            setOnClickListener { requireLicense { requestSubtitleMp4Destination() } }
        }, matchWidth())

        root.addView(sectionTitle("06  •  VOICE STUDIO"), matchWidth())
        root.addView(space(6))
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
            setOnClickListener { requireLicense { speakRecap() } }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "အသံစမ်းနားထောင်ရန်"
            setOnClickListener { requireLicense { previewVoice() } }
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
                    startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
                    status.text = "TTS Voice Data ထည့်သွင်းရန် Settings ကို ဖွင့်ထားပါသည်။ Burmese / မြန်မာ အသံ Data ရှိမရှိ စစ်ဆေးပါ။"
                } catch (_: Exception) {
                    status.text = "ဖုန်း Settings > Additional settings > Languages & input > Text-to-speech output ထဲဝင်ပြီး Burmese / မြန်မာ Voice Data ရှိမရှိ စစ်ဆေးပါ။"
                    android.widget.Toast.makeText(this@MainActivity, status.text.toString(), android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }, matchWidth())

        root.addView(Button(this).apply {
            text = "Recap အသံဖိုင် WAV သိမ်းရန်"
            setOnClickListener { requireLicense { saveRecapAudio() } }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "မြန်မာ Neural AI Voice (Commercial Model မရသေး)"
            setOnClickListener { requireLicense { status.text = "လက်ရှိ MMS မြန်မာအသံ Model သည် CC BY-NC 4.0 ဖြစ်၍ စီးပွားဖြစ်အသုံးပြုခွင့်မရှိပါ။ Commercial License ရရှိသော Model ထည့်သွင်းပြီးမှ ဖွင့်ပေးပါမည်။" } }
        }, matchWidth())
        root.addView(Button(this).apply {
            text = "မြန်မာ Neural Voice WAV သိမ်းရန် (မရသေး)"
            setOnClickListener { requireLicense { status.text = "စီးပွားဖြစ်အသုံးပြုခွင့်ရှိသော မြန်မာ Neural TTS Model မထည့်ရသေးပါ။" } }
        }, matchWidth())
        root.addView(label("မြန်မာ Neural TTS ကို စီးပွားဖြစ်ခွင့်ရှိသော Model ရရှိသည်အထိ ပိတ်ထားသည်။ လက်ရှိအသံရွေးချယ်မှုတွင် ဖုန်း၏ TTS Engine အသံများကို အသုံးပြုပါ။", 11f, 0xFFFFD28A.toInt()))

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
        applyPremiumTheme(root)
        setContentView(scroll)
    }

    private fun applyPremiumTheme(root: LinearLayout) {
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val violet = 0xFF8B7CFF.toInt()
        val mint = 0xFF20C9B5.toInt()
        val panel = 0xFF151D30.toInt()
        val muted = 0xFFAAB6D0.toInt()

        fun rounded(fill: Int, stroke: Int, radius: Float = 18f) =
            android.graphics.drawable.GradientDrawable().apply {
                shape = android.graphics.drawable.GradientDrawable.RECTANGLE
                cornerRadius = dp(radius.toInt()).toFloat()
                setColor(fill)
                setStroke(dp(1).coerceAtLeast(1), stroke)
            }

        root.setPadding(dp(18), dp(26), dp(18), dp(28))
        root.setBackgroundColor(0xFF090E1B.toInt())
        root.clipToPadding = false

        // Brand header is a compact logo card; keep its artwork and typography untouched.
        val brandHeader = root.getChildAt(0) as? LinearLayout
        brandHeader?.elevation = dp(3).toFloat()
        val title = (brandHeader?.getChildAt(1) as? LinearLayout)?.getChildAt(0) as? TextView
        title?.apply {
            textSize = 23f
            setTextColor(0xFFC5BCFF.toInt())
            setPadding(0, 0, 0, dp(3))
        }
        val tagline = (brandHeader?.getChildAt(1) as? LinearLayout)?.getChildAt(1) as? TextView
        tagline?.setTextColor(muted)

        fun style(view: View) {
            when (view) {
                is Button -> {
                    val caption = view.text?.toString().orEmpty()
                    val primary = caption.contains("Activate") ||
                        caption.contains("Generate") ||
                        caption.contains("ရွေးရန်") ||
                        caption.contains("Download") ||
                        caption.contains("Translation") ||
                        caption.contains("Recap ဖတ်ရန်") ||
                        caption.contains("Generate Offline")
                    view.background = rounded(
                        if (primary) 0xFF5144B8.toInt() else 0xFF1A263D.toInt(),
                        if (primary) mint else 0xFF354565.toInt(),
                        15f
                    )
                    view.backgroundTintList = null
                    view.setTextColor(0xFFF7F8FF.toInt())
                    view.textSize = 13.5f
                    view.typeface = android.graphics.Typeface.create(
                        "sans-serif-medium", android.graphics.Typeface.NORMAL
                    )
                    view.isAllCaps = false
                    view.minHeight = dp(50)
                    view.setPadding(dp(15), dp(10), dp(15), dp(10))
                    view.elevation = dp(2).toFloat()
                    (view.layoutParams as? LinearLayout.LayoutParams)?.let {
                        it.bottomMargin = dp(8)
                        view.layoutParams = it
                    }
                }
                is EditText -> {
                    view.background = rounded(0xFF151E31.toInt(), 0xFF46577A.toInt(), 14f)
                    view.setTextColor(0xFFF5F7FF.toInt())
                    view.setHintTextColor(0xFF7F8BA7.toInt())
                    view.textSize = 14f
                    view.setPadding(dp(15), dp(13), dp(15), dp(13))
                    view.backgroundTintList = null
                }
                is TextView -> {
                    if (view.textSize >= 18f) {
                        view.setTypeface(android.graphics.Typeface.create(
                            "sans-serif-bold", android.graphics.Typeface.NORMAL
                        ))
                        view.setTextColor(if (view.text.toString().contains("HYPER")) 0xFFC5BCFF.toInt() else 0xFF83E6D8.toInt())
                        view.setPadding(0, dp(16), 0, dp(9))
                    } else if (view !== title) {
                        view.setTextColor(if (view.text.toString().contains("မရွေးရသေး")) 0xFFB9C5DE.toInt() else muted)
                    }
                }
                is Spinner -> {
                    view.background = rounded(panel, 0xFF354565.toInt(), 13f)
                    view.setPadding(dp(8), dp(4), dp(8), dp(4))
                }
                is SeekBar -> {
                    view.progressTintList = android.content.res.ColorStateList.valueOf(mint)
                    view.thumbTintList = android.content.res.ColorStateList.valueOf(violet)
                    view.progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFF35415A.toInt())
                }
            }
        }

        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i)
            style(child)
            if (child is android.view.ViewGroup) {
                for (j in 0 until child.childCount) style(child.getChildAt(j))
            }
        }
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }
    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )


    private fun requireLicense(action: () -> Unit) {
        status.text = "License အတည်ပြုနေပါသည်..."
        android.widget.Toast.makeText(this, "License စစ်ဆေးနေပါသည်...", android.widget.Toast.LENGTH_SHORT).show()
        Thread {
            val result = LicenseManager.check(this)
            runOnUiThread {
                if (result.valid) {
                    status.text = "License OK — ${result.plan}"
                    android.widget.Toast.makeText(this, "License အတည်ပြုပြီးပါပြီ", android.widget.Toast.LENGTH_SHORT).show()
                    action()
                } else {
                    val message = when (result.message) {
                        "LICENSE_REQUIRED" -> "License Key မထည့်ရသေးပါ။ အပေါ်ဘက်ရှိ Activate / Verify License မှာ Key ထည့်ပြီး Activate လုပ်ပါ။"
                        else -> "အသုံးပြုခွင့်မရှိပါ: ${result.message}"
                    }
                    status.text = message
                    android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_LONG).show()
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Hyper Recap AI — License")
                        .setMessage(message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    override fun onResume() {
        super.onResume()
        val savedLicense = LicenseManager.savedKey(this)
        if (savedLicense.isNotBlank()) {
            Thread {
                val result = LicenseManager.check(this)
                runOnUiThread {
                    if (::status.isInitialized && !result.valid) status.text = "License အခြေအနေ: ${result.message}"
                }
            }.start()
        }
        // Refresh voice data after returning from Android TTS settings.
        if (tts != null && ::voicePicker.isInitialized) {
            try {
                val voices = tts?.voices ?: emptySet()
                val excluded = listOf("thiha", "nilar", "သီဟ", "နီလာ")
                availableVoices = voices.filter { voice ->
                    val identity = (voice.name + " " + voice.locale.displayName).lowercase(Locale.ROOT)
                    voice.locale.language == "my" && excluded.none { identity.contains(it) }
                }.sortedWith(compareBy<Voice>(
                    { if (it.isNetworkConnectionRequired) 1 else 0 },
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
                    if (utteranceId == "hyper_auto_narration" || utteranceId?.startsWith("hyper_auto_narration_chunk_") == true) {
                        pendingNarrationWav?.delete()
                        pendingNarrationChunkFiles.forEach { it.delete() }
                        pendingNarrationChunkFiles.clear()
                        pendingNarrationChunks = emptyList()
                        pendingNarrationChunkIndex = 0
                        pendingNarrationWav = null
                        pendingNarratedMp4Destination = null
                    }
                    runOnUiThread {
                        status.text = if (utteranceId == "hyper_auto_narration")
                            "မြန်မာ TTS က Narration WAV မထုတ်နိုင်ပါ။ Burmese voice data/Internet ကို စစ်ဆေးပါ။"
                        else "အသံဖိုင်ဖန်တီးရာတွင် အမှားဖြစ်နေပါသည်။"
                    }
                    pendingAudioFile?.delete()
                    pendingAudioFile = null
                    pendingAudioUri = null
                }
                override fun onDone(utteranceId: String?) {
                    if (utteranceId?.startsWith("hyper_auto_narration_chunk_") == true) {
                        val completedIndex = utteranceId.substringAfterLast("_").toIntOrNull() ?: return
                        if (completedIndex != pendingNarrationChunkIndex) return
                        pendingNarrationChunkIndex++
                        if (pendingNarrationChunkIndex < pendingNarrationChunks.size) {
                            try {
                                synthesizeNextNarrationChunk()
                            } catch (e: Exception) {
                                cleanupPendingNarration()
                                runOnUiThread { status.text = "နောက်ထပ် အသံအပိုင်းဖန်တီးမရပါ: " + (e.localizedMessage ?: "TTS error") }
                            }
                        } else {
                            val wav = pendingNarrationWav ?: return
                            val destination = pendingNarratedMp4Destination ?: return
                            Thread {
                                try {
                                    mergeNarrationWavChunks(pendingNarrationChunkFiles, wav)
                                    cleanupPendingNarration(keepFinalWav = true)
                                    runOnUiThread { exportNarratedMp4(destination, wav) }
                                } catch (e: Exception) {
                                    wav.delete()
                                    cleanupPendingNarration()
                                    runOnUiThread { status.text = "Narration WAV ပေါင်းစပ်မရပါ: " + (e.localizedMessage ?: "WAV error") }
                                }
                            }.start()
                        }
                        return
                    }
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
                // Burmese-only voice list: never offer other-language fallback voices.
                availableVoices = (tts?.voices ?: emptySet())
                    .filter { voice ->
                        val identity = (voice.name + " " + voice.locale.displayName).lowercase(Locale.ROOT)
                        voice.locale.language == "my" && excluded.none { identity.contains(it) }
                    }
                    .sortedWith(compareBy<Voice>({ if (it.isNetworkConnectionRequired) 1 else 0 }, { -it.quality }, { it.name }))
                val labels = availableVoices.map { voice ->
                    val mode = if (voice.isNetworkConnectionRequired) "Online" else "Offline"
                    "${voice.locale.displayName} — ${voice.name} — $mode — Quality ${voice.quality}"
                }
                if (labels.isEmpty()) {
                    voicePicker.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("အသုံးပြုနိုင်သော အသံမရှိပါ"))
                    voiceInfo.text = "မြန်မာဘာသာ TTS အသံ မတွေ့ပါ။ အခြားဘာသာအသံကို အစားထိုးမသုံးပါ။ လူသားဆန်သော မြန်မာ Neural Voice ထည့်သွင်းရန် လိုအပ်နေပါသည်။"
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
                    voiceInfo.text = "မြန်မာဘာသာအသံ ${labels.size} မျိုးသာ ပြထားသည်။ လူသားဆန်မှုနှင့် အသံကြည်လင်မှုသည် ဖုန်း TTS Engine ပေါ်မူတည်ပါသည်။"
                }
            } catch (e: Exception) {
                voiceInfo.text = "အသံစာရင်း ဖတ်မရပါ: ${e.localizedMessage ?: "Error"}"
            }
        }
    }



    private fun generateMyanmarNeuralVoice() {
        if (latestRecap.isBlank()) {
            status.text = "အရင်ဆုံး Recap Generate လုပ်ပါ။"
            return
        }
        if (neuralVoiceInProgress) {
            status.text = "မြန်မာ Neural Voice ဖန်တီးနေဆဲပါ။"
            return
        }
        neuralVoiceInProgress = true
        val textToSpeak = latestRecap
        val output = File(cacheDir, "hyper_myanmar_neural_" + System.currentTimeMillis() + ".wav")
        status.text = "မြန်မာ Neural Voice Model ကို ပြင်ဆင်နေပါသည်..."
        Thread {
            try {
                MyanmarNeuralVoice.synthesize(this, textToSpeak, output) { message ->
                    runOnUiThread { status.text = message }
                }
                runOnUiThread {
                    latestMyanmarNeuralWav?.delete()
                    latestMyanmarNeuralWav = output
                    playMyanmarNeuralVoice(output)
                }
            } catch (e: Exception) {
                output.delete()
                runOnUiThread {
                    status.text = "မြန်မာ Neural Voice မအောင်မြင်ပါ: " + (e.localizedMessage ?: "Model/Audio error")
                }
            } finally {
                neuralVoiceInProgress = false
            }
        }.start()
    }

    private fun playMyanmarNeuralVoice(file: File) {
        try {
            neuralPlayer?.release()
            val player = MediaPlayer()
            neuralPlayer = player
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener {
                it.start()
                status.text = "မြန်မာ Neural AI Voice ဖြင့် Recap ဖတ်နေပါသည်။"
            }
            player.setOnCompletionListener {
                it.release()
                if (neuralPlayer === it) neuralPlayer = null
                status.text = "မြန်မာ Neural Voice ဖတ်ပြီးပါပြီ။ WAV သိမ်းနိုင်ပါသည်။"
            }
            player.setOnErrorListener { mp, _, _ ->
                mp.release()
                if (neuralPlayer === mp) neuralPlayer = null
                status.text = "Neural Voice အသံဖွင့်မရပါ။ WAV သိမ်းပြီး အခြား Player ဖြင့် စမ်းပါ။"
                true
            }
            player.prepareAsync()
        } catch (e: Exception) {
            neuralPlayer?.release()
            neuralPlayer = null
            status.text = "Neural Voice ဖွင့်မရပါ: " + (e.localizedMessage ?: "Player error")
        }
    }

    private fun saveMyanmarNeuralWav() {
        val source = latestMyanmarNeuralWav
        if (source == null || !source.isFile) {
            status.text = "အရင်ဆုံး မြန်မာ Neural Voice ဖန်တီးပါ။"
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "audio/wav"
            putExtra(Intent.EXTRA_TITLE, "HyperRecap-Myanmar-Neural-Voice.wav")
        }
        startActivityForResult(intent, REQUEST_SAVE_MYANMAR_NEURAL_WAV)
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
        neuralPlayer?.release()
        neuralPlayer = null
        super.onDestroy()
    }

    private fun generateCloudAiRecap() {
        val source = transcriptInput.text.toString().trim()
        if (source.length < 80) {
            status.text = "AI Recap အတွက် Transcript အနည်းဆုံး စာလုံး ၈၀ ခန့် လိုအပ်ပါသည်။ အရင်ဆုံး Video ကို Whisper ဖြင့် Transcribe လုပ်ပါ။"
            return
        }
        status.text = "Cloud AI Generative Recap စတင်နေပါသည်။ Internet လိုအပ်ပြီး အသုံးပြုမှုအလိုက် Cloudflare AI quota ကန့်သတ်ချက်ရှိနိုင်သည်။"
        Thread {
            try {
                val result = CloudAiClient.generateBurmeseRecap(this, source, latestVisualContext) { message ->
                    runOnUiThread { status.text = message }
                }
                runOnUiThread {
                    latestRecap = result
                    latestSrt = SubtitleDraft.fromNarration(result)
                    recapOutput.text = result
                    status.text = "Cloud AI မြန်မာ Movie Recap ပြီးပါပြီ။ SRT timing သည် narration ခန့်မှန်းချိန်ဖြစ်သည်။"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "Cloud AI Recap မအောင်မြင်ပါ: ${e.localizedMessage ?: "Server/Quota error"}" }
            }
        }.start()
    }

    private fun translateTranscriptToMyanmar() {
        val source = transcriptInput.text.toString().trim()
        if (source.length < 30) {
            status.text = "ဘာသာပြန်ရန် English Transcript စာသား ထည့်ပါ။"
            return
        }
        status.text = "English → Myanmar AI Translation စတင်နေပါသည်။ Internet လိုအပ်သည်။"
        Thread {
            try {
                val result = CloudAiClient.translateEnglishToMyanmar(this, source) { message ->
                    runOnUiThread { status.text = message }
                }
                runOnUiThread {
                    transcriptInput.setText(result)
                    importedSrtCues = emptyList()
                    importedSrtSource = ""
                    status.text = "မြန်မာဘာသာပြန်ပြီးပါပြီ။ ဘာသာပြန်စာသားကို ပြန်စစ်ပြီး Recap ထုတ်နိုင်ပါပြီ။"
                }
            } catch (e: Exception) {
                runOnUiThread { status.text = "ဘာသာပြန်မအောင်မြင်ပါ: ${e.localizedMessage ?: "Server/Quota error"}" }
            }
        }.start()
    }

    private fun generateRecap() {
        val source = transcriptInput.text.toString().trim()
        if (source.length < 80) {
            status.text = "Transcript စာသား အနည်းဆုံး စာလုံး ၈၀ ခန့် လိုအပ်ပါသည်။"
            return
        }
        status.text = "ဇာတ်လမ်း Transcript ကို စိစစ်ပြီး Recap ဖန်တီးနေပါသည်..."
        recapOutput.text = "AI Recap ပြင်ဆင်နေပါသည်။ ခဏစောင့်ပါ..."
        Thread {
            try {
                val sourceCues = importedSrtCues
                val highlights = if (sourceCues.isNotEmpty()) SrtTranscript.summarize(sourceCues, 12) else emptyList()
                val localDraft = if (highlights.isNotEmpty()) {
                    highlights.joinToString("\n\n") { it.text }
                } else {
                    OfflineRecap.summarize(source, 12)
                }
                if (localDraft.isBlank()) throw IllegalStateException("Local Recap အလွတ်ဖြစ်နေပါသည်။")

                // Licensed users get story-focused generative synthesis and sampled visual context.
                // Without a valid license or internet, the app safely falls back to the on-device draft.
                var finalRecap = localDraft
                var recapMode = "Local Offline Recap"
                var recapVisualContext = ""
                val savedKey = LicenseManager.savedKey(this)
                if (savedKey.isNotBlank()) {
                    var visualNotes = ""
                    val video = selectedVideoUri
                    if (video != null) {
                        try {
                            runOnUiThread { status.text = "AI ရုပ်ပုံနားလည်မှု — ဇာတ်လမ်းတစ်လျှောက် Scene များကို စိစစ်နေပါသည်..." }
                            visualNotes = analyzeVideoFrames(video) { message -> runOnUiThread { status.text = message } }
                        } catch (visionError: Exception) {
                            // Vision is an enhancement, not a blocker for transcript-based generative recap.
                            runOnUiThread { status.text = "ရုပ်ပုံ AI မရနိုင်ပါ။ Transcript ကိုအခြေခံပြီး Generative Recap ဆက်လုပ်နေပါသည်..." }
                        }
                    }
                    recapVisualContext = visualNotes
                    try {
                        runOnUiThread { status.text = "Generative AI က ဇာတ်လမ်းအစမှ အဆုံးအထိ မြန်မာ Recap ရေးသားနေပါသည်..." }
                        finalRecap = CloudAiClient.generateBurmeseRecap(this, source, visualNotes) { message ->
                            runOnUiThread { status.text = message }
                        }
                        if (finalRecap.isNotBlank()) {
                            recapMode = if (visualNotes.isNotBlank()) "Cloud Generative AI + Visual Scene Analysis"
                                else "Cloud Generative AI (Transcript-based)"
                        }
                    } catch (cloudError: Exception) {
                        runOnUiThread {
                            status.text = "Generative AI မရနိုင်သဖြင့် Offline Recap သို့ ပြောင်းနေပါသည်..."
                        }
                    }
                }
                if (finalRecap.isBlank()) finalRecap = localDraft
                val result = finalRecap
                val mode = recapMode
                runOnUiThread {
                    latestRecap = result
                    latestHighlightCues = highlights
                    latestVisualContext = recapVisualContext
                    latestSrt = SubtitleDraft.fromNarration(result)
                    recapOutput.text = result
                    status.text = "✓ $mode ပြီးပါပြီ။"
                    requestAutoNarratedMp4Destination()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Recap မအောင်မြင်ပါ: ${e.localizedMessage ?: "Processing error"}"
                }
            }
        }.start()
    }

    private fun requestAutoNarratedMp4Destination() {
        if (selectedVideoUri == null || latestRecap.isBlank()) return
        if (tts == null || availableVoices.isEmpty() || tts?.voice?.locale?.language != "my") {
            status.text = "မြန်မာ TTS အသံမရရှိပါ။ ဖုန်း Settings > Text-to-speech တွင် Burmese voice ထည့်သွင်းပြီး ပြန်ရွေးပါ။"
            return
        }
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/mp4"
            putExtra(Intent.EXTRA_TITLE, "HyperRecap-Auto-Narrated.mp4")
        }
        startActivityForResult(intent, REQUEST_AUTO_NARRATED_MP4)
    }

    private fun beginAutomaticNarrationExport(destination: Uri) {
        val engine = tts ?: run { status.text = "TTS Engine မရရှိပါ။"; return }
        if (availableVoices.isEmpty() || engine.voice?.locale?.language != "my") {
            status.text = "မြန်မာဘာသာ TTS Voice မရွေးထားပါ။ အခြားဘာသာအသံဖြင့် မြန်မာစာ မဖတ်စေပါ။"
            return
        }
        if (latestRecap.isBlank() || selectedVideoUri == null) {
            status.text = "Recap သို့မဟုတ် Video မရှိပါ။"
            return
        }
        val wav = File(cacheDir, "hyper_auto_voice_${System.currentTimeMillis()}.wav")
        pendingNarratedMp4Destination = destination
        pendingNarrationWav = wav
        pendingNarrationChunks = splitNarrationIntoChunks(latestRecap)
        pendingNarrationChunkFiles = mutableListOf()
        pendingNarrationChunkIndex = 0
        try {
            if (pendingNarrationChunks.isEmpty()) throw IllegalStateException("Recap စာသားမရှိပါ။")
            engine.setSpeechRate(0.95f)
            engine.setPitch(1.0f)
            status.text = "မြန်မာ Recap အသံ " + pendingNarrationChunks.size + " ပိုင်း ဖန်တီးနေပါသည်..."
            synthesizeNextNarrationChunk()
        } catch (e: Exception) {
            wav.delete()
            cleanupPendingNarration()
            status.text = "အသံဖန်တီးမရပါ: " + (e.localizedMessage ?: "TTS error")
        }
    }

    private fun cleanupPendingNarration(keepFinalWav: Boolean = false) {
        pendingNarrationChunkFiles.forEach { it.delete() }
        pendingNarrationChunkFiles.clear()
        pendingNarrationChunks = emptyList()
        pendingNarrationChunkIndex = 0
        if (!keepFinalWav) pendingNarrationWav?.delete()
        pendingNarrationWav = null
        pendingNarratedMp4Destination = null
    }

    private fun splitNarrationIntoChunks(text: String, maxChars: Int = 3000): List<String> {
        val chunks = mutableListOf<String>()
        var current = StringBuilder()
        for (sentence in text.trim().split(Regex("(?<=[။.!?])\\s*"))) {
            var part = sentence.trim()
            while (part.length > maxChars) {
                val cut = part.lastIndexOf(' ', maxChars).takeIf { it > maxChars / 2 } ?: maxChars
                if (current.isNotEmpty()) {
                    chunks += current.toString().trim()
                    current = StringBuilder()
                }
                chunks += part.substring(0, cut).trim()
                part = part.substring(cut).trim()
            }
            if (part.isNotEmpty()) {
                if (current.isNotEmpty() && current.length + part.length + 1 > maxChars) {
                    chunks += current.toString().trim()
                    current = StringBuilder()
                }
                if (current.isNotEmpty()) current.append(' ')
                current.append(part)
            }
        }
        if (current.isNotEmpty()) chunks += current.toString().trim()
        return chunks.filter { it.isNotBlank() }
    }

    private fun synthesizeNextNarrationChunk() {
        val engine = tts ?: throw IllegalStateException("TTS Engine မရရှိပါ။")
        val index = pendingNarrationChunkIndex
        if (index !in pendingNarrationChunks.indices) throw IllegalStateException("Narration အပိုင်း မရှိပါ။")
        val partFile = File(cacheDir, "hyper_voice_part_" + System.currentTimeMillis() + "_" + index + ".wav")
        partFile.delete()
        pendingNarrationChunkFiles += partFile
        val result = engine.synthesizeToFile(
            pendingNarrationChunks[index], Bundle(), partFile, "hyper_auto_narration_chunk_" + index
        )
        if (result != TextToSpeech.SUCCESS) {
            partFile.delete()
            pendingNarrationChunkFiles.remove(partFile)
            throw IllegalStateException("TTS က အသံအပိုင်း " + (index + 1) + " ကို မဖန်တီးနိုင်ပါ။")
        }
    }

    private data class NarrationWavPart(
        val dataOffset: Long,
        val dataSize: Long,
        val format: ByteArray,
        val channels: Int,
        val sampleRate: Int,
        val bitsPerSample: Int
    )

    private fun readNarrationWavPart(file: File): NarrationWavPart {
        java.io.RandomAccessFile(file, "r").use { input ->
            if (input.length() < 12L) throw IllegalStateException("အသံအပိုင်း WAV မပြည့်စုံပါ။")
            val riff = ByteArray(4).also(input::readFully)
            val riffSize = readWavUInt32(input)
            val wave = ByteArray(4).also(input::readFully)
            if (String(riff, Charsets.US_ASCII) != "RIFF" ||
                String(wave, Charsets.US_ASCII) != "WAVE" ||
                riffSize < 4L || riffSize > input.length() - 8L) {
                throw IllegalStateException("TTS WAV ဖိုင်ပုံစံ မမှန်ပါ။")
            }
            var format: ByteArray? = null
            var channels = 0
            var sampleRate = 0
            var bits = 0
            var dataOffset = -1L
            var dataSize = -1L
            while (input.filePointer + 8L <= input.length()) {
                val idBytes = ByteArray(4).also(input::readFully)
                val id = String(idBytes, Charsets.US_ASCII)
                val size = readWavUInt32(input)
                val chunkStart = input.filePointer
                val chunkEnd = chunkStart + size + (size and 1L)
                if (chunkEnd < chunkStart || chunkEnd > input.length()) {
                    throw IllegalStateException("WAV chunk အရွယ်အစား မမှန်ပါ။")
                }
                if (id == "fmt ") {
                    if (size < 16L || size > 65_536L) throw IllegalStateException("WAV fmt chunk မမှန်ပါ။")
                    val fmt = ByteArray(16)
                    input.readFully(fmt)
                    val buffer = ByteBuffer.wrap(fmt).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                    val encoding = buffer.short.toInt() and 0xffff
                    channels = buffer.short.toInt() and 0xffff
                    sampleRate = buffer.int
                    buffer.int // byte rate
                    buffer.short // block align
                    bits = buffer.short.toInt() and 0xffff
                    if (encoding != 1) throw IllegalStateException("PCM WAV အသံသာ ပေါင်းစပ်နိုင်ပါသည်။")
                    format = fmt
                } else if (id == "data") {
                    dataOffset = chunkStart
                    dataSize = size
                }
                input.seek(chunkEnd)
                if (format != null && dataOffset >= 0L) break
            }
            val fmt = format ?: throw IllegalStateException("WAV fmt chunk မတွေ့ပါ။")
            if (dataOffset < 0L || dataSize <= 0L || dataSize > input.length() - dataOffset ||
                channels !in 1..2 || sampleRate !in 8_000..192_000 || bits != 16) {
                throw IllegalStateException("ပံ့ပိုးမထားသော သို့မဟုတ် မပြည့်စုံသော PCM WAV ဖြစ်ပါသည်။")
            }
            return NarrationWavPart(dataOffset, dataSize, fmt, channels, sampleRate, bits)
        }
    }

    private fun readWavUInt32(input: java.io.RandomAccessFile): Long {
        val b = ByteArray(4)
        input.readFully(b)
        return (b[0].toLong() and 0xffL) or
            ((b[1].toLong() and 0xffL) shl 8) or
            ((b[2].toLong() and 0xffL) shl 16) or
            ((b[3].toLong() and 0xffL) shl 24)
    }

    private fun mergeNarrationWavChunks(parts: List<File>, destination: File) {
        if (parts.isEmpty()) throw IllegalStateException("Narration အသံအပိုင်း မရှိပါ။")
        val parsed = parts.map { part ->
            if (!part.isFile) throw IllegalStateException("အသံအပိုင်း WAV မတွေ့ပါ။")
            readNarrationWavPart(part)
        }
        val reference = parsed.first()
        if (parsed.any { !it.format.contentEquals(reference.format) }) {
            throw IllegalStateException("အသံအပိုင်းများ၏ WAV format မတူညီပါ။")
        }
        val totalData = parsed.sumOf { it.dataSize }
        if (totalData <= 0L || totalData > Int.MAX_VALUE - 44L) {
            throw IllegalStateException("Narration WAV ဖိုင် အလွန်ကြီးနေပါသည်။")
        }
        // Write a canonical 44-byte PCM header, regardless of optional RIFF chunks
        // (LIST/JUNK/fact) present in individual Android TTS output files.
        val header = ByteArray(44)
        fun putAscii(offset: Int, value: String) {
            value.toByteArray(Charsets.US_ASCII).copyInto(header, offset)
        }
        putAscii(0, "RIFF")
        ByteBuffer.wrap(header, 4, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt((36L + totalData).toInt())
        putAscii(8, "WAVE")
        putAscii(12, "fmt ")
        ByteBuffer.wrap(header, 16, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(16)
        reference.format.copyInto(header, 20, 0, 16)
        putAscii(36, "data")
        ByteBuffer.wrap(header, 40, 4).order(java.nio.ByteOrder.LITTLE_ENDIAN).putInt(totalData.toInt())
        destination.outputStream().buffered().use { output ->
            output.write(header)
            val buffer = ByteArray(64 * 1024)
            parsed.forEachIndexed { index, partInfo ->
                java.io.RandomAccessFile(parts[index], "r").use { input ->
                    input.seek(partInfo.dataOffset)
                    var remaining = partInfo.dataSize
                    while (remaining > 0L) {
                        val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count <= 0) throw IllegalStateException("WAV audio data မပြည့်စုံပါ။")
                        output.write(buffer, 0, count)
                        remaining -= count.toLong()
                    }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun exportNarratedMp4(destination: Uri, narrationWav: File) {
        val source = selectedVideoUri
        if (source == null || !narrationWav.isFile || narrationWav.length() < 44L) {
            narrationWav.delete()
            status.text = "Video သို့မဟုတ် Narration WAV မပြည့်စုံပါ။"
            return
        }
        if (activeTransformer != null) {
            narrationWav.delete()
            status.text = "MP4 Export လုပ်ဆောင်နေဆဲပါ။"
            return
        }
        val output = File(cacheDir, "hyper_narrated_${System.currentTimeMillis()}.mp4")
        try {
            // Build a short montage from recap-selected source timestamps. If no reliable
            // source timestamps exist, retain the original video rather than guessing.
            val videoItems = mutableListOf<EditedMediaItem>()
            val cues = latestHighlightCues
            if (cues.isNotEmpty()) {
                val retriever = MediaMetadataRetriever()
                val sourceDurationMs = try {
                    retriever.setDataSource(this, source)
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    retriever.release()
                }
                val narrationRetriever = MediaMetadataRetriever()
                val narrationDurationMs = try {
                    narrationRetriever.setDataSource(narrationWav.absolutePath)
                    narrationRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    narrationRetriever.release()
                }
                if (sourceDurationMs <= 0L || narrationDurationMs <= 0L) {
                    throw IllegalStateException("Video/Narration ကြာချိန်ကို ဖတ်မရပါ။")
                }
                // Match montage length to narration length. Reuse the selected story
                // highlights when the narration is longer than the available cue count.
                // Keep individual source clips within the source video's duration.
                // Very short source videos need more repeated clips to cover a long narration.
                val targetClipMs = minOf(10_000L, sourceDurationMs).coerceAtLeast(1L)
                val clipCount = ((narrationDurationMs + targetClipMs - 1L) / targetClipMs)
                    .coerceAtLeast(1L).coerceAtMost(600L).toInt()
                val chosen = (0 until clipCount).map { index ->
                    val cueIndex = if (clipCount <= cues.size) {
                        (index.toLong() * cues.size / clipCount).toInt()
                    } else {
                        index % cues.size
                    }
                    cues[cueIndex]
                }
                val perClipMs = ((narrationDurationMs + clipCount - 1L) / clipCount)
                    .coerceAtLeast(1_000L)
                chosen.forEachIndexed { index, cue ->
                    // The final clip must use only the remaining narration duration;
                    // otherwise the video sequence can outlast the narration and leave
                    // an unintended silent tail in the exported MP4.
                    val remainingMs = narrationDurationMs - index.toLong() * perClipMs
                    val clipLength = minOf(perClipMs, remainingMs).coerceAtLeast(1L)
                        .coerceAtMost(sourceDurationMs)
                    val center = ((cue.startMs + cue.endMs) / 2L)
                        .coerceIn(0L, sourceDurationMs - 1L)
                    val latestStart = (sourceDurationMs - clipLength).coerceAtLeast(0L)
                    val start = (center - clipLength / 2L).coerceIn(0L, latestStart)
                    val end = (start + clipLength).coerceAtMost(sourceDurationMs)
                    if (end > start) {
                        val clippedItem = MediaItem.Builder()
                            .setUri(source)
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(start)
                                    .setEndPositionMs(end)
                                    .build()
                            )
                            .build()
                        videoItems += EditedMediaItem.Builder(clippedItem).setRemoveAudio(true).build()
                    }
                }
            }
            if (videoItems.isEmpty()) {
                // If transcript-derived highlights are unavailable, do not export the
                // entire movie with only a short recap narration. Create a compact,
                // evenly distributed fallback montage sized to the narration instead.
                val sourceRetriever = MediaMetadataRetriever()
                val sourceDurationMs = try {
                    sourceRetriever.setDataSource(this, source)
                    sourceRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    sourceRetriever.release()
                }
                val narrationRetriever = MediaMetadataRetriever()
                val narrationDurationMs = try {
                    narrationRetriever.setDataSource(narrationWav.absolutePath)
                    narrationRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                } finally {
                    narrationRetriever.release()
                }
                if (sourceDurationMs <= 0L || narrationDurationMs <= 0L) {
                    throw IllegalStateException("Video/Narration ကြာချိန်ကို ဖတ်မရပါ။")
                }
                // Base the fallback clip count on the actual source duration.
                // Otherwise a source shorter than 10 seconds can produce a montage
                // that ends before a longer narration has finished.
                val targetClipMs = minOf(10_000L, sourceDurationMs).coerceAtLeast(1L)
                val clipCount = ((narrationDurationMs + targetClipMs - 1L) / targetClipMs)
                    .coerceAtLeast(1L).coerceAtMost(600L).toInt()
                val clipLengthMs = ((narrationDurationMs + clipCount - 1L) / clipCount)
                    .coerceAtLeast(1L).coerceAtMost(sourceDurationMs)
                for (index in 0 until clipCount) {
                    val remainingMs = narrationDurationMs - index.toLong() * clipLengthMs
                    val actualClipLengthMs = minOf(clipLengthMs, remainingMs)
                        .coerceAtLeast(1L).coerceAtMost(sourceDurationMs)
                    val fraction = (index + 0.5) / clipCount.toDouble()
                    val center = (fraction * sourceDurationMs).toLong().coerceIn(0L, sourceDurationMs - 1L)
                    val latestStart = (sourceDurationMs - actualClipLengthMs).coerceAtLeast(0L)
                    val start = (center - actualClipLengthMs / 2L).coerceIn(0L, latestStart)
                    val end = (start + actualClipLengthMs).coerceAtMost(sourceDurationMs)
                    if (end > start) {
                        val clippedItem = MediaItem.Builder()
                            .setUri(source)
                            .setClippingConfiguration(
                                MediaItem.ClippingConfiguration.Builder()
                                    .setStartPositionMs(start)
                                    .setEndPositionMs(end)
                                    .build()
                            )
                            .build()
                        videoItems += EditedMediaItem.Builder(clippedItem).setRemoveAudio(true).build()
                    }
                }
                if (videoItems.isEmpty()) {
                    throw IllegalStateException("Video မှ Export လုပ်နိုင်သော Clip မရရှိပါ။")
                }
            }
            val audioItem = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(narrationWav))).setRemoveVideo(true).build()
            val videoSequence = EditedMediaItemSequence.Builder(videoItems).build()
            val audioSequence = EditedMediaItemSequence.Builder(listOf(audioItem)).build()
            val composition = Composition.Builder(listOf(videoSequence, audioSequence)).build()
            output.createNewFile()
            val transformer = Transformer.Builder(this)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        Thread {
                            try {
                                contentResolver.openOutputStream(destination)?.use { target ->
                                    output.inputStream().use { input -> input.copyTo(target) }
                                } ?: throw IllegalStateException("MP4 သိမ်းမည့်နေရာ ဖွင့်မရပါ။")
                                runOnUiThread { status.text = if (latestHighlightCues.isNotEmpty()) "✓ မြန်မာ Recap အသံနှင့် ရွေးချယ်ထားသော Scene များပါဝင်သည့် Highlight MP4 သိမ်းပြီးပါပြီ။" else "✓ မြန်မာ Recap အသံနှင့် Video ပေါင်းထားသော MP4 သိမ်းပြီးပါပြီ။" }
                            } catch (e: Exception) {
                                runOnUiThread { status.text = "MP4 သိမ်းမရပါ: ${e.localizedMessage ?: "Storage error"}" }
                            } finally {
                                output.delete()
                                narrationWav.delete()
                                activeTransformer = null
                            }
                        }.start()
                    }
                    override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                        output.delete()
                        narrationWav.delete()
                        activeTransformer = null
                        runOnUiThread { status.text = "Narrated MP4 Export မအောင်မြင်ပါ: ${exportException.localizedMessage ?: "Encoding error"}" }
                    }
                }).build()
            activeTransformer = transformer
            status.text = "မူရင်း Video နှင့် မြန်မာ Recap အသံကို MP4 တစ်ခုတည်းအဖြစ် ပေါင်းနေပါသည်..."
            transformer.start(composition, output.absolutePath)
        } catch (e: Exception) {
            output.delete()
            narrationWav.delete()
            activeTransformer = null
            status.text = "MP4 Export စတင်မရပါ: ${e.localizedMessage ?: "Export error"}"
        }
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
        // Keep Android from sleeping while a long movie is decoded and transcribed.
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
                // Local-first workflow: the movie is understood through its full audio transcript.
                // Do not upload sampled frames or depend on cloud vision for AUTO RECAP.
                latestVisualContext = ""
                runOnUiThread {
                    importedSrtCues = cues
                    importedSrtSource = transcript
                    transcriptInput.setText(transcript)
                    latestSrt = SrtTranscript.toSrt(cues)
                    recapOutput.text = "Whisper Transcript ရရှိပါပြီ။ Recap ကို အလိုအလျောက် ဖန်တီးနေပါသည်..."
                    status.text = "Whisper Offline Transcription ပြီးပါပြီ။ ${cues.size} အပိုင်း ရရှိသည်။ မူရင်း Video အချိန်အပိုင်းအခြားများကို SRT တွင် ထိန်းသိမ်းထားသည်။"
                    if (transcript.length >= 80) {
                        generateRecap()
                    } else {
                        latestRecap = ""
                        recapOutput.text = "အသံစာသား မလုံလောက်သဖြင့် ယုံကြည်စိတ်ချရသော Recap မထုတ်နိုင်ပါ။"
                        status.text = "Whisper မှ ရရှိသော Transcript သည် Recap အတွက် မလုံလောက်ပါ။ Video အသံ/ဘာသာစကားကို စစ်ဆေးပါ။"
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    status.text = "Whisper Transcription မအောင်မြင်ပါ: ${e.localizedMessage ?: "Model/Audio error"}"
                }
            } finally {
                wav.delete()
                transcriptionInProgress = false
                runOnUiThread {
                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            }
        }.start()
    }

    /**
     * Samples up to 24 low-resolution frames across the full movie and sends
     * them in pairs to the licensed Cloudflare Vision endpoint. Frames are
     * encoded one batch at a time to keep peak memory low on 4GB devices.
     */
    private fun analyzeVideoFrames(source: Uri, onProgress: (String) -> Unit): String {
        val retriever = MediaMetadataRetriever()
        val notes = StringBuilder()
        try {
            retriever.setDataSource(this, source)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1L) ?: return ""
            val sampleCount = if (durationMs < 180_000L) 6 else 24
            val intervalMs = (durationMs / sampleCount).coerceAtLeast(30_000L)
            val batch = mutableListOf<Pair<Long, String>>()
            var sampled = 0
            var analyzed = 0
            var timeMs = intervalMs / 2
            while (timeMs < durationMs && sampled < sampleCount) {
                onProgress("AI ရုပ်ပုံနားလည်မှု — ${sampled + 1}/$sampleCount frame ပြင်ဆင်နေသည်...")
                val original = retriever.getFrameAtTime(timeMs * 1000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (original != null) {
                    try {
                        val scale = minOf(256f / original.width, 256f / original.height, 1f)
                        val width = (original.width * scale).toInt().coerceAtLeast(1)
                        val height = (original.height * scale).toInt().coerceAtLeast(1)
                        val small = Bitmap.createScaledBitmap(original, width, height, true)
                        try {
                            val bytes = ByteArrayOutputStream()
                            small.compress(Bitmap.CompressFormat.JPEG, 42, bytes)
                            val encoded = Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
                            if (encoded.length <= 50_000) batch += timeMs to encoded
                        } finally {
                            if (small !== original) small.recycle()
                        }
                    } finally {
                        original.recycle()
                    }
                }
                sampled++
                if (batch.size == 2) {
                    onProgress("AI ရုပ်ပုံနားလည်မှု — ${analyzed + 1}/$sampleCount frame များကို စိစစ်နေသည်...")
                    val description = CloudAiClient.analyzeVideoFrames(this, batch.map { it.second }, batch.map { it.first })
                    batch.forEach { (at, _) -> notes.append("[${formatDuration(at)}] ") }
                    notes.append(description).append("\n")
                    analyzed += batch.size
                    batch.clear()
                }
                timeMs += intervalMs
            }
            if (batch.isNotEmpty()) {
                val description = CloudAiClient.analyzeVideoFrames(this, batch.map { it.second }, batch.map { it.first })
                batch.forEach { (at, _) -> notes.append("[${formatDuration(at)}] ") }
                notes.append(description).append("\n")
                analyzed += batch.size
            }
            onProgress(if (analyzed > 0) "AI ရုပ်ပုံစိစစ်ပြီးပါပြီ — $analyzed frames" else "ဖတ်ရှုနိုင်သော Video frame မတွေ့ပါ။")
            return notes.toString().take(12_000)
        } finally {
            try { retriever.release() } catch (_: Exception) {}
        }
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
        if (requestCode == REQUEST_AUTO_NARRATED_MP4) {
            beginAutomaticNarrationExport(uri)
            return
        }
        if (requestCode == REQUEST_SAVE_MYANMAR_NEURAL_WAV) {
            val source = latestMyanmarNeuralWav ?: return
            Thread {
                try {
                    contentResolver.openOutputStream(uri)?.use { target ->
                        source.inputStream().use { input -> input.copyTo(target) }
                    } ?: throw IllegalStateException("WAV သိမ်းမည့်နေရာ ဖွင့်မရပါ။")
                    runOnUiThread { status.text = "မြန်မာ Neural Voice WAV သိမ်းပြီးပါပြီ။" }
                } catch (e: Exception) {
                    runOnUiThread { status.text = "Neural WAV သိမ်းမရပါ: " + (e.localizedMessage ?: "Error") }
                }
            }.start()
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
        latestVisualContext = ""
        latestRecap = ""
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { }
        val name = queryName(uri) ?: "ရွေးထားသော Video"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            selectedInfo.text = "$name\nကြာချိန်: ${formatDuration(duration)}"
            status.text = "Video ရွေးပြီးပါပြီ။ AUTO RECAP ကိုနှိပ်ပါ။"
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

    companion object { private const val REQUEST_TRANSCRIPT = 1007; private const val REQUEST_VIDEO = 1001; private const val REQUEST_SAVE = 1002; private const val REQUEST_SAVE_AUDIO = 1003; private const val REQUEST_SAVE_EXTRACTED_AUDIO = 1004; private const val REQUEST_SAVE_DECODED_WAV = 1005; private const val REQUEST_SAVE_SRT = 1006; private const val REQUEST_SAVE_OVERLAY_MP4 = 1008; private const val REQUEST_AUTO_NARRATED_MP4 = 1010; private const val REQUEST_SAVE_MYANMAR_NEURAL_WAV = 1009 }
}
