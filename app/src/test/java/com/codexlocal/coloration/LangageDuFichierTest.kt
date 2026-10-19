package com.codexlocal.coloration

import com.codexlocal.project.Langages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Du nom d'un fichier jusqu'au profil de coloration. */
class LangageDuFichierTest {

    private fun profil(nomFichier: String) = Profils.normaliser(Langages.pourColoration(nomFichier))

    @Test fun lesExtensionsCourantes() {
        assertEquals("kotlin", profil("MainActivity.kt"))
        assertEquals("kotlin", profil("build.gradle.kts"))
        assertEquals("groovy", profil("app/build.gradle"))
        assertEquals("java", profil("src/App.java"))
        assertEquals("python", profil("script.py"))
        assertEquals("javascript", profil("index.js"))
        assertEquals("typescript", profil("index.tsx"))
        assertEquals("cpp", profil("lib/ai_chat.cpp"))
        assertEquals("html", profil("index.html"))
        assertEquals("html", profil("page.htm"))
        assertEquals("xml", profil("res/layout/activity_main.xml"))
        assertEquals("css", profil("style.css"))
        assertEquals("css", profil("style.scss"))
        assertEquals("json", profil("package.json"))
        assertEquals("yaml", profil(".github/workflows/android-apk.yml"))
        assertEquals("markdown", profil("README.md"))
        assertEquals("ini", profil("gradle.properties"))
        assertEquals("ini", profil(".env"))
        assertEquals("bash", profil("gradlew.sh"))
    }

    @Test fun lesFichiersSansExtensionSontReconnusParLeurNom() {
        assertEquals("dockerfile", profil("Dockerfile"))
        assertEquals("dockerfile", profil("docker/Dockerfile.dev"))
        assertEquals("makefile", profil("Makefile"))
        assertEquals("cmake", profil("lib/CMakeLists.txt"))
        assertEquals("cmake", profil("cmake/outils.cmake"))
    }

    @Test fun lesTextesSimplesNeSontPasColores() {
        for (nom in listOf("notes.txt", "data.csv", "app.log", ".gitignore", "x.diff")) {
            assertEquals(nom, "plain", profil(nom))
            assertTrue(nom, AnalyseurCode.analyser("une ligne \"avec\" 12 # des signes", Langages.pourColoration(nom)).isEmpty())
        }
    }

    @Test fun unNomInconnuDonneLeProfilGenerique() {
        assertEquals("zzz", profil("fichier.zzz"))
        assertEquals("", profil("LISEZMOI"))
        assertTrue(AnalyseurCode.analyser("a(\"b\") // c", Langages.pourColoration("fichier.zzz")).isNotEmpty())
    }
}
