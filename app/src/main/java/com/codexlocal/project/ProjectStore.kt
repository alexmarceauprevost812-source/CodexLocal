package com.codexlocal.project

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Un projet est soit :
 *  - **interne** : un dossier dans l'espace de l'appli
 *    (/sdcard/Android/data/com.codexlocal/files/projets/…), accessible par USB
 *    et manipulable directement, sans permission particuliere ;
 *  - **externe** : un dossier choisi par l'utilisateur via Storage Access
 *    Framework (autorisation persistante).
 */
data class Projet(
    val nom: String,
    val uri: String,
    val ajoute: Long,
    val interne: Boolean
)

data class FichierProjet(
    val nom: String,
    val chemin: String,
    val uri: String,
    val taille: Long,
    val interne: Boolean
)

object ProjectStore {

    private const val PREFS = "codexlocal_projets"
    private const val CLE = "liste"

    private val ignores = setOf(
        "build", ".git", ".gradle", ".idea", ".cxx", "node_modules",
        ".dart_tool", "Pods", "venv", ".venv", "__pycache__", ".kotlin"
    )

    private const val MAX_FICHIERS = 400
    private const val PROFONDEUR_MAX = 8

    // ------------------------------------------------------- espace interne

    /** Dossier des projets internes, visible par USB. */
    fun dossierInterne(context: Context): File =
        File(context.getExternalFilesDir(null), "projets").apply { mkdirs() }

    /** Cree un projet interne avec un fichier de depart. */
    fun creerProjetInterne(context: Context, nom: String): Projet {
        val propre = nom.trim().ifBlank { "projet" }
            .replace(Regex("[^A-Za-z0-9 _\\-]"), "_")
            .replace(' ', '_')
        val dossier = File(dossierInterne(context), propre)
        dossier.mkdirs()
        File(dossier, "README.md").takeIf { !it.exists() }?.writeText(
            "# $propre\n\nProjet CodexLocal.\n",
            Charsets.UTF_8
        )
        return Projet(propre, dossier.absolutePath, System.currentTimeMillis(), interne = true)
    }

    // ---------------------------------------------------------------- liste

    fun lister(context: Context): List<Projet> {
        val sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val brut = sp.getString(CLE, null)
        val stockes = if (brut == null) emptyList() else runCatching {
            val arr = JSONArray(brut)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val uri = o.optString("uri")
                if (uri.isBlank()) null
                else Projet(
                    nom = o.optString("nom"),
                    uri = uri,
                    ajoute = o.optLong("ajoute"),
                    interne = o.optBoolean("interne", false)
                )
            }
        }.getOrDefault(emptyList())

        // On ajoute les dossiers internes presents mais pas encore enregistres
        val connus = stockes.filter { it.interne }.map { it.uri }.toSet()
        val decouverts = dossierInterne(context).listFiles { f -> f.isDirectory }
            ?.filter { it.absolutePath !in connus }
            ?.map { Projet(it.name, it.absolutePath, it.lastModified(), interne = true) }
            ?: emptyList()

        return stockes + decouverts
    }

    private fun ecrire(context: Context, projets: List<Projet>) {
        val arr = JSONArray()
        projets.forEach {
            arr.put(
                JSONObject()
                    .put("nom", it.nom)
                    .put("uri", it.uri)
                    .put("ajoute", it.ajoute)
                    .put("interne", it.interne)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(CLE, arr.toString()).apply()
    }

    fun ajouter(context: Context, projet: Projet) {
        val actuels = lister(context).filterNot { it.uri == projet.uri }
        ecrire(context, actuels + projet)
    }

    fun supprimer(context: Context, uri: String) {
        ecrire(context, lister(context).filterNot { it.uri == uri })
    }

    // ------------------------------------------------------------- fichiers

    fun listerFichiers(context: Context, projet: Projet): List<FichierProjet> {
        val resultat = ArrayList<FichierProjet>()
        if (projet.interne) {
            parcourirDossier(File(projet.uri), "", resultat, 0)
        } else {
            val racine = DocumentFile.fromTreeUri(context, Uri.parse(projet.uri))
                ?: return emptyList()
            parcourirDocument(racine, "", resultat, 0)
        }
        return resultat.sortedWith(
            compareBy({ it.chemin.count { c -> c == '/' } }, { it.chemin.lowercase() })
        )
    }

    private fun parcourirDossier(
        dossier: File,
        prefixe: String,
        sortie: MutableList<FichierProjet>,
        profondeur: Int
    ) {
        if (profondeur > PROFONDEUR_MAX || sortie.size >= MAX_FICHIERS) return
        val enfants = dossier.listFiles() ?: return

        for (f in enfants) {
            if (sortie.size >= MAX_FICHIERS) return
            if (f.isFile && !f.name.startsWith(".") && Langages.estTexte(f.name)) {
                sortie.add(
                    FichierProjet(
                        nom = f.name,
                        chemin = if (prefixe.isEmpty()) f.name else "$prefixe/${f.name}",
                        uri = f.absolutePath,
                        taille = f.length(),
                        interne = true
                    )
                )
            }
        }
        for (d in enfants) {
            if (sortie.size >= MAX_FICHIERS) return
            if (!d.isDirectory) continue
            if (d.name.startsWith(".") || d.name in ignores) continue
            val chemin = if (prefixe.isEmpty()) d.name else "$prefixe/${d.name}"
            parcourirDossier(d, chemin, sortie, profondeur + 1)
        }
    }

    private fun parcourirDocument(
        dossier: DocumentFile,
        prefixe: String,
        sortie: MutableList<FichierProjet>,
        profondeur: Int
    ) {
        if (profondeur > PROFONDEUR_MAX || sortie.size >= MAX_FICHIERS) return
        val enfants = runCatching { dossier.listFiles() }.getOrNull() ?: return

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
                        taille = f.length(),
                        interne = false
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
            parcourirDocument(d, chemin, sortie, profondeur + 1)
        }
    }

    /**
     * Crée un fichier texte dans un projet. Le nom peut inclure des sous-dossiers
     * relatifs (ex. src/main.py). Pas de chemin absolu, ni de '..', ni d'ecrasement.
     * Le dossier externe doit avoir ete choisi via ACTION_OPEN_DOCUMENT_TREE.
     */
    fun creerFichier(
        context: Context,
        projet: Projet,
        cheminRelatif: String,
        contenu: String = ""
    ): FichierProjet {
        val segments = validerCheminRelatif(cheminRelatif)
        val nom = segments.last()
        require(Langages.estTexte(nom)) {
            "Choisis un fichier texte ou de code (ex. main.py, index.html, README.md)"
        }

        if (projet.interne) {
            val racine = File(projet.uri).canonicalFile
            require(racine.isDirectory) { "Dossier de projet introuvable" }
            var dossier = racine
            for (segment in segments.dropLast(1)) {
                dossier = File(dossier, segment)
                if (!dossier.isDirectory && !dossier.mkdir()) {
                    require(dossier.isDirectory) { "Impossible de créer le sous-dossier" }
                }
                require(dossier.canonicalFile.toPath().startsWith(racine.toPath())) {
                    "Chemin de fichier refusé"
                }
            }
            val cible = File(dossier, nom)
            require(cible.canonicalFile.toPath().startsWith(racine.toPath())) {
                "Chemin de fichier refusé"
            }
            check(cible.createNewFile()) { "Un fichier portant ce nom existe déjà" }
            try {
                if (contenu.isNotEmpty()) cible.writeText(contenu, Charsets.UTF_8)
            } catch (e: Exception) {
                cible.delete()
                throw e
            }
            return FichierProjet(nom, segments.joinToString("/"), cible.absolutePath,
                cible.length(), interne = true)
        }

        var dossier = DocumentFile.fromTreeUri(context, Uri.parse(projet.uri))
            ?: error("Dossier du téléphone inaccessible")
        require(dossier.isDirectory && dossier.canWrite()) {
            "Ce dossier ne permet pas l'écriture : autorise l'accès dans Android"
        }
        for (segment in segments.dropLast(1)) {
            val existant = dossier.findFile(segment)
            dossier = if (existant == null) {
                dossier.createDirectory(segment) ?: error("Création du dossier impossible : $segment")
            } else {
                require(existant.isDirectory) { "Ce nom est déjà utilisé par un fichier : $segment" }
                existant
            }
        }
        check(dossier.findFile(nom) == null) { "Un fichier portant ce nom existe déjà" }
        val document = dossier.createFile("text/plain", nom)
            ?: error("Création du fichier refusée par Android")
        try {
            if (contenu.isNotEmpty()) {
                context.contentResolver.openOutputStream(document.uri, "wt")?.use {
                    it.write(contenu.toByteArray(Charsets.UTF_8))
                } ?: error("Écriture impossible dans ce dossier")
            }
        } catch (e: Exception) {
            document.delete()
            throw e
        }
        return FichierProjet(document.name ?: nom,
            (segments.dropLast(1) + (document.name ?: nom)).joinToString("/"),
            document.uri.toString(), document.length(), interne = false)
    }

    /**
     * Crée un dossier (et ses parents) dans un projet. Même règles de chemin que
     * pour les fichiers : relatif, sans '..', sans nom caché. Renvoie le chemin créé.
     */
    fun creerDossier(context: Context, projet: Projet, cheminRelatif: String): String {
        val segments = validerCheminRelatif(cheminRelatif)
        var existaitDeja = true

        if (projet.interne) {
            val racine = File(projet.uri).canonicalFile
            require(racine.isDirectory) { "Dossier de projet introuvable" }
            var dossier = racine
            for (segment in segments) {
                dossier = File(dossier, segment)
                require(dossier.canonicalFile.toPath().startsWith(racine.toPath())) {
                    "Chemin de dossier refusé"
                }
                if (!dossier.isDirectory) {
                    require(!dossier.exists()) { "Ce nom est déjà utilisé par un fichier : $segment" }
                    check(dossier.mkdir()) { "Impossible de créer le dossier : $segment" }
                    existaitDeja = false
                }
            }
        } else {
            var dossier = DocumentFile.fromTreeUri(context, Uri.parse(projet.uri))
                ?: error("Dossier du téléphone inaccessible")
            require(dossier.isDirectory && dossier.canWrite()) {
                "Ce dossier ne permet pas l'écriture : autorise l'accès dans Android"
            }
            for (segment in segments) {
                val existant = dossier.findFile(segment)
                dossier = if (existant == null) {
                    existaitDeja = false
                    dossier.createDirectory(segment)
                        ?: error("Création du dossier impossible : $segment")
                } else {
                    require(existant.isDirectory) { "Ce nom est déjà utilisé par un fichier : $segment" }
                    existant
                }
            }
        }
        check(!existaitDeja) { "Ce dossier existe déjà" }
        return segments.joinToString("/")
    }

    /** Chemins relatifs de tous les dossiers du projet, y compris les dossiers vides. */
    fun listerDossiers(context: Context, projet: Projet): List<String> {
        val resultat = ArrayList<String>()
        if (projet.interne) {
            parcourirDossiers(File(projet.uri), "", resultat, 0)
        } else {
            val racine = DocumentFile.fromTreeUri(context, Uri.parse(projet.uri))
                ?: return emptyList()
            parcourirDocumentsDossiers(racine, "", resultat, 0)
        }
        return resultat.sortedBy { it.lowercase() }
    }

    private fun parcourirDossiers(
        dossier: File, prefixe: String, sortie: MutableList<String>, profondeur: Int
    ) {
        if (profondeur > PROFONDEUR_MAX || sortie.size >= MAX_FICHIERS) return
        val enfants = dossier.listFiles() ?: return
        for (d in enfants) {
            if (!d.isDirectory || d.name.startsWith(".") || d.name in ignores) continue
            val chemin = if (prefixe.isEmpty()) d.name else "$prefixe/${d.name}"
            sortie.add(chemin)
            parcourirDossiers(d, chemin, sortie, profondeur + 1)
        }
    }

    private fun parcourirDocumentsDossiers(
        dossier: DocumentFile, prefixe: String, sortie: MutableList<String>, profondeur: Int
    ) {
        if (profondeur > PROFONDEUR_MAX || sortie.size >= MAX_FICHIERS) return
        val enfants = runCatching { dossier.listFiles() }.getOrNull() ?: return
        for (d in enfants) {
            val nom = d.name ?: continue
            if (!d.isDirectory || nom.startsWith(".") || nom in ignores) continue
            val chemin = if (prefixe.isEmpty()) nom else "$prefixe/$nom"
            sortie.add(chemin)
            parcourirDocumentsDossiers(d, chemin, sortie, profondeur + 1)
        }
    }

    /** Un chemin relatif, sans traversal, caractères de contrôle ni nom caché. */
    fun validerCheminRelatif(chemin: String): List<String> {
        val valeur = chemin.trim()
        require(valeur.isNotEmpty() && valeur.length <= 240) { "Nom du fichier invalide" }
        require(!valeur.startsWith("/") && !valeur.contains('\\') &&
            !valeur.contains(':') && !valeur.contains('\u0000')) {
            "Utilise un chemin relatif, comme src/main.py"
        }
        val morceaux = valeur.split('/')
        require(morceaux.size <= 8 && morceaux.all {
            it.isNotBlank() && it != "." && it != ".." &&
                !it.startsWith(".") && it.length <= 90 &&
                it.none { char -> Character.isISOControl(char) }
        }) { "Nom interdit : pas de dossier caché, de '..' ou de chemin absolu" }
        return morceaux
    }

    // ------------------------------------------------------------------- E/S

    fun lireTexte(context: Context, fichier: FichierProjet): String =
        if (fichier.interne) {
            File(fichier.uri).readText(Charsets.UTF_8)
        } else {
            context.contentResolver.openInputStream(Uri.parse(fichier.uri))
                ?.use { it.bufferedReader().use { r -> r.readText() } }
                ?: error("Fichier illisible")
        }

    fun ecrireTexte(context: Context, fichier: FichierProjet, contenu: String) {
        if (fichier.interne) {
            File(fichier.uri).writeText(contenu, Charsets.UTF_8)
        } else {
            context.contentResolver.openOutputStream(Uri.parse(fichier.uri), "wt")
                ?.use { it.bufferedWriter().use { w -> w.write(contenu) } }
                ?: error("Écriture impossible")
        }
    }
}
