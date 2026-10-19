package com.codexlocal.project

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SuppressionDossierTest {

    private lateinit var base: File
    private lateinit var racine: File

    @Before fun preparer() {
        base = Files.createTempDirectory("suppr").toFile()
        racine = File(base, "projets").apply { mkdirs() }
    }

    @After fun nettoyer() {
        base.deleteRecursively()
    }

    private fun projet(nom: String): File {
        val p = File(racine, nom).apply { mkdirs() }
        File(p, "index.html").writeText("<h1>x</h1>")
        File(p, "src/ui").mkdirs()
        File(p, "src/ui/app.js").writeText("let a = 1;")
        File(p, "vide").mkdirs()
        return p
    }

    @Test fun deletesTheWholeProject() {
        val p = projet("site")
        assertTrue(SuppressionDossier.supprimer(racine, p))
        assertFalse(p.exists())
        assertTrue(racine.isDirectory)
    }

    @Test fun leavesTheOtherProjectsAlone() {
        val a = projet("a")
        val b = projet("b")
        assertTrue(SuppressionDossier.supprimer(racine, a))
        assertFalse(a.exists())
        assertTrue(File(b, "src/ui/app.js").isFile)
    }

    @Test fun neverDeletesTheProjectsFolderItself() {
        val p = projet("site")
        assertFalse(SuppressionDossier.supprimer(racine, racine))
        assertTrue(File(p, "index.html").isFile)
    }

    @Test fun refusesAFolderOutsideOrDeeperThanAProject() {
        val p = projet("site")
        val dehors = File(base, "perso").apply { mkdirs() }
        File(dehors, "photo.txt").writeText("important")
        assertFalse(SuppressionDossier.supprimer(racine, dehors))
        assertTrue(File(dehors, "photo.txt").isFile)
        // un sous-dossier d'un projet n'est pas un projet
        assertFalse(SuppressionDossier.supprimer(racine, File(p, "src")))
        assertTrue(File(p, "src/ui/app.js").isFile)
    }

    @Test fun refusesDotAndDotDotNames() {
        projet("site")
        assertFalse(SuppressionDossier.supprimer(racine, File(racine, "..")))
        assertFalse(SuppressionDossier.supprimer(racine, File(racine, ".")))
        assertTrue(base.isDirectory)
        assertTrue(File(racine, "site/index.html").isFile)
    }

    @Test fun aMissingFolderCountsAsDeleted() {
        assertTrue(SuppressionDossier.supprimer(racine, File(racine, "fantome")))
    }

    @Test fun doesNotFollowASymbolicLinkOutOfTheProject() {
        val dehors = File(base, "perso").apply { mkdirs() }
        File(dehors, "photo.txt").writeText("important")
        val p = projet("site")
        Files.createSymbolicLink(File(p, "lien").toPath(), dehors.toPath())
        assertTrue(SuppressionDossier.supprimer(racine, p))
        assertFalse(p.exists())
        // le contenu pointe par le lien est intact
        assertEquals("important", File(dehors, "photo.txt").readText())
    }

    @Test fun aSymbolicLinkAsProjectIsRemovedWithoutTouchingItsTarget() {
        val dehors = File(base, "perso").apply { mkdirs() }
        File(dehors, "photo.txt").writeText("important")
        val lien = File(racine, "raccourci")
        Files.createSymbolicLink(lien.toPath(), dehors.toPath())
        assertTrue(SuppressionDossier.supprimer(racine, lien))
        assertFalse(Files.isSymbolicLink(lien.toPath()))
        assertEquals("important", File(dehors, "photo.txt").readText())
    }
}
