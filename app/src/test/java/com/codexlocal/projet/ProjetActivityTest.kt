package com.codexlocal.projet

import android.content.Intent
import android.os.Looper
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.R
import com.codexlocal.bureau.Favoris
import com.codexlocal.project.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProjetActivityTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun ouvrir(uri: String): ProjetActivity {
        val intent = Intent(ctx, ProjetActivity::class.java).putExtra(ProjetActivity.EXTRA_PROJET_URI, uri)
        val activite = Robolectric.buildActivity(ProjetActivity::class.java, intent).setup().get()
        // Le contenu est lu en arriere-plan : on laisse le temps de revenir sur l'ecran.
        val liste = activite.findViewById<RecyclerView>(R.id.listeProjet)
        repeat(100) {
            shadowOf(Looper.getMainLooper()).idle()
            if (liste.adapter!!.itemCount > 1) return activite
            Thread.sleep(30)
        }
        return activite
    }

    private fun creerProjet(): com.codexlocal.project.Projet {
        val p = ProjectStore.creerProjetInterne(ctx, "depot_" + System.nanoTime())
        File(p.uri, "index.html").writeText("<h1>x</h1>")
        File(p.uri, "src/ui").mkdirs()
        File(p.uri, "src/app.js").writeText("let a = 1;")
        File(p.uri, "vide").mkdirs()
        return p
    }

    @Test fun rootListsFoldersThenFilesLikeGithub() {
        val p = creerProjet()
        val activite = ouvrir(p.uri)
        val liste = activite.findViewById<RecyclerView>(R.id.listeProjet)
        // dossiers : src, vide ; fichiers : index.html, README.md
        assertEquals(4, liste.adapter!!.itemCount)
        // le chemin montre le nom du projet
        val chemin = activite.findViewById<android.widget.TextView>(R.id.cheminProjet).text.toString()
        assertTrue(chemin.contains(p.nom))
        // la barre des langages est visible (html + js)
        assertTrue(activite.findViewById<android.view.View>(R.id.barreLangages).visibility == android.view.View.VISIBLE)
    }

    @Test fun starTogglesTheProjectFavorite() {
        val p = creerProjet()
        val activite = ouvrir(p.uri)
        val cle = Favoris.cleProjet(p.uri)
        assertEquals(false, Favoris(ctx).estFavori(cle))
        val barre = activite.findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        (barre.menu.findItem(R.id.action_etoile).actionView as android.widget.TextView).performClick()
        assertEquals(true, Favoris(ctx).estFavori(cle))
        assertEquals("⭐", (barre.menu.findItem(R.id.action_etoile).actionView as android.widget.TextView).text.toString())
    }

    @Test fun unknownProjectClosesTheScreen() {
        val intent = Intent(ctx, ProjetActivity::class.java).putExtra(ProjetActivity.EXTRA_PROJET_URI, "/n/existe/pas")
        val activite = Robolectric.buildActivity(ProjetActivity::class.java, intent).setup().get()
        assertTrue(activite.isFinishing)
    }
}
