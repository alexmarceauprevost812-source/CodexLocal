package com.codexlocal.session

import android.app.ApplicationExitInfo
import org.junit.Assert.*
import org.junit.Test

/** Gel de l'écran pendant l'écriture et diagnostic de la dernière fermeture. */
class GelTest {
    @Test fun unRenduRapideGardeLeRythmeHabituel() {
        assertEquals(80L, RythmeAffichage.intervalle(0, perso = false, coutRenduMs = 0))
        assertEquals(500L, RythmeAffichage.intervalle(100_000, perso = false, coutRenduMs = 10))
        assertEquals(260L, RythmeAffichage.intervalle(100_000, perso = true, coutRenduMs = 10))
    }

    @Test fun unRenduLentEspaceLesRafraichissements() {
        // 200 ms de rendu : au moins 1,2 s entre deux rendus, même dans l'APK perso.
        assertEquals(1_200L, RythmeAffichage.intervalle(20_000, perso = true, coutRenduMs = 200))
        assertEquals(RythmeAffichage.MAX_MS, RythmeAffichage.intervalle(20_000, perso = false, coutRenduMs = 5_000))
        assertEquals(80L, RythmeAffichage.intervalle(0, perso = false, coutRenduMs = -5))
    }

    @Test fun causesDeFermeture() {
        assertEquals(DernierArret.Cause.MEMOIRE, DernierArret.cause(ApplicationExitInfo.REASON_LOW_MEMORY, 0))
        assertEquals(DernierArret.Cause.MEMOIRE, DernierArret.cause(ApplicationExitInfo.REASON_SIGNALED, 9))
        assertNull(DernierArret.cause(ApplicationExitInfo.REASON_SIGNALED, 15))
        assertEquals(DernierArret.Cause.MOTEUR, DernierArret.cause(ApplicationExitInfo.REASON_CRASH_NATIVE, 0))
        assertEquals(DernierArret.Cause.BLOQUEE, DernierArret.cause(ApplicationExitInfo.REASON_ANR, 0))
        assertEquals(DernierArret.Cause.ERREUR, DernierArret.cause(ApplicationExitInfo.REASON_CRASH, 0))
        assertNull(DernierArret.cause(ApplicationExitInfo.REASON_USER_REQUESTED, 0))
        assertTrue(DernierArret.reduitLaMemoire(DernierArret.Cause.MEMOIRE))
        assertFalse(DernierArret.reduitLaMemoire(DernierArret.Cause.BLOQUEE))
    }

    @Test fun memoirePrudenteNePrendJamaisLaGrandeMemoire() {
        val gio = 1024L * 1024 * 1024
        assertEquals(ContexteLocal.BASE, ContexteLocal.tailleSouhaitee(12 * gio, 2 * gio, prudente = true))
        assertEquals(ContexteLocal.GRANDE, ContexteLocal.tailleSouhaitee(12 * gio, 2 * gio))
    }
}
