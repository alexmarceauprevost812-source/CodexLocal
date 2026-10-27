package com.codexlocal.studio

import com.codexlocal.agent.ActionFichier
import com.codexlocal.project.ProjectStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MoteurApplicationTest {
    @Test fun starterIsARealOfflineProjectAndCannotReplaceAnExistingApp() {
        val context = RuntimeEnvironment.getApplication()
        val projet = ProjectStore.creerProjetInterne(context, "Moteur_${System.nanoTime()}")
        MoteurApplication.creerBase(context, projet)
        val page = ProjectStore.trouverFichier(context, projet, "index.html")!!
        assertTrue(ProjectStore.lireTexte(context, page).contains("LexApp.mount"))
        assertNotNull(ProjectStore.trouverFichier(context, projet, MoteurApplication.CHEMIN))
        assertTrue(runCatching { MoteurApplication.creerBase(context, projet) }.isFailure)
    }

    @Test fun missingEngineIsAddedToFileActionsWithoutOverwritingAnExistingEngine() {
        val context = RuntimeEnvironment.getApplication()
        val projet = ProjectStore.creerProjetInterne(context, "Moteur_actions_${System.nanoTime()}")
        val actions = listOf(ActionFichier.Ecrire("index.html", "<script src=\"${MoteurApplication.CHEMIN}\"></script>"))
        assertEquals(2, MoteurApplication.completerActions(context, projet, actions).size)
        ProjectStore.creerFichier(context, projet, MoteurApplication.CHEMIN, "personnalisé")
        assertEquals(actions, MoteurApplication.completerActions(context, projet, actions))
        assertEquals("personnalisé", ProjectStore.lireTexte(context, ProjectStore.trouverFichier(context, projet, MoteurApplication.CHEMIN)!!))
    }
}
