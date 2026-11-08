package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BonCodeurTest {

    private val deux = listOf(
        Probleme("index.html", 3, "référence « js/app.js », mais ce fichier n'existe pas"),
        Probleme("app.js", null, "morceau abrégé")
    )

    @Test fun temperatureEstPlafonneeMaisJamaisRelevee() {
        assertEquals(BonCodeur.TEMPERATURE_MAX_CODE, BonCodeur.temperature(0.9f), 0f)
        assertEquals(0.1f, BonCodeur.temperature(0.1f), 0f)
    }

    @Test fun methodeDonneLesReglesEtReutiliseLesMarqueursDeLApp() {
        assertTrue(BonCodeur.METHODE.contains("=== LIRE: chemin ==="))
        assertTrue(BonCodeur.METHODE.contains("COMPLETS"))
        assertTrue(BonCodeur.METHODE.contains("A verifier"))
        // le marqueur doit etre lu par le vrai analyseur de l'application
        val lu = ActionsFichiers.extraire("=== LIRE: src/main.py ===")
        assertEquals(listOf(ActionFichier.Lire("src/main.py")), lu)
        // courte : elle occupe la memoire du modele a chaque conversation
        assertTrue(BonCodeur.METHODE.length < 900)
    }

    @Test fun demandeDeReparationListeTousLesProblemesEtLeFormatAttendu() {
        val d = BonCodeur.demandeReparation(deux)
        assertTrue(d.contains("2 problèmes"))
        assertTrue(d.contains("index.html (ligne 3)"))
        assertTrue(d.contains("app.js : morceau abrégé"))
        assertTrue(d.contains("=== FICHIER:") && d.contains("=== FIN ==="))
        // la correction demandee est lisible par l'analyseur de fichiers de l'application
        val exemple = "=== FICHIER: app.js ===\nlet a = 1;\n=== FIN ==="
        val actions = ActionsFichiers.extraire(exemple)
        assertEquals(ActionFichier.Ecrire("app.js", "let a = 1;\n", true), actions.single())
    }

    @Test fun singulierEtPluriel() {
        assertTrue(BonCodeur.resume(deux.take(1)).contains("1 problème à corriger"))
        assertTrue(BonCodeur.resume(deux).contains("2 problèmes à corriger"))
        assertFalse(BonCodeur.resume(deux.take(1)).contains("problèmes"))
    }

    @Test fun avertissementDitCeQuiResteSansPromettreDeCorrection() {
        val a = BonCodeur.avertissement(deux)
        assertTrue(a.startsWith("⚠️"))
        assertTrue(a.contains("• app.js : morceau abrégé"))
    }
}
