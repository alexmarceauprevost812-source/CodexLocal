/* Ti-Labo — bureau du laboratoire (interface) */
"use strict";

const $ = (s, el = document) => el.querySelector(s);
const $$ = (s, el = document) => [...el.querySelectorAll(s)];
const esc = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const ic = (id) => `<svg class="ic"><use href="#i-${id}"/></svg>`;

const etat = { params: {}, modeles: [], projets: [], projet: null, fichier: null, modifie: false };

const CATALOGUE = [
  { nom: "qwen2.5-coder:7b", taille: 4.7, tags: ["code", "internet"], desc: "Le meilleur Codex pour un PC ordinaire." },
  { nom: "qwen3:8b", taille: 5.2, tags: ["internet"], desc: "Très bon en jasette et pour faire des plans." },
  { nom: "qwen3-vl:8b", taille: 6.1, tags: ["vision", "internet"], desc: "Voit les images ET peut chercher sur Internet." },
  { nom: "gemma3:4b", taille: 3.3, tags: ["vision"], desc: "Léger, voit les images (captures d'écran, photos)." },
  { nom: "qwen2.5vl:7b", taille: 6.0, tags: ["vision"], desc: "Lit très bien le texte dans les images." },
  { nom: "llama3.1:8b", taille: 4.9, tags: ["internet"], desc: "Classique solide, utilise bien les outils." },
  { nom: "llama3.2:3b", taille: 2.0, tags: ["internet"], desc: "Petit et rapide pour les vieux PC." },
  { nom: "nomic-embed-text", taille: 0.3, tags: ["mémoire"], desc: "Requis pour la Mémoire de la clé." },
];

/* ------------------------------------------------------------ API */
async function api(route, donnees, options = {}) {
  const r = await fetch("/api/" + route, {
    method: donnees === undefined ? "GET" : "POST",
    headers: { "Content-Type": "application/json", "X-TiLabo": window.TILABO_JETON },
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
  texte = texte.replace(/```([\w.+-]*)\n?([\s\S]*?)(```|$)/g, (_, lang, code) => {
    blocs.push({ lang, code });
    return `\u0000${blocs.length - 1}\u0000`;
  });
  let h = esc(texte)
    .replace(/^#### (.*)$/gm, "<h4>$1</h4>").replace(/^### (.*)$/gm, "<h3>$1</h3>").replace(/^## (.*)$/gm, "<h3>$1</h3>").replace(/^# (.*)$/gm, "<h3>$1</h3>")
    .replace(/\*\*(.+?)\*\*/g, "<b>$1</b>")
    .replace(/`([^`\n]+)`/g, "<code>$1</code>")
    .replace(/\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>')
    .replace(/(^|[\s(])(https?:\/\/[^\s<)]+)/g, '$1<a href="$2" target="_blank" rel="noopener">$2</a>');
  h = h.replace(/(?:^|\n)((?:[-*] .*(?:\n|$))+)/g, (_, l) => "\n<ul>" + l.trim().split("\n").map((x) => "<li>" + x.replace(/^[-*] /, "") + "</li>").join("") + "</ul>");
  h = h.replace(/(?:^|\n)((?:\d+[.)] .*(?:\n|$))+)/g, (_, l) => "\n<ol>" + l.trim().split("\n").map((x) => "<li>" + x.replace(/^\d+[.)] /, "") + "</li>").join("") + "</ol>");
  h = h.split(/\n{2,}/).map((p) => (/^\s*<(h\d|ul|ol)/.test(p) || p.includes("\u0000") ? p : "<p>" + p.replace(/\n/g, "<br>") + "</p>")).join("\n");
  return h.replace(/\u0000(\d+)\u0000/g, (_, n) => {
    const b = blocs[+n];
    const actions = avecActions
      ? `<button class="btn petit" data-act="copier">Copier</button><button class="btn petit" data-act="inserer">Mettre dans l'éditeur</button>`
      : "";
    return `<div class="bloc-code"><div class="bc-tete">${esc(b.lang || "code")}<span class="espace"></span>${actions}</div><pre><code>${esc(b.code.replace(/\n$/, ""))}</code></pre></div>`;
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

/* ------------------------------------------------------------ composant de chat */
function creerChat(conteneur, mode) {
  const id = "c" + mode;
  conteneur.innerHTML = `
    <div class="options">
      <select class="champ" aria-label="Modèle d'IA"></select>
      <label class="interrupteur" title="L'IA propose un plan avant de coder"><input type="checkbox" data-o="plan"><span class="rail"></span>${ic("plan")}Plan d'abord</label>
      <label class="interrupteur" title="L'IA peut chercher sur Internet si elle en a besoin"><input type="checkbox" data-o="internet"><span class="rail"></span>${ic("globe")}Internet</label>
      <label class="interrupteur" title="Utiliser la mémoire de la clé"><input type="checkbox" data-o="memoire"><span class="rail"></span>${ic("memoire")}Mémoire</label>
      <span class="espace"></span>
      <button class="btn petit" data-o="vider">Nouvelle jasette</button>
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
  const sel = $("select", conteneur), fil = $(".fil", conteneur), zone = $("textarea", conteneur);
  const apercus = $(".apercus", conteneur), entreeImg = $(`#${id}-img`, conteneur);
  const btEnvoyer = $('[data-o="envoyer"]', conteneur);
  const cases = Object.fromEntries($$("input[data-o]", conteneur).map((c) => [c.dataset.o, c]));
  const cleStock = "tilabo-" + mode;
  let hist = [], images = [], controleur = null;

  try {
    const s = JSON.parse(localStorage.getItem(cleStock + "-opts") || "{}");
    cases.plan.checked = s.plan ?? mode === "codex";
    cases.internet.checked = s.internet ?? true;
    cases.memoire.checked = !!s.memoire;
  } catch { cases.plan.checked = mode === "codex"; cases.internet.checked = true; }
  Object.values(cases).forEach((c) => c.addEventListener("change", () => {
    try { localStorage.setItem(cleStock + "-opts", JSON.stringify({ plan: cases.plan.checked, internet: cases.internet.checked, memoire: cases.memoire.checked })); } catch {}
  }));

  function majModeles() {
    const cle = mode === "codex" ? "modele_codex" : "modele_jasette";
    const choisi = etat.params[cle];
    sel.innerHTML = etat.modeles.length
      ? etat.modeles.filter((m) => !/embed/.test(m.nom)).map((m) => `<option ${m.nom === choisi ? "selected" : ""}>${esc(m.nom)}</option>`).join("")
      : `<option value="">Aucune IA — va dans IA locales</option>`;
    if (!choisi && sel.value) api("parametres", { [cle]: sel.value }).then((p) => Object.assign(etat.params, p)).catch(() => {});
  }
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
  $('[data-o="vider"]', conteneur).onclick = () => { if (controleur) controleur.abort(); hist = []; fil.innerHTML = ""; accueil(); };

  function accueil() {
    fil.innerHTML = `<div class="vide">${mode === "codex"
      ? "Le Codex voit le fichier ouvert dans l'éditeur.<br>Avec « Plan d'abord », il te propose un plan avant d'écrire du code."
      : "Jase avec ton IA locale. Joins une image pour qu'elle la regarde (modèle « vision »)."}</div>`;
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
    const plan = planForce ?? (cases.plan.checked ? "planifier" : null);

    const d = document.createElement("div");
    d.className = "msg assistant";
    d.innerHTML = `<div class="qui">${esc(sel.value)}${plan === "planifier" ? ' <span class="tag or">plan</span>' : ""}</div><div class="etapes"></div><div class="contenu curseur"></div>`;
    fil.appendChild(d);
    const contenu = $(".contenu", d), etapes = $(".etapes", d);
    let reponse = "";
    controleur = new AbortController();
    btEnvoyer.innerHTML = ic("stop");
    btEnvoyer.setAttribute("aria-label", "Arrêter");
    const coller = () => { fil.scrollTop = fil.scrollHeight; };
    try {
      const r = await api("jasette", {
        mode, modele: sel.value, messages: hist, plan,
        internet: cases.internet.checked, memoire: cases.memoire.checked,
        fichier: mode === "codex" ? etat.fichier : null, projet: etat.projet,
      }, { flux: true, signal: controleur.signal });
      await lireFlux(r, (o) => {
        if (o.texte) { reponse += o.texte; contenu.innerHTML = rendreMarkdown(reponse); }
        if (o.outil) etapes.insertAdjacentHTML("beforeend", `<div class="etape">${ic("globe")}${esc(o.outil)}</div>`);
        if (o.liens) etapes.insertAdjacentHTML("beforeend", o.liens.slice(0, 3).map((u) => `<div class="etape" style="padding-left:24px"><a href="${esc(u)}" target="_blank" rel="noopener">${esc(u)}</a></div>`).join(""));
        if (o.sources) etapes.insertAdjacentHTML("beforeend", `<div class="etape">${ic("memoire")}Mémoire : ${esc(o.sources.join(", "))}</div>`);
        if (o.erreur) contenu.insertAdjacentHTML("beforeend", `<p style="color:var(--red)">${esc(o.erreur)}</p>`);
        if (o.jetons_s) $(".qui", d).insertAdjacentHTML("beforeend", `<span>· ${o.jetons_s} jetons/s</span>`);
        coller();
      });
    } catch (e) {
      if (e.name !== "AbortError") contenu.insertAdjacentHTML("beforeend", `<p style="color:var(--red)">${esc(e.message)}</p>`);
    } finally {
      contenu.classList.remove("curseur");
      controleur = null;
      btEnvoyer.innerHTML = ic("envoyer");
      btEnvoyer.setAttribute("aria-label", "Envoyer");
    }
    if (reponse) hist.push({ role: "assistant", content: reponse });
    if (reponse && plan === "planifier") {
      const a = document.createElement("div");
      a.className = "plan-actions";
      a.innerHTML = `<button class="btn prim petit">Approuver et coder</button><button class="btn petit">Corriger le plan</button>`;
      const [ok, corriger] = $$("button", a);
      ok.onclick = () => { a.remove(); envoyer("Le plan est approuvé. Vas-y, code-le.", "executer"); };
      corriger.onclick = () => { a.remove(); zone.placeholder = "Dis ce qu'il faut changer dans le plan…"; zone.focus(); };
      d.appendChild(a);
    }
    coller();
  }

  return { majModeles, envoyer: (t) => envoyer(t), zone };
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
  if (vue !== "codex") fermerSurMobile("#droite", "#bt-droite");
  if (vue === "codex") {
    $("#droite").classList.remove("ferme");
    $("#bt-droite").classList.add("actif");
    codex.zone.focus();
    return;
  }
  $$(".vue").forEach((v) => v.classList.toggle("actif", v.id === "v-" + vue));
  $$(".nav").forEach((n) => n.classList.toggle("actif", n.dataset.vue === vue));
  if (vue === "projets") chargerProjets();
  if (vue === "ia") rendreIA();
  if (vue === "github") rendreGitHub();
  if (vue === "memoire") rendreMemoire();
  if (vue === "studio") rendreStudio();
  if (vue === "parametres") rendreParametres();
  if (vue === "bureau") rendreBureau();
}
$$(".nav").forEach((n) => (n.onclick = () => allerA(n.dataset.vue)));
$$("[data-aller]").forEach((n) => (n.onclick = () => allerA(n.dataset.aller)));
function bascule(bt, panneau) {
  bt.onclick = () => {
    const ferme = $(panneau).classList.toggle("ferme");
    if (!ferme && mobile()) {
      const autre = panneau === "#gauche" ? ["#droite", "#bt-droite"] : ["#gauche", "#bt-gauche"];
      fermerSurMobile(...autre);
    }
    bt.classList.toggle("actif", !ferme);
    bt.setAttribute("aria-pressed", String(!ferme));
  };
}
bascule($("#bt-gauche"), "#gauche");
bascule($("#bt-droite"), "#droite");

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
    $("#codex-contexte").textContent = "Contexte : " + chemin;
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
}
const ed = $("#editeur");
ed.addEventListener("input", () => { marquerModifie(true); majNumeros(); });
ed.addEventListener("scroll", () => ($("#numeros").scrollTop = ed.scrollTop));
ed.addEventListener("keydown", (e) => {
  if (e.key === "Tab") {
    e.preventDefault();
    const s = ed.selectionStart;
    ed.setRangeText("    ", s, ed.selectionEnd, "end");
    marquerModifie(true);
  }
});
async function sauver() {
  if (!etat.fichier) return;
  try { await api("fichier", { chemin: etat.fichier, contenu: ed.value }); marquerModifie(false); toast("Enregistré sur la clé"); }
  catch (e) { toast(e.message); }
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
  $("#arbre").innerHTML = noeuds.map((n) => `<button class="noeud ${n.type} ${n.chemin === garde ? "actif" : ""}" style="padding-left:${12 + n.prof * 14}px" ${n.type === "fichier" ? `data-f="${esc(n.chemin)}"` : "tabindex='-1'"}>${n.type === "dossier" ? "├ " + esc(n.nom) + "/" : "└ " + esc(n.nom)}</button>`).join("");
  $$("#arbre [data-f]").forEach((b) => (b.onclick = () => ouvrirFichier(b.dataset.f)));
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
  if (!st.cles.length) avis += `<p class="vide" style="text-align:left;padding:10px 14px">Branche ta clé USB-C pour déplacer des IA dessus.</p>`;
  st.cles.filter((c) => c.erreur).forEach((c) => (avis += `<p class="vide" style="text-align:left;padding:10px 14px;color:var(--red)">${esc(c.erreur)}</p>`));
  if (cle?.limite) avis += `<p class="vide" style="text-align:left;padding:10px 14px">Android ne permet pas d'écrire à la racine de la clé : les IA vont dans <code>${esc(cle.ia)}</code>. Si tu désinstalles Termux, Android efface ce dossier.</p>`;
  $("#tr-avis").innerHTML = avis;
  const barre = (id) => `<div class="progression" id="${id}"><i></i></div>`;
  let k = 0;
  $("#tr-tel").innerHTML = st.tel_gguf.map((g) => {
    const id = "tg" + k++;
    const actif = st.gguf_actif === g.chemin;
    return `<div class="modele"><div class="info"><b>${esc(g.nom)}</b> <span class="tag">gguf</span>${actif ? ' <span class="tag vert">en marche</span>' : ""}
      <small>${g.taille_go} Go · ${esc(g.dossier.includes("CodexLocal") ? "CodexLocal" : g.dossier)}</small>${barre(id)}</div>
      <button class="btn petit" data-gg-util="${esc(g.chemin)}">${actif ? "Arrêter" : "Utiliser"}</button>
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
        ${x.type === "gguf" ? `<button class="btn petit" data-gg-util="${esc(x.chemin)}">Utiliser (lent)</button>` : ""}
        <button class="btn petit" data-tr='${esc(JSON.stringify({ ...op, garder: true }))}'>Copier sur le tél</button>
        <button class="btn petit prim" data-tr='${esc(JSON.stringify(op))}'>Ramener sur le tél</button></div>`;
    }).join("") || `<p class="vide">Aucune IA sur la clé.</p>`}`).join("");

  $$("[data-tr]", box).forEach((b) => (b.onclick = () => transferer(JSON.parse(b.dataset.tr), b)));
  $$("[data-gg-util]", box).forEach((b) => (b.onclick = async () => {
    const actif = b.textContent === "Arrêter";
    b.disabled = true; b.textContent = actif ? "Arrêt…" : "Démarrage…";
    try {
      if (actif) { await api("gguf/arreter", {}); toast("Retour au moteur Ollama"); }
      else { const r = await api("gguf/demarrer", { chemin: b.dataset.ggUtil }); toast(r.modele + " est prêt dans la Jasette et le Codex"); }
    } catch (e) { toast(e.message); }
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
  $("#ia-installees").innerHTML = etat.modeles.map((m) => {
    const cat = CATALOGUE.find((c) => c.nom === m.nom || c.nom + ":latest" === m.nom);
    return `<div class="modele"><div class="info"><b>${esc(m.nom)}</b>${(cat?.tags || []).map((t) => ` <span class="tag ${t === "vision" ? "bleu" : t === "internet" ? "vert" : "or"}">${t}</span>`).join("")}
      <small>${m.taille_go ? m.taille_go + " Go" : ""} ${esc(m.params || "")} ${esc(m.famille || "")}</small></div>
      <button class="btn petit" data-util="${esc(m.nom)}">Utiliser</button>
      <button class="btn petit danger" data-ret="${esc(m.nom)}">Retirer</button></div>`;
  }).join("") || `<p class="vide">${etat.ollama ? "Aucune IA sur la clé pour l'instant." : "Le moteur est arrêté. Clique « Démarrer le moteur »."}</p>`;
  $("#ia-catalogue").innerHTML = CATALOGUE.map((c) => `
    <div class="modele"><div class="info"><b>${c.nom}</b>${c.tags.map((t) => ` <span class="tag ${t === "vision" ? "bleu" : t === "internet" ? "vert" : "or"}">${t}</span>`).join("")}
      <small>${c.desc} · ${c.taille} Go</small><div class="progression" id="pr-${c.nom.replace(/[^\w]/g, "_")}"><i></i></div></div>
      <button class="btn petit ${pris(c.nom) ? "" : "prim"}" data-tel="${c.nom}" ${pris(c.nom) ? "disabled" : ""}>${pris(c.nom) ? "Sur la clé" : "Télécharger"}</button></div>`).join("");
  $$("[data-util]").forEach((b) => (b.onclick = async () => {
    await api("parametres", { modele_jasette: b.dataset.util, modele_codex: b.dataset.util }).then((p) => Object.assign(etat.params, p));
    jasette.majModeles(); codex.majModeles(); toast(b.dataset.util + " est maintenant utilisée par la Jasette et le Codex");
  }));
  $$("[data-ret]").forEach((b) => (b.onclick = async () => {
    if (!confirm("Retirer " + b.dataset.ret + " de la clé ?")) return;
    try { await api("ia/supprimer", { nom: b.dataset.ret }); await rafraichirEtat(); rendreIA(); } catch (e) { toast(e.message); }
  }));
  $$("[data-tel]").forEach((b) => (b.onclick = () => telecharger(b.dataset.tel, b)));
}

async function telecharger(nom, bouton) {
  const pr = $("#pr-" + nom.replace(/[^\w]/g, "_"));
  if (bouton) { bouton.disabled = true; bouton.textContent = "…"; }
  if (pr) pr.style.display = "block";
  try {
    const r = await api("ia/telecharger", { nom }, { flux: true });
    await lireFlux(r, (o) => {
      if (o.error) throw new Error(o.error);
      if (o.total && o.completed && pr) $("i", pr).style.width = Math.round((o.completed / o.total) * 100) + "%";
      if (bouton) bouton.textContent = o.total ? Math.round(((o.completed || 0) / o.total) * 100) + " %" : (o.status || "…").slice(0, 14);
    });
    toast(nom + " est sur la clé !");
  } catch (e) { toast("Échec : " + e.message); }
  await rafraichirEtat();
  rendreIA();
}
$("#bt-ia-autre").onclick = () => { const n = $("#ia-autre").value.trim(); if (n) { telecharger(n, $("#bt-ia-autre")); $("#ia-autre").value = ""; } };
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
    box.innerHTML = `<label class="lib" for="gh-jeton">Ton jeton GitHub (Settings › Developer settings › Fine-grained tokens, accès « Contents: read »)</label>
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
  dessinerSchema();
}
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
$("#bt-studio-codex").onclick = () => {
  const nom = $("#studio-projet").value;
  if (!nom) return;
  allerA("codex");
  codex.envoyer(`Explique-moi la structure du projet « ${nom} » : utilise l'outil chercher_mes_projets si tu peux, et décris le rôle de chaque partie.`);
};

/* ------------------------------------------------------------ paramètres */
async function rendreParametres() {
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
  ["#gauche", "#droite"].forEach((p) => $(p).classList.add("ferme"));
  ["#bt-gauche", "#bt-droite"].forEach((b) => { $(b).classList.remove("actif"); $(b).setAttribute("aria-pressed", "false"); });
}
const jasette = creerChat($("#chat-jasette"), "jasette");
const codex = creerChat($("#chat-codex"), "codex");
window.addEventListener("beforeunload", (e) => { if (etat.modifie) { e.preventDefault(); e.returnValue = ""; } });
(async () => {
  await rafraichirEtat();
  rendreBureau();
  // Le moteur peut prendre quelques secondes à démarrer : on revérifie.
  let essais = 0;
  const t = setInterval(async () => {
    if (etat.ollama || ++essais > 10) { clearInterval(t); return; }
    await rafraichirEtat();
    if ($("#v-bureau").classList.contains("actif")) rendreBureau();
  }, 2500);
})();
