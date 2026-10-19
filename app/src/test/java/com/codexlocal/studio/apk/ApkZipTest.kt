package com.codexlocal.studio.apk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.Random
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

class ApkZipTest {

    @get:Rule val dossier = TemporaryFolder()

    private fun ecrire(entrees: List<ApkZip.Entree>): ByteArray =
        ByteArrayOutputStream().also { ApkZip.ecrire(it, entrees) }.toByteArray()

    private fun texte(nom: String, contenu: String) =
        ApkZip.Entree(nom, contenu.toByteArray(), ApkZip.doitEtreCompresse(nom))

    @Test fun roundTripKeepsNamesOrderAndContents() {
        val aleatoire = ByteArray(3000).also { Random(7).nextBytes(it) }
        val entrees = listOf(
            texte("AndroidManifest.xml", "<manifest/>".repeat(50)),
            ApkZip.Entree("resources.arsc", ByteArray(1000) { (it % 7).toByte() }, false),
            texte("assets/www/index.html", "<h1>Bonjour</h1>".repeat(40)),
            ApkZip.Entree("assets/www/images/logo.png", aleatoire, false),
            texte("assets/www/vide.txt", "")
        )
        val relus = ApkZip.lire(ecrire(entrees))
        assertEquals(entrees.map { it.nom }, relus.map { it.nom })
        entrees.zip(relus).forEach { (voulu, lu) -> assertArrayEquals(voulu.nom, voulu.octets, lu.octets) }
    }

    @Test fun storedEntriesAreAlignedOnFourBytesWhateverTheNameLength() {
        val entrees = listOf("a.png", "bb.png", "ccc.png", "dddd.png", "eeeee.png", "resources.arsc", "res/x.png")
            .map { ApkZip.Entree(it, ByteArray(37) { b -> b.toByte() }, false) } +
            texte("zz.txt", "z".repeat(500))
        val infos = OutilsZip.structure(ecrire(entrees))
        assertEquals(entrees.size, infos.size)
        infos.filter { it.methode == 0 }.also { assertTrue(it.size >= 7) }.forEach {
            assertEquals("${it.nom} doit commencer sur un multiple de 4", 0, it.debutDonnees % 4)
        }
    }

    @Test fun resourcesArscStaysStoredEvenWhenItWouldCompressWell() {
        val infos = OutilsZip.structure(ecrire(listOf(ApkZip.Entree("resources.arsc", ByteArray(20_000), true))))
        assertEquals(0, infos.single().methode)
        assertEquals(0, infos.single().debutDonnees % 4)
    }

    @Test fun compressionIsUsedOnlyWhenItHelps() {
        val bien = texte("assets/www/index.html", "<p>bonjour</p>".repeat(300))
        val mal = ApkZip.Entree("data.bin", ByteArray(200).also { Random(3).nextBytes(it) }, true)
        val infos = OutilsZip.structure(ecrire(listOf(bien, mal))).associateBy { it.nom }
        assertEquals(8, infos.getValue("assets/www/index.html").methode)
        assertTrue(infos.getValue("assets/www/index.html").tailleStockee < bien.octets.size / 4)
        assertEquals("Des octets aleatoires restent stockes", 0, infos.getValue("data.bin").methode)
        assertEquals(0, infos.getValue("data.bin").debutDonnees % 4)
    }

    @Test fun theJdkCanOpenAndReadTheResult() {
        val entrees = listOf(
            texte("AndroidManifest.xml", "x".repeat(900)),
            ApkZip.Entree("resources.arsc", ByteArray(333) { it.toByte() }, false),
            texte("assets/www/é à ü ✨.html", "<b>accents</b>".repeat(20)),
            texte("assets/www/vide.txt", "")
        )
        val fichier = dossier.newFile("test.apk").apply { writeBytes(ecrire(entrees)) }
        ZipFile(fichier).use { zip ->
            assertEquals(entrees.size, zip.size())
            for (e in entrees) {
                val entree = zip.getEntry(e.nom)
                assertEquals(e.nom, e.nom, entree?.name)
                assertArrayEquals(e.nom, e.octets, zip.getInputStream(entree).readBytes())
            }
        }
    }

    @Test fun duplicateNamesAreRefused() {
        try {
            ecrire(listOf(texte("a.txt", "1"), texte("a.txt", "2")))
            fail("Un nom en double doit etre refuse")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("a.txt"))
        }
    }

    @Test fun readingSkipsFolders() {
        val zip = ByteArrayOutputStream().also { sortie ->
            ZipOutputStream(sortie).use { z ->
                z.putNextEntry(ZipEntry("assets/")); z.closeEntry()
                z.putNextEntry(ZipEntry("assets/a.txt")); z.write("a".toByteArray()); z.closeEntry()
            }
        }.toByteArray()
        assertEquals(listOf("assets/a.txt"), ApkZip.lire(zip).map { it.nom })
    }

    @Test fun paddingAlwaysLandsOnAMultipleOfFour() {
        for (position in 0L..40L) {
            val bourrage = ApkZip.bourrage(position)
            assertEquals("position $position", 0L, (position + bourrage) % 4)
            // Un champ « extra » mesure au moins 4 octets : jamais 1, 2 ou 3.
            assertFalse("position $position : $bourrage", bourrage in 1..3)
        }
    }

    @Test fun compressionChoiceFollowsTheFileType() {
        assertFalse(ApkZip.doitEtreCompresse("resources.arsc"))
        assertFalse(ApkZip.doitEtreCompresse("assets/www/images/Logo.PNG"))
        assertFalse(ApkZip.doitEtreCompresse("a.webp"))
        assertTrue(ApkZip.doitEtreCompresse("assets/www/index.html"))
        assertTrue(ApkZip.doitEtreCompresse("classes.dex"))
        assertTrue(ApkZip.doitEtreCompresse("AndroidManifest.xml"))
    }
}
