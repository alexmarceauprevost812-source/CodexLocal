package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DecoupageMessageTest {

    private fun codes(reponse: String) = DecoupageMessage.decouper(reponse).filterIsInstance<BlocMessage.Code>()

    @Test fun markerFilesGetTheirTitle() {
        val blocs = DecoupageMessage.decouper(
            "Voici :\n=== FICHIER: index.html ===\n<h1>Salut</h1>\n=== FIN ===\nEt le style :\n" +
                "=== FICHIER: css/style.css ===\nbody{}\n=== FIN ===\nVoilà."
        )
        assertEquals(5, blocs.size)
        assertEquals(BlocMessage.Texte("Voici :"), blocs[0])
        assertEquals(BlocMessage.Code("index.html", "html", "<h1>Salut</h1>", true), blocs[1])
        assertEquals(BlocMessage.Texte("Et le style :"), blocs[2])
        assertEquals(BlocMessage.Code("css/style.css", "css", "body{}", true), blocs[3])
        assertEquals(BlocMessage.Texte("Voilà."), blocs[4])
    }

    @Test fun aFileStillBeingWrittenKeepsItsTitle() {
        val c = codes("=== FICHIER: app.js ===\nlet a = 1;\nlet b").single()
        assertEquals("app.js", c.titre)
        assertFalse(c.ferme)
        assertEquals("let a = 1;\nlet b", c.corps)
    }

    @Test fun nextMarkerClosesTheFile() {
        val liste = codes("=== FICHIER: a.txt ===\nun\n=== FICHIER: b.txt ===\ndeux\n=== FIN ===")
        assertEquals(listOf("a.txt", "b.txt"), liste.map { it.titre })
        assertTrue(liste.all { it.ferme })
    }

    @Test fun markdownBlockWithTitleLineBeforeUsesItAsTitle() {
        val blocs = DecoupageMessage.decouper("Voici le site.\n**index.html**\n```html\n<p>a</p>\n```\nFini.")
        assertEquals(
            listOf(
                BlocMessage.Texte("Voici le site."),
                BlocMessage.Code("index.html", "html", "<p>a</p>", true),
                BlocMessage.Texte("Fini.")
            ),
            blocs
        )
    }

    @Test fun announcingSentenceStaysAsTextButGivesTheTitle() {
        val blocs = DecoupageMessage.decouper("Crée le fichier `main.py` pour lancer ça :\n```python\nprint(1)\n```")
        assertEquals("main.py", (blocs.last() as BlocMessage.Code).titre)
        assertTrue(blocs.first() is BlocMessage.Texte)
    }

    @Test fun nameInOpeningLineAndLanguageAreSeparated() {
        val c = codes("```python title=\"main.py\"\nprint(1)\n```").single()
        assertEquals("main.py", c.titre)
        assertEquals("python", c.langage)
    }

    @Test fun nameAsOnlyOpeningInfoGivesLanguageFromExtension() {
        val c = codes("```app.js\nlet a = 1\n```").single()
        assertEquals("app.js", c.titre)
        assertEquals("javascript", c.langage)
    }

    @Test fun commentOnFirstLineGivesTheTitle() {
        val c = codes("```html\n<!-- index.html -->\n<p>x</p>\n```").single()
        assertEquals("index.html", c.titre)
    }

    @Test fun blockWithoutNameHasNoTitle() {
        val c = codes("Lance :\n```bash\nnpm start\n```").single()
        assertNull(c.titre)
        assertEquals("bash", c.langage)
    }

    @Test fun unclosedBlockIsNotClosed() {
        val c = codes("```kotlin\nval a = 1").single()
        assertFalse(c.ferme)
        assertEquals("val a = 1", c.corps)
    }

    @Test fun nestedFencesStayInsideTheBlock() {
        val c = codes("**LISEZMOI.md**\n```markdown\n# T\n```bash\nls\n```\nFin\n```\nVoilà").single()
        assertTrue(c.corps.contains("```bash"))
        assertTrue(c.corps.endsWith("Fin"))
        assertTrue(c.ferme)
    }

    @Test fun readMarkerBecomesALine() {
        val blocs = DecoupageMessage.decouper("=== LIRE: README.md ===")
        assertEquals(1, blocs.size)
        assertTrue((blocs[0] as BlocMessage.Texte).texte.contains("README.md"))
    }

    @Test fun plainTextIsOneBlock() {
        assertEquals(listOf<BlocMessage>(BlocMessage.Texte("a\nb\n\nc")), DecoupageMessage.decouper("a\nb\n\nc"))
        assertTrue(DecoupageMessage.decouper("").let { it.isEmpty() || it == listOf<BlocMessage>(BlocMessage.Texte("")) })
    }

    @Test fun neverCrashesOnOddInput() {
        listOf("```", "``` ``` ```", "=== FICHIER:", "=== FICHIER: ===", "===FIN===", "\n\n```\n\n", "**a.txt**\n```").forEach {
            DecoupageMessage.decouper(it)
        }
    }

    @Test fun randomFragmentsNeverCrashAndNeverLoseTheCode() {
        val morceaux = listOf(
            "```", "```kotlin", "```python title=\"a.py\"", "=== FICHIER: a.txt ===", "=== FIN ===", "=== LIRE: b.txt ===",
            "**index.html**", "### 2. css/style.css", "Voici :", "val x = 1", "<p>x</p>", "", "  ", "// main.js", "`code`", "**gras**"
        )
        val hasard = java.util.Random(42)
        repeat(400) {
            val reponse = (1..hasard.nextInt(14)).joinToString("\n") { morceaux[hasard.nextInt(morceaux.size)] }
            val blocs = DecoupageMessage.decouper(reponse)
            val actions = ActionsFichiers.extraire(reponse)
            assertTrue(actions.count { it is ActionFichier.Ecrire } <= ActionsFichiers.MAX_ECRITURES)
            // le code d'une ligne de code n'est jamais perdu a l'affichage
            if (reponse.contains("val x = 1")) {
                assertTrue(reponse, blocs.any { b ->
                    when (b) {
                        is BlocMessage.Texte -> b.texte.contains("val x = 1")
                        is BlocMessage.Code -> b.corps.contains("val x = 1")
                        is BlocMessage.Reflexion -> false
                    }
                } || reponse.lines().any { it.trim().startsWith("=== FICHIER") })
            }
        }
    }

    @Test fun fourBacktickBlockKeepsItsInnerFences() {
        val blocs = DecoupageMessage.decouper("**LISEZMOI.md**\n````markdown\n# T\n```\ncode\n```\nFin\n````\nVoilà.")
        val c = blocs.filterIsInstance<BlocMessage.Code>().single()
        assertEquals("LISEZMOI.md", c.titre)
        assertTrue(c.ferme)
        assertTrue(c.corps.contains("code"))
        assertTrue(c.corps.endsWith("Fin"))
        assertEquals(BlocMessage.Texte("Voilà."), blocs.last())
    }

    @Test fun commentTitleAfterBlankLinesGivesTheTitle() {
        val c = codes("```html\n\n<!-- index.html -->\n<p>x</p>\n```").single()
        assertEquals("index.html", c.titre)
    }
}
