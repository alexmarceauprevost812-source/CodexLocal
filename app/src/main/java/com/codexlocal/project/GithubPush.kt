package com.codexlocal.project

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pousse les fichiers d'un projet vers un depot GitHub, en un seul commit,
 * avec l'API REST (pas besoin de git sur le telephone).
 */
object GithubPush {

    private const val API = "https://api.github.com"

    /** `proprietaire` est null quand l'utilisateur n'a saisi que le nom du depot. */
    data class Depot(val proprietaire: String?, val nom: String)

    class Resultat(val url: String, val fichiers: Int)

    class GithubException(message: String) : Exception(message)

    private class Reponse(val code: Int, val corps: String)

    /** "nom", "proprietaire/nom" ou une adresse github.com ; null si invalide. */
    fun analyserDepot(saisie: String): Depot? {
        val propre = saisie.trim()
            .removePrefix("https://github.com/")
            .removeSuffix(".git")
            .trim('/')
        val valide = Regex("[A-Za-z0-9._-]+")
        val parts = propre.split('/')
        if (parts.any { !valide.matches(it) || it == "." || it == ".." }) return null
        return when (parts.size) {
            1 -> Depot(null, parts[0])
            2 -> Depot(parts[0], parts[1])
            else -> null
        }
    }

    /**
     * Envoie `fichiers` dans le depot (cree, en prive, s'il n'existe pas sur le compte du jeton).
     * Bloquant : a appeler hors du fil principal.
     */
    fun pousser(
        jeton: String,
        depot: Depot,
        message: String,
        fichiers: List<ProjectStore.EntreeFichier>,
        progression: (String) -> Unit = {}
    ): Resultat {
        if (jeton.isBlank()) throw GithubException("Aucun jeton GitHub : ajoute-le dans les Réglages.")
        if (fichiers.isEmpty()) throw GithubException("Le projet ne contient aucun fichier à envoyer.")

        progression("Connexion à GitHub…")
        val login = JSONObject(ok(appeler(jeton, "GET", "/user"), "Connexion")).getString("login")
        val proprietaire = depot.proprietaire ?: login
        val base = "/repos/$proprietaire/${depot.nom}"

        var cree = false
        var reponse = appeler(jeton, "GET", base)
        if (reponse.code == 404) {
            if (!proprietaire.equals(login, ignoreCase = true)) {
                throw GithubException("Le dépôt $proprietaire/${depot.nom} n'existe pas, et je ne peux en créer que sur ton compte ($login).")
            }
            progression("Création du dépôt privé ${depot.nom}…")
            val corps = JSONObject().put("name", depot.nom).put("private", true).put("auto_init", true)
            ok(appeler(jeton, "POST", "/user/repos", corps), "Création du dépôt")
            cree = true
            reponse = appeler(jeton, "GET", base)
        }
        val branche = JSONObject(ok(reponse, "Dépôt")).optString("default_branch", "main")

        var tete = teteDeBranche(jeton, base, branche, tentatives = if (cree) 6 else 1)
        if (tete == null) {
            // depot existant mais vide : un premier fichier cree la branche
            progression("Initialisation du dépôt vide…")
            val premier = fichiers.first()
            val corps = JSONObject()
                .put("message", message)
                .put("content", Base64.encodeToString(premier.octets, Base64.NO_WRAP))
            ok(appeler(jeton, "PUT", "$base/contents/${premier.chemin}", corps), "Initialisation")
            tete = teteDeBranche(jeton, base, branche, tentatives = 6)
                ?: throw GithubException("Le dépôt reste vide après l'initialisation.")
        }

        val commitParent = tete
        val arbreParent = JSONObject(
            ok(appeler(jeton, "GET", "$base/git/commits/$commitParent"), "Lecture du dernier commit")
        ).getJSONObject("tree").getString("sha")

        val arbre = JSONArray()
        fichiers.forEachIndexed { i, f ->
            progression("Envoi des fichiers ${i + 1}/${fichiers.size}…")
            val blob = JSONObject()
                .put("content", Base64.encodeToString(f.octets, Base64.NO_WRAP))
                .put("encoding", "base64")
            val sha = JSONObject(ok(appeler(jeton, "POST", "$base/git/blobs", blob), "Envoi de ${f.chemin}"))
                .getString("sha")
            arbre.put(
                JSONObject().put("path", f.chemin).put("mode", "100644").put("type", "blob").put("sha", sha)
            )
        }

        progression("Création du commit…")
        val nouvelArbre = JSONObject(
            ok(
                appeler(jeton, "POST", "$base/git/trees", JSONObject().put("base_tree", arbreParent).put("tree", arbre)),
                "Création de l'arbre"
            )
        ).getString("sha")
        val commit = JSONObject(
            ok(
                appeler(
                    jeton, "POST", "$base/git/commits",
                    JSONObject().put("message", message).put("tree", nouvelArbre)
                        .put("parents", JSONArray().put(commitParent))
                ),
                "Création du commit"
            )
        ).getString("sha")

        progression("Mise à jour de la branche $branche…")
        ok(
            appeler(jeton, "PATCH", "$base/git/refs/heads/$branche", JSONObject().put("sha", commit)),
            "Mise à jour de la branche"
        )
        return Resultat("https://github.com/$proprietaire/${depot.nom}/commit/$commit", fichiers.size)
    }

    /** Sha du dernier commit de la branche, ou null si elle n'existe pas (depot vide). */
    private fun teteDeBranche(jeton: String, base: String, branche: String, tentatives: Int): String? {
        repeat(tentatives) { essai ->
            val r = appeler(jeton, "GET", "$base/git/ref/heads/$branche")
            if (r.code in 200..299) {
                return JSONObject(r.corps).getJSONObject("object").getString("sha")
            }
            if (r.code != 404 && r.code != 409) ok(r, "Lecture de la branche")
            if (essai < tentatives - 1) Thread.sleep(1000)
        }
        return null
    }

    private fun appeler(jeton: String, methode: String, chemin: String, corps: JSONObject? = null): Reponse {
        val c = URL(API + chemin).openConnection() as HttpURLConnection
        try {
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            c.requestMethod = methode
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("Authorization", "Bearer $jeton")
            c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            c.setRequestProperty("User-Agent", "CodexLocal")
            if (corps != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(corps.toString().toByteArray()) }
            }
            val code = c.responseCode
            val flux = if (code in 200..299) c.inputStream else c.errorStream
            return Reponse(code, flux?.bufferedReader()?.use { it.readText() } ?: "")
        } finally {
            c.disconnect()
        }
    }

    /** Renvoie le corps de la reponse, ou leve une erreur lisible. */
    private fun ok(r: Reponse, etape: String): String {
        if (r.code in 200..299) return r.corps
        val detail = runCatching { JSONObject(r.corps).optString("message") }.getOrNull().orEmpty()
        val cause = when (r.code) {
            401 -> "jeton refusé ou expiré"
            403, 404 -> "droit manquant ou dépôt introuvable (le jeton doit avoir « Contents : lecture et écriture »)"
            422 -> "GitHub a refusé la demande"
            else -> "HTTP ${r.code}"
        }
        throw GithubException("$etape : $cause" + if (detail.isNotBlank()) " ($detail)" else "")
    }
}
