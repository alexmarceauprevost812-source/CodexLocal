package com.codexlocal.projet

import com.codexlocal.project.FichierProjet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsLangagesTest {

    private fun f(nom: String, taille: Long) = FichierProjet(nom, nom, "/p/$nom", taille, true)

    @Test fun partsFollowFileSizeAndAreSortedBiggestFirst() {
        val parts = StatsLangages.calculer(listOf(f("a.py", 600), f("b.py", 200), f("c.html", 200)))
        assertEquals(listOf("Python", "HTML"), parts.map { it.nom })
        assertEquals(80, parts[0].pourcent)
        assertEquals(20, parts[1].pourcent)
        assertEquals(1f, parts.sumOf { it.fraction.toDouble() }.toFloat(), 0.001f)
    }

    @Test fun unknownAndPlainTextFilesAreIgnored() {
        val parts = StatsLangages.calculer(listOf(f("notes.txt", 500), f("x.zzz", 500), f("m.js", 10)))
        assertEquals(listOf("JavaScript"), parts.map { it.nom })
    }

    @Test fun emptyOrIgnoredProjectGivesNoBar() {
        assertTrue(StatsLangages.calculer(emptyList()).isEmpty())
        assertTrue(StatsLangages.calculer(listOf(f("notes.txt", 5))).isEmpty())
    }

    @Test fun tinyShareIsShownAsAtLeastOnePercent() {
        val parts = StatsLangages.calculer(listOf(f("a.py", 100_000), f("b.js", 1)))
        assertEquals(1, parts.last().pourcent)
    }

    @Test fun emptyFilesStillCount() {
        val parts = StatsLangages.calculer(listOf(f("a.py", 0), f("b.js", 0)))
        assertEquals(2, parts.size)
        assertEquals(50, parts[0].pourcent)
    }
}
