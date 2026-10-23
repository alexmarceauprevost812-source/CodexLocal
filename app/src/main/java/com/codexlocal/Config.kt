package com.codexlocal

import android.content.Context

/** Fournisseurs d'IA disponibles. */
enum class ProviderType(val label: String) {
    LOCAL("Local (hors-ligne)"),
    OPENAI("API compatible OpenAI"),
    ANTHROPIC("Claude (Anthropic)"),
    GEMINI("Google Gemini")
}

/** Le mode est independant du fournisseur local ou distant. */
enum class AssistantMode {
    /** Assistant de code ; avec un projet ouvert il cree, ecrit et lit les fichiers. */
    CODEX,
    CHAT,
    /** Equipe de roles : chef (casque blanc), planificateur, redacteur, codeur. */
    EQUIPE
}

const val CHAT_SYSTEM_PROMPT =
    "Tu es MARCEAU, un assistant conversationnel utile et naturel. " +
        "Reponds en francais, clairement, sans imposer de code. " +
        "Ecris du code uniquement si l'utilisateur le demande."

const val DEFAULT_SYSTEM_PROMPT =
    "Tu es CodexLocal, un assistant de programmation qui tourne sur le telephone, " +
        "souvent sans internet. Reponds en francais, de facon concise et concrete. " +
        "Quand tu donnes du code, mets-le dans un bloc cloture par trois backquotes " +
        "avec le langage indique. Verifie que le code est complet et correct, " +
        "et signale les pieges ou les erreurs probables."

/** Instructions adaptees sans changer les parametres API ni le modele choisi. */
fun AppConfig.forMode(mode: AssistantMode): AppConfig = when (mode) {
    AssistantMode.CODEX -> copy(
        systemPrompt = systemPrompt +
            "\n\nMode Codex : privilegie les solutions de programmation completes, " +
            "les corrections expliquees et les blocs de code utilisables."
    )
    AssistantMode.CHAT -> copy(systemPrompt = CHAT_SYSTEM_PROMPT)
    // Chaque role de l'equipe a ses propres consignes (voir agent/Equipe.kt).
    AssistantMode.EQUIPE -> this
}

object Defaults {
    fun baseUrl(p: ProviderType): String = when (p) {
        ProviderType.LOCAL -> ""
        ProviderType.OPENAI -> "https://api.openai.com/v1"
        ProviderType.ANTHROPIC -> "https://api.anthropic.com/v1"
        ProviderType.GEMINI -> "https://generativelanguage.googleapis.com/v1beta"
    }

    fun model(p: ProviderType): String = when (p) {
        ProviderType.LOCAL -> ""
        ProviderType.OPENAI -> "gpt-4o-mini"
        ProviderType.ANTHROPIC -> "claude-sonnet-4-5"
        ProviderType.GEMINI -> "gemini-2.5-flash"
    }
}

data class AppConfig(
    val provider: ProviderType = ProviderType.LOCAL,
    val temperature: Float = 0.3f,
    val maxTokens: Int = 1024,
    val localModelPath: String = "",
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT
)

/**
 * Preferences de l'application.
 * Chaque fournisseur distant garde sa propre cle / URL / modele.
 */
class Prefs(context: Context) {

    private val sp = context.applicationContext
        .getSharedPreferences("codexlocal", Context.MODE_PRIVATE)

    /** Choix persistant entre assistant de code et discussion classique. */
    var assistantMode: AssistantMode
        get() = runCatching {
            AssistantMode.valueOf(sp.getString(KEY_ASSISTANT_MODE, AssistantMode.CODEX.name)!!)
        }.getOrDefault(AssistantMode.CODEX)
        set(v) {
            sp.edit().putString(KEY_ASSISTANT_MODE, v.name).apply()
        }

    var provider: ProviderType
        get() = runCatching {
            ProviderType.valueOf(sp.getString(KEY_PROVIDER, ProviderType.LOCAL.name)!!)
        }.getOrDefault(ProviderType.LOCAL)
        set(v) {
            sp.edit().putString(KEY_PROVIDER, v.name).apply()
        }

    var temperature: Float
        get() = sp.getFloat(KEY_TEMP, 0.3f)
        set(v) {
            sp.edit().putFloat(KEY_TEMP, v).apply()
        }

    var maxTokens: Int
        get() = sp.getInt(KEY_MAX_TOKENS, 1024)
        set(v) {
            sp.edit().putInt(KEY_MAX_TOKENS, v).apply()
        }

    var localModelPath: String
        get() = sp.getString(KEY_LOCAL_PATH, "") ?: ""
        set(v) {
            sp.edit().putString(KEY_LOCAL_PATH, v).apply()
        }

    var systemPrompt: String
        get() = sp.getString(KEY_SYSTEM_PROMPT, DEFAULT_SYSTEM_PROMPT) ?: DEFAULT_SYSTEM_PROMPT
        set(v) {
            sp.edit().putString(KEY_SYSTEM_PROMPT, v).apply()
        }

    /** Visibilite du fond d'ecran, de 0 a 100 %. */
    var fondOpacite: Int
        get() = sp.getInt(KEY_FOND_OPACITE, FondEcran.OPACITE_DEFAUT)
        set(v) {
            sp.edit().putInt(KEY_FOND_OPACITE, v.coerceIn(0, 100)).apply()
        }

    /** Verifier automatiquement les mises a jour au demarrage. */
    var autoUpdate: Boolean
        get() = sp.getBoolean(KEY_AUTO_UPDATE, true)
        set(v) {
            sp.edit().putBoolean(KEY_AUTO_UPDATE, v).apply()
        }

    /** Conversation en cours, pour la retrouver au prochain lancement. */
    var sessionActive: String
        get() = sp.getString(KEY_SESSION, "") ?: ""
        set(v) {
            sp.edit().putString(KEY_SESSION, v).apply()
        }

    /** Jeton d'acces personnel GitHub, pour pousser les projets. */
    var githubToken: String
        get() = sp.getString(KEY_GITHUB_TOKEN, "") ?: ""
        set(v) {
            sp.edit().putString(KEY_GITHUB_TOKEN, v).apply()
        }

    /** Depot GitHub choisi la derniere fois pour un projet. */
    fun githubDepot(projet: String): String = sp.getString("githubDepot_$projet", "") ?: ""

    fun setGithubDepot(projet: String, v: String) {
        sp.edit().putString("githubDepot_$projet", v).apply()
    }

    fun apiKey(p: ProviderType): String = sp.getString("key_${p.name}", "") ?: ""

    fun setApiKey(p: ProviderType, v: String) {
        sp.edit().putString("key_${p.name}", v).apply()
    }

    fun baseUrl(p: ProviderType): String =
        sp.getString("url_${p.name}", Defaults.baseUrl(p)) ?: Defaults.baseUrl(p)

    fun setBaseUrl(p: ProviderType, v: String) {
        sp.edit().putString("url_${p.name}", v).apply()
    }

    fun model(p: ProviderType): String =
        sp.getString("model_${p.name}", Defaults.model(p)) ?: Defaults.model(p)

    fun setModel(p: ProviderType, v: String) {
        sp.edit().putString("model_${p.name}", v).apply()
    }

    /** Configuration effective pour le fournisseur actif. */
    fun config(): AppConfig {
        val p = provider
        return AppConfig(
            provider = p,
            temperature = temperature,
            maxTokens = maxTokens,
            localModelPath = localModelPath,
            apiKey = apiKey(p),
            baseUrl = baseUrl(p),
            model = model(p),
            systemPrompt = systemPrompt
        )
    }

    private companion object {
        const val KEY_ASSISTANT_MODE = "assistantMode"
        const val KEY_PROVIDER = "provider"
        const val KEY_TEMP = "temperature"
        const val KEY_MAX_TOKENS = "maxTokens"
        const val KEY_LOCAL_PATH = "localModelPath"
        const val KEY_SYSTEM_PROMPT = "systemPrompt"
        const val KEY_AUTO_UPDATE = "autoUpdate"
        const val KEY_FOND_OPACITE = "fondOpacite"
        const val KEY_SESSION = "sessionActive"
        const val KEY_GITHUB_TOKEN = "githubToken"
    }
}
