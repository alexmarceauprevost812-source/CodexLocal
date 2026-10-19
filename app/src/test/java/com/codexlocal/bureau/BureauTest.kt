package com.codexlocal.bureau

import android.app.Activity
import android.os.Bundle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.R
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Projet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BureauTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private val p1 = Projet("Jeu", "/projets/Jeu", 1L, true)
    private val p2 = Projet("Site", "/projets/Site", 2L, true)
    private val fichier = FichierProjet("index.html", "web/index.html", "/projets/Site/web/index.html", 12, true)

    // ----- entrees de menu

    @Test fun menuKeysAreUniqueAndComeBackFromTheirKey() {
        val cles = ActionBureau.values().map { it.cle }
        assertEquals(cles.size, cles.toSet().size)
        ActionBureau.values().forEach { assertEquals(it, ActionBureau.depuisCle(it.cle)) }
        assertNull(ActionBureau.depuisCle("n'existe-pas"))
        assertNull(ActionBureau.depuisCle(null))
    }

    @Test fun everyMenuEntryHasATitleAndAShortExplanation() {
        ActionBureau.values().forEach {
            assertTrue(it.emoji.isNotBlank())
            assertTrue(ctx.getString(it.titre).isNotBlank())
            assertTrue(ctx.getString(it.sous).isNotBlank())
        }
    }

    // ----- favoris

    @Test fun favoritesToggleAndSurviveANewInstance() {
        val cle = Favoris.cleProjet("/projets/Jeu")
        assertFalse(Favoris(ctx).estFavori(cle))
        assertTrue(Favoris(ctx).basculer(cle))
        assertTrue(Favoris(ctx).estFavori(cle))
        assertFalse(Favoris(ctx).basculer(cle))
        assertFalse(Favoris(ctx).estFavori(cle))
    }

    @Test fun favoriteKeysAreDecodedAndDamagedOnesRefused() {
        assertEquals(Favoris.Cible.DeProjet("/a"), Favoris.decoder(Favoris.cleProjet("/a")))
        assertEquals(
            Favoris.Cible.DeFichier("/a", "src/x.py"),
            Favoris.decoder(Favoris.cleFichier("/a", "src/x.py"))
        )
        assertNull(Favoris.decoder("n'importe quoi"))
        assertNull(Favoris.decoder("P\t"))
        assertNull(Favoris.decoder("F\t/a"))
    }

    // ----- composition de la grille

    @Test fun gridStartsWithEveryMenuEntry() {
        val grille = ConstructeurBureau.construire(listOf(p1), emptySet(), { _, _ -> null })
        val menus = grille.take(ActionBureau.values().size)
        assertTrue(menus.all { it is ElementBureau.Menu })
        assertEquals(ActionBureau.values().toList(), menus.map { (it as ElementBureau.Menu).action })
    }

    @Test fun noFavoritesMeansNoFavoritesSection() {
        val grille = ConstructeurBureau.construire(listOf(p1, p2), emptySet(), { _, _ -> null })
        val apres = grille.drop(ActionBureau.values().size)
        assertEquals(ElementBureau.Entete("📁 Projets"), apres.first())
        assertEquals(listOf(p1, p2), apres.filterIsInstance<ElementBureau.ProjetTuile>().map { it.projet })
    }

    @Test fun favoritesComeBeforeProjectsAndMissingOnesAreSkipped() {
        val favoris = setOf(
            Favoris.cleProjet(p1.uri),
            Favoris.cleFichier(p2.uri, "web/index.html"),
            Favoris.cleFichier(p2.uri, "disparu.txt"),
            Favoris.cleProjet("/projets/Efface")
        )
        val grille = ConstructeurBureau.construire(
            listOf(p1, p2), favoris,
            { projet, chemin -> if (projet == p2 && chemin == "web/index.html") fichier else null }
        ).drop(ActionBureau.values().size)

        assertEquals("⭐ Favoris", (grille.first() as ElementBureau.Entete).texte)
        val indexProjets = grille.indexOf(ElementBureau.Entete("📁 Projets"))
        val favorites = grille.take(indexProjets).filter { it !is ElementBureau.Entete }
        assertEquals(2, favorites.size)
        assertTrue(favorites.any { it is ElementBureau.ProjetTuile && it.projet == p1 })
        assertTrue(favorites.any { it is ElementBureau.FichierTuile && it.fichier == fichier })
        // Le projet en favori porte son etoile aussi dans la liste des projets.
        val tuileP1 = grille.drop(indexProjets).filterIsInstance<ElementBureau.ProjetTuile>().first { it.projet == p1 }
        assertTrue(tuileP1.favori)
    }

    @Test fun withoutProjectsAnExplanationIsShown() {
        val grille = ConstructeurBureau.construire(emptyList(), emptySet(), { _, _ -> null })
        assertTrue(grille.last() is ElementBureau.Info)
    }

    // ----- ecran

    @Test fun deskHasFourColumnsAndATapOnAMenuTileReturnsItsKey() {
        val activite = Robolectric.buildActivity(BureauActivity::class.java).setup().get()
        val liste = activite.findViewById<RecyclerView>(R.id.tuiles)
        assertEquals(4, (liste.layoutManager as GridLayoutManager).spanCount)
        assertTrue(liste.adapter!!.itemCount >= ActionBureau.values().size)

        val adaptateur = liste.adapter!!
        val vue = adaptateur.createViewHolder(liste, adaptateur.getItemViewType(0))
        adaptateur.bindViewHolder(vue, 0)
        vue.itemView.performClick()

        val ombre = shadowOf(activite)
        assertEquals(Activity.RESULT_OK, ombre.resultCode)
        assertEquals(ActionBureau.values()[0].cle, ombre.resultIntent.getStringExtra(BureauActivity.EXTRA_ACTION))
        assertTrue(activite.isFinishing)
    }
}
