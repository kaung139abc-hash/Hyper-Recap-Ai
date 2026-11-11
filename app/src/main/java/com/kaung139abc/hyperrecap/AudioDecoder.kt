package com.kaung139abc.hyperrecap

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decodes the selected video's audio stream to a streaming 16-bit PCM WAV.
 * Audio is processed incrementally; the whole movie is never loaded into RAM.
 */
object AudioDecoder {
    fun decodeToWav(
        context: Context,
        source: Uri,
        output: java.io.File,
        onProgress: (String) -> Unit = {}
    ) {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var wav: RandomAccessFile? = null
        try {
            context.contentResolver.openFileDescriptor(source, "r")?.use {
                extractor.setDataSource(it.fileDescriptor)
            } ?: throw IllegalStateException("Video ဖိုင်ဖွင့်မရပါ။")

            var audioTrack = -1
            var inputFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                if ((format.getString(MediaFormat.KEY_MIME) ?: "").startsWith("audio/")) {
                    audioTrack = i
                    inputFormat = format
                    break
                }
            }
            if (audioTrack < 0 || inputFormat == null) throw IllegalStateException("Audio Track မတွေ့ပါ။")
            extractor.selectTrack(audioTrack)

            val mime = inputFormat.getString(MediaFormat.KEY_MIME)
                ?: throw IllegalStateException("Audio format မသိပါ။")
            decoder = MediaCodec.createDecoderByType(mime)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()

            wav = RandomAccessFile(output, "rw")
            wav.setLength(0)
            wav.write(ByteArray(44))
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = 2
            var dataBytes = 0L
            var inputDone = false
            var outputDone = false
            var lastProgress = -1L
            val info = MediaCodec.BufferInfo()

            while (!outputDone) {
                if (!inputDone) {
                    val inputIndex = decoder.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex)
                            ?: throw IllegalStateException("Decoder input buffer မရပါ။")
                        inputBuffer.clear()
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            decoder.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            val pts = extractor.sampleTime.coerceAtLeast(0L)
                            decoder.queueInputBuffer(inputIndex, 0, size, pts, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val outFormat = decoder.outputFormat
                        sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        pcmEncoding = if (outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING))
                            outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else 2
                        onProgress("အသံကို PCM ပြောင်းနေသည် — ${sampleRate}Hz / ${channels}ch")
                    }
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    else -> if (outputIndex >= 0) {
                        val buffer = decoder.getOutputBuffer(outputIndex)
                        if (buffer != null && info.size > 0 &&
                            (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            buffer.position(info.offset)
                            buffer.limit(info.offset + info.size)
                            val chunk = ByteArray(info.size)
                            buffer.get(chunk)
                            if (pcmEncoding == 4) {
                                // Convert float PCM to signed 16-bit PCM for broad WAV compatibility.
                                val floats = ByteBuffer.wrap(chunk).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
                                val converted = ByteBuffer.allocate(floats.remaining() * 2).order(ByteOrder.LITTLE_ENDIAN)
                                while (floats.hasRemaining()) {
                                    val value = floats.get().coerceIn(-1f, 1f)
                                    converted.putShort((value * 32767f).toInt().toShort())
                                }
                                val pcm = converted.array()
                                wav.seek(44L + dataBytes)
                                wav.write(pcm)
                                dataBytes += pcm.size
                            } else {
                                wav.seek(44L + dataBytes)
                                wav.write(chunk)
                                dataBytes += chunk.size
                            }
                            val durationUs = inputFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
                            if (durationUs > 0) {
                                val pct = ((info.presentationTimeUs * 100L) / durationUs).coerceIn(0L, 100L)
                                if (pct / 5 != lastProgress / 5) {
                                    lastProgress = pct
                                    onProgress("အသံ WAV ပြောင်းနေသည် — $pct%")
                                }
                            }
                        }
                        outputDone = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }

            if (dataBytes == 0L) throw IllegalStateException("PCM အသံဒေတာ မရရှိပါ။")
            if (dataBytes > 0xFFFFFFFFL - 36L) throw IllegalStateException("WAV ဖိုင်အရွယ်အစား ကန့်သတ်ချက် ကျော်နေပါသည်။")
            wav.seek(0)
            wav.writeBytes("RIFF")
            wav.writeIntLE((36L + dataBytes).toInt())
            wav.writeBytes("WAVE")
            wav.writeBytes("fmt ")
            wav.writeIntLE(16)
            wav.writeShortLE(1)
            wav.writeShortLE(channels)
            wav.writeIntLE(sampleRate)
            wav.writeIntLE(sampleRate * channels * 2)
            wav.writeShortLE(channels * 2)
            wav.writeShortLE(16)
            wav.writeBytes("data")
            wav.writeIntLE(dataBytes.toInt())
            onProgress("PCM WAV ပြောင်းပြီးပါပြီ။")
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { wav?.close() } catch (_: Exception) {}
        }
    }

    private fun RandomAccessFile.writeIntLE(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }
    private fun RandomAccessFile.writeShortLE(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }
    private fun MediaFormat.getLongOrDefault(key: String, fallback: Long): Long =
        try { getLong(key) } catch (_: Exception) { fallback }
}
