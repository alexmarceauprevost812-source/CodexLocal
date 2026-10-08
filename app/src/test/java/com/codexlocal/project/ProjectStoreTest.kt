package com.codexlocal.project

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vérifie les chemins acceptés pour un nouveau fichier sur Android. */
class ProjectStoreTest {

    @Test fun nouveauFichierPython() {
        assertEquals(listOf("main.py"), ProjectStore.validerCheminRelatif("main.py"))
        assertTrue(Langages.estTexte("main.py"))
    }

    @Test fun sousDossierDuProjet() {
        assertEquals(listOf("src", "outil.py"), ProjectStore.validerCheminRelatif("src/outil.py"))
    }

    @Test fun refuseTraversalEtCheminAbsolu() {
        listOf("../private.txt", "src/../config.py", "/tmp/outil.py",
            "C:\\Windows\\secret.txt", "a//b.py", ".secret", "src/.env",
            "test\u0000.py").forEach { chemin ->
            try {
                ProjectStore.validerCheminRelatif(chemin)
                throw AssertionError("Chemin dangereux autorisé : $chemin")
            } catch (_: IllegalArgumentException) {
                // Correct : aucun chemin ne doit sortir du projet.
            }
        }
    }

    @Test fun refuseLongNom() {
        val tropLong = "x".repeat(100) + ".py"
        try {
            ProjectStore.validerCheminRelatif(tropLong)
            throw AssertionError("Le nom trop long n'a pas été rejeté")
        } catch (_: IllegalArgumentException) {
            // Correct.
        }
    }
}
