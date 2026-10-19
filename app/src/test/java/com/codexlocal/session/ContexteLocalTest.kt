package com.codexlocal.session

import org.junit.Assert.assertEquals
import org.junit.Test

class ContexteLocalTest {

    private val gio = 1024L * 1024 * 1024

    @Test fun plentyOfFreeMemoryGivesTheBigContext() {
        assertEquals(ContexteLocal.GRANDE, ContexteLocal.tailleSouhaitee(8 * gio, 2 * gio))
    }

    @Test fun tightMemoryKeepsTheUsualContext() {
        assertEquals(ContexteLocal.BASE, ContexteLocal.tailleSouhaitee(4 * gio, 2 * gio))
    }

    @Test fun aBigModelUsesUpTheMargin() {
        assertEquals(ContexteLocal.BASE, ContexteLocal.tailleSouhaitee(6 * gio, 4 * gio))
    }

    @Test fun unknownMemoryIsPrudent() {
        assertEquals(ContexteLocal.BASE, ContexteLocal.tailleSouhaitee(0L, 2 * gio))
    }

    @Test fun marginThresholdIsThreeAndAHalfGigabytes() {
        assertEquals(ContexteLocal.GRANDE, ContexteLocal.tailleSouhaitee(5 * gio + gio / 2, 2 * gio))
        assertEquals(ContexteLocal.BASE, ContexteLocal.tailleSouhaitee(5 * gio + gio / 2 - 1, 2 * gio))
    }
}
