package com.codexlocal.project

import android.content.DialogInterface
import android.content.Intent
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import com.codexlocal.projet.ProjetActivity
import com.codexlocal.session.SessionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuppressionProjetTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun projetInterne(): Projet =
        ProjectStore.creerProjetInterne(ctx, "suppr_" + System.nanoTime()).also {
            File(it.uri, "src").mkdirs()
            File(it.uri, "src/app.js").writeText("let a = 1;")
        }

    /** Un ecran Material quelconque pour y ouvrir les fenetres. */
    private fun hote(): ProjetActivity {
        val p = projetInterne()
        val intent = Intent(ctx, ProjetActivity::class.java).putExtra(ProjetActivity.EXTRA_PROJET_URI, p.uri)
        return Robolectric.buildActivity(ProjetActivity::class.java, intent).setup().get()
    }

    /** L'effacement se fait en arriere-plan : on laisse le temps de revenir sur l'ecran. */
    private fun attendre(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(25)
        }
    }

    private fun fenetre(): AlertDialog {
        shadowOf(Looper.getMainLooper()).idle()
        return ShadowDialog.getLatestDialog() as AlertDialog
    }

    // ------------------------------------------------------------ le stockage

    @Test fun anInternalProjectIsReallyDeleted() {
        val p = projetInterne()
        assertTrue(ProjectStore.lister(ctx).any { it.uri == p.uri })
        assertTrue(ProjectStore.supprimerProjet(ctx, p))
        assertFalse(File(p.uri).exists())
        assertTrue(ProjectStore.lister(ctx).none { it.uri == p.uri })
    }

    @Test fun aProjectFolderChosenOnThePhoneIsOnlyRemovedFromTheList() {
        val dossier = File(ctx.cacheDir, "perso_" + System.nanoTime()).apply { mkdirs() }
        File(dossier, "photo.txt").writeText("important")
        val p = Projet("perso", dossier.absolutePath, System.currentTimeMillis(), interne = false)
        ProjectStore.ajouter(ctx, p)
        assertTrue(ProjectStore.lister(ctx).any { it.uri == p.uri })

        assertTrue(ProjectStore.supprimerProjet(ctx, p))
        assertTrue(ProjectStore.lister(ctx).none { it.uri == p.uri })
        assertEquals("important", File(dossier, "photo.txt").readText())
    }

    @Test fun neverDeletesAFolderOutsideTheProjectsSpace() {
        val dossier = File(ctx.cacheDir, "dehors_" + System.nanoTime()).apply { mkdirs() }
        File(dossier, "photo.txt").writeText("important")
        val faux = Projet("dehors", dossier.absolutePath, 0L, interne = true)
        assertFalse(ProjectStore.supprimerProjet(ctx, faux))
        assertEquals("important", File(dossier, "photo.txt").readText())
    }

    @Test fun aProjectWhoseFolderVanishedIsNoLongerListed() {
        val p = projetInterne()
        ProjectStore.ajouter(ctx, p)
        File(p.uri).deleteRecursively()
        assertTrue(ProjectStore.lister(ctx).none { it.uri == p.uri })
    }

    @Test fun severalSessionsCanBeDeleted() {
        val a = SessionStore.creer("a_" + System.nanoTime())
        val b = SessionStore.creer("b_" + System.nanoTime())
        val c = SessionStore.creer("c_" + System.nanoTime())
        listOf(a, b, c).forEach { SessionStore.enregistrer(ctx, it) }
        listOf(a.id, c.id).forEach { SessionStore.supprimer(ctx, it) }
        val restantes = SessionStore.lister(ctx).map { it.id }
        assertFalse(a.id in restantes)
        assertTrue(b.id in restantes)
        assertFalse(c.id in restantes)
        SessionStore.supprimer(ctx, b.id)
    }

    // ------------------------------------------------------------ les fenetres

    @Test fun confirmingDeletesAnInternalProject() {
        val activite = hote()
        val p = projetInterne()
        var fait = false
        Dialogues.confirmerSuppressionProjet(activite, p) { fait = true }
        val d = fenetre()
        assertTrue(File(p.uri).exists())
        d.getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        attendre { fait }
        assertTrue(fait)
        assertFalse(File(p.uri).exists())
        assertTrue(ProjectStore.lister(ctx).none { it.uri == p.uri })
    }

    @Test fun theStepBeforeDeletingRunsFirst() {
        val activite = hote()
        val p = projetInterne()
        var avantVu = false
        var existaitAvant = false
        var fait = false
        Dialogues.confirmerSuppressionProjet(
            activite, p,
            avant = { avantVu = true; existaitAvant = File(p.uri).exists() }
        ) { fait = true }
        fenetre().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        attendre { fait }
        assertTrue(avantVu)
        assertTrue(existaitAvant)
        assertTrue(fait)
    }

    @Test fun aProjectFolderChosenOnThePhoneIsOnlyRemovedFromTheListByTheDialog() {
        val activite = hote()
        val dossier = File(ctx.cacheDir, "perso2_" + System.nanoTime()).apply { mkdirs() }
        File(dossier, "photo.txt").writeText("important")
        val p = Projet("perso2", dossier.absolutePath, System.currentTimeMillis(), interne = false)
        ProjectStore.ajouter(ctx, p)
        var fait = false
        Dialogues.confirmerSuppressionProjet(activite, p) { fait = true }
        fenetre().getButton(DialogInterface.BUTTON_POSITIVE).performClick()
        attendre { fait }
        assertTrue(fait)
        assertTrue(ProjectStore.lister(ctx).none { it.uri == p.uri })
        assertEquals("important", File(dossier, "photo.txt").readText())
    }

    @Test fun cancellingKeepsTheProject() {
        val activite = hote()
        val p = projetInterne()
        var fait = false
        Dialogues.confirmerSuppressionProjet(activite, p) { fait = true }
        fenetre().getButton(DialogInterface.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertTrue(File(p.uri).exists())
        assertFalse(fait)
    }

    private fun cocher(d: AlertDialog, position: Int) {
        val liste = d.listView
        liste.performItemClick(liste.adapter.getView(position, null, liste), position, 0)
    }

    @Test fun theActionStaysGreyedUntilSomethingIsChecked() {
        val activite = hote()
        var recu: List<Int>? = null
        Dialogues.choisirPlusieurs(activite, "Titre", listOf("a", "b", "c"), "Supprimer") { recu = it }
        val d = fenetre()
        val action = d.getButton(DialogInterface.BUTTON_POSITIVE)
        assertFalse(action.isEnabled)
        action.performClick()
        assertEquals(null, recu)

        cocher(d, 1)
        assertTrue(action.isEnabled)
        action.performClick()
        assertEquals(listOf(1), recu)
    }

    @Test fun checkAllThenUncheckAll() {
        val activite = hote()
        var recu: List<Int>? = null
        Dialogues.choisirPlusieurs(activite, "Titre", listOf("a", "b", "c"), "Supprimer") { recu = it }
        val d = fenetre()
        val action = d.getButton(DialogInterface.BUTTON_POSITIVE)
        val tout = d.getButton(DialogInterface.BUTTON_NEUTRAL)

        tout.performClick()
        assertTrue(action.isEnabled)
        assertEquals(activite.getString(com.codexlocal.R.string.suppr_decocher_tout), tout.text.toString())

        tout.performClick()
        assertFalse(action.isEnabled)
        assertEquals(activite.getString(com.codexlocal.R.string.suppr_cocher_tout), tout.text.toString())

        tout.performClick()
        action.performClick()
        assertEquals(listOf(0, 1, 2), recu)
    }
}
