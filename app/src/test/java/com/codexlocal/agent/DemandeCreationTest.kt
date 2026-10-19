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
            "Mets un fond noir",
            "salut, fais un site pour mon chien",
            "ok change la couleur du titre",
            "J'aimerais ajouter un menu",
            "j'ai besoin d'ajouter une page",
            "il faut corriger le bouton",
            "peux tu m'ajouter une page contact",
            "Fais moi 3 fichiers: html, css et js",
            "Programme un jeu de morpion",
            "Mets-moi un bouton bleu",
            "Change la couleur du fond"
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
            "Mon programme fait quoi ?",
            "Fais-moi voir le contenu de README.md",
            "fais voir le fichier index.html",
            "Fais-moi savoir ce que fait ce code",
            "peux-tu me faire voir le css",
            "Programme TV de ce soir ?",
            "Mets préférés en France ?",
            "Mets et vins du Québec",
            "Change du jour en euros ?",
            "Programme du jour",
            "Je voudrais savoir comment ce programme marche",
            "ce programme fait des erreurs",
            "Fais-tu des erreurs ?",
            "il fait beau et le programme est long",
            "Est-ce que le site marche",
            "je veux savoir si ca va changer quelque chose",
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
