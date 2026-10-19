package com.codexlocal

import com.codexlocal.providers.AnthropicProvider
import com.codexlocal.providers.ChatProvider
import com.codexlocal.providers.GeminiProvider
import com.codexlocal.providers.OpenAiProvider
import com.codexlocal.session.FenetreHistorique
import com.codexlocal.session.RappelConversation
import com.codexlocal.usage.CompteurUsage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Choisit le bon fournisseur selon la configuration. */
object ChatRepository {

    private val remote: Map<ProviderType, ChatProvider> = mapOf(
        ProviderType.OPENAI to OpenAiProvider(),
        ProviderType.ANTHROPIC to AnthropicProvider(),
        ProviderType.GEMINI to GeminiProvider()
    )

    fun remoteProvider(p: ProviderType): ChatProvider? = remote[p]

    /**
     * Local : le modele garde l'historique dans son cache KV, on n'envoie que
     * le dernier message de l'utilisateur. Si sa memoire vient d'etre remise a zero (autre session,
     * redemarrage...), un court rappel de la conversation affichee precede ce message.
     * Distant : chaque appel est independant, on envoie l'historique recent (voir [FenetreHistorique]).
     *
     * Avec un `compteur`, les requetes distantes sont comptees (jetons estimes) et refusees
     * quand la limite choisie dans les reglages est atteinte.
     */
    fun stream(history: List<Message>, cfg: AppConfig, compteur: CompteurUsage? = null): Flow<String> {
        // Le texte transmis peut differer de l'affichage (contexte du projet, fichiers lus).
        val envoyes = history.map { m -> m.envoi?.let { m.copy(content = it) } ?: m }
        return if (cfg.provider == ProviderType.LOCAL) {
            flow {
                val dernier = envoyes.indexOfLast { it.isUser }
                var texte = envoyes[dernier].content
                if (LocalEngine.conversationVide) {
                    val rappel = RappelConversation.construire(history.subList(0, dernier))
                    if (rappel.isNotEmpty()) texte = rappel + "\n\n" + texte
                }
                LocalEngine.stream(texte, cfg.maxTokens).collect { emit(it) }
            }
        } else {
            val fournisseur = remote.getValue(cfg.provider)
            val recents = FenetreHistorique.fenetrer(envoyes)
            if (compteur == null) {
                fournisseur.stream(recents, cfg)
            } else {
                val entree = CompteurUsage.estimerJetons(cfg.systemPrompt) +
                    recents.sumOf { CompteurUsage.estimerJetons(it.content) }
                compteur.compter(cfg.provider, entree, fournisseur.stream(recents, cfg))
            }
        }
    }
}
