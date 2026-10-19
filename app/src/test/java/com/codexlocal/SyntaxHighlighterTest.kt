package com.codexlocal

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.view.ContextThemeWrapper
import com.codexlocal.SyntaxHighlighter.CouleurCode
import com.codexlocal.SyntaxHighlighter.PastilleCode
import com.codexlocal.SyntaxHighlighter.StyleCode
import com.codexlocal.coloration.CouleursCode
import com.codexlocal.coloration.Genre
import com.codexlocal.editor.CodeEditText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SyntaxHighlighterTest {

    private fun colore(code: String, langage: String, avant: String = ""): SpannableStringBuilder {
        val sb = SpannableStringBuilder(avant + code)
        SyntaxHighlighter.colorize(sb, avant.length, code, langage)
        return sb
    }

    private fun <T> SpannableStringBuilder.sur(type: Class<T>, debut: Int, fin: Int): List<T> =
        getSpans(debut, fin, type).filter { getSpanStart(it) == debut && getSpanEnd(it) == fin }

    private fun nombreDeSpans(sb: SpannableStringBuilder) =
        sb.getSpans(0, sb.length, CouleurCode::class.java).size +
            sb.getSpans(0, sb.length, StyleCode::class.java).size +
            sb.getSpans(0, sb.length, PastilleCode::class.java).size

    @Test fun unMotCleEstOrangeEtEnGras() {
        val sb = colore("val x = 1", "kotlin")
        val couleur = sb.sur(CouleurCode::class.java, 0, 3).single()
        assertEquals(SyntaxHighlighter.couleurDe(Genre.MOT_CLE), couleur.foregroundColor)
        val style = sb.sur(StyleCode::class.java, 0, 3).single()
        assertEquals(Typeface.BOLD, style.style)
    }

    @Test fun unCommentaireEstGrisEtEnItalique() {
        val code = "val x = 1 // note"
        val sb = colore(code, "kotlin")
        val debut = code.indexOf("//")
        assertEquals(SyntaxHighlighter.couleurDe(Genre.COMMENTAIRE),
            sb.sur(CouleurCode::class.java, debut, code.length).single().foregroundColor)
        assertEquals(Typeface.ITALIC, sb.sur(StyleCode::class.java, debut, code.length).single().style)
    }

    @Test fun unNombreEstColoreSansStyle() {
        val sb = colore("val x = 42", "kotlin")
        assertEquals(SyntaxHighlighter.couleurDe(Genre.NOMBRE), sb.sur(CouleurCode::class.java, 8, 10).single().foregroundColor)
        assertTrue(sb.sur(StyleCode::class.java, 8, 10).isEmpty())
    }

    @Test fun chaqueGenreADeLaCouleurSaufLaCouleurEcrite() {
        for (genre in Genre.values()) {
            if (genre == Genre.COULEUR) continue
            assertEquals("$genre doit avoir une couleur opaque", 0xFF, SyntaxHighlighter.couleurDe(genre) ushr 24)
        }
    }

    @Test fun uneCouleurEcriteDansLeCodeDevientUnePastille() {
        val code = "val c = \"#FF0000\""
        val sb = colore(code, "kotlin")
        val debut = code.indexOf('#')
        val fin = code.length - 1
        assertEquals(0xFFFF0000.toInt(), sb.sur(PastilleCode::class.java, debut, fin).single().backgroundColor)
        // texte lisible sur la pastille : blanc sur rouge, noir sur jaune
        assertEquals(CouleursCode.texteSur(0xFFFF0000.toInt()),
            sb.sur(CouleurCode::class.java, debut, fin).single().foregroundColor)

        val jaune = colore("c = \"#FFFF00\"", "kotlin")
        assertEquals(0xFF000000.toInt(), jaune.sur(CouleurCode::class.java, 5, 12).single().foregroundColor)
    }

    @Test fun leDecalageSertAColorerUnBlocDansUnTextePlusLong() {
        val avant = "Voici le code :\n"
        val sb = colore("val x = 1", "kotlin", avant)
        assertEquals(1, sb.sur(CouleurCode::class.java, avant.length, avant.length + 3).size)
        assertTrue("rien ne doit etre colore avant le bloc", sb.getSpans(0, avant.length, CouleurCode::class.java).isEmpty())
    }

    @Test fun effacerNeRetireQueLesCouleursDeLaColoration() {
        val sb = colore("val x = \"#00FF00\" // c", "kotlin")
        val etrangere = ForegroundColorSpan(0xFF123456.toInt())
        val soulignement = UnderlineSpan()
        sb.setSpan(etrangere, 0, 3, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(soulignement, 4, 5, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        assertTrue(nombreDeSpans(sb) > 0)

        SyntaxHighlighter.effacer(sb)

        assertEquals(0, nombreDeSpans(sb))
        assertEquals(listOf(etrangere), sb.getSpans(0, sb.length, ForegroundColorSpan::class.java).toList())
        assertEquals(listOf(soulignement), sb.getSpans(0, sb.length, UnderlineSpan::class.java).toList())
    }

    @Test fun recolorerNeFaitPasGrossirLeNombreDeSpans() {
        val code = "fun main() { println(\"salut\") } // fin"
        val sb = colore(code, "kotlin")
        val premier = nombreDeSpans(sb)
        assertTrue(premier > 0)
        repeat(3) {
            SyntaxHighlighter.effacer(sb)
            SyntaxHighlighter.colorize(sb, 0, code, "kotlin")
        }
        assertEquals(premier, nombreDeSpans(sb))
    }

    @Test fun unCodePlusLongQueLeTexteNePlantePas() {
        val sb = SpannableStringBuilder("val")
        SyntaxHighlighter.colorize(sb, 0, "val x = 1 // beaucoup plus long que le texte", "kotlin")
        SyntaxHighlighter.colorize(sb, 10, "val x = 1", "kotlin")
        assertEquals(1, sb.sur(CouleurCode::class.java, 0, 3).size)
    }

    @Test fun lesLangagesSansCouleurLaissentLeTexteIntact() {
        val sb = colore("juste du texte", "txt")
        assertEquals(0, nombreDeSpans(sb))
        assertEquals("juste du texte", sb.toString())
    }

    @Test fun lesBlocsDeCodeDuChatSontColores() {
        val rendu = MarkdownLite.format("Voici :\n```kotlin\nval x = 1\n```\nVoila.") as Spanned
        val texte = rendu.toString()
        assertFalse("les ``` ne s'affichent pas", texte.contains("```"))
        val debut = texte.indexOf("val x")
        val couleurs = rendu.getSpans(debut, debut + 3, CouleurCode::class.java)
        assertEquals(1, couleurs.size)
        assertEquals(SyntaxHighlighter.couleurDe(Genre.MOT_CLE), couleurs[0].foregroundColor)
        assertTrue("le texte avant le bloc n'est pas colore", rendu.getSpans(0, debut, CouleurCode::class.java).isEmpty())
    }

    // ------------------------------------------------------------------ l'editeur

    private fun editeur(): CodeEditText {
        val contexte = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_CodexLocal)
        return CodeEditText(contexte)
    }

    @Test fun lEditeurColoreLeCodeEtGardeLesSpansDesAutres() {
        val ed = editeur()
        ed.definirLangage("kotlin")
        ed.setText("val x = 1 // c")
        val etrangere = ForegroundColorSpan(0xFF654321.toInt())
        ed.text!!.setSpan(etrangere, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        ed.recolorerMaintenant()

        val texte = ed.text!!
        assertTrue(texte.getSpans(0, texte.length, CouleurCode::class.java).isNotEmpty())
        assertTrue("la couleur d'un autre n'est pas effacee",
            texte.getSpans(0, texte.length, ForegroundColorSpan::class.java).contains(etrangere))
    }

    @Test fun lEditeurRecolorieApresUneModification() {
        val ed = editeur()
        ed.definirLangage("kotlin")
        ed.setText("val x = 1")
        ed.recolorerMaintenant()
        val avant = ed.text!!.getSpans(0, ed.text!!.length, CouleurCode::class.java).size

        ed.setText("val x = 1\nval y = 2")
        ed.recolorerMaintenant()
        val texte = ed.text!!
        val apres = texte.getSpans(0, texte.length, CouleurCode::class.java).size
        assertTrue("$avant -> $apres", apres > avant)
        assertEquals(2, texte.getSpans(0, texte.length, CouleurCode::class.java)
            .count { texte.subSequence(texte.getSpanStart(it), texte.getSpanEnd(it)).toString() == "val" })
    }
}
