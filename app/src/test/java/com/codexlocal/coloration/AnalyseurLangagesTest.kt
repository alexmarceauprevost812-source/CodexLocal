package com.codexlocal.coloration

import com.codexlocal.coloration.Genre.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ce que chaque langage colore, sur de petits exemples. */
class AnalyseurLangagesTest {

    private fun exemple(langage: String) = EXEMPLES.first { it.first == langage }.second

    // ------------------------------------------------------------------ Kotlin

    @Test fun kotlin() {
        val c = exemple("kotlin")
        assertEquals(MOT_CLE, genreDe(c, "kotlin", "package"))
        assertEquals(MOT_CLE, genreDe(c, "kotlin", "fun"))
        assertEquals(DECLARATION, genreDe(c, "kotlin", "greet"))
        assertEquals(TYPE, genreDe(c, "kotlin", "String"))
        assertEquals(NOMBRE, genreDe(c, "kotlin", "3"))
        assertEquals(ANNOTATION, genreDe(c, "kotlin", "@Composable"))
        assertEquals(COMMENTAIRE, genreDe(c, "kotlin", "/** doc */"))
        assertEquals(COMMENTAIRE, genreDe(c, "kotlin", "// fin"))
        assertEquals(TEXTE, genreDe(c, "kotlin", "\"Hi "))
        assertEquals(VARIABLE, genreDe(c, "kotlin", "\$name"))
        assertEquals(VARIABLE, genreDe(c, "kotlin", "\${n + 1}"))
        assertEquals(ECHAPPEMENT, genreDe(c, "kotlin", "\\n"))
        assertEquals(DECLARATION, genreDe(c, "kotlin", "x"))
        assertEquals(INTEGRE, genreDe(c, "kotlin", "listOf"))
        assertEquals(NOMBRE, genreDe(c, "kotlin", "0xFF_L"))
        assertEquals(CONSTANTE, genreDe(c, "kotlin", "RED"))
        assertEquals(DECLARATION, genreDe(c, "kotlin", "Color"))
    }

    @Test fun lesAliasDeLangageDonnentLeMemeResultat() {
        val c = exemple("kotlin")
        val reference = AnalyseurCode.analyser(c, "kotlin").map { it.toString() }
        for (alias in listOf("kt", "KOTLIN", ".kt", " kotlin ", "kts", "kotlin title=\"a.kt\"")) {
            assertEquals(alias, reference, AnalyseurCode.analyser(c, alias).map { it.toString() })
        }
    }

    // ------------------------------------------------------------------ autres langages a accolades

    @Test fun python() {
        val c = exemple("python")
        assertEquals(ANNOTATION, genreDe(c, "python", "@dataclass"))
        assertEquals(MOT_CLE, genreDe(c, "python", "class"))
        assertEquals(DECLARATION, genreDe(c, "python", "Foo"))
        assertEquals(DECLARATION, genreDe(c, "python", "run"))
        assertEquals(MOT_CLE, genreDe(c, "python", "self"))
        assertEquals(CONSTANTE, genreDe(c, "python", "True"))
        assertEquals(CONSTANTE, genreDe(c, "python", "None"))
        assertEquals(TEXTE, genreDe(c, "python", "\"\"\"doc\"\"\""))
        assertEquals(COMMENTAIRE, genreDe(c, "python", "# hi"))
        assertEquals(INTEGRE, genreDe(c, "python", "print"))
        assertEquals(NOMBRE, genreDe(c, "python", "5"))
    }

    @Test fun pythonSansAccoladesLeDieseEstUnCommentaire() {
        assertEquals(COMMENTAIRE, genreDe("x = 1  # note", "py", "# note"))
    }

    @Test fun javascript() {
        val c = exemple("javascript")
        assertEquals(MOT_CLE, genreDe(c, "javascript", "const"))
        assertEquals(DECLARATION, genreDe(c, "javascript", "a"))
        assertEquals(DECLARATION, genreDe(c, "javascript", "foo"))
        assertEquals(DECLARATION, genreDe(c, "javascript", "App"))
        assertEquals(NOMBRE, genreDe(c, "javascript", "1.5e3"))
        assertEquals(INTEGRE, genreDe(c, "javascript", "console"))
        assertEquals(FONCTION, genreDe(c, "javascript", "log"))
        assertEquals(ECHAPPEMENT, genreDe(c, "javascript", "\\'"))
        // dans un gabarit `...${ }...` seule la partie entre accolades est du code
        assertEquals(TEXTE, genreDe(c, "javascript", "`x "))
        assertEquals(VARIABLE, genreDe(c, "javascript", "\${b.c(1)}"))
    }

    @Test fun uneCouleurEcriteDansUneChaineEstReconnue() {
        val c = "console.log(\"#FF9A3D\")"
        val jeton = AnalyseurCode.analyser(c, "js").single { it.genre == COULEUR }
        assertEquals("#FF9A3D", c.substring(jeton.debut, jeton.fin))
        assertEquals(0xFFFF9A3D.toInt(), jeton.couleur)
    }

    @Test fun bash() {
        val c = exemple("bash")
        assertEquals(COMMENTAIRE, genreDe(c, "bash", "#!/bin/bash"))
        assertEquals(CONSTANTE, genreDe(c, "bash", "NAME"))
        assertEquals(VARIABLE, genreDe(c, "bash", "\$NAME"))
        assertEquals(INTEGRE, genreDe(c, "bash", "grep"))
        assertEquals(ATTRIBUT, genreDe(c, "bash", "-i"))
        assertEquals(MOT_CLE, genreDe(c, "bash", "for"))
        assertEquals(MOT_CLE, genreDe(c, "bash", "fi"))
        // « *.txt » n'est pas un acces a une propriete
        assertTrue(morceauxDe(c, "bash", PROPRIETE).isEmpty())
    }

    @Test fun c() {
        val c = exemple("c")
        assertEquals(ANNOTATION, genreDe(c, "c", "#include"))
        assertEquals(ANNOTATION, genreDe(c, "c", "#define"))
        assertEquals(TEXTE, genreDe(c, "c", "<stdio.h>"))
        assertEquals(TYPE, genreDe(c, "c", "int"))
        assertEquals(FONCTION, genreDe(c, "c", "main"))
        assertEquals(INTEGRE, genreDe(c, "c", "printf"))
        assertEquals(ECHAPPEMENT, genreDe(c, "c", "\\n"))
        assertEquals(CONSTANTE, genreDe(c, "c", "MAX"))
        assertEquals(TEXTE, genreDe(c, "c", "'x'"))
    }

    @Test fun sqlNeDependPasDeLaCasse() {
        assertEquals(MOT_CLE, genreDe("select a from b where c", "sql", "select"))
        assertEquals(MOT_CLE, genreDe("SELECT a FROM b", "sql", "SELECT"))
        val c = exemple("sql")
        assertEquals(INTEGRE, genreDe(c, "sql", "COUNT"))
        assertEquals(COMMENTAIRE, genreDe(c, "sql", "-- adults"))
        assertEquals(NOMBRE, genreDe(c, "sql", "18"))
    }

    @Test fun rustNeConfondPasLesDureesDeVieAvecDesChaines() {
        val c = exemple("rust").lineSequence().first()
        assertTrue("une duree de vie n'est pas une chaine : ${lire(c, "rust")}", morceauxDe(c, "rust", TEXTE).let { it.size == 1 && it[0] == "\"hi\"" })
        assertEquals(TYPE, genreDe(c, "rust", "Option"))
        assertEquals(INTEGRE, genreDe(c, "rust", "println"))
        assertEquals(CONSTANTE, genreDe(c, "rust", "None"))
    }

    @Test fun json() {
        val c = exemple("json")
        assertEquals(CLE, genreDe(c, "json", "\"name\""))
        assertEquals(TEXTE, genreDe(c, "json", "\"x\""))
        assertEquals(NOMBRE, genreDe(c, "json", "1"))
        assertEquals(NOMBRE, genreDe(c, "json", "2.5"))
        assertEquals(CONSTANTE, genreDe(c, "json", "true"))
        assertEquals(CONSTANTE, genreDe(c, "json", "null"))
        assertEquals(COULEUR, genreDe(c, "json", "#ff0000"))
    }

    @Test fun lesParenthesesChangentDeCouleurSelonLaProfondeur() {
        val c = "((([x])))"
        val genres = AnalyseurCode.analyser(c, "kotlin").map { it.genre }
        assertEquals(listOf(PAREN_1, PAREN_2, PAREN_3, PAREN_1, PAREN_1, PAREN_3, PAREN_2, PAREN_1), genres)
    }

    @Test fun uneParentheseFermanteEnTropNePlantePas() {
        val c = "a) b] c}"
        verifierJetons(c, AnalyseurCode.analyser(c, "kotlin"))
    }

    // ------------------------------------------------------------------ pages web

    @Test fun html() {
        val c = exemple("html")
        assertEquals(ATTRIBUT, genreDe(c, "html", "lang"))
        assertEquals(TEXTE, genreDe(c, "html", "\"fr\""))
        assertEquals(BALISE, genreDe(c, "html", "body"))
        assertEquals(ECHAPPEMENT, genreDe(c, "html", "&amp;"))
        assertEquals(COMMENTAIRE, genreDe(c, "html", "<!-- c -->"))
        // le contenu de <script> est du JavaScript, celui de <style> du CSS, et style="" aussi
        assertEquals(MOT_CLE, genreDe(c, "html", "let"))
        assertEquals(PROPRIETE, genreDe(c, "html", "color"))
        assertEquals(COULEUR, genreDe(c, "html", "#ff0000"))
    }

    @Test fun xmlAndroid() {
        val c = exemple("xml")
        assertEquals(BALISE, genreDe(c, "xml", "LinearLayout"))
        assertEquals(ATTRIBUT, genreDe(c, "xml", "android:id"))
        assertEquals(VARIABLE, genreDe(c, "xml", "@+id/main"))
        assertEquals(VARIABLE, genreDe(c, "xml", "@string/hi"))
        assertEquals(COMMENTAIRE, genreDe(c, "xml", "<!-- c -->"))
        assertEquals(COULEUR, genreDe(c, "xml", "#FF9A3D"))
    }

    @Test fun lesHuitChiffresSeLisentAlphaEnPremierDansAndroidEtALaFinEnCss() {
        val android = AnalyseurCode.analyser("<a c=\"#80FF0000\"/>", "xml").single { it.genre == COULEUR }
        val css = AnalyseurCode.analyser("a { color: #FF000080; }", "css").single { it.genre == COULEUR }
        // meme couleur dans les deux cas : rouge a moitie transparent, melange au fond sombre
        assertEquals(android.couleur, css.couleur)
        assertEquals(CouleursCode.opaque(255, 0, 0, 0x80), android.couleur)
    }

    @Test fun css() {
        val c = exemple("css")
        assertEquals(COMMENTAIRE, genreDe(c, "css", "/* c */"))
        assertEquals(SELECTEUR, genreDe(c, "css", ".btn"))
        assertEquals(SELECTEUR, genreDe(c, "css", "#id"))
        assertEquals(INTEGRE, genreDe(c, "css", ":hover"))
        assertEquals(PROPRIETE, genreDe(c, "css", "color"))
        assertEquals(COULEUR, genreDe(c, "css", "rgba(255, 0, 0, .5)"))
        assertEquals(COULEUR, genreDe(c, "css", "#ff9a3d"))
        assertEquals(NOMBRE, genreDe(c, "css", "-4px"))
        assertEquals(MOT_CLE, genreDe(c, "css", "!important"))
        assertEquals(VARIABLE, genreDe(c, "css", "--main", occurrence = 0))
        assertEquals(MOT_CLE, genreDe(c, "css", "@media"))
        assertEquals(TEXTE, genreDe(c, "css", "\"Arial\""))
        assertEquals(FONCTION, genreDe(c, "css", "var"))
    }

    @Test fun scssEtLessSontLusCommeDuCss() {
        val c = "a { color: #fff; } // note"
        assertEquals(COULEUR, genreDe(c, "scss", "#fff"))
    }

    // ------------------------------------------------------------------ configuration

    @Test fun yaml() {
        val c = exemple("yaml")
        assertEquals(listOf("name", "version", "items", "a", "\"b\"", "run", "next", "ref"), morceauxDe(c, "yaml", CLE))
        assertEquals(NOMBRE, genreDe(c, "yaml", "1.2"))
        assertEquals(CONSTANTE, genreDe(c, "yaml", "true"))
        assertEquals(CONSTANTE, genreDe(c, "yaml", "null"))
        assertEquals(COMMENTAIRE, genreDe(c, "yaml", "# conf"))
        assertEquals(COMMENTAIRE, genreDe(c, "yaml", "# end"))
        assertEquals(VARIABLE, genreDe(c, "yaml", "&anc"))
        assertEquals(VARIABLE, genreDe(c, "yaml", "*anc"))
        // dans un bloc « | », « x: y » est du texte, pas une cle
        assertEquals(TEXTE, genreDe(c, "yaml", "echo \"x: y\""))
    }

    @Test fun toml() {
        val c = exemple("toml")
        assertEquals(TITRE, genreDe(c, "toml", "[package]"))
        assertEquals(CLE, genreDe(c, "toml", "name"))
        assertEquals(TEXTE, genreDe(c, "toml", "\"x\""))
        assertEquals(NOMBRE, genreDe(c, "toml", "1"))
        assertEquals(COMMENTAIRE, genreDe(c, "toml", "# c"))
    }

    @Test fun ini() {
        val c = exemple("ini")
        assertEquals(COMMENTAIRE, genreDe(c, "ini", "; c"))
        assertEquals(TITRE, genreDe(c, "ini", "[sec]"))
        assertEquals(CLE, genreDe(c, "ini", "key"))
        assertEquals(COULEUR, genreDe(c, "ini", "#ff0000"))
        assertEquals(NOMBRE, genreDe(c, "ini", "5"))
    }

    // ------------------------------------------------------------------ texte

    @Test fun markdown() {
        val c = exemple("markdown")
        assertEquals(TITRE, genreDe(c, "markdown", "# Titre"))
        assertEquals(MOT_CLE, genreDe(c, "markdown", "**bold**"))
        assertEquals(TEXTE, genreDe(c, "markdown", "`code`"))
        assertEquals(LIEN, genreDe(c, "markdown", "lien"))
        assertEquals(COMMENTAIRE, genreDe(c, "markdown", "cite"))
        // le bloc ```kotlin est colore comme du Kotlin
        assertEquals(MOT_CLE, genreDe(c, "markdown", "val"))
    }

    @Test fun unLangageInconnuEstLuCommeDuCode() {
        for (nom in listOf("", "zzz", "brainfuck")) {
            val c = exemple("inconnu")
            assertEquals(nom, TEXTE, genreDe(c, nom, "\"bar\""))
            assertEquals(nom, COMMENTAIRE, genreDe(c, nom, "// baz 12"))
        }
    }

    @Test fun dutexteSimpleNEstPasColore() {
        for (nom in listOf("txt", "text", "log", "csv", "plain", "diff")) {
            assertTrue(nom, AnalyseurCode.analyser(exemple("plain"), nom).isEmpty())
        }
    }

    @Test fun lesAutresLangagesColorentAuMoinsLesChainesEtLesCommentaires() {
        val avecChaine = listOf("java", "typescript", "cpp", "csharp", "go", "swift", "dart", "php", "ruby",
            "powershell", "lua", "groovy", "perl", "dockerfile")
        for (nom in avecChaine) {
            val genres = AnalyseurCode.analyser(exemple(nom), nom).map { it.genre }.toSet()
            assertTrue("$nom : pas de MOT_CLE ou TEXTE ($genres)", MOT_CLE in genres || TEXTE in genres)
            assertTrue("$nom : rien d'autre que de la ponctuation ($genres)", genres.any { it != PONCTUATION })
        }
    }

    @Test fun uneChaineNonFermeeNeColoreQueSaLigne() {
        val c = "a = \"ouverte\nb = 2"
        assertEquals(NOMBRE, genreDe(c, "kotlin", "2"))
    }

    @Test fun unCommentaireDeBlocNonFermeCouvreLaFin() {
        val c = "val a = 1 /* jamais ferme\nval b = 2"
        val jeton = AnalyseurCode.analyser(c, "kotlin").last()
        assertEquals(COMMENTAIRE, jeton.genre)
        assertEquals(c.length, jeton.fin)
    }

    @Test fun lesTextesSansCodeNeDonnentRien() {
        assertNull(genreDe("   \n\t ", "kotlin", " "))
        assertTrue(AnalyseurCode.analyser("   \n\t ", "kotlin").isEmpty())
    }
}
