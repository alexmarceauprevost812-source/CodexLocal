package ca.tilabo.app

import android.content.Context
import android.util.Log
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.Executors

/**
 * Petit serveur sur 127.0.0.1:8081, compatible avec llama-server (API OpenAI) :
 * le Ti-Labo (Python, dans Termux) s'y branche comme s'il s'agissait de llama-server.
 *
 *   GET  /health                 etat
 *   GET  /v1/models              l'IA chargee
 *   POST /v1/chat/completions    jasette (stream ou non)
 *   GET  /tilabo/etat            etat detaille + .gguf trouves
 *   POST /tilabo/charger         {"chemin": ".../modele.gguf"}
 *   POST /tilabo/decharger
 *   GET  /                       jasette autonome (sans Termux)
 */
class ServeurLocal(private val ctx: Context) {

    companion object {
        const val PORT = 8081
        private const val TAG = "TiLaboServeur"
    }

    private var socket: ServerSocket? = null
    private val pool = Executors.newCachedThreadPool()

    fun demarrer() {
        if (socket != null) return
        val s = ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1")).apply { reuseAddress = true }
        socket = s
        Thread({
            while (!s.isClosed) {
                val client = try { s.accept() } catch (e: SocketException) { break }
                pool.execute { servir(client) }
            }
        }, "tilabo-serveur").start()
        Log.i(TAG, "Moteur gguf pret sur 127.0.0.1:$PORT")
    }

    fun arreter() {
        runCatching { socket?.close() }
        socket = null
    }

    // ------------------------------------------------------------------ HTTP minimal
    private class Requete(val methode: String, val chemin: String, val corps: ByteArray)

    private fun lireLigne(inp: InputStream): String? {
        val b = ByteArrayOutputStream()
        while (true) {
            val c = inp.read()
            if (c == -1) return if (b.size() == 0) null else b.toString("UTF-8")
            if (c == '\n'.code) return b.toString("UTF-8").trimEnd('\r')
            b.write(c)
            if (b.size() > 16_384) throw IllegalStateException("ligne trop longue")
        }
    }

    private fun lire(inp: InputStream): Requete? {
        val premiere = lireLigne(inp) ?: return null
        val morceaux = premiere.split(" ")
        if (morceaux.size < 2) return null
        var longueur = 0
        var chunked = false
        while (true) {
            val l = lireLigne(inp) ?: break
            if (l.isEmpty()) break
            val i = l.indexOf(':')
            if (i <= 0) continue
            val nom = l.substring(0, i).trim().lowercase()
            val v = l.substring(i + 1).trim()
            if (nom == "content-length") longueur = v.toIntOrNull() ?: 0
            if (nom == "transfer-encoding" && v.contains("chunked", true)) chunked = true
        }
        val corps = if (chunked) lireChunked(inp) else {
            require(longueur <= 64 * 1024 * 1024) { "requete trop grosse" }
            val buf = ByteArray(longueur)
            var lu = 0
            while (lu < longueur) {
                val n = inp.read(buf, lu, longueur - lu)
                if (n < 0) break
                lu += n
            }
            buf
        }
        return Requete(morceaux[0].uppercase(), morceaux[1].substringBefore('?'), corps)
    }

    private fun lireChunked(inp: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        while (true) {
            val taille = (lireLigne(inp) ?: break).substringBefore(';').trim().toIntOrNull(16) ?: break
            if (taille == 0) { lireLigne(inp); break }
            val buf = ByteArray(taille)
            var lu = 0
            while (lu < taille) {
                val n = inp.read(buf, lu, taille - lu)
                if (n < 0) break
                lu += n
            }
            out.write(buf, 0, lu)
            lireLigne(inp)
        }
        return out.toByteArray()
    }

    private fun repondre(out: OutputStream, code: Int, type: String, corps: ByteArray) {
        val statut = when (code) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 503 -> "Service Unavailable"; else -> "Error" }
        out.write(("HTTP/1.1 $code $statut\r\nContent-Type: $type\r\nContent-Length: ${corps.size}\r\n" +
            "Access-Control-Allow-Origin: *\r\nCache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(corps)
        out.flush()
    }

    private fun json(out: OutputStream, code: Int, o: Any) =
        repondre(out, code, "application/json; charset=utf-8", o.toString().toByteArray())

    private fun erreur(out: OutputStream, code: Int, message: String) =
        json(out, code, JSONObject().put("error", JSONObject().put("message", message).put("code", code)))

    private fun servir(client: Socket) {
        client.use { s ->
            s.soTimeout = 0
            val inp = BufferedInputStream(s.getInputStream())
            val out = s.getOutputStream()
            try {
                val r = lire(inp) ?: return
                router(r, out)
            } catch (e: SocketException) {
                // le client est parti
            } catch (e: Exception) {
                Log.e(TAG, "Erreur", e)
                runCatching { erreur(out, 500, e.message ?: e.javaClass.simpleName) }
            }
        }
    }

    private fun router(r: Requete, out: OutputStream) {
        if (r.methode == "OPTIONS") {
            out.write(("HTTP/1.1 204 No Content\r\nAccess-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Headers: *\r\nAccess-Control-Allow-Methods: GET, POST\r\nConnection: close\r\n\r\n").toByteArray())
            return
        }
        when (r.chemin) {
            "/health" -> when {
                Moteur.enChargement != null -> json(out, 503, JSONObject().put("status", "loading model"))
                Moteur.chemin != null -> json(out, 200, JSONObject().put("status", "ok"))
                else -> json(out, 503, JSONObject().put("status", "no model"))
            }
            "/v1/models", "/models" -> {
                val data = JSONArray()
                Moteur.nom?.let { data.put(JSONObject().put("id", it).put("object", "model").put("owned_by", "ti-labo")) }
                json(out, 200, JSONObject().put("object", "list").put("data", data))
            }
            "/tilabo/etat" -> {
                val (utilise, capacite) = Moteur.memoire()
                json(out, 200, JSONObject()
                    .put("app", "Ti-Labo")
                    .put("version", BuildConfigInfo.version(ctx))
                    .put("modele", Moteur.nom ?: JSONObject.NULL)
                    .put("chemin", Moteur.chemin ?: JSONObject.NULL)
                    .put("chargement", Moteur.enChargement ?: JSONObject.NULL)
                    .put("erreur", Moteur.derniereErreur ?: JSONObject.NULL)
                    .put("memoire", JSONObject().put("utilise", utilise).put("capacite", capacite))
                    .put("acces_fichiers", android.os.Environment.isExternalStorageManager())
                    .put("fichiers", FichiersGguf.lister()))
            }
            "/tilabo/fichiers" -> json(out, 200, JSONObject().put("fichiers", FichiersGguf.lister()))
            "/tilabo/charger" -> {
                val d = JSONObject(String(r.corps, Charsets.UTF_8).ifBlank { "{}" })
                val chemin = d.optString("chemin")
                if (chemin.isBlank()) return erreur(out, 400, "chemin manquant")
                try {
                    val nom = runBlocking { Moteur.charger(ctx, chemin) }
                    MoteurService.majNotification(ctx)
                    json(out, 200, JSONObject().put("modele", nom))
                } catch (e: Exception) {
                    MoteurService.majNotification(ctx)
                    erreur(out, 500, e.message ?: "Chargement impossible")
                }
            }
            "/tilabo/decharger" -> {
                runBlocking { Moteur.decharger() }
                MoteurService.majNotification(ctx)
                json(out, 200, JSONObject().put("ok", true))
            }
            "/v1/chat/completions", "/chat/completions" -> jasette(r, out)
            "/", "/index.html" -> asset(out, "jasette.html", "text/html; charset=utf-8")
            "/logo.png" -> asset(out, "logo.png", "image/png")
            else -> erreur(out, 404, "introuvable : ${r.chemin}")
        }
    }

    private fun asset(out: OutputStream, nom: String, type: String) =
        repondre(out, 200, type, ctx.assets.open(nom).use { it.readBytes() })

    /** Texte d'un message OpenAI (chaine, ou liste de morceaux texte/image). */
    private fun texte(c: Any?): String = when (c) {
        is String -> c
        is JSONArray -> (0 until c.length()).mapNotNull { i ->
            val p = c.optJSONObject(i) ?: return@mapNotNull null
            when (p.optString("type")) {
                "text" -> p.optString("text")
                "image_url" -> "[image jointe : ce moteur ne voit pas encore les images]"
                else -> null
            }
        }.joinToString("\n")
        else -> ""
    }

    private fun jasette(r: Requete, out: OutputStream) {
        val d = JSONObject(String(r.corps, Charsets.UTF_8))
        // Pas d'outils pour l'instant : le Ti-Labo continue alors sans recherche web.
        if (d.has("tools") && (d.optJSONArray("tools")?.length() ?: 0) > 0) {
            return erreur(out, 400, "Ce moteur ne gere pas les outils")
        }
        if (Moteur.chemin == null) return erreur(out, 503, "Aucune IA chargee dans l'appli Ti-Labo")
        val arr = d.optJSONArray("messages") ?: JSONArray()
        val messages = (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { Moteur.Msg(it.optString("role"), texte(it.opt("content"))) }
        }
        val temp = if (d.has("temperature")) d.optDouble("temperature").toFloat() else null
        val max = when {
            d.has("max_tokens") -> d.optInt("max_tokens")
            d.has("max_completion_tokens") -> d.optInt("max_completion_tokens")
            else -> null
        }?.takeIf { it > 0 }
        val id = "chatcmpl-" + System.currentTimeMillis()
        val modele = Moteur.nom ?: "gguf"
        val cree = System.currentTimeMillis() / 1000

        if (!d.optBoolean("stream", false)) {
            val b = StringBuilder()
            try {
                runBlocking { Moteur.repondre(ctx, messages, temp, max).collect { b.append(it) } }
            } catch (e: Exception) {
                return erreur(out, 500, e.message ?: "Erreur du moteur")
            }
            return json(out, 200, JSONObject()
                .put("id", id).put("object", "chat.completion").put("created", cree).put("model", modele)
                .put("choices", JSONArray().put(JSONObject()
                    .put("index", 0)
                    .put("message", JSONObject().put("role", "assistant").put("content", b.toString()))
                    .put("finish_reason", "stop"))))
        }

        out.write(("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream; charset=utf-8\r\n" +
            "Cache-Control: no-store\r\nAccess-Control-Allow-Origin: *\r\nConnection: close\r\n\r\n").toByteArray())
        out.flush()
        fun morceau(delta: JSONObject, fin: String?) {
            val o = JSONObject().put("id", id).put("object", "chat.completion.chunk").put("created", cree)
                .put("model", modele)
                .put("choices", JSONArray().put(JSONObject().put("index", 0).put("delta", delta)
                    .put("finish_reason", fin ?: JSONObject.NULL)))
            out.write("data: $o\n\n".toByteArray())
            out.flush()
        }
        morceau(JSONObject().put("role", "assistant"), null)
        try {
            runBlocking {
                Moteur.repondre(ctx, messages, temp, max).collect { morceau(JSONObject().put("content", it), null) }
            }
            morceau(JSONObject(), "stop")
        } catch (e: SocketException) {
            return // le client a arrete la reponse
        } catch (e: Exception) {
            runCatching { morceau(JSONObject().put("content", "\n\n[Erreur du moteur : ${e.message}]"), "stop") }
        }
        runCatching { out.write("data: [DONE]\n\n".toByteArray()); out.flush() }
    }
}

object BuildConfigInfo {
    fun version(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
