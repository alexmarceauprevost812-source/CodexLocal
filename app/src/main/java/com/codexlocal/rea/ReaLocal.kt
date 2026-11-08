package com.codexlocal.rea

import android.content.Context
import com.codexlocal.project.ProjectStore
import com.codexlocal.project.Projet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Moteur REA du téléphone : mêmes noms d'outils et même boucle que le serveur REA du PC, mais
 * l'analyse se fait ici, hors-ligne, sur les fichiers du dossier `analyse/` du projet ouvert.
 * Lecture seule : aucun fichier n'est exécuté, installé ni envoyé ailleurs.
 */
class ReaLocal(private val context: Context, private val projet: Projet?) : OutilsRea {
    private val preuves = JSONArray()

    override suspend fun connect(): String {
        val fichiers = projet?.let { p ->
            withContext(Dispatchers.IO) {
                runCatching { ProjectStore.instantane(context, p).keys.filter { it.startsWith("$DOSSIER/") }.take(40) }
                    .getOrDefault(emptyList())
            }
        } ?: emptyList()
        val contexte = when {
            projet == null -> "Aucun projet n'est ouvert : demande à l'utilisateur d'ouvrir un projet puis d'ajouter le fichier avec ＋."
            fichiers.isEmpty() -> "Le dossier $DOSSIER/ du projet est vide : l'utilisateur ajoute un fichier à analyser avec ＋ (Ajouter un fichier à analyser)."
            else -> "Fichiers disponibles : " + fichiers.joinToString(", ")
        }
        return ReaProtocol.consigne(CATALOGUE.toString(), local = true) + "\n" + contexte + "\n" +
            "Limites de ce moteur : " + AnalyseLocale.limites.joinToString(" ")
    }

    override suspend fun call(call: JSONObject, maxChars: Int): String = withContext(Dispatchers.IO) {
        val nom = call.getString("name")
        val args = call.getJSONObject("arguments")
        val sortie = when (nom) {
            "list_unknowns" -> JSONObject().put("unknowns", JSONArray(AnalyseLocale.limites))
            "get_evidence_bundle" -> JSONObject().put("evidence", preuves)
            "inspect_artifact", "inspect_apk_signature", "analyze_javascript_application" -> {
                val chemin = args.optString("path").trim()
                require(chemin.isNotEmpty()) { "Argument path requis (fichier du dossier $DOSSIER/ du projet)." }
                requireNotNull(projet) { "Aucun projet ouvert : ouvre un projet et ajoute le fichier avec ＋." }
                val copie = copier(projet, chemin)
                try {
                    val r = when (nom) {
                        "inspect_artifact" -> AnalyseLocale.inspecter(copie)
                        "inspect_apk_signature" -> AnalyseLocale.signature(copie)
                        else -> AnalyseLocale.javascript(copie)
                    }
                    preuves.put(JSONObject().put("tool", nom).put("path", chemin).put("sha256", AnalyseLocale.sha256(copie)))
                    r
                } finally { copie.delete() }
            }
            else -> error("Outil absent du catalogue local.")
        }
        ReaProtocol.tronquer(JSONObject().put("content", sortie).put("source", "analyse locale sur le téléphone").toString(), maxChars)
    }

    override suspend fun close() {}

    /** Copie temporaire (les analyseurs lisent des fichiers), plafonnée en taille. */
    private fun copier(p: Projet, chemin: String): File {
        val flux = ProjectStore.ouvrirFlux(context, p, chemin) ?: error("Fichier introuvable dans le projet : $chemin")
        val tmp = File.createTempFile("rea", ".bin", context.cacheDir)
        try {
            flux.use { src -> tmp.outputStream().use { dst ->
                val b = ByteArray(64 * 1024); var total = 0L
                while (true) {
                    val n = src.read(b); if (n < 0) break
                    total += n
                    require(total <= AnalyseLocale.TAILLE_MAX) { "Fichier trop gros pour l'analyse locale (200 Mo maximum)." }
                    dst.write(b, 0, n)
                }
            } }
            return tmp
        } catch (e: Throwable) { tmp.delete(); throw e }
    }

    companion object {
        const val DOSSIER = "analyse"
        const val IMPORT_MAX = 64L * 1024 * 1024

        private fun outil(nom: String, description: String, chemin: Boolean) = JSONObject()
            .put("name", nom).put("description", description)
            .put("inputSchema", JSONObject().put("type", "object").apply {
                put("properties", JSONObject().apply {
                    if (chemin) put("path", JSONObject().put("type", "string").put("description", "Chemin relatif dans le projet, ex. $DOSSIER/app.apk"))
                })
                if (chemin) put("required", JSONArray().put("path"))
            })

        val CATALOGUE: JSONArray = JSONArray()
            .put(outil("inspect_artifact", "Taille, empreinte SHA-256, type ; pour un APK/zip : entrées, DEX, bibliothèques natives, permissions.", true))
            .put(outil("inspect_apk_signature", "Vérifie la signature d'un APK (schémas v1/v2/v3) et donne l'empreinte des certificats.", true))
            .put(outil("analyze_javascript_application", "Cherche dans le JavaScript/HTML d'un fichier ou d'un APK : eval, fetch, stockage, domaines, obfuscation.", true))
            .put(outil("get_evidence_bundle", "Liste des preuves (outil, fichier, SHA-256) obtenues pendant cette demande.", false))
            .put(outil("list_unknowns", "Ce que ce moteur local ne peut pas déterminer.", false))

        /** Nom de fichier sûr pour le dossier d'analyse. */
        fun nomSur(brut: String?): String {
            val base = (brut ?: "fichier").substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").trim('.', '_')
            return base.ifEmpty { "fichier" }.take(80)
        }

        /** Ajoute un fichier au dossier d'analyse du projet. Renvoie son chemin. */
        fun importer(context: Context, projet: Projet, flux: java.io.InputStream, nom: String?): String {
            val b = java.io.ByteArrayOutputStream()
            flux.use { s ->
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val n = s.read(buf); if (n < 0) break
                    require(b.size() + n <= IMPORT_MAX) { "Fichier trop gros (64 Mo maximum)." }
                    b.write(buf, 0, n)
                }
            }
            return ProjectStore.ecrireBinaire(context, projet, "$DOSSIER/${nomSur(nom)}", b.toByteArray())
        }
    }
}
