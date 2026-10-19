package com.codexlocal.agent

import com.codexlocal.project.ProjectStore
import com.codexlocal.project.Projet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AgentProjetTest {

    private val ctx get() = RuntimeEnvironment.getApplication()

    private fun projet(): Projet = ProjectStore.creerProjetInterne(ctx, "agent_" + System.nanoTime())

    private fun appliquer(p: Projet, reponse: String, ignorerIncomplets: Boolean = false) =
        AgentProjet.appliquer(ctx, p, ActionsFichiers.extraire(reponse), ignorerIncomplets)

    @Test fun createsNewFilesAndFoldersAutomatically() {
        val p = projet()
        val bilan = appliquer(
            p,
            "=== FICHIER: index.html ===\n<p>hi</p>\n=== FIN ===\n" +
                "=== FICHIER: js/app.js ===\nconsole.log(1);\n=== FIN ==="
        )
        assertEquals(listOf("index.html", "js/app.js"), bilan.crees)
        assertTrue(bilan.modifies.isEmpty())
        assertEquals("<p>hi</p>\n", File(p.uri, "index.html").readText())
        assertEquals("console.log(1);\n", File(p.uri, "js/app.js").readText())
        assertTrue(bilan.resume().contains("📁 Créé : index.html, js/app.js"))
    }

    @Test fun overwritesExistingFilesAndCanUndo() {
        val p = projet()
        File(p.uri, "a.txt").writeText("avant\n")
        val bilan = appliquer(p, "=== FICHIER: a.txt ===\naprès\n=== FIN ===\n=== FICHIER: b.txt ===\nneuf\n=== FIN ===")
        assertEquals(listOf("b.txt"), bilan.crees)
        assertEquals(listOf("a.txt"), bilan.modifies)
        assertEquals("après\n", File(p.uri, "a.txt").readText())

        assertEquals(2, AgentProjet.annuler(ctx, bilan.annulations))
        assertEquals("avant\n", File(p.uri, "a.txt").readText())
        assertFalse(File(p.uri, "b.txt").exists())
    }

    @Test fun identicalContentIsNotReportedAsModified() {
        val p = projet()
        File(p.uri, "a.txt").writeText("pareil\n")
        val bilan = appliquer(p, "=== FICHIER: a.txt ===\npareil\n=== FIN ===")
        assertTrue(bilan.modifies.isEmpty() && bilan.crees.isEmpty() && bilan.annulations.isEmpty())
    }

    @Test fun refusesUnsafePathsButKeepsGoing() {
        val p = projet()
        val bilan = appliquer(
            p,
            "=== FICHIER: ../dehors.txt ===\nx\n=== FIN ===\n" +
                "=== FICHIER: /absolu.txt ===\nx\n=== FIN ===\n" +
                "=== FICHIER: .cache/x.txt ===\nx\n=== FIN ===\n" +
                "=== FICHIER: ok.txt ===\nbon\n=== FIN ==="
        )
        assertEquals(listOf("ok.txt"), bilan.crees)
        assertEquals(3, bilan.ignores.size)
        assertFalse(File(p.uri).parentFile.resolve("dehors.txt").exists())
    }

    @Test fun refusesBinaryFilesAndEmptyContent() {
        val p = projet()
        val bilan = appliquer(
            p,
            "=== FICHIER: image.png ===\nx\n=== FIN ===\n=== FICHIER: vide.txt ===\n\n=== FIN ==="
        )
        assertTrue(bilan.crees.isEmpty())
        assertEquals(2, bilan.ignores.size)
    }

    @Test fun skipsPossiblyTruncatedFileOnlyWhenAsked() {
        val p = projet()
        val reponse = "=== FICHIER: a.txt ===\nok\n=== FIN ===\n=== FICHIER: b.txt ===\ncoupé"
        val strict = appliquer(p, reponse, ignorerIncomplets = true)
        assertEquals(listOf("a.txt"), strict.crees)
        assertEquals(1, strict.ignores.size)
        assertFalse(File(p.uri, "b.txt").exists())

        val tolerant = appliquer(p, reponse, ignorerIncomplets = false)
        assertEquals(listOf("b.txt"), tolerant.crees)
    }

    @Test fun readsFilesAndTruncatesBigOnes() {
        val p = projet()
        File(p.uri, "petit.txt").writeText("bonjour\n")
        File(p.uri, "gros.txt").writeText("x".repeat(AgentProjet.MAX_CARACTERES_LECTURE + 500))
        val bilan = appliquer(
            p, "=== LIRE: petit.txt ===\n=== LIRE: gros.txt ===\n=== LIRE: absent.txt ===\n=== LIRE: ../secret.txt ==="
        )
        assertEquals(4, bilan.lectures.size)
        assertEquals("bonjour\n", bilan.lectures[0].texte)
        assertTrue(bilan.lectures[1].texte!!.contains("tronqué"))
        assertNull(bilan.lectures[2].texte)
        assertNotNull(bilan.lectures[2].erreur)
        assertNull(bilan.lectures[3].texte)
        val renvoi = bilan.texteLectures()
        assertTrue(renvoi.contains("--- Fichier petit.txt ---") && renvoi.contains("fichier introuvable"))
    }

    @Test fun projectContextListsFiles() {
        val p = projet()
        File(p.uri, "src").mkdirs()
        File(p.uri, "src/main.py").writeText("print(1)\n")
        val contexte = AgentProjet.contexteProjet(ctx, p)
        assertTrue(contexte.contains(p.nom) && contexte.contains("README.md") && contexte.contains("src/main.py"))
    }

    @Test fun findFileIsExactAndSafe() {
        val p = projet()
        File(p.uri, "a/b").mkdirs()
        File(p.uri, "a/b/c.txt").writeText("x")
        assertEquals("a/b/c.txt", ProjectStore.trouverFichier(ctx, p, "a/b/c.txt")?.chemin)
        assertNull(ProjectStore.trouverFichier(ctx, p, "a/b"))
        assertNull(ProjectStore.trouverFichier(ctx, p, "a/zzz.txt"))
    }

    @Test fun createsSeveralFilesFromMarkdownBlocksWithTitles() {
        val p = projet()
        val bilan = appliquer(
            p,
            "Voici ton site.\n\n**index.html**\n```html\n<link rel=\"stylesheet\" href=\"css/style.css\">\n```\n\n" +
                "### css/style.css\n```css\nbody { margin: 0; }\n```\n\n" +
                "```javascript title=\"js/app.js\"\nconsole.log(1);\n```"
        )
        assertEquals(listOf("index.html", "css/style.css", "js/app.js"), bilan.crees)
        assertEquals("body { margin: 0; }\n", File(p.uri, "css/style.css").readText())
        assertEquals("console.log(1);\n", File(p.uri, "js/app.js").readText())
        assertTrue(bilan.resume().contains("📁 Créé : index.html, css/style.css, js/app.js"))
    }

    @Test fun bigUnnamedCodeBlockIsReportedNotWritten() {
        val p = projet()
        val bilan = appliquer(p, "Voilà :\n```python\na = 1\nb = 2\nc = 3\nprint(a + b + c)\n```")
        assertTrue(bilan.crees.isEmpty() && bilan.modifies.isEmpty())
        assertEquals(1, bilan.ignores.size)
        assertTrue(bilan.ignores[0].contains("sans nom de fichier"))
        assertTrue(bilan.resume().contains("⚠️"))
        assertEquals(listOf("README.md"), ProjectStore.listerFichiers(ctx, p).map { it.chemin })
    }

    @Test fun unnamedBlockNamedByTheAiIsCreatedWithThatName() {
        val p = projet()
        val reponse = "Voilà :\n```python\na = 1\nb = 2\nc = 3\nprint(a + b + c)\n```"
        val sansNom = ActionsFichiers.extraire(reponse).filterIsInstance<ActionFichier.SansNom>()
        val noms = NommageIA.attribuer(sansNom, listOf("<think>hmm</think>calcul.py"), setOf("README.md"))
        val bilan = AgentProjet.appliquer(
            ctx, p, ActionsFichiers.extraire(ActionsFichiers.avecTitres(reponse, noms)), ignorerIncomplets = false
        )
        assertEquals(listOf("calcul.py"), bilan.crees)
        assertTrue(bilan.ignores.isEmpty())
        assertEquals("a = 1\nb = 2\nc = 3\nprint(a + b + c)\n", File(p.uri, "calcul.py").readText())
    }

    @Test fun unnamedBlockNeverOverwritesAnExistingFile() {
        val p = projet()
        File(p.uri, "main.py").writeText("precieux\n")
        val reponse = "```python\na = 1\nb = 2\nc = 3\nprint(a + b + c)\n```"
        val sansNom = ActionsFichiers.extraire(reponse).filterIsInstance<ActionFichier.SansNom>()
        val noms = NommageIA.attribuer(sansNom, listOf(null), setOf("README.md", "main.py"))
        val bilan = AgentProjet.appliquer(
            ctx, p, ActionsFichiers.extraire(ActionsFichiers.avecTitres(reponse, noms)), ignorerIncomplets = false
        )
        assertEquals(listOf("main_2.py"), bilan.crees)
        assertEquals("precieux\n", File(p.uri, "main.py").readText())
    }

    // ------------------------------------------- fichiers existants que l'IA n'a pas lus

    private fun appliquerAvecConnus(p: Projet, reponse: String, connus: Map<String, String>) =
        AgentProjet.appliquer(ctx, p, ActionsFichiers.extraire(reponse), ignorerIncomplets = false, connus = connus)

    @Test fun existingFileTheAiHasNotSeenIsKeptAndShownBack() {
        val p = projet()
        File(p.uri, "index.html").writeText("<h1>Mon site</h1>\n")
        val bilan = appliquerAvecConnus(
            p, "=== FICHIER: index.html ===\n<h1>Bonjour</h1>\n=== FIN ===", emptyMap()
        )
        assertEquals("<h1>Mon site</h1>\n", File(p.uri, "index.html").readText())
        assertTrue(bilan.crees.isEmpty() && bilan.modifies.isEmpty() && bilan.annulations.isEmpty())
        assertEquals(listOf("index.html"), bilan.proteges)
        assertEquals(1, bilan.lectures.size)
        assertEquals("index.html", bilan.lectures[0].chemin)
        assertEquals("<h1>Mon site</h1>\n", bilan.lectures[0].texte)
        assertTrue(bilan.texteLectures().contains("n'a PAS été remplacé"))
        assertTrue(bilan.resume().contains("🔒 Protégé : index.html"))
    }

    @Test fun fileTheAiHasSeenCanBeReplacedAndUndone() {
        val p = projet()
        File(p.uri, "index.html").writeText("avant\n")
        val bilan = appliquerAvecConnus(
            p, "=== FICHIER: index.html ===\naprès\n=== FIN ===",
            mapOf("index.html" to FichiersConnus.empreinte("avant\n"))
        )
        assertEquals(listOf("index.html"), bilan.modifies)
        assertTrue(bilan.proteges.isEmpty() && bilan.lectures.isEmpty())
        assertEquals("après\n", File(p.uri, "index.html").readText())
        AgentProjet.annuler(ctx, bilan.annulations)
        assertEquals("avant\n", File(p.uri, "index.html").readText())
    }

    @Test fun onlyTheUnseenFilesAreProtectedInAMixedReply() {
        val p = projet()
        File(p.uri, "vu.txt").writeText("vu avant\n")
        File(p.uri, "inconnu.txt").writeText("precieux\n")
        val bilan = appliquerAvecConnus(
            p,
            "=== FICHIER: vu.txt ===\nvu après\n=== FIN ===\n" +
                "=== FICHIER: inconnu.txt ===\nécrasé\n=== FIN ===\n" +
                "=== FICHIER: neuf.txt ===\nnouveau\n=== FIN ===",
            mapOf("vu.txt" to FichiersConnus.empreinte("vu avant\n"))
        )
        assertEquals(listOf("neuf.txt"), bilan.crees)
        assertEquals(listOf("vu.txt"), bilan.modifies)
        assertEquals(listOf("inconnu.txt"), bilan.proteges)
        assertEquals("vu après\n", File(p.uri, "vu.txt").readText())
        assertEquals("precieux\n", File(p.uri, "inconnu.txt").readText())
        assertEquals("nouveau\n", File(p.uri, "neuf.txt").readText())
    }

    @Test fun aFileCreatedEarlierInTheSameReplyCanBeWrittenAgain() {
        val p = projet()
        val bilan = appliquerAvecConnus(
            p,
            "=== FICHIER: neuf.txt ===\npremier\n=== FIN ===\n=== FICHIER: neuf.txt ===\nsecond\n=== FIN ===",
            emptyMap()
        )
        assertTrue(bilan.proteges.isEmpty())
        assertEquals("second\n", File(p.uri, "neuf.txt").readText())
    }

    @Test fun readingAndWritingTheSameUnseenFileReadsItOnlyOnce() {
        val p = projet()
        File(p.uri, "a.txt").writeText("contenu\n")
        val bilan = appliquerAvecConnus(
            p, "=== LIRE: a.txt ===\n=== FICHIER: a.txt ===\nautre\n=== FIN ===", emptyMap()
        )
        assertEquals(1, bilan.lectures.size)
        assertEquals(listOf("a.txt"), bilan.proteges)
        assertEquals("contenu\n", File(p.uri, "a.txt").readText())
    }

    @Test fun writingBeforeReadingTheSameFileAlsoReadsItOnlyOnce() {
        val p = projet()
        File(p.uri, "a.txt").writeText("contenu\n")
        val bilan = appliquerAvecConnus(
            p, "=== FICHIER: a.txt ===\nautre\n=== FIN ===\n=== LIRE: a.txt ===", emptyMap()
        )
        assertEquals(1, bilan.lectures.size)
        assertEquals("contenu\n", File(p.uri, "a.txt").readText())
    }

    @Test fun aBigFileIsProtectedAndNeverCountsAsFullySeen() {
        val p = projet()
        File(p.uri, "gros.js").writeText("x".repeat(AgentProjet.MAX_CARACTERES_LECTURE + 500))
        val bilan = appliquerAvecConnus(
            p, "=== FICHIER: gros.js ===\npetit\n=== FIN ===", emptyMap()
        )
        assertEquals(listOf("gros.js"), bilan.proteges)
        assertTrue(bilan.lectures[0].tronque)
        assertTrue(bilan.lusEnEntier.isEmpty())
        assertTrue(bilan.texteLectures().contains("NOUVEAU fichier"))
        assertEquals(AgentProjet.MAX_CARACTERES_LECTURE + 500, File(p.uri, "gros.js").readText().length)
    }

    @Test fun anExplicitReadIsFullySeenWhenShortEnough() {
        val p = projet()
        File(p.uri, "petit.txt").writeText("bonjour\n")
        val bilan = appliquerAvecConnus(p, "=== LIRE: petit.txt ===", emptyMap())
        assertEquals(setOf("petit.txt"), bilan.lusEnEntier.keys)
        assertTrue(bilan.proteges.isEmpty())
    }

    @Test fun existingBinaryFileIsStillRefusedWithAReason() {
        val p = projet()
        File(p.uri, "logo.png").writeBytes(byteArrayOf(1, 2, 3))
        val bilan = appliquerAvecConnus(p, "=== FICHIER: logo.png ===\nx\n=== FIN ===", emptyMap())
        assertTrue(bilan.proteges.isEmpty() && bilan.lectures.isEmpty())
        assertEquals(1, bilan.ignores.size)
        assertEquals(3, File(p.uri, "logo.png").length().toInt())
    }

    @Test fun codeBlocksWithATitleAreProtectedToo() {
        val p = projet()
        File(p.uri, "index.html").writeText("<p>a moi</p>\n")
        val bilan = appliquerAvecConnus(
            p, "**index.html**\n```html\n<p>remplace</p>\n```", emptyMap()
        )
        assertEquals(listOf("index.html"), bilan.proteges)
        assertEquals("<p>a moi</p>\n", File(p.uri, "index.html").readText())
    }

    @Test fun aFileChangedSinceTheAiSawItIsProtectedAgain() {
        val p = projet()
        File(p.uri, "index.html").writeText("version de l'IA\n")
        val connus = mapOf("index.html" to FichiersConnus.empreinte("version de l'IA\n"))
        // l'utilisateur la modifie a la main : l'IA ne la connait plus
        File(p.uri, "index.html").writeText("version de l'IA + mes retouches\n")
        val bilan = appliquerAvecConnus(p, "=== FICHIER: index.html ===\nreecrit\n=== FIN ===", connus)
        assertEquals(listOf("index.html"), bilan.proteges)
        assertEquals("version de l'IA + mes retouches\n", File(p.uri, "index.html").readText())
        assertEquals("version de l'IA + mes retouches\n", bilan.lectures[0].texte)
    }

    @Test fun whatTheAiWritesBecomesKnownToTheAi() {
        val p = projet()
        File(p.uri, "a.txt").writeText("avant\n")
        val bilan = appliquerAvecConnus(
            p,
            "=== FICHIER: a.txt ===\naprès\n=== FIN ===\n=== FICHIER: b.txt ===\nneuf\n=== FIN ===",
            mapOf("a.txt" to FichiersConnus.empreinte("avant\n"))
        )
        assertEquals(
            mapOf(
                "a.txt" to FichiersConnus.empreinte("après\n"),
                "b.txt" to FichiersConnus.empreinte("neuf\n")
            ),
            bilan.ecrits
        )
        // et ce qui est connu correspond bien au contenu du disque
        assertEquals(FichiersConnus.empreinte(File(p.uri, "a.txt").readText()), bilan.ecrits["a.txt"])
        assertEquals(FichiersConnus.empreinte(File(p.uri, "b.txt").readText()), bilan.ecrits["b.txt"])
    }

    @Test fun nextTurnCanRewriteWhatWasWrittenTheTurnBefore() {
        val p = projet()
        val premier = appliquerAvecConnus(p, "=== FICHIER: jeu.js ===\nlet a = 1;\n=== FIN ===", emptyMap())
        val second = appliquerAvecConnus(p, "=== FICHIER: jeu.js ===\nlet a = 2;\n=== FIN ===", premier.ecrits)
        assertTrue(second.proteges.isEmpty())
        assertEquals(listOf("jeu.js"), second.modifies)
        assertEquals("let a = 2;\n", File(p.uri, "jeu.js").readText())
    }

    @Test fun afterAnUndoTheFileIsNoLongerWhatTheAiKnows() {
        val p = projet()
        File(p.uri, "a.txt").writeText("original\n")
        val premier = appliquerAvecConnus(
            p, "=== FICHIER: a.txt ===\nmodifie\n=== FIN ===",
            mapOf("a.txt" to FichiersConnus.empreinte("original\n"))
        )
        AgentProjet.annuler(ctx, premier.annulations)
        val second = appliquerAvecConnus(p, "=== FICHIER: a.txt ===\nencore\n=== FIN ===", premier.ecrits)
        assertEquals(listOf("a.txt"), second.proteges)
        assertEquals("original\n", File(p.uri, "a.txt").readText())
    }

    // ------------------------------------------- version beaucoup plus courte, apercu pour l'equipe

    @Test fun aKnownFileIsNotReplacedByAMuchShorterVersion() {
        val p = projet()
        val gros = "ligne de code\n".repeat(100)
        File(p.uri, "index.html").writeText(gros)
        val bilan = appliquerAvecConnus(
            p, "=== FICHIER: index.html ===\njuste le nouveau bouton\n=== FIN ===",
            mapOf("index.html" to FichiersConnus.empreinte(gros))
        )
        assertTrue(bilan.modifies.isEmpty() && bilan.proteges.isEmpty() && bilan.annulations.isEmpty())
        assertEquals(1, bilan.ignores.size)
        assertTrue(bilan.ignores[0].startsWith("index.html (la nouvelle version est beaucoup plus courte"))
        assertEquals(gros, File(p.uri, "index.html").readText())
    }

    @Test fun theShorterVersionGoesThroughWhenTheUserAskedForIt() {
        val p = projet()
        val gros = "ligne de code\n".repeat(100)
        File(p.uri, "index.html").writeText(gros)
        val bilan = AgentProjet.appliquer(
            ctx, p,
            ActionsFichiers.extraire("=== FICHIER: index.html ===\nsimple\n=== FIN ==="),
            ignorerIncomplets = false,
            connus = mapOf("index.html" to FichiersConnus.empreinte(gros)),
            reductionPermise = true
        )
        assertEquals(listOf("index.html"), bilan.modifies)
        assertEquals("simple\n", File(p.uri, "index.html").readText())
    }

    @Test fun shrinkingIsNotCheckedWithoutProtection() {
        val p = projet()
        File(p.uri, "index.html").writeText("ligne de code\n".repeat(100))
        val bilan = appliquer(p, "=== FICHIER: index.html ===\nsimple\n=== FIN ===")
        assertEquals(listOf("index.html"), bilan.modifies)
    }

    @Test fun previewShowsExistingFilesAndRecordsWhatWasShown() {
        val p = projet()
        File(p.uri, "index.html").writeText("<h1>Mon site</h1>\n")
        File(p.uri, "css").mkdirs()
        File(p.uri, "css/style.css").writeText("h1 { color: red; }")
        val apercu = AgentProjet.apercuFichiers(ctx, p, "change la couleur", 10_000)
        assertTrue(apercu.texte.contains("--- Fichier index.html ---\n<h1>Mon site</h1>\n"))
        // un fichier sans retour a la ligne final est complete
        assertTrue(apercu.texte.contains("--- Fichier css/style.css ---\nh1 { color: red; }\n"))
        assertTrue(apercu.texte.endsWith("--- Fin des fichiers ---"))
        assertEquals(FichiersConnus.empreinte("<h1>Mon site</h1>\n"), apercu.connus["index.html"])
        assertEquals(FichiersConnus.empreinte("h1 { color: red; }"), apercu.connus["css/style.css"])
    }

    @Test fun previewRespectsTheBudgetAndKeepsTheFilesTheRequestNames() {
        val p = projet()
        File(p.uri, "index.html").writeText("i".repeat(300))
        File(p.uri, "style.css").writeText("s".repeat(300))
        File(p.uri, "app.js").writeText("a".repeat(300))
        val apercu = AgentProjet.apercuFichiers(ctx, p, "corrige app.js", 650)
        // 650 caracteres : deux fichiers de 300 tiennent, pas trois (README.md compte aussi)
        assertTrue(apercu.connus.containsKey("app.js"))
        assertTrue(apercu.connus.containsKey("index.html"))
        assertFalse(apercu.connus.containsKey("style.css"))
        assertTrue(apercu.connus.values.size <= 3)
    }

    @Test fun previewSkipsBigFilesAndBinaryFiles() {
        val p = projet()
        File(p.uri, "gros.js").writeText("x".repeat(AgentProjet.MAX_CARACTERES_LECTURE + 1))
        File(p.uri, "logo.png").writeBytes(byteArrayOf(1, 2, 3))
        val apercu = AgentProjet.apercuFichiers(ctx, p, "", 100_000)
        assertFalse(apercu.connus.containsKey("gros.js"))
        assertFalse(apercu.connus.containsKey("logo.png"))
    }

    @Test fun nothingToShowGivesAnEmptyPreview() {
        val p = projet()
        File(p.uri, "README.md").delete()
        val apercu = AgentProjet.apercuFichiers(ctx, p, "fais un site", 5_000)
        assertTrue(apercu.texte.isEmpty() && apercu.connus.isEmpty())
    }

    @Test fun whatThePreviewShowedCanBeReplacedByTheTeam() {
        val p = projet()
        File(p.uri, "index.html").writeText("<p>avant</p>\n")
        val apercu = AgentProjet.apercuFichiers(ctx, p, "modifie index.html", 5_000)
        val bilan = appliquerAvecConnus(
            p, "=== FICHIER: index.html ===\n<p>apres</p>\n=== FIN ===", apercu.connus
        )
        assertEquals(listOf("index.html"), bilan.modifies)
        // modifie a la main entre-temps : l'IA ne le connait plus
        File(p.uri, "index.html").writeText("<p>a la main</p>\n")
        val encore = appliquerAvecConnus(
            p, "=== FICHIER: index.html ===\n<p>encore</p>\n=== FIN ===", apercu.connus
        )
        assertEquals(listOf("index.html"), encore.proteges)
        assertEquals("<p>a la main</p>\n", File(p.uri, "index.html").readText())
    }

    @Test fun previewCountsCharactersNotBytes() {
        val p = projet()
        // 7 000 caracteres accentues = 14 000 octets : sous la limite en caracteres, au-dessus en octets
        val accentue = "é".repeat(7_000)
        assertTrue(accentue.toByteArray().size > AgentProjet.MAX_CARACTERES_LECTURE)
        File(p.uri, "texte.txt").writeText(accentue)
        val apercu = AgentProjet.apercuFichiers(ctx, p, "modifie texte.txt", 20_000)
        assertEquals(FichiersConnus.empreinte(accentue), apercu.connus["texte.txt"])
    }

    @Test fun previewStillSkipsFilesWithTooManyCharacters() {
        val p = projet()
        File(p.uri, "long.txt").writeText("é".repeat(AgentProjet.MAX_CARACTERES_LECTURE + 1))
        val apercu = AgentProjet.apercuFichiers(ctx, p, "modifie long.txt", 100_000)
        assertFalse(apercu.connus.containsKey("long.txt"))
    }
}
