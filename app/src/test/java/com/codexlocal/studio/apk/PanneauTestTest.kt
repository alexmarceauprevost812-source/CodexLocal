package com.codexlocal.studio.apk

import com.codexlocal.project.ProjectStore.EtatFichier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PanneauTestTest {

    private fun etat(taille: Long, date: Long = 1) = EtatFichier(taille, date)

    @Test fun describesAddedChangedAndRemovedFiles() {
        val avant = mapOf("index.html" to etat(10), "style.css" to etat(5), "vieux.js" to etat(3))
        val apres = mapOf("index.html" to etat(12), "style.css" to etat(5), "neuf.js" to etat(7))
        assertEquals(
            listOf("✏️ index.html", "➕ neuf.js", "➖ vieux.js"),
            PanneauTest.decrireChangements(avant, apres)
        )
    }

    @Test fun aNewModificationDateAloneCountsAsAChange() {
        assertEquals(
            listOf("✏️ a.txt"),
            PanneauTest.decrireChangements(mapOf("a.txt" to etat(4, 1)), mapOf("a.txt" to etat(4, 2)))
        )
    }

    @Test fun nothingChangedMeansNoLines() {
        val photo = mapOf("a" to etat(1), "b" to etat(2))
        assertTrue(PanneauTest.decrireChangements(photo, photo.toMap()).isEmpty())
        assertTrue(PanneauTest.decrireChangements(emptyMap(), emptyMap()).isEmpty())
    }
}
