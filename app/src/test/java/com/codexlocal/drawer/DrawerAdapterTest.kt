package com.codexlocal.drawer

import android.content.Intent
import android.widget.FrameLayout
import com.codexlocal.projet.ProjetActivity
import com.codexlocal.project.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DrawerAdapterTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun hote(): ProjetActivity {
        val p = ProjectStore.creerProjetInterne(ctx, "drawer_" + System.nanoTime())
        val intent = Intent(ctx, ProjetActivity::class.java).putExtra(ProjetActivity.EXTRA_PROJET_URI, p.uri)
        return Robolectric.buildActivity(ProjetActivity::class.java, intent).setup().get()
    }

    @Test fun aSectionCanOfferAPlusAndATrash() {
        val activite = hote()
        val recu = mutableListOf<DrawerItem>()
        val adapter = DrawerAdapter(
            mutableListOf(DrawerItem.Section("Sessions", "＋", "nouvelle", "🗑", "supprimer")),
            {}, { recu.add(it) }
        )
        val ligne = adapter.onCreateViewHolder(FrameLayout(activite), 0)
        adapter.onBindViewHolder(ligne, 0)

        assertEquals(android.view.View.VISIBLE, ligne.binding.action.visibility)
        assertEquals(android.view.View.VISIBLE, ligne.binding.action2.visibility)
        assertEquals("🗑", ligne.binding.action2.text.toString())

        ligne.binding.action.performClick()
        ligne.binding.action2.performClick()
        assertEquals("nouvelle", (recu[0] as DrawerItem.Section).cleAction)
        assertEquals("supprimer", (recu[1] as DrawerItem.Bouton).cleAction)
    }

    @Test fun aSectionWithoutTrashHidesIt() {
        val activite = hote()
        val adapter = DrawerAdapter(
            mutableListOf(
                DrawerItem.Section("Sessions", "＋", "nouvelle", "🗑", "supprimer"),
                DrawerItem.Section("Fichiers", "＋", "fichier")
            ),
            {}, {}
        )
        val ligne = adapter.onCreateViewHolder(FrameLayout(activite), 0)
        adapter.onBindViewHolder(ligne, 0)
        assertEquals(android.view.View.VISIBLE, ligne.binding.action2.visibility)
        // la meme ligne, recyclee pour une section sans corbeille
        adapter.onBindViewHolder(ligne, 1)
        assertEquals(android.view.View.GONE, ligne.binding.action2.visibility)
        assertFalse(ligne.binding.action2.hasOnClickListeners())
    }
}
