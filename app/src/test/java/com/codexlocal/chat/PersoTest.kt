package com.codexlocal.chat

import android.net.Uri
import com.codexlocal.Message
import com.codexlocal.outils.DateTexte
import com.codexlocal.web.RechercheRiche
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

/** Fonctions de l'APK perso : recherche riche, schémas animés, agenda, voix. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PersoTest {

    @Test fun villeMeteoEstTrouvee() {
        assertEquals("Normandin", RechercheRiche.ville("c'est quoi la météo à Normandin demain?"))
        assertEquals("Québec", RechercheRiche.ville("météo de Québec"))
        assertNull(RechercheRiche.ville("météo de demain"))
    }

    @Test fun sujetSansMotsDeclencheurs() {
        val sujet = RechercheRiche.sujet("montre-moi des vidéos de recettes de tourtière", Regex("(?U)\\b(vidéos?)\\b"))
        assertTrue(sujet, sujet.contains("recettes") && sujet.contains("tourtière") && !sujet.contains("vidéo"))
    }

    @Test fun miniGraphique() {
        assertEquals("▁▅█", RechercheRiche.sparkline(listOf(1.0, 2.0, 3.0), points = 3))
        assertEquals("", RechercheRiche.sparkline(listOf(1.0)))
    }

    @Test fun schemaAvecFleches() {
        val s = SchemaAnime.lire("Titre: Cycle de l'eau\nÉvaporation -> Nuages\nNuages -> Pluie -> Rivières")!!
        assertEquals("Cycle de l'eau", s.titre)
        assertEquals(listOf("Évaporation", "Nuages", "Pluie", "Rivières"), s.noeuds)
        assertEquals(3, s.liens.size)
    }

    @Test fun schemaEtapeParLigneEtAllerRetour() {
        val s = SchemaAnime.lire("1. Idée\n2. Code\n3. Test")!!
        assertEquals(listOf(0 to 1, 1 to 2), s.liens)
        val message = Message.assistant().apply { content = "Voici :\n```schema\nA -> B\nB -> C\n```\nFin" }
        SchemaAnime.annoter(message)
        assertFalse(message.content.contains("```"))
        val lien = Regex("\\((\\S+\\.schema://voir\\?d=[^)]+)\\)").find(message.content)!!.groupValues[1]
        val relu = SchemaAnime.decoder(Uri.parse(lien))!!
        assertEquals(listOf("A", "B", "C"), relu.noeuds)
        assertTrue(SchemaAnime.html(relu).contains("animateMotion"))
    }

    @Test fun datesDansUnTexte() {
        val demain = Calendar.getInstance().apply { timeInMillis = DateTexte.trouver("Rendez-vous demain à 14h30")!! }
        val attendu = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        assertEquals(attendu.get(Calendar.DAY_OF_YEAR), demain.get(Calendar.DAY_OF_YEAR))
        assertEquals(14, demain.get(Calendar.HOUR_OF_DAY)); assertEquals(30, demain.get(Calendar.MINUTE))
        val iso = Calendar.getInstance().apply { timeInMillis = DateTexte.trouver("2027-03-05 9:15")!! }
        assertEquals(2027, iso.get(Calendar.YEAR)); assertEquals(Calendar.MARCH, iso.get(Calendar.MONTH)); assertEquals(9, iso.get(Calendar.HOUR_OF_DAY))
        assertNotNull(DateTexte.trouver("le 12 novembre"))
        assertNull(DateTexte.trouver("bonjour"))
    }

    @Test fun emojisCryptoDeviennentDesLogos() {
        assertEquals("Le ![logo](logo:bitcoin.org) monte", EmojisChat.remplacer("Le :btc: monte"))
    }

    @Test fun lectureVoixSansMarkdown() {
        val propre = LecteurVoix.nettoyer("<think>blabla</think>**Salut** ![logo](logo:x.com) [Radio-Canada](https://ici.radio-canada.ca) 🎉")
        assertEquals("Salut Radio-Canada 🎉", propre)
    }
}
