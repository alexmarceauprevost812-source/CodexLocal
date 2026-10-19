package com.codexlocal

import android.text.Spanned
import android.text.style.BackgroundColorSpan
import com.codexlocal.SyntaxHighlighter.CouleurCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MarkdownLiteTest {

    private fun rendu(texte: String) = MarkdownLite.format(texte) as Spanned

    @Test fun everyFileHasItsTitleBarAboveItsCode() {
        val r = rendu(
            "Voici le site :\n=== FICHIER: index.html ===\n<h1>Salut</h1>\n=== FIN ===\n" +
                "=== FICHIER: css/style.css ===\nbody { margin: 0; }\n=== FIN ===\nVoilà."
        )
        val texte = r.toString()
        assertFalse("les marqueurs ne s'affichent pas", texte.contains("==="))
        val barres = r.getSpans(0, r.length, TitreFichierSpan::class.java)
        assertEquals(2, barres.size)
        val titres = barres.sortedBy { r.getSpanStart(it) }.map { r.substring(r.getSpanStart(it), r.getSpanEnd(it)).trim() }
        assertEquals(listOf("📄 index.html", "📄 css/style.css"), titres)
        assertTrue(texte.indexOf("index.html") < texte.indexOf("<h1>"))
        assertTrue(texte.indexOf("style.css") < texte.indexOf("margin"))
    }

    @Test fun theCodeOfAFileIsColoredAccordingToItsExtension() {
        val r = rendu("=== FICHIER: main.py ===\ndef f():\n    return 1\n=== FIN ===")
        val debut = r.toString().indexOf("def")
        val couleurs = r.getSpans(debut, debut + 3, CouleurCode::class.java)
        assertEquals(1, couleurs.size)
    }

    @Test fun aFileStillBeingWrittenAlreadyShowsItsTitle() {
        val r = rendu("=== FICHIER: app.js ===\nlet a = 1;\nlet b")
        assertEquals(1, r.getSpans(0, r.length, TitreFichierSpan::class.java).size)
        assertTrue(r.toString().contains("app.js"))
        assertTrue(r.toString().contains("let b"))
    }

    @Test fun markdownBlocksWithATitleLineGetTheTitleBarToo() {
        val r = rendu("**index.html**\n```html\n<p>a</p>\n```")
        val texte = r.toString()
        assertEquals(1, r.getSpans(0, r.length, TitreFichierSpan::class.java).size)
        assertEquals("the title is not written twice", texte.indexOf("index.html"), texte.lastIndexOf("index.html"))
        assertFalse(texte.contains("```"))
        assertFalse(texte.contains("**"))
    }

    @Test fun blocksWithoutATitleHaveNoBar() {
        val r = rendu("Lance :\n```bash\nnpm start\n```")
        assertTrue(r.getSpans(0, r.length, TitreFichierSpan::class.java).isEmpty())
        assertTrue(r.toString().contains("npm start"))
    }

    @Test fun theCodeKeepsItsDarkBackground() {
        val r = rendu("=== FICHIER: a.txt ===\nbonjour\n=== FIN ===")
        val debut = r.toString().indexOf("bonjour")
        val fonds = r.getSpans(debut, debut + 7, BackgroundColorSpan::class.java)
            .filter { it !is TitreFichierSpan }
        assertEquals(1, fonds.size)
    }

    @Test fun plainTextAndInlineCodeStillWork() {
        val r = rendu("Du **gras** et du `code` ici.")
        assertEquals("Du **gras** et du code ici.", r.toString())
        val debut = r.toString().indexOf("code")
        assertEquals(1, r.getSpans(debut, debut + 4, android.text.style.ForegroundColorSpan::class.java).size)
    }

    @Test fun emptyOrOddTextDoesNotCrash() {
        listOf("", "```", "=== FICHIER: x.txt ===", "```\n```", "**a.txt**\n```").forEach { MarkdownLite.format(it) }
    }

    @Test fun aReasoningIsOneGreyLineInsteadOfTheWholeText() {
        val r = rendu("<think>\nun deux trois quatre\n</think>\nVoici le site.")
        assertEquals("💭 Réflexion (4 mots)\nVoici le site.", r.toString().trimEnd())
        assertFalse(r.toString().contains("trois"))
    }

    @Test fun aReasoningStillGoingOnIsShownAsSuch() {
        val r = rendu("<think>\nje cherche encore")
        assertEquals("💭 Réflexion…", r.toString().trim())
    }

    @Test fun filesAfterAReasoningStillGetTheirTitleBar() {
        val r = rendu("<think>\nhmm\n</think>\n=== FICHIER: index.html ===\n<p>a</p>\n=== FIN ===")
        assertEquals(1, r.getSpans(0, r.length, TitreFichierSpan::class.java).size)
        assertTrue(r.toString().contains("<p>a</p>"))
    }
}
