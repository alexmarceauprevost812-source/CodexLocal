package com.codexlocal.studio.apk

import com.android.apksig.ApkVerifier
import com.codexlocal.project.ProjectStore
import com.codexlocal.project.Projet
import com.codexlocal.studio.CleStudio
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Utilise le vrai modele d'application (module :shell) livre dans les assets de CodexLocal. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ConstructeurApkTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun modele(): ByteArray = ctx.assets.open(ConstructeurApk.MODELE).use { it.readBytes() }

    private fun fichier(chemin: String, texte: String) =
        ProjectStore.EntreeFichier(chemin, texte.toByteArray())

    @Test fun identifierIsStablePerProjectAndLooksLikeAPackageName() {
        val a = Projet("A", "/data/projets/a", 0, true)
        val b = Projet("B", "/data/projets/b", 0, true)
        assertEquals(ConstructeurApk.identifiantPour(a), ConstructeurApk.identifiantPour(a.copy(nom = "Renomme")))
        assertNotEquals(ConstructeurApk.identifiantPour(a), ConstructeurApk.identifiantPour(b))
        assertTrue(Regex("com\\.codexlocal\\.app\\.p[0-9a-f]{8}").matches(ConstructeurApk.identifiantPour(a)))
        // Meme forme que le repere du modele.
        assertEquals(ConstructeurApk.REPERE_ID.length, ConstructeurApk.identifiantPour(a).length)
    }

    @Test fun appNameComesFromTheProjectName() {
        assertEquals("mon super jeu", ConstructeurApk.nomAppli("mon_super_jeu"))
        assertEquals(30, ConstructeurApk.nomAppli("x".repeat(100)).length)
        assertEquals("Mon appli", ConstructeurApk.nomAppli("   "))
        assertEquals("Mon appli", ConstructeurApk.nomAppli("\u0000\u0007"))
        assertEquals("Café ✨", ConstructeurApk.nomAppli("Café ✨"))
    }

    @Test fun templateIsShippedWithBothPlaceholders() {
        val entrees = ApkZip.lire(modele()).associateBy { it.nom }
        for (nom in listOf("AndroidManifest.xml", "classes.dex", "resources.arsc", "assets/www/index.html")) {
            assertTrue("Le modele doit contenir $nom", nom in entrees)
        }
        val chaines = ManifesteBinaire.lireChaines(entrees.getValue("AndroidManifest.xml").octets)
        assertTrue(ConstructeurApk.REPERE_ID in chaines)
        assertTrue(ConstructeurApk.REPERE_NOM in chaines)
        assertFalse("Le modele ne doit demander aucune permission reseau", "android.permission.INTERNET" in chaines)
    }

    @Test fun refusesAProjectWithoutAHomePage() {
        try {
            ConstructeurApk.assembler(modele(), listOf(fichier("app.js", "1")), "N", "com.x.y", 1)
            fail("index.html est obligatoire")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("index.html"))
        }
    }

    @Test fun assemblesTheTemplateAndTheProject() {
        val identifiant = "com.codexlocal.app.pabcdef12"
        val image = PngTest.uni(4, 4, 0xFFFF0000.toInt())
        val octets = ConstructeurApk.assembler(
            modele(),
            listOf(
                fichier("index.html", "<h1>salut</h1>"),
                fichier("css/style.css", "body{color:red}"),
                ProjectStore.EntreeFichier("images/rouge.png", image)
            ),
            "Mon Jeu", identifiant, 4242
        )
        val entrees = ApkZip.lire(octets).associateBy { it.nom }

        // Le projet est la : tel quel, et seulement lui dans assets/www (la page d'exemple du modele a disparu).
        assertEquals(
            setOf("assets/www/index.html", "assets/www/css/style.css", "assets/www/images/rouge.png"),
            entrees.keys.filter { it.startsWith("assets/www/") }.toSet()
        )
        assertEquals("<h1>salut</h1>", String(entrees.getValue("assets/www/index.html").octets))
        assertArrayEquals(image, entrees.getValue("assets/www/images/rouge.png").octets)

        // Les signatures du modele sont retirees : la signature du Studio les remplace.
        assertTrue(entrees.keys.none { it.startsWith("META-INF/") })

        // Le code du modele est intact.
        val origine = ApkZip.lire(modele()).associateBy { it.nom }
        assertArrayEquals(origine.getValue("classes.dex").octets, entrees.getValue("classes.dex").octets)
        assertArrayEquals(origine.getValue("resources.arsc").octets, entrees.getValue("resources.arsc").octets)

        // Identifiant, nom et version sont dans le manifeste.
        val manifeste = entrees.getValue("AndroidManifest.xml").octets
        val chaines = ManifesteBinaire.lireChaines(manifeste)
        assertTrue(identifiant in chaines && "Mon Jeu" in chaines)
        assertFalse(ConstructeurApk.REPERE_ID in chaines || ConstructeurApk.REPERE_NOM in chaines)
        val racine = OutilsAxml.attributs(manifeste, chaines, "manifest")
        assertEquals(identifiant, chaines[racine.getValue("package").donnee])
        assertEquals(4242, racine.getValue("versionCode").donnee)
        val application = OutilsAxml.attributs(manifeste, chaines, "application")
        assertEquals("Mon Jeu", chaines[application.getValue("label").donnee])

        // Android 11+ : resources.arsc non compresse, et toute entree stockee alignee sur 4 octets.
        val structure = OutilsZip.structure(octets)
        assertEquals(0, structure.first { it.nom == "resources.arsc" }.methode)
        structure.filter { it.methode == 0 }.forEach {
            assertEquals("${it.nom} doit etre aligne", 0, it.debutDonnees % 4)
        }
    }

    @Test fun buildsAndSignsARealApkFromAProject() {
        val projet = ProjectStore.creerProjetInterne(ctx, "Mon_Jeu_" + System.nanoTime())
        File(projet.uri, "index.html").writeText("<h1>x</h1>")
        File(projet.uri, "js").mkdirs()
        File(projet.uri, "js/app.js").writeText("console.log(1);")
        assertFalse(CleStudio.existe(ctx))

        val journal = ArrayList<String>()
        val resultat = ConstructeurApk.construire(ctx, projet) { journal.add(it) }

        assertTrue(resultat.fichier.length() > 10_000)
        assertEquals(ConstructeurApk.identifiantPour(projet), resultat.identifiant)
        assertEquals(3, resultat.nbFichiers)            // README.md, index.html, js/app.js
        assertTrue("La cle est creee au premier APK", CleStudio.existe(ctx))
        assertTrue(journal.any { it.contains("Signature") })
        assertFalse("Le fichier non signe est supprime",
            File(ctx.cacheDir, "studio_apk/${resultat.identifiant}-brut.apk").exists())

        // Verification par apksig, comme le fait Android avant d'installer.
        val verification = ApkVerifier.Builder(resultat.fichier).build().verify()
        assertTrue(verification.errors.joinToString(), verification.isVerified)
        assertTrue(verification.isVerifiedUsingV2Scheme)
        assertEquals(resultat.empreinte, CleStudio.empreinte(verification.signerCertificates.single()))

        // Apres signature, l'alignement est toujours bon.
        val structure = OutilsZip.structure(resultat.fichier.readBytes())
        assertEquals(0, structure.first { it.nom == "resources.arsc" }.methode)
        structure.filter { it.methode == 0 }.forEach {
            assertEquals("${it.nom} doit etre aligne", 0, it.debutDonnees % 4)
        }
        assertTrue(structure.any { it.nom == "assets/www/js/app.js" })

        // Une seconde construction reutilise la meme cle et le meme identifiant.
        val encore = ConstructeurApk.construire(ctx, projet)
        assertEquals(resultat.identifiant, encore.identifiant)
        assertEquals(resultat.empreinte, encore.empreinte)
        assertTrue(encore.versionCode >= resultat.versionCode)
    }

    @Test fun buildStopsClearlyWithoutIndexHtml() {
        val projet = ProjectStore.creerProjetInterne(ctx, "Sans_page_" + System.nanoTime())
        try {
            ConstructeurApk.construire(ctx, projet)
            fail("Sans index.html la construction doit s'arreter")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("index.html"))
        }
        assertFalse("Aucune cle inutile n'est creee", CleStudio.existe(ctx))
    }
}
