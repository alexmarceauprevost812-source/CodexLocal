package ca.tilabo.app;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Ti-Labo pour Android.
 * L'appli demarre le Ti-Labo dans Termux (meme Ollama, memes IA que le Codex local),
 * puis l'affiche en plein ecran.
 */
public class MainActivity extends Activity {

    private static final String URL_TILABO = "http://127.0.0.1:8765/";
    private static final String PERM_TERMUX = "com.termux.permission.RUN_COMMAND";
    private static final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private static final String TERMUX_BASH = "/data/data/com.termux/files/usr/bin/bash";
    private static final int CODE_PERM = 1;
    private static final int CODE_FICHIER = 2;

    private WebView vue;
    private ValueCallback<Uri[]> rappelFichier;
    private final Handler principal = new Handler(Looper.getMainLooper());
    private volatile boolean enAttente = false;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        getWindow().setStatusBarColor(Color.BLACK);
        getWindow().setNavigationBarColor(Color.BLACK);

        vue = new WebView(this);
        vue.setBackgroundColor(Color.BLACK);
        WebSettings s = vue.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);

        vue.setWebViewClient(new WebViewClient() {
            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return ouvrirLien(Uri.parse(url));
            }
        });

        vue.setWebChromeClient(new WebChromeClient() {
            // Bouton « joindre une image » du chat (vision)
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> rappel, FileChooserParams p) {
                if (rappelFichier != null) rappelFichier.onReceiveValue(null);
                rappelFichier = rappel;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("image/*");
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                try {
                    startActivityForResult(Intent.createChooser(i, "Choisir une image"), CODE_FICHIER);
                } catch (ActivityNotFoundException e) {
                    rappelFichier = null;
                    return false;
                }
                return true;
            }
        });

        // Les boutons de la page d'aide appellent ces fonctions
        vue.addJavascriptInterface(new Pont(), "TiLaboApp");
        setContentView(vue);
        demarrer();
    }

    /** Les liens vers Internet s'ouvrent dans Chrome ; le Ti-Labo reste ici. */
    private boolean ouvrirLien(Uri u) {
        String hote = u.getHost();
        if ("127.0.0.1".equals(hote) || "localhost".equals(hote)) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, u));
        } catch (ActivityNotFoundException ignore) { }
        return true;
    }

    private void demarrer() {
        afficher(page("Démarrage du Ti-Labo…", "<p>On réveille Termux et ton Ollama.<br>La première fois, ça peut prendre une minute.</p><div class='roue'></div>", false));
        new Thread(new Runnable(){public void run(){
            if (serveurVivant()) {
                principal.post(new Runnable(){public void run(){vue.loadUrl(URL_TILABO);}});
                return;
            }
            principal.post(new Runnable(){public void run(){lancerTermux();}});
        }}).start();
    }

    private void lancerTermux() {
        if (!termuxInstalle()) {
            afficher(page("Termux est introuvable",
                    "<p>Le Ti-Labo roule dans <b>Termux</b>. Installe-le depuis F-Droid ou GitHub (pas le Play Store, trop vieux).</p>", true));
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && checkSelfPermission(PERM_TERMUX) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{PERM_TERMUX}, CODE_PERM);
            return;
        }
        Intent i = new Intent();
        i.setClassName("com.termux", "com.termux.app.RunCommandService");
        i.setAction("com.termux.RUN_COMMAND");
        i.putExtra("com.termux.RUN_COMMAND_PATH", TERMUX_BASH);
        i.putExtra("com.termux.RUN_COMMAND_ARGUMENTS",
                new String[]{TERMUX_HOME + "/TiLabo/demarrer-telephone.sh", "--sans-navigateur"});
        i.putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME);
        i.putExtra("com.termux.RUN_COMMAND_BACKGROUND", true);
        try {
            startService(i);
        } catch (SecurityException | IllegalStateException e) {
            aideReglages(e.getMessage());
            return;
        }
        attendreServeur();
    }

    private void attendreServeur() {
        if (enAttente) return;
        enAttente = true;
        new Thread(new Runnable(){public void run(){
            for (int n = 0; n < 90; n++) {
                if (serveurVivant()) {
                    enAttente = false;
                    principal.post(new Runnable(){public void run(){vue.loadUrl(URL_TILABO);}});
                    return;
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { break; }
            }
            enAttente = false;
            principal.post(new Runnable(){public void run(){aideReglages(null);}});
        }}).start();
    }

    private void aideReglages(String detail) {
        afficher(page("Il manque un réglage dans Termux",
                "<p>Fais ceci <b>une seule fois</b> dans Termux, puis touche « Réessayer » :</p>"
                        + "<ol><li>Vérifie que le dossier <code>~/TiLabo</code> existe (voir LISEZMOI-TELEPHONE).</li>"
                        + "<li>Permets aux applis de lancer le Ti-Labo :<pre>mkdir -p ~/.termux\necho \"allow-external-apps=true\" &gt;&gt; ~/.termux/termux.properties\ntermux-reload-settings</pre></li>"
                        + "<li>Dans Android : Paramètres › Applications › Termux › Batterie › <b>Non restreinte</b>.</li></ol>"
                        + "<p class='petit'>Tu peux aussi lancer à la main dans Termux :<br><code>bash ~/TiLabo/demarrer-telephone.sh</code></p>"
                        + (detail != null ? "<p class='petit'>Détail : " + echapper(detail) + "</p>" : ""), true));
    }

    private boolean termuxInstalle() {
        try {
            getPackageManager().getPackageInfo("com.termux", 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean serveurVivant() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(URL_TILABO).openConnection();
            c.setConnectTimeout(800);
            c.setReadTimeout(1500);
            int code = c.getResponseCode();
            c.disconnect();
            return code == 200;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        if (code != CODE_PERM) return;
        if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            lancerTermux();
        } else {
            afficher(page("Permission refusée",
                    "<p>Le Ti-Labo a besoin de la permission « Exécuter des commandes dans Termux » pour se démarrer tout seul.</p>"
                            + "<p>Paramètres › Applications › Ti-Labo › Autorisations › Autorisations supplémentaires.</p>", true));
        }
    }

    @Override
    protected void onActivityResult(int code, int resultat, Intent donnees) {
        if (code != CODE_FICHIER || rappelFichier == null) {
            super.onActivityResult(code, resultat, donnees);
            return;
        }
        Uri[] uris = null;
        if (resultat == RESULT_OK && donnees != null) {
            if (donnees.getClipData() != null) {
                int n = donnees.getClipData().getItemCount();
                uris = new Uri[n];
                for (int k = 0; k < n; k++) uris[k] = donnees.getClipData().getItemAt(k).getUri();
            } else if (donnees.getData() != null) {
                uris = new Uri[]{donnees.getData()};
            }
        }
        rappelFichier.onReceiveValue(uris);
        rappelFichier = null;
    }

    @Override
    public void onBackPressed() {
        if (vue.canGoBack()) vue.goBack();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ pages locales
    private void afficher(String html) {
        vue.loadDataWithBaseURL("about:blank", html, "text/html", "utf-8", null);
    }

    private static String echapper(String t) {
        return t.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String page(String titre, String corps, boolean bouton) {
        return "<!doctype html><html lang='fr'><head><meta charset='utf-8'>"
                + "<meta name='viewport' content='width=device-width,initial-scale=1'><style>"
                + "body{margin:0;background:#000;color:#fff;font-family:sans-serif;display:flex;min-height:100vh;align-items:center;justify-content:center}"
                + ".b{max-width:440px;padding:28px}h1{color:#FF7A1A;font-size:22px;margin:16px 0 8px}p,li{color:#E5E5E5;line-height:1.5}"
                + "code,pre{font-family:monospace;background:#111;border:1px solid #262626;border-radius:6px;padding:2px 6px;color:#fff}"
                + "pre{padding:10px;white-space:pre-wrap;font-size:13px}.petit{font-size:13px;color:#B3B3B3}"
                + "button{margin-top:18px;width:100%;min-height:48px;border:0;border-radius:10px;background:#FF7A1A;color:#000;font-size:16px;font-weight:700}"
                + ".logo{font-size:30px;font-weight:700;color:#FF7A1A}"
                + ".roue{width:34px;height:34px;border:3px solid #262626;border-top-color:#FF7A1A;border-radius:50%;animation:t 1s linear infinite;margin-top:20px}"
                + "@keyframes t{to{transform:rotate(360deg)}}</style></head><body><div class='b'>"
                + "<div class='logo'>Ti-Labo</div><h1>" + titre + "</h1>" + corps
                + (bouton ? "<button onclick='TiLaboApp.reessayer()'>Réessayer</button>" : "")
                + "</div></body></html>";
    }

    /** Pont appele depuis les pages d'aide. */
    public class Pont {
        @android.webkit.JavascriptInterface
        public void reessayer() {
            principal.post(new Runnable(){public void run(){demarrer();}});
        }
    }
}
