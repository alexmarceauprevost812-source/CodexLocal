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

    /** Émoji choisi selon le sujet d'un titre (nouvelles, rendez-vous, tâches, notes). */
    private val SUJETS: List<Pair<Regex, String>> = listOf(
        "hockey|canadiens?|nhl|lnh" to "🏒", "soccer|football|fifa|but\\b" to "⚽", "sport|match|séries|olymp" to "🏆",
        "élection|politique|gouvernement|ministre|député|premier ministre|parlement|assemblée" to "🏛️",
        "économie|bourse|inflation|taux|banque|argent|budget|prix|impôt|taxe" to "💰",
        "crypto|bitcoin|ethereum" to "₿", "météo|tempête|neige|pluie|chaleur|froid|ouragan|verglas" to "🌦️",
        "feu|incendie|pompier" to "🔥", "police|accident|collision|crime|procès|tribunal" to "🚨",
        "santé|hôpital|médecin|clinique|vaccin|virus" to "🏥", "dentiste|dent" to "🦷",
        "école|cours|examen|université|cégep|étude" to "🎓", "technolog|ia\\b|intelligence artificielle|apple|google|android|logiciel|code|appli" to "💻",
        "espace|nasa|fusée|lune|mars\\b" to "🚀", "film|cinéma|série|netflix|spectacle|musique|concert|festival" to "🎬",
        "voiture|auto|route|trafic|essence" to "🚗", "avion|vol\\b|aéroport|voyage|vacances" to "✈️",
        "réunion|travail|bureau|client|rencontre|meeting|job|emploi" to "💼", "anniversaire|fête|party" to "🎂",
        "épicerie|courses|magasin|achat|acheter" to "🛒", "souper|dîner|déjeuner|resto|restaurant|repas|manger" to "🍽️",
        "sport|gym|entraînement|course|marche|vélo" to "🏃", "appel|téléphone|appeler" to "📞", "courriel|email|mail" to "📧",
        "chien|chat|vétérinaire|animal" to "🐾", "ménage|nettoyer|lavage" to "🧹", "facture|payer|paiement|loyer" to "🧾",
        "lire|livre|note|écrire" to "📝", "québec|montréal|saguenay|lac-saint-jean" to "⚜️", "guerre|armée|conflit" to "🕊️",
        "environnement|climat|forêt|nature" to "🌲",
    ).map { (motif, emoji) -> Regex("(?iU)\\b(?:$motif)") to emoji }

    fun pourSujet(titre: String, defaut: String): String =
        SUJETS.firstOrNull { it.first.containsMatchIn(titre) }?.second ?: defaut

    private val motif = Regex(":([a-z_]{2,20}):")

    fun remplacer(texte: String): String {
        if (!texte.contains(':')) return texte
        return motif.replace(texte) { m ->
            val code = m.groupValues[1]
            CODES[code] ?: CRYPTOS[code]?.let { "![logo](logo:$it)" } ?: m.value
        }
    }
}
