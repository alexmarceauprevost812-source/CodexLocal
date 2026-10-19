package com.codexlocal.studio.apk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ManifesteBinaireTest {

    private val repereId = "com.codexlocal.app.p00000000"
    private val repereNom = "CXLNOMAPPPLACEHOLDER0000"
    private val remplacements = mapOf(
        repereId to "com.codexlocal.app.pabcdef12",
        repereNom to "Mon Jeu"
    )

    private fun pourChaquePool(test: (utf8: Boolean) -> Unit) {
        test(true)
        test(false)
    }

    @Test fun readsTheStringTableOfBothEncodings() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom, autres = listOf("Café ✨"))
        assertEquals(
            listOf("versionCode", "label", "package", "manifest", "application", repereId, repereNom, "Café ✨"),
            ManifesteBinaire.lireChaines(axml)
        )
    }

    @Test fun replacesIdAndNameEverywhereTheyAreReferenced() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom)
        val resultat = ManifesteBinaire.retoucher(axml, remplacements)
        val chaines = ManifesteBinaire.lireChaines(resultat.octets)

        assertEquals(setOf(repereId, repereNom), resultat.remplacees)
        assertTrue("com.codexlocal.app.pabcdef12" in chaines && "Mon Jeu" in chaines)
        assertFalse(repereId in chaines || repereNom in chaines)

        // Les attributs pointent toujours vers la bonne chaine (reference par numero).
        val manifest = OutilsAxml.attributs(resultat.octets, chaines, "manifest")
        assertEquals("com.codexlocal.app.pabcdef12", chaines[manifest.getValue("package").donnee])
        val application = OutilsAxml.attributs(resultat.octets, chaines, "application")
        assertEquals("Mon Jeu", chaines[application.getValue("label").donnee])
    }

    @Test fun writesTheVersionCodeAsAPlainInteger() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom)
        val resultat = ManifesteBinaire.retoucher(axml, remplacements, versionCode = 29_123_456)
        val chaines = ManifesteBinaire.lireChaines(resultat.octets)
        val version = OutilsAxml.attributs(resultat.octets, chaines, "manifest").getValue("versionCode")
        assertEquals(0x10, version.typeValeur)
        assertEquals(29_123_456, version.donnee)
        assertEquals(-1, version.brut)
    }

    @Test fun keepsTheVersionCodeWhenNoneIsGiven() = pourChaquePool { utf8 ->
        val resultat = ManifesteBinaire.retoucher(OutilsAxml.fabriquer(utf8, repereId, repereNom), remplacements)
        val chaines = ManifesteBinaire.lireChaines(resultat.octets)
        assertEquals(1, OutilsAxml.attributs(resultat.octets, chaines, "manifest").getValue("versionCode").donnee)
    }

    @Test fun sizesStayConsistentWhenStringsGrowOrShrink() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom)
        for (nom in listOf("A", "Un nom bien plus long que le repere d'origine !!", "x".repeat(200))) {
            val resultat = ManifesteBinaire.retoucher(axml, mapOf(repereId to "a.b", repereNom to nom))
            // blocs() verifie la taille annoncee et que la suite des blocs tombe pile en fin de fichier.
            val blocs = OutilsAxml.blocs(resultat.octets)
            assertEquals(listOf(0x0180, 0x0102, 0x0102, 0x0103, 0x0103), blocs.map { it.first })
            assertEquals(0, OutilsAxml.tailleTable(resultat.octets) % 4)
            assertTrue(nom in ManifesteBinaire.lireChaines(resultat.octets))
        }
    }

    @Test fun keepsTheResourceMapAndTheOtherBlocksUntouched() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom)
        val resultat = ManifesteBinaire.retoucher(axml, remplacements)
        assertEquals(listOf(0x0101021b, 0x01010001), OutilsAxml.cartesRessources(resultat.octets))
        // Tout ce qui suit la table est identique (hors numero de version, non demande ici).
        val avant = axml.copyOfRange(8 + OutilsAxml.tailleTable(axml), axml.size)
        val apres = resultat.octets.copyOfRange(8 + OutilsAxml.tailleTable(resultat.octets), resultat.octets.size)
        assertArrayEquals(avant, apres)
    }

    @Test fun clearsTheSortedFlagBecauseOrderMayChange() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom, trie = true)
        assertEquals(1, OutilsAxml.drapeauxTable(axml) and 1)
        val resultat = ManifesteBinaire.retoucher(axml, remplacements)
        assertEquals(0, OutilsAxml.drapeauxTable(resultat.octets) and 1)
        // L'encodage (UTF-8 ou UTF-16) est conserve.
        assertEquals(if (utf8) 0x100 else 0, OutilsAxml.drapeauxTable(resultat.octets) and 0x100)
    }

    @Test fun roundTripsAccentsAndLongStrings() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom)
        val longue = "é".repeat(150)      // 150 caracteres, 300 octets en UTF-8 : longueur sur deux octets
        val resultat = ManifesteBinaire.retoucher(axml, mapOf(repereId to "a.b", repereNom to longue))
        assertTrue(longue in ManifesteBinaire.lireChaines(resultat.octets))
        val court = ManifesteBinaire.retoucher(axml, mapOf(repereNom to "Café ✨ déjà"))
        assertTrue("Café ✨ déjà" in ManifesteBinaire.lireChaines(court.octets))
    }

    @Test fun reportsWhatItDidNotFind() = pourChaquePool { utf8 ->
        val axml = OutilsAxml.fabriquer(utf8, repereId, repereNom)
        val resultat = ManifesteBinaire.retoucher(axml, mapOf("absent" to "x", repereNom to "Nom"))
        assertEquals(setOf(repereNom), resultat.remplacees)
        assertTrue(repereId in ManifesteBinaire.lireChaines(resultat.octets))
    }

    @Test fun refusesFilesThatAreNotABinaryManifest() {
        for (faux in listOf(ByteArray(0), ByteArray(100), "<manifest/>".repeat(10).toByteArray())) {
            try {
                ManifesteBinaire.retoucher(faux, remplacements)
                fail("Un fichier qui n'est pas un manifeste binaire doit etre refuse")
            } catch (e: IllegalArgumentException) {
                assertTrue(e.message!!.isNotBlank())
            }
        }
    }
}
