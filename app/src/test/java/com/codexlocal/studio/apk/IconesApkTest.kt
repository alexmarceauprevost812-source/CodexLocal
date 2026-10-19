package com.codexlocal.studio.apk

import android.graphics.BitmapFactory
import com.codexlocal.project.ProjectStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class IconesApkTest {

    @Test fun recognisesTheLauncherIconsOfTheTemplate() {
        assertEquals("mdpi", IconesApk.densite("res/mipmap-mdpi/ic_launcher.png"))
        assertEquals("xhdpi", IconesApk.densite("res/mipmap-xhdpi/ic_launcher.png"))
        assertEquals("xxxhdpi", IconesApk.densite("res/mipmap-xxxhdpi-v4/ic_launcher.png"))
        assertNull(IconesApk.densite("res/mipmap-anydpi-v26/ic_launcher.xml"))
        assertNull(IconesApk.densite("res/mipmap-hdpi/ic_autre.png"))
        assertNull(IconesApk.densite("assets/www/res/mipmap-hdpi/ic_launcher.png"))
        // Chaque densite reconnue a une taille.
        listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi").forEach { assertNotNull(IconesApk.TAILLES[it]) }
    }

    @Test fun picksTheIconByOrderOfPreference() {
        assertEquals("icone.png", IconesApk.trouver(listOf("index.html", "icone.png", "icon.png")))
        assertEquals("icon.png", IconesApk.trouver(listOf("images/icone.png", "icon.png")))
        assertEquals("images/icone.png", IconesApk.trouver(listOf("images/icone.png", "images/autre.png")))
        assertNull(IconesApk.trouver(listOf("index.html", "images/logo.png")))
    }

    @Test fun cropsToASquareAndResizes() {
        val rouge = 0xFFFF0000.toInt()
        val png = IconesApk.versPng(PngTest.uni(100, 60, rouge), 48)
        assertNotNull(png)
        val image = BitmapFactory.decodeByteArray(png, 0, png!!.size)
        assertEquals(48, image.width)
        assertEquals(48, image.height)
        assertEquals(rouge, image.getPixel(24, 24))
    }

    @Test fun unreadableImageGivesNull() {
        assertNull(IconesApk.versPng("pas une image".toByteArray(), 48))
    }

    @Test fun theProjectIconReplacesEveryLauncherIconOfTheTemplate() {
        val ctx = RuntimeEnvironment.getApplication()
        val modele = ctx.assets.open(ConstructeurApk.MODELE).use { it.readBytes() }
        val bleu = 0xFF0000FF.toInt()
        val octets = ConstructeurApk.assembler(
            modele,
            listOf(
                ProjectStore.EntreeFichier("index.html", "<p>x</p>".toByteArray()),
                ProjectStore.EntreeFichier("icone.png", PngTest.uni(300, 300, bleu))
            ),
            "Icone", "com.codexlocal.app.pabcdef12", 1
        )
        val origine = ApkZip.lire(modele).associateBy { it.nom }
        val icones = ApkZip.lire(octets).filter { IconesApk.densite(it.nom) != null }
        assertEquals("Les 5 densites du modele", 5, icones.size)
        for (icone in icones) {
            val taille = IconesApk.TAILLES.getValue(IconesApk.densite(icone.nom)!!)
            val image = BitmapFactory.decodeByteArray(icone.octets, 0, icone.octets.size)
            assertEquals(icone.nom, taille, image.width)
            assertEquals(icone.nom, bleu, image.getPixel(taille / 2, taille / 2))
            assertTrue("${icone.nom} doit differer de l'icone du modele",
                !icone.octets.contentEquals(origine.getValue(icone.nom).octets))
        }
    }
}
