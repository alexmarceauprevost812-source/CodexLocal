package com.codexlocal

import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjectFoldersTest {

    private fun projet(): Projet {
        val ctx = RuntimeEnvironment.getApplication()
        return ProjectStore.creerProjetInterne(ctx, "dossiers_" + System.nanoTime())
    }

    @Test fun createsNestedFoldersAndListsEmptyOnes() {
        val ctx = RuntimeEnvironment.getApplication()
        val p = projet()
        assertEquals("src/utils", ProjectStore.creerDossier(ctx, p, "src/utils"))
        ProjectStore.creerDossier(ctx, p, "assets")
        assertTrue(File(p.uri, "src/utils").isDirectory)
        assertEquals(listOf("assets", "src", "src/utils"), ProjectStore.listerDossiers(ctx, p))
    }

    @Test fun refusesExistingFoldersFilesAndUnsafePaths() {
        val ctx = RuntimeEnvironment.getApplication()
        val p = projet()
        ProjectStore.creerDossier(ctx, p, "src")
        for (chemin in listOf("src", "README.md/sous", "../dehors", "/absolu", ".cache")) {
            try {
                ProjectStore.creerDossier(ctx, p, chemin)
                fail("Aurait dû refuser : $chemin")
            } catch (e: IllegalArgumentException) {
            } catch (e: IllegalStateException) {
            }
        }
        assertEquals(listOf("src"), ProjectStore.listerDossiers(ctx, p))
    }
}
