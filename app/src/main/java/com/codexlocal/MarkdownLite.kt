package com.codexlocal

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan

/**
 * Rendu Markdown minimaliste adapte au code :
 *  - les blocs ```langage sont affiches en monospace sur fond tres sombre
 *    et colores par [SyntaxHighlighter] ;
 *  - le `code en ligne` est en monospace orange ;
 *  - les **gras** sont mis en gras.
 *
 * Aucune dependance externe.
 */
object MarkdownLite {

    private const val CODE_BG = 0xFF070707.toInt()
    private const val INLINE_CODE = 0xFFFFC777.toInt()

    fun format(text: String): CharSequence {
        val out = SpannableStringBuilder()
        val parts = text.split("```")
        parts.forEachIndexed { index, part ->
            if (index % 2 == 1) appendCodeBlock(out, part) else appendProse(out, part)
        }
        return out
    }

    // ------------------------------------------------------------- bloc de code

    private fun appendCodeBlock(out: SpannableStringBuilder, raw: String) {
        var body = raw
        var lang = ""

        val firstNewline = body.indexOf('\n')
        if (firstNewline >= 0) {
            val first = body.substring(0, firstNewline).trim()
            if (first.isNotEmpty() && first.length <= 20 && !first.contains(' ')) {
                lang = first
                body = body.substring(firstNewline + 1)
            }
        }
        body = body.trimEnd('\n')
        if (body.isEmpty()) return

        if (out.isNotEmpty() && out[out.length - 1] != '\n') out.append("\n")

        val start = out.length
        out.append(body)
        val end = out.length

        out.setSpan(TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(BackgroundColorSpan(CODE_BG), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        out.setSpan(RelativeSizeSpan(0.94f), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        SyntaxHighlighter.colorize(out, start, body, lang)

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
