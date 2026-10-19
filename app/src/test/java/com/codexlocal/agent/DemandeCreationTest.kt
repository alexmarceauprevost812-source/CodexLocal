package com.codexlocal.agent

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DemandeCreationTest {

    @Test fun creationRequests() {
        listOf(
            "fais-moi un site avec une page d'accueil",
            "Crée une application de notes",
            "peux-tu me faire un jeu de serpent",
            "Écris un script python qui renomme mes fichiers",
            "ajoute un bouton rouge sur la page",
            "Modifie le fichier index.html",
            "corrige l'erreur dans app.js",
            "génère une calculatrice",
            "Mets un fond noir"
        ).forEach { assertTrue(it, DemandeCreation.estUneDemande(it)) }
    }

    @Test fun questionsAreNotRequests() {
        listOf(
            "c'est quoi un site web ?",
            "Comment faire un site en html",
            "pourquoi ce code plante",
            "explique-moi ce que fait cette fonction",
            "Quel est le meilleur langage pour faire un jeu",
            "bonjour",
            "merci beaucoup",
            "",
            "   "
        ).forEach { assertFalse(it, DemandeCreation.estUneDemande(it)) }
    }

    @Test fun accentsAndCurlyApostrophesDoNotMatter() {
        assertTrue(DemandeCreation.estUneDemande("CRÉE un site"))
        assertTrue(DemandeCreation.estUneDemande("Cree un site"))
        assertFalse(DemandeCreation.estUneDemande("C’est quoi un site"))
    }
}
