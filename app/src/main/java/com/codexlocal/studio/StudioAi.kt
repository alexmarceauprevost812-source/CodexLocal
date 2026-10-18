package com.codexlocal.studio

import android.content.Context
import com.codexlocal.AppConfig
import com.codexlocal.ChatRepository
import com.codexlocal.LocalEngine
import com.codexlocal.Message
import com.codexlocal.Prefs
import com.codexlocal.ProviderType
import com.codexlocal.R

/** Appel unique à l'IA configurée (locale ou distante), sans historique. */
object StudioAi {

    suspend fun generer(
        context: Context,
        prefs: Prefs,
        consignes: String,
        demande: String,
        onStatus: (String) -> Unit,
        onMorceau: (String) -> Unit
    ) {
        val base: AppConfig = prefs.config()
        val cfg = if (base.provider == ProviderType.LOCAL) {
            base.copy(systemPrompt = consignes)
        } else {
            base.copy(systemPrompt = consignes, maxTokens = base.maxTokens.coerceAtLeast(4096))
        }

        if (cfg.provider == ProviderType.LOCAL) {
            require(cfg.localModelPath.isNotBlank()) { context.getString(R.string.msg_no_model_loaded) }
            LocalEngine.load(context, cfg.localModelPath, cfg) { onStatus(it) }
            LocalEngine.resetConversation(context, cfg)
            LocalEngine.applySampling(cfg)
        }
        ChatRepository.stream(listOf(Message.user(demande)), cfg).collect { onMorceau(it) }
    }
}
