package com.codexlocal

import com.codexlocal.providers.AnthropicProvider
import com.codexlocal.providers.ChatProvider
import com.codexlocal.providers.GeminiProvider
import com.codexlocal.providers.OpenAiProvider
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
     * le dernier message de l'utilisateur.
     * Distant : chaque appel est independant, on envoie tout l'historique.
     *
     * Avec un `compteur`, les requetes distantes sont comptees (jetons estimes) et refusees
     * quand la limite choisie dans les reglages est atteinte.
     */
    fun stream(history: List<Message>, cfg: AppConfig, compteur: CompteurUsage? = null): Flow<String> {
        // Le texte transmis peut differer de l'affichage (contexte du projet, fichiers lus).
        val envoyes = history.map { m -> m.envoi?.let { m.copy(content = it) } ?: m }
        return if (cfg.provider == ProviderType.LOCAL) {
            flow {
                val last = envoyes.last { it.isUser }.content
                LocalEngine.stream(last, cfg.maxTokens).collect { emit(it) }
            }
        } else {
            val fournisseur = remote.getValue(cfg.provider)
            if (compteur == null) {
                fournisseur.stream(envoyes, cfg)
            } else {
                val entree = CompteurUsage.estimerJetons(cfg.systemPrompt) +
                    envoyes.sumOf { CompteurUsage.estimerJetons(it.content) }
                compteur.compter(cfg.provider, entree, fournisseur.stream(envoyes, cfg))
            }
        }
    }
}
