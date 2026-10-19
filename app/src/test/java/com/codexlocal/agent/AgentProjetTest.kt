package com.codexlocal.agent

import com.codexlocal.project.ProjectStore
import com.codexlocal.project.Projet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentProjetTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun projet(): Projet = ProjectStore.creerProjetInterne(ctx, "agent_" + System.nanoTime())

    private fun appliquer(p: Projet, reponse: String, ignorerIncomplets: Boolean = false) =
        AgentProjet.appliquer(ctx, p, ActionsFichiers.extraire(reponse), ignorerIncomplets)

    @Test fun createsNewFilesAndFoldersAutomatically() {
        val p = projet()
        val bilan = appliquer(
            p,
            "=== FICHIER: index.html ===\n<p>hi</p>\n=== FIN ===\n" +
                "=== FICHIER: js/app.js ===\nconsole.log(1);\n=== FIN ==="
        )
        assertEquals(listOf("index.html", "js/app.js"), bilan.crees)
        assertTrue(bilan.modifies.isEmpty())
        assertEquals("<p>hi</p>\n", File(p.uri, "index.html").readText())
        assertEquals("console.log(1);\n", File(p.uri, "js/app.js").readText())
        assertTrue(bilan.resume().contains("📁 Créé : index.html, js/app.js"))
    }

    @Test fun overwritesExistingFilesAndCanUndo() {
        val p = projet()
        File(p.uri, "a.txt").writeText("avant\n")
        val bilan = appliquer(p, "=== FICHIER: a.txt ===\naprès\n=== FIN ===\n=== FICHIER: b.txt ===\nneuf\n=== FIN ===")
        assertEquals(listOf("b.txt"), bilan.crees)
        assertEquals(listOf("a.txt"), bilan.modifies)
        assertEquals("après\n", File(p.uri, "a.txt").readText())

        assertEquals(2, AgentProjet.annuler(ctx, bilan.annulations))
        assertEquals("avant\n", File(p.uri, "a.txt").readText())
        assertFalse(File(p.uri, "b.txt").exists())
    }

    @Test fun identicalContentIsNotReportedAsModified() {
        val p = projet()
        File(p.uri, "a.txt").writeText("pareil\n")
        val bilan = appliquer(p, "=== FICHIER: a.txt ===\npareil\n=== FIN ===")
        assertTrue(bilan.modifies.isEmpty() && bilan.crees.isEmpty() && bilan.annulations.isEmpty())
    }

    @Test fun refusesUnsafePathsButKeepsGoing() {
        val p = projet()
        val bilan = appliquer(
            p,
            "=== FICHIER: ../dehors.txt ===\nx\n=== FIN ===\n" +
                "=== FICHIER: /absolu.txt ===\nx\n=== FIN ===\n" +
                "=== FICHIER: .cache/x.txt ===\nx\n=== FIN ===\n" +
                "=== FICHIER: ok.txt ===\nbon\n=== FIN ==="
        )
        assertEquals(listOf("ok.txt"), bilan.crees)
        assertEquals(3, bilan.ignores.size)
        assertFalse(File(p.uri).parentFile.resolve("dehors.txt").exists())
    }

    @Test fun refusesBinaryFilesAndEmptyContent() {
        val p = projet()
        val bilan = appliquer(
            p,
            "=== FICHIER: image.png ===\nx\n=== FIN ===\n=== FICHIER: vide.txt ===\n\n=== FIN ==="
        )
        assertTrue(bilan.crees.isEmpty())
        assertEquals(2, bilan.ignores.size)
    }

    @Test fun skipsPossiblyTruncatedFileOnlyWhenAsked() {
        val p = projet()
        val reponse = "=== FICHIER: a.txt ===\nok\n=== FIN ===\n=== FICHIER: b.txt ===\ncoupé"
        val strict = appliquer(p, reponse, ignorerIncomplets = true)
        assertEquals(listOf("a.txt"), strict.crees)
        assertEquals(1, strict.ignores.size)
        assertFalse(File(p.uri, "b.txt").exists())

        val tolerant = appliquer(p, reponse, ignorerIncomplets = false)
        assertEquals(listOf("b.txt"), tolerant.crees)
    }

    @Test fun readsFilesAndTruncatesBigOnes() {
        val p = projet()
        File(p.uri, "petit.txt").writeText("bonjour\n")
        File(p.uri, "gros.txt").writeText("x".repeat(AgentProjet.MAX_CARACTERES_LECTURE + 500))
        val bilan = appliquer(
            p, "=== LIRE: petit.txt ===\n=== LIRE: gros.txt ===\n=== LIRE: absent.txt ===\n=== LIRE: ../secret.txt ==="
        )
        assertEquals(4, bilan.lectures.size)
        assertEquals("bonjour\n", bilan.lectures[0].texte)
        assertTrue(bilan.lectures[1].texte!!.contains("tronqué"))
        assertNull(bilan.lectures[2].texte)
        assertNotNull(bilan.lectures[2].erreur)
        assertNull(bilan.lectures[3].texte)
        val renvoi = bilan.texteLectures()
        assertTrue(renvoi.contains("--- Fichier petit.txt ---") && renvoi.contains("fichier introuvable"))
    }

    @Test fun projectContextListsFiles() {
        val p = projet()
        File(p.uri, "src").mkdirs()
        File(p.uri, "src/main.py").writeText("print(1)\n")
        val contexte = AgentProjet.contexteProjet(ctx, p)
        assertTrue(contexte.contains(p.nom) && contexte.contains("README.md") && contexte.contains("src/main.py"))
    }

    @Test fun findFileIsExactAndSafe() {
        val p = projet()
        File(p.uri, "a/b").mkdirs()
        File(p.uri, "a/b/c.txt").writeText("x")
        assertEquals("a/b/c.txt", ProjectStore.trouverFichier(ctx, p, "a/b/c.txt")?.chemin)
        assertNull(ProjectStore.trouverFichier(ctx, p, "a/b"))
        assertNull(ProjectStore.trouverFichier(ctx, p, "a/zzz.txt"))
    }
}
