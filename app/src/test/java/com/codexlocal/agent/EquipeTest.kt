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

    @Test fun theTeamKeepsTheSharedPromptWithoutTheReadFirstRule() {
        val codeur = Role.CODEUR.systemPrompt(true)
        assertTrue(codeur.endsWith(AgentPrompts.FICHIERS))
        assertFalse(codeur.contains("JAMAIS de memoire"))
    }

    @Test fun reminderAndRetryDoNotPushToRewriteExistingFiles() {
        assertTrue(AgentPrompts.RAPPEL_FICHIERS.contains("=== LIRE: nom.ext ==="))
        assertTrue(AgentPrompts.RAPPEL_FICHIERS.contains("MODIFIER"))
        assertTrue(AgentPrompts.RAPPEL_FICHIERS.contains("N'ecris que les fichiers qui changent"))
        assertFalse(AgentPrompts.RELANCE_FICHIERS.contains("index.html"))
        assertTrue(AgentPrompts.RELANCE_FICHIERS.contains("=== FICHIER: nom.ext ==="))
    }
}
