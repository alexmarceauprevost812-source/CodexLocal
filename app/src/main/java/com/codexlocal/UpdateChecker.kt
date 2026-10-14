package com.codexlocal

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(
    val version: String,
    val apkUrl: String,
    val notes: String
)

/**
 * Cherche une nouvelle version dans les releases GitHub du projet.
 *
 * ATTENTION : l'API GitHub ne renvoie rien pour un depot prive sans
 * authentification. Le depot doit donc etre public pour que la mise a jour
 * automatique fonctionne depuis l'appli.
 */
object UpdateChecker {

    private const val API =
        "https://api.github.com/repos/alexmarceauprevost812-source/CodexLocal/releases/latest"

    fun versionInstallee(context: Context): String =
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "0"

    /** @return la mise a jour disponible, ou null si on est a jour / injoignable. */
    suspend fun verifier(context: Context): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = runCatching {
            (URL(API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "CodexLocal")
            }
        }.getOrNull() ?: return@withContext null

        try {
            // Un dépôt privé renvoie souvent 404 sans authentification.
            // Ne jamais inclure de jeton GitHub privé dans l'APK.
            // Le canal de distribution doit exposer une Release APK accessible
            // publiquement (ou un service authentifié dédié) pour fonctionner.
            if (conn.responseCode != 200) return@withContext null
            val texte = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(texte)

            val tag = json.optString("tag_name").removePrefix("v").removePrefix("V")
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var apk: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url")
                    break
                }
            }
            val urlApk = apk ?: return@withContext null

            if (plusRecente(tag, versionInstallee(context))) {
                UpdateInfo(tag, urlApk, json.optString("body", ""))
            } else {
                null
            }
        } catch (e: Exception) {
            null
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
