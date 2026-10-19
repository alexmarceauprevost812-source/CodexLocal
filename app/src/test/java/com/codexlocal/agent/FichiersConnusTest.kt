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
        connus.ajouter(mapOf("index.html" to vu("a"), "css/style.css" to vu("b")), 0)
        connus.ajouter(mapOf("index.html" to vu("a2")), 0)
        assertEquals(mapOf("index.html" to vu("a2"), "css/style.css" to vu("b")), connus.instantane())
    }

    @Test fun snapshotIsACopy() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")), 0)
        val photo = connus.instantane()
        connus.ajouter(mapOf("b.txt" to vu("b")), 0)
        assertEquals(setOf("a.txt"), photo.keys)
    }

    @Test fun sameConversationAndProjectKeepTheList() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")), 0)
        connus.pour("s1|p1")
        assertEquals(setOf("a.txt"), connus.instantane().keys)
    }

    @Test fun anotherProjectOrConversationStartsOver() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")), 0)
        connus.pour("s1|p2")
        assertTrue(connus.instantane().isEmpty())
        connus.ajouter(mapOf("b.txt" to vu("b")), 0)
        connus.pour("s2|p2")
        assertTrue(connus.instantane().isEmpty())
    }

    @Test fun forgettingEmptiesTheListButKeepsWorking() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("a")), 0)
        connus.oublier()
        assertTrue(connus.instantane().isEmpty())
        connus.pour("s1|p1")
        connus.ajouter(mapOf("c.txt" to vu("c")), 0)
        assertEquals(setOf("c.txt"), connus.instantane().keys)
    }

    @Test fun filesFromMessagesTheAiNoLongerRemembersAreForgotten() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("vieux.txt" to vu("v")), 2)
        connus.ajouter(mapOf("recent.txt" to vu("r")), 10)
        connus.oublierAvant(2)
        assertEquals(setOf("vieux.txt", "recent.txt"), connus.instantane().keys)
        connus.oublierAvant(3)
        assertEquals(setOf("recent.txt"), connus.instantane().keys)
        connus.oublierAvant(11)
        assertTrue(connus.instantane().isEmpty())
    }

    @Test fun learningAFileAgainRefreshesItsMessageAndFingerprint() {
        val connus = FichiersConnus()
        connus.pour("s1|p1")
        connus.ajouter(mapOf("a.txt" to vu("v1")), 2)
        connus.ajouter(mapOf("a.txt" to vu("v2")), 12)
        connus.oublierAvant(5)
        assertEquals(mapOf("a.txt" to vu("v2")), connus.instantane())
    }

    @Test fun fingerprintChangesWithTheSlightestEdit() {
        assertEquals(vu("<h1>a</h1>\n"), vu("<h1>a</h1>\n"))
        assertNotEquals(vu("<h1>a</h1>\n"), vu("<h1>b</h1>\n"))
        assertNotEquals(vu("a\n"), vu("a"))
        assertNotEquals(vu("é"), vu("e"))
        assertEquals(64, vu("").length)
    }
}
