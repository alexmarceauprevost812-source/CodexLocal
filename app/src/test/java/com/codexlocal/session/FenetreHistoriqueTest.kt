package com.codexlocal.session

import com.codexlocal.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class FenetreHistoriqueTest {

    private fun echange(n: Int, taille: Int): List<Message> = (1..n).flatMap {
        listOf(Message.user("q$it".padEnd(taille, '.')), Message.assistant("r$it".padEnd(taille, '.')))
    }

    @Test fun shortConversationsAreSentWhole() {
        val messages = echange(5, 100) + Message.user("encore")
        assertSame(messages, FenetreHistorique.fenetrer(messages))
    }

    @Test fun longConversationsKeepTheMostRecentMessagesThatFit() {
        val messages = echange(200, 1_000) + Message.user("la fin")
        val fenetre = FenetreHistorique.fenetrer(messages, maxCaracteres = 10_000)
        assertEquals("la fin", fenetre.last().content)
        assertTrue(fenetre.sumOf { it.content.length } <= 10_000)
        assertTrue(fenetre.size in 8..11)
        // ce sont bien les derniers, dans l'ordre
        assertEquals(messages.takeLast(fenetre.size).map { it.content }, fenetre.map { it.content })
    }

    @Test fun theWindowAlwaysStartsWithTheUser() {
        val messages = echange(200, 1_000) + Message.user("la fin")
        repeat(30) { i ->
            val fenetre = FenetreHistorique.fenetrer(messages, maxCaracteres = 5_000 + i * 337)
            assertTrue("doit commencer par l'utilisateur", fenetre.first().isUser)
        }
    }

    @Test fun theLastMessageAlwaysGoesEvenWhenHuge() {
        val messages = listOf(Message.user("avant"), Message.assistant("ok"), Message.user("g".repeat(50_000)))
        val fenetre = FenetreHistorique.fenetrer(messages, maxCaracteres = 1_000)
        assertEquals(1, fenetre.size)
        assertEquals(50_000, fenetre[0].content.length)
    }

    @Test fun emptyAndSingleMessageListsPassThrough() {
        assertEquals(emptyList<Message>(), FenetreHistorique.fenetrer(emptyList()))
        val seul = listOf(Message.user("a"))
        assertSame(seul, FenetreHistorique.fenetrer(seul))
    }

    @Test fun theDefaultBudgetLeavesRoomForLongDiscussions() {
        val messages = echange(100, 500) + Message.user("fin")   // ~100 000 caracteres
        assertEquals(messages.size, FenetreHistorique.fenetrer(messages).size)
    }
}
