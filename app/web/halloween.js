// Thème Halloween : juste les yeux — rouges, ils clignent en blanc et suivent la souris.
// S'active tout seul du 15 octobre au 2 novembre.
// localStorage « tilabo-halloween » : "oui" = toujours, "non" = jamais, absent = automatique.
// <script src="/web/halloween.js" data-barre="52px"></script>  (bureau)
// <script src="/web/halloween.js" data-mode="yeux"></script>     (Jasette : seulement les yeux qui brillent)
(function () {
  "use strict";
  const script = document.currentScript;
  const BARRE = (script && script.dataset.barre) || "52px";
  const SEULEMENT_YEUX = script && script.dataset.mode === "yeux";
  const CLE = "tilabo-halloween";

  function enSaison(d) {
    const m = d.getMonth(), j = d.getDate();
    return (m === 9 && j >= 15) || (m === 10 && j <= 2);
  }
  function choix() { try { return localStorage.getItem(CLE) || "auto"; } catch (_) { return "auto"; } }
  function actif() { const c = choix(); return c === "oui" || (c === "auto" && enSaison(new Date())); }

  const cssYeux = `
  /* juste les yeux : rouges, et ils clignent en blanc */
  html.halloween .ti-yeux{background:radial-gradient(circle at 50% 40%,#1c0303,#050101 72%)!important;
    box-shadow:inset 0 0 0 1px rgba(239,16,16,.45),0 0 calc(var(--t)*.35) rgba(239,16,16,.3)!important}
  html.halloween .ti-oeil{background:linear-gradient(180deg,#ff7b7b,#ef1010 50%,#8b0000)!important;
    box-shadow:0 0 calc(var(--t)*.16) rgba(239,16,16,.9),0 0 calc(var(--t)*.34) rgba(239,16,16,.45)!important}
  html.halloween .ti-yeux.cligne .ti-oeil{transform:none!important;background:#fff!important;
    box-shadow:0 0 calc(var(--t)*.2) #fff,0 0 calc(var(--t)*.4) rgba(255,255,255,.7)!important}
  html.halloween .ti-yeux .points i{background:#ef1010!important}
  /* forme de yeux de demon : en biais, l'air fache, avec une pupille fendue */
  html.halloween .ti-yeux .regard{gap:calc(var(--t)*.1)!important;
    filter:drop-shadow(0 0 calc(var(--t)*.06) rgba(239,16,16,.95)) drop-shadow(0 0 calc(var(--t)*.14) rgba(239,16,16,.5))}
  html.halloween .ti-yeux.cligne .regard{filter:drop-shadow(0 0 calc(var(--t)*.08) #fff)}
  html.halloween .ti-oeil{width:calc(var(--t)*.32)!important;height:calc(var(--t)*.2)!important;border-radius:0!important;position:relative}
  html.halloween .ti-oeil:first-child{clip-path:polygon(0 8%,100% 52%,88% 100%,22% 92%)}
  html.halloween .ti-oeil:last-child{clip-path:polygon(0 52%,100% 8%,78% 92%,12% 100%)}
  html.halloween .ti-oeil::after{content:"";position:absolute;left:50%;top:22%;width:12%;height:70%;transform:translateX(-50%);
    border-radius:50%;background:#140000}
  html.halloween .ti-yeux.cligne .ti-oeil::after{background:#ffd0d0}
  html.halloween .ti-yeux.parle .ti-oeil,html.halloween .ti-yeux.pense .ti-oeil,html.halloween .ti-yeux.ecoute .ti-oeil,
  html.halloween .ti-yeux.oups .ti-oeil{height:calc(var(--t)*.2)!important;transform:none}`;

  const cssBureau = `
  /* a gauche de la colonne des gadgets, pour ne rien cacher */
  .hw-yeux{position:fixed;right:calc(min(400px,36vw) + 36px);bottom:calc(${BARRE} + 22px);z-index:1;pointer-events:none}
  @media (max-width:600px){.hw-yeux{display:none}}`;

  let elements = [], suivre = null;
  function allumer() {
    if (elements.length) return;
    document.documentElement.classList.add("halloween");
    const style = document.createElement("style"); style.textContent = cssYeux + (SEULEMENT_YEUX ? "" : cssBureau);
    document.head.appendChild(style);
    elements = [style];
    // les yeux suivent la souris (bureau et Jasette)
    suivre = (e) => window.TiYeux && TiYeux.regarderVers && TiYeux.regarderVers(e.clientX / innerWidth * 2 - 1, e.clientY / innerHeight * 2 - 1);
    addEventListener("pointermove", suivre);
    if (SEULEMENT_YEUX) return;
    // les grands yeux rouges sur le bureau
    if (window.TiYeux) {
      const z = document.createElement("div"); z.className = "hw-yeux";
      const place = document.createElement("span"); z.appendChild(place);
      document.body.appendChild(z);
      TiYeux.remplacer(place, true);
      elements.push(z);
    }
  }
  function eteindre() {
    if (suivre) { removeEventListener("pointermove", suivre); suivre = null; }
    elements.forEach((e) => e.remove()); elements = [];
    document.documentElement.classList.remove("halloween");
  }

  window.TiHalloween = {
    actif,
    choix,
    regler(v) { // "oui", "non" ou "auto"
      try { v === "auto" ? localStorage.removeItem(CLE) : localStorage.setItem(CLE, v); } catch (_) {}
      actif() ? allumer() : eteindre();
    },
    basculer() { this.regler(actif() ? "non" : "oui"); return actif(); },
  };
  // un changement fait dans une autre fenetre (bureau <-> Jasette) suit tout de suite
  addEventListener("storage", (e) => { if (e.key === CLE) actif() ? allumer() : eteindre(); });
  if (actif()) allumer();
})();
