package ca.tilabo.app

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Mise a jour de l'appli depuis les Releases GitHub (tag ti-labo-vX.Y.Z), installee par-dessus :
 * meme signature, donc rien a desinstaller et rien de perdu.
 * Le depot est prive : il faut le jeton GitHub de la page GitHub de l'appli.
 */
object MiseAJour {

    private const val DEPOT = "alexmarceauprevost812-source/CodexLocal"

    @Volatile var progres: JSONObject = JSONObject().put("etat", "repos")
        private set

    private fun ouvrir(url: String, jeton: String, binaire: Boolean = false): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15_000
        c.readTimeout = 60_000
        c.instanceFollowRedirects = false
        c.setRequestProperty("User-Agent", "Ti-Labo")
        c.setRequestProperty("Accept", if (binaire) "application/octet-stream" else "application/vnd.github+json")
        if (jeton.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $jeton")
        return c
    }

    private fun versionNum(v: String) = v.removePrefix("ti-labo-v").removePrefix("v")
        .split('.').map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }

    private fun plusRecente(a: String, b: String): Boolean {
        val x = versionNum(a); val y = versionNum(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }; val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    fun verifier(ctx: Context): JSONObject {
        val actuelle = BuildConfigInfo.version(ctx)
        val jeton = Reglages.jeton(ctx)
        val c = ouvrir("https://api.github.com/repos/$DEPOT/releases?per_page=30", jeton)
        val texte = try {
            when (c.responseCode) {
                200 -> c.inputStream.bufferedReader().readText()
                401 -> throw IllegalStateException("Jeton GitHub refusé : mets-le à jour dans la page GitHub.")
                404 -> throw IllegalStateException("Ajoute ton jeton GitHub (page 🐙 GitHub) : ton dépôt est privé.")
                else -> throw IllegalStateException("GitHub ne répond pas (${c.responseCode})")
            }
        } finally { c.disconnect() }
        val rel = JSONArray(texte)
        for (i in 0 until rel.length()) {
            val r = rel.getJSONObject(i)
            val tag = r.optString("tag_name")
            if (!tag.startsWith("ti-labo-v") || r.optBoolean("draft")) continue
            val assets = r.optJSONArray("assets") ?: JSONArray()
            val apk = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { it.optString("name") == "Ti-Labo.apk" }
                ?: continue
            val derniere = tag.removePrefix("ti-labo-v")
            return JSONObject().put("actuelle", actuelle).put("derniere", derniere)
                .put("disponible", plusRecente(derniere, actuelle))
                .put("asset", apk.optLong("id")).put("taille_mo", apk.optLong("size") / 1_048_576)
                .put("date", r.optString("published_at").take(10))
        }
        return JSONObject().put("actuelle", actuelle).put("disponible", false)
    }

    fun installer(ctx: Context) {
        if (progres.optString("etat") == "telechargement") return
        progres = JSONObject().put("etat", "telechargement").put("pourcent", 0)
        Thread {
            try {
                val info = verifier(ctx)
                if (!info.optBoolean("disponible")) { progres = JSONObject().put("etat", "a_jour"); return@Thread }
                val jeton = Reglages.jeton(ctx)
                // GitHub renvoie vers une adresse signee : on la suit SANS le jeton
                var c = ouvrir("https://api.github.com/repos/$DEPOT/releases/assets/${info.getLong("asset")}", jeton, true)
                var essais = 0
                while (c.responseCode in 300..399 && essais++ < 5) {
                    val loc = c.getHeaderField("Location"); c.disconnect()
                    c = URL(loc).openConnection() as HttpURLConnection
                    c.instanceFollowRedirects = false
                    c.setRequestProperty("User-Agent", "Ti-Labo")
                }
                if (c.responseCode != 200) throw IllegalStateException("Téléchargement refusé (${c.responseCode})")
                val total = c.contentLengthLong.coerceAtLeast(1)
                val f = File(ctx.cacheDir, "Ti-Labo-maj.apk")
                c.inputStream.use { i ->
                    f.outputStream().use { o ->
                        val b = ByteArray(65_536); var lu = 0L
                        while (true) {
                            val n = i.read(b); if (n < 0) break
                            o.write(b, 0, n); lu += n
                            progres = JSONObject().put("etat", "telechargement").put("pourcent", (lu * 100 / total).toInt())
                        }
                    }
                }
                c.disconnect()
                progres = JSONObject().put("etat", "installation")
                val pi = ctx.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    .apply { setAppPackageName(ctx.packageName) }
                val id = pi.createSession(params)
                pi.openSession(id).use { s ->
                    f.inputStream().use { i -> s.openWrite("ti-labo.apk", 0, f.length()).use { o -> i.copyTo(o); s.fsync(o) } }
                    val retour = PendingIntent.getBroadcast(ctx, 42, Intent(ctx, MajRecepteur::class.java),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                    s.commit(retour.intentSender)
                }
            } catch (e: Exception) {
                progres = JSONObject().put("etat", "erreur").put("message", e.message ?: e.javaClass.simpleName)
            }
        }.start()
    }

    fun signaler(etat: String, message: String? = null) {
        progres = JSONObject().put("etat", etat).apply { if (message != null) put("message", message) }
    }
}

/** Retour de l'installateur Android : demande la confirmation a l'utilisateur. */
class MajRecepteur : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmer = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                confirmer?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { ctx.startActivity(it) }
                MiseAJour.signaler("confirmation")
            }
            PackageInstaller.STATUS_SUCCESS -> MiseAJour.signaler("installee")
            else -> MiseAJour.signaler("erreur", intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "Installation annulée")
        }
    }
}
