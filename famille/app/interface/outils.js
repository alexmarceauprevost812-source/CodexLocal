// Les outils de bureau de l'assistante. Tout se passe dans la fenêtre, sur
// l'ordinateur : calculs exacts, documents Word, tableaux Excel, schémas,
// vidéos MP4 et dossier de travail. L'IA décide QUAND s'en servir, mais c'est
// ce code (pas l'IA) qui calcule et qui fabrique les fichiers.
(function () {
  "use strict";

  const argent = new Intl.NumberFormat("fr-CA", { style: "currency", currency: "CAD" });
  const nombreFr = new Intl.NumberFormat("fr-CA", { maximumFractionDigits: 10 });
  const TPS = 0.05;
  const TVQ = 0.09975;

  // ------------------------------------------------------------ calculs
  // Petit analyseur d'expressions : + - * / ^ ( ) et 15 % ; accepte 12,50.
  function calculer(expression) {
    const s = String(expression).replace(/\s+/g, "").replace(/×/g, "*").replace(/÷/g, "/")
      .replace(/(\d),(\d)/g, "$1.$2").replace(/\$/g, "");
    let i = 0;
    const err = (m) => { throw new Error(m); };
    const voir = () => s[i];
    function nombre() {
      const m = s.slice(i).match(/^\d+(\.\d+)?|^\.\d+/);
      if (!m) err(`Je ne comprends pas « ${s.slice(i) || expression} ».`);
      i += m[0].length;
      return parseFloat(m[0]);
    }
    function facteur() {
      let v;
      if (voir() === "-") { i++; return -facteur(); }
      if (voir() === "+") { i++; return facteur(); }
      if (voir() === "(") { i++; v = somme(); if (voir() !== ")") err("Parenthèse manquante."); i++; }
      else v = nombre();
      while (voir() === "%") { i++; v /= 100; }
      if (voir() === "^") { i++; v = Math.pow(v, facteur()); }
      return v;
    }
    function produit() {
      let v = facteur();
      while (voir() === "*" || voir() === "/") {
        const op = s[i++];
        const d = facteur();
        if (op === "/" && d === 0) err("Division par zéro.");
        v = op === "*" ? v * d : v / d;
      }
      return v;
    }
    function somme() {
      let v = produit();
      while (voir() === "+" || voir() === "-") { const op = s[i++]; const d = produit(); v = op === "+" ? v + d : v - d; }
      return v;
    }
    if (!s) err("Expression vide.");
    const r = somme();
    if (i < s.length) err(`Je ne comprends pas « ${s.slice(i)} ».`);
    if (!isFinite(r)) err("Résultat impossible.");
    return Math.round(r * 1e10) / 1e10;
  }

  function taxes(montant, inclus) {
    const m = typeof montant === "number" ? montant : calculer(montant);
    const avant = inclus ? m / (1 + TPS + TVQ) : m;
    const r = (x) => Math.round(x * 100) / 100;
    const tps = r(avant * TPS);
    const tvq = r(avant * TVQ);
    if (inclus) {
      // Taxes incluses : le total est fixé ; le montant avant taxes absorbe
      // l'écart d'arrondi pour que avant + TPS + TVQ = total, au cent près.
      const total = r(m);
      return { avant: Math.round(total * 100 - tps * 100 - tvq * 100) / 100, tps, tvq, total };
    }
    const base = r(avant);
    return { avant: base, tps, tvq, total: r(base + tps + tvq) };
  }

  // Transforme « 1 234,56 $ » en 1234.56 ; sinon null.
  function lireNombre(v) {
    if (typeof v === "number") return v;
    const t = String(v ?? "").replace(/[\s  ]/g, "").replace(/\$/g, "");
    if (!/^-?\d+([.,]\d+)?%?$/.test(t)) return null;
    const n = parseFloat(t.replace(",", ".").replace("%", ""));
    return t.endsWith("%") ? n / 100 : n;
  }

  // ------------------------------------------------- dossier de travail
  // Le dossier choisi dans Réglages est gardé (avec la permission de Windows)
  // dans la mémoire de la fenêtre. Sans dossier, les fichiers vont dans
  // Téléchargements, comme un fichier téléchargé normal.
  const BD = "assistante-dossier";
  function bd() {
    return new Promise((ok, ko) => {
      const r = indexedDB.open(BD, 1);
      r.onupgradeneeded = () => r.result.createObjectStore("cles");
      r.onsuccess = () => ok(r.result);
      r.onerror = () => ko(r.error);
    });
  }
  async function bdLire(cle) {
    const b = await bd();
    return new Promise((ok) => { const r = b.transaction("cles").objectStore("cles").get(cle); r.onsuccess = () => ok(r.result); r.onerror = () => ok(null); });
  }
  async function bdEcrire(cle, val) {
    const b = await bd();
    return new Promise((ok) => { const t = b.transaction("cles", "readwrite"); t.objectStore("cles").put(val, cle); t.oncomplete = ok; t.onerror = ok; });
  }

  let dossier = null;           // FileSystemDirectoryHandle
  let dossierPret = false;      // permission accordée pour cette session
  let dossierEstCle = false;    // c'est bien « Mes documents » de CETTE clé
  const ID_CLE = new URLSearchParams(location.search).get("cle");
  async function verifierCle() {
    dossierEstCle = false;
    if (!dossier || !dossierPret || !ID_CLE) return;
    try {
      const f = await (await dossier.getFileHandle(".cle-assistante")).getFile();
      dossierEstCle = (await f.text()).trim() === ID_CLE;
    } catch { dossierEstCle = false; }
  }

  async function chargerDossier() {
    try {
      dossier = await bdLire("travail");
      if (dossier) dossierPret = (await dossier.queryPermission({ mode: "readwrite" })) === "granted";
    } catch { dossier = null; dossierPret = false; }
    await verifierCle();
    return etatDossier();
  }
  async function choisirDossier() {
    if (!window.showDirectoryPicker) throw new Error("Cette fenêtre ne permet pas de choisir un dossier.");
    dossier = await window.showDirectoryPicker({ id: "travail", mode: "readwrite", startIn: "documents" });
    dossierPret = true;
    await bdEcrire("travail", dossier);
    await verifierCle();
    return etatDossier();
  }
  async function reconnecterDossier() {
    if (!dossier) return etatDossier();
    dossierPret = (await dossier.requestPermission({ mode: "readwrite" })) === "granted";
    await verifierCle();
    return etatDossier();
  }
  const etatDossier = () => ({ nom: dossier ? dossier.name : null, pret: dossierPret, estCle: dossierEstCle });

  const nomPropre = (n, ext) => {
    let base = String(n || "document").replace(/\.[a-z0-9]{2,4}$/i, "").replace(/[\\/:*?"<>|]+/g, "-").replace(/\s+/g, " ").trim().slice(0, 80) || "document";
    return `${base}.${ext}`;
  };

  async function sousDossier(chemin, creer) {
    let d = dossier;
    for (const part of String(chemin || "").split(/[\\/]+/).map((p) => p.replace(/[:*?"<>|]+/g, "").trim()).filter((p) => p && p !== "." && p !== "..")) {
      d = await d.getDirectoryHandle(part, { create: creer });
    }
    return d;
  }

  async function existe(d, nom) {
    try { await d.getFileHandle(nom); return true; } catch { return false; }
  }

  function telecharger(blob, nom) {
    const url = URL.createObjectURL(blob);
    const a = document.createElement("a");
    a.href = url; a.download = nom; document.body.appendChild(a); a.click(); a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 60000);
  }

  // Enregistre sans jamais écraser : « Lettre.docx » existe -> « Lettre (2).docx ».
  async function enregistrer(blob, nom, chemin) {
    if (dossier && dossierPret) {
      try {
        const d = await sousDossier(chemin, true);
        let final = nom;
        // « Lettre.docx » -> « Lettre (2).docx » ; « README » -> « README (2) ».
        const [, base, ext = ""] = nom.match(/^(.*?)(\.[^.\s]+)?$/);
        for (let n = 2; await existe(d, final); n++) final = `${base} (${n})${ext}`;
        const f = await d.getFileHandle(final, { create: true });
        const w = await f.createWritable();
        await w.write(blob);
        await w.close();
        const ou = [dossier.name, ...String(chemin || "").split(/[\\/]+/).filter(Boolean)].join(" › ");
        return { nom: final, ou, mode: "dossier", blob };
      } catch (e) {
        console.warn("Enregistrement dans le dossier impossible :", e);
      }
    }
    // Le lanceur règle la fenêtre pour que les téléchargements aillent dans
    // « Mes documents » sur la clé.
    // Sans dossier branché, on ne peut pas vérifier si le nom existe déjà
    // (Edge ajouterait « (1) » en cachette). On ajoute donc la date et l'heure
    // au nom : il est unique, et on sait exactement comment le fichier s'appelle.
    const d = new Date(), z2 = (x) => String(x).padStart(2, "0");
    const horodate = `${d.getFullYear()}-${z2(d.getMonth() + 1)}-${z2(d.getDate())} ${z2(d.getHours())}h${z2(d.getMinutes())}m${z2(d.getSeconds())}`;
    const [, base, ext = ""] = nom.match(/^(.*?)(\.[^.\s]+)?$/);
    const unique = `${base} ${horodate}${ext}`;
    telecharger(blob, unique);
    return { nom: unique, ou: "Mes documents", mode: "telechargement", blob };
  }

  async function creerDossier(nom) {
    if (!dossier || !dossierPret) throw new Error("Aucun dossier de travail n'est branché. Choisis-en un dans Réglages.");
    await sousDossier(nom, true);
    return `Dossier « ${nom} » prêt dans ${dossier.name}.`;
  }

  async function listerDocuments(chemin) {
    if (!dossier || !dossierPret) throw new Error("Aucun dossier de travail n'est branché. Choisis-en un dans Réglages.");
    const d = await sousDossier(chemin, false);
    const noms = [];
    for await (const [nom, h] of d.entries()) noms.push(h.kind === "directory" ? `📁 ${nom}` : nom);
    noms.sort((a, b) => a.localeCompare(b, "fr"));
    return noms.length ? noms.slice(0, 200).join("\n") : "(dossier vide)";
  }

  // Pour l'appli « Mes documents » : contenu d'un dossier, lecture, copie.
  function verifierDossier() {
    if (!dossier || !dossierPret) throw new Error("Le dossier « Mes documents » n'est pas branché.");
  }
  async function entrees(chemin) {
    verifierDossier();
    const d = await sousDossier(chemin, false);
    const out = [];
    for await (const [nom, h] of d.entries()) {
      if (nom.startsWith(".")) continue;   // fichiers techniques cachés
      if (h.kind === "directory") out.push({ nom, dossier: true });
      else {
        try { const f = await h.getFile(); out.push({ nom, dossier: false, taille: f.size, date: f.lastModified }); }
        catch { out.push({ nom, dossier: false }); }
      }
    }
    return out.sort((a, b) => (b.dossier - a.dossier) || a.nom.localeCompare(b.nom, "fr"));
  }
  async function fichierDuDossier(chemin, nom) {
    verifierDossier();
    const d = await sousDossier(chemin, false);
    return (await d.getFileHandle(nom)).getFile();
  }
  // Copie des fichiers de l'ordinateur vers le dossier (sans rien écraser).
  async function copierDans(chemin, fichiers) {
    verifierDossier();
    const noms = [];
    for (const f of fichiers) {
      const r = await enregistrer(f, f.name, chemin);
      noms.push(r.nom);
    }
    return noms;
  }

  // Corbeille : « supprimer » déplace le fichier dans Mes documents › Corbeille.
  // Il n'est effacé pour de bon que quand on vide la corbeille.
  const CORBEILLE = "Corbeille";
  async function deplacer(src, nom, dst) {
    const f = await fichierDuDossier(src, nom);
    const r = await enregistrer(f, nom, dst);
    if (r.mode !== "dossier") throw new Error("Déplacement impossible.");
    await (await sousDossier(src, false)).removeEntry(nom);
    return r.nom;
  }
  const mettreALaCorbeille = (chemin, nom) => deplacer(chemin, nom, CORBEILLE);
  const restaurer = (nom) => deplacer(CORBEILLE, nom, "");
  async function supprimerDossierVide(chemin, nom) {
    verifierDossier();
    try { await (await sousDossier(chemin, false)).removeEntry(nom); }
    catch { throw new Error("Ce dossier n'est pas vide : déplace ou supprime d'abord ce qu'il contient."); }
  }
  async function viderCorbeille() {
    verifierDossier();
    const d = await sousDossier(CORBEILLE, true);
    const noms = [];
    for await (const [nom] of d.entries()) noms.push(nom);
    for (const nom of noms) await d.removeEntry(nom, { recursive: true });
    return noms.length;
  }

  // ------------------------------------------------------------- Word
  function docxDepuisMarkdown(titre, texte) {
    const D = window.docx;
    const runs = (t, extra = {}) => Markdown.morceaux(t).flatMap((m) => {
      const parts = m.text.split("\n");
      return parts.map((p, k) => new D.TextRun({ text: p, bold: m.bold || extra.bold, italics: m.italic, font: m.code ? "Consolas" : undefined, break: k ? 1 : undefined }));
    });
    const enfants = [];
    if (titre) enfants.push(new D.Paragraph({ heading: D.HeadingLevel.TITLE, children: [new D.TextRun(titre)] }));
    let numero = 0;
    for (const b of Markdown.blocs(texte)) {
      if (b.type === "h") enfants.push(new D.Paragraph({ heading: [D.HeadingLevel.HEADING_1, D.HeadingLevel.HEADING_2, D.HeadingLevel.HEADING_3, D.HeadingLevel.HEADING_4][b.level - 1], children: runs(b.text) }));
      else if (b.type === "p") enfants.push(new D.Paragraph({ children: runs(b.text), spacing: { after: 160 } }));
      else if (b.type === "ul") b.items.forEach((x) => enfants.push(new D.Paragraph({ numbering: { reference: "puces", level: 0 }, children: runs(x) })));
      else if (b.type === "ol") { numero++; b.items.forEach((x) => enfants.push(new D.Paragraph({ numbering: { reference: "numeros", level: 0, instance: numero }, children: runs(x) }))); }
      else if (b.type === "code") b.text.split("\n").forEach((x) => enfants.push(new D.Paragraph({ children: [new D.TextRun({ text: x, font: "Consolas" })] })));
      else if (b.type === "hr") enfants.push(new D.Paragraph({ border: { bottom: { style: D.BorderStyle.SINGLE, size: 6, color: "999999", space: 1 } }, children: [] }));
      else if (b.type === "table") {
        const nbCol = Math.max(...b.rows.map((r) => r.length));
        enfants.push(new D.Table({
          width: { size: 100, type: D.WidthType.PERCENTAGE },
          rows: b.rows.map((r, ri) => new D.TableRow({
            tableHeader: ri === 0,
            children: Array.from({ length: nbCol }, (_, ci) => new D.TableCell({
              shading: ri === 0 ? { type: D.ShadingType.CLEAR, color: "auto", fill: "E8EEF7" } : undefined,
              children: [new D.Paragraph({ alignment: ri > 0 && lireNombre(r[ci]) !== null ? D.AlignmentType.RIGHT : D.AlignmentType.LEFT, children: runs(r[ci] || "", { bold: ri === 0 }) })],
            })),
          })),
        }));
        enfants.push(new D.Paragraph({ children: [] }));
      }
    }
    const niveau = (format, text) => ({ level: 0, format, text, alignment: D.AlignmentType.LEFT, style: { paragraph: { indent: { left: 720, hanging: 360 } } } });
    const doc = new D.Document({
      creator: "Mon assistante",
      title: titre || "Document",
      styles: { default: { document: { run: { font: "Calibri", size: 24 } } } },
      numbering: { config: [
        { reference: "puces", levels: [niveau(D.LevelFormat.BULLET, "•")] },
        { reference: "numeros", levels: [niveau(D.LevelFormat.DECIMAL, "%1.")] },
      ] },
      sections: [{ properties: { page: { margin: { top: 1134, bottom: 1134, left: 1134, right: 1134 } } }, children: enfants }],
    });
    return D.Packer.toBlob(doc);
  }

  // ------------------------------------------------------------ Excel
  function xlsxDepuisTableau(titre, colonnes, lignes, aTotaliser) {
    const X = window.XLSX;
    const entete = (colonnes || []).map(String);
    const nbCol = Math.max(entete.length, ...(lignes || []).map((l) => (l || []).length), 1);
    const argentCol = new Array(nbCol).fill(false);
    const formatsPct = {};
    let lignesVues = 0;
    // On ajoute nous-mêmes la ligne « Total » : une ligne Total / Sous-total
    // déjà présente serait comptée deux fois. (Les lignes TPS/TVQ restent : la
    // somme des articles + taxes donne bien le total.)
    const estResume = (l) => /^\s*(sous[- ]?total|grand[- ]?total|total)\b/i.test(String((l || []).find((x) => String(x ?? "").trim() !== "") ?? ""));
    const lignesUtiles = (aTotaliser || []).length ? (lignes || []).filter((l) => !estResume(l)) : (lignes || []);
    const corps = lignesUtiles.map((l) => (lignesVues++, Array.from({ length: nbCol }, (_, c) => {
      const brut = (l || [])[c];
      const n = lireNombre(brut);
      if (n !== null && /\$/.test(String(brut))) argentCol[c] = true;
      // « 15 % » devient 0,15 : on garde l'affichage en pourcentage dans Excel.
      const pct = String(brut ?? "").trim().match(/^-?\d+(?:[.,](\d+))?\s*%$/);
      if (n !== null && pct) formatsPct[`${c}:${lignesVues}`] = "0" + (pct[1] ? "." + "0".repeat(Math.min(4, pct[1].length)) : "") + "%";
      // « 00123 » est un code, pas un nombre : on garde le texte (sinon Excel
      // enlèverait les zéros du début).
      const code = /^\s*-?0\d/.test(String(brut ?? ""));
      return n !== null && !code ? n : (brut ?? "");
    })));
    const aoa = [entete, ...corps];
    const ws = X.utils.aoa_to_sheet(aoa);
    const totaux = new Set((aTotaliser || []).map((t) => typeof t === "number" ? t : entete.findIndex((e) => e.toLowerCase() === String(t).toLowerCase())).filter((c) => c >= 0 && c < nbCol));
    if (totaux.size && corps.length) {
      const r = corps.length + 1;
      ws[X.utils.encode_cell({ r, c: 0 })] = { t: "s", v: totaux.has(0) ? "" : "Total" };
      for (const c of totaux) {
        const col = X.utils.encode_col(c);
        const v = corps.reduce((s, l) => s + (typeof l[c] === "number" ? l[c] : 0), 0);
        ws[X.utils.encode_cell({ r, c })] = { t: "n", v: Math.round(v * 100) / 100, f: `SUM(${col}2:${col}${r})` };
      }
      ws["!ref"] = X.utils.encode_range({ s: { r: 0, c: 0 }, e: { r, c: nbCol - 1 } });
    }
    const plage = X.utils.decode_range(ws["!ref"]);
    for (let r = 1; r <= plage.e.r; r++) for (let c = 0; c < nbCol; c++) {
      const cell = ws[X.utils.encode_cell({ r, c })];
      if (cell && cell.t === "n" && argentCol[c]) cell.z = '# ##0.00 "$"';
      if (cell && cell.t === "n" && formatsPct[`${c}:${r}`]) cell.z = formatsPct[`${c}:${r}`];
    }
    ws["!cols"] = Array.from({ length: nbCol }, (_, c) => ({ wch: Math.min(50, Math.max(10, ...aoa.map((l) => String(l[c] ?? "").length + 2))) }));
    const wb = X.utils.book_new();
    X.utils.book_append_sheet(wb, ws, String(titre || "Tableau").replace(/[\[\]:*?\/\\]/g, " ").slice(0, 31) || "Tableau");
    const data = X.write(wb, { bookType: "xlsx", type: "array" });
    return new Blob([data], { type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" });
  }

  // ---------------------------------------------------------- schémas
  let mermaidPret = false;
  async function dessinerSchema(code) {
    if (!window.mermaid) throw new Error("Le moteur de schémas est absent.");
    if (!mermaidPret) {
      mermaid.initialize({ startOnLoad: false, securityLevel: "strict", theme: "default", htmlLabels: false, flowchart: { htmlLabels: false }, fontFamily: "Segoe UI, sans-serif" });
      mermaidPret = true;
    }
    let src = String(code || "").trim().replace(/^```[a-z]*\s*\n?/i, "").replace(/\n?```$/, "").trim();
    try {
      await mermaid.parse(src);
    } catch (e) {
      throw new Error("Le schéma contient une erreur : " + String(e.message || e).split("\n")[0]);
    }
    const { svg } = await mermaid.render("schema" + Date.now(), src);
    return { svg, code: src };
  }

  function svgEnPng(svg) {
    return new Promise((ok, ko) => {
      const img = new Image();
      img.onload = () => {
        const echelle = 2;
        const w = img.naturalWidth || 800, h = img.naturalHeight || 600;
        const c = document.createElement("canvas");
        c.width = w * echelle; c.height = h * echelle;
        const g = c.getContext("2d");
        g.fillStyle = "#ffffff"; g.fillRect(0, 0, c.width, c.height);
        g.drawImage(img, 0, 0, c.width, c.height);
        c.toBlob((b) => (b ? ok(b) : ko(new Error("Image impossible."))), "image/png");
      };
      img.onerror = () => ko(new Error("Image impossible."));
      img.src = "data:image/svg+xml;charset=utf-8," + encodeURIComponent(svg);
    });
  }

  // ------------------------------------------------------------ vidéo
  // Diaporama animé (titres + photos) enregistré en MP4 directement par la
  // fenêtre — marche sur un ordinateur ordinaire, sans carte graphique.
  function formatVideo() {
    const choix = ["video/mp4;codecs=avc1.42E01E", "video/mp4;codecs=avc1", "video/mp4", "video/webm;codecs=vp9", "video/webm"];
    const t = choix.find((c) => window.MediaRecorder && MediaRecorder.isTypeSupported(c));
    if (!t) throw new Error("Cette fenêtre ne peut pas enregistrer de vidéo.");
    return { type: t, ext: t.startsWith("video/mp4") ? "mp4" : "webm" };
  }

  const chargerImage = (src) => new Promise((ok) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => ok(null); i.src = src; });

  function lignesTexte(g, texte, largeur) {
    const mots = String(texte || "").split(/\s+/);
    const out = [];
    let l = "";
    for (const m of mots) {
      const essai = l ? l + " " + m : m;
      if (g.measureText(essai).width > largeur && l) { out.push(l); l = m; } else l = essai;
    }
    if (l) out.push(l);
    return out;
  }

  async function creerVideo(diapos, photos, progres, signal) {
    const { type, ext } = formatVideo();
    const W = 1280, H = 720, FPS = 30;
    const c = document.createElement("canvas");
    c.width = W; c.height = H;
    const g = c.getContext("2d");
    const couleurs = ["#1f4e79", "#7a3e00", "#1e5d3a", "#5b2a6e", "#8a1c1c", "#22424f"];
    const prep = [];
    for (const [k, d] of (diapos || []).slice(0, 20).entries()) {
      const n = Number(d.photo);
      const src = n >= 1 && photos[n - 1] ? photos[n - 1] : null;
      prep.push({ titre: d.titre || "", texte: d.texte || "", duree: Math.min(10, Math.max(2, Number(d.duree) || 4)), image: src ? await chargerImage(src) : null, fond: couleurs[k % couleurs.length] });
    }
    if (!prep.length) throw new Error("Il faut au moins une diapositive.");

    const dessiner = (d, t) => {
      g.fillStyle = d.fond; g.fillRect(0, 0, W, H);
      if (d.image) {
        const z = 1 + 0.06 * t; // léger zoom (effet « Ken Burns »)
        const r = Math.max(W / d.image.width, H / d.image.height) * z;
        const iw = d.image.width * r, ih = d.image.height * r;
        g.drawImage(d.image, (W - iw) / 2, (H - ih) / 2, iw, ih);
        const grad = g.createLinearGradient(0, H * 0.45, 0, H);
        grad.addColorStop(0, "rgba(0,0,0,0)"); grad.addColorStop(1, "rgba(0,0,0,0.75)");
        g.fillStyle = grad; g.fillRect(0, 0, W, H);
      }
      g.fillStyle = "#ffffff"; g.textAlign = "center"; g.shadowColor = "rgba(0,0,0,.5)"; g.shadowBlur = 8;
      g.font = "36px Segoe UI, sans-serif";
      const lx = lignesTexte(g, d.texte, W - 200).slice(0, 3);
      // Avec une photo, le texte est collé en bas : on le remonte selon son
      // nombre de lignes pour que la dernière reste dans l'image.
      const dessous = lx.length ? 64 + (lx.length - 1) * 46 : 0;
      const yBase = d.image ? Math.min(H - 150, H - 40 - dessous) : H / 2 - 40;
      g.font = "600 64px Segoe UI, sans-serif";
      const lt = lignesTexte(g, d.titre, W - 160);
      lt.forEach((l, i) => g.fillText(l, W / 2, yBase - (lt.length - 1 - i) * 72));
      g.font = "36px Segoe UI, sans-serif";
      lx.forEach((l, i) => g.fillText(l, W / 2, yBase + 64 + i * 46));
      g.shadowBlur = 0;
    };

    const flux = c.captureStream(FPS);
    const rec = new MediaRecorder(flux, { mimeType: type, videoBitsPerSecond: 5_000_000 });
    const morceaux = [];
    rec.ondataavailable = (e) => e.data.size && morceaux.push(e.data);
    const fini = new Promise((ok) => (rec.onstop = ok));
    const total = prep.reduce((s, d) => s + d.duree, 0);
    rec.start(250);
    const debut = performance.now();
    await new Promise((ok) => {
      const tick = () => {
        const sec = (performance.now() - debut) / 1000;
        if (signal && signal.aborted) return ok();   // « Arrêter » : on coupe tout de suite
        if (sec >= total) return ok();
        let acc = 0, k = 0;
        while (k < prep.length - 1 && sec >= acc + prep[k].duree) acc += prep[k++].duree;
        const t = (sec - acc) / prep[k].duree;
        dessiner(prep[k], t);
        const fondu = 0.5; // fondu de 0,5 s vers la diapo suivante
        const reste = acc + prep[k].duree - sec;
        if (k < prep.length - 1 && reste < fondu) { g.globalAlpha = 1 - reste / fondu; dessiner(prep[k + 1], 0); g.globalAlpha = 1; }
        progres && progres(Math.min(1, sec / total));
        requestAnimationFrame(tick);
      };
      tick();
    });
    rec.stop();
    await fini;
    flux.getTracks().forEach((t) => t.stop());
    if (signal && signal.aborted) throw new DOMException("Vidéo arrêtée.", "AbortError");
    return { blob: new Blob(morceaux, { type: type.split(";")[0] }), ext, duree: total };
  }

  // ------------------------------------------------- affiche publicitaire
  // Une image prête à publier sur Facebook ou à imprimer : grosse photo du
  // produit, titre accrocheur, prix bien visible, points forts et coordonnées.
  const THEMES = {
    rouge: { fond: "#b3121b", accent: "#ffd400", texte: "#ffffff", bande: "#1a1a1a" },
    bleu: { fond: "#0b3d91", accent: "#ffc72c", texte: "#ffffff", bande: "#081f4d" },
    vert: { fond: "#1e6b3a", accent: "#f2e205", texte: "#ffffff", bande: "#0f3a1f" },
    noir: { fond: "#151515", accent: "#ff6a00", texte: "#ffffff", bande: "#000000" },
  };

  function coins(g, x, y, w, h, r) {
    g.beginPath(); g.moveTo(x + r, y); g.arcTo(x + w, y, x + w, y + h, r); g.arcTo(x + w, y + h, x, y + h, r);
    g.arcTo(x, y + h, x, y, r); g.arcTo(x, y, x + w, y, r); g.closePath();
  }

  async function creerAffiche(a, photos, coordonnees) {
    const imprimer = a.format === "imprimer";
    const W = imprimer ? 1700 : 1080, H = imprimer ? 2200 : 1350;
    const k = W / 1080;
    const th = THEMES[a.couleur] || THEMES.rouge;
    const c = document.createElement("canvas");
    c.width = W; c.height = H;
    const g = c.getContext("2d");
    g.fillStyle = th.fond; g.fillRect(0, 0, W, H);

    // Bandeau du haut (accroche courte : PROMO, NOUVEAU, SOLDE…)
    if (a.accroche) {
      g.fillStyle = th.accent; g.fillRect(0, 0, W, 110 * k);
      g.fillStyle = th.bande; g.font = `800 ${62 * k}px Segoe UI, sans-serif`; g.textAlign = "center"; g.textBaseline = "middle";
      g.fillText(String(a.accroche).toUpperCase().slice(0, 28), W / 2, 58 * k);
    }
    let y = (a.accroche ? 140 : 60) * k;

    // Le contenu du milieu est dessiné à part, puis centré entre le bandeau
    // du haut et la bande du bas (pas de grand trou quand il n'y a pas de photo).
    const haut = (a.accroche ? 110 : 0) * k, bas = 230 * k;
    const cm = document.createElement("canvas");
    cm.width = W; cm.height = H * 2;  // assez haut pour tout dessiner avant de réduire
    const gm = cm.getContext("2d");
    const y0 = y;

    // Photo du produit
    const n = Number(a.photo);
    const img = n >= 1 && photos[n - 1] ? await chargerImage(photos[n - 1]) : null;
    if (img) {
      const pw = W - 120 * k, ph = 560 * k;
      gm.save(); coins(gm, 60 * k, y, pw, ph, 28 * k); gm.clip();
      gm.fillStyle = "#ffffff"; gm.fillRect(60 * k, y, pw, ph);
      const r = Math.min(pw / img.width, ph / img.height);
      gm.drawImage(img, 60 * k + (pw - img.width * r) / 2, y + (ph - img.height * r) / 2, img.width * r, img.height * r);
      gm.restore();
      y += ph + 50 * k;
    } else y += 40 * k;

    // Titre
    gm.textAlign = "center"; gm.textBaseline = "alphabetic"; gm.fillStyle = th.texte;
    gm.font = `800 ${88 * k}px Segoe UI, sans-serif`;
    for (const l of lignesTexte(gm, a.titre || "", W - 140 * k).slice(0, 3)) { y += 92 * k; gm.fillText(l, W / 2, y); }
    if (a.sous_titre) {
      gm.font = `400 ${44 * k}px Segoe UI, sans-serif`;
      for (const l of lignesTexte(gm, a.sous_titre, W - 160 * k).slice(0, 2)) { y += 58 * k; gm.fillText(l, W / 2, y); }
    }

    // Prix dans une pastille
    if (a.prix) {
      y += 40 * k;
      gm.font = `900 ${96 * k}px Segoe UI, sans-serif`;
      const pt = String(a.prix);
      const pw = gm.measureText(pt).width + 110 * k, ph = 150 * k;
      gm.fillStyle = th.accent; coins(gm, (W - pw) / 2, y, pw, ph, 75 * k); gm.fill();
      gm.fillStyle = th.bande; gm.textBaseline = "middle"; gm.fillText(pt, W / 2, y + ph / 2 + 4 * k);
      gm.textBaseline = "alphabetic";
      y += ph;
    }

    // Points forts
    const points = (a.points || []).slice(0, 4);
    if (points.length) {
      y += 30 * k;
      gm.font = `600 ${42 * k}px Segoe UI, sans-serif`; gm.fillStyle = th.texte;
      for (const p of points) { y += 60 * k; gm.fillText("✔ " + String(p).slice(0, 48), W / 2, y); }
    }

    // Trop de contenu (grande photo + long titre) : on réduit le tout pour qu'il tienne.
    const libre = H - bas - haut - 40 * k, hauteur = y - y0 + 20 * k;
    const s = Math.min(1, libre / hauteur);
    const dh = hauteur * s, dw = W * s;
    g.drawImage(cm, 0, y0 - 10 * k, W, hauteur, (W - dw) / 2, haut + 20 * k + (libre - dh) / 2, dw, dh);

    // Bas : appel à l'action + coordonnées
    g.textAlign = "center"; g.textBaseline = "alphabetic";  // même sans bandeau du haut
    g.fillStyle = th.bande; g.fillRect(0, H - bas, W, bas);
    g.fillStyle = th.accent; g.font = `800 ${54 * k}px Segoe UI, sans-serif`;
    g.fillText(String(a.appel || "Venez nous voir !").slice(0, 40), W / 2, H - bas + 85 * k);
    g.fillStyle = "#ffffff"; g.font = `400 ${36 * k}px Segoe UI, sans-serif`;
    const coord = String(coordonnees || "").split("\n").filter(Boolean).slice(0, 2);
    coord.forEach((l, i) => g.fillText(l.slice(0, 60), W / 2, H - bas + (145 + i * 46) * k));

    return new Promise((ok, ko) => c.toBlob((b) => (b ? ok(b) : ko(new Error("Affiche impossible."))), "image/png"));
  }

  // -------------------------------------- outils proposés au modèle (IA)
  const SCHEMAS = [
    { type: "function", function: { name: "calculer", description: "Fait un calcul exact. Utilise-le pour TOUT calcul (montants, quantités, pourcentages). Ex : '12,50*4'. Pour ajouter 15 %, écris '*1,15' ; pour 15 % d'un montant, '200*15%'.", parameters: { type: "object", properties: { expression: { type: "string" } }, required: ["expression"] } } },
    { type: "function", function: { name: "taxes_quebec", description: "Calcule la TPS (5 %) et la TVQ (9,975 %) d'un montant, avant taxes ou taxes incluses.", parameters: { type: "object", properties: { montant: { type: "number" }, taxes_incluses: { type: "boolean", description: "true si le montant contient déjà les taxes" } }, required: ["montant"] } } },
    { type: "function", function: { name: "creer_document_word", description: "Crée un vrai fichier Word (.docx) : lettre, avis, procédure, compte rendu. Le contenu est en Markdown (titres #, listes -, tableaux |).", parameters: { type: "object", properties: { nom_fichier: { type: "string" }, titre: { type: "string" }, contenu: { type: "string" }, dossier: { type: "string", description: "Sous-dossier où le ranger (facultatif)" } }, required: ["nom_fichier", "contenu"] } } },
    { type: "function", function: { name: "creer_tableau_excel", description: "Crée un vrai fichier Excel (.xlsx) : liste, inventaire, facture, horaire. Les totaux sont calculés par Excel.", parameters: { type: "object", properties: { nom_fichier: { type: "string" }, titre: { type: "string" }, colonnes: { type: "array", items: { type: "string" } }, lignes: { type: "array", items: { type: "array", items: { type: "string" } } }, colonnes_a_totaliser: { type: "array", items: { type: "string" }, description: "Noms des colonnes à additionner" }, dossier: { type: "string" } }, required: ["nom_fichier", "colonnes", "lignes"] } } },
    { type: "function", function: { name: "dessiner_schema", description: "Dessine un schéma ou un plan (étapes, organigramme, planning, carte d'idées) à partir de code Mermaid, ex : 'flowchart TD\\n A[Appel client] --> B[Soumission]'. Pour un planning : 'gantt'. Pour des idées : 'mindmap'.", parameters: { type: "object", properties: { code: { type: "string" }, nom_fichier: { type: "string" }, dossier: { type: "string" } }, required: ["code"] } } },
    { type: "function", function: { name: "creer_affiche", description: "Crée une affiche publicitaire (image PNG) pour donner envie aux clients d'acheter : photo du produit, titre accrocheur, prix, points forts. Pour Facebook ou à imprimer.", parameters: { type: "object", properties: { nom_fichier: { type: "string" }, accroche: { type: "string", description: "Mot court en bandeau : PROMO, NOUVEAU, SOLDE…" }, titre: { type: "string" }, sous_titre: { type: "string" }, prix: { type: "string", description: "Ex : 149,99 $ — seulement si on te l'a donné" }, points: { type: "array", items: { type: "string" }, description: "2 à 4 points forts courts" }, appel: { type: "string", description: "Ex : Venez nous voir !" }, photo: { type: "integer", description: "Numéro d'une photo envoyée dans la conversation" }, couleur: { type: "string", enum: ["rouge", "bleu", "vert", "noir"] }, format: { type: "string", enum: ["facebook", "imprimer"] }, dossier: { type: "string" } }, required: ["titre"] } } },
    { type: "function", function: { name: "creer_video", description: "Crée une courte vidéo MP4 en diaporama (titres, textes et photos de la conversation), par exemple une promotion pour le magasin ou un souvenir.", parameters: { type: "object", properties: { nom_fichier: { type: "string" }, diapos: { type: "array", items: { type: "object", properties: { titre: { type: "string" }, texte: { type: "string" }, photo: { type: "integer", description: "Numéro d'une photo envoyée dans la conversation (1, 2…), facultatif" }, duree: { type: "number", description: "Secondes (2 à 10)" } } } }, dossier: { type: "string" } }, required: ["diapos"] } } },
    { type: "function", function: { name: "creer_dossier", description: "Crée un dossier (ou sous-dossier) dans le dossier de travail pour classer les documents.", parameters: { type: "object", properties: { nom: { type: "string" } }, required: ["nom"] } } },
    { type: "function", function: { name: "retenir_lecon", description: "Retient une leçon dans ton mini-cerveau pour ne plus refaire la même erreur. À utiliser quand elle te corrige ou te dit que tu t'es trompée. Écris une règle courte, générale et utile pour la prochaine fois (ex. « On écrit Boutique Le Petit Bleuet avec des majuscules »), sans renseignement personnel délicat.", parameters: { type: "object", properties: { lecon: { type: "string" } }, required: ["lecon"] } } },
    { type: "function", function: { name: "ajouter_rendez_vous", description: "Ajoute un rendez-vous ou un rappel dans son agenda. Calcule la date exacte à partir de la date du jour (ex. « jeudi prochain »).", parameters: { type: "object", properties: { date: { type: "string", description: "AAAA-MM-JJ" }, heure: { type: "string", description: "HH:MM sur 24 h, facultatif" }, titre: { type: "string" }, note: { type: "string" }, rappel_minutes: { type: "integer", description: "Minutes avant pour le rappel (0, 15, 30, 60). 15 par défaut." } }, required: ["date", "titre"] } } },
    { type: "function", function: { name: "voir_agenda", description: "Lit les rendez-vous de son agenda entre deux dates (incluses).", parameters: { type: "object", properties: { du: { type: "string", description: "AAAA-MM-JJ" }, au: { type: "string", description: "AAAA-MM-JJ" } }, required: ["du"] } } },
    { type: "function", function: { name: "lister_documents", description: "Liste les fichiers et dossiers du dossier de travail.", parameters: { type: "object", properties: { dossier: { type: "string" } } } } },
  ];

  // Exécute un outil. Retourne { texte (pour l'IA), carte (affichée à l'écran) }.
  async function executer(nom, a, ctx) {
    a = a || {};
    switch (nom) {
      case "calculer": {
        const r = calculer(a.expression);
        return { texte: `${a.expression} = ${r}`, carte: { genre: "calcul", titre: "Calcul", detail: `${a.expression} = ${nombreFr.format(r)}` } };
      }
      case "taxes_quebec": {
        const t = taxes(a.montant, !!a.taxes_incluses);
        const d = `Avant taxes : ${argent.format(t.avant)}\nTPS (5 %) : ${argent.format(t.tps)}\nTVQ (9,975 %) : ${argent.format(t.tvq)}\nTotal : ${argent.format(t.total)}`;
        return { texte: d, carte: { genre: "calcul", titre: "Taxes", detail: d } };
      }
      case "creer_document_word": {
        const blob = await docxDepuisMarkdown(a.titre, a.contenu);
        const f = await enregistrer(blob, nomPropre(a.nom_fichier, "docx"), a.dossier);
        return { texte: `Document Word créé : ${f.nom} (dans ${f.ou}).`, carte: { genre: "fichier", icone: "📄", ...f } };
      }
      case "creer_tableau_excel": {
        const blob = xlsxDepuisTableau(a.titre, a.colonnes, a.lignes, a.colonnes_a_totaliser);
        const f = await enregistrer(blob, nomPropre(a.nom_fichier, "xlsx"), a.dossier);
        return { texte: `Tableau Excel créé : ${f.nom} (dans ${f.ou}).`, carte: { genre: "fichier", icone: "📊", ...f } };
      }
      case "dessiner_schema": {
        const { svg } = await dessinerSchema(a.code);
        const png = await svgEnPng(svg);
        const f = await enregistrer(png, nomPropre(a.nom_fichier || "schéma", "png"), a.dossier);
        return { texte: `Schéma dessiné et enregistré : ${f.nom} (dans ${f.ou}).`, carte: { genre: "schema", svg, icone: "🗺️", ...f } };
      }
      case "creer_affiche": {
        const png = await creerAffiche(a, ctx.photos || [], ctx.coordonnees);
        const f = await enregistrer(png, nomPropre(a.nom_fichier || "affiche", "png"), a.dossier);
        return { texte: `Affiche créée : ${f.nom} (dans ${f.ou}).`, carte: { genre: "image", url: URL.createObjectURL(png), icone: "🖼️", ...f } };
      }
      case "creer_video": {
        const v = await creerVideo(a.diapos, ctx.photos || [], ctx.progres, ctx.signal);
        const f = await enregistrer(v.blob, nomPropre(a.nom_fichier || "vidéo", v.ext), a.dossier);
        return { texte: `Vidéo de ${Math.round(v.duree)} secondes créée : ${f.nom} (dans ${f.ou}).`, carte: { genre: "video", icone: "🎬", ...f } };
      }
      case "creer_dossier": {
        const t = await creerDossier(a.nom);
        return { texte: t, carte: { genre: "calcul", titre: "Dossier", detail: t } };
      }
      case "retenir_lecon": {
        if (!window.Cerveau) throw new Error("Le mini-cerveau n'est pas disponible.");
        const r = window.Cerveau.ajouter(a.lecon, "assistante");
        return { texte: r === "deja" ? "Cette leçon était déjà dans ton mini-cerveau : elle a été rafraîchie." : "Leçon retenue dans ton mini-cerveau.", carte: { genre: "calcul", titre: "💡 Retenu dans le mini-cerveau", detail: String(a.lecon || "").trim() } };
      }
      case "ajouter_rendez_vous": {
        if (!window.Agenda) throw new Error("L'agenda n'est pas disponible.");
        const r = window.Agenda.ajouter({ date: a.date, heure: a.heure, titre: a.titre, note: a.note, rappel: a.rappel_minutes ?? 15 });
        const d = `${window.Agenda.dateLongue(r.date)}${r.heure ? " à " + r.heure.replace(":", " h ") : ""} — ${r.titre}`;
        return { texte: `Rendez-vous ajouté à l'agenda : ${d}.`, carte: { genre: "calcul", titre: "📅 Ajouté à l'agenda", detail: d } };
      }
      case "voir_agenda": {
        if (!window.Agenda) throw new Error("L'agenda n'est pas disponible.");
        return { texte: window.Agenda.texte(a.du, a.au), carte: null };
      }
      case "lister_documents": {
        const t = await listerDocuments(a.dossier);
        return { texte: t, carte: null };
      }
      default:
        throw new Error(`Outil inconnu : ${nom}`);
    }
  }

  window.Outils = {
    SCHEMAS, executer, calculer, taxes, argent, lireNombre,
    docxDepuisMarkdown, xlsxDepuisTableau, dessinerSchema, svgEnPng, creerVideo, creerAffiche,
    enregistrer, telecharger, nomPropre,
    chargerDossier, choisirDossier, reconnecterDossier, etatDossier,
    entrees, fichierDuDossier, copierDans, creerDossier,
    CORBEILLE, mettreALaCorbeille, restaurer, viderCorbeille, supprimerDossierVide,
  };
})();
