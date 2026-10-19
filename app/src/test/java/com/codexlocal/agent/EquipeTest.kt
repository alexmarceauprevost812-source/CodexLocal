package com.codexlocal.agent

import com.codexlocal.AppConfig
import com.codexlocal.ProviderType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EquipeTest {

    @Test fun fiveStepsInOrderWithTheWhiteHatChiefFirstAndLast() {
        assertEquals(Equipe.NB_ETAPES, Role.values().size)
        assertEquals(
            listOf(Role.CHEF, Role.PLANIFICATEUR, Role.REDACTEUR, Role.CODEUR, Role.REVUE),
            Role.values().toList()
        )
        assertTrue(Role.CHEF.emoji == "⛑️" && Role.REVUE.emoji == "⛑️")
    }

    @Test fun onlyWritersGetTheFileProtocol() {
        assertFalse(Role.CHEF.systemPrompt(true).contains("=== FICHIER"))
        assertFalse(Role.PLANIFICATEUR.systemPrompt(true).contains("=== FICHIER"))
        assertTrue(Role.REDACTEUR.systemPrompt(true).contains("=== FICHIER"))
        assertTrue(Role.CODEUR.systemPrompt(true).contains("=== FIN ==="))
        assertTrue(Role.CODEUR.systemPrompt(false).contains("Aucun projet"))
        assertFalse(Role.CODEUR.systemPrompt(false).contains("=== FICHIER"))
    }

    @Test fun coderGetsRoomForWholeFiles() {
        val local = AppConfig(provider = ProviderType.LOCAL, maxTokens = 1024)
        val distant = AppConfig(provider = ProviderType.OPENAI, maxTokens = 1024)
        assertTrue(Role.CODEUR.maxTokens(local) >= 3000)
        assertTrue(Role.CODEUR.maxTokens(distant) > Role.CODEUR.maxTokens(local))
        assertTrue(Role.CHEF.maxTokens(local) < Role.CODEUR.maxTokens(local))
        // Le reglage de l'utilisateur est respecte quand il est plus grand.
        assertEquals(9000, Role.CODEUR.maxTokens(local.copy(maxTokens = 9000)))
    }

    @Test fun requestsCarryContextAndPreviousWork() {
        val ctx = "[Projet « X » : encore vide]"
        val chef = Equipe.demandeChef("fais un jeu", ctx)
        assertTrue(chef.startsWith(ctx) && chef.contains("fais un jeu"))
        val code = Equipe.demandeCodeur("fais un jeu", "BRIEF", "PLAN", "TEXTES", ctx)
        for (morceau in listOf("fais un jeu", "BRIEF", "PLAN", "TEXTES", ctx)) assertTrue(code.contains(morceau))
        assertEquals("Demande de l'utilisateur :\nx", Equipe.demandeChef("x", null))
    }

    @Test fun longPiecesAreCut() {
        val long = "a".repeat(10_000)
        val code = Equipe.demandeCodeur("d", long, long, long, null)
        assertTrue(code.length < 8_000)
        assertTrue(code.contains("(coupé)"))
    }

    @Test fun reviewMentionsWhatWasWritten() {
        val bilan = Bilan(crees = listOf("index.html"), modifies = listOf("README.md"))
        val revue = Equipe.demandeRevue("d", "plan", bilan)
        assertTrue(revue.contains("index.html") && revue.contains("README.md"))
        assertTrue(Equipe.demandeRevue("d", "plan", Bilan()).contains("aucun fichier"))
    }

    @Test fun lastLineHelperForTheLivePanel() {
        assertEquals("def f():", Equipe.derniereLigne("a\n\n  def f():  \n\n"))
        assertEquals("", Equipe.derniereLigne("   \n  "))
        assertEquals(90, Equipe.derniereLigne("x".repeat(200)).length)
    }

    @Test fun codersKnowHowToBuildLocalApps() {
        val prompt = Role.CODEUR.systemPrompt(true)
        // La page d'accueil des applis fabriquees, et l'usage hors-ligne.
        assertTrue(prompt.contains("index.html"))
        assertTrue(prompt.contains("sans internet"))
        // Le chef et le planificateur n'ecrivent pas de fichiers : pas de regles de fichiers pour eux.
        assertFalse(Role.CHEF.systemPrompt(true).contains("index.html"))
    }

    @Test fun codexPromptProtectsExistingFilesAndEndsWithTheRule() {
        val prompt = AgentPrompts.FICHIERS_CODEX
        assertTrue(prompt.startsWith(AgentPrompts.FICHIERS))
        assertTrue(prompt.endsWith("jamais les autres."))
        assertTrue(prompt.contains("JAMAIS de memoire") && prompt.contains("=== LIRE:"))
        assertTrue(prompt.contains("que tu viens d'ecrire toi-meme"))
    }

    @Test fun theTeamGetsItsOwnRuleBecauseItCannotReadFilesItself() {
        val codeur = Role.CODEUR.systemPrompt(true)
        assertTrue(codeur.endsWith(AgentPrompts.FICHIERS_EQUIPE))
        assertTrue(AgentPrompts.FICHIERS_EQUIPE.startsWith(AgentPrompts.FICHIERS))
        assertTrue(codeur.contains("EN ENTIER") && codeur.contains("n'ecris pas de ligne === LIRE ==="))
        assertFalse(codeur.contains("Avant de le modifier, ecris seule sur une ligne"))
        assertTrue(Role.REDACTEUR.systemPrompt(true).endsWith(AgentPrompts.FICHIERS_EQUIPE))
        assertFalse(Role.CHEF.systemPrompt(true).contains("EN ENTIER"))
    }

    @Test fun previewIsAddedAfterTheProjectLine() {
        val apercu = ApercuFichiers("Contenu actuel…\n--- Fichier a.txt ---\nx\n", mapOf("a.txt" to "h"))
        assertEquals("[Projet]\n\n" + apercu.texte, Equipe.avecApercu("[Projet]", apercu))
        assertEquals(apercu.texte, Equipe.avecApercu(null, apercu))
        assertEquals("[Projet]", Equipe.avecApercu("[Projet]", ApercuFichiers.VIDE))
        assertEquals(null, Equipe.avecApercu(null, ApercuFichiers.VIDE))
        assertEquals(null, Equipe.avecApercu("  ", ApercuFichiers.VIDE))
    }

    @Test fun localPreviewIsSizedToTheMemoryTheModelReallyHas() {
        // 8 192 jetons, consignes de 4 000 caracteres, demande de 3 000, reponse de 3 000 jetons
        val serre = Equipe.budgetLocal(8_192, 4_000, 3_000, 3_000)
        assertTrue("reste un peu de place : $serre", serre in 1..Equipe.BUDGET_APERCU_LOCAL)
        // la place demandee + la reponse + la marge ne depassent jamais la memoire
        val jetons = Math.ceil((4_000 + 3_000 + serre) / 2.5).toInt() + 3_000
        assertTrue(jetons <= 8_192)
        // grande memoire : on est plafonne par le budget maximal
        assertEquals(Equipe.BUDGET_APERCU_LOCAL, Equipe.budgetLocal(16_384, 4_000, 3_000, 3_000))
        // demande enorme : plus de place du tout
        assertEquals(0, Equipe.budgetLocal(8_192, 4_000, 12_000, 3_000))
        // capacite inconnue (aucun modele charge) : on ne montre rien
        assertEquals(0, Equipe.budgetLocal(0, 4_000, 3_000, 3_000))
        assertEquals(0, Equipe.budgetLocal(-1, 0, 0, 0))
    }

    @Test fun onlinePreviewIsBiggerThanTheLocalOne() {
        assertTrue(Equipe.BUDGET_APERCU_DISTANT > Equipe.BUDGET_APERCU_LOCAL)
    }

    @Test fun reminderAndRetryDoNotPushToRewriteExistingFiles() {
        assertTrue(AgentPrompts.RAPPEL_FICHIERS.contains("=== LIRE: nom.ext ==="))
        assertTrue(AgentPrompts.RAPPEL_FICHIERS.contains("MODIFIER"))
        assertTrue(AgentPrompts.RAPPEL_FICHIERS.contains("N'ecris que les fichiers qui changent"))
        assertFalse(AgentPrompts.RELANCE_FICHIERS.contains("index.html"))
        assertTrue(AgentPrompts.RELANCE_FICHIERS.contains("=== FICHIER: nom.ext ==="))
    }
}
