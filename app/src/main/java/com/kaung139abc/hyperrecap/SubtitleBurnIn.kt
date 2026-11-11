package com.kaung139abc.hyperrecap

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.StaticOverlaySettings
import java.text.BreakIterator
import java.util.Locale

/** Draws the active SRT cue into each output frame; the result is a burned-in subtitle track. */
@UnstableApi
object SubtitleBurnIn {
    fun createEffect(width: Int, height: Int, cues: List<SrtTranscript.Cue>): OverlayEffect {
        require(width > 0 && height > 0 && cues.isNotEmpty())
        val blank = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val settings = StaticOverlaySettings.Builder().build()
        val overlay = object : BitmapOverlay() {
            private var cachedIndex = Int.MIN_VALUE
            private var cachedBitmap: Bitmap = blank

            override fun getBitmap(presentationTimeUs: Long): Bitmap {
                val timeMs = presentationTimeUs / 1000L
                val index = cues.indexOfFirst { timeMs >= it.startMs && timeMs < it.endMs }
                if (index != cachedIndex) {
                    cachedIndex = index
                    cachedBitmap = if (index >= 0) drawCue(cues[index].text, width, height) else blank
                }
                return cachedBitmap
            }

            override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings = settings
        }
        return OverlayEffect(listOf(overlay))
    }

    private fun drawCue(text: String, width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = (height * 0.043f).coerceIn(24f, 58f)
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            setShadowLayer(2.5f, 1f, 2f, Color.BLACK)
        }
        val maxTextWidth = width * 0.88f
        val lines = wrap(text, textPaint, maxTextWidth).takeLast(3)
        if (lines.isEmpty()) return bitmap

        val lineHeight = textPaint.fontSpacing * 1.12f
        val paddingX = width * 0.025f
        val paddingY = height * 0.018f
        val textHeight = lineHeight * lines.size
        val centerX = width / 2f
        val bottom = height * 0.075f
        val top = height - bottom - textHeight - paddingY * 2
        val maxLine = lines.maxOf { textPaint.measureText(it) }
        val box = RectF(
            centerX - maxLine / 2f - paddingX,
            top,
            centerX + maxLine / 2f + paddingX,
            height - bottom
        )
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xC9000000.toInt() }
        canvas.drawRoundRect(box, height * 0.018f, height * 0.018f, backgroundPaint)
        var baseline = top + paddingY - textPaint.ascent()
        lines.forEach { line ->
            canvas.drawText(line, centerX, baseline, textPaint)
            baseline += lineHeight
        }
        return bitmap
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        val iterator = BreakIterator.getCharacterInstance(Locale("my"))
        iterator.setText(text)
        val result = mutableListOf<String>()
        var line = StringBuilder()
        var start = iterator.first()
        var end = iterator.next()
        while (end != BreakIterator.DONE) {
            val cluster = text.substring(start, end)
            val candidate = line.toString() + cluster
            if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                result += line.toString().trim()
                line = StringBuilder()
                if (cluster.isNotBlank()) line.append(cluster)
            } else {
                line.append(cluster)
            }
            start = end
            end = iterator.next()
        }
        if (line.isNotEmpty()) result += line.toString().trim()
        return result.filter { it.isNotBlank() }
    }
}
