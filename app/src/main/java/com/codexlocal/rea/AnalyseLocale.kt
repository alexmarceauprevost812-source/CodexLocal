package com.codexlocal.rea

import com.android.apksig.ApkVerifier
import com.codexlocal.studio.apk.ManifesteBinaire
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/**
 * Analyse statique faite sur le téléphone, sans PC ni serveur : on lit, on ne lance jamais rien.
 * Ce n'est pas REA : pas de décompilation ni d'analyse de code natif ; les limites sont annoncées
 * au modèle (voir [limites]).
 */
object AnalyseLocale {
    const val TAILLE_MAX = 200L * 1024 * 1024
    private const val MAX_ENTREES = 20_000
    private const val MAX_FICHIERS_JS = 200
    private const val MAX_OCTETS_JS = 8L * 1024 * 1024
    private const val MAX_OCTETS_FICHIER_JS = 1024 * 1024

    val limites = listOf(
        "Aucune décompilation du bytecode DEX : on ne voit que sa présence et sa taille.",
        "Pas d'analyse du code natif (.so), ni des ressources compilées (resources.arsc).",
        "Le manifeste n'est lu que comme liste de chaînes : permissions et noms, sans la structure.",
        "La signature indique l'empreinte du certificat, pas l'identité réelle de l'auteur.",
        "Le JavaScript est analysé par motifs (texte), sans exécution : une absence de motif ne prouve rien.",
        "Seuls les fichiers du projet ouvert (dossier analyse/) sont lisibles ; rien n'est exécuté ni installé."
    )

    fun sha256(f: File): String {
        val d = MessageDigest.getInstance("SHA-256")
        f.inputStream().use { s ->
            val b = ByteArray(64 * 1024)
            while (true) { val n = s.read(b); if (n < 0) break; d.update(b, 0, n) }
        }
        return hex(d.digest())
    }

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    fun estZip(f: File): Boolean = f.length() >= 4 && f.inputStream().use {
        val m = ByteArray(4); it.read(m) == 4 && m[0] == 'P'.code.toByte() && m[1] == 'K'.code.toByte() &&
            (m[2].toInt() == 3 || m[2].toInt() == 5)
    }

    private fun estTexte(f: File): Boolean = f.inputStream().use {
        val b = ByteArray(4096); val n = it.read(b); n <= 0 || (0 until n).none { i -> b[i].toInt() == 0 }
    }

    // ------------------------------------------------------------ inspect_artifact

    fun inspecter(f: File): JSONObject {
        val o = JSONObject().put("size", f.length()).put("sha256", sha256(f))
        val notes = JSONArray()
        if (estZip(f)) {
            try { ZipFile(f).use { zipInfo(it, o, notes) } } catch (e: Exception) {
                o.put("kind", "zip illisible"); notes.put("Archive invalide : ${e.message}")
            }
        } else if (estTexte(f)) {
            o.put("kind", "texte")
            o.put("preview", f.inputStream().use { s -> String(s.readNBytes(2000), Charsets.UTF_8) })
        } else {
            o.put("kind", "binaire")
            o.put("magic", f.inputStream().use { s -> hex(s.readNBytes(8)) })
        }
        return o.put("notes", notes)
    }

    private fun zipInfo(z: ZipFile, o: JSONObject, notes: JSONArray) {
        var n = 0; var total = 0L
        val ext = HashMap<String, Int>(); val abis = sortedSetOf<String>()
        val dex = JSONArray(); val sample = JSONArray()
        var manifeste = false
        val en = z.entries()
        while (en.hasMoreElements()) {
            val e = en.nextElement()
            if (++n > MAX_ENTREES) { notes.put("Plus de $MAX_ENTREES entrées : liste tronquée."); break }
            total += e.size.coerceAtLeast(0)
            val nom = e.name
            if (nom == "AndroidManifest.xml") manifeste = true
            if (nom.startsWith("classes") && nom.endsWith(".dex")) dex.put(JSONObject().put("name", nom).put("size", e.size))
            if (nom.startsWith("lib/")) nom.split('/').getOrNull(1)?.let { abis.add(it) }
            val x = nom.substringAfterLast('.', "").lowercase().ifEmpty { "(aucune)" }
            ext[x] = (ext[x] ?: 0) + 1
            if (sample.length() < 40 && !e.isDirectory) sample.put(nom)
        }
        val apk = manifeste && dex.length() > 0
        o.put("kind", if (apk) "apk" else "zip").put("entries", n).put("uncompressed_bytes", total)
        o.put("extensions", JSONObject(ext.entries.sortedByDescending { it.value }.take(12).associate { it.key to it.value }))
        o.put("sample_entries", sample)
        if (apk) {
            o.put("dex_files", dex).put("native_abis", JSONArray(abis.toList()))
            try {
                val e = z.getEntry("AndroidManifest.xml")
                if (e.size > 5L * 1024 * 1024) notes.put("Manifeste trop gros : non lu.") else {
                    val chaines = ManifesteBinaire.lireChaines(z.getInputStream(e).use { it.readBytes() })
                    o.put("permissions", JSONArray(chaines.filter { it.startsWith("android.permission.") || it.contains(".permission.") }.distinct().take(80)))
                    o.put("manifest_strings", JSONArray(chaines.filter { it.length in 3..120 }.distinct().take(60)))
                }
            } catch (e: Exception) { notes.put("Manifeste non lisible : ${e.message}") }
        }
    }

    // ------------------------------------------------------- inspect_apk_signature

    fun signature(f: File): JSONObject {
        val v = ApkVerifier.Builder(f).build().verify()
        val certs = JSONArray()
        for (c in v.signerCertificates) {
            certs.put(JSONObject().put("sha256", hex(MessageDigest.getInstance("SHA-256").digest(c.encoded)))
                .put("subject", c.subjectX500Principal.name).put("not_before", c.notBefore.toString())
                .put("not_after", c.notAfter.toString()))
        }
        return JSONObject().put("verified", v.isVerified)
            .put("v1", v.isVerifiedUsingV1Scheme).put("v2", v.isVerifiedUsingV2Scheme)
            .put("v3", v.isVerifiedUsingV3Scheme).put("certificates", certs)
            .put("errors", JSONArray(v.errors.map { it.toString() }.take(20)))
            .put("warnings_count", v.warnings.size)
    }

    // ------------------------------------------- analyze_javascript_application

    private val motifs = linkedMapOf(
        "eval" to Regex("""\beval\s*\("""), "new_function" to Regex("""\bnew\s+Function\s*\("""),
        "document_write" to Regex("""document\.write\s*\("""), "innerHTML" to Regex("""\.innerHTML\s*="""),
        "fetch" to Regex("""\bfetch\s*\("""), "xmlhttprequest" to Regex("""XMLHttpRequest"""),
        "websocket" to Regex("""\bWebSocket\s*\("""), "storage" to Regex("""\b(localStorage|sessionStorage)\b"""),
        "base64" to Regex("""\b(atob|btoa)\s*\("""), "obfuscated_ids" to Regex("""\b_0x[0-9a-f]{3,}\b""")
    )
    private val urlRegex = Regex("""https?://[^\s"'<>)\\]+""")

    fun javascript(f: File): JSONObject {
        val fichiers = ArrayList<Pair<String, String>>()
        val notes = JSONArray()
        if (estZip(f)) ZipFile(f).use { z ->
            var octets = 0L
            val en = z.entries()
            while (en.hasMoreElements() && fichiers.size < MAX_FICHIERS_JS) {
                val e = en.nextElement()
                val bas = e.name.lowercase()
                if (e.isDirectory || !(bas.endsWith(".js") || bas.endsWith(".html") || bas.endsWith(".htm") || bas.endsWith(".mjs"))) continue
                if (e.size > MAX_OCTETS_FICHIER_JS) { notes.put("${e.name} ignoré (plus de 1 Mo)."); continue }
                if (octets + e.size > MAX_OCTETS_JS) { notes.put("Plafond de 8 Mo atteint : analyse partielle."); break }
                octets += e.size
                fichiers.add(e.name to String(z.getInputStream(e).use { it.readBytes() }, Charsets.UTF_8))
            }
        } else if (estTexte(f) && f.length() <= MAX_OCTETS_FICHIER_JS) {
            fichiers.add(f.name to f.readText(Charsets.UTF_8))
        } else notes.put("Fichier non texte ou trop gros pour l'analyse JavaScript.")

        val comptes = JSONObject()
        val domaines = HashMap<String, Int>(); val details = JSONArray()
        var lignesLongues = 0
        for ((nom, txt) in fichiers) {
            val c = JSONObject()
            for ((cle, re) in motifs) { val k = re.findAll(txt).count(); if (k > 0) { c.put(cle, k); comptes.put(cle, comptes.optInt(cle) + k) } }
            urlRegex.findAll(txt).forEach { m -> m.value.substringAfter("://").substringBefore('/').substringBefore('?').lowercase().takeIf { it.isNotEmpty() }?.let { d -> domaines[d] = (domaines[d] ?: 0) + 1 } }
            lignesLongues += txt.lineSequence().count { it.length > 500 }
            if (details.length() < 30) details.put(JSONObject().put("file", nom).put("chars", txt.length).put("patterns", c))
        }
        return JSONObject().put("files_analyzed", fichiers.size).put("pattern_counts", comptes)
            .put("domains", JSONObject(domaines.entries.sortedByDescending { it.value }.take(30).associate { it.key to it.value }))
            .put("lines_over_500_chars", lignesLongues).put("files", details).put("notes", notes)
    }
}
