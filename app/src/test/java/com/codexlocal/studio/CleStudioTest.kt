package com.codexlocal.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class CleStudioTest {

    @Test fun createsAKeyThatCanBeReloadedAndExported() {
        val ctx = RuntimeEnvironment.getApplication()
        assertFalse(CleStudio.existe(ctx))
        assertNull(CleStudio.info(ctx))

        val info = CleStudio.creer(ctx, "Alex, Test=1")
        assertTrue(CleStudio.existe(ctx))
        assertTrue(info.nom.contains("CN="))
        assertFalse("Les caractères spéciaux doivent disparaître", info.nom.contains("Test=1"))
        assertEquals(32 * 3 - 1, info.empreinte.length)
        assertTrue(info.expire.time > System.currentTimeMillis() + 20L * 365 * 24 * 3600 * 1000)

        val (cle, certificat) = CleStudio.charger(ctx)
        assertEquals("RSA", cle.algorithm)
        assertEquals(info.empreinte, CleStudio.empreinte(certificat))

        val sortie = ByteArrayOutputStream()
        CleStudio.copierVers(ctx, sortie)
        assertTrue(sortie.size() > 500)

        // Une seconde clé remplace la première.
        val autre = CleStudio.creer(ctx, "Autre")
        assertNotNull(autre)
        assertFalse(autre.empreinte == info.empreinte)
        assertEquals(autre.empreinte, CleStudio.info(ctx)?.empreinte)
    }

    @Test fun refusesEmptyNames() {
        try {
            CleStudio.nomValide(",=+<>")
            fail("Un nom sans caractère valide doit être refusé")
        } catch (e: IllegalArgumentException) {
            // attendu
        }
        assertEquals("Mon app-1_x.y", CleStudio.nomValide("  Mon app-1_x.y;  "))
    }
}
