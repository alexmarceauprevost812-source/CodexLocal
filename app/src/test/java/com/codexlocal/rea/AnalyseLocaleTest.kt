package com.codexlocal.rea

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AnalyseLocaleTest {
    private fun zip(vararg e: Pair<String, String>): File {
        val f = File.createTempFile("t", ".zip")
        ZipOutputStream(f.outputStream()).use { z -> e.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return f
    }

    @Test fun inspecteUnZipEtUnTexte() {
        val z = zip("classes.dex" to "dex", "AndroidManifest.xml" to "x", "lib/arm64-v8a/a.so" to "so", "assets/app.js" to "1")
        val r = AnalyseLocale.inspecter(z)
        assertEquals(4, r.getInt("entries"))
        assertEquals(64, r.getString("sha256").length)
        assertTrue(r.getJSONArray("notes").length() >= 0)
        val t = File.createTempFile("t", ".txt").apply { writeText("bonjour") }
        val rt = AnalyseLocale.inspecter(t)
        assertEquals("texte", rt.getString("kind")); assertEquals("bonjour", rt.getString("preview"))
        val b = File.createTempFile("t", ".bin").apply { writeBytes(byteArrayOf(0, 1, 2, 3)) }
        assertEquals("binaire", AnalyseLocale.inspecter(b).getString("kind"))
    }

    @Test fun analyseLeJavaScriptParMotifs() {
        val z = zip("www/app.js" to "eval('x'); fetch('https://api.exemple.org/v1'); localStorage.a=1;", "www/index.html" to "<script>document.write('a')</script>", "img.png" to "p")
        val r = AnalyseLocale.javascript(z)
        assertEquals(2, r.getInt("files_analyzed"))
        val c = r.getJSONObject("pattern_counts")
        assertEquals(1, c.getInt("eval")); assertEquals(1, c.getInt("fetch")); assertEquals(1, c.getInt("document_write"))
        assertTrue(r.getJSONObject("domains").has("api.exemple.org"))
    }

    @Test fun signatureInvalideLeveUneErreurPourLeModele() {
        assertTrue(runCatching { AnalyseLocale.signature(zip("a" to "b")) }.let { it.isFailure || !it.getOrThrow().getBoolean("verified") })
    }

    @Test fun nomsSursEtCatalogue() {
        assertEquals("mon_app.apk", ReaLocal.nomSur("mon app.apk"))
        assertEquals("fichier", ReaLocal.nomSur("../.."))
        val noms = (0 until ReaLocal.CATALOGUE.length()).map { ReaLocal.CATALOGUE.getJSONObject(it).getString("name") }
        assertTrue(ReaProtocol.tools.containsAll(noms))
        assertTrue(ReaProtocol.consigne("[C]", local = true).contains("téléphone"))
        assertTrue(ReaProtocol.consigne("[C]").contains("PC"))
    }
}
