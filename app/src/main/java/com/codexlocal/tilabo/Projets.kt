package com.codexlocal.tilabo

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Projets du Codex de l'appli : lire et ecrire les fichiers, toujours a l'interieur d'un projet.
 * Projets dans TiLabo/projets (telephone et cle USB-C) et CodexLocal/projets s'il existe.
 */
object Projets {

    /** Pour retrouver aussi les projets internes de CodexLocal (meme appli). */
    @Volatile var contexte: android.content.Context? = null

    private const val TEL = "/storage/emulated/0"
    private val IGNORES = setOf(".git", "node_modules", "build", ".gradle", "__pycache__", ".idea", ".venv", "venv", "dist", ".cxx")
    private const val MAX_FICHIERS = 500
    private const val MAX_LECTURE = 400_000L

    fun racines(): List<Pair<String, File>> {
        val res = mutableListOf("Téléphone" to File("$TEL/TiLabo/projets").apply { mkdirs() })
        contexte?.let { c -> runCatching { res += "CodexLocal" to com.codexlocal.project.ProjectStore.dossierInterne(c) } }
        File("$TEL/CodexLocal/projets").takeIf { it.isDirectory }?.let { res += "CodexLocal" to it }
        File("/storage").listFiles()?.sortedBy { it.name }?.forEach { cle ->
            if (cle.name == "emulated" || cle.name == "self" || !cle.isDirectory) return@forEach
            listOf("TiLabo/projets", "TiLabo/TiLabo/projets").map { File(cle, it) }
                .firstOrNull { it.isDirectory }?.let { res += "Clé ${cle.name}" to it }
        }
        return res
    }

    fun lister(): JSONArray {
        val arr = JSONArray()
        for ((ou, r) in racines()) {
            r.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedByDescending { it.lastModified() }
                ?.forEach { arr.put(JSONObject().put("nom", it.name).put("chemin", it.path).put("ou", ou)) }
        }
        return arr
    }

    fun creer(nom: String): JSONObject {
        val propre = nom.trim().replace(Regex("[^A-Za-z0-9 _.\\-àâäéèêëîïôöùûüçÀÂÉÈÊÎÔÙÛÇ]"), "").trim().trim('.')
        require(propre.isNotEmpty() && propre.length <= 60) { "Nom de projet invalide" }
        val d = File(racines().first().second, propre)
        require(!d.exists()) { "Ce projet existe déjà" }
        check(d.mkdirs()) { "Impossible de créer le dossier" }
        File(d, "README.md").writeText("# $propre\n\nProjet créé avec le Codex du Ti-Labo.\n")
        return JSONObject().put("nom", d.name).put("chemin", d.path).put("ou", "Téléphone")
    }

    /** Le dossier du projet, seulement s'il est dans une des racines. */
    fun projet(chemin: String): File {
        val d = File(chemin).canonicalFile
        val ok = racines().any { (_, r) -> d.parentFile?.canonicalPath == r.canonicalPath }
        require(ok && d.isDirectory) { "Projet inconnu : $chemin" }
        return d
    }

    /** Un fichier a l'interieur du projet (refuse ../ et les chemins absolus). */
    private fun dedans(projet: File, rel: String): File {
        val propre = rel.trim().trimStart('/', '\\').replace('\\', '/')
        require(propre.isNotEmpty() && !propre.split('/').any { it == ".." }) { "Chemin invalide : $rel" }
        val f = File(projet, propre).canonicalFile
        require(f.path.startsWith(projet.path + File.separator)) { "Chemin hors du projet : $rel" }
        return f
    }

    fun arbre(projet: File): JSONArray {
        val arr = JSONArray()
        fun parcourir(d: File, prefixe: String, prof: Int) {
            if (prof > 8 || arr.length() >= MAX_FICHIERS) return
            d.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))?.forEach { f ->
                if (arr.length() >= MAX_FICHIERS) return
                if (f.isDirectory) {
                    if (f.name !in IGNORES) parcourir(f, "$prefixe${f.name}/", prof + 1)
                } else if (!f.name.startsWith(".tilabo-")) {
                    arr.put(JSONObject().put("chemin", prefixe + f.name).put("taille", f.length()))
                }
            }
        }
        parcourir(projet, "", 0)
        return arr
    }

    fun lire(projet: File, rel: String): String {
        val f = dedans(projet, rel)
        require(f.isFile) { "Fichier introuvable : $rel" }
        require(f.length() <= MAX_LECTURE) { "Fichier trop gros pour l'IA : $rel" }
        return f.readText()
    }

    /** Ecrit le fichier ; renvoie l'ancien contenu (null si nouveau) pour pouvoir annuler. */
    fun ecrire(projet: File, rel: String, contenu: String?): JSONObject {
        val f = dedans(projet, rel)
        val ancien = if (f.isFile && f.length() <= MAX_LECTURE) f.readText() else null
        val existait = f.exists()
        if (contenu == null) {
            // annulation d'une creation : on vide le fichier plutot que de le supprimer
            if (existait) f.writeText("")
        } else {
            f.parentFile?.mkdirs()
            f.writeText(contenu)
        }
        return JSONObject().put("ok", true).put("nouveau", !existait)
            .put("ancien", ancien ?: JSONObject.NULL)
            .put("chemin", projet.toPath().relativize(f.toPath()).toString())
    }
}
