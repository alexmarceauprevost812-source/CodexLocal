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

    @Test fun recognisesTheLauncherIconsBySizeNotByName() {
        val png48 = PngTest.uni(48, 48, 0xFF112233.toInt())
        assertEquals(48, IconesApk.taillePourEntree("res/-B.png", png48))      // chemin raccourci par Gradle
        assertEquals(48, IconesApk.taillePourEntree("res/mipmap-mdpi/ic_launcher.png", png48))
        assertNull(IconesApk.taillePourEntree("assets/www/images/logo.png", png48))   // image du projet : jamais
        assertNull(IconesApk.taillePourEntree("res/-B.xml", png48))
        assertNull(IconesApk.taillePourEntree("res/-B.png", PngTest.uni(48, 50, 0xFF112233.toInt())))   // pas carree
        assertNull(IconesApk.taillePourEntree("res/-B.png", PngTest.uni(64, 64, 0xFF112233.toInt())))   // taille inconnue
        assertNull(IconesApk.taillePourEntree("res/-B.png", "pas un png".toByteArray()))
        IconesApk.TAILLES.values.forEach {
            assertEquals(it, IconesApk.taillePourEntree("res/a.png", PngTest.uni(it, it, 0xFF000000.toInt())))
        }
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
        val iconesModele = ApkZip.lire(modele).filter { IconesApk.taillePourEntree(it.nom, it.octets) != null }
        assertEquals(
            "Le modele a cinq icones, une par taille",
            IconesApk.TAILLES.values.toSet(),
            iconesModele.map { IconesApk.taillePourEntree(it.nom, it.octets) }.toSet()
        )
        assertEquals(5, iconesModele.size)

        val sortie = ApkZip.lire(octets).associateBy { it.nom }
        for (origine in iconesModele) {
            val taille = IconesApk.taillePourEntree(origine.nom, origine.octets)!!
            val nouvelle = sortie.getValue(origine.nom).octets
            val image = BitmapFactory.decodeByteArray(nouvelle, 0, nouvelle.size)
            assertEquals(origine.nom, taille, image.width)
            assertEquals(origine.nom, bleu, image.getPixel(taille / 2, taille / 2))
            assertTrue("${origine.nom} doit differer de l'icone du modele", !nouvelle.contentEquals(origine.octets))
        }
    }
}
