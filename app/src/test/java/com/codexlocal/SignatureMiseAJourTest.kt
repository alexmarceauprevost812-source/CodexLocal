package com.codexlocal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignatureMiseAJourTest {

    private val cleA = SignatureMiseAJour.empreinte("cle A".toByteArray())
    private val cleB = SignatureMiseAJour.empreinte("cle B".toByteArray())

    @Test fun laMemeCleEstCompatible() {
        assertTrue(SignatureMiseAJour.compatibles(setOf(cleA), setOf(cleA)))
    }

    @Test fun uneAutreCleEstRefusee() {
        assertFalse(SignatureMiseAJour.compatibles(setOf(cleA), setOf(cleB)))
    }

    @Test fun uneCleRemplaceeOfficiellementResteCompatible() {
        // la nouvelle signature garde l'ancienne dans son historique (rotation de cle)
        assertTrue(SignatureMiseAJour.compatibles(setOf(cleA), setOf(cleA, cleB)))
    }

    @Test fun unePremiereCleIllisibleNeBloquePas() {
        assertTrue(SignatureMiseAJour.compatibles(emptySet(), setOf(cleB)))
        assertTrue(SignatureMiseAJour.compatibles(setOf(cleA), emptySet()))
    }

    @Test fun lEmpreinteEstUnSha256EnHexadecimal() {
        // SHA-256 de « abc » : valeur de reference de la norme
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            SignatureMiseAJour.empreinte("abc".toByteArray())
        )
        assertEquals(64, cleA.length)
        assertTrue(cleA != cleB)
    }
}
