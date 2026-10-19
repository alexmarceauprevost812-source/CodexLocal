package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FichiersConnusTest {

    private fun vu(texte: String) = FichiersConnus.empreinte(texte)

    @Test fun startsEmptyAndRemembersWhatWasAdded() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        assertTrue(connus.instantane().isEmpty())
        connus.ajouter(mapOf("index.html" to vu("a"), "css/style.css" to vu("b")))
        connus.ajouter(mapOf("index.html" to vu("a2")))
        assertEquals(mapOf("index.html" to vu("a2"), "css/style.css" to vu("b")), connus.instantane())
    }

    @Test fun snapshotIsACopy() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")))
        val photo = connus.instantane()
        connus.ajouter(mapOf("b.txt" to vu("b")))
        assertEquals(setOf("a.txt"), photo.keys)
    }

    @Test fun sameConversationAndProjectKeepTheList() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")))
        connus.pour("s1|p1")
        assertEquals(setOf("a.txt"), connus.instantane().keys)
    }

    @Test fun anotherProjectOrConversationStartsOver() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")))
        connus.pour("s1|p2")
        assertTrue(connus.instantane().isEmpty())
        connus.ajouter(mapOf("b.txt" to vu("b")))
        connus.pour("s2|p2")
        assertTrue(connus.instantane().isEmpty())
    }

    @Test fun forgettingEmptiesTheListButKeepsWorking() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")))
        connus.oublier()
        assertTrue(connus.instantane().isEmpty())
        connus.pour("s1|p1")
        connus.ajouter(mapOf("c.txt" to vu("c")))
        assertEquals(setOf("c.txt"), connus.instantane().keys)
    }

    @Test fun fingerprintChangesWithTheSlightestEdit() {
        assertEquals(vu("<h1>a</h1>\n"), vu("<h1>a</h1>\n"))
        assertNotEquals(vu("<h1>a</h1>\n"), vu("<h1>b</h1>\n"))
        assertNotEquals(vu("a\n"), vu("a"))
        assertNotEquals(vu("é"), vu("e"))
        assertEquals(64, vu("").length)
    }
}
