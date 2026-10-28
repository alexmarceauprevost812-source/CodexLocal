package ca.tilabo.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Trouve les .gguf sur le telephone et sur la cle USB-C. */
object FichiersGguf {

    private const val TEL = "/storage/emulated/0"

    fun dossiers(): List<Pair<String, File>> {
        val res = mutableListOf(
            "CodexLocal" to File("$TEL/CodexLocal/models"),
            "Ti-Labo (téléphone)" to File("$TEL/TiLabo/ia/gguf"),
            "Téléchargements" to File("$TEL/Download"),
        )
        // Cles USB : /storage/XXXX-XXXX
        File("/storage").listFiles()?.sortedBy { it.name }?.forEach { cle ->
            if (cle.name == "emulated" || cle.name == "self" || !cle.isDirectory) return@forEach
            res += "Clé ${cle.name}" to File(cle, "TiLabo/ia/gguf")
            res += "Clé ${cle.name}" to File(cle, "TiLabo/TiLabo/ia/gguf")
            res += "Clé ${cle.name}" to File(cle, "Android/data/com.termux/files/TiLabo/ia/gguf")
            res += "Clé ${cle.name}" to cle
        }
        return res
    }

    fun lister(): JSONArray {
        val vus = HashSet<String>()
        val arr = JSONArray()
        for ((ou, d) in dossiers()) {
            val fichiers = runCatching { d.listFiles() }.getOrNull() ?: continue
            fichiers.filter { it.isFile && it.name.endsWith(".gguf", true) }
                .sortedBy { it.name.lowercase() }
                .forEach { f ->
                    val vrai = runCatching { f.canonicalPath }.getOrDefault(f.path)
                    // Les fichiers « mmproj » (vision) ne se chargent pas seuls
                    if (!vus.add(vrai) || f.name.startsWith("mmproj", true)) return@forEach
                    arr.put(JSONObject()
                        .put("nom", f.name)
                        .put("chemin", f.path)
                        .put("ou", ou)
                        .put("taille_go", Math.round(f.length() / 1e7) / 100.0))
                }
        }
        return arr
    }
}
