// Mon assistante — la fenêtre de discussion et de travail.
// Le moteur IA (llama.cpp) tourne sur cet ordinateur, à la même adresse que
// cette page : aucune donnée ne part sur internet.
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const M = window.Markdown;
  const CFG = window.CONFIG_ASSISTANTE || {};

  // ------------------------------------------------------- petite mémoire
  const stock = {
    lire(cle, defaut) { try { const v = localStorage.getItem(cle); return v ? JSON.parse(v) : defaut; } catch { return defaut; } },
    ecrire(cle, val) {
      try { localStorage.setItem(cle, JSON.stringify(val)); return true; }
      catch { return false; }
    },
  };

  // Réglages techniques choisis dans l'Espace technique (sinon ceux de config.js).
  const avance = () => Object.assign({ temperature: CFG.temperature ?? 0.7, maxTokens: CFG.maxTokens || 1500, messagesGardes: CFG.messagesGardes || 12, instructions: "" }, stock.lire("avance", {}));
  let reglages = Object.assign({ prenom: CFG.prenom || "", nomAssistante: CFG.nomAssistante || "Mon assistante", entreprise: CFG.entreprise || "", role: CFG.role || "", ville: CFG.ville || "", fete: CFG.fete || "", signature: CFG.signature || "", coordonnees: "", taille: 17, voixAuto: false, vitesseVoix: 1, theme: "auto", accent: "bleu", fond: "lac" }, stock.lire("reglages", {}));
  let conversations = stock.lire("conversations", []);
  let documents = stock.lire("documents", []);
  let courante = null;      // conversation affichée
  let joints = [];          // pièces jointes en attente { type:'image'|'texte', nom, data }
  let enCours = null;       // AbortController de la réponse en cours

  function sauver() {
    if (stock.ecrire("conversations", conversations)) return;
    // La mémoire de la fenêtre est pleine : on retire les photos des plus
    // vieilles conversations, mais SEULEMENT dans la copie enregistrée — la
    // conversation en cours garde ses photos (la réponse en a besoin).
    // Celle qui est affichée passe en dernier.
    const copie = JSON.parse(JSON.stringify(conversations));
    const ordre = copie.map((c, i) => ({ c, vraie: conversations[i] })).reverse()
      .sort((x, y) => (x.vraie === courante) - (y.vraie === courante));
    for (const { c } of ordre) {
      if (!c.messages.some((m) => m.images && m.images.length)) continue;
      c.messages.forEach((m) => { if (m.images && m.images.length) { m.texte = (m.texte || "") + `\n[${m.images.length} photo(s) retirée(s) pour faire de la place]`; m.images = []; } });
      if (stock.ecrire("conversations", copie)) return;
    }
  }
  const sauverDocs = () => stock.ecrire("documents", documents.slice(0, 200));
  const id = () => Date.now().toString(36) + Math.random().toString(36).slice(2, 7);

  // ---------------------------------------------------- consigne de l'IA
  function consigne() {
    const qui = reglages.prenom ? reglages.prenom : "la personne qui t'écrit";
    const lignes = [
      `Tu es ${reglages.nomAssistante || "l'assistante"}, l'assistante personnelle de ${qui}${reglages.role ? ", " + reglages.role : ""}${reglages.entreprise ? " chez " + reglages.entreprise : ""}${reglages.ville ? " à " + reglages.ville : ""}, au Québec. ${CFG.contexte || ""}`,
      "Tu l'aides au travail (courriels aux clients et fournisseurs, lettres, avis, rappels de paiement, factures, listes, inventaires, horaires, affiches et publicités, résumés de notes, correction de textes) et dans la vie de tous les jours (recettes, questions pratiques, explications).",
      "",
      "Ta personnalité :",
      "- Tu es une vraie Québécoise : chaleureuse, souriante, terre à terre. Tu tutoies la personne et tu parles comme au Québec, avec des expressions d'ici quand ça adonne (« Pas de trouble ! », « Avec plaisir ! », « On s'y met ! », « C'est beau, c'est réglé ! »), sans en mettre partout.",
      "- Tu es toujours polie et bien élevée : JAMAIS de gros mots, de sacres ni de vulgarité, même pour rire, même si on t'en demande. Québécoise, oui ; vulgaire, jamais.",
      "- Tu AIMES travailler avec cette personne et ça paraît : tu es contente quand elle t'écrit et fière de ce que vous faites ensemble.",
      "- Tu l'encourages et tu soulignes sincèrement ses qualités et ses bons coups (son sens de l'organisation, sa patience avec les clients, une belle idée, un travail bien fait) — une phrase courte et vraie, pas de flatterie exagérée.",
      "- Tu fais du travail de qualité : soigné, sans fautes, bien présenté. Si tu vois une erreur ou un oubli dans sa demande (un montant qui ne balance pas, une date manquante), tu le lui dis gentiment.",
      "- Les textes pour les clients et fournisseurs restent en bon français professionnel, même si tu jases de façon familière avec la personne.",
      "",
      "Façon de répondre :",
      "- Simple et clair, pas de jargon technique.",
      "- Va droit au but. Quand tu écris un texte à envoyer, donne directement le texte prêt à copier.",
      "- N'invente JAMAIS de prix, numéros de pièce, modèles compatibles, garanties, dates, adresses ou numéros de téléphone. S'il manque une info, mets un espace comme [prix] et dis-le.",
      "- Santé : donne de l'information générale et rappelle de consulter un pharmacien, un médecin ou Info-Santé 811 pour un cas personnel. Urgence : 911.",
      "- Tu fonctionnes sans internet : tu ne connais pas les nouvelles ni les prix du jour.",
      "",
      "Tes outils (utilise-les, ils sont exacts) :",
      "- calculer et taxes_quebec pour TOUS les calculs : ne calcule jamais de tête.",
      "- creer_document_word quand on veut un document, une lettre ou un fichier à imprimer.",
      "- creer_tableau_excel pour une liste, un inventaire, une facture ou un tableau avec des totaux.",
      "- creer_affiche pour une publicité ou une promotion avec une photo, pour Facebook ou à imprimer.",
      "- dessiner_schema pour un plan, des étapes, un organigramme ou un planning.",
      "- creer_video pour une petite vidéo diaporama avec les photos.",
      "- creer_dossier et lister_documents pour classer ses fichiers.",
      "- ajouter_rendez_vous et voir_agenda pour son agenda (rendez-vous, rappels, livraisons, paiements à faire).",
      "- retenir_lecon quand on te corrige ou qu'on te dit que tu t'es trompée : excuse-toi en une phrase, corrige-toi, et retiens une règle courte pour ne JAMAIS refaire cette erreur.",
      "Après avoir utilisé un outil, dis en une phrase ce qui a été fait et où trouver le fichier.",
      "Les photos envoyées sont numérotées (photo 1, photo 2…) dans l'ordre de la conversation.",
    ];
    const lecons = window.Cerveau ? window.Cerveau.pourConsigne() : [];
    if (lecons.length) lignes.push("", "Ton mini-cerveau — leçons apprises de tes erreurs passées. Respecte-les TOUJOURS, ne refais jamais ces erreurs :", ...lecons);
    const maintenant = new Date();
    const pad = (n) => String(n).padStart(2, "0");
    lignes.push("", `Aujourd'hui : ${maintenant.toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric" })} (${maintenant.getFullYear()}-${pad(maintenant.getMonth() + 1)}-${pad(maintenant.getDate())}), il est ${pad(maintenant.getHours())} h ${pad(maintenant.getMinutes())}.`);
    if (window.Fete && window.Fete.estFete()) lignes.push(`C'est la FÊTE de ${reglages.prenom || "la personne qui t'écrit"} aujourd'hui ! Souhaite-lui bonne fête chaleureusement dans ta première réponse de la conversation.`);
    if (reglages.signature) lignes.push("", "Signature à mettre au bas des courriels et lettres :", reglages.signature);
    if (reglages.coordonnees) lignes.push("", "Coordonnées de l'entreprise (pour les affiches et lettres) :", reglages.coordonnees);
    const plus = avance().instructions.trim();
    if (plus) lignes.push("", "Consignes particulières (à respecter) :", plus);
    return lignes.join("\n");
  }

  // ------------------------------------------------------------ raccourcis
  const RACCOURCIS = [
    ["✉️", "Courriel à un client", "Réponse polie, prête à envoyer", "Écris un courriel poli à un client pour lui dire que "],
    ["🧾", "Rappel de paiement", "Courtois mais clair", "Écris un rappel de paiement courtois pour la facture no [ ] de [montant], en retard depuis [date], pour le client "],
    ["✍️", "Corriger mon texte", "Fautes et tournures", "Corrige les fautes de ce texte sans changer le sens, et redonne-moi seulement le texte corrigé :\n\n"],
    ["📄", "Lettre en Word", "Un vrai fichier à imprimer", "Fais-moi un document Word : une lettre officielle de l'entreprise pour "],
    ["📊", "Tableau Excel", "Liste, inventaire, facture", "Fais-moi un tableau Excel avec les totaux pour "],
    ["🧮", "Calcul avec taxes", "TPS et TVQ exactes", "Calcule les taxes sur "],
    ["🖼️", "Affiche publicitaire", "Pour Facebook ou la vitrine", "Fais une affiche publicitaire pour donner envie aux clients d'acheter : "],
    ["🗺️", "Plan ou schéma", "Étapes, organigramme, planning", "Dessine-moi un schéma des étapes pour "],
    ["🎬", "Vidéo avec mes photos", "Petit diaporama en MP4", "Fais une petite vidéo avec mes photos pour "],
    ["📷", "Lire une photo", "Facture, étiquette, document", "@photo"],
    ["📝", "Résumer des notes", "En points clairs", "Fais un résumé clair, en points, de ces notes :\n\n"],
    ["🍲", "Idée de recette", "Avec ce que j'ai sous la main", "Propose-moi une recette simple pour ce soir avec "],
  ];

  function afficherRaccourcis() {
    $("raccourcis").innerHTML = "";
    for (const [emoji, titre, sous, modele] of RACCOURCIS) {
      const b = document.createElement("button");
      b.className = "raccourci";
      b.innerHTML = `<span class="emoji">${emoji}</span><div><strong>${M.echapper(titre)}</strong><span>${M.echapper(sous)}</span></div>`;
      b.onclick = () => {
        if (modele === "@photo") {
          $("message").value = "Qu'est-ce qu'on voit sur cette photo ? Si c'est un document, recopie-moi le texte.";
          $("fichier").click();
        } else {
          $("message").value = modele;
        }
        ajusterSaisie();
        $("message").focus();
        $("message").setSelectionRange($("message").value.length, $("message").value.length);
      };
      $("raccourcis").appendChild(b);
    }
  }

  // ------------------------------------------------------------ affichage
  // Thème : clair, sombre, comme Windows, ou automatique selon l'heure
  // (sombre de 19 h à 7 h). Couleur d'accent et fond d'écran au choix.
  const ACCENTS = { bleu: "#0067c0", vert: "#0f7b0f", violet: "#6b3fa0", rouge: "#c42b1c", orange: "#c45f00", rose: "#c2185b" };
  const FONDS = { lac: "Lac", aurore: "Aurore", coucher: "Coucher de soleil", foret: "Forêt", neige: "Neige", uni: "Uni" };
  function appliquerTheme() {
    const h = new Date().getHours();
    const t = reglages.theme === "auto" ? (h >= 19 || h < 7 ? "sombre" : "clair") : reglages.theme;
    document.documentElement.dataset.theme = t;
    document.documentElement.dataset.accent = reglages.accent;
    document.documentElement.dataset.fond = reglages.fond;
  }
  setInterval(appliquerTheme, 60000);

  function pastilles() {
    const ac = $("rAccents"); ac.innerHTML = "<span class=\"petit\">Couleur</span>";
    for (const [nom, couleur] of Object.entries(ACCENTS)) {
      const b = document.createElement("button");
      b.type = "button"; b.className = "pastille" + (reglages.accent === nom ? " choisie" : "");
      b.style.background = couleur; b.title = nom; b.setAttribute("aria-label", "Couleur " + nom);
      b.onclick = () => { reglages.accent = nom; appliquerTheme(); pastilles(); };
      ac.appendChild(b);
    }
    const fo = $("rFonds"); fo.innerHTML = "<span class=\"petit\">Fond d'écran</span>";
    for (const [nom, titre] of Object.entries(FONDS)) {
      const b = document.createElement("button");
      b.type = "button"; b.className = "vignette fond-" + nom + (reglages.fond === nom ? " choisie" : "");
      b.title = titre; b.setAttribute("aria-label", "Fond " + titre);
      b.innerHTML = `<span>${titre}</span>`;
      b.onclick = () => { reglages.fond = nom; appliquerTheme(); pastilles(); };
      fo.appendChild(b);
    }
  }

  function appliquerReglages() {
    appliquerTheme();
    document.documentElement.style.setProperty("--taille", reglages.taille + "px");
    $("nomAssistante").textContent = reglages.nomAssistante || "Mon assistante";
    document.title = reglages.nomAssistante || "Mon assistante";
    const h = new Date().getHours();
    const question = h >= 18 || h < 5 ? "On termine quelque chose ensemble ?" : "Prête à travailler ?";
    ecrireDoucement($("salutation"), (reglages.prenom ? `Salut ${reglages.prenom} ! ` : "Salut ! ") + question);
  }

  // Le message d'accueil s'écrit tout seul, lettre par lettre, comme à la main.
  function ecrireDoucement(el, texte) {
    if (el.dataset.texte === texte) return;
    el.dataset.texte = texte;
    el.setAttribute("aria-label", texte);
    if (matchMedia("(prefers-reduced-motion: reduce)").matches) { el.textContent = texte; return; }
    const tour = (el._tour = (el._tour || 0) + 1);  // une animation par élément
    el.textContent = "";
    el.classList.add("curseur");
    let i = 0;
    const suivant = () => {
      if (tour !== el._tour) return;
      el.textContent = texte.slice(0, ++i);
      if (i < texte.length) setTimeout(suivant, /[ !?,]/.test(texte[i - 1]) ? 110 : 45);
      else setTimeout(() => el.classList.remove("curseur"), 1200);
    };
    setTimeout(suivant, 350);
  }

  function quand(t) {
    const d = new Date(t), auj = new Date();
    if (d.toDateString() === auj.toDateString()) return d.toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
    return d.toLocaleDateString("fr-CA", { day: "numeric", month: "short" });
  }

  function afficherListe() {
    const l = $("listeConversations");
    l.innerHTML = "";
    if (!conversations.length) { l.innerHTML = `<div class="vide">Tes conversations apparaîtront ici.</div>`; }
    for (const c of conversations) {
      const e = document.createElement("div");
      e.className = "element" + (courante && c.id === courante.id ? " actif" : "");
      e.tabIndex = 0;
      e.innerHTML = `<span class="element-texte">${M.echapper(c.titre)}<span class="element-sous">${quand(c.maj)}</span></span><button class="suppr" title="Supprimer" aria-label="Supprimer">🗑️</button>`;
      e.onclick = (ev) => { if (!ev.target.closest(".suppr")) ouvrir(c.id); };
      e.onkeydown = (ev) => { if (ev.key === "Enter") ouvrir(c.id); };
      e.querySelector(".suppr").onclick = () => {
        if (!confirm(`Supprimer la conversation « ${c.titre} » ?`)) return;
        conversations = conversations.filter((x) => x.id !== c.id);
        sauver();
        if (courante && courante.id === c.id) nouvelle(); else afficherListe();
      };
      l.appendChild(e);
    }
    const d = $("listeDocuments");
    d.innerHTML = documents.length ? "" : `<div class="vide">Les documents, tableaux, affiches et vidéos créés apparaîtront ici. Ils sont dans le dossier « Mes documents » de la clé.</div>`;
    for (const doc of documents) {
      const e = document.createElement("div");
      e.className = "element";
      e.innerHTML = `<span>${doc.icone || "📄"}</span><span class="element-texte">${M.echapper(doc.nom)}<span class="element-sous">${M.echapper(doc.ou)} · ${quand(doc.date)}</span></span>`;
      e.title = `Dans : ${doc.ou}`;
      d.appendChild(e);
    }
  }

  function bulleElle(msg) {
    const box = document.createElement("div");
    box.className = "msg elle";
    const cartes = document.createElement("div");
    cartes.className = "cartes";
    cartes.style.cssText = "display:flex;flex-direction:column;gap:8px";
    const b = document.createElement("div");
    b.className = "bulle";
    const actions = document.createElement("div");
    actions.className = "actions-msg";
    box.append(cartes, b, actions);
    (msg.cartes || []).forEach((c) => cartes.appendChild(carte(c)));
    if (msg.texte) b.innerHTML = M.html(msg.texte); else b.classList.add("cache");
    if (msg.texte) ajouterActions(actions, msg);
    return { box, bulle: b, cartes, actions };
  }

  function afficherMessage(msg) {
    if (msg.role === "user") {
      const box = document.createElement("div");
      box.className = "msg moi";
      if (msg.images && msg.images.length) {
        const p = document.createElement("div");
        p.className = "photos";
        msg.images.forEach((src) => { const i = new Image(); i.src = src; i.alt = "photo"; p.appendChild(i); });
        box.appendChild(p);
      }
      if (msg.fichiers && msg.fichiers.length) {
        const j = document.createElement("div");
        j.className = "joint";
        j.textContent = "📎 " + msg.fichiers.join(", ");
        box.appendChild(j);
      }
      if (msg.affiche) { const b = document.createElement("div"); b.className = "bulle"; b.textContent = msg.affiche; box.appendChild(b); }
      $("fil").appendChild(box);
      return box;
    }
    const r = bulleElle(msg);
    $("fil").appendChild(r.box);
    return r;
  }

  function afficherConversation() {
    const fil = $("fil");
    [...fil.children].forEach((n) => { if (n.id !== "accueil") n.remove(); });
    const vide = !courante || !courante.messages.length;
    $("accueil").classList.toggle("cache", !vide);
    $("titreConversation").textContent = vide ? "Nouvelle conversation" : courante.titre;
    if (!vide) courante.messages.forEach(afficherMessage);
    defiler(true);
  }

  function defiler(force) {
    const f = $("fil");
    if (force || f.scrollHeight - f.scrollTop - f.clientHeight < 160) f.scrollTop = f.scrollHeight;
  }

  // ------------------------------------------------------- visionneuse
  // « Ouvrir » affiche le fichier ici même (Word, Excel, image, vidéo, PDF,
  // texte) au lieu de télécharger une copie de plus. Le contenu des documents
  // est montré dans un cadre isolé (aucun script ne peut s'y exécuter).
  let visActuel = null;
  // Ouvrir le VRAI fichier de la clé dans Word / Excel / PowerPoint, d'un clic.
  // Le lanceur donne l'emplacement de la clé (?racine=E:\) ; Office comprend
  // les liens « ms-word:ofe|u|file:///… ». Edge demande la permission la
  // première fois (on peut cocher « Toujours autoriser »).
  const RACINE = new URLSearchParams(location.search).get("racine");
  const OFFICE = { docx: ["ms-word", "Word"], doc: ["ms-word", "Word"], xlsx: ["ms-excel", "Excel"], xls: ["ms-excel", "Excel"], csv: ["ms-excel", "Excel"], pptx: ["ms-powerpoint", "PowerPoint"] };
  function lienOffice(nom, chemin) {
    const o = OFFICE[String(nom).split(".").pop().toLowerCase()];
    if (!o || !RACINE || chemin == null) return null;
    const morceaux = [RACINE.replace(/[\\/]+$/, ""), "Mes documents", ...String(chemin).split(/[\\/]+/).filter(Boolean), nom];
    const fichier = "file:///" + morceaux.map((m, i) => (i === 0 ? m : encodeURIComponent(m))).join("/");
    return { url: `${o[0]}:ofe|u|${fichier}`, appli: o[1] };
  }
  function ouvrirDansOffice(nom, chemin) {
    const l = lienOffice(nom, chemin);
    if (!l) return false;
    const a = document.createElement("a");
    a.href = l.url; document.body.appendChild(a); a.click(); a.remove();
    return true;
  }
  async function ouvrirFichier(blob, nom, chemin) {
    visActuel = { blob, nom, chemin };
    const office = lienOffice(nom, chemin);
    $("visOffice").classList.toggle("cache", !office);
    if (office) $("visOffice").textContent = `📝 Ouvrir dans ${office.appli}`;
    const ext = String(nom).split(".").pop().toLowerCase();
    const z = $("visContenu");
    z.innerHTML = "<div class=\"vide\">Ouverture…</div>";
    $("visTitre").textContent = nom;
    $("visNote").textContent = "Pour modifier ce fichier, ouvre-le depuis la clé dans l'Explorateur Windows (dossier « Mes documents »), ou clique sur « Copie à modifier ».";
    if (!$("visionneuse").open) $("visionneuse").showModal();
    const cadre = (html) => {
      const f = document.createElement("iframe");
      f.setAttribute("sandbox", "");
      f.srcdoc = `<!doctype html><meta charset="utf-8"><style>body{font:15px/1.5 Calibri,"Segoe UI",sans-serif;margin:24px;color:#222;background:#fff}table{border-collapse:collapse}td,th{border:1px solid #ccc;padding:4px 8px}img{max-width:100%}</style>${html}`;
      return f;
    };
    try {
      z.innerHTML = "";
      if (/^(png|jpe?g|gif|webp|bmp|svg)$/.test(ext)) { const i = new Image(); i.src = URL.createObjectURL(blob); i.alt = nom; z.appendChild(i); $("visNote").textContent = ""; }
      else if (/^(mp4|webm)$/.test(ext)) { const v = document.createElement("video"); v.controls = true; v.src = URL.createObjectURL(blob); z.appendChild(v); $("visNote").textContent = ""; }
      else if (ext === "pdf") { const f = document.createElement("iframe"); f.src = URL.createObjectURL(blob); z.appendChild(f); }
      else if (ext === "docx") { const r = await mammoth.convertToHtml({ arrayBuffer: await blob.arrayBuffer() }); z.appendChild(cadre(r.value || "<p>(Document vide)</p>")); }
      else if (/^(xlsx|xls|csv)$/.test(ext)) {
        const wb = XLSX.read(await blob.arrayBuffer(), { type: "array" });
        z.appendChild(cadre(wb.SheetNames.map((n) => `<h3>${M.echapper(n)}</h3>` + XLSX.utils.sheet_to_html(wb.Sheets[n], { header: "", footer: "" })).join("")));
      }
      else if (/^(txt|md|json)$/.test(ext)) { const pre = document.createElement("pre"); pre.className = "vis-texte"; pre.textContent = await blob.text(); z.appendChild(pre); }
      else z.innerHTML = `<div class="vide">Je ne peux pas afficher ce genre de fichier ici. Ouvre-le depuis l'Explorateur Windows, dans le dossier « Mes documents » de la clé.</div>`;
    } catch (e) { z.innerHTML = `<div class="vide">Impossible d'afficher ce fichier : ${M.echapper(e.message || e)}</div>`; }
  }
  $("visFermer").onclick = () => $("visionneuse").close();
  $("visOffice").onclick = () => { if (visActuel) ouvrirDansOffice(visActuel.nom, visActuel.chemin); };
  $("visCopie").onclick = () => { if (visActuel) Outils.telecharger(visActuel.blob, visActuel.nom.replace(/(\.[^.]+)$/, " (copie)$1")); };
  $("visionneuse").addEventListener("close", () => { $("visContenu").innerHTML = ""; visActuel = null; });

  // ------------------------------------------------------ cartes d'outils
  function carte(c) {
    const box = document.createElement("div");
    if (c.genre === "calcul" || c.genre === "erreur") {
      box.className = "carte " + c.genre;
      box.innerHTML = `<span class="gros">${c.genre === "erreur" ? "⚠️" : "🧮"}</span><div class="infos"><strong>${M.echapper(c.titre)}</strong><span>${M.echapper(c.detail)}</span></div>`;
      return box;
    }
    box.className = "carte";
    box.style.flexWrap = "wrap";
    const ou = c.mode === "dossier" ? `Enregistré dans ${c.ou}` : `Enregistré dans ${c.ou} (sur la clé)`;
    box.innerHTML = `<span class="gros">${c.icone || "📄"}</span><div class="infos"><strong>${M.echapper(c.nom)}</strong><span>${M.echapper(ou)}</span></div>`;
    const office = lienOffice(c.nom, cheminDe(c));
    if (office) {
      const w = document.createElement("button");
      w.className = "bouton petit principal";
      w.textContent = `📝 Ouvrir dans ${office.appli}`;
      w.title = `Ouvre le fichier de la clé dans ${office.appli}, pour le modifier`;
      w.onclick = () => ouvrirDansOffice(c.nom, cheminDe(c));
      box.appendChild(w);
    }
    if (c.blob) {
      const b = document.createElement("button");
      b.className = "bouton petit";
      b.textContent = office ? "👀 Aperçu" : "Ouvrir";
      b.title = "Afficher le fichier";
      b.onclick = () => ouvrirFichier(c.blob, c.nom, cheminDe(c));
      box.appendChild(b);
    }
    if (c.svg || c.url || (c.genre === "video" && c.blob)) {
      const v = document.createElement("div");
      v.className = "carte-visuel";
      v.style.flexBasis = "100%";
      if (c.svg) v.innerHTML = c.svg;  // produit par Mermaid en mode strict
      else if (c.url) { const i = new Image(); i.src = c.url; i.alt = c.nom; v.appendChild(i); }
      else { const vid = document.createElement("video"); vid.controls = true; vid.src = URL.createObjectURL(c.blob); vid.style.maxWidth = "100%"; v.appendChild(vid); }
      box.appendChild(v);
    }
    return box;
  }

  // Sous-dossier (dans « Mes documents ») d'un fichier créé, si on le connaît.
  function cheminDe(c) {
    if (c.mode === "telechargement") return "";
    // Dossier choisi dans Réglages : seulement si c'est vérifié comme celui de la clé.
    if (!Outils.etatDossier().estCle) return null;
    const parts = String(c.ou || "").split(" › ");
    return parts[0] === "Mes documents" ? parts.slice(1).join("/") : null;
  }

  function noterDocument(c) {
    documents.unshift({ nom: c.nom, ou: c.ou, icone: c.icone, date: Date.now() });
    sauverDocs();
    afficherListe();
  }

  // ----------------------------------------------------------------- voix
  // Lecture à voix haute avec les voix de Windows installées sur l'ordinateur
  // (localService) : le texte n'est pas envoyé sur internet. On préfère une
  // voix du Québec (fr-CA), sinon une autre voix française.
  const voix = {
    choisie: null,
    bouton: null,
    choisir() {
      const toutes = speechSynthesis.getVoices();
      const locales = toutes.filter((v) => v.localService);
      this.choisie = locales.find((v) => /^fr-CA/i.test(v.lang)) || locales.find((v) => /^fr/i.test(v.lang)) || null;
      return this.choisie;
    },
    arreter() {
      speechSynthesis.cancel();
      if (this.bouton) this.bouton.textContent = "🔊 Écouter";
      this.bouton = null;
    },
    lire(texte, bouton) {
      if (!("speechSynthesis" in window)) return;
      if (document.documentElement.classList.contains("verrouille")) return; // écran verrouillé : silence
      const etaitCeluiLa = this.bouton === bouton;
      this.arreter();
      if (etaitCeluiLa) return;
      if (!this.choisie && !this.choisir()) {
        bandeau("Aucune voix française n'est installée sur cet ordinateur. Dans Windows : Paramètres › Heure et langue › Voix › Ajouter des voix › Français (Canada).");
        return;
      }
      // Par phrases : les longues lectures d'un seul bloc peuvent couper.
      const phrases = M.texteSimple(texte).replace(/[•*#|]/g, " ").match(/[^.!?\n]+[.!?]*\s*/g) || [];
      if (!phrases.length) return;
      this.bouton = bouton || null;
      if (bouton) bouton.textContent = "⏹ Arrêter";
      phrases.forEach((p, i) => {
        const u = new SpeechSynthesisUtterance(p.trim());
        u.voice = this.choisie; u.lang = this.choisie.lang; u.rate = reglages.vitesseVoix || 1;
        if (i === phrases.length - 1) u.onend = () => { if (this.bouton === bouton) { if (bouton) bouton.textContent = "🔊 Écouter"; this.bouton = null; } };
        speechSynthesis.speak(u);
      });
    },
  };
  if ("speechSynthesis" in window) speechSynthesis.onvoiceschanged = () => voix.choisir();

  // Boutons sous chaque réponse : Copier, Écouter, Word, Excel, Recommencer.
  function ajouterActions(zone, msg) {
    zone.innerHTML = "";
    const bouton = (texte, titre, action) => {
      const b = document.createElement("button");
      b.className = "bouton fantome";
      b.textContent = texte; b.title = titre;
      b.onclick = action;
      zone.appendChild(b);
      return b;
    };
    const copier = bouton("📋 Copier", "Copier le texte pour le coller ailleurs", async () => {
      try { await navigator.clipboard.writeText(M.texteSimple(msg.texte)); copier.textContent = "✔ Copié"; }
      catch { copier.textContent = "Copie impossible"; }
      setTimeout(() => (copier.textContent = "📋 Copier"), 1800);
    });
    if ("speechSynthesis" in window) {
      const ecouter = bouton("🔊 Écouter", "Lire la réponse à voix haute", () => voix.lire(msg.texte, ecouter));
      zone._ecouter = ecouter;
    }
    bouton("📄 Mettre dans Word", "Créer un fichier Word avec cette réponse", async () => {
      const titre = (M.blocs(msg.texte).find((b) => b.type === "h") || {}).text || "";
      const nom = prompt("Nom du document :", (titre || (courante && courante.titre) || "Document").slice(0, 60));
      if (!nom) return;
      const blob = await Outils.docxDepuisMarkdown("", msg.texte);
      const f = await Outils.enregistrer(blob, Outils.nomPropre(nom, "docx"));
      noterDocument({ ...f, icone: "📄" });
      zone.parentElement.querySelector(".cartes").appendChild(carte({ genre: "fichier", icone: "📄", ...f }));
    });
    const tableau = M.blocs(msg.texte).find((b) => b.type === "table");
    if (tableau) {
      bouton("📊 Mettre dans Excel", "Créer un fichier Excel avec ce tableau", async () => {
        const nom = prompt("Nom du tableau :", (courante && courante.titre ? courante.titre : "Tableau").slice(0, 60));
        if (!nom) return;
        const [tete, ...corps] = tableau.rows;
        const plat = (t) => M.morceaux(t).map((m) => m.text).join("");
        const nums = tete.map((_, c) => corps.length && corps.every((r) => Outils.lireNombre(plat(r[c] || "")) !== null));
        // On n'additionne pas les colonnes d'identifiants (numéro, code, année,
        // téléphone…) — mots entiers seulement : « Quantité » et « Montant » restent.
        const estIdentifiant = (h) => /^\s*(n[o°º]\.?|num[ée]ro|#|code|ann[ée]e|an|id|t[ée]l(?:[ée]phone)?|prix unitaire|qt[ée] unitaire)(\b|\s|$)/i.test(h);
        const totaliser = tete.filter((_, c) => nums[c] && !estIdentifiant(plat(tete[c]))).map(plat);
        const blob = Outils.xlsxDepuisTableau(nom, tete.map(plat), corps.map((r) => r.map(plat)), totaliser);
        const f = await Outils.enregistrer(blob, Outils.nomPropre(nom, "xlsx"));
        noterDocument({ ...f, icone: "📊" });
        zone.parentElement.querySelector(".cartes").appendChild(carte({ genre: "fichier", icone: "📊", ...f }));
      });
    }
    const derniere = courante && courante.messages[courante.messages.length - 1] === msg;
    if (window.Cerveau) bouton("🧠 Corriger", "Elle s'est trompée ? Dis-lui quoi retenir pour la prochaine fois", () => {
      window.Cerveau.corriger(derniere && !enCours, (lecon) => {
        if (enCours) return;
        if (window.ouvrirAppli) window.ouvrirAppli("assistante");
        $("message").value = `Petite correction : ${lecon}\nPeux-tu refaire ta réponse en tenant compte de ça ?`;
        ajusterSaisie();
        $("envoyer").click();
      });
    });
    if (derniere) {
      const refaire = bouton("🔁 Recommencer", "Écrire une autre réponse", () => {
        // Seulement si cette réponse est encore la dernière de la conversation.
        if (enCours || !courante || courante.messages[courante.messages.length - 1] !== msg) return;
        courante.messages.pop();
        sauver();
        afficherConversation();
        repondre();
      });
      refaire.classList.add("recommencer");
    }
  }

  // ------------------------------------------------------ pièces jointes
  function redimensionner(fichier, max = 1024) {
    return new Promise((ok, ko) => {
      const lecteur = new FileReader();
      lecteur.onload = () => {
        const img = new Image();
        img.onload = () => {
          const r = Math.min(1, max / Math.max(img.width, img.height));
          const c = document.createElement("canvas");
          c.width = Math.round(img.width * r); c.height = Math.round(img.height * r);
          c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
          ok(c.toDataURL("image/jpeg", 0.85));
        };
        img.onerror = () => ko(new Error("Image illisible"));
        img.src = lecteur.result;
      };
      lecteur.onerror = () => ko(lecteur.error);
      lecteur.readAsDataURL(fichier);
    });
  }

  async function lireFichier(f) {
    const nom = f.name || "fichier";
    const ext = nom.split(".").pop().toLowerCase();
    if (f.type.startsWith("image/")) return { type: "image", nom, data: await redimensionner(f) };
    if (ext === "docx") {
      const r = await mammoth.extractRawText({ arrayBuffer: await f.arrayBuffer() });
      return { type: "texte", nom, data: r.value };
    }
    if (ext === "xlsx" || ext === "xls") {
      const wb = XLSX.read(await f.arrayBuffer(), { type: "array" });
      return { type: "texte", nom, data: wb.SheetNames.map((s) => `--- Feuille « ${s} » ---\n` + XLSX.utils.sheet_to_csv(wb.Sheets[s], { FS: " | " })).join("\n\n") };
    }
    if (["txt", "csv", "md"].includes(ext) || f.type.startsWith("text/")) return { type: "texte", nom, data: await f.text() };
    throw new Error(`Je ne sais pas encore lire les fichiers « .${ext} ». Essaie une photo, un Word, un Excel ou un texte.`);
  }

  async function ajouterFichiers(liste) {
    for (const f of liste) {
      try { joints.push(await lireFichier(f)); }
      catch (e) { bandeau(e.message); }
    }
    afficherJoints();
  }

  function afficherJoints() {
    const z = $("apercus");
    z.innerHTML = "";
    joints.forEach((j, k) => {
      const a = document.createElement("div");
      a.className = "apercu";
      a.innerHTML = j.type === "image" ? `<img src="${j.data}" alt="">` : `<span>📄 ${M.echapper(j.nom)}</span>`;
      const x = document.createElement("button");
      x.textContent = "×"; x.title = "Retirer"; x.setAttribute("aria-label", "Retirer");
      x.onclick = () => { joints.splice(k, 1); afficherJoints(); };
      a.appendChild(x);
      z.appendChild(a);
    });
  }

  // --------------------------------------------------------- conversation
  function nouvelle() {
    courante = null;
    afficherConversation();
    afficherListe();
    $("message").focus();
  }

  function ouvrir(cid) {
    courante = conversations.find((c) => c.id === cid) || null;
    afficherConversation();
    afficherListe();
    $("cote").classList.remove("ouvert");
  }

  // Toutes les photos de la conversation, dans l'ordre (photo 1, photo 2…).
  const photosDe = (conv) => (conv ? conv.messages.flatMap((m) => m.images || []) : []);

  async function envoyer() {
    const texte = $("message").value.trim();
    if ((!texte && !joints.length) || enCours) return;
    voix.arreter();
    document.querySelectorAll(".recommencer").forEach((b) => b.remove());
    if (!courante) {
      courante = { id: id(), titre: (texte || "Photo").replace(/\s+/g, " ").slice(0, 48), messages: [], maj: Date.now() };
      conversations.unshift(courante);
    }
    const avant = photosDe(courante).length;
    const images = joints.filter((j) => j.type === "image").map((j) => j.data);
    const textes = joints.filter((j) => j.type === "texte");
    let contenu = texte;
    if (images.length) contenu = `(Photo${images.length > 1 ? "s" : ""} jointe${images.length > 1 ? "s" : ""} : ${images.map((_, k) => "photo " + (avant + k + 1)).join(", ")})\n` + contenu;
    for (const t of textes) contenu += `\n\n--- Contenu du fichier « ${t.nom} » ---\n${t.data.slice(0, 20000)}`;
    const msg = { role: "user", texte: contenu, affiche: texte, images, fichiers: joints.map((j) => j.nom).filter((n, k) => joints[k].type === "texte" || !n.startsWith("image")) };
    courante.messages.push(msg);
    courante.maj = Date.now();
    conversations = [courante, ...conversations.filter((c) => c.id !== courante.id)];
    joints = [];
    afficherJoints();
    $("message").value = "";
    ajusterSaisie();
    $("accueil").classList.add("cache");
    $("titreConversation").textContent = courante.titre;
    afficherMessage(msg);
    defiler(true);
    sauver();
    afficherListe();
    await repondre();
  }

  // Messages envoyés au moteur : consigne + derniers échanges. Les photos ne
  // sont renvoyées que pour les 4 derniers messages (plus rapide).
  function pourLeModele() {
    const garde = courante.messages.slice(-avance().messagesGardes);
    const n = garde.length;
    let compteur = photosDe(courante).length - garde.reduce((s, m) => s + (m.images || []).length, 0);
    const out = [{ role: "system", content: consigne() }];
    garde.forEach((m, k) => {
      if (m.role === "user") {
        const imgs = m.images || [];
        if (imgs.length && k >= n - 4) {
          out.push({ role: "user", content: [{ type: "text", text: m.texte || "Voici la photo." }, ...imgs.map((src) => ({ type: "image_url", image_url: { url: src } }))] });
        } else {
          out.push({ role: "user", content: m.texte + (imgs.length ? `\n[${imgs.length} photo(s) envoyée(s) plus tôt]` : "") });
        }
        compteur += imgs.length;
      } else {
        const fichiers = (m.cartes || []).filter((c) => c.nom).map((c) => `[Fichier créé : ${c.nom}]`).join("\n");
        out.push({ role: "assistant", content: [fichiers, m.texte].filter(Boolean).join("\n") || "(fait)" });
      }
    });
    return out;
  }

  // Lit la réponse du moteur au fur et à mesure (texte + appels d'outils).
  async function demander(messages, signal, surTexte) {
    const r = await fetch("/v1/chat/completions", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      signal,
      body: JSON.stringify({
        messages, stream: true, tools: Outils.SCHEMAS,
        temperature: avance().temperature, top_p: 0.8, top_k: 20, repeat_penalty: 1.05,
        max_tokens: avance().maxTokens,
      }),
    });
    if (!r.ok) {
      let detail = "";
      try { detail = (await r.json()).error?.message || ""; } catch {}
      throw new Error(r.status === 503 ? "L'assistante n'est pas encore prête (installation de l'IA ou réveil en cours). Va voir l'appli « 🧠 Modèles IA » sur le bureau." : `Le moteur a répondu une erreur (${r.status}). ${detail}`);
    }
    const lecteur = r.body.getReader();
    const dec = new TextDecoder();
    let tampon = "", texte = "";
    const appels = [];
    for (;;) {
      const { value, done } = await lecteur.read();
      if (done) break;
      tampon += dec.decode(value, { stream: true });
      const lignes = tampon.split("\n");
      tampon = lignes.pop();
      for (const l of lignes) {
        const t = l.trim();
        if (!t.startsWith("data:")) continue;
        const data = t.slice(5).trim();
        if (data === "[DONE]") continue;
        let j;
        try { j = JSON.parse(data); } catch { continue; }
        const d = j.choices && j.choices[0] && j.choices[0].delta;
        if (!d) continue;
        if (d.content) { texte += d.content; surTexte(texte); }
        for (const tc of d.tool_calls || []) {
          const k = tc.index ?? appels.length;
          appels[k] = appels[k] || { id: tc.id || "appel" + k, type: "function", function: { name: "", arguments: "" } };
          if (tc.id) appels[k].id = tc.id;
          if (tc.function?.name) appels[k].function.name += tc.function.name;
          if (tc.function?.arguments) appels[k].function.arguments += tc.function.arguments;
        }
      }
    }
    return { texte, appels: appels.filter(Boolean) };
  }

  async function repondre() {
    const conv = courante;
    const msg = { role: "assistant", texte: "", cartes: [] };
    const vue = bulleElle(msg);
    vue.bulle.classList.remove("cache");
    vue.bulle.innerHTML = `<span class="ecrit curseur">Je réfléchis</span>`;
    $("fil").appendChild(vue.box);
    defiler(true);
    enCours = new AbortController();
    $("envoyer").classList.add("cache");
    $("arreter").classList.remove("cache");

    const messages = pourLeModele();
    let texteFinal = "";
    let partiel = "";   // texte déjà affiché, gardé si on clique « Arrêter »
    try {
      for (let tour = 0; tour < 6; tour++) {
        partiel = "";
        const { texte, appels } = await demander(messages, enCours.signal, (t) => {
          partiel = t;
          vue.bulle.innerHTML = M.html(t);
          vue.bulle.lastElementChild && vue.bulle.lastElementChild.classList.add("curseur");
          defiler();
        });
        texteFinal = texte;
        if (!appels.length) break;
        messages.push({ role: "assistant", content: texte || "", tool_calls: appels });
        for (const a of appels) {
          if (enCours.signal.aborted) throw new DOMException("Arrêté.", "AbortError");
          let args = {};
          try { args = a.function.arguments ? JSON.parse(a.function.arguments) : {}; } catch { args = null; }
          const attente = document.createElement("div");
          attente.className = "carte";
          attente.innerHTML = `<span class="gros">⏳</span><div class="infos"><strong>Je m'en occupe…</strong><span>${M.echapper(nomOutil(a.function.name))}</span><div class="progres"><div></div></div></div>`;
          vue.cartes.appendChild(attente);
          defiler();
          let resultat;
          try {
            if (args === null) throw new Error("Les informations reçues étaient mal formées.");
            const r = await Outils.executer(a.function.name, args, {
              signal: enCours.signal,
              photos: photosDe(conv),
              coordonnees: reglages.coordonnees,
              progres: (p) => { const b = attente.querySelector(".progres > div"); if (b) b.style.width = Math.round(p * 100) + "%"; },
            });
            resultat = r.texte;
            attente.remove();
            if (r.carte) {
              msg.cartes.push(r.carte);
              vue.cartes.appendChild(carte(r.carte));
              if (r.carte.nom) noterDocument(r.carte);
            }
          } catch (e) {
            if (e.name === "AbortError") { attente.remove(); throw e; }
            resultat = "Erreur : " + (e.message || e);
            attente.remove();
            const c = { genre: "erreur", titre: "Ça n'a pas marché", detail: e.message || String(e) };
            msg.cartes.push(c);
            vue.cartes.appendChild(carte(c));
          }
          messages.push({ role: "tool", tool_call_id: a.id, name: a.function.name, content: resultat });
          defiler();
        }
        vue.bulle.innerHTML = `<span class="ecrit curseur">Je termine</span>`;
      }
    } catch (e) {
      if (e.name === "AbortError") texteFinal = (partiel || texteFinal || "") + "\n\n*(Arrêté.)*";
      else {
        texteFinal = "Oups, je n'arrive pas à répondre : " + (e.message || e) + "\n\nSi ça continue, ferme la fenêtre et redémarre l'assistante.";
        verifierMoteur();
      }
    }
    enCours = null;
    $("envoyer").classList.remove("cache");
    $("arreter").classList.add("cache");
    msg.texte = texteFinal.trim();
    if (!msg.texte && !msg.cartes.length) msg.texte = "(Je n'ai rien trouvé à répondre. Peux-tu reformuler ?)";
    // Les fichiers (blob) ne sont pas gardés en mémoire permanente : ils sont sur la clé.
    const aGarder = { ...msg, cartes: msg.cartes.map(({ blob, url, ...reste }) => reste) };
    conv.messages.push(aGarder);
    conv.maj = Date.now();
    sauver();
    if (msg.texte) { vue.bulle.classList.remove("cache"); vue.bulle.innerHTML = M.html(msg.texte); } else vue.bulle.classList.add("cache");
    // Les boutons utilisent la version affichée (avec le fichier pour « Ouvrir »).
    if (msg.texte) {
      ajouterActions(vue.actions, aGarder);
      // Jamais de lecture à voix haute quand l'écran est verrouillé.
      if (reglages.voixAuto && vue.actions._ecouter && !document.documentElement.classList.contains("verrouille")) voix.lire(aGarder.texte, vue.actions._ecouter);
    }
    if (courante === conv) {
      // Elle est revenue dans cette conversation pendant que la réponse
      // s'écrivait : l'ancien affichage a été remplacé, on redessine le fil.
      if (!vue.box.isConnected) afficherConversation();
      afficherListe();
    }
    defiler();
  }

  const nomOutil = (n) => ({
    calculer: "Calcul", taxes_quebec: "Calcul des taxes", creer_document_word: "Création du document Word",
    creer_tableau_excel: "Création du tableau Excel", creer_affiche: "Création de l'affiche", dessiner_schema: "Dessin du schéma",
    creer_video: "Fabrication de la vidéo (ça prend le temps de la vidéo)", creer_dossier: "Création du dossier", lister_documents: "Liste des documents",
  }[n] || n);

  // -------------------------------------------------------------- divers
  function bandeau(texte, bouton) {
    const b = $("bandeau");
    b.innerHTML = `<span style="flex:1">${M.echapper(texte)}</span>`;
    if (bouton) { const x = document.createElement("button"); x.className = "bouton petit"; x.textContent = bouton.texte; x.onclick = bouton.action; b.appendChild(x); }
    const f = document.createElement("button"); f.className = "bouton fantome petit"; f.textContent = "×"; f.setAttribute("aria-label", "Fermer"); f.onclick = () => b.classList.add("cache");
    b.appendChild(f);
    b.classList.remove("cache");
  }

  async function verifierMoteur() {
    try {
      const r = await fetch("/health", { cache: "no-store" });
      if (r.ok) { $("bandeau").classList.add("cache"); return true; }
      let info = {};
      try { info = await r.json(); } catch {}
      if (info.status === "installation") {
        bandeau("Il manque une étape : installer l'intelligence artificielle de ton mini-ordinateur.",
          { texte: "🧠 Choisir mon IA", action: () => window.ouvrirAppli && window.ouvrirAppli("modeles") });
      } else bandeau("L'assistante finit de se réveiller… encore quelques secondes.");
    } catch {
      bandeau("L'assistante ne répond plus. Ferme cette fenêtre et double-clique de nouveau sur « ▶️ START ».");
    }
    setTimeout(verifierMoteur, 4000);
    return false;
  }

  async function afficherDossier() {
    const e = Outils.etatDossier();
    const z = $("dossierEtat");
    z.innerHTML = "";
    if (!e.nom) { z.textContent = "📁 Fichiers : dossier « Mes documents » de la clé"; $("rDossier").textContent = "Par défaut : « Mes documents » sur la clé."; return; }
    $("rDossier").textContent = `« ${e.nom} »` + (e.pret ? "" : " — à reconnecter");
    if (e.pret) { z.textContent = `📁 Dossier de travail : ${e.nom}`; return; }
    z.textContent = `📁 « ${e.nom} » doit être reconnecté.`;
    const b = document.createElement("button");
    b.className = "bouton petit plein"; b.textContent = "Reconnecter mon dossier";
    b.onclick = async () => { try { await Outils.reconnecterDossier(); } catch {} afficherDossier(); };
    z.appendChild(b);
  }

  function ajusterSaisie() {
    const t = $("message");
    t.style.height = "auto";
    t.style.height = Math.min(240, t.scrollHeight) + "px";
  }

  // ------------------------------------------------------------ branchements
  $("envoyer").onclick = envoyer;
  $("arreter").onclick = () => enCours && enCours.abort();
  $("nouvelle").onclick = nouvelle;
  $("menu").onclick = () => $("cote").classList.toggle("ouvert");
  $("message").addEventListener("input", ajusterSaisie);
  $("message").addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey && !e.isComposing) { e.preventDefault(); envoyer(); }
  });
  $("joindre").onclick = () => $("fichier").click();
  $("fichier").onchange = (e) => { ajouterFichiers([...e.target.files]); e.target.value = ""; };
  document.addEventListener("paste", (e) => {
    const f = [...(e.clipboardData?.files || [])];
    if (f.length) { e.preventDefault(); ajouterFichiers(f); }
  });
  const zone = document.querySelector(".principal-zone");
  zone.addEventListener("dragover", (e) => { e.preventDefault(); zone.classList.add("depot"); });
  zone.addEventListener("dragleave", (e) => { if (!zone.contains(e.relatedTarget)) zone.classList.remove("depot"); });
  zone.addEventListener("drop", (e) => { e.preventDefault(); zone.classList.remove("depot"); ajouterFichiers([...e.dataTransfer.files]); });

  document.querySelectorAll(".onglet").forEach((o) => (o.onclick = () => {
    document.querySelectorAll(".onglet").forEach((x) => x.classList.toggle("actif", x === o));
    $("listeConversations").classList.toggle("cache", o.dataset.onglet !== "conversations");
    $("listeDocuments").classList.toggle("cache", o.dataset.onglet !== "documents");
  }));

  $("textePlus").onclick = () => { reglages.taille = Math.min(26, reglages.taille + 1); stock.ecrire("reglages", reglages); appliquerReglages(); };
  $("texteMoins").onclick = () => { reglages.taille = Math.max(13, reglages.taille - 1); stock.ecrire("reglages", reglages); appliquerReglages(); };

  $("ouvrirReglages").onclick = () => {
    $("rPrenom").value = reglages.prenom; $("rNom").value = reglages.nomAssistante; $("rEntreprise").value = reglages.entreprise;
    $("rSignature").value = reglages.signature; $("rCoordonnees").value = reglages.coordonnees;
    $("rRole").value = reglages.role || ""; $("rVille").value = reglages.ville || "";
    $("rFete").value = reglages.fete ? new Date().getFullYear() + "-" + reglages.fete : "";
    $("rVoixAuto").checked = !!reglages.voixAuto; $("rVitesse").value = reglages.vitesseVoix || 1;
    $("rTheme").value = reglages.theme;
    reglagesAvant = { theme: reglages.theme, accent: reglages.accent, fond: reglages.fond };
    pastilles();
    afficherDossier();
    $("reglages").showModal();
  };
  $("rChoisir").onclick = async () => {
    try { await Outils.choisirDossier(); } catch (e) { if (e.name !== "AbortError") alert(e.message); }
    afficherDossier();
  };
  let reglagesAvant = null;
  $("rTheme").onchange = () => { reglages.theme = $("rTheme").value; appliquerTheme(); };
  $("reglages").addEventListener("close", () => {
    if ($("reglages").returnValue !== "ok") {
      // Annuler : on remet l'apparence d'avant (l'aperçu était en direct).
      if (reglagesAvant) Object.assign(reglages, reglagesAvant);
      appliquerTheme();
      return;
    }
    reglages.prenom = $("rPrenom").value.trim();
    reglages.role = $("rRole").value.trim();
    reglages.ville = $("rVille").value.trim();
    reglages.fete = ($("rFete").value || "").slice(5);
    reglages.nomAssistante = $("rNom").value.trim() || "Mon assistante";
    reglages.entreprise = $("rEntreprise").value.trim();
    reglages.signature = $("rSignature").value.trim();
    reglages.coordonnees = $("rCoordonnees").value.trim();
    reglages.voixAuto = $("rVoixAuto").checked;
    reglages.vitesseVoix = parseFloat($("rVitesse").value) || 1;
    stock.ecrire("reglages", reglages);
    appliquerReglages();
  });

  // Pont avec le bureau (bureau.js) : les autres applis peuvent envoyer un
  // texte ou des fichiers à l'assistante.
  window.Assistante = {
    ajouterFichiers,
    ecrireDoucement,
    ouvrirFichier,
    ecrire(texte, nouvelleConv) {
      if (nouvelleConv) nouvelle();
      $("message").value = texte;
      ajusterSaisie();
      setTimeout(() => { $("message").focus(); $("message").setSelectionRange(texte.length, texte.length); }, 50);
    },
    reglages: () => reglages,
    sauverReglages() { stock.ecrire("reglages", reglages); appliquerReglages(); },
    avance,
    conversations: () => conversations,
    documents: () => documents,
    noterDocument,
    afficherDossier,
  };

  // ---------------------------------------------------------------- départ
  appliquerReglages();
  afficherRaccourcis();
  afficherListe();
  afficherConversation();
  Outils.chargerDossier().then(afficherDossier);
  verifierMoteur();
  $("message").focus();
})();
