package com.codexlocal.tilabo

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.Normalizer
import kotlin.math.ln

/**
 * Memoire du Ti-Labo :
 *  - des souvenirs (ce que tu demandes a l'IA de retenir), dans TiLabo/memoire.json ;
 *  - une recherche dans tes projets (par mots, facon BM25) pour donner a l'IA les bons extraits.
 * Tout reste sur le telephone.
 */
object Memoire {

    private val FICHIER = File("/storage/emulated/0/TiLabo/memoire.json")
    private val verrou = Any()
    private val EXT = setOf("py", "js", "ts", "kt", "java", "html", "css", "md", "txt", "json", "xml", "yml", "yaml", "sh",
        "c", "cpp", "h", "rs", "go", "php", "rb", "lua", "sql", "toml", "ini", "gradle", "kts", "jsx", "tsx", "vue", "dart", "swift")
    private val VIDES = setOf("le", "la", "les", "un", "une", "des", "de", "du", "et", "ou", "a", "au", "aux", "en", "dans", "pour", "par",
        "sur", "est", "que", "qui", "quoi", "ce", "cette", "ces", "mon", "ma", "mes", "ton", "ta", "tes", "son", "sa", "ses", "je", "tu",
        "il", "elle", "on", "nous", "vous", "ils", "the", "of", "to", "and", "is", "in", "it", "comment", "fait", "faire", "peux", "veux")

    private fun lire(): MutableList<JSONObject> = runCatching {
        val a = JSONObject(FICHIER.readText()).optJSONArray("souvenirs") ?: JSONArray()
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.toMutableList()
    }.getOrDefault(mutableListOf())

    private fun ecrire(l: List<JSONObject>) {
        FICHIER.parentFile?.mkdirs()
        FICHIER.writeText(JSONObject().put("version", 1).put("souvenirs", JSONArray(l)).toString(1))
    }

    fun lister(): JSONObject = synchronized(verrou) {
        JSONObject().put("souvenirs", JSONArray(lire().sortedByDescending { it.optLong("date") }))
    }

    fun ajouter(texte: String): JSONObject = synchronized(verrou) {
        val t = texte.trim()
        require(t.isNotEmpty() && t.length <= 2000) { "Souvenir vide ou trop long" }
        val l = lire()
        if (l.none { it.optString("texte").equals(t, true) }) {
            l += JSONObject().put("id", "m" + System.currentTimeMillis().toString(36)).put("texte", t).put("date", System.currentTimeMillis())
            ecrire(l)
        }
        JSONObject().put("ok", true)
    }

    fun supprimer(id: String): JSONObject = synchronized(verrou) {
        val l = lire(); l.removeAll { it.optString("id") == id }; ecrire(l)
        JSONObject().put("ok", true)
    }

    private fun mots(t: String): List<String> =
        Normalizer.normalize(t.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")
            .split(Regex("[^a-z0-9_]+")).filter { it.length > 1 && it !in VIDES }

    // ------------------------------------------------------------ index des projets (morceaux de ~30 lignes)
    private class Morceau(val projet: String, val chemin: String, val debut: Int, val texte: String, val mots: Map<String, Int>, val n: Int)
    private var index: List<Morceau> = emptyList()
    private var signature = ""
    private var dateIndex = 0L

    private fun indexer() {
        val fichiers = ArrayList<Pair<String, File>>()
        for ((_, racine) in Projets.racines()) {
            racine.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.forEach { p ->
                p.walkTopDown().onEnter { it.name !in setOf(".git", "node_modules", "build", ".gradle", "__pycache__", "venv", ".venv", "dist") }
                    .filter { it.isFile && it.extension.lowercase() in EXT && it.length() < 300_000 }
                    .take(1500).forEach { fichiers += p.name to it }
            }
        }
        val sig = fichiers.joinToString("|") { it.second.path + it.second.lastModified() }.hashCode().toString()
        if (sig == signature && System.currentTimeMillis() - dateIndex < 600_000) return
        val res = ArrayList<Morceau>()
        for ((projet, f) in fichiers.take(4000)) {
            val lignes = runCatching { f.readLines() }.getOrNull() ?: continue
            val racineProjet = f.path.substringBefore("/$projet/") + "/$projet/"
            val rel = f.path.removePrefix(racineProjet)
            var i = 0
            while (i < lignes.size) {
                val bout = lignes.subList(i, minOf(lignes.size, i + 30)).joinToString("\n")
                val m = mots("$rel $bout")
                if (m.isNotEmpty()) res += Morceau(projet, rel, i + 1, bout, m.groupingBy { it }.eachCount(), m.size)
                i += 25
            }
        }
        index = res; signature = sig; dateIndex = System.currentTimeMillis()
    }

    /** Souvenirs et extraits de projets lies a la question. */
    fun chercher(requete: String, nProjets: Int = 4): JSONObject {
        val q = mots(requete).distinct()
        val souvenirs = synchronized(verrou) { lire() }
        val sv = JSONArray()
        // Les souvenirs sont courts : on donne ceux qui partagent un mot, sinon les plus recents
        souvenirs.map { s -> s to mots(s.optString("texte")).count { it in q } }
            .sortedWith(compareByDescending<Pair<JSONObject, Int>> { it.second }.thenByDescending { it.first.optLong("date") })
            .take(8).forEach { (s, score) -> if (score > 0 || souvenirs.size <= 8) sv.put(s.optString("texte")) }
        val ex = JSONArray()
        if (q.isNotEmpty()) {
            synchronized(this) { runCatching { indexer() } }
            val idx = index
            if (idx.isNotEmpty()) {
                val moy = idx.sumOf { it.n }.toDouble() / idx.size
                val df = q.associateWith { m -> idx.count { it.mots.containsKey(m) } }
                idx.asSequence().map { mc ->
                    var s = 0.0
                    for (m in q) {
                        val tf = mc.mots[m] ?: continue
                        val d = df[m] ?: 1
                        val idf = ln(1 + (idx.size - d + 0.5) / (d + 0.5))
                        s += idf * (tf * 2.2) / (tf + 1.2 * (0.25 + 0.75 * mc.n / moy))
                    }
                    mc to s
                }.filter { it.second > 0 }.sortedByDescending { it.second }.take(nProjets).forEach { (mc, s) ->
                    ex.put(JSONObject().put("projet", mc.projet).put("chemin", mc.chemin).put("ligne", mc.debut)
                        .put("texte", mc.texte.take(1800)).put("score", Math.round(s * 100) / 100.0))
                }
            }
        }
        return JSONObject().put("souvenirs", sv).put("extraits", ex).put("morceaux", index.size)
    }
}
