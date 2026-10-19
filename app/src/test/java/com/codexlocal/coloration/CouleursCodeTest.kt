package com.codexlocal.coloration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CouleursCodeTest {

    @Test fun troisEtSixChiffres() {
        assertEquals(0xFFFFFFFF.toInt(), CouleursCode.depuisHex("fff", false))
        assertEquals(0xFFFF0000.toInt(), CouleursCode.depuisHex("f00", false))
        assertEquals(0xFFFF9A3D.toInt(), CouleursCode.depuisHex("FF9A3D", false))
        assertEquals(0xFFFF9A3D.toInt(), CouleursCode.depuisHex("ff9a3d", true))
    }

    @Test fun quatreChiffresSontTransparentsEtMelangesAuFond() {
        assertEquals(CouleursCode.opaque(255, 0, 0, 0x88), CouleursCode.depuisHex("f008", false))
        assertEquals(CouleursCode.opaque(255, 0, 0, 255), CouleursCode.depuisHex("f00f", false))
    }

    @Test fun huitChiffresSelonLOrdre() {
        // CSS : RRGGBBAA — Android : AARRGGBB
        assertEquals(CouleursCode.opaque(0x80, 0xFF, 0x00, 0x00), CouleursCode.depuisHex("80ff0000", false))
        assertEquals(CouleursCode.opaque(0xFF, 0x00, 0x00, 0x80), CouleursCode.depuisHex("80ff0000", true))
        assertEquals(0xFFFF0000.toInt(), CouleursCode.depuisHex("ffff0000", true))
        assertEquals(0xFFFF0000.toInt(), CouleursCode.depuisHex("ff0000ff", false))
    }

    @Test fun toutTransparentDonneLeFond() {
        assertEquals(0xFF070707.toInt(), CouleursCode.depuisHex("00000000", true))
    }

    @Test fun lesCodesInvalidesDonnentNull() {
        assertNull(CouleursCode.depuisHex("ggg", false))
        assertNull(CouleursCode.depuisHex("12345", false))
        assertNull(CouleursCode.depuisHex("", false))
        assertNull(CouleursCode.depuisHex("1234567", true))
        assertNull(CouleursCode.depuisHex("ff 0000", false))
    }

    @Test fun letexteEstNoirSurClairEtBlancSurSombre() {
        val noir = 0xFF000000.toInt()
        val blanc = 0xFFFFFFFF.toInt()
        assertEquals(noir, CouleursCode.texteSur(0xFFFFFFFF.toInt()))
        assertEquals(noir, CouleursCode.texteSur(0xFFFF9A3D.toInt()))
        assertEquals(noir, CouleursCode.texteSur(0xFFFFFF00.toInt()))
        assertEquals(blanc, CouleursCode.texteSur(0xFF000000.toInt()))
        assertEquals(blanc, CouleursCode.texteSur(0xFF0000FF.toInt()))
        assertEquals(blanc, CouleursCode.texteSur(0xFFFF0000.toInt()))
    }
}
