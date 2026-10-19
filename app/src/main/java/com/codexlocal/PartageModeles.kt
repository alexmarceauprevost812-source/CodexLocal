package com.codexlocal

import android.content.Context
import android.os.Environment
import java.io.File

/**
 * Partage des modeles .gguf avec d'autres applis (par exemple LexOS Terminal).
 *
 * Par defaut CodexLocal range ses modeles dans son dossier PRIVE :
 *   /sdcard/Android/data/com.codexlocal/files/models/
 * Aucune autre appli ne peut lire ce dossier (regle Android depuis la version 11).
 *
 * Quand le partage est active, les modeles sont deplaces dans un dossier PUBLIC
 * commun que les autres applis peuvent lire :
 *   /sdcard/CodexLocal/models/
 * Cote Termux / LexOS, ce meme dossier s'appelle :
 *   ~/storage/shared/CodexLocal/models/
 *
 * Ecrire dans ce dossier public demande la permission « Acces a tous les fichiers »
 * (MANAGE_EXTERNAL_STORAGE), que l'utilisateur accorde une seule fois.
 */
object PartageModeles {

    /** Chemin a montrer a l'utilisateur pour le configurer cote LexOS / Termux. */
    const val CHEMIN_TERMUX = "~/storage/shared/CodexLocal/models"

    private const val CLE_PARTAGE = "partageModeles"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("codexlocal", Context.MODE_PRIVATE)

    /** Le dossier public commun. */
    fun dossierPartage(): File =
        File(Environment.getExternalStorageDirectory(), "CodexLocal/models")

    /** Le dossier prive habituel de CodexLocal. */
    fun dossierPrive(context: Context): File =
        File(context.getExternalFilesDir(null), "models")

    /** L'appli a-t-elle la permission d'ecrire dans le stockage partage ? */
    fun peutPartager(): Boolean = Environment.isExternalStorageManager()

    /** Le partage est-il demande par l'utilisateur ? */
    fun demande(context: Context): Boolean =
        prefs(context).getBoolean(CLE_PARTAGE, false)

    /** Le partage est-il reellement actif (demande ET permission accordee) ? */
    fun actif(context: Context): Boolean = demande(context) && peutPartager()

    fun definirDemande(context: Context, valeur: Boolean) {
        prefs(context).edit().putBoolean(CLE_PARTAGE, valeur).apply()
    }

    /** Resultat d'un deplacement. */
    data class Resultat(val deplaces: Int, val dossier: File)

    /**
     * Deplace tous les .gguf du dossier prive vers le dossier partage.
     * Met a jour le chemin du modele actif si c'est l'un de ceux qui bougent.
     * Idempotent : relancer ne fait rien de mal.
     */
    fun deplacerTout(context: Context): Resultat {
        val cible = dossierPartage()
        if (!cible.exists()) cible.mkdirs()

        val source = dossierPrive(context)
        val prefs = Prefs(context)
        val cheminActif = prefs.localModelPath

        var deplaces = 0
        val fichiers = source.listFiles { f: File ->
            f.isFile && f.name.endsWith(".gguf", ignoreCase = true)
        } ?: emptyArray()

        for (f in fichiers) {
            val dest = File(cible, f.name)
            if (dest.exists()) {
                // Deja present cote partage : on garde celui qui est la, on efface le double.
                if (dest.length() == f.length()) {
                    f.delete()
                    if (cheminActif == f.absolutePath) prefs.localModelPath = dest.absolutePath
                    continue
                }
                dest.delete()
            }
            val ok = f.renameTo(dest) || copierPuisEffacer(f, dest)
            if (ok) {
                deplaces++
                if (cheminActif == f.absolutePath) prefs.localModelPath = dest.absolutePath
            }
        }
        return Resultat(deplaces, cible)
    }

    /** renameTo echoue parfois entre deux volumes : on copie puis on efface. */
    private fun copierPuisEffacer(source: File, dest: File): Boolean = runCatching {
        source.inputStream().use { input ->
            dest.outputStream().use { output -> input.copyTo(output, 1 shl 16) }
        }
        source.delete()
        true
    }.getOrElse {
        dest.delete()
        false
    }
}
