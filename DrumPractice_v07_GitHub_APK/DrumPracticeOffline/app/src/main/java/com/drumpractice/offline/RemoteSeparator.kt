package com.drumpractice.offline

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.zip.ZipInputStream

/** HTTPS transport to an explicitly configured private Demucs backend. */
object RemoteSeparator {
    fun separate(ctx: Context, uri: Uri, server: String, apiKey: String, status: (String) -> Unit) {
        val boundary = "drum-${UUID.randomUUID()}"
        val connection = URL("$server/separate").openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 20000
        connection.readTimeout = 600000
        connection.doOutput = true
        connection.setRequestProperty("X-Drum-Key", apiKey)
        connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        val temp = File(ctx.cacheDir, "demucs_result.zip")
        try {
            connection.outputStream.use { raw ->
                val out = BufferedOutputStream(raw)
                out.write(("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"song.mp3\"\r\nContent-Type: application/octet-stream\r\n\r\n").toByteArray())
                ctx.contentResolver.openInputStream(uri)?.use { it.copyTo(out) }
                    ?: error("No se puede abrir el audio")
                out.write("\r\n--$boundary--\r\n".toByteArray())
                out.flush()
            }
            status("Procesando IA en servidor...")
            if (connection.responseCode != 200) {
                val message = connection.errorStream?.bufferedReader()?.use { it.readText().take(300) }
                error("Servidor ${connection.responseCode}: $message")
            }
            connection.inputStream.use { input -> temp.outputStream().use { input.copyTo(it) } }
            status("Guardando pistas...")
            val expected = mapOf("drums.wav" to "drums_ai.wav", "no_drums.wav" to "no_drums_ai.wav")
            val found = mutableSetOf<String>()
            ZipInputStream(BufferedInputStream(temp.inputStream())).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val targetName = expected[entry.name]
                    if (targetName != null && !entry.isDirectory) {
                        val output = File(ctx.filesDir, "$targetName.tmp")
                        output.outputStream().use { zip.copyTo(it) }
                        val target = File(ctx.filesDir, targetName)
                        if (target.exists()) target.delete()
                        check(output.renameTo(target)) { "No se pudo guardar $targetName" }
                        found.add(entry.name)
                    }
                    zip.closeEntry()
                }
            }
            check(found == expected.keys) { "Respuesta incompleta del servidor" }
        } finally { connection.disconnect(); temp.delete() }
    }
}
