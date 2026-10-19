package com.codexlocal.coloration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ProfilsTest {

    @Test fun lesNomsSontNormalises() {
        assertEquals("kotlin", Profils.normaliser("kt"))
        assertEquals("kotlin", Profils.normaliser(".KT"))
        assertEquals("kotlin", Profils.normaliser("  Kotlin  "))
        assertEquals("cpp", Profils.normaliser("c++"))
        assertEquals("csharp", Profils.normaliser("c#"))
        assertEquals("javascript", Profils.normaliser("js {1,3}"))
        assertEquals("python", Profils.normaliser("Python3 title=\"a.py\""))
        assertEquals("bash", Profils.normaliser("sh"))
        assertEquals("yaml", Profils.normaliser("yml"))
        assertEquals("markdown", Profils.normaliser("md"))
        assertEquals("plain", Profils.normaliser("txt"))
        assertEquals("", Profils.normaliser(""))
        assertEquals("zzz", Profils.normaliser("zzz"))
    }

    @Test fun lesLangagesProchesPartagentUnProfil() {
        assertSame(Profils.pour("javascript"), Profils.pour("typescript"))
        assertSame(Profils.pour("c"), Profils.pour("cpp"))
    }

    @Test fun unLangageInconnuAUnProfilGenerique() {
        assertSame(Profils.pour(""), Profils.pour("brainfuck"))
    }
}
