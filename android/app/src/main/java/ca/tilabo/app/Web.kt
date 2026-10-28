package ca.tilabo.app

import android.text.Html
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder

/** Recherche Internet pour l'IA locale (meme methode que le Ti-Labo de la cle : DuckDuckGo HTML). */
object Web {

    private const val AGENT = "Mozilla/5.0 (Linux; Android 15) Ti-Labo/2.0"

    fun telecharger(url: String, max: Int = 1_500_000): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 20_000
        c.instanceFollowRedirects = true
        c.setRequestProperty("User-Agent", AGENT)
        c.setRequestProperty("Accept-Language", "fr-CA,fr;q=0.9,en;q=0.7")
        try {
            val octets = c.inputStream.use { inp ->
                val buf = java.io.ByteArrayOutputStream()
                val b = ByteArray(16_384)
                while (buf.size() < max) {
                    val n = inp.read(b); if (n < 0) break
                    buf.write(b, 0, n)
                }
                buf.toByteArray()
            }
            val cs = Regex("charset=([\\w-]+)").find(c.contentType ?: "")?.groupValues?.get(1) ?: "UTF-8"
            return String(octets, runCatching { charset(cs) }.getOrDefault(Charsets.UTF_8))
        } finally {
            c.disconnect()
        }
    }

    private fun nettoyer(s: String) =
        Html.fromHtml(s, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()

    fun chercher(requete: String, n: Int = 6): JSONArray {
        val page = telecharger("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(requete, "UTF-8") + "&kl=ca-fr")
        val res = JSONArray()
        val re = Regex("""<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>(.*?)(?=<a[^>]+class="result__a"|$)""",
            RegexOption.DOT_MATCHES_ALL)
        for (m in re.findAll(page)) {
            var lien = Html.fromHtml(m.groupValues[1], Html.FROM_HTML_MODE_LEGACY).toString()
            Regex("[?&]uddg=([^&]+)").find(lien)?.let { lien = URLDecoder.decode(it.groupValues[1], "UTF-8") }
            if ("duckduckgo.com/y.js" in lien) continue
            if (lien.startsWith("//")) lien = "https:$lien"
            val extrait = Regex("""class="result__snippet"[^>]*>(.*?)</a>""", RegexOption.DOT_MATCHES_ALL)
                .find(m.groupValues[3])?.groupValues?.get(1)?.let { nettoyer(it) } ?: ""
            res.put(JSONObject().put("titre", nettoyer(m.groupValues[2])).put("url", lien).put("extrait", extrait))
            if (res.length() >= n) break
        }
        return res
    }

    /** Images (vignettes) pour illustrer une reponse : DuckDuckGo Images. */
    fun images(requete: String, n: Int = 6): JSONArray {
        val q = URLEncoder.encode(requete, "UTF-8")
        val page = telecharger("https://duckduckgo.com/?q=$q&iax=images&ia=images")
        val vqd = Regex("""vqd=["']?([\d-]+)""").find(page)?.groupValues?.get(1)
            ?: throw IllegalStateException("Recherche d'images indisponible")
        val j = JSONObject(telecharger("https://duckduckgo.com/i.js?l=ca-fr&o=json&q=$q&vqd=$vqd&p=1"))
        val r = j.optJSONArray("results") ?: JSONArray()
        val res = JSONArray()
        for (i in 0 until r.length()) {
            val o = r.optJSONObject(i) ?: continue
            val vignette = o.optString("thumbnail")
            if (vignette.isBlank()) continue
            res.put(JSONObject().put("titre", o.optString("title")).put("vignette", vignette)
                .put("image", o.optString("image")).put("source", o.optString("url")))
            if (res.length() >= n) break
        }
        return res
    }

    /** Le texte lisible d'une page web. */
    fun lirePage(url: String, limite: Int = 9000): JSONObject {
        require(url.startsWith("http://") || url.startsWith("https://")) { "Adresse invalide" }
        var h = telecharger(url)
        val titre = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(h)?.groupValues?.get(1)?.let { nettoyer(it) } ?: url
        h = h.replace(Regex("<(script|style|noscript|svg|nav|footer|header)[^>]*>.*?</\\1>",
            setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), " ")
        // garder les blocs de code et les sauts de ligne
        h = h.replace(Regex("<(br|/p|/div|/li|/h[1-6]|/pre|/tr)[^>]*>", RegexOption.IGNORE_CASE), "\n")
        val texte = Html.fromHtml(h, Html.FROM_HTML_MODE_LEGACY).toString()
            .replace(Regex("[ \\t\\u00A0]+"), " ").replace(Regex("\\n\\s*\\n+"), "\n\n").trim()
        return JSONObject().put("titre", titre).put("url", url).put("texte", texte.take(limite))
    }
}
