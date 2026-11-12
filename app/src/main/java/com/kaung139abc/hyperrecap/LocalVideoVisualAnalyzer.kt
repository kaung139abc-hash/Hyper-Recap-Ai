package com.kaung139abc.hyperrecap

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import java.util.concurrent.TimeUnit

/**
 * Offline visual evidence extraction for movie recaps.
 *
 * Samples a bounded number of frames across the complete source video and
 * classifies visible objects/scenes with ML Kit's bundled on-device model.
 * No frames are uploaded and no network/API key is required.
 *
 * Labels are visual cues, not a claim of full visual plot comprehension.
 */
object LocalVideoVisualAnalyzer {
    private const val MAX_FRAMES = 24
    private const val MIN_CONFIDENCE = 0.38f
    private const val MAX_LABELS_PER_FRAME = 5
    private const val MAX_OUTPUT_CHARS = 8_000

    fun analyze(
        context: Context,
        source: Uri,
        onProgress: (String) -> Unit = {}
    ): String {
        val retriever = MediaMetadataRetriever()
        val labeler = ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)
        val evidence = StringBuilder()
        try {
            retriever.setDataSource(context, source)
            val durationMs = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()
                ?.coerceAtLeast(1L)
                ?: throw IllegalStateException("Video ကြာချိန်ကို ဖတ်မရပါ။")

            val count = if (durationMs < 180_000L) 6 else MAX_FRAMES
            val intervalMs = (durationMs / count).coerceAtLeast(1L)
            var analyzed = 0

            for (index in 0 until count) {
                if (Thread.currentThread().isInterrupted) {
                    throw InterruptedException("Video ရုပ်ပုံစိစစ်မှု ရပ်ဆိုင်းလိုက်ပါပြီ။")
                }
                val timeMs = (index * intervalMs + intervalMs / 2L)
                    .coerceAtMost(durationMs - 1L)
                onProgress("Offline ရုပ်ပုံနားလည်မှု — Frame ${index + 1}/$count ကို စိစစ်နေသည်...")

                val frame = retriever.getFrameAtTime(
                    timeMs * 1_000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                ) ?: continue

                try {
                    val scale = minOf(256f / frame.width, 256f / frame.height, 1f)
                    val width = (frame.width * scale).toInt().coerceAtLeast(1)
                    val height = (frame.height * scale).toInt().coerceAtLeast(1)
                    val resized = Bitmap.createScaledBitmap(frame, width, height, true)
                    try {
                        val image = InputImage.fromBitmap(resized, 0)
                        val labels = Tasks.await(labeler.process(image), 30, TimeUnit.SECONDS)
                            .asSequence()
                            .filter { it.confidence >= MIN_CONFIDENCE }
                            .sortedByDescending { it.confidence }
                            .take(MAX_LABELS_PER_FRAME)
                            .map { "${it.text} (${(it.confidence * 100).toInt()}%)" }
                            .toList()

                        if (labels.isNotEmpty()) {
                            evidence.append("[${formatTimestamp(timeMs)}] ")
                                .append(labels.joinToString(", "))
                                .append('\n')
                            analyzed++
                        }
                    } finally {
                        if (resized !== frame) resized.recycle()
                    }
                } finally {
                    frame.recycle()
                }
            }

            onProgress(
                if (analyzed > 0) "Offline ရုပ်ပုံစိစစ်ပြီးပါပြီ — အထောက်အထားရသော Frame $analyzed ခု"
                else "ရုပ်ပုံမှ ဖော်ထုတ်နိုင်သော အချက်အလက် မတွေ့ပါ။"
            )
            return evidence.toString().take(MAX_OUTPUT_CHARS)
        } finally {
            try { labeler.close() } catch (_: Exception) {}
            try { retriever.release() } catch (_: Exception) {}
        }
    }

    private fun formatTimestamp(milliseconds: Long): String {
        val totalSeconds = milliseconds / 1_000L
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0) {
            "%02d:%02d:%02d".format(hours, minutes, seconds)
        } else {
            "%02d:%02d".format(minutes, seconds)
        }
    }
}
