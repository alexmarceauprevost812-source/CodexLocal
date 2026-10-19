package com.codexlocal.agent

import com.codexlocal.agent.ProtectionFichiers.Candidat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtectionFichiersTest {

    @Test fun aMuchShorterVersionOfABigFileIsSuspicious() {
        val ancien = "a".repeat(1_000)
        assertTrue(ProtectionFichiers.reduitTrop(ancien, "a".repeat(300)))
        assertTrue(ProtectionFichiers.reduitTrop(ancien, "a".repeat(399)))
        assertFalse("40 % pile : on laisse passer", ProtectionFichiers.reduitTrop(ancien, "a".repeat(400)))
        assertFalse(ProtectionFichiers.reduitTrop(ancien, "a".repeat(900)))
        assertFalse(ProtectionFichiers.reduitTrop(ancien, "a".repeat(2_000)))
    }

    @Test fun smallFilesMayShrinkFreely() {
        assertFalse(ProtectionFichiers.reduitTrop("a".repeat(500), "a"))
        assertFalse(ProtectionFichiers.reduitTrop("a".repeat(ProtectionFichiers.TAILLE_MIN_REDUCTION - 1), "a"))
        assertTrue(ProtectionFichiers.reduitTrop("a".repeat(ProtectionFichiers.TAILLE_MIN_REDUCTION), "a"))
    }

    @Test fun requestsThatWantToRemoveOrRedoEverything() {
        listOf(
            "supprime le menu", "Enlève la section contact", "simplifie la page", "Recommence à zéro",
            "refais tout", "Efface le footer", "vide le fichier", "réduis la taille du texte",
            "retire le titre", "réécris tout en plus court", "remplace tout par une version simple",
            "nettoie le code", "repars de zero", "fais le jeu from scratch"
        ).forEach { assertTrue(it, ProtectionFichiers.reductionVoulue(it)) }
    }

    @Test fun ordinaryRequestsDoNotAllowShrinking() {
        listOf(
            "ajoute un bouton rouge", "change la couleur du titre", "fais un jeu avec des vidéos",
            "Refais le menu", "ajoute une page contact", "corrige le bug du score",
            "mets un fond noir", "écris la commande que je tape"
        ).forEach { assertFalse(it, ProtectionFichiers.reductionVoulue(it)) }
    }

    private val fichiers = listOf(
        Candidat("js/app.js", 3_000),
        Candidat("index.html", 5_000),
        Candidat("css/style.css", 800),
        Candidat("notes.txt", 100),
        Candidat("data.json", 400)
    )

    @Test fun filesNamedInTheRequestComeFirstThenIndexThenTheSmallest() {
        val ordre = ProtectionFichiers.ordreApercu(fichiers, "change la couleur dans style.css").map { it.chemin }
        assertEquals(listOf("css/style.css", "index.html", "notes.txt", "data.json", "js/app.js"), ordre)
    }

    @Test fun withoutAnyNameIndexComesFirst() {
        val ordre = ProtectionFichiers.ordreApercu(fichiers, "ajoute un bouton").map { it.chemin }
        assertEquals(listOf("index.html", "notes.txt", "data.json", "css/style.css", "js/app.js"), ordre)
    }

    @Test fun aFullPathInTheRequestIsRecognisedToo() {
        val ordre = ProtectionFichiers.ordreApercu(fichiers, "corrige js/app.js").map { it.chemin }
        assertEquals("js/app.js", ordre.first())
    }

    @Test fun veryShortNamesAreNotMatchedByAccident() {
        val ordre = ProtectionFichiers.ordreApercu(listOf(Candidat("a", 10), Candidat("b.js", 20)), "ajoute un bouton")
        // « a » est dans « ajoute » mais un nom de moins de 3 lettres ne compte pas
        assertEquals(listOf("a", "b.js"), ordre.map { it.chemin })
        assertEquals("b.js", ProtectionFichiers.ordreApercu(
            listOf(Candidat("a", 10), Candidat("b.js", 20)), "corrige b.js"
        ).first().chemin)
    }

    @Test fun previewIsEmptyWhenNothingIsShown() {
        assertTrue(ApercuFichiers.VIDE.texte.isEmpty() && ApercuFichiers.VIDE.connus.isEmpty())
    }
}
