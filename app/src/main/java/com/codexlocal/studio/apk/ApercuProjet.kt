package com.codexlocal.studio.apk

import android.content.Context
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Apercu en direct d'un projet : les fichiers sont lus dans le projet (sans copie) et servis
 * sous la meme adresse fictive que dans l'appli fabriquee, donc le rendu est identique.
 * Rien ne sort sur internet : tout ce qui n'est pas un fichier du projet est refuse.
 */
class ApercuProjet(
    private val context: Context,
    private val projet: Projet
) : WebViewClient() {

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse =
        servir(request.url)

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
        request.url.host != HOTE

    internal fun servir(adresse: Uri): WebResourceResponse {
        if (adresse.host != HOTE) return reponse(403, "Interdit", "text/plain", ByteArray(0))
        var chemin = adresse.path.orEmpty().trimStart('/')
        if (chemin.isEmpty()) chemin = ConstructeurApk.PAGE_ACCUEIL
        val flux = ProjectStore.ouvrirFlux(context, projet, chemin)
            ?: return reponse(404, "Introuvable", "text/plain", "404".toByteArray())
        val type = TypesMime.pour(chemin)
        return WebResourceResponse(type, if (TypesMime.estTexte(type)) "utf-8" else null, flux)
    }

    private fun reponse(code: Int, message: String, type: String, corps: ByteArray) =
        WebResourceResponse(type, "utf-8", code, message, HashMap(), ByteArrayInputStream(corps))

    companion object {
        const val HOTE = "appassets.androidplatform.net"

        fun adresseAccueil() = "https://$HOTE/${ConstructeurApk.PAGE_ACCUEIL}"
    }
}

/** Types MIME des fichiers d'un site (identiques a ceux de l'appli fabriquee). */
object TypesMime {

    private val types = mapOf(
        "html" to "text/html", "htm" to "text/html", "css" to "text/css",
        "js" to "text/javascript", "mjs" to "text/javascript",
        "json" to "application/json", "map" to "application/json",
        "txt" to "text/plain", "md" to "text/plain", "csv" to "text/csv",
        "xml" to "application/xml", "svg" to "image/svg+xml",
        "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "ico" to "image/x-icon",
        "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf",
        "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "wav" to "audio/wav",
        "mp4" to "video/mp4", "webm" to "video/webm", "wasm" to "application/wasm"
    )

    fun pour(chemin: String): String =
        types[chemin.substringAfterLast('.', "").lowercase(Locale.ROOT)] ?: "application/octet-stream"

    fun estTexte(type: String) =
        type.startsWith("text/") || type.contains("json") || type.contains("javascript") ||
            type.contains("xml")
}
