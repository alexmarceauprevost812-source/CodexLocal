package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BilanTest {

    private fun lecture(chemin: String, texte: String = "contenu de $chemin\n", tronque: Boolean = false) =
        LectureFichier(chemin, texte, null, tronque)

    @Test fun summaryNamesProtectedFilesAndSaysNothingWasReplaced() {
        val bilan = Bilan(crees = listOf("b.txt"), proteges = listOf("index.html", "css/style.css"))
        val resume = bilan.resume()
        assertTrue(resume.contains("📁 Créé : b.txt"))
        assertTrue(resume.contains("🔒 Protégé : index.html, css/style.css"))
        assertTrue(resume.contains("pas remplacé"))
    }

    @Test fun nothingProtectedMeansNoLockLine() {
        assertFalse(Bilan(crees = listOf("a.txt")).resume().contains("🔒"))
        assertEquals("", Bilan().resume())
    }

    @Test fun plainReadsKeepTheUsualMessage() {
        val bilan = Bilan(lectures = listOf(lecture("a.txt")))
        val texte = bilan.texteLectures()
        assertTrue(texte.startsWith("Voici le contenu des fichiers demandés."))
        assertTrue(texte.contains("--- Fichier a.txt ---\ncontenu de a.txt"))
        assertTrue(texte.endsWith("Continue ta tâche avec ces fichiers."))
        assertFalse(texte.contains("STOP"))
    }

    @Test fun protectedReadExplainsWhatHappenedAndHowToGoOn() {
        val bilan = Bilan(lectures = listOf(lecture("index.html")), proteges = listOf("index.html"))
        val texte = bilan.texteLectures()
        assertTrue(texte.startsWith("STOP : index.html existe déjà"))
        assertTrue(texte.contains("n'a PAS été remplacé"))
        assertTrue(texte.contains("--- Fichier index.html ---\ncontenu de index.html"))
        assertTrue(texte.contains("EN ENTIER"))
        assertTrue(texte.contains("N'écris que les fichiers qui changent"))
        assertFalse(texte.contains("Continue ta tâche"))
    }

    @Test fun severalProtectedFilesUseThePlural() {
        val bilan = Bilan(
            lectures = listOf(lecture("a.html"), lecture("b.css")),
            proteges = listOf("a.html", "b.css")
        )
        val texte = bilan.texteLectures()
        assertTrue(texte.contains("STOP : a.html, b.css existent déjà"))
        assertTrue(texte.contains("n'ont PAS été remplacés"))
    }

    @Test fun onlyProtectedFilesAreNamedInTheStopLine() {
        val bilan = Bilan(
            lectures = listOf(lecture("lu.txt"), lecture("garde.txt")),
            proteges = listOf("garde.txt")
        )
        val texte = bilan.texteLectures()
        assertTrue(texte.startsWith("STOP : garde.txt existe déjà"))
        assertTrue(texte.contains("--- Fichier lu.txt ---"))
    }

    @Test fun aTooLongFileIsNeverSeenInFullAndTheAiIsToldToUseANewFile() {
        val gros = lecture("gros.js", "début…\n… (fichier tronqué)", tronque = true)
        val petit = lecture("petit.js")
        assertFalse(gros.vuEnEntier)
        assertTrue(petit.vuEnEntier)
        val bilan = Bilan(lectures = listOf(gros, petit), proteges = listOf("gros.js"))
        assertEquals(mapOf("petit.js" to FichiersConnus.empreinte("contenu de petit.js\n")), bilan.lusEnEntier)
        assertTrue(bilan.texteLectures().contains("NOUVEAU fichier"))
    }

    @Test fun aFileThatCouldNotBeReadIsNotKnown() {
        val absent = LectureFichier("absent.txt", null, "fichier introuvable")
        assertFalse(absent.vuEnEntier)
        val bilan = Bilan(lectures = listOf(absent))
        assertTrue(bilan.lusEnEntier.isEmpty())
        assertTrue(bilan.texteLectures().contains("(fichier introuvable)"))
    }

    @Test fun aFullyReadFileCarriesTheFingerprintOfWhatTheAiSees() {
        val l = lecture("a.txt", "bonjour\n")
        assertEquals(FichiersConnus.empreinte("bonjour\n"), l.empreinte)
        assertEquals(null, lecture("gros.txt", "debut", tronque = true).empreinte)
        assertEquals(null, LectureFichier("x", null, "fichier introuvable").empreinte)
    }

    @Test fun addingBilansKeepsProtectedFiles() {
        val somme = Bilan(proteges = listOf("a.txt"), ecrits = mapOf("x" to "1")) +
            Bilan(proteges = listOf("b.txt"), crees = listOf("c.txt"), ecrits = mapOf("y" to "2"))
        assertEquals(listOf("a.txt", "b.txt"), somme.proteges)
        assertEquals(listOf("c.txt"), somme.crees)
        assertEquals(mapOf("x" to "1", "y" to "2"), somme.ecrits)
    }
}
