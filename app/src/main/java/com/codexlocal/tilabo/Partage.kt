package com.codexlocal.tilabo

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Partager un projet (en .zip) ou un fichier : Gmail, Messenger, Drive, Bluetooth… ou telecharger sur le PC. */
object Partage {

    private val IGNORES = setOf("node_modules", "build", ".gradle", "__pycache__", ".venv", "venv", ".cxx", "dist")
    private const val MAX = 200L * 1024 * 1024

    /** (nom du fichier, contenu) : le fichier demande, ou tout le projet en .zip. */
    fun octets(projet: File, chemin: String?): Pair<String, ByteArray> {
        if (!chemin.isNullOrBlank()) {
            val f = File(projet, chemin.trimStart('/')).canonicalFile
            require(f.path.startsWith(projet.path + File.separator) && f.isFile) { "Fichier introuvable : $chemin" }
            require(f.length() < MAX) { "Fichier trop gros" }
            return f.name to f.readBytes()
        }
        val b = ByteArrayOutputStream()
        var total = 0L
        ZipOutputStream(b).use { zip ->
            fun ajouter(x: File, pre: String) {
                x.listFiles()?.sortedBy { it.name }?.forEach { f ->
                    if (f.isDirectory) { if (f.name !in IGNORES) ajouter(f, "$pre${f.name}/") }
                    else if (!f.name.startsWith(".tilabo-")) {
                        total += f.length(); require(total < MAX) { "Projet trop gros pour être partagé (plus de 200 Mo)" }
                        zip.putNextEntry(ZipEntry("${projet.name}/$pre${f.name}").apply { time = f.lastModified() })
                        f.inputStream().use { it.copyTo(zip) }
                        zip.closeEntry()
                    }
                }
            }
            ajouter(projet, "")
        }
        return "${projet.name}.zip" to b.toByteArray()
    }

    /** Ouvre la feuille de partage d'Android. */
    fun partager(ctx: Context, projet: File, chemin: String?) {
        val (nom, contenu) = octets(projet, chemin)
        val dossier = File(ctx.cacheDir, "partage").apply { deleteRecursively(); mkdirs() }
        val f = File(dossier, nom).apply { writeBytes(contenu) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val type = if (nom.endsWith(".zip")) "application/zip" else
            android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension.lowercase()) ?: "text/plain"
        val i = Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, nom).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Partager $nom").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
