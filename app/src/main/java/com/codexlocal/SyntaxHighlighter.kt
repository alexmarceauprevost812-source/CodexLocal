package com.codexlocal

import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import com.codexlocal.coloration.AnalyseurCode
import com.codexlocal.coloration.CouleursCode
import com.codexlocal.coloration.Genre
import com.codexlocal.coloration.Jeton

/**
 * Coloration du code a l'ecran (editeur et blocs de code du chat).
 *
 * [AnalyseurCode] lit le texte et dit ce que chaque morceau est (mot-cle, chaine, fonction, couleur...) ;
 * ici on transforme ce « genre » en couleur et en style, puis on pose les spans sur un texte deja affiche :
 * `colorize(sb, offset, code, lang)`.
 *
 * Les spans posees sont de nos propres classes : [effacer] ne retire que celles-la et laisse intactes
 * celles d'autres sources (selection, correcteur...).
 */
object SyntaxHighlighter {

    /** Couleur de texte posee par la coloration. */
    class CouleurCode(couleur: Int) : ForegroundColorSpan(couleur)

    /** Gras ou italique pose par la coloration. */
    class StyleCode(style: Int) : StyleSpan(style)

    /** Pastille derriere une couleur ecrite dans le code (#FF9A3D, rgb(...)). */
    class PastilleCode(couleur: Int) : BackgroundColorSpan(couleur)

    // ------------------------------------------------------------------ palette (fond sombre)
    private const val C_COMMENT = 0xFF6B7280.toInt()
    private const val C_STRING = 0xFF9ECE6A.toInt()
    private const val C_ESCAPE = 0xFF89DDFF.toInt()
    private const val C_NUMBER = 0xFFFFC777.toInt()
    private const val C_KEYWORD = 0xFFFF9A3D.toInt()
    private const val C_CONSTANT = 0xFFFF757F.toInt()
    private const val C_TYPE = 0xFF2AC3DE.toInt()
    private const val C_FUNCTION = 0xFF7AA2F7.toInt()
    private const val C_DECLARATION = 0xFFFFD866.toInt()
    private const val C_PROPERTY = 0xFF73DACA.toInt()
    private const val C_BUILTIN = 0xFF7DCFFF.toInt()
    private const val C_ANNOTATION = 0xFFBB9AF7.toInt()
    private const val C_VARIABLE = 0xFFFF9E64.toInt()
    private const val C_OPERATOR = 0xFFC0CAF5.toInt()
    private const val C_PUNCT = 0xFF9AA5B1.toInt()
    private const val C_PAREN_1 = 0xFFFFD866.toInt()
    private const val C_PAREN_2 = 0xFFC792EA.toInt()
    private const val C_PAREN_3 = 0xFF4FC3F7.toInt()
    private const val C_TAG = 0xFFFF7A00.toInt()
    private const val C_ATTR = 0xFF2AC3DE.toInt()
    private const val C_KEY = 0xFF73DACA.toInt()
    private const val C_SELECTOR = 0xFFE0AF68.toInt()
    private const val C_HEADING = 0xFFFF9A3D.toInt()
    private const val C_LINK = 0xFF7AA2F7.toInt()

    /** Couleur d'un genre, ou 0 pour « couleur normale ». */
    internal fun couleurDe(genre: Genre): Int = when (genre) {
        Genre.COMMENTAIRE -> C_COMMENT
        Genre.TEXTE -> C_STRING
        Genre.ECHAPPEMENT -> C_ESCAPE
        Genre.NOMBRE -> C_NUMBER
        Genre.MOT_CLE -> C_KEYWORD
        Genre.CONSTANTE -> C_CONSTANT
        Genre.TYPE -> C_TYPE
        Genre.FONCTION -> C_FUNCTION
        Genre.DECLARATION -> C_DECLARATION
        Genre.PROPRIETE -> C_PROPERTY
        Genre.INTEGRE -> C_BUILTIN
        Genre.ANNOTATION -> C_ANNOTATION
        Genre.VARIABLE -> C_VARIABLE
        Genre.OPERATEUR -> C_OPERATOR
        Genre.PONCTUATION -> C_PUNCT
        Genre.PAREN_1 -> C_PAREN_1
        Genre.PAREN_2 -> C_PAREN_2
        Genre.PAREN_3 -> C_PAREN_3
        Genre.BALISE -> C_TAG
        Genre.ATTRIBUT -> C_ATTR
        Genre.CLE -> C_KEY
        Genre.SELECTEUR -> C_SELECTOR
        Genre.TITRE -> C_HEADING
        Genre.LIEN -> C_LINK
        Genre.COULEUR -> 0
    }

    /** Style d'un genre : gras pour ce qui structure, italique pour les commentaires. */
    internal fun styleDe(genre: Genre): Int = when (genre) {
        Genre.COMMENTAIRE -> Typeface.ITALIC
        Genre.MOT_CLE, Genre.DECLARATION, Genre.BALISE, Genre.TITRE -> Typeface.BOLD
        else -> Typeface.NORMAL
    }

    // ------------------------------------------------------------------ API

    /** Colore le texte [code], deja present dans [sb] a partir de [offset]. */
    fun colorize(sb: Spannable, offset: Int, code: String, lang: String) {
        for (jeton in AnalyseurCode.analyser(code, lang)) appliquer(sb, offset, jeton)
    }

    /** Retire toutes les spans posees par [colorize] (et seulement celles-la). */
    fun effacer(sb: Spannable) {
        sb.getSpans(0, sb.length, CouleurCode::class.java).forEach { sb.removeSpan(it) }
        sb.getSpans(0, sb.length, StyleCode::class.java).forEach { sb.removeSpan(it) }
        sb.getSpans(0, sb.length, PastilleCode::class.java).forEach { sb.removeSpan(it) }
    }

    // ------------------------------------------------------------------ interne

    private fun appliquer(sb: Spannable, offset: Int, jeton: Jeton) {
        val debut = offset + jeton.debut
        val fin = offset + jeton.fin
        if (debut < 0 || fin > sb.length || debut >= fin) return
        if (jeton.genre == Genre.COULEUR) {
            // la couleur ecrite dans le code sert de fond, et le texte passe en noir ou blanc pour rester lisible
            sb.setSpan(PastilleCode(jeton.couleur), debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(CouleurCode(CouleursCode.texteSur(jeton.couleur)), debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }
        sb.setSpan(CouleurCode(couleurDe(jeton.genre)), debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val style = styleDe(jeton.genre)
        if (style != Typeface.NORMAL) {
            sb.setSpan(StyleCode(style), debut, fin, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
