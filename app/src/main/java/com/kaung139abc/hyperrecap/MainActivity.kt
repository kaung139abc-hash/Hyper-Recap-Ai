package com.kaung139abc.hyperrecap

import android.app.Activity
import android.app.DownloadManager
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var selectedInfo: TextView
    private lateinit var status: TextView
    private lateinit var linkInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
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
            if (!shared.isNullOrBlank() && ::linkInput.isInitialized) linkInput.setText(shared)
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

        val choose = Button(this).apply {
            text = "ဖုန်းထဲက Video ရွေးရန်"
            setOnClickListener { chooseVideo() }
        }
        root.addView(choose, matchWidth())

        selectedInfo = label("Video မရွေးရသေးပါ", 16f)
        selectedInfo.setPadding(0, 24, 0, 16)
        root.addView(selectedInfo)

        status = label("Link သို့မဟုတ် ဖုန်းထဲက Video ကို ရွေးနိုင်သည်", 14f, 0xFFB8C0D9.toInt())
        root.addView(status)

        val note = label(
            "မှတ်ချက် — လက်ရှိ Link Download သည် .mp4/.mkv/.webm/.mov/.m4v ကဲ့သို့ တိုက်ရိုက် Video ဖိုင် URL များအတွက်သာ ဖြစ်သည်။ YouTube/Facebook/TikTok စာမျက်နှာ Link များကို platform က ခွင့်ပြုသည့်နည်းလမ်းမရှိဘဲ တိုက်ရိုက်ဒေါင်းလုဒ်မလုပ်နိုင်သေးပါ။ AI Recap Engine ကို နောက်တစ်ဆင့်တွင် ထည့်သွင်းရဦးမည်။",
            13f, 0xFFFFD28A.toInt()
        )
        note.setPadding(0, 28, 0, 0)
        root.addView(note)
        setContentView(root)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }
    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun downloadDirectVideo(raw: String) {
        val uri = try { Uri.parse(raw) } catch (_: Exception) { null }
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
        if (requestCode != REQUEST_VIDEO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        try { contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        catch (_: SecurityException) { }
        val name = queryName(uri) ?: "ရွေးထားသော Video"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            selectedInfo.text = "$name\nကြာချိန်: ${formatDuration(duration)}"
            status.text = "Video ကို ရွေးပြီးပါပြီ။ AI Recap Processing ကို နောက်ထပ် Build တွင် ထည့်သွင်းရဦးမည်။"
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

    companion object { private const val REQUEST_VIDEO = 1001 }
}
