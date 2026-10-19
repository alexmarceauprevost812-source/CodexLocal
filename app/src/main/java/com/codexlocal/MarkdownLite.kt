package com.codexlocal

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import com.codexlocal.agent.BlocMessage
import com.codexlocal.agent.DecoupageMessage

/** Fond de la barre de titre d'un fichier ecrit par l'IA (sert aussi a la retrouver dans les tests). */
internal class TitreFichierSpan : BackgroundColorSpan(0xFF16263A.toInt())

/**
 * Rendu Markdown minimaliste adapte au code :
 *  - chaque fichier ou bloc de code ecrit par l'IA a sa barre de TITRE (« 📄 index.html »), puis son code
 *    en monospace sur fond tres sombre, colore par [SyntaxHighlighter] selon l'extension du fichier ;
 *  - le `code en ligne` est en monospace orange ;
 *  - les **gras** sont mis en gras.
 *
 * Le decoupage (texte / fichiers / titres) est fait par [DecoupageMessage]. Aucune dependance externe.
 */
object MarkdownLite {

    private const val CODE_BG = 0xFF070707.toInt()
    private const val INLINE_CODE = 0xFFFFC777.toInt()
    private const val TITRE_FG = 0xFF8AB4F8.toInt()

    fun format(text: String): CharSequence {
        val out = SpannableStringBuilder()
        for (bloc in DecoupageMessage.decouper(text)) {
            when (bloc) {
                is BlocMessage.Texte -> if (bloc.texte.isNotBlank()) {
                    if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append("\n")
                    appendProse(out, bloc.texte)
                }
                is BlocMessage.Code -> appendCodeBlock(out, bloc)
            }
        }
        return out
    }

    // ------------------------------------------------------------- bloc de code

    private fun appendCodeBlock(out: SpannableStringBuilder, bloc: BlocMessage.Code) {
        val titre = bloc.titre
        if (bloc.corps.isBlank() && titre == null) return

        if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append("\n")

        if (titre != null) {
            val debutTitre = out.length
            out.append(" 📄 ").append(titre).append(' ')
            val finTitre = out.length
            out.setSpan(TitreFichierSpan(), debutTitre, finTitre, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(ForegroundColorSpan(TITRE_FG), debutTitre, finTitre, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(StyleSpan(Typeface.BOLD), debutTitre, finTitre, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.setSpan(TypefaceSpan("monospace"), debutTitre, finTitre, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            out.append("\n")
        }
        if (bloc.corps.isEmpty()) return

        val start = out.length
        out.append(bloc.corps)
        val end = out.length

        out.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(BackgroundColorSpan(CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(RelativeSizeSpan(0.94f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        SyntaxHighlighter.colorize(out, start, bloc.corps, bloc.langage)

        out.append("\n")
    }

    // ------------------------------------------------------------ texte normal

    private fun appendProse(out: SpannableStringBuilder, text: String) {
        val chunks = text.split("`")
        chunks.forEachIndexed { index, chunk ->
            val start = out.length
            out.append(chunk)
            val end = out.length
            if (index % 2 == 1) {
                if (chunk.isNotEmpty()) {
                    out.setSpan(
                        TypefaceSpan("monospace"), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    out.setSpan(
                        ForegroundColorSpan(INLINE_CODE), start, end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            } else {
                applyBold(out, start)
            }
        }
    }

    private fun applyBold(sb: SpannableStringBuilder, from: Int) {
        var i = from
        while (i + 1 < sb.length) {
            if (sb[i] == '*' && sb[i + 1] == '*') {
                val close = sb.indexOf("**", i + 2)
                if (close > 0) {
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD), i + 2, close,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    i = close + 2
                    continue
                }
            }
            i++
        }
    }
}
