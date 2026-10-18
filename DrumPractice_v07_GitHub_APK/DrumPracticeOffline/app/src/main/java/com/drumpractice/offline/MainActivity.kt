package com.drumpractice.offline

import android.app.Activity
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.widget.SeekBar
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.widget.ScrollView
import android.media.PlaybackParams
import android.provider.OpenableColumns
import android.widget.EditText
import java.io.File

/** First milestone: offline import/playback; no misleading separation button. */
class MainActivity : Activity() {
    private var player: MediaPlayer? = null
    private var current: Uri? = null
    private lateinit var status: TextView
    private val pickAudio = 101
    private val metronome = ClickMetronome()
    private lateinit var bpmLabel: TextView
    private var bpm = 120
    private var speed = 1.0f
    private lateinit var speedLabel: TextView
    private lateinit var progressLabel: TextView
    private lateinit var positionBar: SeekBar
    private val uiHandler = Handler(Looper.getMainLooper())
    private val updatePosition = object : Runnable {
        override fun run() {
            player?.let { p -> try {
                if (!positionBar.isPressed) positionBar.progress = p.currentPosition
                progressLabel.text = "${formatTime(p.currentPosition)} / ${formatTime(p.duration)}"
            } catch (_: Exception) {} }
            uiHandler.postDelayed(this, 500)
        }
    }
    private fun formatTime(ms: Int): String { val seconds = (ms / 1000).coerceAtLeast(0); return "%02d:%02d".format(seconds / 60, seconds % 60) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 60, 32, 32)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        fun button(text: String, click: () -> Unit) {
            root.addView(Button(this).apply { this.text = text; setOnClickListener { click() } })
        }
        root.addView(TextView(this).apply { text = "🥁 Drum Practice v0.6"; textSize = 24f })
        status = TextView(this).apply { text = "Elegí una canción de tu Xiaomi"; textSize = 16f }
        root.addView(status)
        val prefs = getSharedPreferences("drum_practice", MODE_PRIVATE)
        prefs.getString("last_audio", null)?.let { saved ->
            try {
                current = Uri.parse(saved)
                status.text = "Última canción: ${current!!.lastPathSegment ?: "Audio"}"
            } catch (_: Exception) { current = null }
        }
        button("Elegir canción MP3") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
            }, pickAudio)
        }
        button("Reproducir / Pausar") {
            val uri = current ?: return@button
            try {
                if (player == null) {
                    player = MediaPlayer().apply {
                        setDataSource(this@MainActivity, uri)
                        prepare()
                        if (Build.VERSION.SDK_INT >= 23) playbackParams = PlaybackParams().setSpeed(speed)
                        positionBar.max = duration
                        start()
                    }
                } else if (player!!.isPlaying) player!!.pause() else player!!.start()
            } catch (e: Exception) { Toast.makeText(this, "No se pudo reproducir: ${e.message}", Toast.LENGTH_LONG).show() }
        }
        progressLabel = TextView(this).apply { text = "00:00 / 00:00"; textSize = 16f }
        root.addView(progressLabel)
        positionBar = SeekBar(this).apply {
            max = 1
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    if (fromUser) { try { player?.seekTo(value) } catch (_: Exception) {} }
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        root.addView(positionBar)
        uiHandler.post(updatePosition)
        speedLabel = TextView(this).apply { text = "Velocidad: 100%"; textSize = 18f }
        root.addView(speedLabel)
        root.addView(SeekBar(this).apply {
            max = 100; progress = 50 // 50% a 150%
            setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    speed = (50 + progress) / 100f
                    speedLabel.text = "Velocidad: ${(speed * 100).toInt()}%"
                    try { if (Build.VERSION.SDK_INT >= 23 && player != null) player?.playbackParams = PlaybackParams().setSpeed(speed) } catch (_: Exception) {}
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        })
        bpmLabel = TextView(this).apply { text = "Tempo: 120 BPM (sin analizar)"; textSize = 20f }
        root.addView(bpmLabel)
        button("Detectar BPM de la canción") {
            val uri = current ?: run { Toast.makeText(this, "Elegí una canción primero", Toast.LENGTH_SHORT).show(); return@button }
            bpmLabel.text = "Analizando audio..."
            Thread {
                try {
                    val detected = TempoEngine.estimate(this, uri)
                    runOnUiThread { bpm = detected; metronome.bpm = bpm; bpmLabel.text = "Tempo estimado: $bpm BPM" }
                } catch (e: Exception) { runOnUiThread { bpmLabel.text = "Error al analizar: ${e.message}" } }
            }.start()
        }
        button("BPM −") { bpm = (bpm-1).coerceAtLeast(30); metronome.bpm=bpm; bpmLabel.text="$bpm BPM" }
        button("BPM +") { bpm = (bpm+1).coerceAtMost(250); metronome.bpm=bpm; bpmLabel.text="$bpm BPM" }
        button("Mitad de BPM") { bpm = (bpm/2).coerceAtLeast(30); metronome.bpm=bpm; bpmLabel.text="$bpm BPM" }
        button("Doble de BPM") { bpm = (bpm*2).coerceAtMost(250); metronome.bpm=bpm; bpmLabel.text="$bpm BPM" }
        var metroOn = false
        button("Metrónomo ON / OFF") { metroOn = !metroOn; if (metroOn) metronome.start() else metronome.stop(); Toast.makeText(this, if (metroOn) "Metrónomo encendido" else "Metrónomo apagado", Toast.LENGTH_SHORT).show() }
        button("Compás 4/4") { metronome.beatsPerBar=4 }
        button("Compás 3/4") { metronome.beatsPerBar=3 }
        button("Compás 6/8 (6 pulsos)") { metronome.beatsPerBar=6 }
        root.addView(TextView(this).apply { text = "Volumen del clic" })
        root.addView(SeekBar(this).apply { max=100; progress=60; setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, user: Boolean) { metronome.volume=p/100f }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        }) })
        root.addView(TextView(this).apply { text = "Separación IA por Internet (servidor Demucs)"; textSize = 18f })
        val serverUrl = EditText(this).apply {
            hint = "https://tu-servidor.example"
            setSingleLine(true)
            setText(prefs.getString("server_url", ""))
        }
        root.addView(serverUrl)
        val apiKeyInput = EditText(this).apply {
            hint = "Clave del servidor (Colab)"
            setSingleLine(true)
            setText(prefs.getString("server_key", ""))
        }
        root.addView(apiKeyInput)
        button("Separar batería con IA (Internet)") {
            val uri = current ?: run { Toast.makeText(this, "Elegí una canción primero", Toast.LENGTH_SHORT).show(); return@button }
            val url = serverUrl.text.toString().trim().trimEnd('/')
            if (!url.startsWith("https://")) { status.text = "Ingresá una dirección HTTPS de tu servidor"; return@button }
            val apiKey = apiKeyInput.text.toString().trim()
            if (apiKey.isEmpty()) { status.text = "Ingresá la clave que muestra Colab"; return@button }
            prefs.edit().putString("server_url", url).putString("server_key", apiKey).apply()
            status.text = "Enviando canción al servidor..."
            Thread {
                try {
                    RemoteSeparator.separate(this, uri, url, apiKey) { msg -> runOnUiThread { status.text = msg } }
                    runOnUiThread { status.text = "Separación IA terminada. Usá los botones para escuchar." }
                } catch (e: Exception) { runOnUiThread { status.text = "Error IA: ${e.message}" } }
            }.start()
        }
        button("Escuchar sin batería (IA)") { playResult(File(filesDir, "no_drums_ai.wav")) }
        button("Escuchar solo batería (IA)") { playResult(File(filesDir, "drums_ai.wav")) }
        button("Separar percusión / acompañamiento (sin Internet)") {
            val uri = current ?: run { Toast.makeText(this, "Elegí una canción primero", Toast.LENGTH_SHORT).show(); return@button }
            status.text = "Separando audio localmente..."
            Thread {
                try {
                    val result = LocalSeparator.separate(this, uri) { pct -> runOnUiThread { status.text = "Separando: $pct%" } }
                    runOnUiThread { status.text = "Listo: percusión y acompañamiento generados"; Toast.makeText(this, "Separación finalizada", Toast.LENGTH_LONG).show() }
                } catch (e: Exception) { runOnUiThread { status.text = "Error: ${e.message}" } }
            }.start()
        }
        button("Escuchar acompañamiento sin percusión") {
            val file = java.io.File(filesDir, "accompaniment_v05.wav")
            if (!file.exists()) { Toast.makeText(this, "Primero separá la canción", Toast.LENGTH_SHORT).show(); return@button }
            try { player?.release(); player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); positionBar.max = duration; start() } }
            catch (e: Exception) { status.text = "Error al reproducir: ${e.message}" }
        }
        button("Escuchar percusión aislada") {
            val file = java.io.File(filesDir, "percussion_v05.wav")
            if (!file.exists()) { Toast.makeText(this, "Primero separá la canción", Toast.LENGTH_SHORT).show(); return@button }
            try { player?.release(); player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); positionBar.max = duration; start() } }
            catch (e: Exception) { status.text = "Error al reproducir: ${e.message}" }
        }
        button("Detener") { player?.stop(); player?.release(); player = null }
        root.addView(TextView(this).apply {
            text = "v0.5: separación espectral local de percusión (HPSS). No equivale a separación de batería por IA: puede dejar batería audible y quitar ataques de otros instrumentos."
            textSize = 14f
        })
        scroll.addView(root)
        setContentView(scroll)
    }

    @Deprecated("Uses legacy activity result for minimal no-AndroidX example")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickAudio && resultCode == RESULT_OK && data?.data != null) {
            player?.release(); player = null
            current = data.data
            try { contentResolver.takePersistableUriPermission(current!!, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: SecurityException) {}
            prefsSave(current!!)
            status.text = "Canción cargada: ${current!!.lastPathSegment ?: "Audio"}"
        }
    }
    private fun playResult(file: File) {
        if (!file.exists()) { status.text = "Primero procesá una canción con IA"; return }
        try {
            player?.release()
            player = null
            player = MediaPlayer().apply { setDataSource(file.absolutePath); prepare(); positionBar.max = duration; start() }
        } catch (e: Exception) { status.text = "Error al reproducir: ${e.message}" }
    }
    private fun prefsSave(uri: Uri) {
        getSharedPreferences("drum_practice", MODE_PRIVATE).edit().putString("last_audio", uri.toString()).apply()
    }
    override fun onDestroy() { uiHandler.removeCallbacks(updatePosition); metronome.stop(); player?.release(); player = null; super.onDestroy() }
}
