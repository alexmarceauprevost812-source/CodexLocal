package com.codexlocal.tilabo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom

/**
 * Pont PC : le bureau Ti-Labo de CodexLocal, ouvert dans le navigateur d'un ordinateur
 * sur le meme Wi-Fi (http://IP-du-telephone:8082). Protege par un code secret :
 * le lien (ou le code QR) contient le code ; sans lui, rien n'est accessible.
 */
object PontPC {

    const val PORT = 8082
    private var socket: ServerSocket? = null
    @Volatile private var cle: String = ""

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("tilabo", Context.MODE_PRIVATE)

    private fun cle(ctx: Context): String {
        if (cle.isEmpty()) {
            cle = prefs(ctx).getString("pont_cle", null) ?: run {
                val a = "abcdefghjkmnpqrstuvwxyz23456789"
                val r = SecureRandom()
                (1..10).map { a[r.nextInt(a.length)] }.joinToString("").also { prefs(ctx).edit().putString("pont_cle", it).apply() }
            }
        }
        return cle
    }

    /** L'adresse du telephone sur le Wi-Fi (192.168.x.x, 10.x.x.x…). */
    private fun ip(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .sortedBy { if (it.name.startsWith("wlan")) 0 else if (it.name.startsWith("ap") || it.name.startsWith("swlan")) 1 else 2 }
            .flatMap { it.inetAddresses.toList() }
            .firstOrNull { it is Inet4Address && it.isSiteLocalAddress }?.hostAddress
    }.getOrNull()

    fun etat(ctx: Context): JSONObject {
        val actif = socket?.isClosed == false
        val ip = ip()
        val o = JSONObject().put("actif", actif).put("ip", ip ?: JSONObject.NULL).put("port", PORT).put("code", cle(ctx))
        if (actif && ip != null) o.put("lien", "http://$ip:$PORT/?cle=${cle(ctx)}").put("adresse", "http://$ip:$PORT")
        return o
    }

    fun demarrer(ctx: Context, servir: (Socket) -> Unit) {
        if (socket?.isClosed == false) return
        requireNotNull(ip()) { "Branche le téléphone sur un Wi-Fi (ou active le point d'accès) pour ouvrir le bureau sur ton PC." }
        cle(ctx)
        val s = ServerSocket(PORT, 16, InetAddress.getByName("0.0.0.0")).apply { reuseAddress = true }
        socket = s
        Thread({
            while (!s.isClosed) {
                val c = try { s.accept() } catch (e: SocketException) { break }
                // Seulement le reseau local (jamais Internet)
                val a = c.inetAddress
                if (!(a.isSiteLocalAddress || a.isLoopbackAddress || a.isLinkLocalAddress)) { runCatching { c.close() }; continue }
                servir(c)
            }
        }, "tilabo-pont-pc").start()
    }

    fun arreter() {
        runCatching { socket?.close() }
        socket = null
    }

    /** Verifie le code (dans le lien une premiere fois, puis dans un temoin). Renvoie vrai si la requete peut continuer. */
    fun autoriser(chemin: String, cleLien: String?, temoins: String?, out: OutputStream): Boolean {
        val bonne = cle
        if (bonne.isEmpty()) return false
        val temoin = temoins?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("tilabo_cle=") }?.substringAfter('=')
        if (temoin != null && java.security.MessageDigest.isEqual(temoin.toByteArray(), bonne.toByteArray())) return true
        if (cleLien != null && java.security.MessageDigest.isEqual(cleLien.trim().lowercase().toByteArray(), bonne.toByteArray())) {
            // On garde le code dans un temoin et on enleve le code de l'adresse
            out.write(("HTTP/1.1 302 Found\r\nLocation: ${if (chemin.isBlank()) "/" else chemin}\r\n" +
                "Set-Cookie: tilabo_cle=$bonne; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000\r\n" +
                "Content-Length: 0\r\nConnection: close\r\n\r\n").toByteArray())
            out.flush()
            return false
        }
        val page = """<!doctype html><html lang="fr"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>Ti-Labo · code</title><style>body{margin:0;min-height:100vh;display:flex;align-items:center;justify-content:center;background:#000;color:#fff;font-family:system-ui,sans-serif}
            form{background:#0d0d0d;border:1px solid #FF7A1A;border-radius:18px;padding:26px;max-width:340px;text-align:center}
            h1{color:#FF7A1A;font-size:22px;margin:0 0 8px}p{color:#bbb;font-size:14px;line-height:1.5}
            input{width:100%;box-sizing:border-box;background:#000;border:1px solid #333;border-radius:10px;padding:12px;color:#fff;font-size:18px;text-align:center;letter-spacing:2px;margin:10px 0}
            button{width:100%;background:#FF7A1A;color:#000;border:0;border-radius:10px;padding:12px;font-weight:700;font-size:16px}</style></head>
            <body><form method="get" action="/"><h1>🔥 Ti-Labo</h1><p>Écris le code affiché dans CodexLocal sur ton téléphone<br>(Ti-Labo › Paramètres › Ouvrir sur mon PC).</p>
            <input name="cle" autocomplete="off" autofocus placeholder="code"><button>Ouvrir le bureau</button></form></body></html>""".trimIndent()
        val b = page.toByteArray()
        out.write(("HTTP/1.1 401 Unauthorized\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${b.size}\r\nConnection: close\r\n\r\n").toByteArray())
        out.write(b); out.flush()
        return false
    }

    fun qr(texte: String): ByteArray {
        val m = QRCodeWriter().encode(texte, BarcodeFormat.QR_CODE, 360, 360, mapOf(EncodeHintType.MARGIN to 1))
        val bmp = Bitmap.createBitmap(m.width, m.height, Bitmap.Config.RGB_565)
        for (x in 0 until m.width) for (y in 0 until m.height) bmp.setPixel(x, y, if (m[x, y]) Color.BLACK else Color.WHITE)
        return ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
    }
}
