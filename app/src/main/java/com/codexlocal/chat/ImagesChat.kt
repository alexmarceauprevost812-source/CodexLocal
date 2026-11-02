package com.codexlocal.chat

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Images dans le texte du chat (logos de cryptos, logos de sites).
 *
 * Pour la vie privée, l'IA ne peut pas faire télécharger n'importe quelle image :
 *  - `logo:domaine.com` devient l'icône du site, servie par icons.duckduckgo.com ;
 *  - une adresse https n'est chargée que si elle vient d'un hôte de la liste [HOTES]
 *    (logos CoinGecko, icônes DuckDuckGo). Les autres restent de simples liens.
 */
object ImagesChat {

    private val HOTES = setOf(
        "assets.coingecko.com",
        "coin-images.coingecko.com",
        "icons.duckduckgo.com",
        // Miniatures de la recherche riche (vidéos YouTube, photos Wikipédia), affichées en grand.
        "i.ytimg.com",
        "upload.wikimedia.org",
    )
    private val GRANDES = setOf("i.ytimg.com", "upload.wikimedia.org")
    private val domaineValide = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)+$")

    private const val TAILLE_MAX = 512 * 1024

    private val cache = LruCache<String, Bitmap>(48)
    private val enCours = HashSet<String>()
    private val echecs = HashSet<String>()
    private val attente = HashMap<String, MutableList<() -> Unit>>()
    private val travail = Executors.newFixedThreadPool(2)
    private val principal = Handler(Looper.getMainLooper())

    /** Adresse réelle à charger, ou null si l'image n'est pas permise. */
    fun resoudre(source: String): String? {
        val brut = source.trim()
        if (brut.startsWith("logo:", ignoreCase = true)) {
            val domaine = brut.substring(5).trim().lowercase(Locale.ROOT)
                .removePrefix("https://").removePrefix("http://").removePrefix("www.").substringBefore('/')
            return if (domaineValide.matches(domaine)) "https://icons.duckduckgo.com/ip3/$domaine.ico" else null
        }
        return if (estPermise(brut)) brut else null
    }

    fun estPermise(url: String): Boolean = runCatching {
        val uri = URI(url)
        uri.scheme == "https" && uri.userInfo == null && uri.port in listOf(-1, 443) &&
            uri.host?.lowercase(Locale.ROOT) in HOTES
    }.getOrDefault(false)

    /**
     * L'image prête à afficher (carrée, [taillePx] de côté), ou null si elle n'est pas encore
     * chargée : dans ce cas elle se charge en arrière-plan et [quandPrete] est appelé ensuite.
     */
    fun drawable(resources: Resources, source: String, taillePx: Int, quandPrete: () -> Unit): Drawable? {
        val url = resoudre(source) ?: return null
        val bitmap = cache.get(url)
        if (bitmap != null) {
            val grande = runCatching { URI(url).host?.lowercase(Locale.ROOT) in GRANDES }.getOrDefault(false)
            if (grande && bitmap.width > 0) {
                // Miniature : large (environ la largeur d'une bulle), proportions gardées.
                val largeur = minOf(taillePx * 9, (resources.displayMetrics.widthPixels * 0.7f).toInt())
                val hauteur = largeur * bitmap.height / bitmap.width
                return BitmapDrawable(resources, bitmap).apply { setBounds(0, 0, largeur, hauteur) }
            }
            return BitmapDrawable(resources, bitmap).apply { setBounds(0, 0, taillePx, taillePx) }
        }
        charger(url, quandPrete)
        return null
    }

    private fun charger(url: String, quandPrete: () -> Unit) {
        synchronized(this) {
            if (url in echecs) return
            attente.getOrPut(url) { mutableListOf() }.add(quandPrete)
            if (!enCours.add(url)) return
        }
        travail.execute {
            val bitmap = runCatching { telecharger(url) }.getOrNull()
            val rappels = synchronized(this) {
                enCours.remove(url)
                if (bitmap != null) cache.put(url, bitmap) else echecs.add(url)
                attente.remove(url).orEmpty()
            }
            if (bitmap != null) principal.post { rappels.forEach { it() } }
        }
    }

    private fun telecharger(url: String): Bitmap? {
        var courant = url
        repeat(3) {
            require(estPermise(courant))
            val connexion = URL(courant).openConnection() as HttpURLConnection
            try {
                connexion.connectTimeout = 8_000
                connexion.readTimeout = 8_000
                connexion.instanceFollowRedirects = false
                connexion.setRequestProperty("User-Agent", "CodexLocal/1.0 (images)")
                val code = connexion.responseCode
                if (code in 300..399) {
                    courant = URI(courant).resolve(connexion.getHeaderField("Location") ?: return null).toString()
                    return@repeat
                }
                if (code !in 200..299) return null
                val octets = connexion.inputStream.use { entree ->
                    val sortie = ByteArrayOutputStream()
                    val tampon = ByteArray(8192)
                    while (true) {
                        val n = entree.read(tampon)
                        if (n < 0) break
                        if (sortie.size() + n > TAILLE_MAX) return null
                        sortie.write(tampon, 0, n)
                    }
                    sortie.toByteArray()
                }
                return BitmapFactory.decodeByteArray(octets, 0, octets.size)
            } finally {
                connexion.disconnect()
            }
        }
        return null
    }
}
