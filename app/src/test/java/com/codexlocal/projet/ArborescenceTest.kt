package com.codexlocal.projet

import com.codexlocal.project.FichierProjet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArborescenceTest {

    private fun f(chemin: String, taille: Long = 10) =
        FichierProjet(chemin.substringAfterLast('/'), chemin, "/p/$chemin", taille, true)

    private val fichiers = listOf(
        f("index.html"), f("README.md"), f("src/app.js"), f("src/Util.js"),
        f("src/ui/vue.html"), f("zeta.txt")
    )

    @Test fun rootShowsFoldersThenFilesSortedIgnoringCase() {
        val c = Arborescence.contenu("", fichiers, listOf("src", "src/ui", "vide"))
        assertEquals(listOf("src", "vide"), c.dossiers.map { it.nom })
        assertEquals(listOf("index.html", "README.md", "zeta.txt"), c.fichiers.map { it.nom })
    }

    @Test fun folderCountsItsDirectChildren() {
        val c = Arborescence.contenu("", fichiers, listOf("src", "src/ui", "vide"))
        // src : app.js, Util.js et le sous-dossier ui
        assertEquals(3, c.dossiers.first { it.nom == "src" }.elements)
        assertEquals(0, c.dossiers.first { it.nom == "vide" }.elements)
    }

    @Test fun enteringAFolderShowsOnlyItsContent() {
        val c = Arborescence.contenu("src", fichiers, listOf("src", "src/ui"))
        assertEquals(listOf("ui"), c.dossiers.map { it.nom })
        assertEquals("src/ui", c.dossiers.single().chemin)
        assertEquals(listOf("app.js", "Util.js"), c.fichiers.map { it.nom })
    }

    @Test fun foldersImpliedByFilesAreFoundEvenIfNotListed() {
        val c = Arborescence.contenu("", listOf(f("a/b/c.txt")), emptyList())
        assertEquals(listOf("a"), c.dossiers.map { it.nom })
        assertEquals(1, c.dossiers.single().elements)
    }

    @Test fun parentAndSegments() {
        assertEquals("", Arborescence.parent("src"))
        assertEquals("src", Arborescence.parent("src/ui"))
        assertEquals("", Arborescence.parent(""))
        assertEquals(listOf("src" to "src", "ui" to "src/ui"), Arborescence.segments("src/ui"))
        assertTrue(Arborescence.segments("").isEmpty())
    }
}
