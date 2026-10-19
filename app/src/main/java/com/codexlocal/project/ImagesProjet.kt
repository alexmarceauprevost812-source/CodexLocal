package com.codexlocal.project

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayOutputStream
import java.util.Locale

/**
 * Images d'un projet (bouton « ＋ ») : elles sont copiees dans le dossier images/ du projet,
 * puis embarquees dans l'APK par le Studio. L'icone de l'appli est l'image icone.png a la racine.
 */
object ImagesProjet {

    const val DOSSIER = "images"
    const val ICONE = "icone.png"

    /** Au-dela, l'image est reduite : un APK de telephone n'a pas besoin de photos de 12 Mpx. */
    private const val COTE_MAX = 2048
    private const val OCTETS_MAX_SANS_REDUCTION = 3 * 1024 * 1024
    private const val COTE_ICONE = 512

    private val extensions = setOf("png", "jpg", "jpeg", "gif", "webp")

    fun estImage(chemin: String): Boolean =
        chemin.substringAfterLast('.', "").lowercase(Locale.ROOT) in extensions

    /** Nom de fichier sur : lettres, chiffres, tiret, souligne ; extension en minuscules. */
    fun nomSur(nomOriginal: String?): String {
        val brut = nomOriginal.orEmpty().substringAfterLast('/').trim()
        val extension = brut.substringAfterLast('.', "").lowercase(Locale.ROOT).takeIf { it in extensions }
        val base = brut.substringBeforeLast('.', brut)
            .map { if (it.isLetterOrDigit() && it.code < 128 || it == '-' || it == '_') it else '_' }
            .joinToString("")
            .trim('_')
            .take(40)
            .ifEmpty { "image" }
        return base + "." + (extension ?: "png")
    }

    /** `images/logo.png`, ou `images/logo-2.png` si le nom est deja pris : on n'ecrase jamais. */
    fun cheminLibre(existants: Collection<String>, dossier: String, nom: String): String {
        val pris = existants.map { it.lowercase(Locale.ROOT) }.toSet()
        fun chemin(n: String) = if (dossier.isEmpty()) n else "$dossier/$n"
        if (chemin(nom).lowercase(Locale.ROOT) !in pris) return chemin(nom)
        val base = nom.substringBeforeLast('.')
        val extension = nom.substringAfterLast('.', "")
        var i = 2
        while (true) {
            val candidat = chemin("$base-$i" + (if (extension.isEmpty()) "" else ".$extension"))
            if (candidat.lowercase(Locale.ROOT) !in pris) return candidat
            i++
        }
    }

    /** Copie l'image dans images/ du projet. Renvoie le chemin cree (ex. images/logo.png). */
    fun importer(context: Context, projet: Projet, uri: Uri, nomAffiche: String?): String {
        val octets = lire(context, uri)
        val nom = nomSur(nomAffiche)
        val reduit = reduireSiBesoin(octets, nom)
        val existants = ProjectStore.instantane(context, projet).keys
        val chemin = cheminLibre(existants, DOSSIER, reduit.first)
        return ProjectStore.ecrireBinaire(context, projet, chemin, reduit.second)
    }

    /** Fait de l'image l'icone de l'appli : carre de 512 px enregistre dans icone.png (remplace l'ancienne). */
    fun definirIcone(context: Context, projet: Projet, uri: Uri): String {
        val octets = lire(context, uri)
        val source = BitmapFactory.decodeByteArray(octets, 0, octets.size)
            ?: error("Image illisible")
        val cote = minOf(source.width, source.height)
        val carre = Bitmap.createBitmap(source, (source.width - cote) / 2, (source.height - cote) / 2, cote, cote)
        val redimensionne = Bitmap.createScaledBitmap(carre, COTE_ICONE, COTE_ICONE, true)
        val sortie = ByteArrayOutputStream()
        redimensionne.compress(Bitmap.CompressFormat.PNG, 100, sortie)
        return ProjectStore.ecrireBinaire(context, projet, ICONE, sortie.toByteArray())
    }

    private fun lire(context: Context, uri: Uri): ByteArray {
        val octets = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Image illisible")
        require(octets.isNotEmpty()) { "Image vide" }
        require(octets.size <= 40 * 1024 * 1024) { "Image trop lourde (maximum 40 Mo)" }
        return octets
    }

    /** Garde les petites images telles quelles ; reduit les grandes (cote maximum 2048 px). */
    private fun reduireSiBesoin(octets: ByteArray, nom: String): Pair<String, ByteArray> {
        val bornes = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(octets, 0, octets.size, bornes)
        require(bornes.outWidth > 0 && bornes.outHeight > 0) { "Ce fichier n'est pas une image lisible" }
        val grande = maxOf(bornes.outWidth, bornes.outHeight) > COTE_MAX
        if (!grande && octets.size <= OCTETS_MAX_SANS_REDUCTION) return nom to octets

        val source = BitmapFactory.decodeByteArray(octets, 0, octets.size) ?: error("Image illisible")
        val echelle = minOf(1f, COTE_MAX.toFloat() / maxOf(source.width, source.height))
        val bitmap = if (echelle < 1f) {
            Bitmap.createScaledBitmap(
                source, (source.width * echelle).toInt().coerceAtLeast(1),
                (source.height * echelle).toInt().coerceAtLeast(1), true
            )
        } else source
        val sortie = ByteArrayOutputStream()
        val png = nom.endsWith(".png") || bitmap.hasAlpha()
        bitmap.compress(if (png) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 90, sortie)
        val base = nom.substringBeforeLast('.')
        return (base + if (png) ".png" else ".jpg") to sortie.toByteArray()
    }
}
