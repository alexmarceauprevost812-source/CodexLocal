package com.codexlocal

/**
 * Un message de la conversation. `role` vaut "user" ou "assistant" (format API).
 *
 * `envoi` est le texte reellement transmis a l'IA quand il differe de ce qui s'affiche
 * (contexte du projet, contenu de fichiers lus). Il n'est pas sauvegarde avec la session.
 */
data class Message(
    val role: String,
    var content: String,
    val isUser: Boolean,
    var envoi: String? = null
) {
    companion object {
        fun user(text: String) = Message("user", text, true)
        fun assistant(text: String = "") = Message("assistant", text, false)
    }
}
