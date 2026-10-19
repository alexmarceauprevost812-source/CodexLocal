package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NommageIATest {

    private fun sansNom(reponse: String) =
        ActionsFichiers.extraire(reponse).filterIsInstance<ActionFichier.SansNom>()

    private val codePython = "```python\na = 1\nb = 2\nc = 3\nprint(a + b + c)\n```"

    // --- le bloc sans nom garde son code et sa place ---

    @Test fun unnamedBlockKeepsItsCodeAndPosition() {
        val bloc = sansNom("Voici le code :\n\n$codePython\nFini.").single()
        assertEquals("python", bloc.langage)
        assertEquals(2, bloc.ligneOuverture)
        assertEquals("a = 1\nb = 2\nc = 3\nprint(a + b + c)\n", bloc.contenu)
        assertTrue(bloc.complet)
    }

    @Test fun twoUnnamedBlocksHaveTheirOwnPositions() {
        val reponse = "$codePython\nEt le style :\n```css\na { color: red; }\nb { color: blue; }\nc { margin: 0; }\nd { padding: 0; }\n```"
        assertEquals(listOf(0, 7), sansNom(reponse).map { it.ligneOuverture })
    }

    // --- lire la reponse de l'IA ---

    @Test fun readsAPlainAnswer() {
        assertEquals("main.py", NommageIA.lireNom("main.py", "python"))
        assertEquals("main.py", NommageIA.lireNom("  `main.py`\n", "python"))
        assertEquals("css/style.css", NommageIA.lireNom("**css/style.css**", "css"))
        assertEquals("index.html", NommageIA.lireNom("Le nom du fichier est : index.html.", "html"))
    }

    @Test fun readsTheAnswerAfterTheThinking() {
        val reponse = "<think>\nOn pourrait l'appeler script.js ou app.js, mais c'est du python.\n</think>\n\ncalcul.py"
        assertEquals("calcul.py", NommageIA.lireNom(reponse, "python"))
    }

    @Test fun fallsBackToTheLastNameOfTheThinkingWhenCutOff() {
        val reponse = "<think>\nCe code additionne trois nombres. Un bon nom serait somme.py"
        assertEquals("somme.py", NommageIA.lireNom(reponse, "python"))
    }

    @Test fun refusesAnExtensionThatDoesNotMatchTheLanguage() {
        assertNull(NommageIA.lireNom("index.html", "python"))
        assertNull(NommageIA.lireNom("<think>peut-etre style.css</think>style.css", "python"))
    }

    @Test fun refusesAnswersWithoutAFileName() {
        assertNull(NommageIA.lireNom("", "python"))
        assertNull(NommageIA.lireNom("Je ne sais pas.", "python"))
        assertNull(NommageIA.lireNom("<think>hmm", "python"))
    }

    @Test fun unknownLanguageAcceptsAnyFileName() {
        assertTrue(NommageIA.compatible("a.xyz", "inconnu"))
        assertTrue(NommageIA.compatible("src/App.kt", "kotlin"))
        assertFalse(NommageIA.compatible("src/App.java", "kotlin"))
    }

    // --- noms par defaut et noms uniques ---

    @Test fun defaultNames() {
        assertEquals("index.html", NommageIA.nomParDefaut("html"))
        assertEquals("main.py", NommageIA.nomParDefaut("python"))
        assertEquals("script.js", NommageIA.nomParDefaut("javascript"))
        assertEquals("fichier.zzz", NommageIA.nomParDefaut("zzz"))
    }

    @Test fun uniqueNeverReusesATakenName() {
        assertEquals("main.py", NommageIA.unique("main.py", emptySet()))
        assertEquals("main_2.py", NommageIA.unique("main.py", setOf("main.py")))
        assertEquals("main_3.py", NommageIA.unique("main.py", setOf("main.py", "main_2.py")))
        assertEquals("css/style_2.css", NommageIA.unique("css/style.css", setOf("CSS/Style.css")))
        assertEquals("Dockerfile_2", NommageIA.unique("Dockerfile", setOf("dockerfile")))
    }

    // --- attribuer ---

    @Test fun assignsTheNamesTheAiChose() {
        val blocs = sansNom(codePython)
        val noms = NommageIA.attribuer(blocs, listOf("calcul.py"), emptySet())
        assertEquals(mapOf(0 to "calcul.py"), noms)
    }

    @Test fun usesTheDefaultNameWhenTheAiAnswersBadlyOrNotAtAll() {
        val blocs = sansNom(codePython)
        assertEquals(mapOf(0 to "main.py"), NommageIA.attribuer(blocs, listOf("n'importe quoi"), emptySet()))
        assertEquals(mapOf(0 to "main.py"), NommageIA.attribuer(blocs, listOf(null), emptySet()))
        assertEquals(mapOf(0 to "main.py"), NommageIA.attribuer(blocs, emptyList(), emptySet()))
    }

    @Test fun neverOverwritesAnExistingFileNorGivesTheSameNameTwice() {
        val reponse = "$codePython\n$codePython"
        val blocs = sansNom(reponse)
        val noms = NommageIA.attribuer(blocs, listOf("main.py", "main.py"), setOf("main.py"))
        assertEquals(mapOf(0 to "main_2.py", 6 to "main_3.py"), noms)
    }

    // --- de bout en bout : le nom choisi devient le titre et le fichier est ecrit ---

    @Test fun theChosenNameBecomesTheTitleAndTheFile() {
        val reponse = "Voici :\n$codePython\nFini."
        val noms = NommageIA.attribuer(sansNom(reponse), listOf("calcul.py"), emptySet())
        val titree = ActionsFichiers.avecTitres(reponse, noms)

        assertTrue(titree.contains("**calcul.py**\n```python"))
        val ecriture = ActionsFichiers.extraire(titree).filterIsInstance<ActionFichier.Ecrire>().single()
        assertEquals("calcul.py", ecriture.chemin)
        assertEquals("a = 1\nb = 2\nc = 3\nprint(a + b + c)\n", ecriture.contenu)
        assertTrue(ActionsFichiers.extraire(titree).none { it is ActionFichier.SansNom })

        val code = DecoupageMessage.decouper(titree).filterIsInstance<BlocMessage.Code>().single()
        assertEquals("calcul.py", code.titre)
    }

    @Test fun titlesGoBeforeTheRightBlocks() {
        val reponse = "$codePython\nPuis :\n```css\na { color: red; }\nb { color: blue; }\nc { margin: 0; }\nd { padding: 0; }\n```"
        val noms = NommageIA.attribuer(sansNom(reponse), listOf("calcul.py", "style.css"), emptySet())
        val ecritures = ActionsFichiers.extraire(ActionsFichiers.avecTitres(reponse, noms))
            .filterIsInstance<ActionFichier.Ecrire>()
        assertEquals(listOf("calcul.py", "style.css"), ecritures.map { it.chemin })
        assertTrue(ecritures[1].contenu.startsWith("a { color: red; }"))
    }

    @Test fun noNamesLeavesTheReplyUntouched() {
        assertEquals("abc\ndef", ActionsFichiers.avecTitres("abc\ndef", emptyMap()))
    }

    // --- la question posee a l'IA ---

    @Test fun theQuestionContainsTheCodeTheLanguageAndTheTakenNames() {
        val q = NommageIA.demande(sansNom(codePython).single(), listOf("README.md", "index.html"))
        assertTrue(q.contains("python"))
        assertTrue(q.contains("print(a + b + c)"))
        assertTrue(q.contains("README.md, index.html"))
        assertTrue(q.contains("UNIQUEMENT"))
    }

    @Test fun theQuestionKeepsOnlyTheStartOfBigCode() {
        val gros = "```python\n" + (1..500).joinToString("\n") { "x$it = $it" } + "\n```"
        val q = NommageIA.demande(sansNom(gros).single(), emptyList())
        assertTrue(q.length < 2500)
        assertTrue(q.contains("x1 = 1"))
        assertFalse(q.contains("x500 = 500"))
    }

    @Test fun checksEachChosenNameDirectlyInTheProject() {
        // le projet a des fichiers que la liste (limitee) ne montre pas
        val existants = setOf("main.py", "main_2.py")
        val noms = NommageIA.attribuer(sansNom(codePython), listOf("main.py"), emptySet()) { it in existants }
        assertEquals(mapOf(0 to "main_3.py"), noms)
    }

    @Test fun onlyExtensionsTheProjectCanWriteAreAccepted() {
        assertFalse(NommageIA.compatible("icone.svg", "xml"))
        assertNull(NommageIA.lireNom("icone.svg", "xml"))
        assertEquals("data.xml", NommageIA.lireNom("data.xml", "xml"))
    }

    @Test fun everyAcceptedExtensionCanBeWrittenByTheProject() {
        // sinon un nom choisi par l'IA serait refuse par le projet, et le fichier perdu
        NommageIA.extensionsAcceptees().forEach { extension ->
            assertTrue(extension, com.codexlocal.project.Langages.estTexte("fichier.$extension"))
        }
        listOf("html", "css", "javascript", "python", "kotlin", "java", "sql", "markdown", "ini", "xml").forEach {
            assertTrue(it, com.codexlocal.project.Langages.estTexte(NommageIA.nomParDefaut(it)))
        }
    }

    @Test fun theConversationQuestionIdentifiesTheBlockByItsFirstLines() {
        val q = NommageIA.demandeDansLaConversation(sansNom(codePython).single())
        assertTrue(q.contains("python"))
        assertTrue(q.contains("a = 1\nb = 2\nc = 3"))
        assertFalse("pas tout le code : il est deja dans la conversation", q.contains("print(a + b + c)"))
        assertTrue(q.contains("UNIQUEMENT"))
    }

    @Test fun aLongAnswerThatStartsWithTheNameIsStillRead() {
        // dans la conversation, le modele peut repondre par un bloc entier au lieu du nom seul
        val reponse = "=== FICHIER: calcul.py ===\na = 1\nb = 2"
        assertEquals("calcul.py", NommageIA.lireNom(reponse, "python"))
    }
}
