/* Ti-Labo — Devoirs : document imprimable + présentation plein écran animée.
   Ce fichier marche aussi tout seul (dans la présentation exportée en .html),
   donc il ne dépend PAS de app.js. Il utilise TiSchemas (schemas.js) s'il est là. */
(function () {
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // ---------------------------------------------------------------- texte d'une ligne
  function enLigne(t, image) {
    return esc(t)
      .replace(/!\[([^\]]*)\]\(image:([^)\s]+)\)/g, (_, alt, nom) => {
        const src = image(decodeURIComponent(nom));
        return src ? `<img class="dv-img-ligne" src="${esc(src)}" alt="${alt}">` : "";
      })
      .replace(/\*\*(.+?)\*\*/g, "<b>$1</b>")
      .replace(/(^|[^*])\*([^*\n]+)\*/g, "$1<i>$2</i>")
      .replace(/`([^`\n]+)`/g, "<code>$1</code>")
      .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>');
  }

  // ---------------------------------------------------------------- markdown -> blocs HTML
  // Reconnu : # ## ###, paragraphes, listes, listes numérotées, > citation, tableaux |a|b|,
  // ![légende](image:nom.png), [IMAGE: description] (image à créer), ```mermaid (schéma animé)
  function blocs(md, image, options = {}) {
    const lignes = String(md || "").replace(/\r/g, "").split("\n");
    const out = [];
    let i = 0;
    while (i < lignes.length) {
      const l = lignes[i];
      if (/^```/.test(l)) {
        const lang = l.slice(3).trim().toLowerCase();
        const code = [];
        i++;
        while (i < lignes.length && !/^```/.test(lignes[i])) code.push(lignes[i++]);
        i++;
        if (lang === "mermaid" || /^(flowchart|graph|sequenceDiagram|mindmap|timeline|pie|classDiagram|stateDiagram|erDiagram|journey|gantt)\b/.test(code[0] || "")) {
          out.push(`<figure class="dv-schema" data-code="${esc(code.join("\n"))}"><div class="schema-dessin"></div></figure>`);
        } else {
          out.push(`<pre class="dv-code"><code>${esc(code.join("\n"))}</code></pre>`);
        }
        continue;
      }
      if (!l.trim()) { i++; continue; }
      let m;
      if ((m = l.match(/^(#{1,4})\s+(.*)$/))) {
        const n = Math.min(4, m[1].length);
        out.push(`<h${n}>${enLigne(m[2], image)}</h${n}>`);
        i++; continue;
      }
      if ((m = l.match(/^\s*!\[([^\]]*)\]\(image:([^)\s]+)\)\s*$/))) {
        const src = image(decodeURIComponent(m[2]));
        out.push(src ? `<figure class="dv-image"><img src="${esc(src)}" alt="${esc(m[1])}">${m[1] ? `<figcaption>${esc(m[1])}</figcaption>` : ""}</figure>`
          : `<figure class="dv-image manquante"><figcaption>Image introuvable : ${esc(m[2])}</figcaption></figure>`);
        i++; continue;
      }
      if ((m = l.match(/^\s*\[IMAGE\s*:\s*(.+?)\]\s*$/i))) {
        if (options.brouillon) out.push(`<figure class="dv-a-creer" data-prompt="${esc(m[1])}"><span>🖼️ Image à ajouter :</span> <i>${esc(m[1])}</i> <button class="dv-bt" data-dv="creer-image">Créer avec l'IA</button> <button class="dv-bt" data-dv="choisir-image">Mettre la mienne</button></figure>`);
        i++; continue;
      }
      if (/^\s*\|.*\|\s*$/.test(l)) {
        const rangs = [];
        while (i < lignes.length && /^\s*\|.*\|\s*$/.test(lignes[i])) rangs.push(lignes[i++]);
        const cellules = (r) => r.trim().replace(/^\||\|$/g, "").split("|").map((c) => c.trim());
        const corps = rangs.filter((r) => !/^\s*\|[\s:|-]+\|\s*$/.test(r));
        const [tete, ...reste] = corps;
        out.push(`<table class="dv-table"><thead><tr>${cellules(tete).map((c) => `<th>${enLigne(c, image)}</th>`).join("")}</tr></thead><tbody>${reste.map((r) => `<tr>${cellules(r).map((c) => `<td>${enLigne(c, image)}</td>`).join("")}</tr>`).join("")}</tbody></table>`);
        continue;
      }
      if (/^\s*[-*•]\s+/.test(l)) {
        const items = [];
        while (i < lignes.length && /^\s*[-*•]\s+/.test(lignes[i])) items.push(lignes[i++].replace(/^\s*[-*•]\s+/, ""));
        out.push(`<ul>${items.map((x) => `<li>${enLigne(x, image)}</li>`).join("")}</ul>`);
        continue;
      }
      if (/^\s*\d+[.)]\s+/.test(l)) {
        const items = [];
        while (i < lignes.length && /^\s*\d+[.)]\s+/.test(lignes[i])) items.push(lignes[i++].replace(/^\s*\d+[.)]\s+/, ""));
        out.push(`<ol>${items.map((x) => `<li>${enLigne(x, image)}</li>`).join("")}</ol>`);
        continue;
      }
      if (/^\s*>/.test(l)) {
        const q = [];
        while (i < lignes.length && /^\s*>/.test(lignes[i])) q.push(lignes[i++].replace(/^\s*>\s?/, ""));
        out.push(`<blockquote>${q.map((x) => enLigne(x, image)).join("<br>")}</blockquote>`);
        continue;
      }
      const p = [];
      while (i < lignes.length && lignes[i].trim() && !/^(#{1,4}\s|```|\s*[-*•]\s|\s*\d+[.)]\s|\s*>|\s*\||\s*!\[|\s*\[IMAGE)/i.test(lignes[i])) p.push(lignes[i++]);
      if (!p.length) { i++; continue; }   // ligne non reconnue : on avance
      out.push(`<p>${p.map((x) => enLigne(x, image)).join("<br>")}</p>`);
    }
    return out;
  }

  // ---------------------------------------------------------------- le document (page blanche)
  function entete(meta) {
    const infos = [meta.eleve, meta.matiere, meta.niveau, meta.date].filter(Boolean).map(esc);
    return infos.length ? `<div class="dv-infos">${infos.join(" · ")}</div>` : "";
  }
  function documentHtml(md, meta = {}, image = () => null, options = {}) {
    const b = blocs(md, image, options);
    // le # titre en premier + l'entête (élève, matière…) juste dessous
    const iTitre = b.findIndex((x) => x.startsWith("<h1"));
    if (iTitre >= 0) b.splice(iTitre + 1, 0, entete(meta));
    else b.unshift((meta.titre ? `<h1>${esc(meta.titre)}</h1>` : "") + entete(meta));
    return `<article class="dv-papier">${b.join("\n")}</article>`;
  }

  async function activerSchemas(conteneur, animation = false) {
    if (!window.TiSchemas) return;
    for (const f of conteneur.querySelectorAll(".dv-schema")) {
      if (f.dataset.fait) continue;
      f.dataset.fait = "1";
      try { await TiSchemas.dessiner(f, f.dataset.code, animation); }
      catch (e) { f.innerHTML = `<figcaption class="dv-erreur">Schéma à corriger : ${esc(String(e.message || e).slice(0, 160))}</figcaption>`; }
    }
  }

  // ---------------------------------------------------------------- diapos
  function diapos(md, meta = {}, image = () => null) {
    const texte = String(md || "").replace(/\r/g, "");
    const morceaux = texte.split(/\n(?=##\s)/);
    const res = [];
    let debut = morceaux[0].startsWith("## ") ? "" : morceaux.shift();
    const titre = (debut.match(/^#\s+(.*)$/m) || [])[1] || meta.titre || "Mon devoir";
    const intro = blocs(debut.replace(/^#\s+.*$/m, ""), image);
    res.push({ couverture: true, html: `<h1>${enLigne(titre, image)}</h1>${entete(meta)}${intro.join("")}` });
    for (const m of morceaux) {
      const h = blocs(m, image);
      if (h.length) res.push({ html: h.join("") });
    }
    return res;
  }

  // ---------------------------------------------------------------- présentation plein écran
  function presenter(md, meta = {}, image = () => null, depart = 0) {
    const liste = diapos(md, meta, image);
    document.querySelector(".dv-show")?.remove();
    const show = document.createElement("div");
    show.className = "dv-show";
    show.setAttribute("role", "dialog");
    show.setAttribute("aria-label", "Présentation");
    show.innerHTML = `<div class="dv-scene"></div>
      <div class="dv-barre"><div class="dv-prog"><i></i></div>
        <button class="dv-nav" data-n="-1" aria-label="Diapo précédente">◀</button>
        <span class="dv-compte"></span>
        <button class="dv-nav" data-n="1" aria-label="Diapo suivante">▶</button>
        <span class="dv-espace"></span>
        <button class="dv-nav" data-n="plein" aria-label="Plein écran">⛶</button>
        <button class="dv-nav" data-n="fermer" aria-label="Fermer la présentation">✕</button></div>`;
    document.body.appendChild(show);
    const scene = show.querySelector(".dv-scene");
    let n = Math.max(0, Math.min(liste.length - 1, depart));
    const aller = (k) => {
      n = Math.max(0, Math.min(liste.length - 1, k));
      const d = liste[n];
      scene.innerHTML = `<section class="dv-diapo ${d.couverture ? "couverture" : ""}">${d.html}</section>`;
      // chaque élément arrive un après l'autre (titre, texte, puces, image, schéma)
      let delai = 0;
      scene.querySelectorAll(".dv-diapo > *, .dv-diapo li").forEach((el) => {
        el.style.animationDelay = (delai += el.tagName === "LI" ? 0.18 : 0.12) + "s";
        el.classList.add("dv-entre");
      });
      activerSchemas(scene, true);
      show.querySelector(".dv-compte").textContent = `${n + 1} / ${liste.length}`;
      show.querySelector(".dv-prog i").style.width = ((n + 1) / liste.length) * 100 + "%";
    };
    const fermer = () => {
      document.removeEventListener("keydown", touche);
      if (document.fullscreenElement) document.exitFullscreen().catch(() => {});
      show.remove();
    };
    const touche = (e) => {
      if (["ArrowRight", "PageDown", " ", "Enter"].includes(e.key)) { e.preventDefault(); aller(n + 1); }
      else if (["ArrowLeft", "PageUp", "Backspace"].includes(e.key)) { e.preventDefault(); aller(n - 1); }
      else if (e.key === "Home") aller(0);
      else if (e.key === "End") aller(liste.length - 1);
      else if (e.key === "Escape") fermer();
      else if (e.key.toLowerCase() === "f") pleinEcran();
    };
    const pleinEcran = () => (document.fullscreenElement ? document.exitFullscreen() : show.requestFullscreen()).catch(() => {});
    document.addEventListener("keydown", touche);
    show.querySelector(".dv-barre").addEventListener("click", (e) => {
      const b = e.target.closest("[data-n]"); if (!b) return;
      if (b.dataset.n === "fermer") fermer();
      else if (b.dataset.n === "plein") pleinEcran();
      else aller(n + +b.dataset.n);
    });
    scene.addEventListener("click", (e) => { if (!e.target.closest("a, button")) aller(n + 1); });
    pleinEcran();
    aller(n);
    return { aller, fermer };
  }

  // ---------------------------------------------------------------- page autonome (fichier .html exporté)
  function autonome(md, meta, images) {
    const image = (nom) => images[nom] || null;
    document.title = meta.titre || "Devoir";
    document.body.innerHTML = `<div class="dv-autonome-barre"><b>${esc(meta.titre || "Devoir")}</b><span class="dv-espace"></span>
      <button class="dv-bt prim" id="dv-go">▶ Présenter</button><button class="dv-bt" id="dv-print">🖨️ Imprimer</button></div>
      <div class="dv-autonome">${documentHtml(md, meta, image)}</div>`;
    activerSchemas(document.querySelector(".dv-autonome"), false);
    document.getElementById("dv-go").onclick = () => presenter(md, meta, image);
    document.getElementById("dv-print").onclick = () => window.print();
  }

  window.TiDevoir = { blocs, documentHtml, activerSchemas, diapos, presenter, autonome, esc };
})();
