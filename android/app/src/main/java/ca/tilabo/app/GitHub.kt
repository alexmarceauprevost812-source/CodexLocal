package ca.tilabo.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/** Reglages de l'appli (jeton GitHub, Internet pour l'IA). Restent sur le telephone. */
object Reglages {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tilabo", Context.MODE_PRIVATE)

    fun lire(ctx: Context): JSONObject {
        val p = prefs(ctx)
        val jeton = p.getString("github_token", "") ?: ""
        return JSONObject()
            .put("internet", p.getBoolean("internet", true))
            .put("ville", p.getString("ville", "") ?: "")
            .put("github_token_present", jeton.isNotEmpty())
            .put("github_token_fin", if (jeton.length > 4) "…" + jeton.takeLast(4) else "")
    }

    fun ecrire(ctx: Context, d: JSONObject) {
        val e = prefs(ctx).edit()
        if (d.has("internet")) e.putBoolean("internet", d.optBoolean("internet"))
        if (d.has("github_token")) e.putString("github_token", d.optString("github_token").trim())
        // ville par defaut de la meteo (meme cle que Meteo.obtenir)
        if (d.has("ville")) d.optString("ville").trim().let { if (it.isEmpty()) e.remove("ville") else e.putString("ville", it) }
        e.apply()
    }

    fun internet(ctx: Context) = prefs(ctx).getBoolean("internet", true)
    fun jeton(ctx: Context) = prefs(ctx).getString("github_token", "") ?: ""
}

/** Cloner un depot GitHub dans TiLabo/projets (archive zip : pas besoin de git sur le telephone). */
object GitHub {

    private fun ouvrir(url: String, jeton: String): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.setRequestProperty("User-Agent", "Ti-Labo")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        if (jeton.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $jeton")
        return c
    }

    private fun json(url: String, jeton: String): Any {
        val c = ouvrir(url, jeton)
        try {
            if (c.responseCode == 401) throw IllegalStateException("Jeton GitHub refusé")
            if (c.responseCode == 404) throw IllegalStateException("Dépôt introuvable (privé ? ajoute ton jeton)")
            val t = c.inputStream.bufferedReader().readText()
            return if (t.trimStart().startsWith("[")) JSONArray(t) else JSONObject(t)
        } finally { c.disconnect() }
    }

    fun mesDepots(ctx: Context): JSONArray {
        val jeton = Reglages.jeton(ctx)
        require(jeton.isNotEmpty()) { "Ajoute ton jeton GitHub (github_pat_…) dans GitHub › Jeton." }
        val r = json("https://api.github.com/user/repos?per_page=100&sort=updated", jeton) as JSONArray
        val res = JSONArray()
        for (i in 0 until r.length()) {
            val o = r.getJSONObject(i)
            res.put(JSONObject().put("nom", o.optString("full_name")).put("prive", o.optBoolean("private"))
                .put("description", o.optString("description").takeIf { it != "null" } ?: "")
                .put("branche", o.optString("default_branch")))
        }
        return res
    }

    /** "proprio/depot" ou une adresse github.com. */
    fun cloner(ctx: Context, depot: String, branche: String?): JSONObject {
        val propre = depot.trim().removeSuffix(".git").removeSuffix("/")
            .replace(Regex("^(https?://)?(www\\.)?github\\.com/"), "")
        val morceaux = propre.split("/")
        require(morceaux.size >= 2 && morceaux[0].isNotBlank() && morceaux[1].isNotBlank()) { "Écris proprio/depot" }
        val (proprio, nom) = morceaux[0] to morceaux[1]
        val jeton = Reglages.jeton(ctx)
        val br = branche?.takeIf { it.isNotBlank() }
            ?: (json("https://api.github.com/repos/$proprio/$nom", jeton) as JSONObject).optString("default_branch", "main")
        val dst = File("/storage/emulated/0/TiLabo/projets", nom)
        require(!dst.exists()) { "Le projet « $nom » existe déjà sur le téléphone" }
        val c = ouvrir("https://api.github.com/repos/$proprio/$nom/zipball/$br", jeton)
        var n = 0
        try {
            if (c.responseCode != 200) throw IllegalStateException("GitHub a refusé le téléchargement (${c.responseCode})")
            ZipInputStream(c.inputStream.buffered()).use { zip ->
                val base = dst.canonicalPath + File.separator
                while (true) {
                    val e = zip.nextEntry ?: break
                    // l'archive contient un dossier « proprio-depot-commit/ » : on l'enleve
                    val rel = e.name.substringAfter('/', "")
                    if (rel.isEmpty()) continue
                    val f = File(dst, rel)
                    require(f.canonicalPath.startsWith(base)) { "Archive invalide" }
                    if (e.isDirectory) f.mkdirs() else {
                        f.parentFile?.mkdirs()
                        f.outputStream().use { zip.copyTo(it) }
                        n++
                    }
                }
            }
        } catch (ex: Exception) {
            dst.deleteRecursively()
            throw ex
        } finally { c.disconnect() }
        File(dst, ".tilabo-github.json").writeText(JSONObject().put("depot", "$proprio/$nom").put("branche", br).toString())
        return JSONObject().put("ok", true).put("nom", nom).put("chemin", dst.path).put("fichiers", n).put("branche", br)
    }
}
