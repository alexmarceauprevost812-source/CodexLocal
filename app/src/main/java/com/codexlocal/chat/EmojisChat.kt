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

    private val motif = Regex(":([a-z_]{2,20}):")

    fun remplacer(texte: String): String {
        if (!texte.contains(':')) return texte
        return motif.replace(texte) { m -> CODES[m.groupValues[1]] ?: m.value }
    }
}
