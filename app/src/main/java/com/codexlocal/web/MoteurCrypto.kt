package com.codexlocal.web

import com.codexlocal.Message
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Prix des cryptomonnaies en direct (CoinGecko, sans clé).
 * « c'est quoi le prix du BTC ? » → le cours en $ CA et $ US, la variation sur 24 h et le logo,
 * affichés en carte en haut de la réponse et donnés à l'IA pour qu'elle utilise les vrais chiffres.
 */
object MoteurCrypto {

    private val monnaies = linkedMapOf(
        "bitcoin" to "bitcoin", "btc" to "bitcoin",
        "ethereum" to "ethereum", "ether" to "ethereum", "eth" to "ethereum",
        "solana" to "solana", "sol" to "solana",
        "dogecoin" to "dogecoin", "doge" to "dogecoin",
        "xrp" to "ripple", "ripple" to "ripple",
        "cardano" to "cardano", "ada" to "cardano",
        "litecoin" to "litecoin", "ltc" to "litecoin",
        "bnb" to "binancecoin",
        "tether" to "tether", "usdt" to "tether",
        "usdc" to "usd-coin",
        "polkadot" to "polkadot", "dot" to "polkadot",
        "shiba" to "shiba-inu", "shib" to "shiba-inu",
        "avalanche" to "avalanche-2", "avax" to "avalanche-2",
        "chainlink" to "chainlink", "link" to "chainlink",
        "tron" to "tron", "trx" to "tron",
        "monero" to "monero", "xmr" to "monero",
        "pepe" to "pepe",
    )

    private val intention = Regex(
        "\\b(prix|valeur|cours|combien|vaut|valent|cote|coût\\w*|cout\\w*|price|worth|value|taux|monte|baisse|graphique)\\b"
    )

    /** Identifiant CoinGecko si le message demande le prix d'une crypto, sinon null. */
    fun detecter(message: String): String? {
        val texte = message.lowercase(Locale.ROOT)
        if (!intention.containsMatchIn(texte)) return null
        val mots = Regex("[\\p{L}\\p{N}]+").findAll(texte).map { it.value }.toSet()
        // Les sigles courts (sol, dot, link…) sont des mots courants : on exige « crypto » autour.
        val ambigus = setOf("sol", "dot", "link", "eth", "ada", "pepe", "tron")
        val parleCrypto = Regex("crypto|coin|jeton|token|blockchain").containsMatchIn(texte)
        return monnaies.entries.firstOrNull { (mot, _) ->
            mot in mots && (mot !in ambigus || parleCrypto)
        }?.value
    }

    data class Cours(
        val id: String,
        val nom: String,
        val symbole: String,
        val logo: String,
        val prixCad: Double,
        val prixUsd: Double?,
        val variation24h: Double?,
        val heure: Long,
    ) {
        private fun prix(valeur: Double, devise: String): String {
            val format = NumberFormat.getNumberInstance(Locale.CANADA_FRENCH).apply {
                maximumFractionDigits = if (valeur >= 1) 2 else 6
                minimumFractionDigits = if (valeur >= 1) 2 else 0
            }
            return format.format(valeur) + " " + devise
        }

        private fun variation(): String? = variation24h?.let {
            val signe = if (it >= 0) "▲" else "▼"
            val format = NumberFormat.getNumberInstance(Locale.CANADA_FRENCH).apply { maximumFractionDigits = 2 }
            "$signe ${format.format(abs(it))} % sur 24 h"
        }

        private fun heureTexte() = SimpleDateFormat("HH:mm", Locale.CANADA_FRENCH).format(Date(heure))

        /** Ce qui est donné à l'IA (jamais affiché tel quel). */
        fun contexte(): String {
            val json = JSONObject()
                .put("crypto", "$nom (${symbole.uppercase(Locale.ROOT)})")
                .put("prix_cad", prix(prixCad, "\$ CA"))
                .put("heure", heureTexte())
            prixUsd?.let { json.put("prix_usd", prix(it, "\$ US")) }
            variation()?.let { json.put("variation", it) }
            return "Cours en direct (CoinGecko), à utiliser tel quel, sans inventer d'autres chiffres :\n$json"
        }

        /** Carte affichée en haut de la réponse : logo, prix, variation, lien vers la source. */
        fun carte(): String = buildString {
            append("![$nom]($logo) **$nom (${symbole.uppercase(Locale.ROOT)})** : ")
            append(prix(prixCad, "\$ CA"))
            prixUsd?.let { append(" · ").append(prix(it, "\$ US")) }
            variation()?.let { append("\n").append(if ((variation24h ?: 0.0) >= 0) "📈 " else "📉 ").append(it) }
            append("\n🔗 [Voir sur CoinGecko](https://www.coingecko.com/fr/pi%C3%A8ces/$id) · ⏰ ${heureTexte()}")
        }

        /** Ajoute la carte à la réponse affichée, sans la renvoyer à l'IA au tour suivant. */
        fun annoter(message: Message) {
            require(!message.isUser)
            if (message.envoi == null) message.envoi = message.content
            message.content = carte() + "\n\n" + message.content
        }
    }

    suspend fun obtenir(id: String, charger: (String) -> String = ::chargerHttps): Cours = withContext(Dispatchers.IO) {
        val ids = URLEncoder.encode(id, "UTF-8")
        val marche = JSONArray(
            charger("https://api.coingecko.com/api/v3/coins/markets?vs_currency=cad&ids=$ids&price_change_percentage=24h")
        )
        require(marche.length() > 0) { "Crypto introuvable" }
        val c = marche.getJSONObject(0)
        val usd = runCatching {
            JSONObject(charger("https://api.coingecko.com/api/v3/simple/price?ids=$ids&vs_currencies=usd"))
                .getJSONObject(id).getDouble("usd")
        }.getOrNull()
        Cours(
            id = id,
            nom = c.getString("name"),
            symbole = c.getString("symbol"),
            logo = c.optString("image"),
            prixCad = c.getDouble("current_price"),
            prixUsd = usd,
            variation24h = if (c.isNull("price_change_percentage_24h")) null else c.optDouble("price_change_percentage_24h"),
            heure = System.currentTimeMillis(),
        )
    }

    private fun chargerHttps(url: String): String {
        require(url.startsWith("https://api.coingecko.com/"))
        val connexion = URL(url).openConnection() as HttpURLConnection
        try {
            connexion.connectTimeout = 10_000
            connexion.readTimeout = 10_000
            connexion.instanceFollowRedirects = false
            connexion.setRequestProperty("Accept", "application/json")
            connexion.setRequestProperty("User-Agent", "CodexLocal/1.0 (crypto)")
            val code = connexion.responseCode
            require(code in 200..299) { "HTTP $code" }
            return connexion.inputStream.use { entree ->
                val sortie = ByteArrayOutputStream()
                val tampon = ByteArray(8192)
                while (true) {
                    val n = entree.read(tampon)
                    if (n < 0) break
                    require(sortie.size() + n <= 200_000) { "Réponse trop volumineuse" }
                    sortie.write(tampon, 0, n)
                }
                sortie.toString("UTF-8")
            }
        } finally {
            connexion.disconnect()
        }
    }
}
