package com.codexlocal.tilabo

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ti-Labo pour Android.
 * - Le moteur gguf (llama.cpp) roule dans l'appli : TiLaboService, sur 127.0.0.1:8081.
 * - Le Ti-Labo complet (Python) roule dans Termux et s'affiche ici, en plein ecran.
 * - Sans Termux, la « Jasette autonome » de l'appli parle directement a tes .gguf.
 */
class TiLaboActivity : Activity() {

    companion object {
        private const val URL_TILABO = "http://127.0.0.1:8765/"
        private const val URL_AUTONOME = "http://127.0.0.1:${ServeurLocal.PORT}/"
        private const val PERM_TERMUX = "com.termux.permission.RUN_COMMAND"
        private const val TERMUX_HOME = "/data/data/com.termux/files/home"
        private const val TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash"
        private const val CODE_PERM = 1
        private const val CODE_FICHIER = 2
        private const val CODE_NOTIF = 3
        private const val CODE_POSITION = 4
    }

    private lateinit var vue: WebView
    private var rappelFichier: ValueCallback<Array<Uri>>? = null
    private val principal = Handler(Looper.getMainLooper())
    @Volatile private var enAttente = false
    private var attendAcces = false
    private var logo = ""

    override fun onCreate(etat: Bundle?) {
        super.onCreate(etat)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        logo = runCatching {
            "data:image/png;base64," + Base64.encodeToString(assets.open("tilabo/logo.png").use { it.readBytes() }, Base64.NO_WRAP)
        }.getOrDefault("")

        vue = WebView(this)
        vue.setBackgroundColor(Color.BLACK)
        vue.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            mediaPlaybackRequiresUserGesture = false
            textZoom = 100
        }
        vue.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, req: WebResourceRequest): Boolean =
                ouvrirLien(req.url)

            override fun onReceivedError(v: WebView, req: WebResourceRequest, err: WebResourceError) {
                // Le Ti-Labo de Termux s'est arrete pendant qu'on l'utilisait
                if (req.isForMainFrame && req.url.port == 8765) aideReglages("Le Ti-Labo ne répond plus (${err.description}).")
            }
        }
        vue.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                v: WebView, rappel: ValueCallback<Array<Uri>>, p: FileChooserParams
            ): Boolean {
                rappelFichier?.onReceiveValue(null)
                rappelFichier = rappel
                val types = p.acceptTypes.filter { it.isNotBlank() }
                val i = Intent(Intent.ACTION_GET_CONTENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = if (types.size == 1) types[0] else "*/*"
                    if (types.size > 1) putExtra(Intent.EXTRA_MIME_TYPES, types.toTypedArray())
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.mode == FileChooserParams.MODE_OPEN_MULTIPLE)
                }
                return try {
                    startActivityForResult(Intent.createChooser(i, "Choisir un fichier"), CODE_FICHIER)
                    true
                } catch (e: ActivityNotFoundException) {
                    rappelFichier = null
                    false
                }
            }
        }
        // Videos MP4 et fichiers exportes par le Ti-Labo -> dossier Download/TiLabo
        vue.setDownloadListener { url, _, disposition, mime, _ ->
            val nom = android.webkit.URLUtil.guessFileName(url, disposition, mime)
            if (url.startsWith("blob:") || url.startsWith("data:")) {
                vue.evaluateJavascript(
                    """(async()=>{try{const b=await (await fetch(${q(url)})).blob();const r=new FileReader();
                       r.onload=()=>TiLaboApp.enregistrer(r.result,${q(nom)});r.readAsDataURL(b);}
                       catch(e){TiLaboApp.message('Téléchargement impossible : '+e)}})()""", null
                )
            } else {
                ouvrirLien(Uri.parse(url))
            }
        }
        vue.addJavascriptInterface(Pont(), "TiLaboApp")
        setContentView(vue)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), CODE_NOTIF)
        }
        TiLaboService.demarrer(this)
        demarrer()
    }

    override fun onNewIntent(i: Intent) {
        super.onNewIntent(i)
        setIntent(i)
        // touche d'une notification de rappel : on ouvre l'agenda
        if (i.getStringExtra("page") == "agenda" && ::vue.isInitialized) vue.loadUrl(URL_AUTONOME + "agenda")
    }

    override fun onResume() {
        super.onResume()
        // L'IA du Ti-Labo se charge en memoire des qu'on ouvre le Ti-Labo : reponses rapides
        Moteur.precharger(this)
        if (attendAcces && Environment.isExternalStorageManager()) {
            attendAcces = false
            demarrer()
        }
    }

    private fun q(s: String) = org.json.JSONObject.quote(s)

    /** Les liens vers Internet s'ouvrent dans Chrome ; le Ti-Labo reste ici. */
    private fun ouvrirLien(u: Uri): Boolean {
        if (u.host == "127.0.0.1" || u.host == "localhost") return false
        try { startActivity(Intent(Intent.ACTION_VIEW, u)) } catch (_: ActivityNotFoundException) { }
        return true
    }

    private fun demarrer() {
        if (!Environment.isExternalStorageManager()) {
            attendAcces = true
            afficher(page("Accès à tes IA",
                "<p>Pour lire tes fichiers <b>.gguf</b> (CodexLocal, Téléchargements, clé USB-C), " +
                    "le Ti-Labo a besoin de l'accès « <b>Tous les fichiers</b> ».</p>" +
                    "<p class='petit'>Il lit seulement tes IA et tes projets. Rien ne sort du téléphone.</p>",
                "<button onclick='TiLaboApp.ouvrirAcces()'>Donner l'accès</button>" +
                    "<button class='second' onclick='TiLaboApp.plusTard()'>Plus tard</button>"))
            return
        }
        ouvrirBureau()
    }

    /** Le Bureau de l'appli (moteur gguf, Jasette, Codex, Clé USB-C, GitHub). */
    private fun ouvrirBureau() {
        afficher(page("Démarrage…", "<p>On prépare le moteur d'IA du téléphone.</p><div class='roue'></div>"))
        Thread {
            for (n in 0 until 40) {
                if (vivant(URL_AUTONOME + "tilabo/etat")) {
                    val page = if (intent?.getStringExtra("page") == "agenda") "agenda" else ""
                    principal.post { vue.loadUrl(URL_AUTONOME + page) }
                    return@Thread
                }
                try { Thread.sleep(250) } catch (e: InterruptedException) { break }
            }
            principal.post {
                afficher(page("Le moteur ne démarre pas",
                    "<p>Le port 8081 est peut-être pris par llama-server dans Termux. Arrête-le (Ctrl+C dans Termux) puis réessaie.</p>",
                    "<button onclick='TiLaboApp.reessayer()'>Réessayer</button>"))
            }
        }.start()
    }

    /** Le Ti-Labo complet (Python, dans Termux) : Studio, Mémoire… */
    private fun lancer() {
        afficher(page("Ti-Labo complet…",
            "<p>On réveille le Ti-Labo dans Termux (Studio, Mémoire…).<br>Tes IA .gguf passent par le moteur de l'appli.</p><div class='roue'></div>",
            "<button class='second' onclick='TiLaboApp.autonome()'>Retour au Bureau</button>"))
        Thread {
            if (vivant(URL_TILABO)) principal.post { vue.loadUrl(URL_TILABO) }
            else principal.post { lancerTermux() }
        }.start()
    }

    private fun lancerTermux() {
        if (!termuxInstalle()) {
            afficher(page("Termux est absent",
                "<p>Tout le reste marche déjà dans l'appli : touche « Retour au Bureau ».</p>" +
                    "<p class='petit'>Pour le Ti-Labo complet (Codex, Studio, GitHub…), installe Termux depuis F-Droid ou GitHub, " +
                    "puis suis LISEZMOI-TELEPHONE.</p>",
                boutonsAide()))
            return
        }
        if (checkSelfPermission(PERM_TERMUX) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(PERM_TERMUX), CODE_PERM)
            return
        }
        val i = Intent().apply {
            setClassName("com.termux", "com.termux.app.RunCommandService")
            action = "com.termux.RUN_COMMAND"
            putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                arrayOf("$TERMUX_HOME/TiLabo/demarrer-telephone.sh", "--sans-navigateur"))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
        }
        try {
            startService(i)
        } catch (e: Exception) {
            aideReglages(e.message)
            return
        }
        attendreServeur()
    }

    private fun attendreServeur() {
        if (enAttente) return
        enAttente = true
        Thread {
            for (n in 0 until 90) {
                if (vivant(URL_TILABO)) {
                    enAttente = false
                    principal.post { vue.loadUrl(URL_TILABO) }
                    return@Thread
                }
                try { Thread.sleep(1000) } catch (e: InterruptedException) { break }
            }
            enAttente = false
            principal.post { aideReglages(null) }
        }.start()
    }

    private fun boutonsAide() =
        "<button onclick='TiLaboApp.autonome()'>Retour au Bureau</button>" +
            "<button class='second' onclick='TiLaboApp.reessayer()'>Réessayer le Ti-Labo</button>"

    private fun aideReglages(detail: String?) {
        afficher(page("Le Ti-Labo complet ne démarre pas",
            "<p>Jasette, Codex, Clé USB-C et GitHub marchent quand même : « Retour au Bureau ».</p>" +
                "<p>Pour le Ti-Labo complet, fais ceci <b>une seule fois</b> dans Termux :</p>" +
                "<ol><li>Le dossier <code>~/TiLabo</code> doit exister (voir LISEZMOI-TELEPHONE).</li>" +
                "<li><pre>mkdir -p ~/.termux\necho \"allow-external-apps=true\" &gt;&gt; ~/.termux/termux.properties\ntermux-reload-settings</pre></li>" +
                "<li>Paramètres › Applications › Termux › Batterie › <b>Non restreinte</b>.</li></ol>" +
                (if (detail != null) "<p class='petit'>Détail : ${echapper(detail)}</p>" else ""),
            boutonsAide()))
    }

    private fun termuxInstalle() = try {
        packageManager.getPackageInfo("com.termux", 0); true
    } catch (e: PackageManager.NameNotFoundException) { false }

    private fun vivant(url: String): Boolean = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 800
        c.readTimeout = 1500
        val ok = c.responseCode == 200
        c.disconnect()
        ok
    } catch (e: Exception) { false }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (code == CODE_POSITION) {
            val ok = res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED
            vue.evaluateJavascript("window.dispatchEvent(new CustomEvent('tl-position',{detail:$ok}))", null)
            return
        }
        if (code != CODE_PERM) return
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) lancerTermux()
        else afficher(page("Permission refusée",
            "<p>Sans la permission « Exécuter des commandes dans Termux », le Ti-Labo complet ne peut pas se démarrer tout seul.</p>" +
                "<p class='petit'>Paramètres › Applications › Ti-Labo › Autorisations.</p>", boutonsAide()))
    }

    @Deprecated("API Activity")
    override fun onActivityResult(code: Int, resultat: Int, donnees: Intent?) {
        if (code != CODE_FICHIER || rappelFichier == null) {
            super.onActivityResult(code, resultat, donnees)
            return
        }
        var uris: Array<Uri>? = null
        if (resultat == RESULT_OK && donnees != null) {
            val clip = donnees.clipData
            uris = if (clip != null) Array(clip.itemCount) { clip.getItemAt(it).uri }
            else donnees.data?.let { arrayOf(it) }
        }
        rappelFichier?.onReceiveValue(uris)
        rappelFichier = null
    }

    @Deprecated("API Activity")
    override fun onBackPressed() {
        // Sur le bureau, Retour reduit d'abord la fenetre ouverte (comme Windows), puis on quitte
        vue.evaluateJavascript("(window.tlRetour && window.tlRetour()) ? 1 : 0") { r ->
            if (r == "1") return@evaluateJavascript
            if (vue.canGoBack()) vue.goBack() else super.onBackPressed()
        }
    }

    // ------------------------------------------------------------------ pages locales
    private fun afficher(html: String) =
        vue.loadDataWithBaseURL("https://tilabo.local/", html, "text/html", "utf-8", null)

    private fun echapper(t: String) = t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun page(titre: String, corps: String, boutons: String = "") = """
        <!doctype html><html lang='fr'><head><meta charset='utf-8'>
        <meta name='viewport' content='width=device-width,initial-scale=1'><style>
        body{margin:0;background:#000;color:#fff;font-family:sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center}
        .b{max-width:440px;padding:24px;text-align:left}
        .logo{display:block;width:210px;height:210px;margin:0 auto 4px;object-fit:contain}
        h1{color:#FF7A1A;font-size:22px;margin:12px 0 8px}p,li{color:#E5E5E5;line-height:1.5}
        code,pre{font-family:monospace;background:#111;border:1px solid #262626;border-radius:6px;padding:2px 6px;color:#fff}
        pre{padding:10px;white-space:pre-wrap;font-size:13px}.petit{font-size:13px;color:#B3B3B3}
        button{margin-top:12px;width:100%;min-height:48px;border:0;border-radius:10px;background:#FF7A1A;color:#000;font-size:16px;font-weight:700}
        button.second{background:#111;color:#FF7A1A;border:1px solid #FF7A1A}
        .roue{width:34px;height:34px;border:3px solid #262626;border-top-color:#FF7A1A;border-radius:50%;animation:t 1s linear infinite;margin:18px auto}
        @keyframes t{to{transform:rotate(360deg)}}</style></head><body><div class='b'>
        ${if (logo.isNotEmpty()) "<img class='logo' src='$logo' alt=''>" else ""}
        <h1>$titre</h1>$corps$boutons</div></body></html>""".trimIndent()

    /** Appele depuis les pages (aide, Ti-Labo). */
    inner class Pont {
        @JavascriptInterface fun reessayer() = principal.post { demarrer() }.let { }
        @JavascriptInterface fun autonome() = principal.post { vue.loadUrl(URL_AUTONOME) }.let { }
        @JavascriptInterface fun plusTard() = principal.post { attendAcces = false; ouvrirBureau() }.let { }
        @JavascriptInterface fun complet() = principal.post { lancer() }.let { }
        @JavascriptInterface fun retour() = principal.post { finish() }.let { }

        /** Un choix du menu de CodexLocal (Codex, Chat, Studio, Reglages…) touche sur le bureau : CodexLocal le fait. */
        @JavascriptInterface fun actionCodexLocal(cle: String) = principal.post {
            if (com.codexlocal.bureau.ActionBureau.depuisCle(cle) == null) return@post
            startActivity(Intent(this@TiLaboActivity, com.codexlocal.MainActivity::class.java)
                .putExtra(com.codexlocal.bureau.BureauActivity.EXTRA_ACTION, cle)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            finish()
        }.let { }
        /** Partager un fichier, ou tout le projet en .zip (chemin vide). */
        @JavascriptInterface fun partager(projet: String, chemin: String) {
            Thread {
                try { Partage.partager(this@TiLaboActivity, Projets.projet(projet), chemin.ifBlank { null }) }
                catch (e: Exception) { message("Partage impossible : ${e.message}") }
            }.start()
        }
        @JavascriptInterface fun demanderPosition() = principal.post {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), CODE_POSITION)
        }.let { }
        @JavascriptInterface fun message(t: String) = principal.post {
            Toast.makeText(this@TiLaboActivity, t, Toast.LENGTH_LONG).show()
        }.let { }

        @JavascriptInterface fun ouvrirAcces() = principal.post {
            try {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:$packageName")))
            } catch (e: ActivityNotFoundException) {
                startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }.let { }

        /** Enregistre un fichier exporte (dataURL) dans Download/TiLabo. */
        @JavascriptInterface fun enregistrer(dataUrl: String, nom: String) {
            try {
                val virgule = dataUrl.indexOf(',')
                val mime = dataUrl.substringAfter("data:").substringBefore(';').ifBlank { "application/octet-stream" }
                val octets = Base64.decode(dataUrl.substring(virgule + 1), Base64.DEFAULT)
                val v = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, nom)
                    put(MediaStore.Downloads.MIME_TYPE, mime)
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/TiLabo")
                }
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
                    ?: throw IllegalStateException("écriture refusée")
                contentResolver.openOutputStream(uri)!!.use { it.write(octets) }
                message("Enregistré : Téléchargements/TiLabo/$nom")
            } catch (e: Exception) {
                message("Enregistrement impossible : ${e.message}")
            }
        }
    }
}
