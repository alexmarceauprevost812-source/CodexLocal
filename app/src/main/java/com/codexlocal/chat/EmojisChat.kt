package com.codexlocal.chat

/**
 * Mini-émojis dans le texte du chat : l'IA (ou l'utilisateur) peut écrire un code
 * comme `:fusee:` ou `:feu:` et il devient 🚀 ou 🔥. Les codes inconnus restent tels quels.
 */
object EmojisChat {

    val CODES: Map<String, String> = linkedMapOf(
        "sourire" to "🙂", "rire" to "😄", "mdr" to "😂", "clin" to "😉", "cool" to "😎",
        "coeur" to "❤️", "pouce" to "👍", "bravo" to "👏", "fete" to "🎉", "fusee" to "🚀",
        "feu" to "🔥", "etoile" to "⭐", "eclair" to "⚡", "idee" to "💡", "ok" to "✅",
        "non" to "❌", "attention" to "⚠️", "question" to "❓", "info" to "ℹ️", "loupe" to "🔍",
        "lien" to "🔗", "argent" to "💰", "dollar" to "💵", "graphique" to "📈", "baisse" to "📉",
        "bitcoin" to "₿", "cadenas" to "🔒", "cle" to "🔑", "outil" to "🛠️", "engrenage" to "⚙️",
        "ordi" to "💻", "telephone" to "📱", "code" to "👨‍💻", "robot" to "🤖", "cerveau" to "🧠",
        "livre" to "📖", "note" to "📝", "dossier" to "📁", "horloge" to "⏰", "calendrier" to "📅",
        "soleil" to "☀️", "pluie" to "🌧️", "neige" to "❄️", "quebec" to "⚜️", "cafe" to "☕",
        "pizza" to "🍕", "musique" to "🎵", "jeu" to "🎮", "photo" to "📷", "monde" to "🌍",
        "yeux" to "👀", "demon" to "😈", "citrouille" to "🎃", "fantome" to "👻",
        // Codes anglais courants
        "smile" to "🙂", "fire" to "🔥", "rocket" to "🚀", "heart" to "❤️", "thumbsup" to "👍",
        "warning" to "⚠️", "check" to "✅", "money" to "💰", "eyes" to "👀",
    )

    /** Émojis crypto : `:btc:` devient le logo de la crypto (icône du site officiel). */
    val CRYPTOS: Map<String, String> = linkedMapOf(
        "btc" to "bitcoin.org", "eth" to "ethereum.org", "sol" to "solana.com", "doge" to "dogecoin.com",
        "xrp" to "xrpl.org", "ada" to "cardano.org", "ltc" to "litecoin.org", "bnb" to "bnbchain.org",
        "usdt" to "tether.to", "usdc" to "circle.com", "dot" to "polkadot.network", "link" to "chain.link",
        "trx" to "tron.network", "xmr" to "getmonero.org", "avax" to "avax.network", "shib" to "shibatoken.com",
        "pepe" to "pepe.vip", "ton" to "ton.org",
    )

    /** Consigne pour l'IA (mode Chat). */
    val CONSIGNE_CRYPTO: String =
        "Pour montrer le logo d'une cryptomonnaie, écris son code entre deux-points : " +
            CRYPTOS.keys.joinToString(", ") { ":$it:" } + "."

    private val motif = Regex(":([a-z_]{2,20}):")

    fun remplacer(texte: String): String {
        if (!texte.contains(':')) return texte
        return motif.replace(texte) { m ->
            val code = m.groupValues[1]
            CODES[code] ?: CRYPTOS[code]?.let { "![logo](logo:$it)" } ?: m.value
        }
    }
}
