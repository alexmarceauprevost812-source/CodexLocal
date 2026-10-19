package com.codexlocal.usage

import com.codexlocal.ProviderType
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CompteurUsageTest {

    private val ctx get() = RuntimeEnvironment.getApplication()
    private var date = LocalDate.of(2026, 10, 18)
    private fun compteur() = CompteurUsage(ctx) { date }
    private val p = ProviderType.OPENAI

    @Test fun requestsAndTokensAddUpForTodayAndThisMonth() {
        val c = compteur()
        c.enregistrer(p, 100, 40)
        c.enregistrer(p, 10, 5)
        val e = c.etat(p)
        assertEquals(2, e.jour.requetes)
        assertEquals(155, e.jour.jetons)
        assertEquals(110, e.jour.jetonsEntree)
        assertEquals(155, e.mois.jetons)
    }

    @Test fun eachProviderHasItsOwnCounter() {
        val c = compteur()
        c.enregistrer(ProviderType.OPENAI, 100, 100)
        assertEquals(0, c.etat(ProviderType.ANTHROPIC).jour.jetons)
        assertEquals(200, c.etat(ProviderType.OPENAI).jour.jetons)
    }

    @Test fun dayRestartsAtZeroButMonthKeepsCounting() {
        val c = compteur()
        c.enregistrer(p, 100, 0)
        date = date.plusDays(1)
        assertEquals(0, c.etat(p).jour.jetons)
        assertEquals(100, c.etat(p).mois.jetons)
        c.enregistrer(p, 50, 0)
        assertEquals(50, c.etat(p).jour.jetons)
        assertEquals(150, c.etat(p).mois.jetons)
    }

    @Test fun monthRestartsAtZeroToo() {
        val c = compteur()
        c.enregistrer(p, 100, 0)
        date = LocalDate.of(2026, 11, 1)
        assertEquals(0, c.etat(p).mois.jetons)
    }

    @Test fun resetClearsTheCountersButKeepsTheLimits() {
        val c = compteur()
        c.definirLimites(p, 1000, 5000)
        c.enregistrer(p, 100, 100)
        c.remettreAZero(p)
        val e = c.etat(p)
        assertEquals(0, e.jour.jetons)
        assertEquals(0, e.mois.requetes)
        assertEquals(1000, e.limiteJour)
        assertEquals(5000, e.limiteMois)
    }

    @Test fun noLimitMeansNothingIsEverBlocked() {
        val c = compteur()
        c.enregistrer(p, 5_000_000, 5_000_000)
        c.verifier(p)
        assertNull(c.etat(p).pourcentMax)
        assertFalse(c.etat(p).limiteAtteinte)
    }

    @Test fun dayLimitBlocksOnceReached() {
        val c = compteur()
        c.definirLimites(p, 1000, 0)
        c.enregistrer(p, 500, 400)
        c.verifier(p)
        assertEquals(90, c.etat(p).pourcentJour)
        c.enregistrer(p, 100, 0)
        try {
            c.verifier(p)
            fail("la limite du jour aurait du bloquer")
        } catch (e: LimiteAtteinteException) {
            assertTrue(e.message!!.contains("jour"))
        }
        // Le lendemain, c'est reparti.
        date = date.plusDays(1)
        c.verifier(p)
    }

    @Test fun monthLimitBlocksEvenWhenTheDayIsFresh() {
        val c = compteur()
        c.definirLimites(p, 0, 1000)
        c.enregistrer(p, 1000, 0)
        date = date.plusDays(1)
        try {
            c.verifier(p)
            fail("la limite du mois aurait du bloquer")
        } catch (e: LimiteAtteinteException) {
            assertTrue(e.message!!.contains("mois"))
        }
    }

    @Test fun negativeValuesAreNotAccepted() {
        val c = compteur()
        c.definirLimites(p, -5, -1)
        assertEquals(0, c.etat(p).limiteJour)
        c.enregistrer(p, -10, -10)
        assertEquals(0, c.etat(p).jour.jetons)
    }

    @Test fun tokenEstimateIsAboutFourCharactersEach() {
        assertEquals(0, CompteurUsage.estimerJetons(""))
        assertEquals(1, CompteurUsage.estimerJetons("a"))
        assertEquals(1, CompteurUsage.estimerJetons("abcd"))
        assertEquals(2, CompteurUsage.estimerJetons("abcde"))
        assertEquals(250, CompteurUsage.estimerJetons("x".repeat(1000)))
    }

    @Test fun summaryWarnsFromEightyPercent() {
        val c = compteur()
        c.definirLimites(p, 1000, 0)
        c.enregistrer(p, 700, 0)
        assertFalse(CompteurUsage.resume(c.etat(p)).contains("⚠️"))
        c.enregistrer(p, 100, 0)
        val texte = CompteurUsage.resume(c.etat(p))
        assertTrue(texte.contains("⚠️") && texte.contains("jetons aujourd'hui"))
    }

    @Test fun streamIsCountedAfterItEnds() = runBlocking {
        val c = compteur()
        val recu = c.compter(p, 25, flowOf("abcd", "efgh", "ij")).toList()
        assertEquals(listOf("abcd", "efgh", "ij"), recu)
        val e = c.etat(p)
        assertEquals(1, e.jour.requetes)
        assertEquals(25, e.jour.jetonsEntree)
        assertEquals(3, e.jour.jetonsSortie) // 10 caracteres -> 3 jetons
    }

    @Test fun failedRequestWithoutAnyAnswerCostsNothing() = runBlocking {
        val c = compteur()
        try {
            c.compter(p, 25, flow<String> { error("HTTP 401") }).toList()
            fail("l'erreur devait remonter")
        } catch (e: IllegalStateException) {
            assertEquals("HTTP 401", e.message)
        }
        assertEquals(0, c.etat(p).jour.requetes)
    }

    @Test fun blockedRequestNeverReachesTheService() = runBlocking {
        val c = compteur()
        c.definirLimites(p, 10, 0)
        c.enregistrer(p, 10, 0)
        var appele = false
        try {
            c.compter(p, 1, flow { appele = true; emit("x") }).toList()
            fail("la limite devait bloquer")
        } catch (e: LimiteAtteinteException) {
            // attendu
        }
        assertFalse(appele)
    }
}
