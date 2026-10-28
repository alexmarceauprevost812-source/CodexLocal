package com.codexlocal.chat

import android.graphics.drawable.ColorDrawable
import android.text.Spanned
import android.text.style.ImageSpan
import android.text.style.URLSpan
import com.codexlocal.MarkdownLite
import com.codexlocal.Message
import com.codexlocal.web.MoteurCrypto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChatRicheTest {

    @Test fun emojiCodesBecomeEmojisButUnknownCodesStay() {
        assertEquals("Go 🚀 🔥 :inconnu: 12:30:45", EmojisChat.remplacer("Go :fusee: :feu: :inconnu: 12:30:45"))
    }

    @Test fun cryptoPriceQuestionsAreDetected() {
        assertEquals("bitcoin", MoteurCrypto.detecter("c'est quoi le prix du BTC?"))
        assertEquals("ethereum", MoteurCrypto.detecter("Combien vaut l'ethereum aujourd'hui"))
        assertEquals("solana", MoteurCrypto.detecter("valeur de la crypto sol"))
        assertNull("sans crypto, « sol » est un mot normal", MoteurCrypto.detecter("le prix du sol de cuisine"))
        assertNull("sans demande de prix", MoteurCrypto.detecter("explique-moi le bitcoin"))
    }

    @Test fun cryptoCardUsesLivePriceAndLogo() = runBlocking {
        val cours = MoteurCrypto.obtenir("bitcoin") { url ->
            if (url.contains("/coins/markets")) {
                """[{"id":"bitcoin","symbol":"btc","name":"Bitcoin","image":"https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png","current_price":98123.45,"price_change_percentage_24h":-1.5}]"""
            } else {
                """{"bitcoin":{"usd":71000.5}}"""
            }
        }
        val carte = cours.carte()
        assertTrue(carte, carte.startsWith("![Bitcoin](https://coin-images.coingecko.com/"))
        assertTrue(carte, carte.contains("BTC"))
        assertTrue(carte, carte.contains("98") && carte.contains("$ CA") && carte.contains("$ US"))
        assertTrue(carte, carte.contains("▼"))
        assertTrue(cours.contexte().contains("prix_cad"))

        val reponse = Message.assistant().apply { content = "Le bitcoin baisse un peu." }
        cours.annoter(reponse)
        assertTrue(reponse.content.startsWith("![Bitcoin]"))
        assertEquals("la carte n'est pas renvoyée à l'IA", "Le bitcoin baisse un peu.", reponse.envoi)
    }

    @Test fun linksAndBareUrlsAreClickable() {
        val r = MarkdownLite.format("Va sur [GitHub](https://github.com) ou https://example.com.") as Spanned
        val texte = r.toString()
        assertFalse(texte.contains("]("))
        val liens = r.getSpans(0, r.length, URLSpan::class.java).sortedBy { r.getSpanStart(it) }
        assertEquals(listOf("https://github.com", "https://example.com"), liens.map { it.url })
        assertTrue("le point final reste du texte", texte.endsWith("https://example.com."))
    }

    @Test fun imagesShowWhenResolvedAndOnlyAllowedHostsLoad() {
        val vues = mutableListOf<String>()
        val r = MarkdownLite.format("![logo](logo:github.com) GitHub") { source ->
            vues += source
            ColorDrawable().apply { setBounds(0, 0, 10, 10) }
        } as Spanned
        assertEquals(listOf("logo:github.com"), vues)
        assertEquals(1, r.getSpans(0, r.length, ImageSpan::class.java).size)
        assertTrue(r.toString().endsWith(" GitHub"))

        assertEquals("https://icons.duckduckgo.com/ip3/github.com.ico", ImagesChat.resoudre("logo:github.com"))
        assertNull(ImagesChat.resoudre("logo:../../etc"))
        assertNull("hôte inconnu : pas de téléchargement", ImagesChat.resoudre("https://pisteur.example/img.png"))
        assertTrue(ImagesChat.estPermise("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png"))
    }

    @Test fun codeIsNotTouchedByEmojisOrLinks() {
        val r = MarkdownLite.format("Exemple `:feu: https://x.com`") as Spanned
        assertTrue(r.toString().contains(":feu: https://x.com"))
        assertEquals(0, r.getSpans(0, r.length, URLSpan::class.java).size)
    }
}
