package com.codexlocal

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Gestion des fichiers .gguf.
 *
 * Ils sont stockes dans le dossier externe prive de l'app :
 *   /sdcard/Android/data/com.codexlocal/files/models/
 * On peut donc y copier un gros modele par USB (MTP) sans passer par l'app.
 */
object ModelStore {

    fun modelsDir(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun list(context: Context): List<File> =
        modelsDir(context)
            .listFiles { f: File -> f.isFile && f.name.endsWith(".gguf", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()

    fun find(context: Context, path: String): File? =
        list(context).firstOrNull { it.absolutePath == path }

    fun importFromUri(
        context: Context,
        uri: Uri,
        displayName: String,
        onProgress: (Long) -> Unit
    ): File {
        val safeName = displayName.ifBlank { "modele.gguf" }.replace('/', '_')
        val target = File(modelsDir(context), safeName)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Impossible d'ouvrir le fichier selectionne")
        input.use { source ->
            target.outputStream().use { output -> pump(source, output, onProgress) }
        }
        return target
    }

    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "CodexLocal")
        }
        try {
            val code = conn.responseCode
            require(code in 200..299) { "HTTP $code" }
            val total = conn.contentLengthLong
            val input = conn.inputStream ?: error("Flux vide")
            input.use { source ->
                dest.outputStream().use { output -> pump(source, output) { onProgress(it, total) } }
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun pump(input: InputStream, output: OutputStream, onProgress: (Long) -> Unit) {
        val buffer = ByteArray(1 shl 16)
        var done = 0L
        var lastReport = 0L
        while (true) {
            val read = input.read(buffer)
            if (read <= 0) break
            output.write(buffer, 0, read)
            done += read
            if (done - lastReport > 4_000_000L) {
                lastReport = done
                onProgress(done)
            }
        }
        onProgress(done)
    }
}
