package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActionsFichiersTest {

    private fun ecritures(reponse: String) =
        ActionsFichiers.extraire(reponse).filterIsInstance<ActionFichier.Ecrire>()

    @Test fun readsFilesClosedByFin() {
        val reponse = """
            Voici le projet.
            === FICHIER: index.html ===
            <h1>Salut</h1>
            === FIN ===
            Et le style :
            === FICHIER: css/style.css ===
            body { color: red; }
            === FIN ===
            Voilà !
        """.trimIndent()
        val liste = ecritures(reponse)
        assertEquals(2, liste.size)
        assertEquals("index.html", liste[0].chemin)
        assertEquals("<h1>Salut</h1>\n", liste[0].contenu)
        assertTrue(liste[0].complet)
        assertEquals("css/style.css", liste[1].chemin)
        assertEquals("body { color: red; }\n", liste[1].contenu)
    }

    @Test fun textAfterFinIsNotPartOfTheFile() {
        val liste = ecritures("=== FICHIER: a.txt ===\nbonjour\n=== FIN ===\nEt voilà, c'est fini.")
        assertEquals("bonjour\n", liste.single().contenu)
    }

    @Test fun nextMarkerClosesTheFileWithoutFin() {
        val liste = ecritures("=== FICHIER: a.txt ===\nun\n=== FICHIER: b.txt ===\ndeux\n=== FIN ===")
        assertEquals(listOf("a.txt", "b.txt"), liste.map { it.chemin })
        assertTrue(liste.all { it.complet })
    }

    @Test fun missingFinOnLastFileIsMarkedIncomplete() {
        val liste = ecritures("=== FICHIER: a.txt ===\nun\n=== FIN ===\n=== FICHIER: b.txt ===\ncoupé en plein")
        assertTrue(liste[0].complet)
        assertFalse(liste[1].complet)
        assertEquals("coupé en plein\n", liste[1].contenu)
    }

    @Test fun stripsCodeFenceAndIgnoresTextAfterIt() {
        val reponse = "=== FICHIER: main.py ===\n```python\nprint('x')\n```\nVoilà le fichier.\n=== FIN ==="
        assertEquals("print('x')\n", ecritures(reponse).single().contenu)
    }

    @Test fun keepsInnerFencesWhenOnlyTheOuterOneWraps() {
        val reponse = "=== FICHIER: LISEZMOI.md ===\n```\n# Titre\n```js\nx\n```\n```\n=== FIN ==="
        val contenu = ecritures(reponse).single().contenu
        assertTrue(contenu.contains("```js"))
        assertTrue(contenu.startsWith("# Titre"))
    }

    @Test fun markersAreTolerantAboutCaseSpacesAndDecoration() {
        val liste = ecritures(
            "==  fichier :  \"src/app.js\"  ==\nlet a = 1;\n=== fin ===\n" +
                "=== FICHIER: **notes.md** ===\ntexte\n=== FIN ==="
        )
        assertEquals(listOf("src/app.js", "notes.md"), liste.map { it.chemin })
    }

    @Test fun readRequestsAreCollectedOnceAndCapped() {
        val reponse = (1..8).joinToString("\n") { "=== LIRE: f$it.txt ===" } + "\n=== LIRE: f1.txt ==="
        val lectures = ActionsFichiers.extraire(reponse).filterIsInstance<ActionFichier.Lire>()
        assertEquals(ActionsFichiers.MAX_LECTURES, lectures.size)
        assertEquals(lectures.map { it.chemin }.distinct(), lectures.map { it.chemin })
    }

    @Test fun readClosesTheFileBeingRead() {
        val actions = ActionsFichiers.extraire("=== FICHIER: a.txt ===\nun\n=== LIRE: b.txt ===\n")
        assertEquals(ActionFichier.Ecrire("a.txt", "un\n", true), actions[0])
        assertEquals(ActionFichier.Lire("b.txt"), actions[1])
    }

    @Test fun plainAnswersProduceNoAction() {
        assertTrue(ActionsFichiers.extraire("Une explication.\n```kotlin\nval a = 1\n```").isEmpty())
        assertTrue(ActionsFichiers.extraire("").isEmpty())
    }

    @Test fun capsTheNumberOfFiles() {
        val reponse = (1..40).joinToString("\n") { "=== FICHIER: f$it.txt ===\nx\n=== FIN ===" }
        assertEquals(ActionsFichiers.MAX_ECRITURES, ecritures(reponse).size)
    }

    @Test fun emptyFileContentIsKeptEmpty() {
        assertEquals("", ecritures("=== FICHIER: vide.txt ===\n\n=== FIN ===").single().contenu)
    }

    // --- reponses sans marqueurs : blocs de code avec un nom de fichier ---

    private fun paires(reponse: String) = ecritures(reponse).map { it.chemin to it.contenu }

    @Test fun codeBlockWithTitleLineBeforeIsAFile() {
        val reponse = "Voici ton site.\n\n**index.html**\n```html\n<h1>Salut</h1>\n```\n\n**style.css**\n```css\nbody { margin: 0; }\n```\nVoilà !"
        assertEquals(
            listOf("index.html" to "<h1>Salut</h1>\n", "style.css" to "body { margin: 0; }\n"),
            paires(reponse)
        )
    }

    @Test fun codeBlockWithNameInTheOpeningLine() {
        val reponse = "```python title=\"main.py\"\nprint(1)\n```\n```js:app.js\nlet a = 1\n```"
        assertEquals(listOf("main.py" to "print(1)\n", "app.js" to "let a = 1\n"), paires(reponse))
    }

    @Test fun codeBlockWithNameAsFirstLineComment() {
        val reponse = "```html\n<!-- index.html -->\n<p>x</p>\n```"
        val e = ecritures(reponse).single()
        assertEquals("index.html", e.chemin)
        assertTrue(e.contenu.contains("<p>x</p>"))
    }

    @Test fun announcingSentenceBeforeTheBlock() {
        val reponse = "Crée le fichier `src/main.py` :\n\n```python\nprint('a')\n```"
        assertEquals(listOf("src/main.py" to "print('a')\n"), paires(reponse))
    }

    @Test fun markersWinOverCodeBlocks() {
        val reponse = "=== FICHIER: a.txt ===\nun\n=== FIN ===\n**b.txt**\n```\ndeux\n```"
        assertEquals(listOf("a.txt"), ecritures(reponse).map { it.chemin })
    }

    @Test fun codeBlockWithoutNameIsNotWritten() {
        val reponse = "Voici :\n```bash\nnpm install\n```\nEt aussi :\n```kotlin\nval a = 1\n```"
        assertTrue(ActionsFichiers.extraire(reponse).isEmpty())
    }

    @Test fun bigCodeBlockWithoutNameIsReported() {
        val reponse = "Voici le code :\n```python\na = 1\nb = 2\nc = 3\nprint(a + b + c)\n```"
        val action = ActionsFichiers.extraire(reponse).single()
        val sansNom = action as ActionFichier.SansNom
        assertEquals("python", sansNom.langage)
        assertEquals(4, sansNom.lignes)
    }

    @Test fun unclosedCodeBlockIsIncomplete() {
        val e = ecritures("**app.js**\n```js\nlet a = 1;\nlet b").single()
        assertEquals("app.js", e.chemin)
        assertFalse(e.complet)
    }

    @Test fun fencesInsideAFileDoNotCloseIt() {
        val reponse = "**LISEZMOI.md**\n```markdown\n# Titre\n\n```bash\nnpm start\n```\n\nFin\n```\nEt voilà."
        val e = ecritures(reponse).single()
        assertEquals("LISEZMOI.md", e.chemin)
        assertTrue(e.contenu.contains("npm start"))
        assertTrue(e.contenu.trimEnd().endsWith("Fin"))
    }

    @Test fun severalFilesAreAllRead() {
        val reponse = (1..5).joinToString("\n\n") { "### $it. f$it.txt\n```\ncontenu $it\n```" }
        assertEquals((1..5).map { "f$it.txt" }, ecritures(reponse).map { it.chemin })
    }

    @Test fun codeBlockWithTitleTwoLinesBefore() {
        val reponse = "**index.html**\n\n```html\n<p>a</p>\n```"
        assertEquals("index.html", ecritures(reponse).single().chemin)
    }

    @Test fun anOldTitleDoesNotLeakIntoALaterBlock() {
        val reponse = "**a.txt**\n```\nun\n```\nPuis lance ça :\n```bash\nls\n```"
        assertEquals(listOf("a.txt"), ecritures(reponse).map { it.chemin })
    }

    @Test fun aFileWrappedInFourBackticksKeepsItsInnerBlocks() {
        val reponse = "**LISEZMOI.md**\n````markdown\n# Titre\n\n```\ncode sans langage\n```\n\nFin\n````\nEt voilà."
        val e = ecritures(reponse).single()
        assertEquals("LISEZMOI.md", e.chemin)
        assertTrue(e.complet)
        assertTrue(e.contenu.contains("code sans langage"))
        assertTrue(e.contenu.trimEnd().endsWith("Fin"))
    }

    @Test fun fourBacktickBlockIsIncompleteUntilItsOwnFence() {
        val e = ecritures("**a.md**\n````md\ndébut\n```\nencore").single()
        assertFalse(e.complet)
        assertTrue(e.contenu.contains("encore"))
    }

    @Test fun commentTitleAfterBlankLinesIsStillTheFileName() {
        val e = ecritures("```html\n\n<!-- index.html -->\n<p>x</p>\n```").single()
        assertEquals("index.html", e.chemin)
    }

    @Test fun longKnownExtensionsAreFileNames() {
        val e = ecritures("**gradle.properties**\n```\norg.gradle.jvmargs=-Xmx2g\n```").single()
        assertEquals("gradle.properties", e.chemin)
    }
}
