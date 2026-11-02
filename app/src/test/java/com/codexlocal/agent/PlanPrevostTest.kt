package com.codexlocal.agent

import org.junit.Assert.*
import org.junit.Test

class PlanPrevostTest {
    @Test fun lectureNeDemandePasConfirmation() {
        val plan = PlanPrevost("demo", listOf(EtapePrevost(TypeEtapePrevost.LIRE, "src/Main.kt")))
        assertFalse(plan.exigeConfirmation)
        assertTrue(plan.previsualiser().contains("Lecture seule"))
    }

    @Test fun ecritureExigeConfirmation() {
        val plan = PlanPrevost("demo", listOf(EtapePrevost(TypeEtapePrevost.ECRIRE, "src/Main.kt", "ok")))
        assertTrue(plan.exigeConfirmation)
        assertTrue(plan.previsualiser().contains("Confirmation requise"))
    }

    @Test fun cheminsDangereuxSontRefuses() {
        listOf("../secret.txt", "/tmp/a", "src/../a", "C:/Windows/a",
            "models/prive.gguf", "src/a.gguf", ".git/config", "src//a", "src\\a"
        ).forEach { chemin ->
            assertFalse("Devrait refuser : $chemin", cheminRelatifSur(chemin))
        }
    }

    @Test fun planSansContenuEstRefuse() {
        try {
            PlanPrevost("demo", listOf(EtapePrevost(TypeEtapePrevost.ECRIRE, "src/Main.kt")))
            fail("Écriture sans contenu acceptée")
        } catch (_: IllegalArgumentException) { }
    }
}
