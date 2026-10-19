package com.codexlocal.studio.apk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PngEnteteTest {

    @Test fun readsWidthAndHeightFromTheHeader() {
        assertEquals(48 to 48, PngEntete.dimensions(PngTest.uni(48, 48, 0xFF000000.toInt())))
        assertEquals(300 to 200, PngEntete.dimensions(PngTest.uni(300, 200, 0xFFFF0000.toInt())))
        assertEquals(1 to 700, PngEntete.dimensions(PngTest.uni(1, 700, 0xFF00FF00.toInt())))
    }

    @Test fun refusesAnythingThatIsNotAPng() {
        assertNull(PngEntete.dimensions(ByteArray(0)))
        assertNull(PngEntete.dimensions(ByteArray(100)))
        assertNull(PngEntete.dimensions("<html>erreur 404</html>".repeat(5).toByteArray()))
        // PNG tronque dans son en-tete.
        assertNull(PngEntete.dimensions(PngTest.uni(8, 8, 0xFF000000.toInt()).copyOf(20)))
        // Signature correcte mais premier bloc qui n'est pas IHDR.
        val faux = PngTest.uni(8, 8, 0xFF000000.toInt()).also { it[12] = 'X'.code.toByte() }
        assertNull(PngEntete.dimensions(faux))
    }
}
