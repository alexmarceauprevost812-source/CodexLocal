package com.codexlocal.tilabo

import org.json.JSONArray
import org.json.JSONObject

/**
 * Outils (format OpenAI « tools ») pour les .gguf, comme llama-server --jinja :
 * le Codex du Ti-Labo (ecrire_fichier, modifier_fichier, lire_fichier…) marche avec le moteur de l'appli.
 *
 * Les outils sont decrits dans la consigne ; l'IA les appelle avec des blocs
 *   <tool_call>{"name": "...", "arguments": {...}}</tool_call>
 * (le format que connaissent Qwen, Hermes, Llama 3…), et on les renvoie en tool_calls.
 */
object Outils {

    private fun consigneOutils(outils: JSONArray): String {
        val b = StringBuilder()
        b.append("\n\n# Outils\nTu peux utiliser ces outils. Pour en appeler un, ecris exactement :\n")
        b.append("<tool_call>\n{\"name\": \"nom_de_l_outil\", \"arguments\": {\"param\": \"valeur\"}}\n</tool_call>\n")
        b.append("Tu peux mettre plusieurs blocs <tool_call> a la suite. Les arguments sont du JSON valide ")
        b.append("(echappe les guillemets et les retours de ligne dans les chaines). ")
        b.append("Apres l'appel, attends le resultat (<tool_response>). Quand le travail est fini, ")
        b.append("reponds normalement, sans <tool_call>.\n\nOutils disponibles :\n")
        for (i in 0 until outils.length()) {
            val f = outils.optJSONObject(i)?.optJSONObject("function") ?: continue
            b.append("- ").append(f.optString("name")).append(" : ").append(f.optString("description"))
            f.optJSONObject("parameters")?.let { b.append("\n  parametres : ").append(it.toString()) }
            b.append('\n')
        }
        return b.toString()
    }

    /**
     * Messages OpenAI -> messages du moteur (system/user/assistant seulement).
     * Les appels d'outils et leurs resultats deviennent du texte, toujours de la meme facon,
     * pour que le moteur reconnaisse la suite de la conversation et garde sa memoire.
     */
    fun convertir(arr: JSONArray, outils: JSONArray?, texte: (Any?) -> String): List<Moteur.Msg> {
        val brut = ArrayList<Moteur.Msg>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            when (val role = m.optString("role")) {
                "system", "developer" -> brut += Moteur.Msg("system", texte(m.opt("content")))
                "user" -> brut += Moteur.Msg("user", texte(m.opt("content")))
                "assistant" -> {
                    val b = StringBuilder(texte(m.opt("content")))
                    val appels = m.optJSONArray("tool_calls")
                    if (appels != null) for (k in 0 until appels.length()) {
                        val f = appels.optJSONObject(k)?.optJSONObject("function") ?: continue
                        val args = f.opt("arguments").let { a ->
                            if (a is String) runCatching { JSONObject(a) }.getOrElse { a } else a ?: JSONObject()
                        }
                        val appel = JSONObject().put("name", f.optString("name")).put("arguments", args)
                        if (b.isNotEmpty()) b.append('\n')
                        b.append("<tool_call>\n").append(appel.toString()).append("\n</tool_call>")
                    }
                    brut += Moteur.Msg("assistant", b.toString())
                }
                "tool", "function" -> brut += Moteur.Msg("user",
                    "<tool_response>\n" + texte(m.opt("content")) + "\n</tool_response>")
                else -> if (role.isNotBlank()) brut += Moteur.Msg("user", texte(m.opt("content")))
            }
        }
        // Une seule consigne (avec les outils), et pas deux messages de suite du meme role
        val consigne = brut.filter { it.role == "system" }.joinToString("\n\n") { it.contenu }
        val res = ArrayList<Moteur.Msg>()
        val c = consigne.ifBlank { Moteur.CONSIGNE_DEFAUT } + (outils?.let { consigneOutils(it) } ?: "")
        res += Moteur.Msg("system", c)
        for (m in brut) {
            if (m.role == "system") continue
            val dernier = res.lastOrNull()
            if (dernier != null && dernier.role == m.role) res[res.size - 1] = Moteur.Msg(m.role, dernier.contenu + "\n\n" + m.contenu)
            else res += m
        }
        return res
    }

    private val bloc = Regex("""<tool_call>\s*(.*?)\s*(</tool_call>|$)""", RegexOption.DOT_MATCHES_ALL)
    private val reflexion = Regex("""<think>.*?(</think>|$)""", RegexOption.DOT_MATCHES_ALL)

    /** Reponse de l'IA -> (texte, tool_calls). */
    fun extraire(reponse: String, outils: JSONArray): Pair<String, JSONArray> {
        val noms = (0 until outils.length()).mapNotNull { outils.optJSONObject(it)?.optJSONObject("function")?.optString("name") }.toSet()
        val sansReflexion = reflexion.replace(reponse, "")
        val appels = JSONArray()
        var reste = sansReflexion
        for (m in bloc.findAll(sansReflexion)) {
            val obj = lireJson(m.groupValues[1]) ?: continue
            ajouter(obj, noms, appels)
            reste = reste.replace(m.value, "")
        }
        // Certains modeles ecrivent seulement le JSON (parfois dans un bloc ```json)
        if (appels.length() == 0) {
            val nu = sansReflexion.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
            if (nu.startsWith("{") || nu.startsWith("[")) {
                val obj = lireJson(nu)
                if (obj != null && ajouter(obj, noms, appels)) reste = ""
            }
        }
        val texte = if (appels.length() > 0) reste.trim() else reponse
        return texte to appels
    }

    private fun lireJson(t: String): Any? {
        val s = t.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        return runCatching { if (s.startsWith("[")) JSONArray(s) else JSONObject(s) }.getOrNull()
    }

    private fun ajouter(obj: Any, noms: Set<String>, appels: JSONArray): Boolean {
        if (obj is JSONArray) {
            var ok = false
            for (i in 0 until obj.length()) obj.opt(i)?.let { if (ajouter(it, noms, appels)) ok = true }
            return ok
        }
        val o = obj as? JSONObject ?: return false
        val f = o.optJSONObject("function") ?: o
        val nom = f.optString("name")
        if (nom.isBlank() || (noms.isNotEmpty() && nom !in noms)) return false
        val args = f.opt("arguments") ?: f.opt("parameters") ?: JSONObject()
        val argsTexte = if (args is String) args else args.toString()
        appels.put(JSONObject()
            .put("id", "call_" + (appels.length() + 1) + "_" + System.nanoTime() % 100000)
            .put("type", "function")
            .put("function", JSONObject().put("name", nom).put("arguments", argsTexte)))
        return true
    }
}
