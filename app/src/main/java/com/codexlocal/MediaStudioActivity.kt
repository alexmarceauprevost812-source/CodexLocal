package com.codexlocal

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.VideoView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/** Import et aperçu de médias, sans toucher aux modèles IA locaux. */
class MediaStudioActivity : AppCompatActivity() {
    private lateinit var container: LinearLayout
    private val mediaDir by lazy { File(filesDir, "studio-media").apply { mkdirs() } }
    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importMedia(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "MARCEAU · Studio multimédia"
        container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
            setBackgroundColor(0xff080808.toInt())
        }
        val scroll = android.widget.ScrollView(this).apply { addView(container) }
        setContentView(scroll)
        addButton("＋ Ajouter une image ou une petite vidéo") {
            picker.launch(arrayOf("image/*", "video/mp4", "video/webm"))
        }
        addButton("📁 Voir mes médias") { showFiles() }
        showFiles()
    }

    private fun addButton(label: String, click: () -> Unit) {
        container.addView(Button(this).apply {
            text = label
            isAllCaps = false
            setTextColor(0xffff8500.toInt())
            setOnClickListener { click() }
        })
    }

    private fun showFiles() {
        while (container.childCount > 2) container.removeViewAt(2)
        val files = mediaDir.listFiles()?.filter { it.isFile }?.sortedByDescending { it.lastModified() }.orEmpty()
        if (files.isEmpty()) {
            container.addView(TextView(this).apply {
                text = "Aucun média importé pour le moment."
                setTextColor(-1)
            })
        }
        files.forEach { file ->
            addButton("👁  ${file.name} (${file.length() / 1024} Ko)") { preview(file) }
        }
    }

    private fun importMedia(uri: Uri) {
        val mime = contentResolver.getType(uri).orEmpty()
        if (!mime.startsWith("image/") && mime != "video/mp4" && mime != "video/webm") {
            message("Format non pris en charge."); return
        }
        val maxBytes = if (mime.startsWith("video/")) 25L * 1024 * 1024 else 15L * 1024 * 1024
        val ext = when (mime) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            "video/mp4" -> ".mp4"
            "video/webm" -> ".webm"
            else -> ".jpg"
        }
        val file = File(mediaDir, "media-${System.currentTimeMillis()}$ext")
        try {
            contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input)
                file.outputStream().use { output ->
                    val buf = ByteArray(8192)
                    var count = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        count += n
                        require(count <= maxBytes) { "Fichier trop volumineux (limite : ${maxBytes / 1024 / 1024} Mo)." }
                        output.write(buf, 0, n)
                    }
                }
            }
            showFiles()
            preview(file)
        } catch (e: Exception) {
            file.delete()
            message("Import impossible : ${e.message}")
        }
    }

    private fun preview(file: File) {
        val ext = file.extension.lowercase()
        val view = if (ext == "mp4" || ext == "webm") {
            VideoView(this).apply {
                setVideoPath(file.absolutePath)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 520)
                setOnPreparedListener { it.isLooping = true; start() }
            }
        } else {
            ImageView(this).apply {
                setImageURI(Uri.fromFile(file))
                adjustViewBounds = true
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 520)
                scaleType = ImageView.ScaleType.FIT_CENTER
            }
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(file.name)
            .setView(view)
            .setPositiveButton("Fermer", null)
            .setNeutralButton("Supprimer") { _, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle("Supprimer ce média ?")
                    .setMessage("Seule la copie du Studio sera supprimée.")
                    .setPositiveButton("Supprimer") { _, _ -> file.delete(); showFiles() }
                    .setNegativeButton("Annuler", null)
                    .show()
            }
            .show()
    }

    private fun message(s: String) {
        MaterialAlertDialogBuilder(this).setMessage(s).setPositiveButton("OK", null).show()
    }
}
