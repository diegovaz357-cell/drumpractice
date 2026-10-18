package com.drumpractice.offline

import android.content.Context
import android.media.*
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.*

/** Offline tempo estimate based on positive changes in short-time audio energy. */
object TempoEngine {
    fun estimate(context: Context, uri: Uri): Int {
        val extractor = MediaExtractor()
        extractor.setDataSource(context, uri, null)
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: error("No hay pista de audio")
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: error("Formato desconocido")
        val codec = MediaCodec.createDecoderByType(mime)
        val energies = ArrayList<Double>()
        var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val window = 1024
        var sum = 0.0; var count = 0
        var inputDone = false; var outputDone = false
        val info = MediaCodec.BufferInfo()
        try {
            codec.configure(format, null, null, 0); codec.start()
            while (!outputDone && energies.size < 18000) {
                if (!inputDone) {
                    val idx = codec.dequeueInputBuffer(10000)
                    if (idx >= 0) {
                        val buf = codec.getInputBuffer(idx)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) { codec.queueInputBuffer(idx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); inputDone = true }
                        else { codec.queueInputBuffer(idx, 0, n, extractor.sampleTime, 0); extractor.advance() }
                    }
                }
                val idx = codec.dequeueOutputBuffer(info, 10000)
                if (idx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val out = codec.outputFormat
                    sampleRate = out.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = out.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                } else if (idx >= 0) {
                    val buffer = codec.getOutputBuffer(idx)!!
                    buffer.position(info.offset); buffer.limit(info.offset + info.size)
                    val pcmEncoding = if (codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    buffer.order(ByteOrder.LITTLE_ENDIAN)
                    if (pcmEncoding == AudioFormat.ENCODING_PCM_FLOAT) {
                        while (buffer.remaining() >= 4) { val s = buffer.float.toDouble(); sum += s*s; if (++count == window * channels) { energies.add(sum/count); sum=0.0; count=0 } }
                    } else {
                        while (buffer.remaining() >= 2) { val s = buffer.short.toDouble()/32768.0; sum += s*s; if (++count == window * channels) { energies.add(sum/count); sum=0.0; count=0 } }
                    }
                    outputDone = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    codec.releaseOutputBuffer(idx, false)
                }
            }
        } finally { try { codec.stop() } catch (_: Exception) {}; codec.release(); extractor.release() }
        if (energies.size < 100) error("Audio demasiado corto")
        val onset = DoubleArray(energies.size) { i -> if (i == 0) 0.0 else max(0.0, sqrt(energies[i]) - sqrt(energies[i-1])) }
        val framesPerSecond = sampleRate.toDouble() / window
        var best = 120; var bestScore = Double.NEGATIVE_INFINITY
        for (bpm in 60..200) {
            val lag = (60.0 * framesPerSecond / bpm).roundToInt()
            if (lag < 1 || lag >= onset.size) continue
            var score = 0.0
            for (i in lag until onset.size) score += onset[i] * onset[i-lag]
            score /= (onset.size - lag)
            if (score > bestScore) { bestScore = score; best = bpm }
        }
        return best
    }
}

/** Standalone audible metronome; independent of music playback. */
class ClickMetronome {
    @Volatile private var running = false
    @Volatile var bpm = 120
    @Volatile var beatsPerBar = 4
    @Volatile var volume = 0.6f
    private var worker: Thread? = null
    fun start() {
        if (running) return
        running = true
        worker = Thread {
            val rate = 44100
            val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val track = AudioTrack.Builder().setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                .setBufferSizeInBytes(max(min, 4096)).setTransferMode(AudioTrack.MODE_STREAM).build()
            try {
                track.play(); var beat = 0
                while (running) {
                    val duration = (60_000.0 / bpm.coerceIn(30,250)).toLong()
                    val samples = (rate * 0.035).toInt()
                    val data = ShortArray(samples) { i ->
                        val env = exp(-i.toDouble()/300.0)
                        val freq = if (beat % beatsPerBar.coerceAtLeast(1) == 0) 1400 else 900
                        (sin(2*PI*freq*i/rate)*env*volume.coerceIn(0f,1f)*16000).toInt().toShort()
                    }
                    track.write(data, 0, data.size)
                    beat++
                    val remaining = duration - 35
                    if (remaining > 0) Thread.sleep(remaining)
                }
            } catch (_: InterruptedException) {} finally { track.stop(); track.release() }
        }.also { it.start() }
    }
    fun stop() { running = false; worker?.interrupt(); worker = null }
}
