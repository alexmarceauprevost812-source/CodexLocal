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
}
