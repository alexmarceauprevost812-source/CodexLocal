package com.codexlocal

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.ImageView
import java.io.File
import java.io.FileOutputStream

/**
 * Fond d'écran de la conversation : le logo MARCEAU par défaut, ou une image
 * choisie par l'utilisateur, affichée à 35 % de visibilité (réglable).
 */
object FondEcran {

    const val OPACITE_DEFAUT = 35

    private const val FICHIER = "fond_ecran.jpg"
    private const val MAX_COTE = 1600

    private fun fichier(context: Context) = File(context.filesDir, FICHIER)

    fun estPersonnalise(context: Context): Boolean = fichier(context).exists()

    /** Copie une version réduite de l'image choisie dans l'espace de l'appli. */
    fun enregistrer(context: Context, uri: Uri) {
        val resolver = context.contentResolver
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bornes) }
            ?: error("Image illisible")
        require(bornes.outWidth > 0 && bornes.outHeight > 0) { "Image illisible" }

        var echantillon = 1
        while (bornes.outWidth / (echantillon * 2) >= MAX_COTE ||
            bornes.outHeight / (echantillon * 2) >= MAX_COTE
        ) echantillon *= 2

        val options = BitmapFactory.Options().apply { inSampleSize = echantillon }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Image illisible")
        val temporaire = File(context.filesDir, "$FICHIER.tmp")
        try {
            FileOutputStream(temporaire).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            temporaire.copyTo(fichier(context), overwrite = true)
        } finally {
            bitmap.recycle()
            temporaire.delete()
        }
    }

    fun reinitialiser(context: Context) {
        fichier(context).delete()
    }

    /** Affiche le fond dans la vue, avec l'opacité demandée (0 à 100 %). */
    fun appliquer(context: Context, vue: ImageView, opacitePourcent: Int) {
        val bitmap = if (estPersonnalise(context)) {
            BitmapFactory.decodeFile(fichier(context).absolutePath)
        } else null
        if (bitmap != null) {
            vue.scaleType = ImageView.ScaleType.CENTER_CROP
            vue.setImageBitmap(bitmap)
        } else {
            vue.scaleType = ImageView.ScaleType.FIT_CENTER
            vue.setImageResource(R.drawable.logo_marceau)
        }
        vue.imageAlpha = opacitePourcent.coerceIn(0, 100) * 255 / 100
    }
}
