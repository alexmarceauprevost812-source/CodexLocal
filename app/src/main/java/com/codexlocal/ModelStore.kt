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
        // Si le partage avec les autres applis (LexOS...) est actif, on lit et on
        // ecrit dans le dossier public commun. Sinon, le dossier prive habituel.
        if (PartageModeles.actif(context)) {
            val partage = PartageModeles.dossierPartage()
            if (partage.exists() || partage.mkdirs()) return partage
        }
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
        val partiel = partiel(target)
        val input = context.contentResolver.openInputStream(uri)
            ?: error("Impossible d'ouvrir le fichier selectionne")
        try {
            input.use { source ->
                partiel.outputStream().use { output -> pump(source, output, onProgress) }
            }
            terminer(partiel, target)
        } catch (e: Exception) {
            partiel.delete()
            throw e
        }
        return target
    }

    fun download(url: String, dest: File, onProgress: (Long, Long) -> Unit) {
        // On ecrit dans un fichier ".part" : un telechargement interrompu ne laisse jamais
        // un faux modele (incomplet) dans la liste.
        val partiel = partiel(dest)
        partiel.delete()
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
                partiel.outputStream().use { output -> pump(source, output) { onProgress(it, total) } }
            }
            val recu = partiel.length()
            check(total <= 0 || recu == total) {
                "Téléchargement incomplet (${recu / 1_048_576} / ${total / 1_048_576} Mo). " +
                    "Vérifie la connexion puis recommence."
            }
            terminer(partiel, dest)
        } catch (e: Exception) {
            partiel.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private fun partiel(cible: File) = File(cible.parentFile, cible.name + ".part")

    /** Verifie le fichier recu puis le met a sa place definitive. */
    private fun terminer(partiel: File, cible: File) {
        check(estGguf(partiel)) {
            "Ce fichier n'est pas un modèle GGUF valide (mauvaise adresse ou fichier abîmé)."
        }
        if (cible.exists()) cible.delete()
        check(partiel.renameTo(cible)) { "Impossible d'enregistrer le modèle." }
    }

    /** Un fichier GGUF commence par les quatre octets "GGUF". */
    fun estGguf(fichier: File): Boolean = runCatching {
        val entete = ByteArray(4)
        fichier.inputStream().use { it.read(entete) } == 4 &&
            String(entete, Charsets.US_ASCII) == "GGUF"
    }.getOrDefault(false)

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
