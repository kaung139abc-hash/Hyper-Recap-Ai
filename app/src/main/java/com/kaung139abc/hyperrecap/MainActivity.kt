package com.kaung139abc.hyperrecap

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var selectedInfo: TextView
    private lateinit var status: TextView
    private var selectedUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(28, 44, 28, 28)
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
        root.addView(space(30))

        val choose = Button(this).apply {
            text = "ရုပ်ရှင် Video ရွေးရန်"
            setOnClickListener { chooseVideo() }
        }
        root.addView(choose, matchWidth())

        selectedInfo = label("Video မရွေးရသေးပါ", 16f)
        selectedInfo.setPadding(0, 28, 0, 20)
        root.addView(selectedInfo)

        status = label("ပထမအဆင့် — Video ဖိုင်ရွေးချယ်ခြင်း", 14f, 0xFFB8C0D9.toInt())
        root.addView(status)

        val note = label(
            "ဤစမ်းသပ်ဗားရှင်းသည် Video အချက်အလက်နှင့် ကြာချိန်ကို ဖတ်ပေးသည်။ " +
                "AI Recap၊ Offline ဘာသာပြန်နှင့် အသံထုတ်စနစ်များကို နောက်အဆင့်များတွင် ထည့်သွင်းမည်။",
            14f, 0xFFFFD28A.toInt()
        )
        note.setPadding(0, 36, 0, 0)
        root.addView(note)

        setContentView(root)
    }

    private fun space(height: Int) = View(this).apply {
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    private fun matchWidth() = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )

    private fun chooseVideo() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "video/*"
        }
        startActivityForResult(intent, REQUEST_VIDEO)
    }

    @Deprecated("Deprecated in Android API, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_VIDEO || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        selectedUri = uri
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) { }
        val name = queryName(uri) ?: "ရွေးထားသော Video"
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            val durationMs = retriever.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_DURATION
            )?.toLongOrNull() ?: 0L
            selectedInfo.text = "$name\nကြာချိန်: ${formatDuration(durationMs)}"
            status.text = "Video ကို အောင်မြင်စွာ ရွေးချယ်ပြီးပါပြီ"
        } catch (e: Exception) {
            selectedInfo.text = name
            status.text = "Video ကို ဖတ်မရပါ: ${e.localizedMessage ?: "Unknown error"}"
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }
    }

    private fun queryName(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            if (cursor?.moveToFirst() == true) cursor.getString(0) else null
        } catch (_: Exception) {
            null
        } finally {
            cursor?.close()
        }
    }

    private fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }

    companion object { private const val REQUEST_VIDEO = 1001 }
}
