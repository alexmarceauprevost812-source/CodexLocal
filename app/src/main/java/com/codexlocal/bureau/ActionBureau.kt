package com.codexlocal.bureau

import androidx.annotation.StringRes
import com.codexlocal.R

/**
 * Les choix du « Bureau » : une grande page avec toutes les entrees de menu de l'appli.
 * Le Bureau ne fait rien lui-meme : il renvoie la `cle` choisie a MainActivity.
 */
enum class ActionBureau(
    val cle: String,
    val emoji: String,
    @StringRes val titre: Int,
    @StringRes val sous: Int
) {
    CODEX("codex", "💻", R.string.bureau_codex, R.string.bureau_codex_sous),
    CHAT("chat", "💬", R.string.bureau_chat, R.string.bureau_chat_sous),
    EQUIPE("equipe", "⛑️", R.string.bureau_equipe, R.string.bureau_equipe_sous),
    STUDIO("studio", "🛠️", R.string.bureau_studio, R.string.bureau_studio_sous),
    TESTER("tester", "▶️", R.string.bureau_tester, R.string.bureau_tester_sous),
    PROJETS("projets", "📁", R.string.bureau_projets, R.string.bureau_projets_sous),
    OUVRIR_DOSSIER("dossier", "📂", R.string.bureau_dossier, R.string.bureau_dossier_sous),
    NOUVELLE("nouvelle", "📝", R.string.bureau_nouvelle, R.string.bureau_nouvelle_sous),
    REGLAGES("reglages", "⚙️", R.string.bureau_reglages, R.string.bureau_reglages_sous),
    MISES_A_JOUR("maj", "🔄", R.string.bureau_maj, R.string.bureau_maj_sous),
    PARTAGER("partager", "🔗", R.string.bureau_partager, R.string.bureau_partager_sous);

    companion object {
        fun depuisCle(cle: String?): ActionBureau? = values().firstOrNull { it.cle == cle }
    }
}
