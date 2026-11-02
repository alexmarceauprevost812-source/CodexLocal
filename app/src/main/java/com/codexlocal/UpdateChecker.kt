package com.codexlocal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.io.IOException

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String
)

/**
 * Cherche une nouvelle version dans les releases GitHub du projet.
 *
 * Le depot doit rester public pour que la mise a jour automatique
 * fonctionne sans authentification. Les releases sont creees par le workflow
 * GitHub Actions (job publier-release).
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/alexmarceauprevost812-source/CodexLocal/releases/latest"

    fun versionInstallee(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    /** TVA LexOS (APK perso) : ses versions sont dans les releases « perso-v… ». */
    private const val API_PERSO =
        "https://api.github.com/repos/alexmarceauprevost812-source/CodexLocal/releases?per_page=30"

    /** null signifie uniquement qu'aucune version plus recente n'est disponible. */
    suspend fun verifier(context: Context): UpdateInfo? =
        if (BuildConfig.PERSO) verifierPerso(context) else verifierStandard(context)

    /** Dernière release « perso-v… » avec son APK TVA-LexOS (mise à jour en un clic). */
    private suspend fun verifierPerso(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(API_PERSO).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "TVA-LexOS")
        }
        try {
            if (conn.responseCode != 200) throw IOException("Vérification impossible : HTTP ${conn.responseCode}")
            val releases = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            for (i in 0 until releases.length()) {
                val r = releases.optJSONObject(i) ?: continue
                val tag = r.optString("tag_name")
                if (r.optBoolean("draft") || !tag.startsWith("perso-v")) continue
                val assets = r.optJSONArray("assets") ?: continue
                for (j in 0 until assets.length()) {
                    val a = assets.optJSONObject(j) ?: continue
                    val nom = a.optString("name")
                    if (nom.startsWith("TVA-LexOS") && nom.endsWith(".apk", ignoreCase = true)) {
                        val version = tag.removePrefix("perso-v")
                        return@withContext if (plusRecente(version, versionInstallee(context))) {
                            UpdateInfo(version, a.optString("browser_download_url"), r.optString("body", ""))
                        } else null
                    }
                }
            }
            throw IOException("Aucune version de TVA LexOS publiée pour le moment.")
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun verifierStandard(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn =
            (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CodexLocal")
            }

        try {
            when (conn.responseCode) {
                404 -> throw IOException("Aucune version publiée pour le moment. Vous pouvez aussi utiliser « Installer un APK téléchargé » dans les réglages.")
                200 -> Unit
                else -> throw IOException("Vérification impossible : HTTP ${conn.responseCode}")
            }
            val texte = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(texte)

            val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets") ?: throw IOException("Aucun APK dans cette version.")
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url")
                    break
                }
            }
            val urlApk = apk ?: throw IOException("Aucun APK dans cette version.")

            if (plusRecente(tag, versionInstallee(context))) {
                UpdateInfo(tag, urlApk, json.optString("body", ""))
            } else {
                null
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Compare deux versions numeriques (1.2 < 1.10). */
    private fun plusRecente(distant: String, installe: String): Boolean {
        fun parties(v: String) = v.split('.', '-', '_', '+').mapNotNull { it.toIntOrNull() }
        val a = parties(distant)
        val b = parties(installe)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }
}
