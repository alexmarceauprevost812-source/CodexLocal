package ca.tilabo.app

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Projet de la cle USB-C <-> copie sur le telephone.
 *
 * 1. « Copier sur le téléphone » : le projet de la cle (TiLabo/projets/X) est copie dans
 *    TiLabo/projets/X du telephone. On peut debrancher la cle et travailler.
 * 2. Cle rebranchee : « Renvoyer sur la clé » copie seulement ce qui a change sur le telephone ;
 *    « Récupérer de la clé » ramene ce qui a change sur le PC.
 * Un fichier modifie des deux cotes n'est jamais ecrase : l'autre version est gardee a cote.
 *
 * Le fichier .tilabo-sync.json (dans la copie du telephone) retient d'ou vient le projet et
 * l'etat commun des fichiers a la derniere synchro.
 */
object Sync {

    const val MARQUEUR = ".tilabo-sync.json"
    private val IGNORES = setOf("node_modules", "build", ".gradle", "__pycache__", ".venv", "venv", ".cxx", "dist")
    private const val TEL_PROJETS = "/storage/emulated/0/TiLabo/projets"

    /** Projets des cles branchees. */
    fun projetsCle(): JSONArray {
        val arr = JSONArray()
        for ((ou, r) in Projets.racines()) {
            if (!ou.startsWith("Clé")) continue
            r.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() }?.forEach {
                arr.put(JSONObject().put("nom", it.name).put("chemin", it.path).put("ou", ou)
                    .put("copie", File(TEL_PROJETS, it.name).let { t -> File(t, MARQUEUR).isFile }))
            }
        }
        return arr
    }

    /** Projets du telephone qui viennent d'une cle. */
    fun projetsLies(): JSONArray {
        val arr = JSONArray()
        File(TEL_PROJETS).listFiles()?.filter { File(it, MARQUEUR).isFile }?.sortedBy { it.name.lowercase() }?.forEach { d ->
            val m = runCatching { JSONObject(File(d, MARQUEUR).readText()) }.getOrNull() ?: return@forEach
            val cle = dossierCle(m)
            arr.put(JSONObject().put("nom", d.name).put("chemin", d.path)
                .put("cle", m.optString("cle")).put("branchee", cle?.isDirectory == true)
                .put("derniere", m.optString("date")))
        }
        return arr
    }

    private fun dossierCle(m: JSONObject): File? {
        val racine = File("/storage", m.optString("cle"))
        return if (racine.isDirectory) File(racine, m.optString("chemin_cle")) else null
    }

    private fun empreinte(f: File): String {
        val md = MessageDigest.getInstance("SHA-1")
        f.inputStream().use { inp ->
            val b = ByteArray(65_536)
            while (true) { val n = inp.read(b); if (n < 0) break; md.update(b, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun empreintes(d: File): Map<String, String> {
        val res = HashMap<String, String>()
        fun parcourir(x: File, pre: String) {
            x.listFiles()?.forEach { f ->
                if (res.size > 20_000) return
                if (f.isDirectory) { if (f.name !in IGNORES) parcourir(f, "$pre${f.name}/") }
                else if (!(pre.isEmpty() && f.name == MARQUEUR)) res["$pre${f.name}"] = empreinte(f)
            }
        }
        if (d.isDirectory) parcourir(d, "")
        return res
    }

    private fun copier(src: File, dst: File) {
        dst.parentFile?.mkdirs()
        src.inputStream().use { i -> dst.outputStream().use { o -> i.copyTo(o, 65_536) } }
        dst.setLastModified(src.lastModified())
    }

    private fun maintenant() = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CANADA_FRENCH).format(Date())

    private fun ecrireMarqueur(tel: File, cle: String, cheminCle: String, base: Map<String, String>) {
        val b = JSONObject(); base.forEach { (k, v) -> b.put(k, v) }
        File(tel, MARQUEUR).writeText(JSONObject().put("cle", cle).put("chemin_cle", cheminCle)
            .put("date", maintenant()).put("base", b).toString())
    }

    /** Racine de la cle (/storage/XXXX-XXXX) et chemin du projet dans la cle. */
    private fun surCle(projetCle: File): Pair<String, String> {
        val p = projetCle.canonicalPath.removePrefix("/storage/")
        val cle = p.substringBefore('/')
        require(cle.isNotEmpty() && cle != "emulated" && p.contains('/')) { "Ce projet n'est pas sur une clé USB" }
        return cle to p.substringAfter('/')
    }

    fun cloner(cheminCle: String): JSONObject {
        val src = Projets.projet(cheminCle)
        val (cle, rel) = surCle(src)
        var dst = File(TEL_PROJETS, src.name)
        if (dst.exists()) {
            require(!File(dst, MARQUEUR).isFile) { "Ce projet est déjà copié sur le téléphone : utilise « Récupérer de la clé »." }
            dst = File(TEL_PROJETS, src.name + "-cle")
            require(!dst.exists()) { "Un projet « ${dst.name} » existe déjà sur le téléphone." }
        }
        var n = 0
        fun parcourir(x: File, y: File) {
            x.listFiles()?.forEach { f ->
                if (f.isDirectory) { if (f.name !in IGNORES) parcourir(f, File(y, f.name)) }
                else { copier(f, File(y, f.name)); n++ }
            }
        }
        dst.mkdirs()
        parcourir(src, dst)
        ecrireMarqueur(dst, cle, rel, empreintes(dst))
        return JSONObject().put("ok", true).put("nom", dst.name).put("chemin", dst.path).put("fichiers", n)
    }

    private class Etat(val tel: File, val cleDir: File, val m: JSONObject,
                       val p: Map<String, String>, val k: Map<String, String>, val b: Map<String, String>) {
        val modifTel = p.keys.filter { p[it] != b[it] }.toSet()
        val modifCle = k.keys.filter { k[it] != b[it] }.toSet()
        val conflits = (modifTel intersect modifCle).filter { p[it] != k[it] }.toSet()
        val effacesTel = b.keys.filter { it !in p && it in k }
        val effacesCle = b.keys.filter { it !in k && it in p }
    }

    private fun etatInterne(cheminTel: String): Etat {
        val tel = File(cheminTel).canonicalFile
        require(tel.parentFile?.canonicalPath == File(TEL_PROJETS).canonicalPath) { "Projet du téléphone inconnu" }
        val m = JSONObject(File(tel, MARQUEUR).readText())
        val cleDir = dossierCle(m) ?: throw IllegalStateException(
            "Branche la clé USB-C ${m.optString("cle")} (et donne l'accès « Tous les fichiers » au Ti-Labo).")
        require(cleDir.isDirectory) { "Le projet n'est plus sur la clé : ${m.optString("chemin_cle")}" }
        val bj = m.optJSONObject("base") ?: JSONObject()
        val base = bj.keys().asSequence().associateWith { bj.optString(it) }
        return Etat(tel, cleDir, m, empreintes(tel), empreintes(cleDir), base)
    }

    private fun liste(s: Collection<String>) = JSONArray(s.sorted())

    fun etat(cheminTel: String): JSONObject {
        val e = etatInterne(cheminTel)
        return JSONObject()
            .put("a_renvoyer", liste(e.modifTel - e.conflits))
            .put("a_recuperer", liste(e.modifCle - e.conflits))
            .put("conflits", liste(e.conflits))
            .put("effaces_tel", liste(e.effacesTel))
            .put("effaces_cle", liste(e.effacesCle))
            .put("cle", e.m.optString("cle")).put("derniere", e.m.optString("date"))
    }

    private fun copieDeCote(f: File, etiquette: String): File {
        val date = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
        val nom = f.name
        val point = nom.lastIndexOf('.')
        val nouveau = if (point > 0) nom.substring(0, point) + ".$etiquette-$date" + nom.substring(point)
                      else "$nom.$etiquette-$date"
        return File(f.parentFile, nouveau)
    }

    /** vers = "cle" (telephone -> cle) ou "tel" (cle -> telephone). */
    fun synchroniser(cheminTel: String, vers: String): JSONObject {
        val e = etatInterne(cheminTel)
        val versCle = vers == "cle"
        val (src, dst) = if (versCle) e.tel to e.cleDir else e.cleDir to e.tel
        val aCopier = (if (versCle) e.modifTel else e.modifCle) - e.conflits
        val gardees = JSONArray()
        for (rel in aCopier.sorted()) copier(File(src, rel), File(dst, rel))
        // Modifie des deux cotes : la version qui arrive gagne, l'autre est gardee a cote
        for (rel in e.conflits.sorted()) {
            val cible = File(dst, rel)
            val cote = copieDeCote(cible, if (versCle) "version-cle" else "version-tel")
            copier(cible, cote)
            copier(File(src, rel), cible)
            gardees.put(cote.relativeTo(dst).path)
        }
        // Nouvel etat commun : les fichiers identiques des deux cotes
        val p = empreintes(e.tel)
        val k = empreintes(e.cleDir)
        ecrireMarqueur(e.tel, e.m.optString("cle"), e.m.optString("chemin_cle"), p.filter { (f, h) -> k[f] == h })
        return JSONObject().put("ok", true).put("copies", aCopier.size + e.conflits.size)
            .put("versions_gardees", gardees)
            .put("effaces_non_repliques", liste(if (versCle) e.effacesTel else e.effacesCle))
    }
}
