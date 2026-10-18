package com.drumpractice.offline

import android.content.Context
import android.media.*
import android.net.Uri
import org.jtransforms.fft.FloatFFT_1D
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.*

/** HPSS spectral percussion extraction. Real offline signal separation, NOT a neural drums model.
 *  Percussive output contains transients from other instruments; drums may leak into accompaniment.
 */
object LocalSeparator {
    data class Result(val percussion: File, val accompaniment: File)
    fun separate(context: Context, uri: Uri, progress: (Int) -> Unit): Result {
        val (samples, rate) = decodeMono(context, uri)
        require(samples.size >= 4096) { "Audio demasiado corto" }
        val n = 2048; val hop = 512
        val frames = 1 + (samples.size - 1) / hop
        // Bounded RAM: 2048 bins x all frames is too large for long songs.
        // Process independent 12-second chunks with small overlap.
        val perc = File(context.filesDir, "percussion_v05.wav")
        val accomp = File(context.filesDir, "accompaniment_v05.wav")
        val outP = WavWriter(perc, rate); val outA = WavWriter(accomp, rate)
        try {
            val chunk = rate * 10
            var pos = 0
            while (pos < samples.size) {
                val end = min(samples.size, pos + chunk)
                val section = samples.copyOfRange(pos, end)
                val (p, a) = hpss(section)
                outP.write(p); outA.write(a)
                pos = end
                progress((100L * pos / samples.size).toInt())
            }
        } finally { outP.close(); outA.close() }
        return Result(perc, accomp)
    }
    private fun hpss(input: FloatArray): Pair<FloatArray, FloatArray> {
        val n = 2048; val hop = 512; val bins = n / 2 + 1
        val count = 1 + (input.size + n - 1) / hop
        val fft = FloatFFT_1D(n.toLong())
        val spectra = Array(count) { FloatArray(2*n) }
        val magnitudes = Array(count) { FloatArray(bins) }
        val win = FloatArray(n) { i -> (0.5 - 0.5*cos(2*PI*i/(n-1))).toFloat() }
        for (t in 0 until count) {
            val spec = spectra[t]
            for (i in 0 until n) { val ix=t*hop+i-n/2; spec[2*i]=if(ix in input.indices) input[ix]*win[i] else 0f }
            fft.complexForward(spec)
            for (k in 0 until bins) magnitudes[t][k]=hypot(spec[2*k],spec[2*k+1])
        }
        val p = FloatArray(input.size+n); val a = FloatArray(input.size+n); val norm = FloatArray(input.size+n)
        val temp = FloatArray(17)
        fun median(length: Int): Float { java.util.Arrays.sort(temp,0,length);return temp[length/2] }
        for (t in 0 until count) {
            val sp = spectra[t]; val ps=FloatArray(2*n); val aspec=FloatArray(2*n)
            for (k in 0 until bins) {
                var len=0
                for (j in max(0,t-8)..min(count-1,t+8)) temp[len++]=magnitudes[j][k]
                val harmonic=median(len)
                len=0
                for (j in max(0,k-8)..min(bins-1,k+8)) temp[len++]=magnitudes[t][j]
                val percussive=median(len)
                val hp=harmonic*harmonic; val pp=percussive*percussive
                val mask=pp/(hp+pp+1e-9f)
                ps[2*k]=sp[2*k]*mask; ps[2*k+1]=sp[2*k+1]*mask
                aspec[2*k]=sp[2*k]*(1-mask);aspec[2*k+1]=sp[2*k+1]*(1-mask)
                if (k>0 && k<n/2) { val mirror=n-k
                    ps[2*mirror]=ps[2*k];ps[2*mirror+1]=-ps[2*k+1]
                    aspec[2*mirror]=aspec[2*k];aspec[2*mirror+1]=-aspec[2*k+1]
                }
            }
            fft.complexInverse(ps,true);fft.complexInverse(aspec,true)
            for(i in 0 until n) { val ix=t*hop+i-n/2
                if(ix in input.indices) { val w=win[i];p[ix]+=ps[2*i]*w;a[ix]+=aspec[2*i]*w;norm[ix]+=w*w }
            }
        }
        return Pair(FloatArray(input.size){i->p[i]/norm[i].coerceAtLeast(1e-6f)},FloatArray(input.size){i->a[i]/norm[i].coerceAtLeast(1e-6f)})
    }
    private fun decodeMono(context: Context, uri: Uri): Pair<FloatArray,Int> {
        val extractor=MediaExtractor();extractor.setDataSource(context,uri,null)
        val track=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true } ?: error("Sin pista de audio")
        extractor.selectTrack(track);val fmt=extractor.getTrackFormat(track)
        val codec=MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME)!!)
        var rate=fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);var channels=fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val data=ArrayList<Float>();val info=MediaCodec.BufferInfo();var inputDone=false;var outputDone=false
        try { codec.configure(fmt,null,null,0);codec.start()
            while(!outputDone) {
                if(!inputDone) { val idx=codec.dequeueInputBuffer(10000)
                    if(idx>=0) { val b=codec.getInputBuffer(idx)!!;b.clear();val size=extractor.readSampleData(b,0)
                        if(size<0) {codec.queueInputBuffer(idx,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputDone=true}
                        else {codec.queueInputBuffer(idx,0,size,extractor.sampleTime,0);extractor.advance()}
                    }
                }
                val idx=codec.dequeueOutputBuffer(info,10000)
                if(idx==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {val f=codec.outputFormat;rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)}
                else if(idx>=0) {val b=codec.getOutputBuffer(idx)!!;b.position(info.offset);b.limit(info.offset+info.size);b.order(ByteOrder.LITTLE_ENDIAN)
                    val encoding=if(codec.outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) codec.outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) else AudioFormat.ENCODING_PCM_16BIT
                    var sum=0f;var c=0
                    while(b.remaining()>=if(encoding==AudioFormat.ENCODING_PCM_FLOAT)4 else 2) {
                        sum+=if(encoding==AudioFormat.ENCODING_PCM_FLOAT)b.float else b.short/32768f
                        if(++c==channels){data.add(sum/channels);sum=0f;c=0}
                    }
                    outputDone=(info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;codec.releaseOutputBuffer(idx,false)
                }
            }
        } finally {try{codec.stop()}catch(_:Exception){};codec.release();extractor.release()}
        return Pair(FloatArray(data.size){data[it]},rate)
    }
    private class WavWriter(file: File,private val rate:Int) {
        private val stream=BufferedOutputStream(FileOutputStream(file));private val target=file;private var count=0L
        init {stream.write(ByteArray(44))}
        fun write(samples:FloatArray) {val bytes=ByteArray(samples.size*2);for(i in samples.indices){val v=(samples[i].coerceIn(-1f,1f)*32767).toInt();bytes[2*i]=v.toByte();bytes[2*i+1]=(v shr 8).toByte()};stream.write(bytes);count+=bytes.size}
        fun close() {stream.close();java.io.RandomAccessFile(target,"rw").use {f->
            fun str(s:String){f.write(s.toByteArray(Charsets.US_ASCII))};fun le(v:Long){f.write((v and 255).toInt());f.write(((v shr 8) and 255).toInt());f.write(((v shr 16) and 255).toInt());f.write(((v shr 24) and 255).toInt())}
            str("RIFF");le(count+36);str("WAVEfmt ");le(16);f.write(byteArrayOf(1,0,1,0));le(rate.toLong());le(rate*2L);f.write(byteArrayOf(2,0,16,0));str("data");le(count)
        }}
    }
}
