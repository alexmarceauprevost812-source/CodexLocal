/* Ti-Labo — schemas animes dans le chat + export en video (100 % hors-ligne, Mermaid local) */
"use strict";

(function () {
  let pret = false, compteur = 0;

  function initialiser() {
    if (pret || !window.mermaid) return !!window.mermaid;
    window.mermaid.initialize({
      startOnLoad: false,
      securityLevel: "strict",
      theme: "base",
      // htmlLabels:false = texte SVG pur, indispensable pour exporter en video/PNG
      htmlLabels: false,
      flowchart: { htmlLabels: false, curve: "basis", padding: 14 },
      // Coins arrondis : ce CSS est inclus DANS le schema, donc aussi dans la video et l'image
      themeCSS: ".node rect, .node .label-container, rect.basic, .cluster rect, .actor, .note, rect.task { rx: 14px; ry: 14px; }" +
        " .edgeLabel rect, .labelBkg { rx: 8px; ry: 8px; } .node polygon { stroke-linejoin: round; }",
      themeVariables: {
        darkMode: true, background: "#000000", fontFamily: "Segoe UI, system-ui, sans-serif", fontSize: "16px",
        primaryColor: "#1F1208", primaryBorderColor: "#FF7A1A", primaryTextColor: "#FFFFFF",
        secondaryColor: "#111111", secondaryBorderColor: "#6CD4FF", secondaryTextColor: "#FFFFFF",
        tertiaryColor: "#0A0A0A", tertiaryBorderColor: "#3A3A3A", tertiaryTextColor: "#E5E5E5",
        lineColor: "#FF9A4D", textColor: "#E5E5E5", mainBkg: "#1F1208", nodeBorder: "#FF7A1A",
        clusterBkg: "#0A0A0A", clusterBorder: "#3A3A3A", edgeLabelBackground: "#000000",
        actorBkg: "#1F1208", actorBorder: "#FF7A1A", actorTextColor: "#FFFFFF", signalColor: "#FF9A4D",
        signalTextColor: "#E5E5E5", noteBkgColor: "#111111", noteTextColor: "#E5E5E5", noteBorderColor: "#3A3A3A",
      },
    });
    pret = true;
    return true;
  }

  // Les morceaux a faire apparaitre, dans l'ordre de lecture (haut -> bas, gauche -> droite)
  function morceaux(svg) {
    const sel = [
      "g.node", "g.cluster", ".edgePath", "path.flowchart-link", "g.edgeLabel",
      "g.actor", "line.actor-line", ".messageLine0", ".messageLine1", ".messageText", ".note", ".noteText",
      ".mindmap-node", ".section", "g.task", "rect.task", ".taskText", "g.classGroup", ".relation",
      "g.stateGroup", ".transition", "g.er.entityBox", ".er.relationshipLine", ".pieCircle", ".slice", ".legend",
      "g.timeline-node", ".journey-section", "g.commit-bullets > *", ".arrow",
    ].join(",");
    const vus = new Set(), liste = [];
    svg.querySelectorAll(sel).forEach((el) => {
      if (vus.has(el) || [...vus].some((v) => v.contains(el))) return;
      vus.add(el);
      let b;
      try { b = el.getBBox(); } catch { b = { x: 0, y: 0, width: 0, height: 0 }; }
      const trait = el.matches("path, line") || el.querySelector(":scope > path");
      liste.push({ el, x: b.x + b.width / 2, y: b.y + b.height / 2, trait: !!trait && !el.matches("g.node, g.cluster") });
    });
    // les traits apparaissent juste apres ce qu'ils relient : on les decale un peu vers le bas
    liste.forEach((m) => (m.cle = m.y + (m.trait ? 18 : 0)));
    liste.sort((a, b) => a.cle - b.cle || a.x - b.x);
    return liste;
  }

  function preparerTrait(el) {
    const chemins = el.matches("path, line") ? [el] : [...el.querySelectorAll("path, line")];
    chemins.forEach((p) => {
      let l = 300;
      try { l = p.getTotalLength ? p.getTotalLength() : 300; } catch { /* ligne */ }
      p.dataset.long = l;
    });
    return chemins;
  }

  // progres de 0 a 1 pour chaque morceau a l'instant t (secondes)
  const DUREE_MORCEAU = 0.55, ECART = 0.32;
  function etatA(liste, t) {
    return liste.map((m, i) => Math.max(0, Math.min(1, (t - i * ECART) / DUREE_MORCEAU)));
  }
  function appliquer(liste, progres) {
    liste.forEach((m, i) => {
      const p = progres[i];
      if (m.trait) {
        m.chemins.forEach((c) => {
          const l = +c.dataset.long || 300;
          c.style.strokeDasharray = l;
          c.style.strokeDashoffset = String(l * (1 - p));
        });
        m.el.style.opacity = p > 0 ? 1 : 0;
        m.el.querySelectorAll("marker, polygon").forEach((x) => (x.style.opacity = p >= 1 ? 1 : 0));
      } else {
        // opacite seulement : les groupes Mermaid ont deja leur propre transform (position)
        m.el.style.opacity = String(p);
      }
    });
  }

  function animer(carte) {
    const svg = carte.querySelector("svg");
    if (!svg) return;
    const liste = carte._morceaux || (carte._morceaux = morceaux(svg).map((m) => ({ ...m, chemins: m.trait ? preparerTrait(m.el) : [] })));
    cancelAnimationFrame(carte._raf);
    const total = liste.length * ECART + DUREE_MORCEAU;
    if (window.matchMedia("(prefers-reduced-motion: reduce)").matches) { appliquer(liste, liste.map(() => 1)); return; }
    const depart = performance.now();
    const pas = () => {
      const t = (performance.now() - depart) / 1000;
      appliquer(liste, etatA(liste, t));
      if (t < total) carte._raf = requestAnimationFrame(pas);
    };
    pas();
  }

  // Un <foreignObject> (texte HTML) bloque l'export : on le remplace par du texte SVG equivalent
  function sansHtml(clone) {
    clone.querySelectorAll("foreignObject").forEach((fo) => {
      const w = +fo.getAttribute("width") || 0, h = +fo.getAttribute("height") || 0;
      const x = +fo.getAttribute("x") || 0, y = +fo.getAttribute("y") || 0;
      const lignes = (fo.textContent || "").trim().split(/\n+/).filter(Boolean);
      const t = document.createElementNS("http://www.w3.org/2000/svg", "text");
      t.setAttribute("text-anchor", "middle");
      t.setAttribute("dominant-baseline", "central");
      t.setAttribute("fill", "#FFFFFF");
      t.setAttribute("font-family", "Segoe UI, system-ui, sans-serif");
      t.setAttribute("font-size", "16");
      lignes.forEach((l, i) => {
        const ts = document.createElementNS("http://www.w3.org/2000/svg", "tspan");
        ts.setAttribute("x", x + w / 2);
        ts.setAttribute("y", y + h / 2 + (i - (lignes.length - 1) / 2) * 19);
        ts.textContent = l;
        t.appendChild(ts);
      });
      fo.replaceWith(t);
    });
    return clone;
  }

  async function dessinerImage(svg, ctx, larg, haut) {
    const clone = sansHtml(svg.cloneNode(true));
    clone.setAttribute("width", larg);
    clone.setAttribute("height", haut);
    clone.style.background = "#000";
    const txt = new XMLSerializer().serializeToString(clone);
    const url = URL.createObjectURL(new Blob([txt], { type: "image/svg+xml;charset=utf-8" }));
    try {
      const img = new Image();
      img.decoding = "sync";
      await new Promise((ok, ko) => { img.onload = ok; img.onerror = ko; img.src = url; });
      ctx.fillStyle = "#000";
      ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
      const pad = 40;
      ctx.drawImage(img, pad, pad, ctx.canvas.width - pad * 2, ctx.canvas.height - pad * 2);
    } finally { URL.revokeObjectURL(url); }
  }

  function taille(svg) {
    const vb = svg.viewBox.baseVal;
    const l = vb && vb.width ? vb.width : svg.getBoundingClientRect().width;
    const h = vb && vb.height ? vb.height : svg.getBoundingClientRect().height;
    // 1280 de large au maximum, dimensions paires (exige par l'encodeur video)
    const k = Math.min(2.5, 1200 / l, 1000 / h);
    const pair = (v) => Math.max(2, Math.round(v) & ~1);
    return { l: pair(l * k), h: pair(h * k), cl: pair(l * k + 80), ch: pair(h * k + 80) };
  }

  function telecharger(blob, nom) {
    const a = document.createElement("a");
    a.href = URL.createObjectURL(blob);
    a.download = nom;
    document.body.appendChild(a);
    a.click();
    setTimeout(() => { URL.revokeObjectURL(a.href); a.remove(); }, 2000);
  }

  async function video(carte, bouton) {
    const svg = carte.querySelector("svg");
    const liste = carte._morceaux;
    if (!svg || !liste) return;
    const formats = ["video/mp4;codecs=avc1", "video/mp4", "video/webm;codecs=vp9", "video/webm"];
    const type = formats.find((f) => window.MediaRecorder && MediaRecorder.isTypeSupported(f));
    if (!type) { alert("Ce navigateur ne sait pas enregistrer de video."); return; }
    const t = taille(svg);
    const canvas = document.createElement("canvas");
    canvas.width = t.cl; canvas.height = t.ch;
    const ctx = canvas.getContext("2d");
    const flux = canvas.captureStream(30);
    const rec = new MediaRecorder(flux, { mimeType: type, videoBitsPerSecond: 6_000_000 });
    const morceauxVid = [];
    rec.ondataavailable = (e) => e.data.size && morceauxVid.push(e.data);
    const fini = new Promise((ok) => (rec.onstop = ok));
    const texte = bouton.textContent;
    bouton.disabled = true;
    const total = liste.length * ECART + DUREE_MORCEAU + 1.5; // 1,5 s de pause a la fin
    cancelAnimationFrame(carte._raf);
    rec.start(250);
    const depart = performance.now();
    try {
      for (;;) {
        const s = (performance.now() - depart) / 1000;
        appliquer(liste, etatA(liste, s));
        await dessinerImage(svg, ctx, t.l, t.h);
        bouton.textContent = "Vidéo… " + Math.min(100, Math.round((s / total) * 100)) + " %";
        if (s >= total) break;
        await new Promise((ok) => requestAnimationFrame(ok));
      }
    } catch (e) {
      alert("Video impossible : " + e.message);
    }
    if (rec.state !== "inactive") { rec.requestData(); rec.stop(); }
    await fini;
    flux.getTracks().forEach((tr) => tr.stop());
    appliquer(liste, liste.map(() => 1));
    bouton.disabled = false;
    bouton.textContent = texte;
    const ext = type.includes("mp4") ? "mp4" : "webm";
    telecharger(new Blob(morceauxVid, { type: type.split(";")[0] }), "schema-ti-labo-" + Date.now() + "." + ext);
  }

  async function png(carte) {
    const svg = carte.querySelector("svg");
    if (!svg) return;
    if (carte._morceaux) appliquer(carte._morceaux, carte._morceaux.map(() => 1));
    const t = taille(svg);
    const canvas = document.createElement("canvas");
    canvas.width = t.cl; canvas.height = t.ch;
    await dessinerImage(svg, canvas.getContext("2d"), t.l, t.h);
    canvas.toBlob((b) => telecharger(b, "schema-ti-labo-" + Date.now() + ".png"), "image/png");
  }

  // Repare les erreurs frequentes des petites IA : textes de boites sans guillemets
  // qui contiennent ( ) [ ] { } : , etc. -> A[Texte (ex: 1, 2)] devient A["Texte (ex: 1, 2)"]
  const OUVRANTS = [["[[", "]]"], ["[(", ")]"], ["([", "])"], ["((", "))"], ["{{", "}}"], ["[/", "/]"], ["[\\", "\\]"], ["[", "]"], ["(", ")"], ["{", "}"], [">", "]"]];
  const APRES = /^\s*($|-->|---|-\.|==>|=+|-+>|<-|&|;|:::|o--|x--|--|~~~|\|)/;
  function reparerLigne(l) {
    let out = "", i = 0;
    while (i < l.length) {
      const m = /^([A-Za-z0-9_\u00C0-\u017F-]+)/.exec(l.slice(i));
      if (m && (i === 0 || /[\s&;>|-]/.test(l[i - 1]))) {
        const id = m[1], j = i + id.length;
        const o = OUVRANTS.find(([a]) => l.startsWith(a, j));
        if (o && !(o[0] === ">" && l[j - 1] === "-")) {
          const [a, b] = o, debut = j + a.length;
          let k = l.indexOf(b, debut), fin = -1;
          while (k >= 0) {
            if (APRES.test(l.slice(k + b.length))) { fin = k; break; }
            k = l.indexOf(b, k + 1);
          }
          if (fin >= 0) {
            let texte = l.slice(debut, fin).trim();
            if (!/^".*"$/.test(texte) && /[()\[\]{}:;,<>#&]/.test(texte)) texte = '"' + texte.replace(/"/g, "#quot;") + '"';
            out += id + a + texte + b;
            i = fin + b.length;
            continue;
          }
        }
        out += id;
        i = j;
        continue;
      }
      out += l[i++];
    }
    return out;
  }
  function reparer(code) {
    const lignes = code.replace(/\r/g, "").split("\n");
    const type = (lignes.find((x) => x.trim()) || "").trim();
    if (!/^(flowchart|graph)\b/i.test(type)) return code;
    return lignes.map((l, n) => (n === 0 || /^\s*(%%|classDef|class |style |linkStyle|subgraph|end\b|direction)/.test(l) ? l : reparerLigne(l))).join("\n");
  }

  // Remplace chaque bloc ```mermaid termine par un schema anime
  async function activer(zone) {
    if (!zone) return;
    const blocs = [...zone.querySelectorAll('.bloc-code[data-lang="mermaid"]:not([data-fait])')];
    if (!blocs.length) return;
    if (!initialiser()) return;
    for (const bloc of blocs) {
      bloc.dataset.fait = "1";
      const code = bloc.querySelector("code").textContent;
      if (window.TiArtefact) {
        // Mode artefact : le schema s'ouvre en grand a droite, une puce reste dans la conversation
        const v = window.TiArtefact.ajouterVersion(code);
        const puce = document.createElement("button");
        puce.className = "puce-artefact";
        puce.innerHTML = `<span class="pa-icone" aria-hidden="true">◇</span><span><b>Schéma</b><small>version ${v.numero} · cliquer pour ouvrir</small></span>`;
        puce.onclick = () => window.TiArtefact.ouvrir(v.numero);
        bloc.style.display = "none";
        bloc.before(puce);
        continue;
      }
      const carte = document.createElement("div");
      carte.className = "schema-carte";
      carte.innerHTML = `<div class="schema-tete"><b>Schéma</b><span class="espace"></span>
        <button class="btn petit" data-sc="rejouer">Rejouer</button>
        <button class="btn petit prim" data-sc="video">Vidéo</button>
        <button class="btn petit" data-sc="png">Image</button>
        <button class="btn petit" data-sc="code">Code</button></div>
        <div class="schema-dessin" role="img" aria-label="Schéma animé"></div>`;
      try {
        let rendu;
        try {
          rendu = await window.mermaid.render("schema-" + Date.now() + "-" + compteur++, code);
        } catch (e1) {
          // deuxieme essai apres reparation automatique
          document.querySelectorAll('[id^="dschema-"]').forEach((x) => x.remove());
          rendu = await window.mermaid.render("schema-" + Date.now() + "-" + compteur++, reparer(code));
        }
        carte.querySelector(".schema-dessin").innerHTML = rendu.svg;
      } catch (e) {
        bloc.insertAdjacentHTML("afterend", `<p class="sous" style="color:var(--red)">Schéma invalide : ${String(e.message || e).replace(/</g, "&lt;").slice(0, 200)}</p>`);
        continue;
      }
      bloc.style.display = "none";
      bloc.before(carte);
      carte.querySelector('[data-sc="rejouer"]').onclick = () => animer(carte);
      carte.querySelector('[data-sc="video"]').onclick = (e) => video(carte, e.currentTarget);
      carte.querySelector('[data-sc="png"]').onclick = () => png(carte);
      carte.querySelector('[data-sc="code"]').onclick = () => { bloc.style.display = bloc.style.display ? "" : "none"; };
      requestAnimationFrame(() => animer(carte));
    }
  }

  // Dessine un schema dans n'importe quelle « carte » (element qui contient .schema-dessin)
  async function dessiner(carte, code, animation = true) {
    if (!initialiser()) throw new Error("Moteur de schémas absent");
    if (window.TiArtefact && window.TiArtefact.preparer) code = window.TiArtefact.preparer(code); // couleurs + images
    let rendu;
    try {
      rendu = await window.mermaid.render("schema-" + Date.now() + "-" + compteur++, code);
    } catch (e1) {
      document.querySelectorAll('[id^="dschema-"]').forEach((x) => x.remove());
      rendu = await window.mermaid.render("schema-" + Date.now() + "-" + compteur++, reparer(code));
    }
    carte.querySelector(".schema-dessin").innerHTML = rendu.svg;
    carte._morceaux = null;
    if (animation) requestAnimationFrame(() => animer(carte));
    else {
      const svg = carte.querySelector("svg");
      if (svg) carte._morceaux = morceaux(svg).map((m) => ({ ...m, chemins: m.trait ? preparerTrait(m.el) : [] }));
    }
  }

  window.TiSchemas = { activer, reparer, dessiner, animer, video, png };
})();
