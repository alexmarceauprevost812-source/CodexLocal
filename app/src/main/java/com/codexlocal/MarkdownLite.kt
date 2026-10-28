package com.codexlocal

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import android.graphics.drawable.Drawable
import android.text.style.DynamicDrawableSpan
import android.text.style.ImageSpan
import com.codexlocal.chat.EmojisChat
import com.codexlocal.agent.BlocMessage
import com.codexlocal.agent.DecoupageMessage

/** Fond de la barre de titre d'un fichier ecrit par l'IA (sert aussi a la retrouver dans les tests). */
internal class TitreFichierSpan : BackgroundColorSpan(0xFF16263A.toInt())

/**
 * Rendu Markdown minimaliste adapte au code :
 *  - chaque fichier ou bloc de code ecrit par l'IA a sa barre de TITRE (« 📄 index.html »), puis son code
 *    en monospace sur fond tres sombre, colore par [SyntaxHighlighter] selon l'extension du fichier ;
 *  - le `code en ligne` est en monospace orange ;
 *  - les **gras** sont mis en gras ;
 *  - les liens `[texte](https://…)` et les adresses https sont cliquables ;
 *  - `![logo](…)` affiche une petite image (logo de crypto ou de site, voir [com.codexlocal.chat.ImagesChat]) ;
 *  - les codes `:fusee:` deviennent des mini-émojis (voir [EmojisChat]).
 *
 * Le decoupage (texte / fichiers / titres) est fait par [DecoupageMessage]. Aucune dependance externe.
 */
object MarkdownLite {

    private const val CODE_BG = 0xFF070707.toInt()
    private const val INLINE_CODE = 0xFFFFC777.toInt()
    private const val TITRE_FG = 0xFF8AB4F8.toInt()
    private const val REFLEXION = 0xFF8A8A8A.toInt()

    private const val LIEN = 0xFFFF9A3D.toInt()

    /** Caractère réservé à une image en ligne. */
    private const val OBJET = "\uFFFC"

    private val motifRiche = Regex(
        "!\\[([^\\]\\n]{0,80})\\]\\(([^)\\s]{1,300})\\)" +      // ![alt](source)
            "|\\[([^\\]\\n]{1,200})\\]\\((https?://[^)\\s]{1,500})\\)" + // [texte](https://…)
            "|(https?://[^\\s<>()\\[\\]\"]{3,500})"                      // adresse nue
    )

    /**
     * @param images donne l'image prête pour une source `![…](source)`, ou null si elle n'est
     *   pas (encore) disponible ; sans résolveur, les images ne s'affichent pas.
     */
    fun format(text: String, images: ((String) -> Drawable?)? = null): CharSequence {
        resolveurImages = images
        try {
            return formatInterne(text)
        } finally {
            resolveurImages = null
        }
    }

    private var resolveurImages: ((String) -> Drawable?)? = null

    private fun formatInterne(text: String): CharSequence {
        val out = SpannableStringBuilder()
        for (bloc in DecoupageMessage.decouper(text)) {
            when (bloc) {
                is BlocMessage.Texte -> if (bloc.texte.isNotBlank()) {
                    if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append("\n")
                    appendProse(out, bloc.texte)
                }
                is BlocMessage.Code -> appendCodeBlock(out, bloc)
                is BlocMessage.Reflexion -> appendReflexion(out, bloc)
            }
        }
        return out
    }

    // ------------------------------------------------------------- reflexion

    /** Une seule ligne grise : le raisonnement du modele n'est pas affiche en entier. */
    private fun appendReflexion(out: SpannableStringBuilder, bloc: BlocMessage.Reflexion) {
        if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append("\n")
        val debut = out.length
        out.append(if (bloc.enCours) "💭 Réflexion…" else "💭 Réflexion (${bloc.mots} mots)")
        val fin = out.length
        out.setSpan(ForegroundColorSpan(REFLEXION), debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(StyleSpan(Typeface.ITALIC), debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.append("\n")
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
            if (index % 2 == 1) out.append(chunk) else appendRiche(out, EmojisChat.remplacer(chunk))
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

    /** Texte normal avec images, liens et adresses cliquables. */
    private fun appendRiche(out: SpannableStringBuilder, texte: String) {
        var curseur = 0
        for (m in motifRiche.findAll(texte)) {
            out.append(texte, curseur, m.range.first)
            curseur = m.range.last + 1
            val image = m.groups[2]?.value
            val lienTexte = m.groups[3]?.value
            val lienUrl = m.groups[4]?.value
            val nue = m.groups[5]?.value
            when {
                image != null -> {
                    val dessin = resolveurImages?.invoke(image)
                    if (dessin != null) {
                        val debut = out.length
                        out.append(OBJET)
                        out.setSpan(
                            ImageSpan(dessin, DynamicDrawableSpan.ALIGN_CENTER), debut, out.length,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    } else if (image.startsWith("https://")) {
                        // Image non permise ou indisponible : on garde au moins un lien.
                        val nom = m.groups[1]?.value?.takeIf { it.isNotBlank() } ?: "image"
                        ajouterLien(out, "🖼️ $nom", image)
                    }
                }
                lienTexte != null && lienUrl != null -> ajouterLien(out, lienTexte, lienUrl)
                nue != null -> {
                    // La ponctuation finale (« voir https://x.com. ») ne fait pas partie du lien.
                    val propre = nue.trimEnd('.', ',', ';', ':', '!', '?', '»', '\'')
                    ajouterLien(out, propre, propre)
                    out.append(nue, propre.length, nue.length)
                }
            }
        }
        out.append(texte, curseur, texte.length)
    }

    private fun ajouterLien(out: SpannableStringBuilder, texte: String, url: String) {
        val debut = out.length
        out.append(texte)
        out.setSpan(URLSpan(url), debut, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(ForegroundColorSpan(LIEN), debut, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
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
