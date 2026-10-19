package com.codexlocal.project

import android.net.Uri
import com.codexlocal.studio.apk.PngTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ImagesProjetTest {

    @get:Rule val dossier = TemporaryFolder()

    private val ctx get() = RuntimeEnvironment.getApplication()

    @Test fun recognisesImagesByExtension() {
        for (nom in listOf("a.png", "dir/B.JPG", "c.jpeg", "d.gif", "images/e.WebP")) {
            assertTrue(nom, ImagesProjet.estImage(nom))
        }
        for (nom in listOf("a.html", "png", "image.svg", "x.png.txt", "")) {
            assertFalse(nom, ImagesProjet.estImage(nom))
        }
    }

    @Test fun safeNamesKeepOnlyHarmlessCharacters() {
        assertEquals("Ma_Photo__1.jpg", ImagesProjet.nomSur("Ma Photo (1).JPG"))
        assertEquals("image.png", ImagesProjet.nomSur(null))
        assertEquals("image.png", ImagesProjet.nomSur("é.png"))
        assertEquals("image.png", ImagesProjet.nomSur(".png"))
        assertEquals("passwd.png", ImagesProjet.nomSur("../../etc/passwd"))
        assertEquals("x.png", ImagesProjet.nomSur("x.exe"))
        assertEquals("logo-2_v.webp", ImagesProjet.nomSur("logo-2_v.webp"))
        assertEquals(40 + ".png".length, ImagesProjet.nomSur("a".repeat(100) + ".png").length)
    }

    @Test fun neverOverwritesAnExistingImage() {
        assertEquals("images/logo.png", ImagesProjet.cheminLibre(emptySet(), "images", "logo.png"))
        assertEquals("images/logo-2.png", ImagesProjet.cheminLibre(setOf("images/logo.png"), "images", "logo.png"))
        assertEquals(
            "images/logo-3.png",
            ImagesProjet.cheminLibre(setOf("images/logo.png", "images/logo-2.png"), "images", "logo.png")
        )
        // La casse ne compte pas (certains stockages ne la distinguent pas).
        assertEquals("images/Logo-2.PNG", ImagesProjet.cheminLibre(setOf("IMAGES/LOGO.PNG"), "images", "Logo.PNG"))
        assertEquals("logo.png", ImagesProjet.cheminLibre(emptySet(), "", "logo.png"))
        assertEquals("readme-2", ImagesProjet.cheminLibre(setOf("readme"), "", "readme"))
    }

    @Test fun importCopiesIntoTheImagesFolderWithoutOverwriting() {
        val projet = ProjectStore.creerProjetInterne(ctx, "img_" + System.nanoTime())
        val png = PngTest.uni(16, 16, 0xFF00AA00.toInt())
        val source = dossier.newFile("photo.png").apply { writeBytes(png) }

        val premier = ImagesProjet.importer(ctx, projet, Uri.fromFile(source), "Mon logo.png")
        assertEquals("images/Mon_logo.png", premier)
        assertArrayEquals(png, File(projet.uri, "images/Mon_logo.png").readBytes())

        val second = ImagesProjet.importer(ctx, projet, Uri.fromFile(source), "Mon logo.png")
        assertEquals("images/Mon_logo-2.png", second)
        assertTrue(File(projet.uri, "images/Mon_logo.png").exists())
    }

    @Test fun refusesFilesThatAreNotImages() {
        val projet = ProjectStore.creerProjetInterne(ctx, "img_" + System.nanoTime())
        val faux = dossier.newFile("faux.png").apply { writeText("ceci n'est pas une image") }
        try {
            ImagesProjet.importer(ctx, projet, Uri.fromFile(faux), "faux.png")
            org.junit.Assert.fail("Un fichier qui n'est pas une image doit etre refuse")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.isNotBlank())
        }
        assertFalse(File(projet.uri, "images/faux.png").exists())
    }

    @Test fun iconIsASquareReplacedAtTheProjectRoot() {
        val projet = ProjectStore.creerProjetInterne(ctx, "img_" + System.nanoTime())
        val source = dossier.newFile("grande.png").apply { writeBytes(PngTest.uni(300, 200, 0xFFFF8800.toInt())) }

        assertEquals("icone.png", ImagesProjet.definirIcone(ctx, projet, Uri.fromFile(source)))
        val octets = File(projet.uri, "icone.png").readBytes()
        val image = android.graphics.BitmapFactory.decodeByteArray(octets, 0, octets.size)
        assertEquals(512, image.width)
        assertEquals(512, image.height)

        // Une seconde icone remplace la premiere.
        val autre = dossier.newFile("autre.png").apply { writeBytes(PngTest.uni(64, 64, 0xFF000000.toInt())) }
        ImagesProjet.definirIcone(ctx, projet, Uri.fromFile(autre))
        assertEquals(1, projet.let { File(it.uri).listFiles()!!.count { f -> f.name == "icone.png" } })
    }
}
