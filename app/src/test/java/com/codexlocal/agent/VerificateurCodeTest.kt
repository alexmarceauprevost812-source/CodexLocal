package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerificateurCodeTest {

    private fun verifier(
        chemin: String, texte: String,
        existants: Set<String> = emptySet(),
        anciens: Map<String, String> = emptyMap()
    ) = VerificateurCode.verifier(mapOf(chemin to texte), existants, anciens)

    private fun ok(chemin: String, texte: String, existants: Set<String> = emptySet()) =
        assertEquals(emptyList<Probleme>(), verifier(chemin, texte, existants))

    // ------------------------------------------------------------ equilibre : pas de fausse alerte

    @Test fun codeJavaScriptNormalNeDonneRien() = ok(
        "app.js",
        """
        const re = /[(]\d+\/x/g;           // regex avec une parenthese et un slash echappe
        const t = `a ${'$'}{ {x: 1}.x } b ${'$'}{ f(`n${'$'}{1}`) }`;
        const s = "une ) fermante et un { dans une chaine";
        /* commentaire avec ( [ { */
        // commentaire avec ) ] }
        function f(a) { return a / 2 / 3; }
        const g = x => /^a(b)/.test(x);
        if (re.test("x")) { console.log(s, t); }
        """.trimIndent()
    )

    @Test fun jsxEstIgnore() = ok("App.jsx", "const a = <p>Don't {</p>;")

    @Test fun pythonAvecDocstringEtCommentairesNeDonneRien() = ok(
        "main.py",
        "def f(x):\n" +
            "    \"\"\"Doc avec ( et [ et un #.\n    Suite ) ]\n    \"\"\"\n" +
            "    s = 'a ) b'  # commentaire )\n" +
            "    return {'k': [1, 2, (3, 4)], \"z\": f\"{x['a']}\"}\n" +
            "def stub(): ...\n" +
            "class P:\n    ...\n"
    )

    @Test fun kotlinAvecGabaritsImbriquesNeDonneRien() = ok(
        "A.kt",
        "fun f(m: Map<String, Int>) = \"v=\${m[\"a\"]} et \${ if (m.isEmpty()) \"{\" else \"}\" }\"\n" +
            "val c = '{'\nval raw = \"\"\"a ( \${m.size} \"\"\"\n"
    )

    @Test fun cssAvecChaineEtCommentaireNeDonneRien() = ok(
        "style.css",
        "a::after { content: \"}\"; } /* { */\n.b { color: red; }\n"
    )

    // ------------------------------------------------------------ equilibre : vraies erreurs

    @Test fun accoladeNonFermeeEstSignaleeAvecSaLigne() {
        val p = verifier("app.js", "function f() {\n  if (x) {\n    y();\n  \n}\n")
        assertEquals(1, p.size)
        assertEquals(1, p[0].ligne)
        assertTrue(p[0].message.contains("jamais fermé"))
    }

    @Test fun fermanteEnTropEstSignalee() {
        val p = verifier("app.js", "a();\n}\n")
        assertEquals(2, p.single().ligne)
        assertTrue(p.single().message.contains("ne ferme rien"))
    }

    @Test fun melangeDeParenthesesEstSignale() {
        val p = verifier("a.py", "x = (1, 2]\n")
        assertEquals(1, p.single().ligne)
        assertTrue(p.single().message.contains("ne correspond pas"))
    }

    @Test fun chaineTripleNonFermeeEnPython() {
        val p = verifier("a.py", "x = 1\n\"\"\"docstring\nsans fin\n")
        assertEquals(2, p.single().ligne)
    }

    @Test fun expressionDeGabaritKotlinNonFermee() {
        val p = verifier("A.kt", "val s = \"a \${b\n")
        assertEquals(1, p.size)
    }

    @Test fun commentaireBlocNonFerme() {
        assertEquals(1, verifier("a.js", "/* oups\nx();\n").size)
    }

    @Test fun uneChaineNonFermeeSurSaLigneNEstPasAccusee() {
        // un apostrophe isole ne doit jamais fabriquer une fausse alerte
        ok("a.js", "var a = 'abc;\nvar b = 2;\n")
    }

    // ------------------------------------------------------------ JSON

    @Test fun jsonValideNeDonneRien() = ok(
        "package.json",
        "{\n  \"a\": [1, 2.5e3, -3, true, null, {\"b\": \"x\\n\\u00e9\"}],\n  \"c\": {}\n}\n"
    )

    @Test fun jsonAvecVirguleEnTrop() {
        val p = verifier("data.json", "{\n  \"a\": 1,\n}\n")
        assertEquals(3, p.single().ligne)
        assertTrue(p.single().message.contains("virgule en trop"))
    }

    @Test fun jsonAvecGuillemetsSimples() {
        assertTrue(verifier("d.json", "{'a': 1}").single().message.contains("guillemets simples"))
    }

    @Test fun jsonAvecCommentaire() {
        assertTrue(verifier("d.json", "{\n// note\n\"a\": 1}").single().message.contains("commentaires"))
    }

    @Test fun jsonCoupe() {
        assertNotNull(verifier("d.json", "{\"a\": [1, 2").singleOrNull())
    }

    @Test fun tsconfigAccepteLesCommentaires() = ok("tsconfig.json", "{\n// ok\n\"a\": 1\n}\n")

    // ------------------------------------------------------------ fichier abrege

    @Test fun commentaireResteDuCodeEstSignale() {
        val p = verifier("app.js", "function a() {}\n// ... reste du code ...\n")
        assertEquals(2, p.single().ligne)
        assertTrue(p.single().message.contains("EN ENTIER"))
    }

    @Test fun pointsDeSuspensionSeulsEnJavaScript() {
        assertEquals(2, verifier("a.js", "function a() {\n  ...\n}\n").single().ligne)
    }

    @Test fun variantesAnglaisesEtFrancaises() {
        for (c in listOf(
            "// rest of the code remains the same", "# Ajoute ton code ici", "<!-- le reste du code -->",
            "// ... existing code ...", "/* autres fonctions inchangées */", "# TODO: implement",
            "// à compléter"
        )) {
            assertEquals(c, 1, verifier("a.js", "$c\nlet a;\n").size)
        }
    }

    @Test fun lesCommentairesNormauxNeSontPasAccuses() = ok(
        "a.js",
        "// Implémente le tri à bulles\n// Retourne le reste de la division\nconst autres = [...a, ...b];\n" +
            "const { x, ...reste } = o;\n"
    )

    @Test fun ellipsisPythonEstValide() = ok("a.py", "def f():\n    ...\n")

    @Test fun markdownNEstPasRelu() = ok("README.md", "...\n// reste du code\n")

    // ------------------------------------------------------------ perte de contenu

    private fun lignes(n: Int) = (1..n).joinToString("\n") { "ligne $it" }

    @Test fun fichierReecritTresCourtEstSignale() {
        val p = verifier("notes.txt", lignes(10), anciens = mapOf("notes.txt" to lignes(100)))
        assertEquals(1, p.size)
        assertTrue(p.single().message.contains("100") && p.single().message.contains("10"))
    }

    @Test fun reductionRaisonnableOuPetitFichierNEstPasSignale() {
        assertEquals(0, verifier("n.txt", lignes(80), anciens = mapOf("n.txt" to lignes(100))).size)
        assertEquals(0, verifier("n.txt", lignes(3), anciens = mapOf("n.txt" to lignes(30))).size)
    }

    // ------------------------------------------------------------ HTML

    @Test fun pageAvecSesFichiersNeDonneRien() = ok(
        "index.html",
        "<link rel=\"stylesheet\" href=\"css/style.css\"><script src=\"js/app.js\"></script>" +
            "<img src=\"img/logo.png\"><a href=\"autre.html\">x</a>" +
            "<script src=\"https://cdn.exemple.com/x.js\"></script><link rel=\"icon\" href=\"favicon.ico\">",
        existants = setOf("css/style.css", "js/app.js", "img/logo.png")
    )

    @Test fun fichiersEcritsDansLaMemeReponseCompte() {
        val p = VerificateurCode.verifier(
            mapOf("index.html" to "<script src=\"app.js\"></script>", "app.js" to "let a = 1;\n")
        )
        assertEquals(emptyList<Probleme>(), p)
    }

    @Test fun fichierJsManquantEstSignaleAvecSaLigne() {
        val p = verifier("index.html", "<html>\n<body>\n<script src=\"js/app.js\"></script>\n</body></html>")
        assertEquals(3, p.single().ligne)
        assertTrue(p.single().message.contains("js/app.js"))
        assertTrue(p.single().message.contains("écris-le"))
    }

    @Test fun imageManquanteExpliqueQueLIAnePeutPasLaCreer() {
        val p = verifier("index.html", "<img src=\"photo.jpg\">")
        assertTrue(p.single().message.contains("SVG"))
    }

    @Test fun cheminRelatifAuDossierDeLaPage() {
        val p = verifier(
            "pages/a.html", "<link rel=\"stylesheet\" href=\"../css/s.css\"><script src=\"a.js\"></script>",
            existants = setOf("css/s.css")
        )
        assertEquals(1, p.size)
        assertTrue(p.single().message.contains("a.js"))
        ok("pages/a.html", "<script src=\"a.js\"></script>", existants = setOf("pages/a.js"))
        ok("pages/a.html", "<script src=\"/js/x.js\"></script>", existants = setOf("js/x.js"))
    }

    @Test fun mauvaiseCasseEstExpliquee() {
        val p = verifier("index.html", "<script src=\"js/App.js\"></script>", existants = setOf("js/app.js"))
        assertTrue(p.single().message.contains("majuscules"))
    }

    @Test fun scriptEnLigneNonFermeEstSignaleALaBonneLigne() {
        val p = verifier("index.html", "<html>\n<script>\nfunction f() {\n  g();\n</script>\n</html>")
        assertEquals(3, p.single().ligne)
        assertTrue(p.single().message.startsWith("dans <script>"))
    }

    @Test fun styleEnLigneNonFermeEstSignale() {
        val p = verifier("i.html", "<style>\nbody { color: red;\n</style>")
        assertEquals(2, p.single().ligne)
    }

    @Test fun gabaritsEtDonneesNeSontPasLus() {
        ok("t.html", "<script>{{ donnees }} {</script><script type=\"application/json\">{\"a\": </script>")
    }

    // ------------------------------------------------------------ limites

    @Test fun jamaisPlusDeSixProblemes() {
        val ecrits = (1..20).associate { "f$it.js" to "function a() {\n" }
        assertEquals(VerificateurCode.MAX_PROBLEMES, VerificateurCode.verifier(ecrits).size)
    }

    @Test fun grosFichierNEstPasRelu() {
        val gros = "{\n".repeat(VerificateurCode.MAX_CARACTERES)
        assertEquals(0, verifier("gros.js", gros).size)
    }

    @Test fun texteDuProblemeContientFichierEtLigne() {
        assertEquals("a.js (ligne 4) : x", Probleme("a.js", 4, "x").texte())
        assertEquals("a.js : x", Probleme("a.js", null, "x").texte())
    }
}
