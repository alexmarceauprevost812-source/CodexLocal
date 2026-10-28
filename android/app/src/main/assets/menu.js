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
  .k{color:#FF9F4A}.s{color:#9BE37A}.c{color:#6B7280;font-style:italic}.nb{color:#7CC4FF}.f{color:#F7D774}.t{color:#FF7A9C}
  `;
  const st = document.createElement("style"); st.textContent = css; document.head.appendChild(st);

  const ICI = location.pathname;
  const choix = [
    ["/", "🏠", "Bureau"],
    ["/jasette", "💬", "Jasette"],
    ["/codex", "🧑‍💻", "Codex"],
    ["/jasette#ia", "🧠", "IA locales"],
    ["/cle", "🔌", "Clé USB-C"],
    ["/github", "🐙", "GitHub"],
  ];
  const p = document.createElement("nav"); p.id = "tl-panneau";
  p.innerHTML = `<div class="tl-tete"><img src="/logo.png" alt=""><div><b>Ti-Labo</b><small>Moteur gguf sur le téléphone</small></div></div>` +
    choix.map(([h, e, n]) => `<a href="${h}" class="${(h === ICI && !h.includes("#")) ? "actif" : ""}"><span class="e">${e}</span>${n}</a>`).join("") +
    `<a href="#" id="tl-complet"><span class="e">🎬</span>Studio, Mémoire…<small>Ti-Labo complet</small></a>` +
    `<div class="tl-sep"></div><label><input type="checkbox" id="tl-internet"> 🌐 Internet pour l'IA</label>`;
  const v = document.createElement("div"); v.id = "tl-voile";
  document.body.append(v, p);
  v.onclick = () => document.body.classList.remove("tl-ouvert");
  p.querySelector("#tl-complet").onclick = e => {
    e.preventDefault();
    if (window.TiLaboApp && TiLaboApp.complet) TiLaboApp.complet();
    else alert("Le Ti-Labo complet roule dans Termux : bash ~/TiLabo/demarrer-telephone.sh");
  };
  const h = document.querySelector("header");
  if (h) {
    const b = document.createElement("button"); b.className = "tl-burger"; b.textContent = "☰"; b.title = "Menu";
    b.onclick = () => document.body.classList.add("tl-ouvert");
    h.prepend(b);
  }

  // --- reglages et outils Internet
  const TL = window.TL = {internet: true};
  const caseInternet = p.querySelector("#tl-internet");
  TL.chargerReglages = async () => {
    try { const r = await (await fetch("/tilabo/reglages")).json(); TL.internet = r.internet; caseInternet.checked = r.internet; TL.reglages = r; }
    catch (_) {}
    document.dispatchEvent(new Event("tl-reglages"));
  };
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

  TL.chargerReglages();
})();
