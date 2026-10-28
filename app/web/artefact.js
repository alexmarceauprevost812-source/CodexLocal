/* Ti-Labo — panneau « artefact » pour construire des schemas dans la Jasette */
"use strict";

(function () {
  const $ = (s) => document.querySelector(s);
  const panneau = $("#artefact");
  if (!panneau) return;
  const carte = panneau; // contient .schema-dessin : sert de « carte » pour TiSchemas
  const versions = [];
  let actuelle = 0, zoom = 1, minuteur = null;

  const MODELE = `flowchart TD
    A["Idée"] --> B["Plan"]
    B --> C["Code"]
    C --> D["Test"]
    D -->|"ça marche"| E["Fini !"]
    D -->|"bogue"| C`;

  function ouvrirPanneau() {
    panneau.hidden = false;
    document.querySelector(".jasette-split")?.classList.add("avec-artefact");
  }

  function majVersions() {
    $("#af-version").innerHTML = versions.map((v) => `<option value="${v.numero}" ${v.numero === actuelle ? "selected" : ""}>Version ${v.numero}${v.nom ? " · " + v.nom.replace(/</g, "&lt;") : ""}</option>`).join("");
  }

  async function afficher(code, animation = true) {
    $("#af-erreur").textContent = "";
    try {
      await TiSchemas.dessiner(carte, code, animation);
      ajuster();
    } catch (e) {
      $("#af-erreur").textContent = "Schéma invalide : " + String(e.message || e).split("\n").slice(0, 3).join(" ");
    }
  }

  function ajouterVersion(code, nom) {
    const v = { numero: versions.length + 1, code: code.trim(), nom: nom || "" };
    versions.push(v);
    actuelle = v.numero;
    majVersions();
    ouvrirPanneau();
    $("#af-texte").value = v.code;
    afficher(v.code);
    return v;
  }

  function ouvrir(numero) {
    const v = versions.find((x) => x.numero === numero) || versions[versions.length - 1];
    if (!v) return;
    actuelle = v.numero;
    majVersions();
    ouvrirPanneau();
    $("#af-texte").value = v.code;
    afficher(v.code);
  }

  // ---- zoom
  function appliquerZoom() { $("#af-toile").style.transform = `scale(${zoom})`; }
  function ajuster() {
    const svg = carte.querySelector(".schema-dessin svg");
    if (!svg) return;
    const vb = svg.viewBox.baseVal;
    const zone = $("#af-apercu");
    if (!vb || !vb.width) { zoom = 1; appliquerZoom(); return; }
    svg.style.maxWidth = "none";
    svg.setAttribute("width", vb.width);
    svg.setAttribute("height", vb.height);
    zoom = Math.max(0.2, Math.min(2.5, Math.min((zone.clientWidth - 48) / vb.width, (zone.clientHeight - 48) / vb.height)));
    appliquerZoom();
  }
  $("#af-plus").onclick = () => { zoom = Math.min(4, zoom * 1.2); appliquerZoom(); };
  $("#af-moins").onclick = () => { zoom = Math.max(0.15, zoom / 1.2); appliquerZoom(); };
  $("#af-ajuster").onclick = ajuster;
  $("#af-apercu").addEventListener("wheel", (e) => {
    if (!e.ctrlKey) return;
    e.preventDefault();
    zoom = Math.max(0.15, Math.min(4, zoom * (e.deltaY < 0 ? 1.1 : 0.9)));
    appliquerZoom();
  }, { passive: false });
  // glisser pour se deplacer
  let glisse = null;
  $("#af-apercu").addEventListener("pointerdown", (e) => {
    if (e.button !== 0) return;
    glisse = { x: e.clientX, y: e.clientY, sl: $("#af-apercu").scrollLeft, st: $("#af-apercu").scrollTop };
  });
  window.addEventListener("pointermove", (e) => {
    if (!glisse) return;
    $("#af-apercu").scrollLeft = glisse.sl - (e.clientX - glisse.x);
    $("#af-apercu").scrollTop = glisse.st - (e.clientY - glisse.y);
  });
  window.addEventListener("pointerup", () => (glisse = null));
  window.addEventListener("resize", () => { if (!panneau.hidden) ajuster(); });

  // ---- onglets Apercu / Code
  document.querySelectorAll(".af-onglet").forEach((o) => (o.onclick = () => {
    const code = o.dataset.af === "code";
    document.querySelectorAll(".af-onglet").forEach((x) => { x.classList.toggle("actif", x === o); x.setAttribute("aria-selected", String(x === o)); });
    $("#af-code").hidden = !code;
    if (code) $("#af-texte").focus();
  }));
  // edition du code : le schema se redessine en direct
  $("#af-texte").addEventListener("input", () => {
    clearTimeout(minuteur);
    minuteur = setTimeout(() => {
      const v = versions.find((x) => x.numero === actuelle);
      const code = $("#af-texte").value;
      if (v) v.code = code;
      afficher(code, false);
    }, 450);
  });
  $("#af-texte").addEventListener("keydown", (e) => {
    if (e.key === "Tab") { e.preventDefault(); e.target.setRangeText("    ", e.target.selectionStart, e.target.selectionEnd, "end"); e.target.dispatchEvent(new Event("input")); }
  });

  // ---- boutons
  $("#af-version").onchange = () => ouvrir(+$("#af-version").value);
  $("#af-fermer").onclick = () => { panneau.hidden = true; document.querySelector(".jasette-split")?.classList.remove("avec-artefact"); };
  $("#af-rejouer").onclick = () => TiSchemas.animer(carte);
  $("#af-video").onclick = (e) => TiSchemas.video(carte, e.currentTarget);
  $("#af-png").onclick = () => TiSchemas.png(carte);
  $("#af-nouveau").onclick = () => ajouterVersion(MODELE, "nouveau");
  $("#af-plein").onclick = () => {
    if (document.fullscreenElement) document.exitFullscreen();
    else panneau.requestFullscreen?.().then(() => setTimeout(ajuster, 150)).catch(() => {});
  };
  document.addEventListener("fullscreenchange", () => setTimeout(ajuster, 150));

  // ---- enregistrer sur la cle
  function api(route, donnees) {
    return fetch("/api/" + route, {
      method: donnees === undefined ? "GET" : "POST",
      headers: { "Content-Type": "application/json", "X-TiLabo": window.TILABO_JETON || "" },
      body: donnees === undefined ? undefined : JSON.stringify(donnees),
    }).then(async (r) => { const j = await r.json().catch(() => ({})); if (!r.ok || j.erreur) throw new Error(j.erreur || "Erreur"); return j; });
  }
  const message = (t) => (window.toast ? window.toast(t) : alert(t));
  async function majMesSchemas() {
    try {
      const liste = await api("schemas");
      $("#af-mes").innerHTML = `<option value="">Mes schémas (${liste.length})…</option>` + liste.map((n) => `<option>${n.replace(/</g, "&lt;")}</option>`).join("");
    } catch { /* pas encore deverrouille */ }
  }
  $("#af-enregistrer").onclick = async () => {
    const v = versions.find((x) => x.numero === actuelle);
    if (!v) return;
    const nom = prompt("Nom du schéma :", v.nom && v.nom !== "nouveau" ? v.nom : "mon-schema");
    if (!nom) return;
    try {
      const r = await api("schemas/enregistrer", { nom, code: $("#af-texte").value });
      v.nom = r.nom;
      majVersions();
      majMesSchemas();
      message("Schéma enregistré sur la clé : " + r.nom);
    } catch (e) { message(e.message); }
  };
  $("#af-mes").onfocus = majMesSchemas;
  $("#af-mes").onchange = async () => {
    const nom = $("#af-mes").value;
    if (!nom) return;
    try { const r = await api("schemas/lire", { nom }); ajouterVersion(r.code, nom); }
    catch (e) { message(e.message); }
    $("#af-mes").value = "";
  };

  window.TiArtefact = {
    ajouterVersion,
    ouvrir,
    // le schema ouvert, envoye a l'IA pour qu'elle puisse le modifier
    actuel: () => (panneau.hidden ? null : (versions.find((x) => x.numero === actuelle) || {}).code || null),
  };
})();
