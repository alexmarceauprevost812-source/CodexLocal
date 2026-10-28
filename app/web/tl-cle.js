// Outils partages des applis Ecriture et Meteo sur la cle USB-C (memes noms que dans l'APK CodexLocal : window.TL).
(function () {
  "use strict";
  try { if (!window.TILABO_JETON && window.parent.TILABO_JETON) window.TILABO_JETON = window.parent.TILABO_JETON; } catch (_) {}
  // Mot de passe actif et page ouverte seule (pas dans une fenetre du bureau deverrouille) :
  // on passe par le bureau, qui demande le mot de passe, puis on ouvre l'appli dans une fenetre.
  if (!window.TILABO_JETON) {
    const appli = location.pathname.replace(/^\//, "");
    // « /bureau » explicitement : c'est lui qui lit ?ouvrir=, meme si l'interface classique est choisie
    location.replace("/bureau" + (appli ? "?ouvrir=" + encodeURIComponent(appli) : ""));
    return;
  }
  const echap = t => String(t ?? "").replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c]));
  const css = `
  .tl-outil{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:#7CC4FF;background:#08131d;border:1px solid #16314a;border-radius:20px;padding:3px 10px;margin:3px 0}
  .tl-sources{display:flex;gap:8px;overflow-x:auto;padding:4px 0 8px;margin:4px 0;white-space:normal}
  .tl-source{flex:0 0 210px;background:#0d0d0d;border:1px solid #262626;border-top:3px solid #FF7A1A;border-radius:10px;padding:8px 10px;text-decoration:none;color:#fff;font-family:system-ui,sans-serif}
  .tl-source .h{font-size:11px;color:#B3B3B3;margin-bottom:4px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
  .tl-source b{display:block;font-size:13px;line-height:1.3;max-height:2.6em;overflow:hidden}
  .tl-source p{margin:4px 0 0;font-size:12px;color:#B3B3B3;line-height:1.35;max-height:4em;overflow:hidden}
  .tl-meteo{position:relative;overflow:hidden;border-radius:18px;padding:14px 16px;margin:6px 0 10px;color:#fff;white-space:normal;
    background:linear-gradient(135deg,var(--m1),var(--m2));box-shadow:0 8px 24px -10px var(--m1)}
  .tl-meteo .gros{display:flex;align-items:center;gap:12px}
  .tl-meteo .em{font-size:58px;line-height:1;filter:drop-shadow(0 3px 6px rgba(0,0,0,.3));animation:tlFlotte 3s ease-in-out infinite}
  @keyframes tlFlotte{50%{transform:translateY(-5px)}}
  .tl-meteo .temp{font-size:42px;font-weight:800;line-height:1}
  .tl-meteo .desc{font-size:15px;opacity:.95}
  .tl-meteo .lieu{font-size:13px;opacity:.85;margin-bottom:6px}
  .tl-meteo .details{display:flex;gap:12px;flex-wrap:wrap;font-size:13px;margin-top:10px;opacity:.95}
  .tl-meteo .jours{display:grid;grid-template-columns:repeat(4,1fr);gap:6px;margin-top:12px}
  .tl-meteo .jour{background:rgba(0,0,0,.22);border-radius:12px;padding:7px 4px;text-align:center;font-size:12px}
  .tl-meteo .jour .e{font-size:24px;display:block;margin:2px 0}
  .tl-meteo .jour b{font-size:13px}`;
  const st = document.createElement("style"); st.textContent = css; document.head.appendChild(st);

  async function api(route, donnees) {
    const r = await fetch("/api/" + route, {method: donnees === undefined ? "GET" : "POST",
      headers: {"Content-Type": "application/json", "X-TiLabo": window.TILABO_JETON || ""},
      body: donnees === undefined ? undefined : JSON.stringify(donnees)});
    const j = await r.json().catch(() => ({}));
    if (!r.ok) throw new Error(j.erreur || "Erreur " + r.status);
    return j;
  }
  const TL = window.TL = {hote: "cle", internet: true, api};
  TL.chargerReglages = async () => {
    try { const e = await api("etat"); TL.etat = e; TL.internet = !(e.params && e.params.internet === false); } catch (_) {}
  };
  TL.pret = TL.chargerReglages();
  TL.etatIA = async () => {
    await TL.pret;
    const p = (TL.etat && TL.etat.params) || {};
    return p.modele_jasette || "";
  };

  // --- l'IA de la cle (meme moteur que la Jasette), en direct
  TL.ia = async (systeme, question, surTexte, signal) => {
    const r = await fetch("/api/jasette", {method: "POST", signal,
      headers: {"Content-Type": "application/json", "X-TiLabo": window.TILABO_JETON || ""},
      body: JSON.stringify({mode: "ecriture", consigne: systeme, messages: [{role: "user", content: question}]})});
    if (!r.ok) { const j = await r.json().catch(() => ({})); throw new Error(j.erreur || "L'IA ne répond pas (" + r.status + ")"); }
    const lecteur = r.body.getReader(), dec = new TextDecoder(); let tampon = "", rep = "";
    for (;;) {
      const {done, value} = await lecteur.read(); if (done) break;
      tampon += dec.decode(value, {stream: true});
      const lignes = tampon.split("\n"); tampon = lignes.pop();
      for (const l of lignes) {
        if (!l.trim()) continue;
        let o; try { o = JSON.parse(l); } catch (_) { continue; }
        if (o.erreur) throw new Error(o.erreur);
        if (o.texte) { rep += o.texte; surTexte(rep); }
      }
    }
    return rep;
  };

  // --- recherche Internet (DuckDuckGo, par le serveur de la cle)
  TL.chercher = q => api("web/chercher", {requete: q}).then(j => j.resultats || []);
  TL.page = u => api("web/page", {url: u});
  TL.images = async () => [];
  TL.galerie = () => "";
  TL.outil = t => `<div class="tl-outil">${echap(t)}</div>`;
  TL.cartesSources = res => !res || !res.length ? "" : "<div class='tl-sources'>" + res.map(r => {
    let h = ""; try { h = new URL(r.url).hostname.replace(/^www\./, ""); } catch (_) {}
    return `<a class="tl-source" href="${echap(r.url)}" target="_blank" rel="noopener"><div class="h">${echap(h)}</div><b>${echap(r.titre)}</b><p>${echap(r.extrait)}</p></a>`;
  }).join("") + "</div>";
  TL.pourIA = (q, res, page) => "Resultats de recherche Internet pour « " + q + " » :\n" +
    res.map((r, i) => (i + 1) + ". " + r.titre + " — " + r.url + "\n" + r.extrait).join("\n") +
    (page && page.texte ? "\n\nContenu de " + page.url + " :\n" + page.texte.slice(0, 4000) : "") + "\n\n";

  // --- meteo (Open-Meteo, gratuit, sans cle) : appelee directement depuis le navigateur
  const CODES = {0:["Ciel dégagé","☀️","🌙"],1:["Plutôt dégagé","🌤️","🌙"],2:["Partiellement nuageux","⛅","☁️"],3:["Couvert","☁️","☁️"],
    45:["Brouillard","🌫️"],48:["Brouillard givrant","🌫️"],51:["Bruine légère","🌦️"],53:["Bruine","🌦️"],55:["Bruine forte","🌧️"],
    56:["Bruine verglaçante","🧊"],57:["Bruine verglaçante","🧊"],61:["Pluie faible","🌦️"],63:["Pluie","🌧️"],65:["Forte pluie","🌧️"],
    66:["Pluie verglaçante","🧊"],67:["Pluie verglaçante","🧊"],71:["Neige faible","🌨️"],73:["Neige","❄️"],75:["Forte neige","❄️"],
    77:["Grains de neige","🌨️"],80:["Averses","🌦️"],81:["Averses","🌧️"],82:["Violentes averses","⛈️"],85:["Averses de neige","🌨️"],
    86:["Fortes averses de neige","❄️"],95:["Orage","⛈️"],96:["Orage avec grêle","⛈️"],99:["Orage avec grêle","⛈️"]};
  TL.codeMeteo = (c, jour) => { const x = CODES[c] || ["Météo", "🌡️"]; return {desc: x[0], emoji: (jour === 0 && x[2]) ? x[2] : x[1]}; };
  function couleurs(c, jour) {
    if (!jour) return ["#1e1b4b", "#4c1d95"];
    if (c >= 95) return ["#312e81", "#6d28d9"];
    if ([71, 73, 75, 77, 85, 86].includes(c)) return ["#0e7490", "#93c5fd"];
    if (c >= 51) return ["#1e3a8a", "#3b82f6"];
    if (c >= 45) return ["#475569", "#94a3b8"];
    if (c >= 2) return ["#2563eb", "#93c5fd"];
    return ["#ea580c", "#fbbf24"];
  }
  const villeParDefaut = () => { try { return localStorage.getItem("tilabo-ville") || "Normandin"; } catch (_) { return "Normandin"; } };
  TL.meteo = async ville => {
    ville = (ville || villeParDefaut() || "").trim();
    if (!ville) { const e = new Error("Écris une ville"); e.code = "position"; throw e; }
    let g;
    try { g = await (await fetch("https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&name=" + encodeURIComponent(ville))).json(); }
    catch (_) { throw new Error("Pas d'Internet sur ce PC (la météo vient du web)"); }
    const l = g.results && g.results[0];
    if (!l) throw new Error("Ville introuvable : " + ville);
    try { localStorage.setItem("tilabo-ville", ville); } catch (_) {}
    const m = await (await fetch(`https://api.open-meteo.com/v1/forecast?latitude=${l.latitude}&longitude=${l.longitude}` +
      "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day,precipitation" +
      "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=auto&forecast_days=4&wind_speed_unit=kmh")).json();
    const lieu = [l.name, l.admin1, l.country_code].filter(Boolean).filter((x, i, a) => a.indexOf(x) === i).join(", ");
    return {lieu, actuel: m.current || {}, jours: m.daily || {}};
  };
  const JOURS = ["dim", "lun", "mar", "mer", "jeu", "ven", "sam"];
  TL.carteMeteo = m => {
    const a = m.actuel, j = m.jours, jour = a.is_day;
    const {desc, emoji} = TL.codeMeteo(a.weather_code, jour);
    const [c1, c2] = couleurs(a.weather_code, jour);
    const jours = (j.time || []).slice(0, 4).map((d, i) => {
      const x = TL.codeMeteo(j.weather_code[i], 1);
      const nom = i === 0 ? "auj." : JOURS[new Date(d + "T12:00").getDay()];
      return `<div class="jour">${nom}<span class="e">${x.emoji}</span><b>${Math.round(j.temperature_2m_max[i])}°</b> / ${Math.round(j.temperature_2m_min[i])}°` +
        (j.precipitation_probability_max && j.precipitation_probability_max[i] != null ? `<br>💧${j.precipitation_probability_max[i]}%` : "") + `</div>`;
    }).join("");
    return `<div class="tl-meteo" style="--m1:${c1};--m2:${c2}"><div class="lieu">📍 ${echap(m.lieu)}</div>` +
      `<div class="gros"><span class="em">${emoji}</span><div><div class="temp">${Math.round(a.temperature_2m)}°C</div><div class="desc">${desc}</div></div></div>` +
      `<div class="details"><span>🌡️ Ressenti ${Math.round(a.apparent_temperature)}°</span><span>💨 ${Math.round(a.wind_speed_10m)} km/h</span>` +
      `<span>💧 ${a.relative_humidity_2m}%</span>${a.precipitation > 0 ? `<span>☔ ${a.precipitation} mm</span>` : ""}</div>` +
      `<div class="jours">${jours}</div></div>`;
  };
  /** Ouvre une autre appli du bureau (Jasette…) quand on est dans une fenetre. */
  TL.ouvrirAppli = (id, question) => {
    try { if (window.parent !== window && window.parent.tlOuvrir) return window.parent.tlOuvrir(id, question); } catch (_) {}
    location.href = "/index.html?vue=" + encodeURIComponent(id) + (question ? "&question=" + encodeURIComponent(question) : "");
  };
})();
