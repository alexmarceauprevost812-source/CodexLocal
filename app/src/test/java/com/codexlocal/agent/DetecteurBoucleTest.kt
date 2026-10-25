package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DetecteurBoucleTest {

    private val ligne = "EXACT DEPLOYAGE : 0 # N'oubliez pas de definir la date"

    @Test
    fun `une meme ligne repetee est une boucle`() {
        val texte = "Voici le fichier :\n$ligne\n$ligne\n$ligne\n"
        val garder = DetecteurBoucle.boucle(texte)
        assertNotNull(garder)
        assertEquals("Voici le fichier :\n$ligne\n", texte.substring(0, garder!!))
    }

    @Test
    fun `un groupe de deux lignes repete est une boucle`() {
        val a = "EXACT NAME : Kali Linux # nom du fichier"
        val b = "EXACT VERSION : 1.0.0 # version"
        val texte = "debut\n$a\n$b\n$a\n$b\n$a\n$b\n"
        val garder = DetecteurBoucle.boucle(texte)
        assertEquals("debut\n$a\n$b\n", texte.substring(0, garder!!))
    }

    @Test
    fun `les accolades et balises courtes ne declenchent rien`() {
        val texte = "fun a() {\n  if (x) {\n    y()\n  }\n}\n}\n}\n</div>\n</div>\n</div>\n"
        assertNull(DetecteurBoucle.boucle(texte))
    }

    @Test
    fun `deux repetitions seulement ne suffisent pas`() {
        assertNull(DetecteurBoucle.boucle("$ligne\n$ligne\n"))
    }

    @Test
    fun `la ligne en cours d'ecriture est ignoree`() {
        assertNull(DetecteurBoucle.boucle("$ligne\n$ligne\n$ligne"))
    }

    @Test
    fun `du code normal ne boucle pas`() {
        val code = """
            import os
            def lire(chemin):
                with open(chemin) as f:
                    return f.read()
            def ecrire(chemin, texte):
                with open(chemin, "w") as f:
                    f.write(texte)
        """.trimIndent() + "\n"
        assertNull(DetecteurBoucle.boucle(code))
    }
}
