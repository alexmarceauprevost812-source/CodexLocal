package com.codexlocal.shell;

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Affiche le site local du projet (assets/www/index.html) dans un WebView plein ecran.
 *
 * Les fichiers sont servis sous une adresse https fictive, ce qui permet a la page d'utiliser
 * fetch(), localStorage, les modules JS... exactement comme sur un vrai site. Aucune requete
 * ne sort de l'appli : tout ce qui n'est pas un fichier du projet est refuse.
 */
public class ShellActivity extends Activity {

    private static final String HOTE = "appassets.androidplatform.net";
    private static final String DOSSIER = "www/";

    private WebView web;

    @Override
    protected void onCreate(Bundle etat) {
        super.onCreate(etat);
        web = new WebView(this);
        setContentView(web);

        WebSettings reglages = web.getSettings();
        reglages.setJavaScriptEnabled(true);
        reglages.setDomStorageEnabled(true);
        reglages.setAllowFileAccess(false);
        reglages.setAllowContentAccess(false);
        reglages.setMediaPlaybackRequiresUserGesture(false);

        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView vue, WebResourceRequest requete) {
                return servir(requete.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView vue, WebResourceRequest requete) {
                // Pas de navigation hors du projet.
                return !HOTE.equals(requete.getUrl().getHost());
            }
        });

        if (etat != null) {
            web.restoreState(etat);
        } else {
            web.loadUrl("https://" + HOTE + "/index.html");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle etat) {
        super.onSaveInstanceState(etat);
        web.saveState(etat);
    }

    @Override
    protected void onPause() {
        super.onPause();
        web.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    private WebResourceResponse servir(Uri adresse) {
        if (!HOTE.equals(adresse.getHost())) {
            return reponse(403, "Interdit", "text/plain", new byte[0]);
        }
        String chemin = adresse.getPath();
        if (chemin == null || chemin.isEmpty() || chemin.equals("/")) {
            chemin = "/index.html";
        }
        chemin = chemin.substring(1);
        if (chemin.contains("..")) {
            return reponse(403, "Interdit", "text/plain", new byte[0]);
        }
        try {
            InputStream flux = getAssets().open(DOSSIER + chemin);
            String type = typeMime(chemin);
            return new WebResourceResponse(type, type.startsWith("text/") || type.contains("json")
                    || type.contains("javascript") || type.contains("xml") ? "utf-8" : null, flux);
        } catch (IOException e) {
            return reponse(404, "Introuvable", "text/plain", "404".getBytes());
        }
    }

    private static WebResourceResponse reponse(int code, String message, String type, byte[] corps) {
        return new WebResourceResponse(type, "utf-8", code, message, new HashMap<String, String>(),
                new ByteArrayInputStream(corps));
    }

    private static final Map<String, String> TYPES = new HashMap<>();

    static {
        TYPES.put("html", "text/html");
        TYPES.put("htm", "text/html");
        TYPES.put("css", "text/css");
        TYPES.put("js", "text/javascript");
        TYPES.put("mjs", "text/javascript");
        TYPES.put("json", "application/json");
        TYPES.put("map", "application/json");
        TYPES.put("txt", "text/plain");
        TYPES.put("md", "text/plain");
        TYPES.put("csv", "text/csv");
        TYPES.put("xml", "application/xml");
        TYPES.put("svg", "image/svg+xml");
        TYPES.put("png", "image/png");
        TYPES.put("jpg", "image/jpeg");
        TYPES.put("jpeg", "image/jpeg");
        TYPES.put("gif", "image/gif");
        TYPES.put("webp", "image/webp");
        TYPES.put("ico", "image/x-icon");
        TYPES.put("woff", "font/woff");
        TYPES.put("woff2", "font/woff2");
        TYPES.put("ttf", "font/ttf");
        TYPES.put("otf", "font/otf");
        TYPES.put("mp3", "audio/mpeg");
        TYPES.put("ogg", "audio/ogg");
        TYPES.put("wav", "audio/wav");
        TYPES.put("mp4", "video/mp4");
        TYPES.put("webm", "video/webm");
        TYPES.put("wasm", "application/wasm");
    }

    private static String typeMime(String chemin) {
        int point = chemin.lastIndexOf('.');
        if (point < 0) return "application/octet-stream";
        String type = TYPES.get(chemin.substring(point + 1).toLowerCase(Locale.ROOT));
        return type != null ? type : "application/octet-stream";
    }
}
