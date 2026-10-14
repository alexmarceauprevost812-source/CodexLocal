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
        // Le sélecteur Android est le seul pont fiable avec le stockage partagé de Termux.
        val safeName = displayName.substringAfterLast('/').substringAfterLast('\\')
            .ifBlank { "modele.gguf" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        require(safeName.endsWith(".gguf", ignoreCase = true)) {
            "Sélectionne un fichier de modèle .gguf"
        }

        // Ne jamais écraser un modèle existant : choisir un nom libre.
        val dir = modelsDir(context)
        val baseName = safeName.dropLast(5)
        var target = File(dir, safeName)
        var suffix = 2
        while (target.exists()) {
            target = File(dir, "$baseName-$suffix.gguf")
            suffix++
        }

        // Une copie incomplète ne doit pas apparaître comme un modèle disponible.
        val partial = File(dir, ".${target.name}.${System.nanoTime()}.partial")
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: error("Impossible d'ouvrir le fichier sélectionné")
            input.buffered().use { source ->
                source.mark(4)
                val magic = ByteArray(4)
                require(source.read(magic) == 4 &&
                    magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))) {
                    "Ce fichier n'est pas un GGUF valide"
                }
                source.reset()
                partial.outputStream().use { output ->
                    pump(source, output, onProgress)
                }
            }
            check(partial.renameTo(target)) { "Impossible de finaliser la copie du modèle" }
            return target
        } finally {
            partial.delete()
        }
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
