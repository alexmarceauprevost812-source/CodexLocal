/* Ti-Labo — panneau « artefact » pour construire des schemas dans la Jasette */
"use strict";

(function () {
  const $ = (s) => document.querySelector(s);
  const panneau = $("#artefact");
  if (!panneau) return;
  const carte = panneau; // contient .schema-dessin : sert de « carte » pour TiSchemas
  const versions = [];
  const images = {};            // "ti-image:1" -> data:image/...
  let compteurImages = 0;
  let theme = "aucun";
  try { theme = localStorage.getItem("tilabo-schema-theme") || "arc"; } catch { theme = "arc"; }

  const THEMES = {
    tilabo: [["#1F1208", "#FF7A1A", "#FFFFFF"]],
    arc: [["#3B0A0A", "#FF5F5F", "#FFFFFF"], ["#3A2200", "#FFA726", "#FFFFFF"], ["#2E2A00", "#FFE14D", "#FFFFFF"],
          ["#0B2E14", "#4ADE80", "#FFFFFF"], ["#082A33", "#6CD4FF", "#FFFFFF"], ["#1A1240", "#A5B4FC", "#FFFFFF"], ["#2E0F33", "#F0A3FF", "#FFFFFF"]],
    ocean: [["#04263A", "#38BDF8", "#FFFFFF"], ["#062F2F", "#2DD4BF", "#FFFFFF"], ["#0B1E44", "#60A5FA", "#FFFFFF"], ["#10253A", "#93C5FD", "#FFFFFF"]],
    neon: [["#14001F", "#FF2BD6", "#FFFFFF"], ["#001A14", "#00FFA3", "#FFFFFF"], ["#1C1600", "#FFE600", "#FFFFFF"], ["#00121F", "#00D1FF", "#FFFFFF"]],
    pastel: [["#FDE2E4", "#E07A8B", "#2B2B2B"], ["#E2F0CB", "#7FB069", "#2B2B2B"], ["#DCEBFA", "#6A9BD8", "#2B2B2B"], ["#FFF1C1", "#E0B23A", "#2B2B2B"], ["#EBDDF7", "#A383D6", "#2B2B2B"]],
  };

  // Ajoute des couleurs a un flowchart qui n'en a pas deja
  function colorer(code) {
    const pal = THEMES[theme];
    if (!pal || !/^\s*(flowchart|graph)\b/i.test(code) || /^\s*(classDef|style)\b/m.test(code)) return code;
    const ids = [];
    const re = /(^|[\s&>|;-])([A-Za-z][\w-]*)\s*(?=\[|\(|\{|>|@\{)/g;
    code.split("\n").slice(1).forEach((l) => {
      if (/^\s*(%%|subgraph|end\b|class |click )/.test(l)) return;
      let m;
      while ((m = re.exec(l))) if (!ids.includes(m[2])) ids.push(m[2]);
    });
    if (!ids.length) return code;
    const lignes = pal.map(([fond, bord, texte], i) => `classDef ti${i} fill:${fond},stroke:${bord},stroke-width:2px,color:${texte}`);
    const groupes = pal.map(() => []);
    ids.forEach((id, i) => groupes[i % pal.length].push(id));
    groupes.forEach((g, i) => g.length && lignes.push(`class ${g.join(",")} ti${i}`));
    return code.replace(/\s*$/, "") + "\n" + lignes.join("\n");
  }

  // ---- images de fichiers (dessinees ici, 100 % hors-ligne)
  const TYPES = {
    py: ["#3776AB", "#FFD43B", "PY"], js: ["#F7DF1E", "#222", "JS"], mjs: ["#F7DF1E", "#222", "JS"], ts: ["#3178C6", "#fff", "TS"],
    jsx: ["#61DAFB", "#222", "JSX"], tsx: ["#3178C6", "#fff", "TSX"], html: ["#E34F26", "#fff", "HTML"], htm: ["#E34F26", "#fff", "HTML"],
    css: ["#1572B6", "#fff", "CSS"], json: ["#8BC34A", "#1b1b1b", "JSON"], md: ["#9E9E9E", "#111", "MD"], txt: ["#BDBDBD", "#111", "TXT"],
    png: ["#AB47BC", "#fff", "IMG"], jpg: ["#AB47BC", "#fff", "IMG"], jpeg: ["#AB47BC", "#fff", "IMG"], gif: ["#AB47BC", "#fff", "IMG"],
    svg: ["#FFB13B", "#222", "SVG"], bat: ["#4CAF50", "#fff", "BAT"], ps1: ["#2671BE", "#fff", "PS1"], sh: ["#4CAF50", "#fff", "SH"],
    java: ["#E76F00", "#fff", "JAVA"], kt: ["#7F52FF", "#fff", "KT"], c: ["#5C6BC0", "#fff", "C"], cpp: ["#00599C", "#fff", "C++"],
    rs: ["#DEA584", "#222", "RS"], go: ["#00ADD8", "#fff", "GO"], php: ["#777BB4", "#fff", "PHP"], sql: ["#E38C00", "#fff", "SQL"],
    yml: ["#CB171E", "#fff", "YML"], yaml: ["#CB171E", "#fff", "YML"], toml: ["#9C4221", "#fff", "TOML"], gguf: ["#FF7A1A", "#000", "IA"],
  };
  const cacheIcones = {};
  function icone(type) {
    if (cacheIcones[type]) return cacheIcones[type];
    let svg;
    if (type === "dossier") {
      svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><path d="M4 14a4 4 0 0 1 4-4h16l6 6h26a4 4 0 0 1 4 4v32a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z" fill="#FFB020"/><path d="M4 24h56v28a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4z" fill="#FF9A1A"/></svg>`;
    } else if (type === "projet") {
      svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><rect x="4" y="6" width="56" height="52" rx="10" fill="#1F1208" stroke="#FF7A1A" stroke-width="3"/><path d="M16 24l8 8-8 8M30 40h16" stroke="#FF7A1A" stroke-width="4" fill="none" stroke-linecap="round" stroke-linejoin="round"/></svg>`;
    } else {
      const [fond, texte, etiquette] = TYPES[type] || ["#FF7A1A", "#000", (type || "?").toUpperCase().slice(0, 4)];
      const taille = etiquette.length > 3 ? 11 : 14;
      svg = `<svg xmlns="http://www.w3.org/2000/svg" width="64" height="64" viewBox="0 0 64 64"><path d="M12 4h28l14 14v40a2 2 0 0 1-2 2H12a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2z" fill="#F5F5F5"/><path d="M40 4v12a2 2 0 0 0 2 2h12" fill="#D6D6D6"/><rect x="6" y="32" width="44" height="18" rx="3" fill="${fond}"/><text x="28" y="45.5" font-family="Segoe UI,Arial,sans-serif" font-size="${taille}" font-weight="700" text-anchor="middle" fill="${texte}">${etiquette}</text><path d="M18 16h14M18 22h20M18 56h28" stroke="#BDBDBD" stroke-width="2"/></svg>`;
    }
    return (cacheIcones[type] = "data:image/svg+xml;base64," + btoa(unescape(encodeURIComponent(svg))));
  }

  function preparer(code) {
    let c = colorer(code);
    c = c.replace(/ti-image:\d{1,4}/g, (id) => images[id] || id);
    c = c.replace(/ti-icone:([a-z0-9+]{1,8})/g, (_, t) => icone(t));
    return c;
  }

  // ---- squelette d'un projet : chaque fichier devient une image de fichier
  function squelette(projet, info) {
    const MAX = 70;
    const fichiers = info.fichiers.slice(0, MAX);
    const idDe = {}, lignes = ["flowchart LR"];
    let n = 0;
    const id = (cle) => idDe[cle] || (idDe[cle] = "N" + ++n);
    const propre = (t) => t.replace(/"/g, "'");
    lignes.push(`    ${id("/")}@{ img: "ti-icone:projet", label: "${propre(projet)}", pos: "b", w: 64, h: 64, constraint: "on" }`);
    const dossiers = new Set();
    fichiers.forEach((f) => {
      const parts = f.split("/");
      let parent = "/";
      for (let i = 0; i < parts.length - 1; i++) {
        const d = parts.slice(0, i + 1).join("/");
        if (!dossiers.has(d)) {
          dossiers.add(d);
          lignes.push(`    ${id(d)}@{ img: "ti-icone:dossier", label: "${propre(parts[i])}", pos: "b", w: 52, h: 52, constraint: "on" }`);
          lignes.push(`    ${id(parent)} --> ${id(d)}`);
        }
        parent = d;
      }
      const ext = (f.includes(".") ? f.split(".").pop() : "").toLowerCase().replace(/[^a-z0-9+]/g, "");
      lignes.push(`    ${id(f)}@{ img: "ti-icone:${ext || "txt"}", label: "${propre(parts[parts.length - 1])}", pos: "b", w: 48, h: 48, constraint: "on" }`);
      lignes.push(`    ${id(parent)} --> ${id(f)}`);
    });
    info.liens.forEach(([a, b]) => { if (idDe[a] && idDe[b]) lignes.push(`    ${idDe[a]} -.->|"utilise"| ${idDe[b]}`); });
    if (info.fichiers.length > MAX) lignes.push(`    PLUS["➕ ${info.fichiers.length - MAX} autres fichiers"]`, `    ${id("/")} --> PLUS`);
    lignes.push("    linkStyle default stroke:#FF9A4D");
    return lignes.join("\n");
  }

  function enregistrerImage(dataUrl) {
    const deja = Object.keys(images).find((k) => images[k] === dataUrl);
    if (deja) return deja;
    const id = "ti-image:" + ++compteurImages;
    images[id] = dataUrl;
    return id;
  }
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

  // ---- images et couleurs
  const barre = $(".af-outils");
  const choixCouleur = document.createElement("select");
  choixCouleur.className = "champ";
  choixCouleur.style.width = "auto";
  choixCouleur.setAttribute("aria-label", "Couleurs du schéma");
  choixCouleur.innerHTML = [["aucun", "Couleurs : de l'IA"], ["arc", "Arc-en-ciel"], ["tilabo", "Ti-Labo orange"], ["ocean", "Océan"], ["neon", "Néon"], ["pastel", "Pastel"]]
    .map(([v, t]) => `<option value="${v}" ${v === theme ? "selected" : ""}>${t}</option>`).join("");
  choixCouleur.onchange = () => {
    theme = choixCouleur.value;
    try { localStorage.setItem("tilabo-schema-theme", theme); } catch {}
    afficher($("#af-texte").value, false);
  };
  const btImage = document.createElement("button");
  btImage.className = "btn petit";
  btImage.textContent = "+ Image";
  const fichierImage = document.createElement("input");
  fichierImage.type = "file"; fichierImage.accept = "image/*"; fichierImage.hidden = true;
  btImage.onclick = () => fichierImage.click();
  fichierImage.onchange = async () => {
    const f = fichierImage.files[0];
    fichierImage.value = "";
    if (!f) return;
    const data = await (window.reduireImage ? window.reduireImage(f, 512) : new Promise((ok) => { const r = new FileReader(); r.onload = () => ok(r.result); r.readAsDataURL(f); }));
    const id = enregistrerImage(data);
    const n = id.split(":")[1];
    const t = $("#af-texte");
    if (!/^\s*(flowchart|graph)\b/i.test(t.value)) t.value = "flowchart TD\n" + t.value;
    t.value = t.value.replace(/\s*$/, "") + `\n    IMG${n}@{ img: "${id}", label: "${f.name.replace(/\.[^.]+$/, "").replace(/"/g, "")}", pos: "b", w: 140, h: 140, constraint: "on" }`;
    t.dispatchEvent(new Event("input"));
    message("Image ajoutée au schéma (IMG" + n + "). Relie-la dans le Code, ou demande à l'IA de la placer.");
  };
  const choixSquelette = document.createElement("select");
  choixSquelette.className = "champ";
  choixSquelette.style.width = "auto";
  choixSquelette.setAttribute("aria-label", "Squelette d'un projet");
  choixSquelette.innerHTML = `<option value="">📁 Squelette du projet…</option>`;
  choixSquelette.onfocus = choixSquelette.onmousedown = async () => {
    try {
      const projets = await api("projets");
      choixSquelette.innerHTML = `<option value="">📁 Squelette du projet…</option>` + projets.map((p) => `<option>${p.nom.replace(/</g, "&lt;")}</option>`).join("");
    } catch {}
  };
  let dernierProjet = null;
  choixSquelette.onchange = async () => {
    const projet = choixSquelette.value;
    if (projet) dernierProjet = projet;
    choixSquelette.value = "";
    if (!projet) return;
    try {
      const info = await api("projet/squelette?projet=" + encodeURIComponent(projet));
      if (!info.fichiers.length) return message("Ce projet est vide.");
      ajouterVersion(squelette(projet, info), "squelette " + projet);
      message(`Squelette de « ${projet} » : ${info.fichiers.length} fichiers, ${info.liens.length} liens entre fichiers`);
    } catch (e) { message(e.message); }
  };
  barre.insertBefore(choixSquelette, $("#af-enregistrer"));

  // ---- points positifs et negatifs d'un projet, analyses par l'IA a partir du vrai code
  const btAnalyse = document.createElement("button");
  btAnalyse.className = "btn petit";
  btAnalyse.textContent = "👍👎 Points + et −";
  btAnalyse.title = "L'IA lit le projet et fait un schéma de ses points positifs et négatifs";
  btAnalyse.onclick = async () => {
    if (!dernierProjet) return message("Choisis d'abord un projet dans « 📁 Squelette du projet ».");
    if (typeof jasette === "undefined") return;
    btAnalyse.disabled = true;
    try {
      const info = await api("projet/squelette?projet=" + encodeURIComponent(dernierProjet));
      // les fichiers les plus importants : README, main, puis les plus gros fichiers de code
      const code = info.fichiers.filter((f) => /\.(py|js|ts|html|css|md|json|bat|sh|java|kt|c|cpp|rs|go|php)$/i.test(f));
      const prio = (f) => (/readme/i.test(f) ? 0 : /^(main|app|index|server)\./i.test(f.split("/").pop()) ? 1 : 2);
      const choisis = code.sort((a, b) => prio(a) - prio(b)).slice(0, 6);
      const extraits = [];
      for (const f of choisis) {
        try {
          const r = await api("fichier?chemin=" + encodeURIComponent(dernierProjet + "/" + f));
          extraits.push(`--- ${f} ---\n` + r.contenu.split("\n").slice(0, 60).join("\n"));
        } catch { /* fichier illisible : on saute */ }
      }
      const demande =
        `Analyse honnêtement mon projet « ${dernierProjet} » et fais UN schéma mermaid (flowchart LR) de ses points positifs et négatifs.\n` +
        `Structure : le projet au centre, une branche « ✅ Points positifs » et une branche « ⚠️ Points négatifs », 3 à 6 points chacune, ` +
        `textes courts entre guillemets avec un emoji au début. Colore avec classDef : positifs en vert (fill:#0B2E14,stroke:#4ADE80,color:#fff), ` +
        `négatifs en rouge (fill:#3B0A0A,stroke:#FF5F5F,color:#fff), projet en orange (fill:#1F1208,stroke:#FF7A1A,color:#fff). ` +
        `Base-toi seulement sur ce qui est vraiment dans le code ci-dessous, puis donne 2 ou 3 conseils concrets pour améliorer.\n\n` +
        `Fichiers (${info.fichiers.length}) : ${info.fichiers.slice(0, 80).join(", ")}\n` +
        `Liens entre fichiers : ${info.liens.map(([a, b]) => a + " → " + b).join(", ") || "aucun"}\n\n` + extraits.join("\n\n");
      jasette.envoyer(demande);
    } catch (e) { message(e.message); }
    btAnalyse.disabled = false;
  };
  barre.insertBefore(btAnalyse, $("#af-enregistrer"));
  barre.insertBefore(choixCouleur, $("#af-enregistrer"));
  barre.insertBefore(btImage, $("#af-enregistrer"));
  barre.appendChild(fichierImage);

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
      const code = $("#af-texte").value;
      const utilisees = {};
      (code.match(/ti-image:\d{1,4}/g) || []).forEach((id) => { if (images[id]) utilisees[id] = images[id]; });
      const r = await api("schemas/enregistrer", { nom, code, images: utilisees });
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
    try {
      const r = await api("schemas/lire", { nom });
      Object.entries(r.images || {}).forEach(([id, data]) => {
        images[id] = data;
        compteurImages = Math.max(compteurImages, +id.split(":")[1] || 0);
      });
      ajouterVersion(r.code, nom);
    }
    catch (e) { message(e.message); }
    $("#af-mes").value = "";
  };

  window.TiArtefact = {
    ajouterVersion,
    preparer,
    enregistrerImage,
    ouvrir,
    // le schema ouvert, envoye a l'IA pour qu'elle puisse le modifier
    actuel: () => (panneau.hidden ? null : (versions.find((x) => x.numero === actuelle) || {}).code || null),
  };
})();
