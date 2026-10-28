// Ti-Labo — couleur des boutons (et des yeux de l'IA), gardee sur l'appareil et appliquee a toutes les pages.
(function () {
  "use strict";
  const COULEURS = {
    orange: ["Orange", "#FF7A1A", "#ff9a4d"], bleu: ["Bleu", "#3b82f6", "#7cb2ff"], vert: ["Vert", "#22c55e", "#6ee79a"],
    violet: ["Violet", "#a855f7", "#c99bff"], rose: ["Rose", "#ec4899", "#f58fc3"], rouge: ["Rouge", "#ef4444", "#f78a8a"],
    jaune: ["Jaune", "#facc15", "#fde68a"], cyan: ["Cyan", "#06b6d4", "#67e8f9"],
  };
  const lire = () => { try { return localStorage.getItem("tilabo-couleur") || "orange"; } catch (_) { return "orange"; } };
  function appliquer(nom) {
    const c = COULEURS[nom] || COULEURS.orange, r = document.documentElement.style;
    // les noms de couleur utilises par les differentes pages (Jasette, bureau, cle…)
    for (const v of ["--orange", "--or", "--accent", "--ti-yeux"]) r.setProperty(v, c[1]);
    for (const v of ["--or2", "--accent-light"]) r.setProperty(v, c[2]);
    r.setProperty("--accent-bg", "color-mix(in srgb, " + c[1] + " 14%, #000)");
    r.setProperty("--accent-border", "color-mix(in srgb, " + c[1] + " 48%, #000)");
    document.dispatchEvent(new CustomEvent("tl-couleur", {detail: nom}));
  }
  appliquer(lire());
  // une autre fenetre ou page a change la couleur : on suit tout de suite
  addEventListener("storage", (e) => { if (e.key === "tilabo-couleur") appliquer(lire()); });

  window.TiTeinte = {
    couleurs: COULEURS,
    actuelle: lire,
    choisir(nom) { try { localStorage.setItem("tilabo-couleur", nom); } catch (_) {} appliquer(nom); },
    /** Les pastilles de couleur a mettre dans les Parametres. */
    choix(el) {
      if (!el) return;
      const dessiner = () => {
        el.innerHTML = "";
        el.style.cssText += ";display:flex;flex-wrap:wrap;gap:10px";
        for (const [nom, [titre, c1]] of Object.entries(COULEURS)) {
          const b = document.createElement("button");
          b.type = "button"; b.title = titre; b.setAttribute("aria-label", "Boutons " + titre);
          b.setAttribute("aria-pressed", String(nom === lire()));
          b.style.cssText = "width:38px;height:38px;min-height:38px;padding:0;border-radius:50%;cursor:pointer;background:" + c1 +
            ";border:3px solid " + (nom === lire() ? "#fff" : "transparent") + ";box-shadow:0 0 12px " + c1 + "66";
          b.onclick = () => { TiTeinte.choisir(nom); dessiner(); };
          el.appendChild(b);
        }
      };
      dessiner();
    },
  };
})();

// Menu du Ti-Labo (panneau de gauche, memes choix que sur la cle) + outils Internet partages.
(function () {
  const css = `
  .tl-burger{min-width:40px}
  #tl-voile{position:fixed;inset:0;background:rgba(0,0,0,.6);opacity:0;pointer-events:none;transition:opacity .2s;z-index:40}
  #tl-panneau{position:fixed;top:0;bottom:0;left:0;width:min(290px,84vw);background:#080808;border-right:1px solid #FF7A1A;
    transform:translateX(-102%);transition:transform .22s ease-out;z-index:41;display:flex;flex-direction:column;padding:14px 10px calc(14px + env(safe-area-inset-bottom))}
  body.tl-ouvert #tl-voile{opacity:1;pointer-events:auto}
  body.tl-ouvert #tl-panneau{transform:none}
  #tl-panneau .tl-tete{display:flex;align-items:center;gap:10px;padding:0 6px 12px;border-bottom:1px solid #262626;margin-bottom:8px}
  #tl-panneau .tl-tete img{width:46px;height:46px;object-fit:contain}
  #tl-panneau .tl-tete b{color:#FF7A1A;font-size:18px}
  #tl-panneau .tl-tete small{display:block;color:#B3B3B3;font-size:12px}
  #tl-panneau a{display:flex;align-items:center;gap:12px;color:#E5E5E5;text-decoration:none;padding:11px 10px;border-radius:10px;font-size:16px}
  #tl-panneau a .e{width:26px;text-align:center;font-size:19px}
  #tl-panneau a.actif{background:#FF7A1A;color:#000;font-weight:700}
  #tl-panneau a small{margin-left:auto;color:#777;font-size:11px}
  #tl-panneau .tl-sep{flex:1}
  #tl-panneau label{display:flex;align-items:center;gap:10px;color:#E5E5E5;padding:10px;font-size:15px}
  #tl-panneau label input{accent-color:#FF7A1A;width:20px;height:20px}
  #tl-maj.dispo{background:#1d0f04;border:1px solid #FF7A1A}
  #tl-maj.dispo small{color:#FF7A1A}
  .tl-banniere{display:flex;align-items:center;gap:10px;background:linear-gradient(135deg,#3a1a04,#1a0c02);border:1px solid #FF7A1A;border-radius:14px;padding:10px 12px;margin:10px 0;color:#fff;font-size:14px}
  .tl-banniere span{flex:1}
  .tl-banniere button{background:#FF7A1A;color:#000;border:0;border-radius:10px;min-height:36px;padding:0 12px;font-weight:700}
  .tl-sources{display:flex;gap:8px;overflow-x:auto;padding:4px 0 8px;margin:4px 0;white-space:normal}
  .tl-source{flex:0 0 210px;background:#0d0d0d;border:1px solid #262626;border-top:3px solid var(--tl-c,#FF7A1A);border-radius:10px;padding:8px 10px;text-decoration:none;color:#fff}
  .tl-source .h{display:flex;align-items:center;gap:6px;font-size:11px;color:#B3B3B3;margin-bottom:4px;overflow:hidden;white-space:nowrap;text-overflow:ellipsis}
  .tl-source .h img{width:14px;height:14px;border-radius:3px}
  .tl-source b{display:block;font-size:13px;line-height:1.3;max-height:2.6em;overflow:hidden}
  .tl-source p{margin:4px 0 0;font-size:12px;color:#B3B3B3;line-height:1.35;max-height:4em;overflow:hidden}
  .tl-galerie{display:flex;gap:6px;overflow-x:auto;padding:4px 0 8px}
  .tl-galerie a{flex:0 0 auto}
  .tl-galerie img{height:110px;width:auto;max-width:180px;object-fit:cover;border-radius:10px;border:1px solid #262626;background:#111}
  .tl-outil{display:inline-flex;align-items:center;gap:6px;font-size:12px;color:#7CC4FF;background:#08131d;border:1px solid #16314a;border-radius:20px;padding:3px 10px;margin:3px 0}
  .msg img.tl-img{max-width:100%;border-radius:10px;margin:6px 0;border:1px solid #262626}
  .msg h3,.msg h4{color:#FF7A1A;margin:10px 0 4px;font-size:16px}
  .msg li::marker{color:#FF7A1A}
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
  .tl-meteo .jour b{font-size:13px}
  .schema-carte{border:1px solid #2a2a2a;border-radius:12px;overflow:hidden;margin:.6em 0;background:#000;white-space:normal}
  .schema-tete{display:flex;align-items:center;gap:6px;padding:6px 8px 6px 12px;background:#101010;border-bottom:1px solid #2a2a2a;flex-wrap:wrap}
  .schema-tete b{color:#FF7A1A;font-size:13px}
  .schema-tete .espace{flex:1}
  .schema-dessin{padding:12px;overflow:auto;display:flex;justify-content:center}
  .schema-dessin svg{max-width:100%;height:auto}
  .btn.petit{min-height:32px;padding:0 10px;font-size:12.5px;border-radius:8px;background:#161616;color:#FF7A1A;border:1px solid #2a2a2a}
  .btn.petit.prim{background:#FF7A1A;color:#000;border:0;font-weight:700}
  .bloc-code pre{white-space:pre;overflow-x:auto;background:#0e0e0e;border:1px solid #262626;border-radius:8px;padding:10px;font:12px ui-monospace,monospace}
  .sous{color:#9a9a9a;font-size:12px}
  .actions-plan{display:flex;gap:8px;flex-wrap:wrap;margin:8px 0 2px;white-space:normal}
  .actions-plan button{flex:1;min-width:140px;min-height:42px;border-radius:12px;font:inherit;font-size:14px;background:#161616;color:#FF7A1A;border:1px solid #2a2a2a}
  .actions-plan button.plein{background:linear-gradient(135deg,#ff9a4d,#FF7A1A);color:#000;border:0;font-weight:700;box-shadow:0 6px 16px -8px #FF7A1A}
  .k{color:#FF9F4A}.s{color:#9BE37A}.c{color:#6B7280;font-style:italic}.nb{color:#7CC4FF}.f{color:#F7D774}.t{color:#FF7A9C}
  `;
  const st = document.createElement("style"); st.textContent = css; document.head.appendChild(st);

  const ICI = location.pathname;
  const choix = [
    ["/", "🖥️", "Bureau"],
    ["/jasette", "💬", "Jasette"],
    ["/codex", "🧑‍💻", "Codex"],
    ["/agenda", "🗓️", "Agenda"],
    ["/jasette#ia", "🧠", "IA locales"],
    ["/cle", "🔌", "Clé USB-C"],
    ["/github", "🐙", "GitHub"],
    ["/memoire", "🧠", "Mémoire"],
    ["/parametres", "⚙️", "Paramètres"],
  ];
  const p = document.createElement("nav"); p.id = "tl-panneau";
  p.innerHTML = `<div class="tl-tete"><img src="/logo.png" alt=""><div><b>Ti-Labo</b><small id="tl-sous">Mon labo d'IA perso</small></div></div>` +
    choix.map(([h, e, n]) => `<a href="${h}" class="${(h === ICI && !h.includes("#")) ? "actif" : ""}"><span class="e">${e}</span>${n}</a>`).join("") +
    `<a href="#" id="tl-complet"><span class="e">🎬</span>Studio, Mémoire…<small>Ti-Labo complet</small></a>` +
    `<div class="tl-sep"></div><label><input type="checkbox" id="tl-internet"> 🌐 Internet pour l'IA</label>` +
    `<a href="#" id="tl-retour" hidden><span class="e">↩️</span>Retour à CodexLocal</a>` +
    `<a href="#" id="tl-maj"><span class="e">⬆️</span>Mise à jour<small id="tl-version"></small></a>`;
  const v = document.createElement("div"); v.id = "tl-voile";
  document.body.append(v, p);
  v.onclick = () => document.body.classList.remove("tl-ouvert");
  p.querySelector("#tl-complet").onclick = e => {
    e.preventDefault();
    if (window.TiLaboApp && TiLaboApp.complet) TiLaboApp.complet();
    else alert("Le Ti-Labo complet roule dans Termux : bash ~/TiLabo/demarrer-telephone.sh");
  };
  // Dans une fenetre du bureau : pas de menu ☰ (le bouton Demarrer du bureau le remplace)
  const enFenetre = window.top !== window || new URLSearchParams(location.search).has("fenetre");
  const h = document.querySelector("header");
  if (h && !enFenetre) {
    const b = document.createElement("button"); b.className = "tl-burger"; b.textContent = "☰"; b.title = "Menu";
    b.onclick = () => document.body.classList.add("tl-ouvert");
    h.prepend(b);
  }

  // --- reglages et outils Internet
  const TL = window.TL = {internet: true};
  const caseInternet = p.querySelector("#tl-internet");
  TL.chargerReglages = async () => {
    try { const r = await (await fetch("/tilabo/reglages")).json(); TL.internet = r.internet; TL.hote = r.hote || "tilabo"; caseInternet.checked = r.internet; TL.reglages = r;
      // Dans CodexLocal : « Retour à CodexLocal », et c'est CodexLocal qui se met a jour.
      // Dans l'APK Ti-Labo (perso) : le Ti-Labo se met a jour lui-meme.
      const dansCL = TL.hote === "codexlocal";
      p.querySelector("#tl-sous").textContent = dansCL ? "dans CodexLocal" : "Mon labo d'IA perso";
      p.querySelector("#tl-retour").hidden = !(dansCL && window.TiLaboApp && TiLaboApp.retour);
      p.querySelector("#tl-maj").hidden = dansCL; }
    catch (_) {}
    // Les autres fenetres (Jasette, Meteo…) relisent les reglages tout de suite (evenement « storage »)
    try { if (localStorage.getItem("tilabo-internet") !== String(TL.internet)) localStorage.setItem("tilabo-internet", String(TL.internet)); } catch (_) {}
    document.dispatchEvent(new Event("tl-reglages"));
  };
  addEventListener("storage", (e) => { if (e.key === "tilabo-internet") TL.chargerReglages(); });
  caseInternet.onchange = async () => {
    await fetch("/tilabo/reglages", {method: "POST", body: JSON.stringify({internet: caseInternet.checked})});
    TL.chargerReglages();
  };
  async function post(c, corps) {
    const r = await fetch(c, {method: "POST", body: JSON.stringify(corps)});
    const j = await r.json(); if (!r.ok) throw new Error(j.error ? j.error.message : r.status); return j;
  }
  TL.chercher = q => post("/tilabo/web/chercher", {requete: q}).then(j => j.resultats);
  TL.images = q => post("/tilabo/web/images", {requete: q}).then(j => j.images);
  TL.page = u => post("/tilabo/web/page", {url: u});

  const echap = t => String(t).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const couleurs = ["#FF7A1A", "#7CC4FF", "#9BE37A", "#F7D774", "#FF7A9C", "#B58CFF"];
  TL.cartesSources = res => !res || !res.length ? "" : "<div class='tl-sources'>" + res.map((r, i) => {
    let dom = ""; try { dom = new URL(r.url).hostname.replace(/^www\./, ""); } catch (_) {}
    return `<a class="tl-source" style="--tl-c:${couleurs[i % couleurs.length]}" href="${echap(r.url)}"><div class="h">` +
      `<img src="https://icons.duckduckgo.com/ip3/${echap(dom)}.ico" alt="" onerror="this.remove()">${echap(dom)}</div>` +
      `<b>${echap(r.titre)}</b><p>${echap(r.extrait || "")}</p></a>`;
  }).join("") + "</div>";
  TL.galerie = imgs => !imgs || !imgs.length ? "" : "<div class='tl-galerie'>" +
    imgs.map(i => `<a href="${echap(i.source || i.image)}"><img src="${echap(i.vignette)}" alt="${echap(i.titre)}" loading="lazy" onerror="this.parentNode.remove()"></a>`).join("") + "</div>";
  TL.outil = t => `<div class="tl-outil">${echap(t)}</div>`;
  /** Le resume des resultats donne a l'IA. */
  TL.pourIA = (q, res, page) => "Resultats de recherche Internet pour « " + q + " » :\n" +
    res.map((r, i) => (i + 1) + ". " + r.titre + " — " + r.url + "\n   " + (r.extrait || "")).join("\n") +
    (page ? "\n\nTexte de la page " + page.url + " :\n" + page.texte : "") + "\n\n";
  /** Markdown en plus : images, titres, listes. A appliquer sur du texte deja echappe. */
  TL.enrichir = html => html
    .replace(/!\[([^\]]*)\]\((https?:\/\/[^\s)]+)\)/g, (_, a, u) => `<img class="tl-img" src="${u}" alt="${a}" loading="lazy" onerror="this.remove()">`)
    .replace(/^#{1,3} (.+)$/gm, "<h3>$1</h3>").replace(/^#{4,6} (.+)$/gm, "<h4>$1</h4>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" style="color:#7CC4FF">$1</a>');

  // --- coloration du code (Jasette et Codex)
  const echapC = t => String(t).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  const MOTS = "if else elif for while do return function def class import from export default const let var new try catch except finally raise throw async await yield with as in of is not and or None null true false True False this self super public private protected static void int float double string bool boolean fun val when object interface extends implements package struct enum match case break continue lambda pass print echo";
  const MOTS_SET = new Set(MOTS.split(" "));
  TL.colorer = function (code, nom) {
    const ext = (nom || "").split(".").pop().toLowerCase();
    if (["html","xml","svg","vue"].includes(ext)) {
      return echapC(code).replace(/(&lt;!--[\s\S]*?--&gt;)/g, "<span class='c'>$1</span>")
        .replace(/(&lt;\/?)([a-zA-Z][\w-]*)/g, "$1<span class='t'>$2</span>")
        .replace(/([\w-]+)=(&quot;|")(.*?)\2/g, "<span class='f'>$1</span>=<span class='s'>\"$3\"</span>");
    }
    const diese = ["py","sh","rb","yml","yaml","toml","r","pl","conf","ini"].includes(ext);
    const re = diese
      ? /(#[^\n]*)|("""[\s\S]*?"""|'''[\s\S]*?'''|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(\b\d+(?:\.\d+)?\b)|([A-Za-z_]\w*)(?=\s*\()|([A-Za-z_]\w*)/g
      : /(\/\/[^\n]*|\/\*[\s\S]*?\*\/)|(`(?:\\.|[^`\\])*`|"(?:\\.|[^"\\\n])*"|'(?:\\.|[^'\\\n])*')|(\b\d+(?:\.\d+)?\b)|([A-Za-z_$][\w$]*)(?=\s*\()|([A-Za-z_$][\w$]*)/g;
    let h = "", i = 0, m;
    while ((m = re.exec(code))) {
      h += echapC(code.slice(i, m.index)); i = re.lastIndex;
      if (m[1]) h += "<span class='c'>" + echapC(m[1]) + "</span>";
      else if (m[2]) h += "<span class='s'>" + echapC(m[2]) + "</span>";
      else if (m[3]) h += "<span class='nb'>" + m[3] + "</span>";
      else if (m[4]) h += MOTS_SET.has(m[4]) ? "<span class='k'>" + m[4] + "</span>" : "<span class='f'>" + m[4] + "</span>";
      else h += MOTS_SET.has(m[5]) ? "<span class='k'>" + m[5] + "</span>" : m[5];
    }
    return h + echapC(code.slice(i));
  }


  // --- meteo (Open-Meteo) : carte coloree avec emojis
  const CODES = {0:["Ciel dégagé","☀️","🌙"],1:["Plutôt dégagé","🌤️","🌙"],2:["Partiellement nuageux","⛅","☁️"],3:["Couvert","☁️","☁️"],
    45:["Brouillard","🌫️"],48:["Brouillard givrant","🌫️"],51:["Bruine légère","🌦️"],53:["Bruine","🌦️"],55:["Bruine forte","🌧️"],
    56:["Bruine verglaçante","🧊"],57:["Bruine verglaçante","🧊"],61:["Pluie faible","🌦️"],63:["Pluie","🌧️"],65:["Forte pluie","🌧️"],
    66:["Pluie verglaçante","🧊"],67:["Pluie verglaçante","🧊"],71:["Neige faible","🌨️"],73:["Neige","❄️"],75:["Forte neige","❄️"],
    77:["Grains de neige","🌨️"],80:["Averses","🌦️"],81:["Averses","🌧️"],82:["Violentes averses","⛈️"],85:["Averses de neige","🌨️"],
    86:["Fortes averses de neige","❄️"],95:["Orage","⛈️"],96:["Orage avec grêle","⛈️"],99:["Orage avec grêle","⛈️"]};
  TL.codeMeteo = (c, jour) => { const x = CODES[c] || ["Météo","🌡️"]; return {desc: x[0], emoji: (jour === 0 && x[2]) ? x[2] : x[1]}; };
  function couleursMeteo(c, jour){
    if (!jour) return ["#1e1b4b", "#4c1d95"];
    if (c >= 95) return ["#312e81", "#6d28d9"];
    if ([71,73,75,77,85,86].includes(c)) return ["#0e7490", "#93c5fd"];
    if (c >= 51) return ["#1e3a8a", "#3b82f6"];
    if (c >= 45) return ["#475569", "#94a3b8"];
    if (c >= 2) return ["#2563eb", "#93c5fd"];
    return ["#ea580c", "#fbbf24"];
  }
  TL.QUESTION_METEO = /m[ée]t[ée]o|temp[ée]rature|fait[- ]il (beau|froid|chaud)|il fait combien|pleuvoir|pleut|neiger|neige[- ]t|weather|pr[ée]visions?/i;
  /** « météo à Normandin », « temps qu'il fait au Lac-Saint-Jean » -> la ville, sinon null. */
  TL.villeDans = t => {
    const m = t.match(/(?:^|\s)(?:à|a|au|aux|en|pour|sur|de|du|in)\s+((?:la |le |les |l')?[A-ZÉÈÀÂÎÔÛ][\w'’\-éèêëàâîïôöûüç]+(?:[\s-](?:de |du |des |la |le |sur |en )?[A-ZÉÈÀÂÎÔÛ][\w'’\-éèêëàâîïôöûüç]+)*)/);
    return m ? m[1].replace(/^(la |le |les |l')/i, "") : null;
  };
  TL.meteo = async ville => {
    const r = await fetch("/tilabo/meteo", {method: "POST", body: JSON.stringify({ville: ville || ""})});
    const j = await r.json();
    if (!r.ok) { const e = new Error(j.error ? j.error.message : r.status); e.code = j.error && j.error.code; throw e; }
    return j;
  };
  const JOURS = ["dim", "lun", "mar", "mer", "jeu", "ven", "sam"];
  TL.carteMeteo = m => {
    const a = m.actuel, j = m.jours, jour = a.is_day;
    const {desc, emoji} = TL.codeMeteo(a.weather_code, jour);
    const [c1, c2] = couleursMeteo(a.weather_code, jour);
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
  /** Resume donne a l'IA pour qu'elle reponde avec des emojis meteo. */
  TL.meteoPourIA = m => {
    const a = m.actuel, j = m.jours, x = TL.codeMeteo(a.weather_code, a.is_day);
    let t = "Meteo en direct a " + m.lieu + " : " + Math.round(a.temperature_2m) + " °C (ressenti " + Math.round(a.apparent_temperature) +
      " °C), " + x.desc + " " + x.emoji + ", vent " + Math.round(a.wind_speed_10m) + " km/h, humidite " + a.relative_humidity_2m + " %.\nPrevisions :\n";
    (j.time || []).slice(0, 4).forEach((d, i) => {
      const y = TL.codeMeteo(j.weather_code[i], 1);
      t += "- " + d + " : " + y.desc + " " + y.emoji + ", max " + Math.round(j.temperature_2m_max[i]) + " °C, min " +
        Math.round(j.temperature_2m_min[i]) + " °C" + (j.precipitation_probability_max ? ", risque de precipitation " + j.precipitation_probability_max[i] + " %" : "") + "\n";
    });
    return t + "\nReponds a la question meteo de facon vivante, avec plein d'emojis meteo (☀️🌤️⛅🌧️❄️🌬️🌡️🧥☔), " +
      "et un petit conseil pratique (quoi porter, parapluie, pneus d'hiver…).\n\n";
  };


  p.querySelector("#tl-retour").onclick = e => { e.preventDefault(); if (window.TiLaboApp && TiLaboApp.retour) TiLaboApp.retour(); else history.back(); };
  // --- mise a jour : dans CodexLocal, c'est CodexLocal qui se met a jour (une seule APK)
  const bMaj = p.querySelector("#tl-maj");
  TL.verifierMaj = async () => {
    try {
      const r = await post("/tilabo/maj", {});
      TL.maj = r;
      p.querySelector("#tl-version").textContent = r.disponible ? "v" + r.derniere + " dispo !" : "v" + r.actuelle;
      bMaj.classList.toggle("dispo", !!r.disponible);
      document.dispatchEvent(new Event("tl-maj"));
      return r;
    } catch (e) { p.querySelector("#tl-version").textContent = ""; TL.majErreur = e.message; return null; }
  };
  TL.installerMaj = async (bouton) => {
    const affiche = t => { if (bouton) bouton.textContent = t; p.querySelector("#tl-version").textContent = t; };
    try { await post("/tilabo/maj/installer", {}); } catch (e) { alert("⚠ " + e.message); return; }
    for (;;) {
      await new Promise(ok => setTimeout(ok, 700));
      const e = await (await fetch("/tilabo/maj/etat")).json();
      if (e.etat === "telechargement") affiche("⬇️ " + (e.pourcent || 0) + " %");
      else if (e.etat === "installation") affiche("📦 Installation…");
      else if (e.etat === "confirmation") { affiche("👉 Confirme l'installation"); return; }
      else if (e.etat === "a_jour") { affiche("✅ Déjà à jour"); return; }
      else if (e.etat === "installee") { affiche("✅ Installée"); return; }
      else if (e.etat === "erreur") { affiche("⚠ Erreur"); alert("⚠ " + e.message); return; }
    }
  };
  bMaj.onclick = async e => {
    e.preventDefault();
    const r = TL.maj || await TL.verifierMaj();
    if (!r) return alert("⚠ " + (TL.majErreur || "Vérification impossible"));
    if (!r.disponible) return alert("✅ Ti-Labo est à jour (v" + r.actuelle + ").");
    if (confirm("⬆️ Ti-Labo " + r.derniere + " est disponible (" + r.taille_mo + " Mo).\nL'installer maintenant ? Rien ne sera perdu.")) TL.installerMaj();
  };
  const vraieVerif = TL.verifierMaj;
  TL.verifierMaj = async () => { await TL.pret; return TL.hote === "codexlocal" ? null : vraieVerif(); };
  // une verification par jour au plus (desactivee dans CodexLocal)
  try {
    const der = Number(localStorage.getItem("tilabo-maj-verif") || 0);
    if (Date.now() - der > 6 * 3600e3) { localStorage.setItem("tilabo-maj-verif", String(Date.now())); setTimeout(TL.verifierMaj, 1500); }
    else setTimeout(TL.verifierMaj, 4000);
  } catch (_) { setTimeout(TL.verifierMaj, 2000); }

  TL.pret = TL.chargerReglages();
})();

// IA en mémoire : pastille « ⚡ prête / ⏳ chargement… / 💤 charger » dans la Jasette et le Codex.
// L'IA se charge toute seule quand on ouvre le Ti-Labo ; la pastille montre où elle en est.
(function () {
  const p = location.pathname.replace(/\/$/, "") || "/";
  if (!["/", "/jasette", "/codex"].includes(p)) return;
  const tete = document.querySelector("header");
  if (!tete) return;
  const st = document.createElement("style");
  st.textContent = `.tl-charge{border:1px solid #262626;background:#0d0d0d;color:#B3B3B3;border-radius:999px;padding:6px 10px;font-size:12.5px;white-space:nowrap;min-height:34px}
  .tl-charge.prete{color:#4ade80;border-color:#14532d;background:#06180c}
  .tl-charge.charge{color:#facc15;border-color:#713f12;background:#1a1204}
  .tl-charge.dort{color:#FF9A4D;border-color:#7A3A0E}`;
  document.head.appendChild(st);
  const b = document.createElement("button");
  b.type = "button"; b.className = "tl-charge"; b.hidden = true;
  const avant = tete.querySelector("#bNouv");
  avant ? tete.insertBefore(b, avant) : tete.appendChild(b);
  let suivi = null, dernier = null;
  async function maj() {
    let e;
    try { e = await (await fetch("/tilabo/etat")).json(); } catch (_) { b.hidden = true; return false; }
    dernier = e.dernier;
    b.hidden = !e.chargement && !e.pret && !e.dernier;
    if (e.chargement) { b.className = "tl-charge charge"; b.textContent = "⏳ chargement…"; b.title = "L'IA se charge en mémoire (une seule fois) : " + e.chargement; }
    else if (e.pret) { b.className = "tl-charge prete"; b.textContent = "⚡ prête"; b.title = "L'IA est en mémoire : les réponses partent tout de suite"; }
    else { b.className = "tl-charge dort"; b.textContent = "💤 charger"; b.title = "Charger l'IA en mémoire pour des réponses rapides"; }
    return !!e.chargement;
  }
  function suivre() {
    clearInterval(suivi);
    suivi = setInterval(async () => { if (!(await maj())) { clearInterval(suivi); suivi = null; } }, 1500);
  }
  b.onclick = async () => {
    if (b.classList.contains("prete") || !dernier) return;
    try { await fetch("/tilabo/precharger", { method: "POST", body: "{}" }); } catch (_) {}
    b.className = "tl-charge charge"; b.textContent = "⏳ chargement…";
    suivre();
  };
  maj().then((enCours) => { if (enCours) suivre(); });
  setInterval(maj, 20000);
  document.addEventListener("visibilitychange", () => { if (!document.hidden) maj().then((x) => x && suivre()); });
})();
