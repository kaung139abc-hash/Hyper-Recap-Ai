package com.kaung139abc.hyperrecap

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.ParcelFileDescriptor
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
        var sourceDescriptor: ParcelFileDescriptor? = null
        try {
            // MediaExtractor may continue reading lazily after setDataSource returns.
            // Keep the descriptor open until extraction and decoding have completely finished.
            sourceDescriptor = context.contentResolver.openFileDescriptor(source, "r")
                ?: throw IllegalStateException("Video ဖိုင်ဖွင့်မရပါ။")
            extractor.setDataSource(sourceDescriptor.fileDescriptor)

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

            val out = RandomAccessFile(output, "rw")
            wav = out
            out.setLength(0)
            out.write(ByteArray(44))
            var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var pcmEncoding = 2
            var dataBytes = 0L
            // Reserve enough cache space before starting a multi-hour decode.
            val sourceDurationUs = inputFormat.getLongOrDefault(MediaFormat.KEY_DURATION, 0L)
            if (sourceDurationUs > 0L) {
                // The Whisper pipeline needs mono audio. Downmix while decoding so a
                // two-hour 48 kHz stereo source uses roughly half the temporary storage.
                val estimatedWavBytes = (sourceDurationUs.toDouble() / 1_000_000.0 *
                    sampleRate.toDouble() * 2.0 + 44.0).toLong()
                val availableBytes = output.parentFile?.usableSpace ?: context.cacheDir.usableSpace
                val safetyMargin = 256L * 1024L * 1024L
                if (estimatedWavBytes > availableBytes - safetyMargin) {
                    throw IllegalStateException(
                        "၂ နာရီကျော် Video အသံ WAV အတွက် နေရာမလုံလောက်ပါ။ လိုအပ်မည့်နေရာ ${estimatedWavBytes / (1024 * 1024)} MB ခန့်၊ လွတ်နေသောနေရာ ${availableBytes / (1024 * 1024)} MB"
                    )
                }
            }
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
                            decoder.queueInputBuffer(inputIndex, 0, size, pts, 0)
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
                            // WAV header below is always 16-bit PCM. Normalize decoder output
                            // rather than writing 8/24/32-bit samples under a false 16-bit header.
                            val decodedPcm = convertToPcm16(chunk, pcmEncoding)
                            val pcm = downmixToMonoPcm16(decodedPcm, channels)
                            if (pcm.size.toLong() > 0xFFFFFFFFL - 36L - dataBytes) {
                                throw IllegalStateException("WAV 4 GB ကန့်သတ်ချက် ကျော်နေပါသည်။ Audio ကို အပိုင်းခွဲ၍ ပြောင်းရန်လိုအပ်ပါသည်။")
                            }
                            out.seek(44L + dataBytes)
                            out.write(pcm)
                            dataBytes += pcm.size
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

            if (sampleRate !in 8_000..192_000 || channels !in 1..8) {
                throw IllegalStateException("Decoder မှ Audio format မမှန်ကန်ပါ: ${sampleRate}Hz / ${channels}ch")
            }
            if (dataBytes == 0L) throw IllegalStateException("PCM အသံဒေတာ မရရှိပါ။")
            if (dataBytes % 2L != 0L) {
                throw IllegalStateException("Mono PCM အသံဒေတာသည် Sample Frame အပြည့်မဟုတ်ပါ။")
            }
            if (dataBytes > 0xFFFFFFFFL - 36L) throw IllegalStateException("WAV ဖိုင်အရွယ်အစား ကန့်သတ်ချက် ကျော်နေပါသည်။")
            out.seek(0)
            out.writeBytes("RIFF")
            out.writeIntLE((36L + dataBytes).toInt())
            out.writeBytes("WAVE")
            out.writeBytes("fmt ")
            out.writeIntLE(16)
            out.writeShortLE(1)
            // Store a mono WAV; Whisper resamples this to 16 kHz in bounded windows.
            out.writeShortLE(1)
            out.writeIntLE(sampleRate)
            out.writeIntLE(sampleRate * 2)
            out.writeShortLE(2)
            out.writeShortLE(16)
            out.writeBytes("data")
            out.writeIntLE(dataBytes.toInt())
            onProgress("PCM WAV ပြောင်းပြီးပါပြီ။")
        } catch (e: Exception) {
            output.delete()
            throw e
        } finally {
            try { decoder?.stop() } catch (_: Exception) {}
            try { decoder?.release() } catch (_: Exception) {}
            try { extractor.release() } catch (_: Exception) {}
            try { sourceDescriptor?.close() } catch (_: Exception) {}
            try { wav?.close() } catch (_: Exception) {}
        }
    }

    /**
     * Downmix interleaved PCM16 to mono as the bytes are streamed from MediaCodec.
     * This avoids retaining stereo WAVs for long movies; peak memory stays bounded
     * to one decoder output buffer plus one mono output buffer.
     */
    private fun downmixToMonoPcm16(interleaved: ByteArray, channels: Int): ByteArray {
        if (channels !in 1..8) throw IllegalStateException("Audio channel အရေအတွက် မမှန်ပါ။")
        val frameBytes = channels * 2
        if (interleaved.size % frameBytes != 0) {
            throw IllegalStateException("Decoder PCM buffer သည် audio frame အပြည့်မဟုတ်ပါ။")
        }
        if (channels == 1) return interleaved
        val frames = interleaved.size / frameBytes
        val mono = ByteArray(frames * 2)
        var source = 0
        var target = 0
        repeat(frames) {
            var sum = 0
            repeat(channels) {
                val low = interleaved[source++].toInt() and 0xff
                val high = interleaved[source++].toInt()
                sum += (high shl 8) or low
            }
            val sample = (sum / channels).coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            mono[target++] = (sample and 0xff).toByte()
            mono[target++] = ((sample ushr 8) and 0xff).toByte()
        }
        return mono
    }

    private fun convertToPcm16(bytes: ByteArray, encoding: Int): ByteArray {
        if (encoding == 2) return bytes // ENCODING_PCM_16BIT
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val output = when (encoding) {
            3 -> ByteBuffer.allocate((bytes.size / 1) * 2).order(ByteOrder.LITTLE_ENDIAN) // PCM_8BIT
            4 -> ByteBuffer.allocate((bytes.size / 4) * 2).order(ByteOrder.LITTLE_ENDIAN) // PCM_FLOAT
            21 -> ByteBuffer.allocate((bytes.size / 3) * 2).order(ByteOrder.LITTLE_ENDIAN) // PCM_24BIT_PACKED
            22 -> ByteBuffer.allocate((bytes.size / 4) * 2).order(ByteOrder.LITTLE_ENDIAN) // PCM_32BIT
            else -> throw IllegalStateException("မပံ့ပိုးသော PCM encoding: $encoding")
        }
        when (encoding) {
            3 -> while (input.remaining() >= 1) {
                val unsigned = input.get().toInt() and 0xff
                output.putShort(((unsigned - 128) shl 8).toShort())
            }
            4 -> while (input.remaining() >= 4) {
                val value = input.getFloat().coerceIn(-1f, 1f)
                output.putShort((value * 32767f).toInt().toShort())
            }
            21 -> while (input.remaining() >= 3) {
                var value = (input.get().toInt() and 0xff) or
                    ((input.get().toInt() and 0xff) shl 8) or
                    (input.get().toInt() shl 16)
                if ((value and 0x800000) != 0) value = value or -0x1000000
                output.putShort((value shr 8).toShort())
            }
            22 -> while (input.remaining() >= 4) {
                output.putShort((input.int shr 16).toShort())
            }
        }
        return output.array()
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
