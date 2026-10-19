package com.codexlocal.studio.apk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.ByteArrayOutputStream

/** Icone de l'appli fabriquee : une image du projet, rognee en carre puis mise a toutes les tailles. */
object IconesApk {

    /** Dans l'ordre de preference : le premier fichier trouve sert d'icone. */
    val NOMS_ICONE = listOf(
        "icone.png", "icon.png", "images/icone.png", "images/icon.png",
        "icone.jpg", "icone.jpeg", "icone.webp", "icon.jpg", "icon.webp",
        "images/icone.jpg", "images/icone.webp"
    )

    /**
     * Tailles du lanceur Android (pixels) par densite. Ce sont aussi les tailles des cinq icones
     * du modele (module :shell) : on les reconnait a leurs dimensions (voir [taillePourEntree]).
     */
    val TAILLES = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)

    /**
     * Si cette entree de l'APK est une icone du lanceur du modele, sa taille en pixels, sinon null.
     *
     * On ne se fie pas au nom : Android Gradle raccourcit les chemins de ressources (`res/-B.png`,
     * `res/2P.png`…). Le modele n'a pas d'autre image que ses cinq icones carrees, de tailles connues.
     */
    fun taillePourEntree(nom: String, octets: ByteArray): Int? {
        if (!nom.startsWith("res/") || !nom.endsWith(".png")) return null
        val (largeur, hauteur) = PngEntete.dimensions(octets) ?: return null
        return largeur.takeIf { largeur == hauteur && it in TAILLES.values }
    }

    /** Chemin du fichier a utiliser comme icone parmi les fichiers du projet, ou null. */
    fun trouver(chemins: Collection<String>): String? =
        NOMS_ICONE.firstOrNull { it in chemins }

    /** PNG carre de `taille` pixels (image rognee au centre), ou null si l'image est illisible. */
    fun versPng(image: ByteArray, taille: Int): ByteArray? {
        val source = BitmapFactory.decodeByteArray(image, 0, image.size) ?: return null
        val cote = minOf(source.width, source.height)
        val carre = Bitmap.createBitmap(
            source, (source.width - cote) / 2, (source.height - cote) / 2, cote, cote
        )
        val redimensionne = Bitmap.createScaledBitmap(carre, taille, taille, true)
        val sortie = ByteArrayOutputStream()
        redimensionne.compress(Bitmap.CompressFormat.PNG, 100, sortie)
        if (carre !== source) carre.recycle()
        if (redimensionne !== carre) redimensionne.recycle()
        source.recycle()
        return sortie.toByteArray()
    }
}
