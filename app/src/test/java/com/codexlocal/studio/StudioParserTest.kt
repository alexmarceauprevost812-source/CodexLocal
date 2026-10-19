package com.codexlocal.studio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudioParserTest {

    @Test fun readsSeveralFilesAndStripsFences() {
        val reponse = """
            === FICHIER: index.html ===
            <h1>Salut</h1>

            === FICHIER: src/app.js ===
            ```js
            console.log(1)
            ```
            === FICHIER: `./css/style.css` ===
            body { color: red }
        """.trimIndent()
        val fichiers = StudioParser.extraireFichiers(reponse)
        assertEquals(listOf("index.html", "src/app.js", "css/style.css"), fichiers.map { it.chemin })
        assertEquals("<h1>Salut</h1>\n", fichiers[0].contenu)
        assertEquals("console.log(1)\n", fichiers[1].contenu)
        assertEquals("body { color: red }\n", fichiers[2].contenu)
    }

    @Test fun ignoresTextBeforeFirstMarkerAndEmptyAnswers() {
        assertTrue(StudioParser.extraireFichiers("Voici du texte sans fichier").isEmpty())
        val f = StudioParser.extraireFichiers("intro\n=== FICHIER: a.txt ===\nx")
        assertEquals(1, f.size)
        assertEquals("x\n", f[0].contenu)
    }

    @Test fun limitsTheNumberOfFiles() {
        val reponse = (1..50).joinToString("\n") { "=== FICHIER: f$it.txt ===\n$it" }
        assertEquals(StudioParser.MAX_FICHIERS, StudioParser.extraireFichiers(reponse).size)
    }

    @Test fun extractsAndSanitizesSvg() {
        val reponse = """
            Voici :
            <svg viewBox="0 0 10 10" onload="alert(1)">
              <script>alert(2)</script>
              <image href="http://exemple.com/x.png"/>
              <a href="javascript:alert(3)"><circle cx="5" cy="5" r="4" fill="orange"/></a>
            </svg>
        """.trimIndent()
        val svg = StudioParser.extraireSvg(reponse)
        assertNotNull(svg)
        svg!!
        assertTrue(svg.contains("<circle"))
        assertTrue(svg.contains("xmlns=\"http://www.w3.org/2000/svg\""))
        assertFalse(svg.contains("script", ignoreCase = true))
        assertFalse(svg.contains("onload", ignoreCase = true))
        assertFalse(svg.contains("javascript", ignoreCase = true))
        assertFalse(svg.contains("<image", ignoreCase = true))
    }

    @Test fun noSvgMeansNull() {
        assertNull(StudioParser.extraireSvg("Désolé, je ne peux pas."))
    }
}
