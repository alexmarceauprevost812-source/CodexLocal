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
