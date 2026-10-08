package com.codexlocal

/** Un message de la conversation. `role` vaut "user" ou "assistant" (format API). */
data class Message(
    val role: String,
    var content: String,
    val isUser: Boolean
) {
    companion object {
        fun user(text: String) = Message("user", text, true)
        fun assistant(text: String = "") = Message("assistant", text, false)
    }
}
