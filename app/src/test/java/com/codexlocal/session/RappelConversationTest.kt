package com.codexlocal.session

import com.codexlocal.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RappelConversationTest {

    private fun echange(n: Int, taille: Int = 20): List<Message> = (1..n).flatMap {
        listOf(
            Message.user("question $it " + "x".repeat(taille)),
            Message.assistant("reponse $it " + "y".repeat(taille))
        )
    }

    @Test fun nothingToRecallBeforeTheFirstMessage() {
        assertEquals("", RappelConversation.construire(emptyList()))
    }

    @Test fun recallListsEarlierMessagesInOrderBetweenMarkers() {
        val texte = RappelConversation.construire(echange(2))
        assertTrue(texte.startsWith(RappelConversation.DEBUT))
        assertTrue(texte.endsWith(RappelConversation.FIN))
        val q1 = texte.indexOf("Utilisateur : question 1")
        val r1 = texte.indexOf("Assistant : reponse 1")
        val q2 = texte.indexOf("Utilisateur : question 2")
        val r2 = texte.indexOf("Assistant : reponse 2")
        assertTrue(q1 in 0 until r1 && r1 < q2 && q2 < r2)
    }

    @Test fun mostRecentMessagesWinWhenTheBudgetIsShort() {
        val texte = RappelConversation.construire(echange(40, taille = 100), budget = 600)
        assertTrue(texte.contains("reponse 40"))
        assertTrue(texte.contains("question 40"))
        assertFalse(texte.contains("question 1 "))
        // le budget concerne les messages : le rappel ne depasse jamais de beaucoup
        assertTrue(texte.length < 600 + RappelConversation.DEBUT.length + RappelConversation.FIN.length + 200)
    }

    @Test fun theMostRecentMessageIsAlwaysKeptEvenIfItAloneExceedsTheBudget() {
        val texte = RappelConversation.construire(listOf(Message.assistant("z".repeat(5_000))), budget = 100)
        assertTrue(texte.contains("Assistant : zzzz"))
    }

    @Test fun longMessagesAreShortenedKeepingBothEnds() {
        val long = "DEBUT" + "m".repeat(5_000) + "FIN"
        val texte = RappelConversation.construire(listOf(Message.user(long)))
        assertTrue(texte.contains("DEBUT"))
        assertTrue(texte.contains("FIN"))
        assertTrue(texte.contains("[…]"))
        assertTrue(texte.length < 1_500)
    }

    @Test fun shorteningLeavesShortTextAloneAndRespectsTheLimit() {
        assertEquals("court", RappelConversation.raccourcir("court", 50))
        val coupe = RappelConversation.raccourcir("a".repeat(1_000), 100)
        assertEquals(100, coupe.length)
    }

    @Test fun blankAndErrorMessagesAreSkipped() {
        val texte = RappelConversation.construire(
            listOf(
                Message.user("vraie question"),
                Message.assistant("[erreur] panne"),
                Message.assistant("   "),
                Message.assistant("vraie reponse")
            )
        )
        assertTrue(texte.contains("vraie question"))
        assertTrue(texte.contains("vraie reponse"))
        assertFalse(texte.contains("panne"))
    }

    @Test fun lineBreaksAreFlattenedSoEachMessageIsOneLine() {
        val texte = RappelConversation.construire(listOf(Message.assistant("ligne 1\n\n  ligne 2\n```kotlin\nfun a() {}\n```")))
        val lignes = texte.lines()
        assertEquals(3, lignes.size)
        assertTrue(lignes[1].startsWith("Assistant : ligne 1 ligne 2"))
    }

    @Test fun onlyNothingUsefulGivesNoRecallAtAll() {
        assertEquals("", RappelConversation.construire(listOf(Message.assistant(""), Message.assistant("[erreur] x"))))
    }
}
