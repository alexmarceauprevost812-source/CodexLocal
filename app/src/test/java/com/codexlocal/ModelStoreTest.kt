package com.codexlocal

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ModelStoreTest {

    @get:Rule val dossier = TemporaryFolder()

    @Test fun recognizesGgufMagicOnly() {
        val bon = dossier.newFile("bon.gguf").apply { writeBytes("GGUF".toByteArray() + ByteArray(32)) }
        val html = dossier.newFile("page.gguf").apply { writeText("<html>erreur 404</html>") }
        val vide = dossier.newFile("vide.gguf")
        val court = dossier.newFile("court.gguf").apply { writeText("GG") }
        assertTrue(ModelStore.estGguf(bon))
        assertFalse(ModelStore.estGguf(html))
        assertFalse(ModelStore.estGguf(vide))
        assertFalse(ModelStore.estGguf(court))
        assertFalse(ModelStore.estGguf(dossier.root.resolve("absent.gguf")))
    }
}
