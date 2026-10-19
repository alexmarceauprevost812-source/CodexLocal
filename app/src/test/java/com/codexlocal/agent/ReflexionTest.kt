package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflexionTest {

    @Test fun noReasoningMeansNothingToHide() {
        val d = Reflexion.separer("Bonjour\n```kotlin\nval a = 1\n```")
        assertNull(d.reflexion)
        assertFalse(d.enCours)
        assertEquals("Bonjour\n```kotlin\nval a = 1\n```", d.reponse)
    }

    @Test fun reasoningWithBothTags() {
        val d = Reflexion.separer("<think>\nIl faut un site.\n</think>\n\nVoici le site.")
        assertEquals("Il faut un site.", d.reflexion)
        assertFalse(d.enCours)
        assertEquals("Voici le site.", d.reponse)
    }

    @Test fun reasoningWithOnlyTheClosingTag() {
        // le modele de conversation a deja ecrit <think> : seule la fermeture apparait
        val d = Reflexion.separer("Il faut un site.\nFaisons-le simple.\n</think>\n\nVoici.")
        assertEquals("Il faut un site.\nFaisons-le simple.", d.reflexion)
        assertEquals("Voici.", d.reponse)
    }

    @Test fun reasoningStillGoingOn() {
        val d = Reflexion.separer("<think>\nOn pourrait faire")
        assertEquals("On pourrait faire", d.reflexion)
        assertTrue(d.enCours)
        assertEquals("", d.reponse)
    }

    @Test fun anOpeningTagInTheMiddleOfAnAnswerIsNotReasoning() {
        val d = Reflexion.separer("Voici du HTML avec <think> dedans")
        assertNull(d.reflexion)
    }

    @Test fun countsWords() {
        assertEquals(4, Reflexion.mots("un  deux\ntrois quatre"))
        assertEquals(0, Reflexion.mots("  "))
    }

    @Test fun maskingKeepsTheNumberOfLines() {
        val texte = "<think>\nligne 1\nligne 2\n</think>\n```python\nprint(1)\n```"
        val masque = Reflexion.masquer(texte)
        assertEquals(texte.lines().size, masque.lines().size)
        assertEquals(texte.lines().indexOf("```python"), masque.lines().indexOf("```python"))
        assertFalse(masque.contains("ligne 1"))
    }

    @Test fun maskingAnUnfinishedReasoningLeavesNothing() {
        val masque = Reflexion.masquer("<think>\nje cherche\n=== FICHIER: a.txt ===\nx")
        assertTrue(masque.isBlank())
        assertEquals(3, masque.count { it == '\n' })
    }

    @Test fun fileMarkersInsideTheReasoningCreateNothing() {
        val texte = "<think>\nPeut-etre === FICHIER: faux.txt ===\nbof\n=== FIN ===\n</think>\n" +
            "=== FICHIER: vrai.txt ===\nok\n=== FIN ==="
        val ecritures = ActionsFichiers.extraire(texte).filterIsInstance<ActionFichier.Ecrire>()
        assertEquals(listOf("vrai.txt"), ecritures.map { it.chemin })
    }

    @Test fun anUnfinishedReasoningCreatesNothing() {
        assertTrue(ActionsFichiers.extraire("<think>\n=== FICHIER: a.txt ===\nx\n=== FIN ===").isEmpty())
    }

    @Test fun titlesStillGoBeforeTheRightBlockAfterAReasoning() {
        val reponse = "<think>\nje reflechis\nencore\n</think>\nVoila :\n" +
            "```python\na = 1\nb = 2\nc = 3\nprint(a + b + c)\n```"
        val bloc = ActionsFichiers.extraire(reponse).filterIsInstance<ActionFichier.SansNom>().single()
        val titree = ActionsFichiers.avecTitres(reponse, mapOf(bloc.ligneOuverture to "calcul.py"))
        val ecriture = ActionsFichiers.extraire(titree).filterIsInstance<ActionFichier.Ecrire>().single()
        assertEquals("calcul.py", ecriture.chemin)
        assertTrue(titree.contains("**calcul.py**\n```python"))
    }

    @Test fun theChatShowsOneGreyLineInsteadOfTheReasoning() {
        val blocs = DecoupageMessage.decouper("<think>\nun deux trois\n</think>\nVoici le texte.")
        assertEquals(BlocMessage.Reflexion(3, false), blocs[0])
        assertEquals(BlocMessage.Texte("Voici le texte."), blocs[1])
        assertEquals(2, blocs.size)
    }

    @Test fun anUnfinishedReasoningIsShownAsInProgress() {
        val blocs = DecoupageMessage.decouper("<think>\nje cherche")
        assertEquals(listOf<BlocMessage>(BlocMessage.Reflexion(2, true)), blocs)
    }
}
