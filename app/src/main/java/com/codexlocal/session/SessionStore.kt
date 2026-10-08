package com.codexlocal.session

import android.content.Context
import com.codexlocal.Message
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Une conversation sauvegardee. */
data class Session(
    val id: String,
    var titre: String,
    val cree: Long,
    var modifie: Long,
    val messages: MutableList<Message>
)

/**
 * Les sessions sont stockees en JSON dans le dossier prive de l'appli :
 * une conversation survit donc a la rotation, a la mise en arriere-plan et
 * meme a la fermeture de l'appli.
 */
object SessionStore {

    private fun dossier(context: Context): File =
        File(context.filesDir, "sessions").apply { mkdirs() }

    private fun fichier(context: Context, id: String) = File(dossier(context), "$id.json")

    fun creer(titre: String = "Nouvelle conversation"): Session {
        val maintenant = System.currentTimeMillis()
        return Session(UUID.randomUUID().toString(), titre, maintenant, maintenant, mutableListOf())
    }

    fun lister(context: Context): List<Session> =
        dossier(context).listFiles { f -> f.isFile && f.name.endsWith(".json") }
            ?.mapNotNull { chargerFichier(it) }
            ?.sortedByDescending { it.modifie }
            ?: emptyList()

    fun charger(context: Context, id: String): Session? =
        chargerFichier(fichier(context, id))

    fun enregistrer(context: Context, session: Session) {
        session.modifie = System.currentTimeMillis()
        val arr = JSONArray()
        session.messages.forEach { m ->
            arr.put(
                JSONObject()
                    .put("role", m.role)
                    .put("content", m.content)
                    .put("user", m.isUser)
            )
        }
        val json = JSONObject()
            .put("id", session.id)
            .put("titre", session.titre)
            .put("cree", session.cree)
            .put("modifie", session.modifie)
            .put("messages", arr)
        runCatching {
            fichier(context, session.id).writeText(json.toString(), Charsets.UTF_8)
        }
    }

    fun supprimer(context: Context, id: String) {
        runCatching { fichier(context, id).delete() }
    }

    /** Titre automatique a partir du premier message. */
    fun titreAutomatique(premierMessage: String): String {
        val propre = premierMessage.trim().replace('\n', ' ').replace(Regex("\\s+"), " ")
        return if (propre.length <= 42) propre else propre.take(42).trimEnd() + "…"
    }

    private fun chargerFichier(f: File): Session? = runCatching {
        val json = JSONObject(f.readText(Charsets.UTF_8))
        val arr = json.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Message(
                role = o.optString("role", "user"),
                content = o.optString("content", ""),
                isUser = o.optBoolean("user", true)
            )
        }.toMutableList()
        Session(
            id = json.optString("id", f.nameWithoutExtension),
            titre = json.optString("titre", "Conversation"),
            cree = json.optLong("cree"),
            modifie = json.optLong("modifie"),
            messages = messages
        )
    }.getOrNull()
}
