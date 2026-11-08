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

    /** Releases de toutes les variantes ; chacune reconnaît les siennes par son préfixe de tag. */
    private const val API_RELEASES =
        "https://api.github.com/repos/alexmarceauprevost812-source/CodexLocal/releases?per_page=30"

    /** Variante de l'appli : où sont ses releases et comment s'appelle son APK. */
    data class Variante(
        val prefixeTag: String,
        val prefixeApk: String,
        val nom: String,
        val userAgent: String
    )

    val PERSO = Variante("perso-v", "TVA-LexOS", "TVA LexOS", "TVA-LexOS")
    val REA = Variante("rea-v", "Codex-REA", "Codex REA", "Codex-REA")

    /** null = appli standard (releases/latest). Perso et REA ne se combinent pas. */
    fun variante(perso: Boolean, rea: Boolean): Variante? = when {
        perso -> PERSO
        rea -> REA
        else -> null
    }

    /** Premier .apk dont le nom commence par [prefixe] (vide : n'importe quel .apk). */
    fun choisirApk(assets: org.json.JSONArray?, prefixe: String): String? {
        if (assets == null) return null
        for (i in 0 until assets.length()) {
            val a = assets.optJSONObject(i) ?: continue
            val nom = a.optString("name")
            if (nom.startsWith(prefixe) && nom.endsWith(".apk", ignoreCase = true)) {
                return a.optString("browser_download_url")
            }
        }
        return null
    }

    /** Première release publiée de la variante avec son APK : (version, lien, notes), ou null. */
    fun choisirRelease(releases: org.json.JSONArray, v: Variante): Triple<String, String, String>? {
        for (i in 0 until releases.length()) {
            val r = releases.optJSONObject(i) ?: continue
            val tag = r.optString("tag_name")
            if (r.optBoolean("draft") || !tag.startsWith(v.prefixeTag)) continue
            val apk = choisirApk(r.optJSONArray("assets"), v.prefixeApk) ?: continue
            return Triple(tag.removePrefix(v.prefixeTag), apk, r.optString("body", ""))
        }
        return null
    }

    /** null signifie uniquement qu'aucune version plus recente n'est disponible. */
    suspend fun verifier(context: Context): UpdateInfo? =
        variante(BuildConfig.PERSO, BuildConfig.REA_APP).let { v ->
            // On branche sur la variante, pas sur le résultat : « à jour » (null) ne doit jamais
            // retomber sur les releases de l'appli standard.
            if (v != null) verifierVariante(context, v) else verifierStandard(context)
        }

    /** Dernière release de la variante (TVA LexOS, Codex REA) : mise à jour en un clic. */
    private suspend fun verifierVariante(context: Context, v: Variante): UpdateInfo? = withContext(Dispatchers.IO) {
        val conn = (URL(API_RELEASES).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", v.userAgent)
        }
        try {
            if (conn.responseCode != 200) throw IOException("Vérification impossible : HTTP ${conn.responseCode}")
            val releases = org.json.JSONArray(conn.inputStream.bufferedReader().use { it.readText() })
            val (version, apk, notes) = choisirRelease(releases, v)
                ?: throw IOException("Aucune version de ${v.nom} publiée pour le moment.")
            if (plusRecente(version, versionInstallee(context))) UpdateInfo(version, apk, notes) else null
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
            val apk = choisirApk(assets, "")
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
