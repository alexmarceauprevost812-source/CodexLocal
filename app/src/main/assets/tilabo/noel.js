// Thème de Noël du bureau : neige, guirlande lumineuse, sapin et compte à rebours.
// S'active tout seul du 1er décembre au 6 janvier (jour des Rois).
// localStorage « tilabo-noel » : "oui" = toujours, "non" = jamais, absent = automatique.
// Fonctionne sur n'importe quel bureau : <script src="/noel.js" data-barre="56px"></script>
(function () {
  "use strict";
  const script = document.currentScript;
  const BARRE = (script && script.dataset.barre) || "56px";
  const CADRE = script && script.dataset.cadre;        // où mettre la neige (sinon après #fond)
  const GADGETS = (script && script.dataset.gadgets) || "#gadgets";
  const CARTE = (script && script.dataset.carte) || "gad"; // classe de la carte compte à rebours
  const STYLE = script && script.dataset.style;        // "famille" : fond d'hiver pour Mon assistante
  const CLE = "tilabo-noel";

  function enSaison(d) {
    const m = d.getMonth(), j = d.getDate();
    return m === 11 || (m === 0 && j <= 6);
  }
  function choix() {
    try { return localStorage.getItem(CLE) || "auto"; } catch (_) { return "auto"; }
  }
  function actif() {
    const c = choix();
    return c === "oui" || (c === "auto" && enSaison(new Date()));
  }

  const css = `
  .noel-neige{position:fixed;inset:0;z-index:0;pointer-events:none}
  html.noel #fond{--fond-img:radial-gradient(1100px 700px at 75% 0%,#123a2a 0%,#06140e 45%,#020806 80%)!important}
  html.noel #fond::after{filter:drop-shadow(0 0 40px rgba(220,38,38,.55))}
  html.noel #demarrer{background:linear-gradient(145deg,#ef4444,#b91c1c)!important;color:#fff!important;box-shadow:0 0 16px rgba(239,68,68,.55)!important}
  .noel-guirlande{position:fixed;left:0;right:0;bottom:calc(${BARRE} - 6px);height:18px;z-index:21;pointer-events:none;display:flex;justify-content:space-around;overflow:hidden}
  .noel-guirlande::before{content:"";position:absolute;left:0;right:0;top:3px;height:2px;background:#0f3d24;border-radius:2px}
  .noel-guirlande i{position:relative;top:4px;width:8px;height:12px;border-radius:50% 50% 45% 45%;animation:noel-brille 1.6s infinite ease-in-out}
  .noel-guirlande i:nth-child(4n+1){background:#ef4444;color:#ef4444}
  .noel-guirlande i:nth-child(4n+2){background:#facc15;color:#facc15;animation-delay:.4s}
  .noel-guirlande i:nth-child(4n+3){background:#22c55e;color:#22c55e;animation-delay:.8s}
  .noel-guirlande i:nth-child(4n+4){background:#38bdf8;color:#38bdf8;animation-delay:1.2s}
  @keyframes noel-brille{0%,100%{opacity:1;box-shadow:0 0 8px 2px currentColor}50%{opacity:.35;box-shadow:none}}
  .noel-sapin{position:fixed;right:22px;bottom:calc(${BARRE} + 14px);width:170px;z-index:1;pointer-events:none;filter:drop-shadow(0 10px 18px rgba(0,0,0,.6))}
  .noel-sapin .lum{animation:noel-brille 1.8s infinite ease-in-out}
  .noel-sapin .lum:nth-of-type(2n){animation-delay:.6s}.noel-sapin .lum:nth-of-type(3n){animation-delay:1.2s}
  .noel-etoile{animation:noel-etoile 3s infinite ease-in-out;transform-origin:85px 22px}
  @keyframes noel-etoile{0%,100%{transform:scale(1)}50%{transform:scale(1.15)}}
  .noel-gad h4{color:#f87171!important}
  .noel-gad .compte{font-size:26px;font-weight:700;color:#fff;line-height:1.1}
  .noel-gad .compte small{display:block;font-size:12.5px;font-weight:400;color:#a3a3a3;margin-top:3px}
  .noel-gad{position:relative}
  .noel-gad .ferme{position:absolute;top:8px;right:10px;border:0;background:transparent;color:inherit;opacity:.55;font-size:15px;cursor:pointer;min-width:28px;min-height:28px}
  .noel-gad .ferme:hover{opacity:1}
  html.noel.noel-famille .bureau{background:radial-gradient(1100px 700px at 75% 0%,#1e5a3c 0%,#0c2a1c 50%,#06140d 85%)!important}
  html.noel.noel-famille .noel-gad .compte{color:inherit}
  html.noel-famille .noel-sapin{right:calc(min(420px,42vw) + 56px);width:150px}
  html.noel-famille .noel-gad h4{margin:0}
  @media (max-width:600px){.noel-sapin{display:none}}
  @media (prefers-reduced-motion:reduce){.noel-guirlande i,.noel-sapin .lum,.noel-etoile{animation:none}}
  `;

  // sapin dessiné à la main (aucune image externe)
  const SAPIN = `<svg class="noel-sapin" viewBox="0 0 170 210" aria-hidden="true">
    <rect x="74" y="168" width="22" height="22" rx="3" fill="#7c3f12"/>
    <path d="M85 30 L35 95 H58 L22 140 H50 L12 175 H158 L120 140 H148 L112 95 H135 Z" fill="#15803d"/>
    <path d="M85 30 L35 95 H58 L22 140 H50 L12 175 H85 Z" fill="#166534"/>
    <path d="M44 122 Q85 136 128 112" stroke="#facc15" stroke-width="2.5" fill="none" opacity=".8"/>
    <path d="M30 160 Q85 172 142 150" stroke="#facc15" stroke-width="2.5" fill="none" opacity=".8"/>
    <circle class="lum" cx="70" cy="80" r="5" fill="#ef4444" color="#ef4444"/>
    <circle class="lum" cx="100" cy="88" r="5" fill="#38bdf8" color="#38bdf8"/>
    <circle class="lum" cx="58" cy="128" r="5.5" fill="#facc15" color="#facc15"/>
    <circle class="lum" cx="96" cy="126" r="5.5" fill="#ef4444" color="#ef4444"/>
    <circle class="lum" cx="122" cy="146" r="5.5" fill="#22c55e" color="#22c55e"/>
    <circle class="lum" cx="44" cy="164" r="5.5" fill="#38bdf8" color="#38bdf8"/>
    <circle class="lum" cx="82" cy="158" r="5.5" fill="#facc15" color="#facc15"/>
    <g class="noel-etoile"><path d="M85 8 L91 22 L106 23 L94 32 L98 47 L85 39 L72 47 L76 32 L64 23 L79 22 Z" fill="#fde047"/></g>
    <rect x="18" y="178" width="34" height="26" rx="3" fill="#dc2626"/><rect x="32" y="178" width="6" height="26" fill="#fde047"/><rect x="18" y="188" width="34" height="5" fill="#fde047"/>
    <rect x="112" y="182" width="40" height="22" rx="3" fill="#2563eb"/><rect x="129" y="182" width="6" height="22" fill="#f8fafc"/><rect x="112" y="190" width="40" height="5" fill="#f8fafc"/>
    <rect x="58" y="188" width="26" height="16" rx="3" fill="#16a34a"/><rect x="68" y="188" width="5" height="16" fill="#fca5a5"/>
  </svg>`;

  let elements = [], anim = 0;

  function joursAvantNoel() {
    const d = new Date(), noel = new Date(d.getFullYear(), 11, 25);
    if (d > new Date(d.getFullYear(), 11, 25, 23, 59)) noel.setFullYear(d.getFullYear() + 1);
    return Math.ceil((noel - new Date(d.getFullYear(), d.getMonth(), d.getDate())) / 864e5);
  }
  function texteCompte() {
    const d = new Date();
    if (d.getMonth() === 11 && d.getDate() === 24) return ["C'est la veille de Noël !", "Joyeux réveillon 🎅"];
    if (d.getMonth() === 11 && d.getDate() === 25) return ["Joyeux Noël ! 🎁", "Profite de ta journée"];
    if (d.getMonth() === 11 && d.getDate() === 31) return ["Bonne année ! 🥂", "On se revoit en 2027… ou presque"];
    if (d.getMonth() === 0) return ["Bonne année ! 🥂", "Le thème part après les Rois"];
    if (d.getMonth() === 11 && d.getDate() > 25) return ["Joyeuses Fêtes ! ✨", "Le temps des Fêtes continue"];
    const n = joursAvantNoel();
    return [n + (n > 1 ? " dodos" : " dodo"), "avant Noël"];
  }

  function neige(canvas) {
    if (window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches) return;
    const ctx = canvas.getContext("2d");
    let W, H, flocons = [];
    function taille() {
      const r = window.devicePixelRatio || 1;
      W = innerWidth; H = innerHeight; canvas.width = W * r; canvas.height = H * r; ctx.setTransform(r, 0, 0, r, 0, 0);
      const n = Math.min(140, Math.round(W * H / 14000));
      flocons = Array.from({length: n}, () => ({x: Math.random() * W, y: Math.random() * H, r: .8 + Math.random() * 2.6, v: .3 + Math.random() * .9, o: Math.random() * 6.28}));
    }
    taille(); addEventListener("resize", taille);
    let avant = 0;
    function image(t) {
      anim = requestAnimationFrame(image);
      if (document.hidden || t - avant < 33) return; // ~30 images/s, ménage la batterie
      avant = t;
      ctx.clearRect(0, 0, W, H); ctx.fillStyle = "rgba(255,255,255,.85)"; ctx.beginPath();
      for (const f of flocons) {
        f.y += f.v; f.o += .02; f.x += Math.sin(f.o) * .4;
        if (f.y > H + 4) { f.y = -4; f.x = Math.random() * W; }
        ctx.moveTo(f.x, f.y); ctx.arc(f.x, f.y, f.r, 0, 6.283);
      }
      ctx.fill();
    }
    anim = requestAnimationFrame(image);
  }

  function allumer() {
    if (elements.length) return;
    document.documentElement.classList.add("noel");
    const style = document.createElement("style"); style.textContent = css; document.head.appendChild(style);
    if (STYLE) document.documentElement.classList.add("noel-" + STYLE);
    const c = document.createElement("canvas"); c.className = "noel-neige";
    const cadre = CADRE && document.querySelector(CADRE), fond = document.getElementById("fond");
    if (cadre) cadre.prepend(c);
    else if (fond && fond.parentNode) fond.parentNode.insertBefore(c, fond.nextSibling);
    else document.body.prepend(c);
    neige(c);
    const g = document.createElement("div"); g.className = "noel-guirlande";
    g.innerHTML = "<i></i>".repeat(Math.max(16, Math.round(innerWidth / 46)));
    document.body.appendChild(g);
    const s = document.createElement("div"); s.innerHTML = SAPIN; const sapin = s.firstElementChild; document.body.appendChild(sapin);
    elements = [style, c, g, sapin];
    const gadgets = document.querySelector(GADGETS);
    if (gadgets) {
      const gad = document.createElement("div"); gad.className = CARTE + " noel-gad";
      const [a, b] = texteCompte();
      gad.innerHTML = `<button class="ferme" title="Enlever le thème de Noël" aria-label="Enlever le thème de Noël">✕</button><h4>🎄 Temps des Fêtes</h4><div class="compte">${a}<small>${b}</small></div>`;
      gad.querySelector(".ferme").onclick = (e) => { e.stopPropagation(); window.TiNoel.regler("non"); };
      gadgets.insertBefore(gad, gadgets.children[1] || null);
      elements.push(gad);
    }
  }
  function eteindre() {
    cancelAnimationFrame(anim);
    elements.forEach(e => e.remove()); elements = [];
    document.documentElement.classList.remove("noel", "noel-" + STYLE);
  }

  window.TiNoel = {
    actif,
    enSaison: () => enSaison(new Date()),
    choix,
    regler(v) { // "oui", "non" ou "auto"
      try { v === "auto" ? localStorage.removeItem(CLE) : localStorage.setItem(CLE, v); } catch (_) {}
      actif() ? allumer() : eteindre();
    },
    basculer() { this.regler(actif() ? "non" : "oui"); return actif(); },
  };
  if (actif()) allumer();
})();
