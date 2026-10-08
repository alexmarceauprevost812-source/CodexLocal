package com.codexlocal.project

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject

data class Projet(val nom: String, val uri: String, val ajoute: Long)

data class FichierProjet(
    val nom: String,
    val chemin: String,
    val uri: String,
    val taille: Long
)

/**
 * Les projets sont des dossiers choisis par l'utilisateur (Storage Access
 * Framework). On ne stocke que l'URI, avec une autorisation persistante.
 */
object ProjectStore {

    private const val PREFS = "codexlocal_projets"
    private const val CLE = "liste"

    /** Dossiers techniques qu'on ne parcourt pas. */
    private val ignores = setOf(
        "build", ".git", ".gradle", ".idea", ".cxx", "node_modules",
        ".dart_tool", "Pods", "venv", ".venv", "__pycache__", ".kotlin"
    )

    private const val MAX_FICHIERS = 400
    private const val PROFONDEUR_MAX = 8

    // ------------------------------------------------------------------ liste

    fun lister(context: Context): List<Projet> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val brut = sp.getString(CLE, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(brut)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri")
                if (uri.isBlank()) null
                else Projet(o.optString("nom"), uri, o.optLong("ajoute"))
            }
        }.getOrDefault(emptyList())
    }

    private fun ecrire(context: Context, projets: List<Projet>) {
        val arr = JSONArray()
        projets.forEach {
            arr.put(
                JSONObject()
                    .put("nom", it.nom)
                    .put("uri", it.uri)
                    .put("ajoute", it.ajoute)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CLE, arr.toString()).apply()
    }

    fun ajouter(context: Context, nom: String, uri: Uri) {
        val actuels = lister(context).filterNot { it.uri == uri.toString() }
        ecrire(context, actuels + Projet(nom, uri.toString(), System.currentTimeMillis()))
    }

    fun supprimer(context: Context, uri: String) {
        ecrire(context, lister(context).filterNot { it.uri == uri })
    }

    // --------------------------------------------------------------- fichiers

    /** Parcourt le dossier (profondeur et nombre limites) et renvoie les fichiers. */
    fun listerFichiers(context: Context, projet: Projet): List<FichierProjet> {
        val racine = DocumentFile.fromTreeUri(context, Uri.parse(projet.uri)) ?: return emptyList()
        val resultat = ArrayList<FichierProjet>()
        parcourir(racine, "", projet.nom, resultat, 0)
        return resultat.sortedWith(
            compareBy({ it.chemin.count { c -> c == '/' } }, { it.chemin.lowercase() })
        )
    }

    private fun parcourir(
        dossier: DocumentFile,
        prefixe: String,
        nomProjet: String,
        sortie: MutableList<FichierProjet>,
        profondeur: Int
    ) {
        if (profondeur > PROFONDEUR_MAX || sortie.size >= MAX_FICHIERS) return
        val enfants = runCatching { dossier.listFiles() }.getOrNull() ?: return

        // d'abord les fichiers, puis les sous-dossiers
        for (f in enfants) {
            if (sortie.size >= MAX_FICHIERS) return
            val nom = f.name ?: continue
            if (nom.startsWith(".")) continue
            if (!f.isDirectory) {
                if (!Langages.estTexte(nom)) continue
                sortie.add(
                    FichierProjet(
                        nom = nom,
                        chemin = if (prefixe.isEmpty()) nom else "$prefixe/$nom",
                        uri = f.uri.toString(),
                        taille = f.length()
                    )
                )
            }
        }
        for (d in enfants) {
            if (sortie.size >= MAX_FICHIERS) return
            val nom = d.name ?: continue
            if (!d.isDirectory) continue
            if (nom.startsWith(".") || nom in ignores) continue
            val chemin = if (prefixe.isEmpty()) nom else "$prefixe/$nom"
            parcourir(d, chemin, nomProjet, sortie, profondeur + 1)
        }
    }

    // ------------------------------------------------------------------- E/S

    fun lireTexte(context: Context, uri: Uri, limiteOctets: Int = 512 * 1024): String {
        val texte = context.contentResolver.openInputStream(uri)?.use { flux ->
            flux.bufferedReader().use { it.readText() }
        } ?: error("Fichier illisible")
        return if (texte.length > limiteOctets) texte.substring(0, limiteOctets) else texte
    }

    fun ecrireTexte(context: Context, uri: Uri, contenu: String) {
        context.contentResolver.openOutputStream(uri, "wt")?.use { flux ->
            flux.bufferedWriter().use { it.write(contenu) }
        } ?: error("Écriture impossible")
    }

    /** Nom lisible d'une URI de document. */
    fun nomDuDocument(context: Context, uri: Uri): String {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            runCatching {
                DocumentFile.fromSingleUri(context, uri)?.name?.let { return it }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "fichier"
    }
}
