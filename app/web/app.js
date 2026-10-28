/* Ti-Labo — bureau du laboratoire (interface) */
"use strict";

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const ic = (id) => `<svg class="ic"><use href="#i-${id}"/></svg>`;

const etat = { params: {}, modeles: [], projets: [], projet: null, fichier: null, modifie: false };

const CATALOGUE = [
  // --- Top 5 pour coder (du plus fort au plus léger)
  { nom: "qwen3-coder:30b", taille: 19, tags: ["code", "internet", "top"], groupe: "code", desc: "N° 1 du code en local. Demande un gros PC (24 Go de mémoire vidéo ou 32 Go de RAM)." },
  { nom: "devstral:24b", taille: 14, tags: ["code", "internet", "top"], groupe: "code", desc: "N° 2 — très bon pour travailler dans un projet entier (agent de code)." },
  { nom: "deepseek-coder-v2:16b", taille: 8.9, tags: ["code", "top"], groupe: "code", desc: "N° 3 — rapide malgré sa taille, très bon en code." },
  { nom: "qwen2.5-coder:14b", taille: 9.0, tags: ["code", "internet", "top"], groupe: "code", desc: "N° 4 — l'équilibre parfait pour une carte de 16 Go." },
  { nom: "qwen2.5-coder:7b", taille: 4.7, tags: ["code", "internet", "top"], groupe: "code", desc: "N° 5 — le meilleur Codex pour un PC ordinaire." },
  // --- Généralistes, vision et mémoire
  { nom: "qwen3:8b", taille: 5.2, tags: ["internet"], desc: "Très bon en jasette et pour faire des plans. Bon avec Internet." },
  { nom: "gpt-oss:20b", taille: 14, tags: ["internet", "open source"], desc: "IA open source (OpenAI, licence Apache 2.0) très forte pour chercher sur Internet et suivre des liens. Pour le PC (16 Go de RAM ou plus)." },
  { nom: "qwen3-vl:8b", taille: 6.1, tags: ["vision", "internet"], desc: "Voit les images ET peut chercher sur Internet." },
  { nom: "gemma3:4b", taille: 3.3, tags: ["vision"], desc: "Léger, voit les images (captures d'écran, photos)." },
  { nom: "qwen2.5vl:7b", taille: 6.0, tags: ["vision"], desc: "Lit très bien le texte dans les images." },
  { nom: "llama3.2:3b", taille: 2.0, tags: ["internet"], desc: "Petit et rapide pour les vieux PC." },
  { nom: "nomic-embed-text", taille: 0.3, tags: ["mémoire"], desc: "Requis pour la Mémoire de la clé." },
];

/* ------------------------------------------------------------ API */
async function api(route, donnees, options = {}) {
  const r = await fetch("/api/" + route, {
    method: donnees === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json", "X-TiLabo": window.TILABO_JETON || "" },
    body: donnees === undefined ? undefined : JSON.stringify(donnees),
    signal: options.signal,
  });
  if (options.flux) return r;
  const j = await r.json().catch(() => ({}));
  if (!r.ok || j.erreur) throw new Error(j.erreur || "Erreur " + r.status);
  return j;
}

async function lireFlux(reponse, surLigne) {
  if (!reponse.ok) {
    const j = await reponse.json().catch(() => ({}));
    throw new Error(j.erreur || "Erreur " + reponse.status);
  }
  const lecteur = reponse.body.getReader();
  const dec = new TextDecoder();
  let tampon = "";
  for (;;) {
    const { value, done } = await lecteur.read();
    if (done) break;
    tampon += dec.decode(value, { stream: true });
    let i;
    while ((i = tampon.indexOf("\n")) >= 0) {
      const l = tampon.slice(0, i).trim();
      tampon = tampon.slice(i + 1);
      if (!l) continue;
      let o;
      try { o = JSON.parse(l); } catch { continue; /* ligne partielle */ }
      surLigne(o);
    }
  }
}

let toastMinuteur;
function toast(msg) {
  const t = $("#toast");
  t.textContent = msg;
  t.style.display = "block";
  clearTimeout(toastMinuteur);
  toastMinuteur = setTimeout(() => (t.style.display = "none"), 4000);
}

/* ------------------------------------------------------------ markdown minimal */
function rendreMarkdown(texte, avecActions = true) {
  const blocs = [];
  // Blocs du Codex (« === FICHIER: x === … === FIN === ») : code en couleur, même pendant qu'il s'écrit
  texte = texte.replace(/^=== (FICHIER|MODIFIER): *(.+?) *===[ \t]*\n?([\s\S]*?)(?:^=== FIN ===[ \t]*$|(?![\s\S]))/gm, (_, sorte, chemin, code) => {
    blocs.push({ lang: chemin.split("/").pop(), code: code.replace(/\n$/, ""), titre: (sorte === "FICHIER" ? "📄 " : "✏️ ") + chemin.trim() });
    return `\u0000${blocs.length - 1}\u0000`;
  });
  texte = texte.replace(/```([\w.+-]*)\n?([\s\S]*?)(```|$)/g, (_, lang, code) => {
    blocs.push({ lang, code });
    return `\u0000${blocs.length - 1}\u0000`;
  });
  let h = esc(texte)
    .replace(/^#### (.*)$/gm, "<h4>$1</h4>").replace(/^### (.*)$/gm, "<h3>$1</h3>").replace(/^## (.*)$/gm, "<h3>$1</h3>").replace(/^# (.*)$/gm, "<h3>$1</h3>")
    .replace(/\*\*(.+?)\*\*/g, "<b>$1</b>")
    .replace(/`([^`\n]+)`/g, "<code>$1</code>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>')
    .replace(/(^|[\s(])(https?:\/\/[^\s<)]+)/g, '$1<a href="$2" target="_blank" rel="noopener noreferrer">$2</a>');
  h = h.replace(/(?:^|\n)((?:[-*] .*(?:\n|$))+)/g, (_, l) => "\n<ul>" + l.trim().split("\n").map((x) => "<li>" + x.replace(/^[-*] /, "") + "</li>").join("") + "</ul>");
  h = h.replace(/(?:^|\n)((?:\d+[.)] .*(?:\n|$))+)/g, (_, l) => "\n<ol>" + l.trim().split("\n").map((x) => "<li>" + x.replace(/^\d+[.)] /, "") + "</li>").join("") + "</ol>");
  h = h.split(/\n{2,}/).map((p) => (/^\s*<(h\d|ul|ol)/.test(p) || p.includes("\u0000") ? p : "<p>" + p.replace(/\n/g, "<br>") + "</p>")).join("\n");
  return h.replace(/\u0000(\d+)\u0000/g, (_, n) => {
    const b = blocs[+n];
    const actions = avecActions
      ? `<button class="btn petit" data-act="copier">Copier</button><button class="btn petit" data-act="inserer">Mettre dans l'éditeur</button>`
      : "";
    return `<div class="bloc-code" data-lang="${esc((b.lang || "").toLowerCase())}"><div class="bc-tete">${esc(b.titre || b.lang || "code")}<span class="espace"></span>${actions}</div><pre><code>${window.TiCouleurs ? TiCouleurs.colorer(b.code.replace(/\n$/, ""), TiCouleurs.langueDe(b.lang)) : esc(b.code.replace(/\n$/, ""))}</code></pre></div>`;
  });
}

document.addEventListener("click", (e) => {
  const b = e.target.closest("[data-act]");
  if (!b) return;
  const code = b.closest(".bloc-code")?.querySelector("code")?.textContent ?? "";
  if (b.dataset.act === "copier") {
    navigator.clipboard.writeText(code).then(() => toast("Copié !"));
  } else if (b.dataset.act === "inserer") {
    if (!etat.fichier) return toast("Ouvre d'abord un fichier dans Mes projets.");
    if (!confirm("Remplacer le contenu de " + etat.fichier + " par ce code ?\n(Rien n'est enregistré tant que tu ne fais pas Ctrl+S.)")) return;
    $("#editeur").value = code;
    marquerModifie(true);
    majNumeros();
    allerA("projets");
  }
});

/* ------------------------------------------------------------ images (vision) */
function reduireImage(fichier, max = 1280) {
  return new Promise((ok, ko) => {
    const lecteur = new FileReader();
    lecteur.onerror = ko;
    lecteur.onload = () => {
      const img = new Image();
      img.onerror = ko;
      img.onload = () => {
        const r = Math.min(1, max / Math.max(img.width, img.height));
        const c = document.createElement("canvas");
        c.width = Math.round(img.width * r);
        c.height = Math.round(img.height * r);
        c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
        ok(c.toDataURL("image/jpeg", 0.88));
      };
      img.src = lecteur.result;
    };
    lecteur.readAsDataURL(fichier);
  });
}

/* ------------------------------------------------------------ écriture fluide (machine à écrire) */
function machineAEcrire(el, apres) {
  // L'affichage suit la vitesse réelle de l'IA choisie : une IA rapide écrit vite, une IA lente écrit
  // doucement, mais toujours lettre par lettre, sans à-coups. S'il prend du retard, il rattrape en douceur.
  let cible = "", montre = 0, boucle = null, erreurs = "", fin = null, dernierRendu = 0;
  let avant = 0, reserve = 0, debit = 60, dernierAjout = 0; // debit : caractères par seconde reçus de l'IA
  function rendre(force) {
    const t = performance.now();
    if (!force && t - dernierRendu < 33) return; // ~30 images/s : fluide sans faire chauffer le PC
    dernierRendu = t;
    el.innerHTML = rendreMarkdown(cible.slice(0, Math.floor(montre))) + erreurs;
    apres();
  }
  function pas(t) {
    const dt = Math.min(100, avant ? t - avant : 16);
    avant = t;
    const reste = cible.length - montre;
    if (reste > 0) {
      let vitesse = Math.max(45, debit * 1.08);           // un peu plus vite que l'IA pour ne pas traîner
      const retard = reste / vitesse;                      // secondes de retard
      if (retard > 1.2) vitesse *= retard / 1.2;           // rattrapage progressif
      if (fin) vitesse = Math.max(vitesse, reste * 2.5);   // l'IA a fini : on termine en moins d'une demi-seconde
      reserve += (vitesse * dt) / 1000;
      const n = Math.floor(reserve);
      if (n > 0) { reserve -= n; montre = Math.min(cible.length, montre + n); rendre(); }
      boucle = requestAnimationFrame(pas);
    } else {
      boucle = null; avant = 0; reserve = 0;
      rendre(true);
      if (fin) { fin(); fin = null; }
    }
  }
  return {
    ajouter(t) {
      const maintenant = performance.now();
      if (dernierAjout) {
        const ecart = Math.max(8, maintenant - dernierAjout);
        debit = debit * 0.8 + ((t.length * 1000) / ecart) * 0.2; // moyenne glissante du débit de l'IA
      }
      dernierAjout = maintenant;
      cible += t;
      if (!boucle) boucle = requestAnimationFrame(pas);
    },
    erreur(m) { erreurs += `<p style="color:var(--red)">${esc(m)}</p>`; rendre(true); },
    terminer() {
      if (document.hidden) { montre = cible.length; rendre(true); return Promise.resolve(); }
      if (!boucle && montre >= cible.length) { rendre(true); return Promise.resolve(); }
      return new Promise((ok) => { fin = ok; if (!boucle) boucle = requestAnimationFrame(pas); });
    },
  };
}

/* ------------------------------------------------------------ IA en mémoire (chargées) */
// Une IA « chargée » est déjà dans la mémoire du PC (ou de la carte graphique) : elle répond tout de suite.
// Sinon, la première réponse doit d'abord relire le modèle sur la clé (10 à 60 s).
etat.chargees = []; etat.enChargement = new Set();
const estChargee = (nom) => etat.chargees.some((c) => c.nom === nom || c.nom === nom + ":latest");
let minuterieCharge = null;
async function majCharge() {
  try {
    const r = await api("ia/chargees");
    etat.chargees = r.chargees || [];
    etat.enChargement = new Set((r.en_cours || []).filter((n) => !estChargee(n)));
  } catch { etat.chargees = []; }
  $$('[data-s="charge"]').forEach((b) => {
    const nom = b.closest(".chat")?.querySelector('[data-s="modele"]')?.value || "";
    const m = etat.modeles.find((x) => x.nom === nom);
    const c = etat.chargees.find((x) => x.nom === nom || x.nom === nom + ":latest");
    b.hidden = !m || !!m.nuage;
    b.className = "charge-ia " + (c ? "prete" : etat.enChargement.has(nom) ? "charge" : "dort");
    b.textContent = c ? "⚡ prête" + (c.gpu_pct >= 90 ? " · GPU" : c.gpu_pct > 0 ? " · GPU " + c.gpu_pct + " %" : " · CPU")
      : etat.enChargement.has(nom) ? "⏳ chargement…" : "💤 charger";
    b.title = c ? "En mémoire : réponses rapides. " + (c.gpu_pct >= 90 ? "Elle roule sur la carte graphique." : c.gpu_pct > 0 ? "En partie sur la carte graphique." : "Elle roule sur le processeur (plus lent).")
      : "Clique pour charger l'IA en mémoire : la première réponse sera rapide.";
  });
  if ($("#v-ia")?.classList.contains("actif")) rendreChargeesIA();
  return etat.enChargement.size;
}
function suivreCharge() {
  clearInterval(minuterieCharge);
  minuterieCharge = setInterval(async () => { if (!(await majCharge())) { clearInterval(minuterieCharge); minuterieCharge = null; } }, 2000);
}
function rendreChargeesIA() {
  $$("[data-charger]").forEach((b) => {
    const nom = b.dataset.charger, c = etat.chargees.find((x) => x.nom === nom);
    b.textContent = c ? "Décharger" : etat.enChargement.has(nom) ? "⏳ Chargement…" : "⚡ Charger";
    b.disabled = etat.enChargement.has(nom);
    b.classList.toggle("prim", !c);
    const info = b.closest(".modele")?.querySelector("[data-memoire]");
    if (info) info.innerHTML = c ? `<span class="tag vert">⚡ en mémoire · ${c.gpu_pct >= 90 ? "GPU" : c.gpu_pct > 0 ? "GPU " + c.gpu_pct + " %" : "CPU"}</span>` : "";
  });
}

/* ------------------------------------------------------------ composant de chat */
function creerChat(conteneur, mode) {
  const id = "c" + mode;
  conteneur.innerHTML = `
    <div class="options">
      <select class="champ moteur" data-s="moteur" aria-label="Moteur d'IA" title="Où roulent les IA" style="width:auto">
        <option value="ollama">IA de la clé</option>
        <option value="ollama_pc">IA du PC</option>
        <option value="openai">LM Studio / OpenAI</option>
      </select>
      <select class="champ" data-s="modele" aria-label="Modèle d'IA"></select>
      <button class="charge-ia" data-s="charge" type="button" title="Charger l'IA en mémoire pour des réponses rapides">…</button>
      ${mode === "codex" ? `<select class="champ" data-s="plan" aria-label="Mode du Codex" title="Codex direct : écrit le code tout de suite. Plan à valider : fait un plan, puis tu cliques Accepter pour qu'il écrive." style="width:auto">
        <option value="direct">⚡ Codex direct</option>
        <option value="valider">📋 Plan à valider</option>
      </select>` : `<select class="champ" data-s="plan" aria-label="Façon de travailler" title="Plan : l'IA réfléchit d'abord à un plan" style="width:auto">
        <option value="direct">Direct</option>
        <option value="auto">Plan puis code (sans attendre)</option>
        <option value="valider">Plan à valider</option>
      </select>`}
      ${mode === "codex" ? `<label class="interrupteur" title="Le Codex crée et modifie les fichiers du projet ouvert"><input type="checkbox" data-o="ecriture"><span class="rail"></span>${ic("dossier")}Écrire les fichiers</label>
      <label class="interrupteur" title="Agent : travaille seul jusqu'au bout, teste son code avec des commandes (tu autorises chaque commande)"><input type="checkbox" data-o="agent"><span class="rail"></span>${ic("terminal")}Agent</label>
      <label class="interrupteur turbo" title="Turbo : agent jusqu'à 40 étapes, IA gardée en mémoire, plus de contexte. Les commandes de test (python fichier.py, pytest, node, npm test, git status) se lancent sans demander ; les autres demandent toujours."><input type="checkbox" data-o="turbo"><span class="rail"></span>⚡ Turbo</label>` : ""}
      <label class="interrupteur" title="L'IA peut chercher sur Internet si elle en a besoin"><input type="checkbox" data-o="internet"><span class="rail"></span>${ic("globe")}Internet</label>
      ${mode === "codex" ? "" : `<label class="interrupteur" title="L'IA répond avec un schéma animé (exportable en vidéo)"><input type="checkbox" data-o="schema"><span class="rail"></span>${ic("studio")}Schéma</label>`}
      <label class="interrupteur" title="Utiliser la mémoire de la clé"><input type="checkbox" data-o="memoire"><span class="rail"></span>${ic("memoire")}Mémoire</label>
      <span class="espace"></span>
      <button class="btn petit" data-o="historique" aria-expanded="false">🕘 Historique</button>
      <button class="btn petit" data-o="vider">${mode === "codex" ? "Nouvelle session" : "Nouvelle jasette"}</button>
    </div>
    <div class="historique" hidden>
      <div class="hist-tete">
        <input class="champ" type="search" placeholder="Chercher dans toutes les conversations…" aria-label="Chercher dans l'historique">
        <button class="btn-ic petit" data-h="fermer" aria-label="Fermer l'historique">✕</button>
      </div>
      <div class="hist-liste" role="list"></div>
    </div>
    <div class="fil" aria-live="polite"></div>
    <div class="composer">
      <div class="apercus"></div>
      <div class="saisie">
        <input type="file" accept="image/*" multiple hidden id="${id}-img">
        <button class="btn-ic" data-o="joindre" aria-label="Joindre une image">${ic("image")}</button>
        <textarea rows="1" placeholder="${mode === "codex" ? "Demande au Codex… (colle une capture d'écran avec Ctrl+V)" : "Jase avec ton IA locale… (Ctrl+V pour coller une image)"}" aria-label="Message"></textarea>
        <button class="btn prim" data-o="envoyer" aria-label="Envoyer">${ic("envoyer")}</button>
      </div>
    </div>`;
  const sel = $('[data-s="modele"]', conteneur), fil = $(".fil", conteneur), zone = $("textarea", conteneur);
  const choixMoteur = $('[data-s="moteur"]', conteneur);
  choixMoteur.addEventListener("change", async () => {
    try {
      const p = await api("parametres", { moteur: choixMoteur.value });
      Object.assign(etat.params, p);
      await rafraichirEtat();
      toast(choixMoteur.value === "ollama_pc" ? "IA du PC (Ollama installé sur Windows)" : choixMoteur.value === "ollama" ? "IA de la clé USB-C" : "Moteur LM Studio / OpenAI");
    } catch (e) { toast(e.message); }
  });
  const apercus = $(".apercus", conteneur), entreeImg = $(`#${id}-img`, conteneur);
  const btEnvoyer = $('[data-o="envoyer"]', conteneur);
  const cases = Object.fromEntries($$("input[data-o]", conteneur).map((c) => [c.dataset.o, c]));
  // Le Codex n'a pas de plan ni de schema : ca se fait dans la Jasette
  const choixPlan = $('[data-s="plan"]', conteneur) || { value: "direct", addEventListener() {} };
  const cleStock = "tilabo-" + mode;
  let hist = [], images = [], controleur = null, sessionId = null;
  const nouvelId = () => Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 8);
  async function sauverSession() {
    if (!hist.length) return;
    sessionId = sessionId || nouvelId();
    const premier = hist.find((m) => m.role === "user");
    try {
      await api("conversations/sauver", {
        id: sessionId, mode, modele: sel.value, projet: mode === "codex" ? etat.projet : "",
        titre: (premier?.content || "Conversation").replace(/^Réalise ce plan[^\n]*\n+/, "").slice(0, 80),
        messages: hist,
      });
    } catch { /* l'historique ne doit jamais bloquer le chat */ }
  }

  // ---- historique : retrouver et rouvrir les conversations
  const panHist = $(".historique", conteneur), listeHist = $(".hist-liste", conteneur), chercheHist = $('input[type="search"]', panHist);
  const btHist = $('[data-o="historique"]', conteneur);
  let minuteurHist = null;
  const dateCourte = (t) => new Date(t * 1000).toLocaleString("fr-CA", { dateStyle: "medium", timeStyle: "short" });
  async function remplirHistorique() {
    let liste = [];
    try { liste = await api("conversations?mode=" + mode + "&q=" + encodeURIComponent(chercheHist.value)); } catch {}
    listeHist.innerHTML = liste.map((c) => `
      <div class="hist-item ${c.id === sessionId ? "actif" : ""}" role="listitem">
        <button class="hist-ouvrir" data-id="${esc(c.id)}"><b>${esc(c.titre)}</b>
          <small>${dateCourte(c.date)} · ${c.nb} messages${c.projet ? " · " + esc(c.projet) : ""}${c.modele ? " · " + esc(c.modele) : ""}</small>
          ${c.extrait ? `<small class="hist-extrait">… ${esc(c.extrait)} …</small>` : ""}</button>
        <button class="btn-ic petit" data-sup="${esc(c.id)}" aria-label="Supprimer cette conversation">🗑</button>
      </div>`).join("") || `<p class="vide">${chercheHist.value ? "Rien trouvé." : "Aucune conversation encore."}</p>`;
    $$(".hist-ouvrir", listeHist).forEach((b) => (b.onclick = () => rouvrir(b.dataset.id)));
    $$("[data-sup]", listeHist).forEach((b) => (b.onclick = async () => {
      if (!confirm("Supprimer cette conversation ? (elle va dans donnees\\corbeille)")) return;
      await api("conversations/supprimer", { id: b.dataset.sup }).catch((e) => toast(e.message));
      if (b.dataset.sup === sessionId) sessionId = null;
      remplirHistorique();
    }));
  }
  function basculerHistorique(ouvrir = panHist.hidden) {
    panHist.hidden = !ouvrir;
    btHist.setAttribute("aria-expanded", String(ouvrir));
    if (ouvrir) { remplirHistorique(); chercheHist.focus(); }
  }
  btHist.onclick = () => basculerHistorique();
  $('[data-h="fermer"]', panHist).onclick = () => basculerHistorique(false);
  chercheHist.addEventListener("input", () => { clearTimeout(minuteurHist); minuteurHist = setTimeout(remplirHistorique, 250); });
  async function rouvrir(id) {
    if (controleur) controleur.abort();
    let c;
    try { c = await api("conversations/lire", { id }); } catch (e) { return toast(e.message); }
    sessionId = c.id;
    hist = c.messages.map((m) => ({ role: m.role, content: m.content, images: m.images || [] }));
    fil.innerHTML = "";
    for (const m of hist) {
      if (m.role === "user") { bulleUser(m.content, m.images || []); continue; }
      const d = document.createElement("div");
      d.className = "msg assistant";
      d.innerHTML = `<div class="qui">${esc(c.modele || "")}</div><div class="contenu"></div>`;
      $(".contenu", d).innerHTML = rendreMarkdown(m.content);
      fil.appendChild(d);
      if (window.TiSchemas && mode !== "codex") TiSchemas.activer($(".contenu", d)).catch(() => {});
    }
    if (mode === "codex" && c.projet && c.projet !== etat.projet) { await choisirProjet(c.projet).catch(() => {}); rendreCodexProjets?.(); }
    // Codex : si cette vieille conversation contient du code, proposer de l'enregistrer dans le projet
    if (mode === "codex" && c.projet && hist.some((m) => m.role === "assistant" && (m.content || "").includes("```"))) {
      proposerRecuperation(c.projet, hist.filter((m) => m.role === "assistant").map((m) => m.content || ""));
    }
    fil.scrollTop = fil.scrollHeight;
    basculerHistorique(false);
    toast("Conversation rouverte : " + (c.titre || ""));
  }
  function proposerRecuperation(projet, textes) {
    const bar = document.createElement("div");
    bar.className = "plan-actions recup";
    bar.innerHTML = `<span>💾 Cette conversation contient du code. L'enregistrer dans les fichiers du projet « ${esc(projet)} » ?</span>
      <button class="btn prim petit">Récupérer les fichiers</button><button class="btn petit">Non</button>`;
    const [ok, non] = $$("button", bar);
    non.onclick = () => bar.remove();
    ok.onclick = async () => {
      ok.disabled = true; ok.textContent = "…";
      try {
        const r = await api("projets/recuperer", { projet, textes });
        bar.remove();
        if (!r.fichiers.length) return toast("Aucun fichier complet à récupérer (que des extraits avec « … »)");
        r.fichiers.forEach((f) => carteFichier(fil, { chemin: f.chemin, action: f.action, plus: f.plus, moins: f.moins }));
        apresModifs(new Set(r.fichiers.map((f) => f.chemin)));
        toast("✅ " + r.fichiers.length + " fichier(s) enregistré(s) dans « " + projet + " »");
      } catch (e) { ok.disabled = false; ok.textContent = "Récupérer les fichiers"; toast(e.message); }
    };
    fil.prepend(bar);
  }

  cases.internet.checked = true;
  if (cases.ecriture) cases.ecriture.checked = true;
  try {
    const s = JSON.parse(localStorage.getItem(cleStock + "-opts2") || "{}");
    choixPlan.value = ["direct", "auto", "valider"].includes(s.plan) ? s.plan : "direct";
    cases.internet.checked = s.internet ?? true;
    cases.memoire.checked = !!s.memoire;
    if (cases.ecriture) cases.ecriture.checked = s.ecriture ?? true;
    if (cases.agent) cases.agent.checked = !!s.agent;
    if (cases.turbo) cases.turbo.checked = !!s.turbo;
  } catch { /* stockage indisponible : valeurs par defaut */ }
  const garderOptions = () => {
    try {
      localStorage.setItem(cleStock + "-opts2", JSON.stringify({ plan: choixPlan.value, internet: cases.internet.checked,
        memoire: cases.memoire.checked, ecriture: cases.ecriture?.checked, agent: cases.agent?.checked, turbo: cases.turbo?.checked }));
    } catch {}
  };
  [...Object.values(cases), choixPlan].forEach((c) => c.addEventListener("change", garderOptions));

  function majModeles() {
    const tel = etat.plateforme === "android";
    choixMoteur.querySelector('[value="ollama_pc"]').hidden = tel;
    choixMoteur.querySelector('[value="ollama"]').textContent = tel ? "IA du téléphone" : "IA de la clé";
    choixMoteur.value = tel && etat.params.moteur === "ollama_pc" ? "ollama" : (etat.params.moteur || "ollama");
    const cle = mode === "codex" ? "modele_codex" : "modele_jasette";
    const choisi = etat.params[cle];
    sel.innerHTML = etat.modeles.length
      ? (() => {
          const opt = (m) => `<option value="${esc(m.nom)}" ${m.nom === choisi ? "selected" : ""}>${esc(m.nom)}</option>`;
          const locales = etat.modeles.filter((m) => !m.nuage && !/embed/.test(m.nom));
          const nuage = etat.modeles.filter((m) => m.nuage);
          return (locales.length ? `<optgroup label="IA locales (hors-ligne)">${locales.map(opt).join("")}</optgroup>` : "") +
            (nuage.length ? `<optgroup label="Claude (Internet, clé API)">${nuage.map(opt).join("")}</optgroup>` : "");
        })()
      : `<option value="">${etat.params.moteur === "ollama_pc" ? "IA du PC introuvable (Ollama est-il ouvert ?)" : "Aucune IA — va dans IA locales"}</option>`;
    if (sel.value && sel.value !== choisi) api("parametres", { [cle]: sel.value }).then((p) => Object.assign(etat.params, p)).catch(() => {});
  }
  const prechauffer = () => {
    const m = etat.modeles.find((x) => x.nom === sel.value);
    if (m && !m.nuage) api("ia/prechauffer", { nom: m.nom }).catch(() => {});
  };
  sel.addEventListener("change", prechauffer);
  const btCharge = $('[data-s="charge"]', conteneur);
  btCharge.onclick = () => {
    const m = etat.modeles.find((x) => x.nom === sel.value);
    if (!m || m.nuage) return;
    if (estChargee(m.nom)) return toast(m.nom + " est déjà en mémoire : les réponses sont rapides ⚡");
    api("ia/prechauffer", { nom: m.nom }).then(() => { etat.enChargement.add(m.nom); majCharge(); suivreCharge(); }).catch((e) => toast(e.message));
  };
  sel.addEventListener("change", () => setTimeout(() => { etat.enChargement.add(sel.value); majCharge(); suivreCharge(); }, 50));
  sel.addEventListener("change", () => {
    const cle = mode === "codex" ? "modele_codex" : "modele_jasette";
    api("parametres", { [cle]: sel.value }).then((p) => Object.assign(etat.params, p)).catch((e) => toast(e.message));
  });

  function majApercus() {
    apercus.innerHTML = images.map((src, i) => `<div class="apercu"><img src="${src}" alt="Image jointe ${i + 1}"><button aria-label="Retirer l'image" data-i="${i}">✕</button></div>`).join("");
  }
  apercus.addEventListener("click", (e) => {
    const b = e.target.closest("button[data-i]");
    if (b) { images.splice(+b.dataset.i, 1); majApercus(); }
  });
  async function ajouterImages(fichiers) {
    for (const f of fichiers) {
      if (!f.type.startsWith("image/")) continue;
      try { images.push(await reduireImage(f)); } catch { toast("Image illisible"); }
    }
    majApercus();
  }
  $('[data-o="joindre"]', conteneur).onclick = () => entreeImg.click();
  entreeImg.onchange = () => { ajouterImages(entreeImg.files); entreeImg.value = ""; };
  zone.addEventListener("paste", (e) => {
    const f = [...e.clipboardData.items].filter((x) => x.type.startsWith("image/")).map((x) => x.getAsFile());
    if (f.length) { e.preventDefault(); ajouterImages(f); }
  });
  conteneur.addEventListener("dragover", (e) => { e.preventDefault(); conteneur.classList.add("depot"); });
  conteneur.addEventListener("dragleave", (e) => { if (!conteneur.contains(e.relatedTarget)) conteneur.classList.remove("depot"); });
  conteneur.addEventListener("drop", (e) => { e.preventDefault(); conteneur.classList.remove("depot"); ajouterImages(e.dataTransfer.files); });

  zone.addEventListener("input", () => { zone.style.height = "auto"; zone.style.height = Math.min(zone.scrollHeight, 200) + "px"; });
  zone.addEventListener("keydown", (e) => { if (e.key === "Enter" && !e.shiftKey) { e.preventDefault(); envoyer(); } });
  btEnvoyer.onclick = () => (controleur ? controleur.abort() : envoyer());
  $('[data-o="vider"]', conteneur).onclick = () => { if (controleur) controleur.abort(); hist = []; sessionId = null; fil.innerHTML = ""; accueil(); };

  function accueil() {
    fil.innerHTML = `<div class="vide">${mode === "codex"
      ? "Choisis un projet en haut. Le Codex écrit le code directement dans tes fichiers et te le montre ici.<br>Chaque changement peut être annulé. Pour les plans et les schémas, va dans la Jasette."
      : "Jase avec ton IA locale : idées, plans et schémas animés (exportables en vidéo).<br>Joins une image pour qu'elle la regarde (modèle « vision »)."}</div>`;
  }
  accueil();

  function bulleUser(texte, imgs) {
    $(".vide", fil)?.remove();
    const d = document.createElement("div");
    d.className = "msg user";
    d.innerHTML = (imgs.length ? `<div class="img-msg">${imgs.map((s) => `<img src="${s}" alt="Image envoyée">`).join("")}</div>` : "") + esc(texte);
    fil.appendChild(d);
  }

  async function envoyer(texteForce, planForce) {
    const texte = (texteForce ?? zone.value).trim();
    if (!texte && !images.length) return;
    if (!sel.value) return toast("Choisis ou télécharge une IA dans « IA locales ».");
    const imgs = texteForce ? [] : images.slice();
    bulleUser(texte || "(image)", imgs);
    hist.push({ role: "user", content: texte || "Regarde cette image.", images: imgs });
    if (!texteForce) { zone.value = ""; zone.style.height = "auto"; images = []; majApercus(); }
    const facon = choixPlan.value;
    const plan = planForce ?? (facon === "direct" ? null : "planifier");
    if (mode === "codex" && cases.ecriture?.checked && !etat.projet && !texteForce)
      toast("Choisis un projet en haut du Codex pour qu'il puisse écrire dans tes fichiers.");

    const d = document.createElement("div");
    d.className = "msg assistant";
    const auCentre = false; // Codex et Jasette : meme interface, tout dans la conversation
    d.innerHTML = `<div class="qui">${esc(sel.value)}${plan === "planifier" ? ' <span class="tag or">plan</span>' : ""}</div><div class="etapes"></div>` +
      (auCentre ? `<div class="renvoi">${ic("bureau")}Réponse écrite au centre</div>` : `<div class="contenu curseur"></div>`);
    fil.appendChild(d);
    const contenu = auCentre ? ouvrirSortieCentre(sel.value, plan) : $(".contenu", d), etapes = $(".etapes", d);
    let reponse = "";
    const modifies = new Set();
    const sources = []; // pages web consultees par l'IA
    const toujours = { actif: false }; // « ne plus demander » pour cette demande seulement
    const zoneDefile = auCentre ? $("#sc-corps") : fil;
    const ecrivain = machineAEcrire(contenu, () => { if (pres) zoneDefile.scrollTop = zoneDefile.scrollHeight; });
    let pres = true;
    const surDefilement = () => { pres = zoneDefile.scrollHeight - zoneDefile.scrollTop - zoneDefile.clientHeight < 80; };
    zoneDefile.addEventListener("scroll", surDefilement);
    controleur = new AbortController();
    btEnvoyer.innerHTML = ic("stop");
    btEnvoyer.setAttribute("aria-label", "Arrêter");
    const coller = () => { fil.scrollTop = fil.scrollHeight; };
    try {
      const r = await api("jasette", {
        mode, modele: sel.value, messages: hist, plan,
        internet: cases.internet.checked, memoire: cases.memoire.checked, schema: !!cases.schema?.checked,
        schema_actuel: mode === "jasette" && window.TiArtefact ? TiArtefact.actuel() : null,
        images_ids: mode === "jasette" && window.TiArtefact && imgs.length ? imgs.map((x) => TiArtefact.enregistrerImage(x)) : null,
        fichier: mode === "codex" ? etat.fichier : null, projet: etat.projet,
        ecriture: mode === "codex" && (!!cases.ecriture?.checked || !!cases.agent?.checked || !!cases.turbo?.checked),
        agent: mode === "codex" && (!!cases.agent?.checked || !!cases.turbo?.checked),
        turbo: mode === "codex" && !!cases.turbo?.checked,
      }, { flux: true, signal: controleur.signal });
      await lireFlux(r, (o) => {
        if (o.texte) { reponse += o.texte; ecrivain.ajouter(o.texte); }
        if (o.outil) etapes.insertAdjacentHTML("beforeend", `<div class="etape">${ic(/^(Lit|Regarde|Ecrit|Modifie)/.test(o.outil) ? "dossier" : "globe")}${esc(o.outil)}</div>`);
        if (o.fichier) { carteFichier(etapes, o.fichier); modifies.add(o.fichier.chemin); montrerAuCentre(o.fichier); }
        if (o.autorisation) carteAutorisation(etapes, o.autorisation, toujours);
        if (o.commande) sortieCommande(o.commande);
        if (o.liens) o.liens.forEach((l) => {
          const lien = typeof l === "string" ? { url: l, titre: l } : l;
          if (!/^https?:\/\//i.test(lien.url)) return;
          const deja = sources.find((x) => x.url === lien.url);
          if (deja) { deja.lu = deja.lu || lien.lu; return; }
          sources.push(lien);
        });
        if (o.sources) etapes.insertAdjacentHTML("beforeend", `<div class="etape">${ic("memoire")}Mémoire : ${esc(o.sources.join(", "))}</div>`);
        if (o.erreur) ecrivain.erreur(o.erreur);
        if (o.jetons_s) $(".qui", d).insertAdjacentHTML("beforeend", `<span>· ${o.jetons_s} jetons/s</span>`);
        coller();
      });
    } catch (e) {
      if (e.name !== "AbortError") { await ecrivain.terminer(); contenu.insertAdjacentHTML("beforeend", `<p style="color:var(--red)">${esc(e.message)}</p>`); }
    } finally {
      await ecrivain.terminer();
      if (window.TiSchemas && mode !== "codex") TiSchemas.activer(contenu).catch(() => {});
      zoneDefile.removeEventListener("scroll", surDefilement);
      contenu.classList.remove("curseur");
      if (auCentre) $("#sc-etat").textContent = "";
      controleur = null;
      btEnvoyer.innerHTML = ic("envoyer");
      btEnvoyer.setAttribute("aria-label", "Envoyer");
    }
    if (reponse) hist.push({ role: "assistant", content: reponse });
    if (sources.length) carteSources(d, sources);
    sauverSession();
    if (modifies.size) apresModifs(modifies);
    if (reponse && plan === "planifier" && facon === "auto" && !planForce) {
      // Plan puis code, sans attendre de validation
      coller();
      return envoyer("Parfait, vas-y : réalise ce plan maintenant.", "executer");
    }
    if (reponse && plan === "planifier") {
      const a = document.createElement("div");
      a.className = "plan-actions";
      const btnPrincipal = mode === "codex" ? "✅ Accepter le plan" : "Envoyer au Codex";
      a.innerHTML = `<button class="btn prim petit">${btnPrincipal}</button><button class="btn petit">🧐 Avis sur le plan</button><button class="btn petit">Corriger le plan</button>`;
      const [ok, avis, corriger] = $$("button", a);
      avis.onclick = () => {
        avis.disabled = true;
        envoyer("Avant que je valide, donne-moi ton avis HONNÊTE sur ce plan, comme un développeur senior qui le relit :\n" +
          "1. ✅ Ce qui est juste\n2. ⚠️ Ce qui cloche ou est dépassé (outils, modèles, étapes inutiles)\n" +
          "3. ❓ Ce qui manque\n4. Verdict clair : « Valide-le » ou « Ne le valide pas tel quel », puis le plan corrigé en quelques étapes.\n" +
          "Ne sois pas complaisant : si le plan est mauvais, dis-le.\n\nLe plan :\n" + reponse, "planifier");
      };
      if (mode === "codex") {
        // Dans le Codex : on accepte et il écrit les fichiers tout de suite (plan=executer, le code est enregistré)
        ok.onclick = () => { a.remove(); envoyer("Le plan est bon. Réalise-le maintenant et écris les fichiers du projet.", "executer"); };
      } else {
        // Dans la Jasette : le plan se fait ici, le code part au Codex
        ok.onclick = () => { a.remove(); allerA("codex"); codex.envoyer("Réalise ce plan dans le projet :\n\n" + reponse); };
      }
      corriger.onclick = () => { a.remove(); zone.placeholder = "Dis ce qu'il faut changer dans le plan…"; zone.focus(); };
      d.appendChild(a);
    }
    coller();
  }

  return { majModeles, envoyer: (t) => envoyer(t), zone, prechauffer };
}

/* ------------------------------------------------------------ agent : commandes autorisees une par une */
function carteAutorisation(zone, a, toujours) {
  const repondre = (ok) => api("agent/autoriser", { id: a.id, ok }).catch((e) => toast(e.message));
  if (toujours.actif) {
    zone.insertAdjacentHTML("beforeend", `<div class="etape">${ic("terminal")}Lance : <code>${esc(a.commande)}</code></div>`);
    repondre(true);
    return;
  }
  const el = document.createElement("div");
  el.className = "carte-autorisation";
  el.innerHTML = `<div>${ic("terminal")}<b>L'agent veut lancer</b></div><pre>${esc(a.commande)}</pre>
    <label class="interrupteur" style="font-size:12px"><input type="checkbox"><span class="rail"></span>Ne plus demander pour cette demande</label>
    <div class="ligne"><button class="btn petit prim" data-ok>Autoriser</button><button class="btn petit danger" data-non>Refuser</button></div>`;
  const fini = (ok) => {
    toujours.actif = ok && $("input", el).checked;
    el.innerHTML = `<div class="etape">${ic("terminal")}${ok ? "Autorisé" : "Refusé"} : <code>${esc(a.commande)}</code></div>`;
    repondre(ok);
  };
  $("[data-ok]", el).onclick = () => fini(true);
  $("[data-non]", el).onclick = () => fini(false);
  zone.appendChild(el);
  $("[data-ok]", el).focus();
}
function sortieCommande(c) {
  const out = $("#sortie-terminal");
  if (c.refusee) { out.textContent += `\n[agent] commande refusée : ${c.commande}\n`; }
  else out.textContent += `\n[agent] ${etat.projet || ""}> ${c.commande}\n${c.sortie}${c.code ? `\n[code ${c.code}]` : ""}\n`;
  out.scrollTop = out.scrollHeight;
  if (etat.projet) choisirProjetSilencieux().catch(() => {});
}

/* ------------------------------------------------------------ sources web cliquables */
function carteSources(apres, sources) {
  const domaine = (u) => { try { return new URL(u).hostname.replace(/^www\./, ""); } catch { return ""; } };
  const el = document.createElement("div");
  el.className = "carte-sources";
  el.innerHTML = `<div class="cs-tete">🔗 Sources consultées sur le web (${sources.length})</div>` + sources.map((l) => `
    <a class="cs-lien" href="${esc(l.url)}" target="_blank" rel="noopener noreferrer">
      <span aria-hidden="true">🌐</span>
      <span><b>${esc(l.titre || l.url)}</b>${l.lu ? ' <span class="tag vert">lue par l\'IA</span>' : ""}<small>${esc(domaine(l.url))}${l.extrait ? " — " + esc(l.extrait) : ""}</small></span>
    </a>`).join("");
  apres.appendChild(el);
}

/* ------------------------------------------------------------ envoyer un projet sur GitHub */
async function envoyerSurGitHub(projet) {
  if (!projet) return toast("Choisis d'abord un projet.");
  if (!etat.params.github_utilisateur) { toast("Connecte d'abord ton jeton GitHub."); return allerA("github"); }
  if (etat.modifie) await sauver(true);
  const depot = prompt(`Envoyer « ${projet} » sur GitHub (${etat.params.github_utilisateur}).\nNom du dépôt :`, projet);
  if (!depot) return;
  const prive = confirm("Dépôt PRIVÉ ?\n\nOK = privé (seulement toi)\nAnnuler = public (tout le monde)");
  const message = prompt("Message du commit (optionnel) :", "") || null;
  toast("Envoi sur GitHub…");
  try {
    const r = await api("github/envoyer", { projet, depot, prive, message }, { flux: true });
    await lireFlux(r, (o) => {
      if (o.erreur) throw new Error(o.erreur);
      if (o.etape) toast(o.etape + "…");
      if (o.fini) {
        toast((o.cree ? "Dépôt créé et projet envoyé ! " : "Projet envoyé ! ") + o.url);
        window.open(o.url, "_blank", "noopener");
      }
    });
  } catch (e) { toast("GitHub : " + e.message); }
}

/* ------------------------------------------------------------ projet du Codex */
async function rendreCodexProjets() {
  await chargerListeProjets();
  const sel = $("#codex-projet");
  sel.innerHTML = `<option value="">— choisis un projet —</option>` +
    etat.projets.map((p) => `<option ${p.nom === etat.projet ? "selected" : ""}>${esc(p.nom)}</option>`).join("");
  sel.onchange = async () => {
    if (!sel.value) return;
    await choisirProjet(sel.value);
    $("#codex-contexte").textContent = etat.fichier ? "fichier ouvert : " + etat.fichier.split("/").slice(1).join("/") : "aucun fichier ouvert";
  };
}

/* ------------------------------------------------------------ sortie du Codex au centre */
function ouvrirSortieCentre(modele, plan) {
  if (!$("#v-projets").classList.contains("actif")) {
    $$(".vue").forEach((v) => v.classList.toggle("actif", v.id === "v-projets"));
    $$(".nav").forEach((n) => n.classList.toggle("actif", n.dataset.vue === "projets"));
    chargerProjets();
  }
  const s = $("#sortie-codex");
  s.hidden = false;
  s.classList.remove("reduite");
  $("#sc-reduire").textContent = "Réduire";
  $("#sc-reduire").setAttribute("aria-expanded", "true");
  $("#sc-modele").textContent = "· " + modele + (plan === "planifier" ? " · plan" : "");
  $("#sc-etat").textContent = "écrit…";
  const corps = $("#sc-corps");
  corps.innerHTML = "";
  const c = document.createElement("div");
  c.className = "msg assistant contenu curseur";
  corps.appendChild(c);
  return c;
}
$("#sc-reduire").onclick = () => {
  const r = $("#sortie-codex").classList.toggle("reduite");
  $("#sc-reduire").textContent = r ? "Agrandir" : "Réduire";
  $("#sc-reduire").setAttribute("aria-expanded", String(!r));
};

/* ------------------------------------------------------------ changements de fichiers du Codex */
function carteFichier(zone, f) {
  const el = document.createElement("div");
  el.className = "carte-fichier" + (f.action === "cree" ? " cree" : "");
  const verbe = f.action === "cree" ? "Créé" : "Modifié";
  el.innerHTML = `${ic("dossier")}<span><b>${verbe}</b> <code>${esc(f.chemin)}</code> <span class="plus">+${f.plus}</span> <span class="moins">−${f.moins}</span> <span class="tag vert" title="Écrit et vérifié sur la clé">✓ enregistré</span></span>
    <span class="espace" style="flex:1"></span>
    <button class="btn petit" data-f-vue>Fichier complet</button>
    <button class="btn petit" data-f-ouvrir>Ouvrir</button><button class="btn petit danger" data-f-annuler>Annuler</button>`;
  const apercu = document.createElement("div");
  apercu.className = "bloc-code apercu-fichier";
  const lang = TiCouleurs.langueDe(f.chemin);
  // Vue « changements » : lignes ajoutees en vert, supprimees en rouge, avec leurs numeros
  const vueDiff = () => {
    if (!f.diff || !f.diff.length) return null;
    return `<div class="bc-tete">changements · <span class="plus">+${f.plus} ajoutées</span> · <span class="moins">−${f.moins} supprimées</span></div>` +
      `<pre class="diff">${f.diff.map((x) => x.t === "…"
        ? `<span class="d-saut">${esc(x.l || "⋯")}</span>`
        : `<span class="d-l d-${x.t === "+" ? "plus" : x.t === "-" ? "moins" : "eq"}"><i>${x.t === "-" ? "" : x.n}</i><i>${x.t === "-" ? x.n : ""}</i><b>${x.t === " " ? " " : x.t}</b><code class="d-code">${TiCouleurs.colorer(x.l || "", lang) || " "}</code></span>`).join("")}</pre>`;
  };
  let complet = null;
  const montrer = async (enComplet) => {
    if (enComplet || !vueDiff()) {
      if (complet === null) complet = await api("fichier?chemin=" + encodeURIComponent(f.chemin)).then((r) => r.contenu).catch(() => "");
      apercu.innerHTML = `<pre><code>${TiCouleurs.colorer(complet, lang)}</code></pre>`;
    } else apercu.innerHTML = vueDiff();
    $("[data-f-vue]", el).textContent = enComplet ? "Changements" : "Fichier complet";
  };
  let vueComplete = false;
  $("[data-f-vue]", el).onclick = () => { vueComplete = !vueComplete; montrer(vueComplete); };
  montrer(false);
  setTimeout(() => el.after(apercu), 0);
  $("[data-f-ouvrir]", el).onclick = async () => {
    allerA("projets");
    const projet = f.chemin.split("/")[0];
    if (etat.projet !== projet) await choisirProjet(projet);
    ouvrirFichier(f.chemin);
  };
  $("[data-f-annuler]", el).onclick = async (e) => {
    const b = e.currentTarget;
    b.disabled = true;
    try {
      await api("fichier/annuler", { sauvegarde: f.sauvegarde });
      el.classList.add("annule");
      apercu.remove();
      b.textContent = "Annulé";
      apresModifs(new Set([f.chemin]));
    } catch (err) { toast(err.message); b.disabled = false; }
  };
  zone.appendChild(el);
}
// Petite animation quand le Codex passe a un autre fichier
async function animerChangement(f) {
  const zone = $(".zone-edit");
  const badge = document.createElement("div");
  badge.className = "badge-fichier" + (f.action === "cree" ? " cree" : "");
  badge.innerHTML = `${ic(f.action === "cree" ? "plus" : "dossier")}<span>${f.action === "cree" ? "Nouveau fichier" : "Le Codex passe à"}</span><code>${esc(f.chemin.split("/").slice(1).join("/"))}</code>`;
  zone.appendChild(badge);
  zone.classList.remove("change-fichier");
  void zone.offsetWidth; // relance l'animation
  zone.classList.add("change-fichier");
  const noeud = $$("#arbre [data-f]").find((b) => b.dataset.f === f.chemin);
  if (noeud) {
    noeud.scrollIntoView({ block: "nearest" });
    const cls = f.action === "cree" ? "neuf" : "pulse";
    noeud.classList.add(cls);
    setTimeout(() => noeud.classList.remove(cls), cls === "neuf" ? 2600 : 1600);
  }
  await new Promise((ok) => setTimeout(ok, window.matchMedia("(prefers-reduced-motion: reduce)").matches ? 0 : 650));
  setTimeout(() => badge.remove(), 1400);
}

// Le code s'ecrit au centre, dans l'editeur, sous les yeux de l'utilisateur
let fileCentre = Promise.resolve();
function montrerAuCentre(f) {
  fileCentre = fileCentre.then(() => ecrireAuCentre(f)).catch(() => {});
}
async function ecrireAuCentre(f) {
  if (etat.modifie) return; // ne jamais ecraser du travail non enregistre
  const projet = f.chemin.split("/")[0];
  // Depuis la vue Codex, on reste dans la conversation : l'apercu du code est dans la carte du fichier
  if (!$("#v-projets").classList.contains("actif")) return;
  if (etat.projet !== projet) await choisirProjet(projet);
  else await choisirProjetSilencieux().catch(() => {});
  const changeDeFichier = etat.fichier !== f.chemin;
  const ancien = changeDeFichier ? "" : ed.value;
  const r = await api("fichier?chemin=" + encodeURIComponent(f.chemin));
  if (changeDeFichier) await animerChangement(f);
  etat.fichier = f.chemin;
  $("#nom-fichier").textContent = f.chemin;
  $("#codex-contexte").textContent = "fichier ouvert : " + f.chemin.split("/").slice(1).join("/");
  ["#bt-expliquer", "#bt-dossier", "#bt-sauver"].forEach((x) => ($(x).disabled = false));
  $$("#arbre [data-f]").forEach((b) => b.classList.toggle("actif", b.dataset.f === f.chemin));
  const nouveau = r.contenu;
  // premiere ligne qui change : on ecrit a partir de la, comme si l'IA tapait
  let debut = 0;
  const lim = Math.min(ancien.length, nouveau.length);
  while (debut < lim && ancien[debut] === nouveau[debut]) debut++;
  debut = nouveau.lastIndexOf("\n", debut - 1) + 1;
  const reste = nouveau.length - debut;
  const pasParImage = Math.max(3, Math.ceil(reste / 90)); // environ 1,5 s, meme pour un gros fichier
  ed.readOnly = true;
  ed.classList.add("ecrit-par-ia");
  const zoneEd = $(".zone-edit");
  zoneEd.style.setProperty("--avance", "0%");
  zoneEd.classList.add("ecrit");
  let i = debut;
  await new Promise((ok) => {
    function tape() {
      i = Math.min(nouveau.length, i + pasParImage);
      ed.value = nouveau.slice(0, i);
      majNumeros();
      ed.scrollTop = ed.scrollHeight;
      synchroDefilement();
      zoneEd.style.setProperty("--avance", (reste ? Math.round(((i - debut) / reste) * 100) : 100) + "%");
      if (i < nouveau.length && !document.hidden) requestAnimationFrame(tape);
      else { ed.value = nouveau; majNumeros(); ok(); }
    }
    tape();
  });
  ed.readOnly = false;
  ed.classList.remove("ecrit-par-ia");
  zoneEd.style.setProperty("--avance", "100%");
  setTimeout(() => zoneEd.classList.remove("ecrit"), 450);
  marquerModifie(false);
  // place le curseur au debut du changement
  ed.focus({ preventScroll: true });
  ed.setSelectionRange(debut, debut);
  const ligne = nouveau.slice(0, debut).split("\n").length - 1;
  ed.scrollTop = Math.max(0, ligne * 21.6 - 80);
  synchroDefilement();
}

async function apresModifs(chemins) {
  await fileCentre;
  if (etat.projet) await choisirProjetSilencieux().catch(() => {});
  chargerListeProjets();
  if (etat.fichier && chemins.has(etat.fichier)) {
    if (etat.modifie) toast("Le Codex a changé " + etat.fichier + " — tes modifications non enregistrées sont gardées dans l'éditeur.");
    else {
      const r = await api("fichier?chemin=" + encodeURIComponent(etat.fichier)).catch(() => null);
      if (r && r.contenu !== ed.value) { ed.value = r.contenu; majNumeros(); }
    }
  }
}

/* ------------------------------------------------------------ navigation */
const mobile = () => window.matchMedia("(max-width: 860px)").matches;
function fermerSurMobile(panneau, bouton) {
  if (!mobile()) return;
  $(panneau).classList.add("ferme");
  $(bouton).classList.remove("actif");
  $(bouton).setAttribute("aria-pressed", "false");
}
function allerA(vue) {
  fermerSurMobile("#gauche", "#bt-gauche");
  $$(".vue").forEach((v) => v.classList.toggle("actif", v.id === "v-" + vue));
  $$(".nav").forEach((n) => n.classList.toggle("actif", n.dataset.vue === vue));
  if (vue === "projets") chargerProjets();
  if (vue === "ia") rendreIA();
  if (vue === "github") rendreGitHub();
  if (vue === "memoire") rendreMemoire();
  if (vue === "studio") rendreStudio();
  if (vue === "parametres") rendreParametres();
  if (vue === "images") rendreImages();
  if (vue === "devoirs") rendreDevoirs();
  if (vue === "nouvelles" && window.TiNouvelles) TiNouvelles.rendre();
  if (vue === "agenda" && window.TiAgenda) TiAgenda.rendre();
  if (vue === "bureau") rendreBureau();
  if (vue === "codex") { rendreCodexProjets(); setTimeout(() => codex.zone.focus(), 30); }
  let plein = false; try { plein = vue === "codex" && localStorage.getItem("tilabo-codex-plein") === "1"; } catch (_) {}
  if ($("#bt-codex-plein")) codexPlein(plein);
}
$$(".nav").forEach((n) => (n.onclick = () => allerA(n.dataset.vue)));
// Changer de bureau : on retient le choix sur la cle, puis on ouvre le bureau facon Windows
if ($("#bt-bureau-windows")) $("#bt-bureau-windows").onclick = async (e) => {
  e.preventDefault();
  try { await api("parametres", { interface: "bureau" }); } catch (_) {}
  location.href = "/bureau";
};
$$("[data-aller]").forEach((n) => (n.onclick = () => allerA(n.dataset.aller)));
function bascule(bt, panneau) {
  bt.onclick = () => {
    const ferme = $(panneau).classList.toggle("ferme");
    if (!ferme && mobile()) {
      /* un seul panneau maintenant */
    }
    bt.classList.toggle("actif", !ferme);
    bt.setAttribute("aria-pressed", String(!ferme));
  };
}
bascule($("#bt-gauche"), "#gauche");


/* ------------------------------------------------------------ état général */
async function rafraichirEtat() {
  try {
    const e = await api("etat");
    etat.params = e.params;
    etat.git = e.git;
    etat.ollama = e.ollama;
    $("#libre").textContent = e.libre_go + " Go";
    $("#jauge").style.width = Math.max(4, 100 - Math.min(100, e.libre_go)) + "%";
    $("#racine").textContent = e.racine;
    etat.plateforme = e.plateforme;
    $("#dossier-modeles").textContent = e.plateforme === "android" ? "ton Ollama Termux (les mêmes IA que ton Codex local)" : e.racine + "\\ia\\modeles";
    $("#p-cle").textContent = e.plateforme === "android" ? "Téléphone · Termux" : "Clé " + e.lecteur;
    const pia = $("#p-ia");
    pia.className = "pastille " + (e.ollama ? "ok" : "off");
    pia.innerHTML = `<i class="pt"></i>IA : ${e.ollama ? "en marche" : "arrêtée"}`;
    const pnet = $("#p-net");
    pnet.className = "pastille " + (e.params.internet ? "warn" : "ok");
    pnet.innerHTML = `<i class="pt"></i>${e.params.internet ? "Internet permis" : "100 % hors-ligne"}`;
    appliquerPolices();
  } catch (e) {
    toast("Le Ti-Labo ne répond pas : " + e.message);
  }
  try {
    etat.modeles = await api("ia/modeles");
  } catch { etat.modeles = []; }
  $("#nb-ia").textContent = etat.modeles.length || "";
  jasette.majModeles();
  codex.majModeles();
}

function appliquerPolices() {
  const p = etat.params;
  document.documentElement.style.setProperty("--ui", `"${p.police || "Segoe UI"}", system-ui, sans-serif`);
  document.documentElement.style.setProperty("--mono", `"${p.police_code || "Cascadia Mono"}", Consolas, monospace`);
}

/* ------------------------------------------------------------ bureau */
async function rendreBureau() {
  await chargerListeProjets();
  const carte = (titre, valeur, detail, ok) => `<div class="carte"><div class="ligne" style="justify-content:space-between"><span class="sous" style="margin:0">${titre}</span><span class="tag ${ok ? "vert" : "or"}">${ok ? "prêt" : "à faire"}</span></div><div style="font-size:20px;font-weight:700;margin:8px 0 2px">${valeur}</div><small class="sous">${detail}</small></div>`;
  $("#bureau-etat").innerHTML =
    carte("Moteur d'IA", etat.ollama ? "Ollama " + etat.ollama : "Arrêté", etat.ollama ? "Les IA de la clé sont prêtes" : "Va dans IA locales › Démarrer", !!etat.ollama) +
    carte("IA installées", etat.modeles.length, etat.modeles.length ? etat.modeles.slice(0, 3).map((m) => m.nom).join(", ") : "Télécharge ta première IA", etat.modeles.length > 0) +
    carte("Git", etat.git ? "Présent" : "Absent", etat.git ? "Clonage GitHub disponible" : "Relance Installer-Ti-Labo.bat", etat.git) +
    carte("Projets", etat.projets.length, "dans le dossier projets de la clé", etat.projets.length > 0);
  $("#bureau-recents").innerHTML = etat.projets.slice(0, 6).map((p) => `
    <button class="carte" style="text-align:left;color:inherit" data-ouvrir="${esc(p.nom)}">
      <div class="ligne" style="justify-content:space-between"><b>${esc(p.nom)}</b><span class="tag or">${esc(p.langage)}</span></div>
      <p class="sous" style="font-size:12px">${esc(p.resume || p.fichiers + " fichiers")}</p></button>`).join("") || `<p class="vide">Aucun projet encore. Crée-en un dans Mes projets ou clone-le depuis GitHub.</p>`;
  $$("[data-ouvrir]").forEach((b) => (b.onclick = () => { allerA("projets"); choisirProjet(b.dataset.ouvrir); }));
}

/* ------------------------------------------------------------ projets + éditeur */
async function chargerListeProjets() {
  try { etat.projets = await api("projets"); } catch { etat.projets = []; }
  $("#nb-projets").textContent = etat.projets.length || "";
}

async function chargerProjets() {
  await chargerListeProjets();
  $("#liste-projets").innerHTML = etat.projets.map((p) => `
    <button class="proj ${p.nom === etat.projet ? "actif" : ""}" data-p="${esc(p.nom)}">${esc(p.nom)}
      <small>${esc(p.langage)} · ${p.fichiers} fichiers${p.git ? " · git" : ""}</small></button>`).join("") || `<p class="vide">Aucun projet</p>`;
  $$("#liste-projets .proj").forEach((b) => (b.onclick = () => choisirProjet(b.dataset.p)));
}

async function choisirProjet(nom) {
  if (etat.modifie && !confirm("Le fichier n'est pas enregistré. Changer quand même ?")) return;
  etat.projet = nom;
  $("#invite-terminal").textContent = nom + ">";
  $$("#liste-projets .proj").forEach((b) => b.classList.toggle("actif", b.dataset.p === nom));
  try {
    const noeuds = await api("arbre?projet=" + encodeURIComponent(nom));
    $("#arbre").innerHTML = noeuds.map((n) => `<button class="noeud ${n.type}" style="padding-left:${12 + n.prof * 14}px" ${n.type === "fichier" ? `data-f="${esc(n.chemin)}"` : "tabindex='-1'"}>${n.type === "dossier" ? "├ " + esc(n.nom) + "/" : "└ " + esc(n.nom)}</button>`).join("");
    $$("#arbre [data-f]").forEach((b) => (b.onclick = () => ouvrirFichier(b.dataset.f)));
    const principal = noeuds.find((n) => /^(main|app|index)\.(py|js|html)$/i.test(n.nom)) || noeuds.find((n) => n.type === "fichier");
    if (principal && !etat.fichier?.startsWith(nom + "/")) ouvrirFichier(principal.chemin);
  } catch (e) { toast(e.message); }
}

async function ouvrirFichier(chemin) {
  if (etat.modifie && !confirm("Le fichier n'est pas enregistré. Ouvrir l'autre quand même ?")) return;
  try {
    const f = await api("fichier?chemin=" + encodeURIComponent(chemin));
    etat.fichier = chemin;
    $("#editeur").value = f.contenu;
    $("#nom-fichier").textContent = chemin;
    $("#codex-contexte").textContent = "fichier ouvert : " + chemin.split("/").slice(1).join("/");
    if ($("#codex-projet")) $("#codex-projet").value = chemin.split("/")[0];
    ["#bt-expliquer", "#bt-dossier", "#bt-sauver"].forEach((s) => ($(s).disabled = false));
    $$("#arbre [data-f]").forEach((b) => b.classList.toggle("actif", b.dataset.f === chemin));
    marquerModifie(false);
    majNumeros();
  } catch (e) { toast(e.message); }
}

function marquerModifie(v) { etat.modifie = v; $("#etat-fichier").hidden = !v; }
function majNumeros() {
  const n = $("#editeur").value.split("\n").length;
  $("#numeros").textContent = Array.from({ length: n }, (_, i) => i + 1).join("\n");
  colorerEditeur();
}
let colorationEnAttente = false;
function colorerEditeur() {
  if (colorationEnAttente) return;
  colorationEnAttente = true;
  requestAnimationFrame(() => {
    colorationEnAttente = false;
    const t = $("#editeur").value;
    const lang = TiCouleurs.langueDe(etat.fichier || "");
    // au-dela de ~400 Ko, on garde le texte simple pour rester rapide
    $("#coloration-code").innerHTML = (t.length > 400000 ? t.replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c])) : TiCouleurs.colorer(t, lang)) + "\n";
    synchroDefilement();
  });
}
function synchroDefilement() {
  const t = $("#editeur"), pre = $("#coloration");
  pre.scrollTop = t.scrollTop;
  pre.scrollLeft = t.scrollLeft;
  $("#numeros").scrollTop = t.scrollTop;
}
const ed = $("#editeur");
let minuteurAuto = null;
ed.addEventListener("input", () => {
  marquerModifie(true);
  majNumeros();
  // Enregistrement automatique sur la cle 1,2 s apres la derniere frappe
  clearTimeout(minuteurAuto);
  minuteurAuto = setTimeout(() => { if (etat.modifie && etat.fichier && !ed.readOnly) sauver(true); }, 1200);
});
ed.addEventListener("scroll", synchroDefilement);
ed.addEventListener("keydown", (e) => {
  if (e.key === "Tab") {
    e.preventDefault();
    const s = ed.selectionStart;
    ed.setRangeText("    ", s, ed.selectionEnd, "end");
    marquerModifie(true);
    majNumeros();
  }
});
async function sauver(auto = false) {
  if (!etat.fichier) return;
  const chemin = etat.fichier, contenu = ed.value;
  try {
    await api("fichier", { chemin, contenu });
    if (etat.fichier === chemin && ed.value === contenu) marquerModifie(false);
    const t = $("#etat-auto");
    if (t) { t.textContent = "✓ enregistré " + new Date().toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit", second: "2-digit" }); t.hidden = false; }
    if (!auto) toast("Enregistré sur la clé");
  } catch (e) { toast("Pas enregistré : " + e.message); }
}
document.addEventListener("keydown", (e) => { if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === "s") { e.preventDefault(); sauver(); } });
$("#bt-sauver").onclick = sauver;
$("#bt-dossier").onclick = () => api("ouvrir-dossier", { chemin: etat.fichier }).catch((e) => toast(e.message));
$("#bt-expliquer").onclick = () => { allerA("codex"); codex.envoyer("Explique-moi ce fichier simplement : à quoi il sert, comment il fonctionne, et ce qui pourrait être amélioré."); };
$("#bt-nouveau").onclick = async () => {
  const nom = $("#nouveau-nom").value.trim();
  if (!nom) return $("#nouveau-nom").focus();
  try { await api("projet/nouveau", { nom }); $("#nouveau-nom").value = ""; await chargerProjets(); choisirProjet(nom); }
  catch (e) { toast(e.message); }
};

const historiqueCmd = [];
let posCmd = 0;
$("#form-terminal").onsubmit = async (e) => {
  e.preventDefault();
  const champ = $("#commande"), cmd = champ.value.trim();
  if (!cmd) return;
  historiqueCmd.push(cmd); posCmd = historiqueCmd.length;
  champ.value = "";
  const out = $("#sortie-terminal");
  if (cmd === "cls" || cmd === "clear") { out.textContent = ""; return; }
  out.textContent += `\n${etat.projet || "projets"}> ${cmd}\n`;
  champ.disabled = true;
  try {
    const r = await api("terminal", { projet: etat.projet, commande: cmd });
    out.textContent += r.sortie + (r.code ? `\n[code ${r.code}]` : "");
  } catch (err) { out.textContent += err.message; }
  champ.disabled = false; champ.focus();
  out.scrollTop = out.scrollHeight;
  if (etat.projet) choisirProjetSilencieux();
};
$("#commande").addEventListener("keydown", (e) => {
  if (e.key === "ArrowUp" && posCmd > 0) { e.target.value = historiqueCmd[--posCmd]; e.preventDefault(); }
  if (e.key === "ArrowDown") { posCmd = Math.min(historiqueCmd.length, posCmd + 1); e.target.value = historiqueCmd[posCmd] || ""; }
});
async function choisirProjetSilencieux() {
  const garde = etat.fichier;
  const noeuds = await api("arbre?projet=" + encodeURIComponent(etat.projet)).catch(() => null);
  if (!noeuds) return;
  const connus = new Set($$("#arbre [data-f]").map((b) => b.dataset.f));
  $("#arbre").innerHTML = noeuds.map((n) => `<button class="noeud ${n.type} ${n.chemin === garde ? "actif" : ""}" style="padding-left:${12 + n.prof * 14}px" ${n.type === "fichier" ? `data-f="${esc(n.chemin)}"` : "tabindex='-1'"}>${n.type === "dossier" ? "├ " + esc(n.nom) + "/" : "└ " + esc(n.nom)}</button>`).join("");
  $$("#arbre [data-f]").forEach((b) => (b.onclick = () => ouvrirFichier(b.dataset.f)));
  // petit effet discret sur les fichiers qui viennent d'apparaitre
  if (connus.size) $$("#arbre [data-f]").forEach((b) => {
    if (!connus.has(b.dataset.f)) { b.classList.add("neuf"); setTimeout(() => b.classList.remove("neuf"), 2600); }
  });
}

/* ------------------------------------------------------------ IA locales */
/* ------------------------------------------------------------ téléphone <-> clé */
async function rendreTransfert() {
  let st;
  try { st = await api("stockage"); } catch { return; }
  const box = $("#transfert");
  const utile = st.cles.length || st.tel_gguf.length || st.plateforme === "android";
  box.hidden = !utile;
  if (!utile) return;
  const cle = st.cles.find((c) => !c.erreur);
  $("#tr-espace").textContent = `Téléphone : ${st.tel_libre_go ?? "?"} Go libres` + (cle ? ` · Clé : ${cle.libre_go ?? "?"} Go libres` : "");
  let avis = "";
  if (st.plateforme === "android" && !st.dossier_codexlocal_ok)
    avis += `<p class="vide" style="text-align:left;padding:10px 14px">Pour voir les IA de CodexLocal : dans CodexLocal, active le <b>partage des modèles</b> (dossier CodexLocal/models), puis dans Termux lance <code>termux-setup-storage</code>.</p>`;
  if (!st.cles.length && st.plateforme === "android") avis += `<p class="vide" style="text-align:left;padding:10px 14px">Branche ta clé USB-C pour déplacer des IA dessus.</p>`;
  st.cles.filter((c) => c.erreur).forEach((c) => (avis += `<p class="vide" style="text-align:left;padding:10px 14px;color:var(--red)">${esc(c.erreur)}</p>`));
  if (cle?.limite) avis += `<p class="vide" style="text-align:left;padding:10px 14px">Android ne permet pas d'écrire à la racine de la clé : les IA vont dans <code>${esc(cle.ia)}</code>. Si tu désinstalles Termux, Android efface ce dossier.</p>`;
  $("#tr-avis").innerHTML = avis;
  const titreTel = $("#tr-titre-tel");
  if (titreTel) titreTel.textContent = st.plateforme === "android" ? "Sur le téléphone" : "Fichiers .gguf (CodexLocal) sur la clé";
  const barre = (id) => `<div class="progression" id="${id}"><i></i></div>`;
  let k = 0;
  $("#tr-tel").innerHTML = st.tel_gguf.map((g) => {
    const id = "tg" + k++;
    const actif = st.gguf_actif === g.chemin;
    const pc = st.plateforme !== "android";
    const nomImporte = g.nom.replace(/\.gguf$/i, "").toLowerCase().replace(/[^a-z0-9._-]+/g, "-").replace(/^[-._]+|[-._]+$/g, "").slice(0, 60) + ":gguf";
    const importe = pc && (st.importes || []).includes(nomImporte);
    const bouton = pc
      ? (importe ? `<span class="tag vert">prêt : ${esc(nomImporte)}</span>` : `<button class="btn petit prim" data-gg-util="${esc(g.chemin)}">Activer dans le Ti-Labo</button>`)
      : `<button class="btn petit" data-gg-util="${esc(g.chemin)}">${actif ? "Arrêter" : "Utiliser"}</button>`;
    return `<div class="modele"><div class="info"><b>${esc(g.nom)}</b> <span class="tag">gguf</span>${actif ? ' <span class="tag vert">en marche</span>' : ""}
      <small>${g.taille_go} Go · ${esc(g.dossier.includes("CodexLocal") ? "CodexLocal" : pc ? "sur la clé (ia\\gguf)" : g.dossier)}</small>${barre(id)}</div>
      <button class="btn petit" data-ren='${esc(JSON.stringify({ type: "gguf", chemin: g.chemin, actuel: g.nom.replace(/\.gguf$/i, "") }))}'>Renommer</button>
      ${bouton}
      ${cle ? `<button class="btn petit prim" data-tr='${esc(JSON.stringify({ type: "gguf", chemin: g.chemin, sens: "vers_cle", cle: cle.chemin, p: id }))}'>Déplacer sur la clé</button>` : ""}</div>`;
  }).join("") + etat.modeles.filter(() => cle && st.plateforme === "android").map((m) => {
    const id = "to" + k++;
    return `<div class="modele"><div class="info"><b>${esc(m.nom)}</b> <span class="tag">ollama</span><small>${m.taille_go ?? "?"} Go</small>${barre(id)}</div>
      <button class="btn petit prim" data-tr='${esc(JSON.stringify({ type: "ollama", nom: m.nom, sens: "vers_cle", cle: cle.chemin, p: id }))}'>Déplacer sur la clé</button></div>`;
  }).join("") || `<p class="vide">Aucune IA trouvée sur le téléphone.</p>`;
  $("#tr-cles").innerHTML = st.cles.filter((c) => !c.erreur).map((c) => `
    <div style="padding:10px 14px 0;border-top:1px solid var(--border)"><h2 style="font-size:14px">Sur la clé · ${esc(c.chemin)}</h2></div>
    ${[...c.gguf.map((g) => ({ ...g, type: "gguf" })), ...c.ollama.map((m) => ({ ...m, type: "ollama" }))].map((x) => {
      const id = "tc" + k++;
      const op = x.type === "gguf" ? { type: "gguf", chemin: x.chemin, sens: "vers_tel", cle: c.chemin, p: id } : { type: "ollama", nom: x.nom, sens: "vers_tel", cle: c.chemin, p: id };
      return `<div class="modele"><div class="info"><b>${esc(x.nom)}</b> <span class="tag">${x.type}</span><small>${x.taille_go} Go</small>${barre(id)}</div>
        ${x.type === "gguf" ? `<button class="btn petit" data-ren='${esc(JSON.stringify({ type: "gguf", chemin: x.chemin, actuel: x.nom.replace(/\.gguf$/i, "") }))}'>Renommer</button><button class="btn petit" data-gg-util="${esc(x.chemin)}">Utiliser (lent)</button>` : ""}
        <button class="btn petit" data-tr='${esc(JSON.stringify({ ...op, garder: true }))}'>Copier sur le tél</button>
        <button class="btn petit prim" data-tr='${esc(JSON.stringify(op))}'>Ramener sur le tél</button></div>`;
    }).join("") || `<p class="vide">Aucune IA sur la clé.</p>`}`).join("");

  $$("[data-tr]", box).forEach((b) => (b.onclick = () => transferer(JSON.parse(b.dataset.tr), b)));
  brancherRenommer(box);
  $$("[data-gg-util]", box).forEach((b) => (b.onclick = async () => {
    const actif = b.textContent === "Arrêter";
    b.disabled = true; b.textContent = actif ? "Arrêt…" : etat.plateforme === "android" ? "Démarrage…" : "Import… (quelques minutes)";
    try {
      if (actif) { await api("gguf/arreter", {}); toast("Retour au moteur Ollama"); }
      else { const r = await api("gguf/demarrer", { chemin: b.dataset.ggUtil }); toast(r.modele + " est prêt dans la Jasette et le Codex"); }
    } catch (e) { toast(e.message); }
    await rafraichirEtat(); rendreIA();
  }));
}

function brancherRenommer(zone) {
  $$("[data-ren]", zone).forEach((b) => (b.onclick = async () => {
    const op = JSON.parse(b.dataset.ren);
    const aide = op.type === "gguf" ? "Nouveau nom du fichier (sans .gguf) :" : "Nouveau nom de l'IA (minuscules, ex. mon-codex ou mon-codex:v2) :";
    const nouveau = prompt(aide, op.actuel);
    if (!nouveau || nouveau.trim() === op.actuel) return;
    b.disabled = true; b.textContent = "…";
    try { const r = await api("ia/renommer", { ...op, nouveau: nouveau.trim() }); toast("Renommé : " + r.nom); }
    catch (e) { toast(e.message); }
    await rafraichirEtat(); rendreIA();
  }));
}

async function transferer(op, bouton) {
  const verbe = op.sens === "vers_cle" ? (op.garder ? "Copier" : "Déplacer") + " sur la clé" : (op.garder ? "Copier" : "Ramener") + " sur le téléphone";
  if (!op.garder && !confirm(`${verbe} : ${op.nom || op.chemin.split("/").pop()} ?\nL'original sera effacé seulement après vérification de la copie.`)) return;
  const pr = $("#" + op.p);
  $$("[data-tr]").forEach((b) => (b.disabled = true));
  pr.style.display = "block";
  const texte = bouton.textContent;
  try {
    const r = await api("ia/deplacer", op, { flux: true });
    await lireFlux(r, (o) => {
      if (o.erreur) throw new Error(o.erreur);
      if (o.total) { $("i", pr).style.width = Math.round((o.fait / o.total) * 100) + "%"; bouton.textContent = Math.round((o.fait / o.total) * 100) + " %"; }
      if (o.etape) bouton.textContent = "…";
      if (o.message) toast(o.message);
    });
  } catch (e) { toast("Échec : " + e.message); }
  bouton.textContent = texte;
  await rafraichirEtat();
  rendreIA();
}

function rendreIA() {
  rendreTransfert();
  const installes = new Set(etat.modeles.map((m) => m.nom));
  const pris = (nom) => installes.has(nom) || installes.has(nom + ":latest");
  $("#ia-installees").innerHTML = etat.modeles.filter((m) => !m.nuage).map((m) => {
    const cat = CATALOGUE.find((c) => c.nom === m.nom || c.nom + ":latest" === m.nom);
    return `<div class="modele"><div class="info"><b>${esc(m.nom)}</b>${(cat?.tags || []).map((t) => ` <span class="tag ${t === "vision" ? "bleu" : t === "internet" ? "vert" : "or"}">${t}</span>`).join("")}
      <small>${m.taille_go ? m.taille_go + " Go" : ""} ${esc(m.params || "")} ${esc(m.famille || "")}</small></div>
      <button class="btn petit" data-ren='${esc(JSON.stringify({ type: "ollama", nom: m.nom, actuel: m.nom }))}'>Renommer</button>
      <span data-memoire></span>
      <button class="btn petit" data-charger="${esc(m.nom)}">⚡ Charger</button>
      <button class="btn petit" data-util="${esc(m.nom)}">Utiliser</button>
      <button class="btn petit danger" data-ret="${esc(m.nom)}">Retirer</button></div>`;
  }).join("") || `<p class="vide">${etat.ollama ? "Aucune IA sur la clé pour l'instant." : "Le moteur est arrêté. Clique « Démarrer le moteur »."}</p>`;
  const ligneCat = (c) => `
    <div class="modele"><div class="info"><b>${c.nom}</b>${c.tags.map((t) => ` <span class="tag ${t === "vision" ? "bleu" : t === "internet" ? "vert" : "or"}">${t === "top" ? "top 5" : t}</span>`).join("")}
      <small>${c.desc} · ${c.taille} Go</small><div class="progression" id="pr-${c.nom.replace(/[^\w]/g, "_")}"><i></i></div></div>
      <button class="btn petit ${pris(c.nom) ? "" : "prim"}" data-tel="${c.nom}" ${pris(c.nom) ? "disabled" : ""}>${pris(c.nom) ? "Installée" : "Télécharger"}</button></div>`;
  $("#ia-catalogue").innerHTML =
    `<div class="sous-titre">Top 5 pour coder</div>` + CATALOGUE.filter((c) => c.groupe === "code").map(ligneCat).join("") +
    `<div class="sous-titre">Jasette, vision et mémoire</div>` + CATALOGUE.filter((c) => c.groupe !== "code").map(ligneCat).join("");
  $$("[data-util]").forEach((b) => (b.onclick = async () => {
    await api("parametres", { modele_jasette: b.dataset.util, modele_codex: b.dataset.util }).then((p) => Object.assign(etat.params, p));
    jasette.majModeles(); codex.majModeles(); toast(b.dataset.util + " est maintenant utilisée par la Jasette et le Codex");
  }));
  $$("[data-ret]").forEach((b) => (b.onclick = async () => {
    if (!confirm("Retirer " + b.dataset.ret + " de la clé ?")) return;
    try { await api("ia/supprimer", { nom: b.dataset.ret }); await rafraichirEtat(); rendreIA(); } catch (e) { toast(e.message); }
  }));
  $$("[data-tel]").forEach((b) => (b.onclick = () => telecharger(b.dataset.tel)));
  $$("[data-charger]").forEach((b) => (b.onclick = async () => {
    const nom = b.dataset.charger;
    try {
      if (estChargee(nom)) { await api("ia/decharger", { nom }); toast(nom + " libère la mémoire"); }
      else { await api("ia/prechauffer", { nom }); etat.enChargement.add(nom); toast("Chargement de " + nom + " en mémoire…"); suivreCharge(); }
    } catch (e) { toast(e.message); }
    majCharge();
  }));
  majCharge();
  dessinerTels();
  brancherRenommer($("#ia-installees"));
}

/* ---- Téléchargements d'IA : ils roulent dans le serveur, la page ne fait que regarder.
   On peut changer de page ou recharger : ça continue, et la bulle en bas suit partout. */
const tels = {};          // nom -> état reçu du serveur
let suiviTel = null;
const idTel = (nom) => nom.replace(/[^\w]/g, "_");
const go = (o) => (o / 1e9).toFixed(1).replace(".", ",");

async function telecharger(nom) {
  try {
    const r = await api("ia/telecharger", { nom });
    majTels(r.telechargements);
    toast("Téléchargement de " + nom + " lancé — tu peux changer de page, il continue");
  } catch (e) { toast("Échec : " + e.message); }
}
async function annulerTel(nom) {
  try { majTels((await api("ia/annuler-telechargement", { nom })).telechargements); } catch (e) { toast(e.message); }
}
function majTels(liste) {
  const avant = { ...tels };
  Object.keys(tels).forEach((k) => delete tels[k]);
  (liste || []).forEach((t) => (tels[t.nom] = t));
  let fini = false;
  for (const t of Object.values(tels)) {
    const ancien = avant[t.nom];
    if (ancien && ancien.etat === "en_cours" && t.etat !== "en_cours") {
      fini = true;
      const n = t.titre || t.nom;
      if (t.etat === "fini") toast("✅ " + n + " est sur la clé !");
      else if (t.etat === "erreur") toast("❌ " + n + " : " + (t.erreur || "échec"));
      else toast(n + " : téléchargement annulé");
    }
  }
  dessinerTels();
  if (fini) rafraichirEtat().then(() => {
    if ($("#v-ia").classList.contains("actif")) rendreIA();
    if ($("#v-images").classList.contains("actif")) rendreImages();
  });
  const actif = Object.values(tels).some((t) => t.etat === "en_cours");
  if (actif && !suiviTel) suiviTel = setInterval(() => api("ia/telechargements", {}).then((r) => majTels(r.telechargements)).catch(() => {}), 1000);
  if (!actif && suiviTel) { clearInterval(suiviTel); suiviTel = null; }
}
function texteTel(t) {
  if (t.etat === "erreur") return "Erreur";
  if (t.etat === "fini") return "Installée";
  if (t.etat === "annule") return "Annulé";
  return t.total ? Math.round(t.pourcent) + " %" : (t.statut || "…").slice(0, 14);
}
function dessinerTels() {
  // 1) les lignes de la page IA locales (si elles sont affichées)
  for (const t of Object.values(tels)) {
    const pr = $("#pr-" + idTel(t.nom));
    if (pr) { pr.style.display = "block"; $("i", pr).style.width = (t.pourcent || 0) + "%"; }
    const b = $(`[data-tel="${CSS.escape(t.nom)}"]`);
    if (b && t.etat === "en_cours") { b.disabled = true; b.textContent = texteTel(t); }
  }
  // 2) la bulle, visible sur toutes les pages
  let bulle = $("#tel-bulle");
  if (!bulle) { bulle = document.createElement("div"); bulle.id = "tel-bulle"; document.body.appendChild(bulle); }
  const visibles = Object.values(tels).filter((t) => t.etat === "en_cours" || t.etat === "erreur");
  bulle.style.display = visibles.length ? "block" : "none";
  bulle.innerHTML = visibles.map((t) => `
    <div class="tel-ligne ${t.etat}">
      <div class="tel-haut"><b>⬇ ${esc(t.titre || t.nom)}</b>
        <span>${t.etat === "erreur" ? "❌ " + esc(t.erreur || "échec") : texteTel(t) + (t.total ? " · " + go(t.fait) + " / " + go(t.total) + " Go" : "")}</span>
        <button class="tel-x" data-telx="${esc(t.nom)}" title="${t.etat === "erreur" ? "Réessayer" : "Annuler"}">${t.etat === "erreur" ? "↻" : "✕"}</button></div>
      <div class="progression" style="display:block"><i style="width:${t.pourcent || 0}%"></i></div>
    </div>`).join("");
  $$("[data-telx]", bulle).forEach((b) => (b.onclick = (e) => {
    e.stopPropagation();
    const t = tels[b.dataset.telx];
    if (t && t.etat === "erreur") (t.nom.startsWith("img:") ? telechargerImage(t.nom.slice(4)) : telecharger(t.nom)); else annulerTel(b.dataset.telx);
  }));
  bulle.onclick = () => allerA("ia");
}
api("ia/telechargements", {}).then((r) => majTels(r.telechargements)).catch(() => {});
$("#bt-ia-autre").onclick = () => { const n = $("#ia-autre").value.trim(); if (n) { telecharger(n); $("#ia-autre").value = ""; } };
$("#bt-demarrer-ia").onclick = async () => {
  $("#bt-demarrer-ia").textContent = "Démarrage…";
  try { const r = await api("ia/demarrer", {}); toast(r.ok ? "Moteur démarré" : "Ollama introuvable sur la clé — relance Installer-Ti-Labo.bat"); }
  catch (e) { toast(e.message); }
  $("#bt-demarrer-ia").textContent = "Démarrer le moteur";
  await rafraichirEtat(); rendreIA();
};

/* ------------------------------------------------------------ GitHub */
async function rendreGitHub() {
  const u = etat.params.github_utilisateur;
  const box = $("#gh-connexion");
  if (u) {
    box.innerHTML = `<div class="ligne"><span>Connecté en tant que <b style="color:var(--accent)">${esc(u)}</b></span><span class="espace" style="flex:1"></span><button class="btn petit danger" id="bt-gh-off">Déconnecter</button></div>`;
    $("#bt-gh-off").onclick = async () => { await api("github/deconnecter", {}); await rafraichirEtat(); rendreGitHub(); };
    $("#gh-depots").innerHTML = `<p class="vide">Chargement…</p>`;
    try {
      const depots = await api("github/depots");
      $("#gh-depots").innerHTML = depots.map((d) => `<div class="modele"><div class="info"><b>${esc(d.nom)}</b> ${d.prive ? '<span class="tag or">privé</span>' : ""} <span class="tag">${esc(d.langage)}</span><small>${esc(d.description)}</small></div><button class="btn petit" data-cl="${esc(d.url)}">Cloner</button></div>`).join("") || `<p class="vide">Aucun dépôt</p>`;
      $$("[data-cl]").forEach((b) => (b.onclick = () => cloner(b.dataset.cl, b)));
    } catch (e) { $("#gh-depots").innerHTML = `<p class="vide">${esc(e.message)}</p>`; }
  } else {
    box.innerHTML = `<label class="lib" for="gh-jeton">Ton jeton GitHub (Settings › Developer settings › Fine-grained tokens) — droits : « Contents : lecture et écriture » + « Administration : écriture » pour créer des dépôts</label>
      <div class="ligne"><input class="champ" type="password" id="gh-jeton" placeholder="github_pat_…" style="flex:1" autocomplete="off"><button class="btn prim" id="bt-gh-on">Connecter</button></div>
      <p class="sous" style="font-size:12px">Le jeton reste sur la clé (donnees\\parametres.json). Si tu perds la clé, révoque-le sur GitHub.</p>`;
    $("#bt-gh-on").onclick = async () => {
      try { const r = await api("github/connecter", { jeton: $("#gh-jeton").value }); toast("Connecté : " + r.utilisateur); await rafraichirEtat(); rendreGitHub(); }
      catch (e) { toast(e.message); }
    };
    $("#gh-depots").innerHTML = `<p class="vide">Connecte-toi pour voir tes dépôts.</p>`;
  }
}
async function cloner(url, bouton) {
  const texte = bouton.textContent;
  bouton.disabled = true; bouton.textContent = "Clonage…";
  try { const r = await api("github/cloner", { url }); toast("« " + r.nom + " » est sur la clé"); await chargerListeProjets(); }
  catch (e) { toast(e.message); }
  bouton.disabled = false; bouton.textContent = texte;
}
$("#bt-cloner").onclick = () => cloner($("#gh-url").value, $("#bt-cloner"));

/* ------------------------------------------------------------ mémoire */
async function rendreMemoire() {
  $("#modele-memoire").textContent = etat.params.modele_memoire;
  await chargerListeProjets();
  $("#mem-projets").innerHTML = etat.projets.map((p) => `<div class="modele"><div class="info"><b>${esc(p.nom)}</b> ${p.indexe ? '<span class="tag vert">indexé</span>' : '<span class="tag">pas indexé</span>'}<small>${p.fichiers} fichiers</small></div><button class="btn petit" data-ix="${esc(p.nom)}">${p.indexe ? "Ré-indexer" : "Indexer"}</button></div>`).join("") || `<p class="vide">Aucun projet</p>`;
  $$("[data-ix]").forEach((b) => (b.onclick = async () => {
    b.disabled = true; b.textContent = "Indexation…";
    try { const r = await api("memoire/indexer", { projet: b.dataset.ix }); toast(r.morceaux + " morceaux mémorisés"); }
    catch (e) { toast(e.message.includes("not found") ? "Télécharge d'abord « nomic-embed-text » dans IA locales" : e.message); }
    rendreMemoire();
  }));
}
$("#bt-mem-q").onclick = async () => {
  const q = $("#mem-q").value.trim();
  if (!q) return;
  $("#mem-res").innerHTML = `<p class="vide">Recherche…</p>`;
  try {
    const r = await api("memoire/chercher", { question: q });
    $("#mem-res").innerHTML = r.map((x) => `<div class="bloc-code"><div class="bc-tete">${esc(x.fichier)}<span class="espace"></span>${x.score}</div><pre><code>${esc(x.texte.slice(0, 600))}</code></pre></div>`).join("") || `<p class="vide">Rien trouvé — indexe d'abord un projet.</p>`;
  } catch (e) { $("#mem-res").innerHTML = `<p class="vide">${esc(e.message)}</p>`; }
};

/* ------------------------------------------------------------ studio : schéma animé */
async function rendreStudio() {
  await chargerListeProjets();
  const sel = $("#studio-projet");
  const garde = sel.value || etat.projet || etat.projets[0]?.nom;
  sel.innerHTML = etat.projets.map((p) => `<option ${p.nom === garde ? "selected" : ""}>${esc(p.nom)}</option>`).join("");
  sel.onchange = dessinerSchema;
  if (!$("#studio-schema").hidden) dessinerSchema();
  rendreVitrine();
}
// onglets du Studio : la vitrine des applis ou le schéma
$$(".studio-onglet").forEach((b) => (b.onclick = () => {
  $$(".studio-onglet").forEach((x) => { x.classList.toggle("actif", x === b); x.setAttribute("aria-selected", String(x === b)); });
  $("#studio-applis").hidden = b.dataset.studio !== "applis";
  $("#studio-schema").hidden = b.dataset.studio !== "schema";
  if (b.dataset.studio === "schema") dessinerSchema();
}));
const urlAppli = (nom) => "/apercu/" + encodeURIComponent(window.TILABO_JETON || "") + "/" + encodeURIComponent(nom) + "/";
// La vitrine : chaque projet qui a une page index.html est une appli qu'on lance d'un clic
function rendreVitrine() {
  const V = $("#vitrine");
  const projets = [...etat.projets].sort((a, b) => (b.web === true) - (a.web === true) || b.modifie - a.modifie);
  if (!projets.length) { V.innerHTML = `<p class="sous">Aucun projet pour l'instant. Demande au Codex de te créer une appli, elle apparaîtra ici.</p>`; return; }
  V.innerHTML = "";
  projets.forEach((p) => {
    const c = document.createElement("article");
    c.className = "appli";
    const date = new Date(p.modifie * 1000).toLocaleDateString("fr-CA", { day: "numeric", month: "short" });
    c.innerHTML = (p.web
      ? `<div class="ecran" title="Lancer l'appli"><iframe loading="lazy" tabindex="-1" sandbox="allow-scripts" src="${urlAppli(p.nom)}" title=""></iframe><div class="jouer">▶</div></div>`
      : `<div class="ecran code" title="Pas de page web à lancer">${p.langage === "Python" ? "🐍" : "🧩"}</div>`) +
      `<div class="infos"><b>${esc(p.nom)}</b><p>${esc(p.resume || (p.web ? "Appli web" : "Projet " + (p.langage || "")))}</p>
        <span class="sous" style="font-size:12px">${esc(p.langage || "—")} · ${p.fichiers} fichiers · modifié le ${date}</span></div>
      <div class="actions">${p.web ? `<button class="btn petit prim" data-lancer>▶ Lancer</button>` : ""}<button class="btn petit" data-codex>🧑‍💻 Codex</button><button class="btn petit" data-ouvrir>📁 Fichiers</button></div>`;
    const lancer = () => lancerAppli(p.nom);
    if (p.web) { $(".ecran", c).onclick = lancer; $("[data-lancer]", c).onclick = lancer; }
    $("[data-codex]", c).onclick = async () => { await choisirProjet(p.nom).catch(() => {}); allerA("codex"); };
    $("[data-ouvrir]", c).onclick = async () => { await choisirProjet(p.nom).catch(() => {}); allerA("projets"); };
    V.appendChild(c);
  });
}
function lancerAppli(nom) {
  $("#lanceur-titre").textContent = "▶ " + nom;
  $("#lanceur-cadre").src = urlAppli(nom);
  $("#lanceur").hidden = false;
  $("#lanceur-recharger").onclick = () => { $("#lanceur-cadre").src = urlAppli(nom) + "?t=" + Date.now(); };
  $("#lanceur-onglet").onclick = () => window.open(urlAppli(nom), "_blank", "noopener");
  $("#lanceur-codex").onclick = async () => { fermerLanceur(); await choisirProjet(nom).catch(() => {}); allerA("codex"); };
}
function fermerLanceur() { $("#lanceur").hidden = true; $("#lanceur-cadre").src = "about:blank"; }
$("#lanceur-fermer").onclick = fermerLanceur;
addEventListener("keydown", (e) => { if (e.key === "Escape" && !$("#lanceur").hidden) fermerLanceur(); });
async function dessinerSchema() {
  const svg = $("#schema"), nom = $("#studio-projet").value;
  if (!nom) { svg.innerHTML = `<text x="40" y="60">Aucun projet</text>`; return; }
  const noeuds = (await api("arbre?projet=" + encodeURIComponent(nom)).catch(() => [])).slice(0, 80);
  const colX = [40, 300, 560, 820, 1080];
  const parCol = {};
  const pos = {};
  const racine = { chemin: nom, nom, prof: -1, type: "racine" };
  const tous = [racine, ...noeuds];
  tous.forEach((n) => {
    const c = Math.min(n.prof + 1, 4);
    parCol[c] = (parCol[c] || 0) + 1;
    pos[n.chemin] = { x: colX[c], y: 30 + (parCol[c] - 1) * 44, n };
  });
  const h = Math.max(520, ...Object.values(pos).map((p) => p.y + 60));
  svg.setAttribute("viewBox", `0 0 1320 ${h}`);
  svg.style.height = h + "px";
  let liens = "", boites = "";
  tous.forEach((n, i) => {
    const p = pos[n.chemin];
    if (n !== racine) {
      const parentChemin = n.chemin.split("/").slice(0, -1).join("/");
      const par = pos[parentChemin] || pos[nom];
      liens += `<path class="lien" d="M${par.x + 200} ${par.y + 15} C${par.x + 230} ${par.y + 15},${p.x - 30} ${p.y + 15},${p.x} ${p.y + 15}"/>`;
    }
    const cls = n.type === "racine" ? "n-r" : n.type === "dossier" ? "n-d" : "n-f";
    boites += `<g class="apparait" style="animation-delay:${Math.min(i * 30, 1500)}ms"><rect class="${cls}" x="${p.x}" y="${p.y}" width="200" height="30" rx="7"/><text x="${p.x + 12}" y="${p.y + 19}">${esc((n.type === "dossier" ? "▸ " : "") + n.nom).slice(0, 26)}</text></g>`;
  });
  svg.innerHTML = liens + boites;
}
$("#bt-codex-github").onclick = () => envoyerSurGitHub($("#codex-projet").value || etat.projet);
// Codex plein écran : le code s'écrit sur toute la page
function codexPlein(oui) {
  document.documentElement.classList.toggle("codex-plein", oui);
  $("#bt-codex-plein").setAttribute("aria-pressed", String(oui));
  $("#bt-codex-plein").textContent = oui ? "⤡ Quitter le plein écran" : "⛶ Plein écran";
  try { localStorage.setItem("tilabo-codex-plein", oui ? "1" : ""); } catch (_) {}
}
$("#bt-codex-plein").onclick = () => codexPlein(!document.documentElement.classList.contains("codex-plein"));
addEventListener("keydown", (e) => { if (e.key === "Escape" && document.documentElement.classList.contains("codex-plein")) codexPlein(false); });
$("#bt-projet-github").onclick = () => envoyerSurGitHub(etat.projet);
$("#bt-studio-codex").onclick = () => {
  const nom = $("#studio-projet").value;
  if (!nom) return;
  allerA("codex");
  codex.envoyer(`Explique-moi la structure du projet « ${nom} » : utilise l'outil chercher_mes_projets si tu peux, et décris le rôle de chaque partie.`);
};

/* ------------------------------------------------------------ images */
let infoImages = null;
const urlImage = (u) => u + (u.includes("?") ? "&" : "?") + "t=" + encodeURIComponent(window.TILABO_JETON);
const ACTIONS_IMG = {
  creer: { bouton: "Créer l'image", lib: "Décris l'image (l'anglais donne de meilleurs résultats)", ph: "ex. a cozy cabin by a lake in Quebec, autumn, sunset, detailed",
    aide: "Écris ce que tu veux voir : l'IA dessine une image toute neuve." },
  modifier: { bouton: "Modifier l'image", lib: "Qu'est-ce qu'on change ?", ph: "ex. change the sky to a sunset · add snow · make it a cartoon · remove the car",
    aide: "Choisis une image, puis donne un ordre : l'IA garde le reste et change ce que tu dis. (FLUX.2 Klein)" },
  zone: { bouton: "Retoucher la zone", lib: "Qu'est-ce qu'on met dans la zone peinte ?", ph: "ex. a red bicycle · empty grass · a smiling cat",
    aide: "Choisis une image, peins en orange la zone à changer, puis dis ce qu'il faut y mettre. Pour effacer un objet : décris le fond (« grass », « wall »…)." },
  retoucher: { bouton: "Redessiner l'image", lib: "Dans quel style ou avec quels changements ?", ph: "ex. oil painting, warm colors · anime style · more detailed",
    aide: "L'IA redessine toute l'image en gardant sa forme. Petite force = proche de l'original, grande force = plus de liberté." },
  agrandir: { bouton: "Agrandir ×4", lib: "", ph: "",
    aide: "Rend l'image 4 fois plus grande et plus nette (Real-ESRGAN). Idéal avant d'imprimer." },
};
let actionImg = "creer";
const imgSource = { data: null, nom: null, largeur: 0, hauteur: 0, masque: null, peint: false };

async function rendreImages() {
  try { infoImages = await api("images"); } catch (e) { toast(e.message); return; }
  const { moteur, modeles, catalogue, galerie } = infoImages;
  const avis = $("#img-avis");
  const msgs = [];
  if (!moteur) msgs.push(etat.plateforme === "android"
    ? "La création d'images marche sur le PC (Ti-Labo de la clé). Sur le téléphone, c'est trop lourd pour l'instant."
    : "Le moteur d'images n'est pas encore sur la clé : relance <b>Installer-Ti-Labo.bat</b> (étape 4/4).");
  if (!modeles.length) msgs.push("Télécharge une IA d'images plus bas (SD-Turbo est la plus rapide, FLUX.2 Klein sait aussi modifier).");
  avis.hidden = !msgs.length;
  avis.innerHTML = msgs.map((m) => `<p class="sous" style="margin:4px 0">${m}</p>`).join("");
  choisirActionImg(actionImg);
  $("#galerie").innerHTML = galerie.map((g) => `<div class="img-case">
      <a href="${esc(urlImage(g.url))}" target="_blank" rel="noopener"><img src="${esc(urlImage(g.url))}" alt="${esc(g.nom)}" loading="lazy"></a>
      <div class="img-actions"><button class="btn petit" data-imod="${esc(g.nom)}" title="Modifier cette image">✏️</button>
      <button class="btn petit" data-igr="${esc(g.nom)}" title="Agrandir ×4">🔍</button></div></div>`).join("") || `<p class="vide">Tes images apparaîtront ici.</p>`;
  brancherGalerie();
  const PEUT = { creer: "créer", modifier: "modifier par ordre", zone: "retoucher une zone", retoucher: "redessiner", agrandir: "agrandir" };
  $("#img-catalogue").innerHTML = Object.entries(catalogue).map(([nom, c]) => `
    <div class="modele"><div class="info"><b>${esc(c.titre)}</b> ${c.peut.map((p) => `<span class="tag ${p === "modifier" ? "bleu" : p === "zone" ? "vert" : "or"}">${PEUT[p]}</span>`).join(" ")}
      <small>${esc(c.desc)} · ${c.taille_go < 1 ? Math.round(c.taille_go * 1000) + " Mo" : c.taille_go + " Go"}</small>
      <div class="progression" id="pr-${idTel("img:" + nom)}"><i></i></div></div>
      <button class="btn petit ${c.installe ? "" : "prim"}" data-tel="img:${esc(nom)}" data-itel="${esc(nom)}" ${c.installe ? "disabled" : ""}>${c.installe ? "Sur la clé" : "Télécharger"}</button></div>`).join("");
  $$("[data-itel]").forEach((b) => (b.onclick = () => telechargerImage(b.dataset.itel)));
  dessinerTels();
}
async function telechargerImage(nom) {
  try {
    const r = await api("images/telecharger", { nom });
    majTels(r.telechargements);
    toast("Téléchargement lancé — tu peux changer de page, il continue");
  } catch (e) { toast("Échec : " + e.message); }
}
function choisirActionImg(act) {
  actionImg = act;
  const A = ACTIONS_IMG[act], modeles = infoImages?.modeles || [];
  $$(".img-onglet").forEach((o) => { o.classList.toggle("actif", o.dataset.act === act); o.setAttribute("aria-selected", String(o.dataset.act === act)); });
  $("#img-aide").textContent = A.aide;
  $("#bt-img").textContent = A.bouton;
  $("#img-texte-zone").hidden = act === "agrandir";
  $("#img-prompt-lib").textContent = A.lib;
  $("#img-prompt").placeholder = A.ph;
  $("#img-source-zone").hidden = act === "creer";
  $("#img-pinceau").hidden = act !== "zone";
  $("#img-format-zone").hidden = act !== "creer";
  $("#img-pas-zone").hidden = act === "agrandir";
  $("#img-force-zone").hidden = !(act === "retoucher" || act === "zone");
  $("#img-negatif-zone").hidden = act === "agrandir" || act === "modifier";
  if (act === "zone") $("#img-force").value = 1; else if (act === "retoucher" && +$("#img-force").value === 1) $("#img-force").value = 0.6;
  $("#img-force-val").textContent = Math.round($("#img-force").value * 100) + " %";
  const bons = modeles.filter((m) => (m.peut || ["creer"]).includes(act));
  const sel = $("#img-modele"), garde = sel.value;
  sel.innerHTML = bons.map((m) => `<option value="${esc(m.nom)}" ${m.nom === garde ? "selected" : ""}>${esc(m.titre)}</option>`).join("")
    || `<option value="">Aucune IA pour ça — télécharge-la plus bas</option>`;
  const choisi = () => bons.find((m) => m.nom === sel.value);
  sel.onchange = () => { const m = choisi(); if (m && m.pas) $("#img-pas").value = m.pas; };
  if (choisi()?.pas) $("#img-pas").value = choisi().pas;
  $("#bt-img").disabled = !infoImages?.moteur || !bons.length;
  dessinerSource();
}
$$(".img-onglet").forEach((o) => (o.onclick = () => choisirActionImg(o.dataset.act)));
$("#img-force").oninput = () => ($("#img-force-val").textContent = Math.round($("#img-force").value * 100) + " %");

function brancherGalerie() {
  $$("[data-imod]").forEach((b) => (b.onclick = () => chargerSourceUrl(b.dataset.imod, actionImg === "creer" || actionImg === "agrandir" ? "modifier" : actionImg)));
  $$("[data-igr]").forEach((b) => (b.onclick = () => chargerSourceUrl(b.dataset.igr, "agrandir")));
}
// Toute image (PC ou galerie) est convertie en PNG dans la page : le moteur reçoit toujours du PNG.
function chargerSource(src, nom, action) {
  const img = new Image();
  img.onload = () => {
    const c = document.createElement("canvas");
    c.width = img.naturalWidth; c.height = img.naturalHeight;
    c.getContext("2d").drawImage(img, 0, 0);
    Object.assign(imgSource, { data: c.toDataURL("image/png"), nom, largeur: c.width, hauteur: c.height, masque: null, peint: false });
    if (action) choisirActionImg(action); else dessinerSource();
    $("#v-images").scrollTo?.({ top: 0, behavior: "smooth" });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  img.onerror = () => toast("Impossible de lire cette image");
  img.src = src;
}
function chargerSourceUrl(nom, action) { chargerSource(urlImage("/images/" + encodeURIComponent(nom)), nom, action); }
$("#img-fichier").onchange = (e) => {
  const f = e.target.files[0]; if (!f) return;
  const r = new FileReader();
  r.onload = () => chargerSource(r.result, f.name);
  r.readAsDataURL(f);
  e.target.value = "";
};
function dessinerSource() {
  const z = $("#img-source");
  if (!imgSource.data) { z.innerHTML = `<p class="vide">Aucune image choisie</p>`; return; }
  z.innerHTML = `<div class="img-cadre"><img src="${imgSource.data}" alt="Image à modifier"><canvas id="img-masque" aria-label="Zone à retoucher"></canvas></div>
    <small class="sous">${esc(imgSource.nom || "")} · ${imgSource.largeur}×${imgSource.hauteur}</small>`;
  const cv = $("#img-masque");
  cv.width = imgSource.largeur; cv.height = imgSource.hauteur;
  cv.style.display = actionImg === "zone" ? "block" : "none";
  const ctx = cv.getContext("2d");
  if (imgSource.masque) { const m = new Image(); m.onload = () => ctx.drawImage(m, 0, 0); m.src = imgSource.masque; }
  let peint = false;
  const point = (e) => { const r = cv.getBoundingClientRect(); return [(e.clientX - r.left) * cv.width / r.width, (e.clientY - r.top) * cv.height / r.height, cv.width / r.width]; };
  const trace = (e) => {
    if (!peint) return;
    const [x, y, ech] = point(e);
    ctx.fillStyle = "rgba(255,122,26,.55)";
    ctx.beginPath(); ctx.arc(x, y, (+$("#img-taille-pinceau").value / 2) * ech, 0, Math.PI * 2); ctx.fill();
    imgSource.peint = true;
  };
  cv.onpointerdown = (e) => { peint = true; cv.setPointerCapture(e.pointerId); trace(e); };
  cv.onpointermove = trace;
  cv.onpointerup = cv.onpointerleave = () => { if (peint) imgSource.masque = cv.toDataURL("image/png"); peint = false; };
}
$("#img-effacer-masque").onclick = () => { imgSource.masque = null; imgSource.peint = false; dessinerSource(); };
// Le masque pour le moteur : blanc = à redessiner, noir = à garder
function masquePourMoteur() {
  const cv = $("#img-masque"); if (!cv || !imgSource.peint) return null;
  const sortie = document.createElement("canvas");
  sortie.width = cv.width; sortie.height = cv.height;
  const o = sortie.getContext("2d");
  o.fillStyle = "#000"; o.fillRect(0, 0, cv.width, cv.height);
  const src = cv.getContext("2d").getImageData(0, 0, cv.width, cv.height), dst = o.getImageData(0, 0, cv.width, cv.height);
  for (let i = 3; i < src.data.length; i += 4) if (src.data[i] > 0) { dst.data[i - 3] = dst.data[i - 2] = dst.data[i - 1] = 255; }
  o.putImageData(dst, 0, 0);
  return sortie.toDataURL("image/png");
}

$("#bt-img").onclick = async () => {
  const b = $("#bt-img"), pr = $("#img-pr"), etatTxt = $("#img-etat");
  const act = actionImg;
  const m = infoImages?.modeles.find((x) => x.nom === $("#img-modele").value);
  if (act !== "creer" && !imgSource.data) return toast("Choisis d'abord une image (bouton 📂 ou ✏️ sur une de tes images)");
  const masque = act === "zone" ? masquePourMoteur() : null;
  if (act === "zone" && !masque) return toast("Peins d'abord la zone à changer sur l'image");
  const [l, h] = $("#img-format").value.split("x").map(Number);
  b.disabled = true; pr.style.display = "block"; $("i", pr).style.width = "0";
  etatTxt.textContent = "Préparation…";
  const depart = Date.now();
  try {
    const r = await api("images/creer", {
      action: act, modele: $("#img-modele").value, prompt: $("#img-prompt").value, negatif: $("#img-negatif").value,
      largeur: l, hauteur: h, pas: +$("#img-pas").value || m?.pas, cfg: m?.cfg,
      image: act === "creer" ? undefined : imgSource.data, masque, force: +$("#img-force").value,
    }, { flux: true });
    await lireFlux(r, (o) => {
      if (o.erreur) throw new Error(o.erreur);
      if (o.etape) etatTxt.textContent = o.etape;
      if (o.total) { $("i", pr).style.width = Math.round((o.fait / o.total) * 100) + "%"; etatTxt.textContent = `Étape ${o.fait}/${o.total}`; }
      if (o.image) {
        etatTxt.textContent = `Fini en ${Math.round((Date.now() - depart) / 1000)} s`;
        const d = document.createElement("div");
        d.className = "img-case nouvelle";
        d.innerHTML = `<a href="${esc(urlImage(o.image.url))}" target="_blank" rel="noopener"><img src="${esc(urlImage(o.image.url))}" alt="Nouvelle image"></a>
          <div class="img-actions"><button class="btn petit" data-imod="${esc(o.image.nom)}" title="Modifier cette image">✏️</button>
          <button class="btn petit" data-igr="${esc(o.image.nom)}" title="Agrandir ×4">🔍</button></div>`;
        $(".vide", $("#galerie"))?.remove();
        $("#galerie").prepend(d);
        brancherGalerie();
      }
    });
  } catch (e) { etatTxt.textContent = ""; toast(e.message); }
  b.disabled = false; pr.style.display = "none";
};

/* ------------------------------------------------------------ devoirs */
const dv = { id: null, minuteur: null, apercuMin: null, controleur: null, photo: null, precedent: null, liste: [] };
const CHAMPS_DV = ["titre", "matiere", "eleve", "niveau", "date", "consigne"];
const imageDv = (nom) => urlImage("/images/" + encodeURIComponent(nom));
const metaDv = () => ({ titre: $("#dv-titre").value, matiere: $("#dv-matiere").value, eleve: $("#dv-eleve").value,
  niveau: $("#dv-niveau").value, date: $("#dv-date").value ? new Date($("#dv-date").value + "T12:00").toLocaleDateString("fr-CA", { day: "numeric", month: "long", year: "numeric" }) : "" });

async function rendreDevoirs() {
  // l'IA : même liste que la Jasette
  const sel = $("#dv-modele"), garde = sel.value || etat.params.modele_jasette;
  const locales = etat.modeles.filter((m) => !/embed/.test(m.nom));
  sel.innerHTML = locales.map((m) => `<option value="${esc(m.nom)}" ${m.nom === garde ? "selected" : ""}>${esc(m.nom)}</option>`).join("") || `<option value="">Aucune IA — va dans IA locales</option>`;
  try { dv.liste = (await api("devoirs", {})).devoirs; } catch (e) { return toast(e.message); }
  if (!dv.id) {
    if (dv.liste.length) await ouvrirDevoir(dv.liste[0].id);
    else nouveauDevoir();
  }
  majListeDv();
}
function majListeDv() {
  $("#dv-liste").innerHTML = (dv.id && !dv.liste.some((d) => d.id === dv.id) ? `<option value="${dv.id}">(nouveau devoir)</option>` : "") +
    dv.liste.map((d) => `<option value="${esc(d.id)}" ${d.id === dv.id ? "selected" : ""}>${esc(d.titre)}${d.matiere ? " · " + esc(d.matiere) : ""}</option>`).join("") ||
    `<option value="">Mes devoirs</option>`;
}
function remplirDv(d) {
  dv.id = d.id || null;
  CHAMPS_DV.forEach((k) => ($("#dv-" + k).value = d[k] || ($("#dv-" + k).tagName === "SELECT" ? $("#dv-" + k).value : "")));
  $("#dv-texte").value = d.contenu || "";
  dv.precedent = null; dv.photo = null; $("#dv-photo-nom").textContent = "";
  apercuDv(true);
}
function nouveauDevoir() {
  remplirDv({ date: new Date().toISOString().slice(0, 10), eleve: $("#dv-eleve").value, niveau: $("#dv-niveau").value });
  dv.id = null;
  $("#dv-sauve").textContent = "";
  majListeDv();
}
async function ouvrirDevoir(id) {
  try { remplirDv(await api("devoirs/lire", { id })); majListeDv(); } catch (e) { toast(e.message); }
}
function sauverDvBientot() {
  clearTimeout(dv.minuteur);
  $("#dv-sauve").textContent = "…";
  dv.minuteur = setTimeout(sauverDv, 900);
}
async function sauverDv() {
  clearTimeout(dv.minuteur);
  const corps = { id: dv.id, contenu: $("#dv-texte").value };
  CHAMPS_DV.forEach((k) => (corps[k] = $("#dv-" + k).value));
  if (!corps.titre) corps.titre = (corps.contenu.match(/^#\s+(.+)$/m) || [])[1] || "";
  if (!corps.titre && !corps.contenu.trim() && !corps.consigne.trim()) { $("#dv-sauve").textContent = ""; return; }
  try {
    const r = await api("devoirs/sauver", corps);
    dv.id = r.id;
    const i = dv.liste.findIndex((d) => d.id === r.id);
    const ligne = { id: r.id, titre: r.titre || "Sans titre", matiere: r.matiere, maj: r.maj };
    if (i >= 0) dv.liste[i] = ligne; else dv.liste.unshift(ligne);
    majListeDv();
    $("#dv-sauve").textContent = "💾 Enregistré sur la clé";
  } catch (e) { $("#dv-sauve").textContent = "⚠️ " + e.message; }
}
function apercuDv(tout_de_suite) {
  clearTimeout(dv.apercuMin);
  const faire = () => {
    const z = $("#dv-apercu");
    const haut = z.scrollTop;
    z.innerHTML = TiDevoir.documentHtml($("#dv-texte").value || "*Ton devoir va apparaître ici.*", metaDv(), imageDv, { brouillon: true });
    TiDevoir.activerSchemas(z, false).then(() => (z.scrollTop = haut));
    z.scrollTop = haut;
  };
  if (tout_de_suite) faire(); else dv.apercuMin = setTimeout(faire, 500);
}
["#dv-texte", ...CHAMPS_DV.map((k) => "#dv-" + k)].forEach((s) => $(s).addEventListener("input", () => { sauverDvBientot(); apercuDv(); }));
$("#dv-niveau").addEventListener("change", () => { sauverDvBientot(); apercuDv(); });
$("#dv-liste").onchange = async (e) => { if (e.target.value && e.target.value !== dv.id) { await sauverDv(); ouvrirDevoir(e.target.value); } };
$("#dv-nouveau").onclick = async () => { await sauverDv(); nouveauDevoir(); $("#dv-titre").focus(); };
$("#dv-effacer").onclick = async () => {
  if (!dv.id) return nouveauDevoir();
  if (!confirm("Effacer ce devoir ? (une copie .efface reste dans donnees\\devoirs)")) return;
  try { await api("devoirs/supprimer", { id: dv.id }); dv.liste = dv.liste.filter((d) => d.id !== dv.id); dv.id = null; nouveauDevoir(); toast("Devoir effacé"); }
  catch (e) { toast(e.message); }
};

// insérer du texte là où est le curseur
function insererDv(txt, remplacerSelection = true) {
  const t = $("#dv-texte"), a = t.selectionStart, b = remplacerSelection ? t.selectionEnd : t.selectionStart;
  t.value = t.value.slice(0, a) + txt + t.value.slice(b);
  t.focus(); t.selectionStart = t.selectionEnd = a + txt.length;
  sauverDvBientot(); apercuDv();
}
const SCHEMA_DV = '\n```mermaid\nflowchart LR\n  A["Étape 1"] --> B["Étape 2"] --> C["Étape 3"]\n```\n';
$$("[data-dvo]").forEach((b) => (b.onclick = () => {
  const t = $("#dv-texte");
  if (b.dataset.dvo === "section") insererDv("\n\n## Nouvelle section\n- ");
  if (b.dataset.dvo === "schema") insererDv(SCHEMA_DV);
  if (b.dataset.dvo === "gras") { const s = t.value.slice(t.selectionStart, t.selectionEnd) || "important"; insererDv("**" + s + "**"); }
  if (b.dataset.dvo === "mes-images") choisirImageDv((nom) => insererDv(`\n![](image:${nom})\n`));
}));
async function choisirImageDv(quandChoisi) {
  let g = [];
  try { g = (await api("images")).galerie; } catch (e) { return toast(e.message); }
  $("#dv-galerie-liste").innerHTML = g.map((x) => `<button data-dvimg="${esc(x.nom)}"><img src="${esc(urlImage(x.url))}" alt="${esc(x.nom)}" loading="lazy"></button>`).join("")
    || `<p class="vide">Pas encore d'images. Crée-en dans « Images » ou utilise « Image du PC ».</p>`;
  $("#dv-galerie").hidden = false;
  $$("[data-dvimg]").forEach((b) => (b.onclick = () => { $("#dv-galerie").hidden = true; quandChoisi(b.dataset.dvimg); }));
}
$("#dv-galerie-fermer").onclick = () => ($("#dv-galerie").hidden = true);
$("#dv-galerie").onclick = (e) => { if (e.target.id === "dv-galerie") e.currentTarget.hidden = true; };
// une image du PC -> PNG (1600 px max) -> rangée dans Mes images
function imagePcEnPng(fichier) {
  return new Promise((ok, ko) => {
    const r = new FileReader();
    r.onerror = ko;
    r.onload = () => {
      const img = new Image();
      img.onerror = () => ko(new Error("Image illisible"));
      img.onload = () => {
        const k = Math.min(1, 1600 / Math.max(img.width, img.height));
        const c = document.createElement("canvas");
        c.width = Math.round(img.width * k); c.height = Math.round(img.height * k);
        c.getContext("2d").drawImage(img, 0, 0, c.width, c.height);
        ok(c.toDataURL("image/png"));
      };
      img.src = r.result;
    };
    r.readAsDataURL(fichier);
  });
}
$("#dv-img-pc").onchange = async (e) => {
  const f = e.target.files[0]; e.target.value = "";
  if (!f) return;
  try { const r = await api("images/ajouter", { image: await imagePcEnPng(f) }); insererDv(`\n![${f.name.replace(/\.[^.]+$/, "")}](image:${r.nom})\n`); }
  catch (er) { toast(er.message); }
};
// boutons dans l'aperçu : [IMAGE: …] à créer ou à remplacer
function remplacerPlaceholderDv(prompt, ligne) {
  const t = $("#dv-texte");
  const re = new RegExp("^\\s*\\[IMAGE\\s*:\\s*" + prompt.replace(/[.*+?^${}()|[\]\\]/g, "\\$&") + "\\]\\s*$", "mi");
  t.value = t.value.replace(re, ligne);
  sauverDvBientot(); apercuDv(true);
}
$("#dv-apercu").addEventListener("click", async (e) => {
  const b = e.target.closest("[data-dv]"); if (!b) return;
  const prompt = b.closest(".dv-a-creer").dataset.prompt;
  if (b.dataset.dv === "choisir-image") return choisirImageDv((nom) => remplacerPlaceholderDv(prompt, `![](image:${nom})`));
  // créer avec une IA d'images de la clé
  let info;
  try { info = await api("images"); } catch (er) { return toast(er.message); }
  const ordre = ["flux2_klein", "sdxl_turbo", "sd_turbo", "sd15"];
  const m = info.modeles.filter((x) => (x.peut || []).includes("creer")).sort((a, b2) => (ordre.indexOf(a.nom) + 99) % 99 - (ordre.indexOf(b2.nom) + 99) % 99)[0];
  if (!info.moteur || !m) { toast("Il faut une IA d'images : va dans « Images » et télécharge-en une (SD-Turbo est la plus rapide)."); return; }
  b.disabled = true; b.textContent = "Création…";
  try {
    const r = await api("images/creer", { action: "creer", modele: m.nom, prompt, largeur: 768, hauteur: 512, pas: m.pas, cfg: m.cfg }, { flux: true });
    let nom = null;
    await lireFlux(r, (o) => {
      if (o.erreur) throw new Error(o.erreur);
      if (o.total) b.textContent = `Étape ${o.fait}/${o.total}`;
      if (o.image) nom = o.image.nom;
    });
    if (nom) remplacerPlaceholderDv(prompt, `![](image:${nom})`);
  } catch (er) { toast(er.message); b.disabled = false; b.textContent = "Créer avec l'IA"; }
});

// ---- l'IA écrit le devoir (en direct dans le texte)
$("#dv-photo").onchange = async (e) => {
  const f = e.target.files[0]; e.target.value = "";
  if (!f) return;
  try { dv.photo = await reduireImage(f, 1600); $("#dv-photo-nom").textContent = "📎 " + f.name; } catch { toast("Photo illisible"); }
};
function nettoyerDevoirIA(t) {
  t = t.replace(/^\s*```(?:markdown|md)?\s*\n/i, "").replace(/\n```\s*$/, "");
  const i = t.search(/^#\s/m);
  return (i > 0 ? t.slice(i) : t).replace(/<think>[\s\S]*?<\/think>/g, "").trimStart();
}
async function faireDevoirIA(changement) {
  const consigne = $("#dv-consigne").value.trim();
  const actuel = $("#dv-texte").value;
  if (!changement && !consigne && !dv.photo) return toast("Colle la consigne (ou prends la feuille en photo) d'abord");
  if (changement && !actuel.trim()) return toast("Il n'y a pas encore de devoir à changer");
  if (!changement && actuel.trim() && !confirm("Remplacer ton texte actuel par celui de l'IA ? (tu pourras revenir en arrière)")) return;
  const m = metaDv();
  const demande = changement
    ? "Changement demande : " + changement
    : "Consigne du devoir :\n" + (consigne || "(voir la photo de la feuille)") +
      (m.titre ? "\nTitre voulu : " + m.titre : "") + (m.matiere ? "\nMatiere : " + m.matiere : "");
  dv.precedent = actuel;
  dv.controleur = new AbortController();
  $("#dv-faire").disabled = $("#dv-changer").disabled = true; $("#dv-stop").hidden = false;
  $("#dv-etat").textContent = "L'IA écrit ton devoir…";
  let texte = "";
  try {
    const r = await api("jasette", {
      mode: "devoir", modele: $("#dv-modele").value, niveau: m.niveau, internet: true,
      devoir_actuel: changement ? actuel : null,
      messages: [{ role: "user", content: demande, images: dv.photo && !changement ? [dv.photo] : [] }],
    }, { flux: true, signal: dv.controleur.signal });
    await lireFlux(r, (o) => {
      if (o.erreur) throw new Error(o.erreur);
      if (o.outil) $("#dv-etat").textContent = "🌐 " + o.outil;
      if (o.texte) {
        texte += o.texte;
        $("#dv-texte").value = nettoyerDevoirIA(texte);
        $("#dv-texte").scrollTop = $("#dv-texte").scrollHeight;
        apercuDv();
      }
    });
    $("#dv-texte").value = nettoyerDevoirIA(texte);
    if (!$("#dv-titre").value) $("#dv-titre").value = ($("#dv-texte").value.match(/^#\s+(.+)$/m) || [])[1] || "";
    $("#dv-etat").innerHTML = `✅ Fini ! Relis-le et change ce que tu veux. <button class="btn petit" id="dv-annuler-ia">↩️ Revenir à avant</button>`;
    $("#dv-annuler-ia").onclick = () => { $("#dv-texte").value = dv.precedent || ""; dv.precedent = null; $("#dv-etat").textContent = "Version d'avant remise."; sauverDvBientot(); apercuDv(true); };
    if (changement) $("#dv-changement").value = "";
  } catch (e) {
    if (e.name === "AbortError") $("#dv-etat").textContent = "Arrêté.";
    else { $("#dv-etat").textContent = ""; toast(e.message); }
  }
  dv.controleur = null;
  $("#dv-faire").disabled = $("#dv-changer").disabled = false; $("#dv-stop").hidden = true;
  sauverDv(); apercuDv(true);
}
$("#dv-faire").onclick = () => faireDevoirIA(null);
$("#dv-changer").onclick = () => { const c = $("#dv-changement").value.trim(); if (c) faireDevoirIA(c); else $("#dv-changement").focus(); };
$("#dv-changement").addEventListener("keydown", (e) => { if (e.key === "Enter") $("#dv-changer").click(); });
$("#dv-stop").onclick = () => dv.controleur?.abort();

// ---- présenter, imprimer, exporter
$("#dv-presenter").onclick = () => {
  const md = $("#dv-texte").value;
  if (!md.trim()) return toast("Écris ou fais d'abord ton devoir");
  TiDevoir.presenter(md, metaDv(), imageDv);
};
$("#dv-imprimer").onclick = async () => {
  if (!$("#dv-texte").value.trim()) return toast("Écris ou fais d'abord ton devoir");
  apercuDv(true);
  await TiDevoir.activerSchemas($("#dv-apercu"), false);
  const page = $("#dv-apercu .dv-papier").outerHTML;
  const f = document.createElement("iframe");
  f.style.cssText = "position:fixed;right:0;bottom:0;width:0;height:0;border:0";
  document.body.appendChild(f);
  const d = f.contentDocument;
  d.open();
  d.write(`<!doctype html><html lang="fr-CA"><head><meta charset="utf-8"><title>${esc($("#dv-titre").value || "Devoir")}</title><link rel="stylesheet" href="/web/devoirs.css"><style>body{margin:0;background:#fff}.dv-papier{box-shadow:none;max-width:none;padding:0}.dv-a-creer{display:none}</style></head><body>${page}</body></html>`);
  d.close();
  await new Promise((ok) => (f.onload = ok, setTimeout(ok, 1500)));
  await Promise.all([...d.images].map((im) => (im.complete ? 0 : new Promise((ok) => (im.onload = im.onerror = ok)))));
  f.contentWindow.focus();
  f.contentWindow.print();
  setTimeout(() => f.remove(), 60000);
};
const enDataUrl = (blob) => new Promise((ok, ko) => { const r = new FileReader(); r.onload = () => ok(r.result); r.onerror = ko; r.readAsDataURL(blob); });
const pourScript = (t) => t.replace(/<\/script/gi, "<\\/script").replace(/<!--/g, "<\\!--");
async function construirePresentation() {
  const md = $("#dv-texte").value, meta = metaDv();
  const [mermaid, schemas, devoirs, css] = await Promise.all(
    ["/web/lib/mermaid.min.js", "/web/schemas.js", "/web/devoirs.js", "/web/devoirs.css"].map((u) => fetch(u).then((r) => r.text())));
  const images = {};
  for (const m of md.matchAll(/\]\(image:([^)\s]+)\)/g)) {
    const nom = decodeURIComponent(m[1]);
    if (images[nom]) continue;
    try { images[nom] = await enDataUrl(await (await fetch(imageDv(nom))).blob()); } catch { /* image manquante */ }
  }
  const donnees = JSON.stringify({ md, meta, images }).replace(/</g, "\\u003c").replace(/\u2028/g, "\\u2028").replace(/\u2029/g, "\\u2029");
  return `<!doctype html>
<html lang="fr-CA"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<title>${esc(meta.titre || "Devoir")}</title>
<meta name="generator" content="Ti-Labo">
<style>${css}</style></head><body>
<script>${pourScript(mermaid)}</script>
<script>${pourScript(schemas)}</script>
<script>${pourScript(devoirs)}</script>
<script>const D = ${donnees}; TiDevoir.autonome(D.md, D.meta, D.images);</script>
</body></html>`;
}
$("#dv-exporter").onclick = async () => {
  if (!$("#dv-texte").value.trim()) return toast("Écris ou fais d'abord ton devoir");
  const b = $("#dv-exporter"); b.disabled = true; b.textContent = "📦 Préparation…";
  try {
    const html = await construirePresentation();
    const titre = $("#dv-titre").value || ($("#dv-texte").value.match(/^#\s+(.+)$/m) || [])[1] || "Devoir";
    const r = await api("devoirs/exporter", { titre, html });
    toast("✅ Enregistré sur la clé : " + r.relatif + " — à l'école, double-clique ce fichier (aucune installation)");
  } catch (e) { toast("Échec : " + e.message); }
  b.disabled = false; b.textContent = "📦 Exporter pour l'école";
};

/* ------------------------------------------------------------ paramètres */
function rendreClaude() {
  const z = $("#claude-zone"), active = !!etat.params.anthropic_key;
  z.innerHTML = active
    ? `<div class="ligne"><span class="tag vert">clé enregistrée</span><span class="sous" style="margin:0">Choisis « claude-… » dans la liste des modèles de la Jasette ou du Codex.</span><span style="flex:1"></span><button class="btn petit danger" id="bt-claude-off">Retirer la clé</button></div>`
    : `<label class="lib" for="claude-cle">Clé API Anthropic</label><div class="ligne"><input class="champ" type="password" id="claude-cle" placeholder="sk-ant-…" autocomplete="off" style="flex:1"><button class="btn prim" id="bt-claude-on">Ajouter Claude</button></div>
       <p class="sous" style="font-size:12px">La clé reste sur la clé USB (donnees\\parametres.json). Si tu perds la clé USB, révoque-la sur console.anthropic.com.</p>`;
  const envoyer = async (cle) => {
    try { await api("claude/cle", { cle }); toast(cle ? "Claude est ajouté !" : "Clé retirée"); await rafraichirEtat(); rendreClaude(); }
    catch (e) { toast(e.message); }
  };
  if (active) $("#bt-claude-off").onclick = () => confirm("Retirer la clé API Claude ?") && envoyer("");
  else $("#bt-claude-on").onclick = () => envoyer($("#claude-cle").value.trim());
}
function rendreMdp() {
  const z = $("#mdp-zone"), actif = !!etat.params.mot_de_passe;
  z.innerHTML = `${actif ? `<div><label class="lib" for="mdp-actuel">Mot de passe actuel</label><input class="champ" type="password" id="mdp-actuel" autocomplete="current-password"></div>` : ""}
    <div class="grille" style="margin-top:8px">
      <div><label class="lib" for="mdp-nouveau">${actif ? "Nouveau mot de passe" : "Mot de passe"}</label><input class="champ" type="password" id="mdp-nouveau" autocomplete="new-password"></div>
      <div><label class="lib" for="mdp-confirme">Confirme-le</label><input class="champ" type="password" id="mdp-confirme" autocomplete="new-password"></div>
    </div>
    <div class="ligne" style="margin-top:10px"><button class="btn prim" id="bt-mdp">${actif ? "Changer le mot de passe" : "Activer le mot de passe"}</button>
    ${actif ? `<button class="btn danger" id="bt-mdp-off">Enlever le mot de passe</button>` : ""}</div>
    <p class="sous" style="font-size:12px">Note-le quelque part : sans lui, il faut effacer « mot_de_passe » dans donnees\\parametres.json pour rentrer.</p>`;
  $("#bt-mdp").onclick = async () => {
    const n = $("#mdp-nouveau").value;
    if (n !== $("#mdp-confirme").value) return toast("Les deux mots de passe ne sont pas pareils");
    if (n.length < 4) return toast("Au moins 4 caractères");
    try { await api("verrou/changer", { actuel: $("#mdp-actuel")?.value, nouveau: n }); toast("Mot de passe activé"); await rafraichirEtat(); rendreMdp(); }
    catch (e) { toast(e.message); }
  };
  if (actif) $("#bt-mdp-off").onclick = async () => {
    try { await api("verrou/changer", { actuel: $("#mdp-actuel").value, nouveau: "" }); toast("Mot de passe enlevé"); await rafraichirEtat(); rendreMdp(); }
    catch (e) { toast(e.message); }
  };
}

async function rendreParametres() {
  rendreClaude();
  rendreMdp();
  const p = etat.params;
  $("#pa-moteur").value = p.moteur;
  $("#pa-url").value = p.url_openai;
  $("#pa-memoire").value = p.modele_memoire;
  $("#pa-internet").checked = !!p.internet;
  let polices = [];
  try { polices = await api("polices"); } catch {}
  const opts = (choisi) => [...new Set([choisi, ...polices])].map((f) => `<option ${f === choisi ? "selected" : ""}>${esc(f)}</option>`).join("");
  $("#pa-police").innerHTML = opts(p.police);
  $("#pa-police-code").innerHTML = opts(p.police_code);
  const apercu = () => ($("#apercu-police").style.fontFamily = `"${$("#pa-police").value}"`);
  $("#pa-police").onchange = apercu;
  $("#pa-police-code").onchange = () => ($("#apercu-police").style.fontFamily = `"${$("#pa-police-code").value}"`);
  apercu();
}
$("#bt-sauver-params").onclick = async () => {
  try {
    const p = await api("parametres", {
      moteur: $("#pa-moteur").value, url_openai: $("#pa-url").value.trim(), modele_memoire: $("#pa-memoire").value.trim(),
      internet: $("#pa-internet").checked, police: $("#pa-police").value, police_code: $("#pa-police-code").value,
    });
    Object.assign(etat.params, p);
    toast("Paramètres enregistrés");
    await rafraichirEtat();
  } catch (e) { toast(e.message); }
};

/* ------------------------------------------------------------ Codex : trouve mes projets */
async function trouve() {
  const q = $("#trouve-q").value.trim();
  if (!q) return;
  const box = $("#trouve-res");
  box.innerHTML = `<p class="sous">Recherche…</p>`;
  try {
    const r = await api("codex/trouve", { question: q });
    box.innerHTML = r.map((x) => `<div class="carte" style="padding:10px"><div class="ligne" style="justify-content:space-between"><b>${esc(x.nom)}</b><span class="tag or">${esc(x.langage)}</span></div>
      ${x.fichiers.map((f) => `<button class="noeud" style="padding-left:0" data-tf="${esc(f)}">└ ${esc(f.split("/").slice(1).join("/"))}</button>`).join("")}
      <button class="btn petit" style="margin-top:6px" data-tp="${esc(x.nom)}">Ouvrir</button></div>`).join("") || `<p class="sous">Rien trouvé sur la clé.</p>`;
    $$("[data-tp]", box).forEach((b) => (b.onclick = () => { allerA("projets"); choisirProjet(b.dataset.tp); }));
    $$("[data-tf]", box).forEach((b) => (b.onclick = async () => { allerA("projets"); await choisirProjet(b.dataset.tf.split("/")[0]); ouvrirFichier(b.dataset.tf); }));
  } catch (e) { box.innerHTML = `<p class="sous">${esc(e.message)}</p>`; }
}
$("#bt-trouve").onclick = trouve;
$("#trouve-q").addEventListener("keydown", (e) => { if (e.key === "Enter") trouve(); });

/* ------------------------------------------------------------ démarrage */
if (mobile()) {
  $("#gauche").classList.add("ferme");
  $("#bt-gauche").classList.remove("actif");
  $("#bt-gauche").setAttribute("aria-pressed", "false");
}
const jasette = creerChat($("#chat-jasette"), "jasette");
const codex = creerChat($("#chat-codex"), "codex");
window.addEventListener("beforeunload", (e) => { if (etat.modifie) { e.preventDefault(); e.returnValue = ""; } });
function deverrouiller() {
  if (window.TILABO_JETON) return Promise.resolve();
  const v = $("#verrou"), f = $("#form-verrou"), champ = $("#verrou-mdp"), msg = $("#verrou-msg");
  v.hidden = false;
  setTimeout(() => champ.focus(), 50);
  return new Promise((ok) => {
    f.onsubmit = async (e) => {
      e.preventDefault();
      msg.textContent = "Vérification…";
      try {
        const r = await fetch("/api/verrou/entrer", { method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify({ mot_de_passe: champ.value }) });
        const j = await r.json();
        if (!r.ok) throw new Error(j.erreur || "Erreur");
        window.TILABO_JETON = j.jeton;
        champ.value = "";
        v.hidden = true;
        ok();
      } catch (err) { msg.textContent = err.message; champ.select(); }
    };
  });
}

(async () => {
  await deverrouiller();
  await rafraichirEtat();
  rendreBureau();
  // Fenetre du bureau facon Windows : ?vue=codex&projet=nom
  const qCadre = new URLSearchParams(location.search);
  if (qCadre.get("vue")) {
    if (qCadre.get("projet")) { try { await choisirProjet(qCadre.get("projet")); } catch (_) {} }
    allerA(qCadre.get("vue"));
  }
  codex.prechauffer(); // l'IA du Codex est prete avant le premier message
  majCharge().then((n) => { if (n) suivreCharge(); });
  setInterval(majCharge, 30000);
  // Le moteur peut prendre quelques secondes à démarrer : on revérifie.
  let essais = 0;
  const t = setInterval(async () => {
    if (etat.ollama || ++essais > 10) { clearInterval(t); return; }
    await rafraichirEtat();
    if ($("#v-bureau").classList.contains("actif")) rendreBureau();
  }, 2500);
})();
