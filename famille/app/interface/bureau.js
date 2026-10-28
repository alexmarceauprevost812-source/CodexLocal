// Le « mini PC » de la clé : un bureau avec de grosses icônes, une barre des
// tâches comme Windows, et de petites applis (Mes documents, Calculatrice,
// Bloc-notes, Créateur d'affiches) à côté de l'assistante.
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const M = window.Markdown;
  const A = window.Assistante;

  const APPLIS = [
    { vue: "assistante", icone: "💬", nom: "Assistante", sous: "Jaser et travailler avec l'IA", couleur: "#0067c0" },
    { vue: "documents", icone: "📁", nom: "Mes documents", sous: "Tes fichiers sur la clé", couleur: "#c47f00" },
    { vue: "calcul", icone: "🧮", nom: "Calculatrice", sous: "Avec TPS et TVQ", couleur: "#0f7b0f" },
    { vue: "agenda", icone: "📅", nom: "Agenda", sous: "Rendez-vous et rappels", couleur: "#c2185b" },
    { vue: "notes", icone: "📝", nom: "Bloc-notes", sous: "Notes et brouillons", couleur: "#6b3fa0" },
    { vue: "affiche", icone: "🖼️", nom: "Affiches", sous: "Publicités pour le magasin", couleur: "#b3121b" },
    { vue: "cerveau", icone: "💡", nom: "Mini-cerveau", sous: "Ce que l'assistante a appris", couleur: "#f2a900" },
    { vue: "modeles", icone: "🧠", nom: "Modèles IA", sous: "Choisir et installer l'IA", couleur: "#00897b" },
    { vue: "guide", icone: "📖", nom: "Guide", sous: "Comment ça marche", couleur: "#00838f" },
    { vue: "corbeille", image: "img/corbeille.png", icone: "🗑️", nom: "Corbeille", sous: "Fichiers supprimés", couleur: "#4a6a8a" },
    { vue: "reglages", image: "img/reglages.png", icone: "⚙️", nom: "Réglages", sous: "Thème, couleurs, voix", couleur: "#5f5f5f" },
  ];

  // ----------------------------------------------------------- navigation
  let vueActive = "bureau";
  function ouvrir(vue) {
    if (vue === "reglages") { $("ouvrirReglages").click(); return; }
    vueActive = vue;
    const ecran = vue === "corbeille" ? "documents" : vue;
    if (vue === "corbeille" || vue === "documents") { docs.mode = vue; docs.chemin = []; }
    document.querySelectorAll(".vue").forEach((v) => v.classList.toggle("cache", v.id !== "vue-" + ecran));
    document.querySelectorAll(".tache").forEach((t) => t.classList.toggle("actif", t.dataset.vue === vue));
    if (vue === "bureau") afficherBureau();
    if (ecran === "documents") docs.afficher();
    if (vue === "notes") notes.afficher();
    if (vue === "calcul") setTimeout(() => $("calcEcran").focus(), 30);
    if (vue === "alex") alex.afficher();
    if (vue === "modeles") modeles.afficher();
    if (vue === "agenda" && window.Agenda) window.Agenda.afficher();
    if (vue === "cerveau" && window.Cerveau) { window.Cerveau.afficher(); setTimeout(() => $("cerveauNouvelle").focus(), 30); }
    $("boutonAlex").classList.toggle("actif", vue === "alex");
    if (vue === "assistante") setTimeout(() => $("message").focus(), 30);
  }
  window.ouvrirAppli = ouvrir;

  function barreTaches() {
    const t = $("taches");
    t.querySelectorAll(".tache:not(.demarrer)").forEach((x) => x.remove());
    // Toutes les applis en bas, précédées d'un bouton « Bureau » (comme Windows).
    const tout = [{ vue: "bureau", icone: "🖥️", nom: "Bureau" }, ...APPLIS];
    for (const a of tout) {
      const b = document.createElement("button");
      b.className = "tache"; b.dataset.vue = a.vue; b.title = a.nom; b.setAttribute("aria-label", a.nom);
      b.innerHTML = a.image ? `<img src="${a.image}" alt="">` : `<span>${a.icone}</span>`;
      b.onclick = () => ouvrir(a.vue);
      t.appendChild(b);
    }
    $("boutonDemarrer").onclick = (e) => { e.stopPropagation(); menu.basculer(); };
  }

  function horloge() {
    const d = new Date();
    const heure = d.toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
    $("horloge").innerHTML = `<div>${heure}</div><div>${d.toLocaleDateString("fr-CA")}</div>`;
    // La grosse horloge du bureau, à droite.
    if ($("grosseHeure").textContent !== heure) {
      $("grosseHeure").textContent = heure;
      const date = d.toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
      $("grosseDate").textContent = date.charAt(0).toUpperCase() + date.slice(1);
    }
  }

  // --------------------------------------------------------------- bureau
  function afficherBureau() {
    const r = A.reglages();
    const h = new Date().getHours();
    const question = h >= 18 || h < 5 ? "On termine quelque chose ensemble ?" : "Prête à travailler ?";
    const fete = window.Fete && window.Fete.estFete();
    A.ecrireDoucement($("bureauSalut"), fete ? (r.prenom ? `Bonne fête ${r.prenom} ! 🎂` : "Bonne fête ! 🎂") : (r.prenom ? `Salut ${r.prenom} ! ` : "Salut ! ") + question);
    if (window.Agenda) window.Agenda.resumeBureau();
    const date = new Date().toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
    $("bureauDate").textContent = date.charAt(0).toUpperCase() + date.slice(1);
    const rec = $("bureauRecents");
    const liste = (A.documents() || []).slice(0, 6);
    rec.innerHTML = liste.length ? "" : `<span class="petit">Rien encore — demande à l'assistante une lettre, un tableau ou une affiche !</span>`;
    for (const d of liste) {
      const e = document.createElement("div");
      e.className = "recent";
      e.innerHTML = `<span>${d.icone || "📄"}</span><span class="recent-nom">${M.echapper(d.nom)}</span>`;
      e.title = "Dans : " + d.ou;
      rec.appendChild(e);
    }
  }

  // Icônes du bureau, comme sur un PC : image + nom, en colonnes à gauche.
  // Un seul clic suffit pour ouvrir (plus simple que le double-clic).
  function tuiles() {
    const t = $("tuiles");
    t.innerHTML = "";
    for (const a of APPLIS) {
      const b = document.createElement("button");
      b.className = "icone-bureau";
      b.setAttribute("role", "listitem");
      b.title = a.sous;
      b.style.setProperty("--c", a.couleur);
      const ic = a.image ? `<img src="${a.image}" alt="">` : `<span class="icone-emoji">${a.icone}</span>`;
      b.innerHTML = `${ic}<span class="icone-nom">${a.nom}</span>`;
      b.onclick = () => ouvrir(a.vue);
      t.appendChild(b);
    }
  }

  // ---------------------------------------------------------- Menu Démarrer
  const menu = {
    ouvert: false,
    basculer(force) {
      this.ouvert = force ?? !this.ouvert;
      $("menuDemarrer").classList.toggle("cache", !this.ouvert);
      $("boutonDemarrer").setAttribute("aria-expanded", String(this.ouvert));
      if (this.ouvert) { $("menuRecherche").value = ""; this.remplir(""); setTimeout(() => $("menuRecherche").focus(), 30); }
    },
    remplir(filtre) {
      const f = filtre.trim().toLowerCase();
      const z = $("menuApplis");
      z.innerHTML = "";
      const liste = [...APPLIS, { vue: "alex", icone: "🛠️", nom: "Espace technique", sous: "Réglages avancés" }]
        .filter((a) => !f || (a.nom + " " + a.sous).toLowerCase().includes(f));
      for (const a of liste) {
        const b = document.createElement("button");
        b.className = "menu-appli";
        b.innerHTML = (a.image ? `<img src="${a.image}" alt="">` : `<span>${a.icone}</span>`) + `<span>${a.nom}</span>`;
        b.onclick = () => { this.basculer(false); ouvrir(a.vue); };
        z.appendChild(b);
      }
      if (!liste.length) z.innerHTML = `<span class="petit">Aucune appli ne correspond. Essaie de le demander à l'assistante !</span>`;
      const r = $("menuRecents");
      const docsRec = (A.documents() || []).slice(0, 4);
      r.innerHTML = docsRec.length ? "" : `<span class="petit">Tes derniers fichiers apparaîtront ici.</span>`;
      for (const d of docsRec) {
        const e = document.createElement("div");
        e.className = "recent";
        e.innerHTML = `<span>${d.icone || "📄"}</span><span class="recent-nom">${M.echapper(d.nom)}</span>`;
        r.appendChild(e);
      }
      const prenom = A.reglages().prenom || "Moi";
      $("menuNom").textContent = prenom;
      $("menuAvatar").textContent = prenom.charAt(0).toUpperCase();
    },
  };
  $("menuRecherche").addEventListener("input", (e) => menu.remplir(e.target.value));
  $("menuRecherche").addEventListener("keydown", (e) => {
    if (e.key === "Enter") { const p = document.querySelector(".menu-appli"); if (p) p.click(); }
    if (e.key === "Escape") menu.basculer(false);
  });
  document.addEventListener("click", (e) => { if (menu.ouvert && !e.target.closest("#menuDemarrer")) menu.basculer(false); });
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && menu.ouvert) menu.basculer(false); });
  $("widgetAssistante").onclick = () => ouvrir("assistante");

  // -------------------------------------------------------- Mes documents
  const ICONES = { docx: "📄", doc: "📄", xlsx: "📊", xls: "📊", csv: "📊", pdf: "📕", png: "🖼️", jpg: "🖼️", jpeg: "🖼️", gif: "🖼️", webp: "🖼️", mp4: "🎬", webm: "🎬", txt: "📝", zip: "🗜️" };
  const taille = (o) => o == null ? "" : o < 1024 ? o + " o" : o < 1048576 ? Math.round(o / 1024) + " Ko" : (o / 1048576).toFixed(1).replace(".", ",") + " Mo";

  const docs = {
    chemin: [],
    mode: "documents",          // ou "corbeille"
    async afficher() {
      const corbeille = this.mode === "corbeille";
      $("docsTitre").innerHTML = corbeille ? `<img src="img/corbeille.png" alt="" class="titre-image"> Corbeille` : "📁 Mes documents";
      const e = Outils.etatDossier();
      $("docsBrancher").classList.toggle("cache", !!e.nom);
      $("docsReconnecter").classList.toggle("cache", !e.nom || e.pret);
      const pret = e.nom && e.pret;
      $("docsListe").classList.toggle("cache", !pret);
      ["docsHaut", "docsNouveau", "docsAjouter"].forEach((id) => { $(id).classList.toggle("cache", corbeille); $(id).disabled = !pret; });
      $("docsVider").classList.toggle("cache", !corbeille || !pret);
      $("docsHaut").disabled = !pret || !this.chemin.length;
      $("docsPied").textContent = corbeille
        ? "Les fichiers supprimés attendent ici. « Restaurer » les remet dans Mes documents."
        : "Astuce : glisse des fichiers de l'ordinateur dans cette fenêtre pour les copier sur la clé.";
      $("docsChemin").textContent = pret ? (corbeille ? [e.nom, Outils.CORBEILLE] : [e.nom, ...this.chemin]).join("  ›  ") : "";
      if (!pret) return;
      const l = $("docsListe");
      const chemin = corbeille ? Outils.CORBEILLE : this.chemin.join("/");
      let items;
      try {
        if (corbeille) await Outils.creerDossier(Outils.CORBEILLE);
        items = await Outils.entrees(chemin);
      } catch (err) { l.innerHTML = `<div class="vide">Impossible de lire ce dossier : ${M.echapper(err.message)}</div>`; return; }
      // La corbeille a sa propre icône sur le bureau : on ne la montre pas dans la liste.
      if (!corbeille && !this.chemin.length) items = items.filter((it) => !(it.dossier && it.nom === Outils.CORBEILLE));
      l.innerHTML = items.length ? "" : corbeille
        ? `<div class="vide">La corbeille est vide. 👍</div>`
        : `<div class="vide">Ce dossier est vide. Glisse des fichiers ici, ou clique sur « Ajouter des fichiers ».</div>`;
      for (const it of items) {
        const ext = it.nom.split(".").pop().toLowerCase();
        const ligne = document.createElement("div");
        ligne.className = "doc";
        ligne.setAttribute("role", "listitem");
        ligne.tabIndex = 0;
        ligne.innerHTML = `<span class="doc-icone">${it.dossier ? "📁" : ICONES[ext] || "📄"}</span>
          <span class="doc-nom">${M.echapper(it.nom)}</span>
          <span class="doc-info">${it.dossier ? "Dossier" : taille(it.taille)}</span>
          <span class="doc-info">${it.date ? new Date(it.date).toLocaleDateString("fr-CA") : ""}</span>
          <span class="doc-actions"></span>`;
        const act = ligne.querySelector(".doc-actions");
        const agir = async (fn, ok) => { try { await fn(); if (ok) $("docsPied").textContent = ok; } catch (err) { alert(err.message); } this.afficher(); };
        if (corbeille) {
          if (!it.dossier) act.appendChild(this.bouton("↩ Restaurer", () => agir(() => Outils.restaurer(it.nom), `✔ « ${it.nom} » est de retour dans Mes documents.`)));
        } else if (it.dossier) {
          const ouvrirDossier = () => { this.chemin.push(it.nom); this.afficher(); };
          ligne.ondblclick = ouvrirDossier;
          ligne.onkeydown = (ev) => ev.key === "Enter" && ouvrirDossier();
          act.appendChild(this.bouton("Ouvrir", ouvrirDossier));
          act.appendChild(this.bouton("🗑️", () => agir(() => Outils.supprimerDossierVide(chemin, it.nom)), "Supprimer ce dossier (s'il est vide)"));
        } else {
          const ouvrirFichier = async () => { const f = await Outils.fichierDuDossier(chemin, it.nom); A.ouvrirFichier(f, it.nom, Outils.etatDossier().estCle ? chemin : null); };
          ligne.ondblclick = ouvrirFichier;
          act.appendChild(this.bouton("Ouvrir", ouvrirFichier, "Afficher le fichier"));
          if (/^(png|jpe?g|gif|webp|bmp|docx|xlsx|xls|txt|csv|md)$/.test(ext)) {
            act.appendChild(this.bouton("💬 À l'assistante", async () => {
              const f = await Outils.fichierDuDossier(chemin, it.nom);
              ouvrir("assistante");
              await A.ajouterFichiers([f]);
              A.ecrire(/^(png|jpe?g|gif|webp|bmp)$/.test(ext) ? "Regarde cette photo et dis-moi ce que tu vois." : "Lis ce document et fais-m'en un résumé.");
            }, "Envoyer ce fichier à l'assistante"));
          }
          act.appendChild(this.bouton("🗑️", () => agir(() => Outils.mettreALaCorbeille(chemin, it.nom), `« ${it.nom} » est dans la corbeille.`), "Mettre à la corbeille"));
        }
        l.appendChild(ligne);
      }
    },
    bouton(texte, action, titre) {
      const b = document.createElement("button");
      b.className = "bouton petit"; b.textContent = texte; if (titre) b.title = titre;
      b.onclick = (ev) => { ev.stopPropagation(); action(); };
      return b;
    },
    async copier(fichiers) {
      if (!fichiers.length || this.mode === "corbeille") return;
      try {
        const noms = await Outils.copierDans(this.chemin.join("/"), fichiers);
        $("docsPied").textContent = `✔ Copié sur la clé : ${noms.join(", ")}`;
      } catch (e) { $("docsPied").textContent = "Copie impossible : " + e.message; }
      this.afficher();
    },
  };

  $("docsChoisir").onclick = async () => {
    try { await Outils.choisirDossier(); } catch (e) { if (e.name !== "AbortError") alert(e.message); }
    docs.chemin = []; docs.afficher(); A.afficherDossier();
  };
  $("docsReconnecterBtn").onclick = async () => { try { await Outils.reconnecterDossier(); } catch {} docs.afficher(); A.afficherDossier(); };
  $("docsHaut").onclick = () => { docs.chemin.pop(); docs.afficher(); };
  $("docsVider").onclick = async () => {
    if (!confirm("Vider la corbeille ? Les fichiers seront effacés pour de bon.")) return;
    try { const n = await Outils.viderCorbeille(); $("docsPied").textContent = `Corbeille vidée (${n} élément${n > 1 ? "s" : ""}).`; } catch (e) { alert(e.message); }
    docs.afficher();
  };
  $("docsAjouter").onclick = () => $("docsFichiers").click();
  $("docsFichiers").onchange = (e) => { docs.copier([...e.target.files]); e.target.value = ""; };
  $("docsNouveau").onclick = async () => {
    const nom = prompt("Nom du nouveau dossier :", "Nouveau dossier");
    if (!nom) return;
    try { await Outils.creerDossier([...docs.chemin, nom].join("/")); } catch (e) { alert(e.message); }
    docs.afficher();
  };
  const zoneDocs = $("docsZone");
  zoneDocs.addEventListener("dragover", (e) => { e.preventDefault(); zoneDocs.classList.add("depot"); });
  zoneDocs.addEventListener("dragleave", (e) => { if (!zoneDocs.contains(e.relatedTarget)) zoneDocs.classList.remove("depot"); });
  zoneDocs.addEventListener("drop", (e) => { e.preventDefault(); zoneDocs.classList.remove("depot"); docs.copier([...e.dataTransfer.files]); });

  // ---------------------------------------------------------- Calculatrice
  const calc = {
    hist: JSON.parse(localStorage.getItem("calc-hist") || "[]"),
    calculer() {
      const v = $("calcEcran").value.trim();
      if (!v) { $("calcResultat").innerHTML = "&nbsp;"; return null; }
      try {
        const r = Outils.calculer(v);
        $("calcResultat").textContent = "= " + r.toLocaleString("fr-CA", { maximumFractionDigits: 6 });
        return r;
      } catch { $("calcResultat").textContent = "…"; return null; }
    },
    garder(ligne) {
      this.hist.unshift(ligne);
      this.hist = this.hist.slice(0, 30);
      try { localStorage.setItem("calc-hist", JSON.stringify(this.hist)); } catch {}
      this.afficherHist();
    },
    afficherHist() {
      $("calcHist").innerHTML = this.hist.length ? this.hist.map((h) => `<div>${M.echapper(h)}</div>`).join("") : `<span class="petit">Tes calculs apparaîtront ici.</span>`;
    },
    taxes(inclus) {
      const r = this.calculer();
      if (r === null) { $("calcDetail").textContent = "Écris d'abord un montant."; return; }
      const t = Outils.taxes(r, inclus);
      const f = (x) => Outils.argent.format(x);
      $("calcDetail").innerHTML = `<div>Avant taxes <strong>${f(t.avant)}</strong></div><div>TPS (5 %) <strong>${f(t.tps)}</strong></div><div>TVQ (9,975 %) <strong>${f(t.tvq)}</strong></div><div class="total">Total <strong>${f(t.total)}</strong></div>`;
      this.garder(`${f(r)} ${inclus ? "taxes incluses" : "+ taxes"} → avant ${f(t.avant)}, total ${f(t.total)}`);
    },
  };
  const TOUCHES = ["7", "8", "9", "÷", "4", "5", "6", "×", "1", "2", "3", "−", "0", ",", "%", "+", "(", ")", "⌫", "="];
  TOUCHES.forEach((k) => {
    const b = document.createElement("button");
    b.className = "bouton touche" + (k === "=" ? " principal" : "");
    b.textContent = k;
    b.onclick = () => {
      const e = $("calcEcran");
      if (k === "=") {
        const r = calc.calculer();
        if (r !== null) { calc.garder(`${e.value} = ${r.toLocaleString("fr-CA", { maximumFractionDigits: 6 })}`); e.value = String(r).replace(".", ","); }
      } else if (k === "⌫") e.value = e.value.slice(0, -1);
      else e.value += k === "−" ? "-" : k;
      calc.calculer();
      e.focus();
    };
    $("calcTouches").appendChild(b);
  });
  $("calcEcran").addEventListener("input", () => calc.calculer());
  $("calcEcran").addEventListener("keydown", (e) => {
    if (e.key === "Enter") document.querySelector(".touche.principal").click();
    if (e.key === "Escape") { e.target.value = ""; calc.calculer(); $("calcDetail").innerHTML = ""; }
  });
  $("calcPlusTaxes").onclick = () => calc.taxes(false);
  $("calcTaxesIncluses").onclick = () => calc.taxes(true);
  calc.afficherHist();

  // ------------------------------------------------------------ Bloc-notes
  const notes = {
    liste: JSON.parse(localStorage.getItem("notes") || "[]"),
    courante: null,
    sauver() { try { localStorage.setItem("notes", JSON.stringify(this.liste)); } catch {} },
    afficher() {
      if (!this.liste.length) this.nouvelle(false);
      if (!this.courante) this.courante = this.liste[0];
      const l = $("notesListe");
      l.innerHTML = "";
      for (const n of this.liste) {
        const e = document.createElement("div");
        e.className = "element" + (n === this.courante ? " actif" : "");
        e.tabIndex = 0;
        e.innerHTML = `<span class="element-texte">${M.echapper(n.titre || "Sans titre")}<span class="element-sous">${new Date(n.maj).toLocaleDateString("fr-CA")}</span></span><button class="suppr" title="Supprimer" aria-label="Supprimer">🗑️</button>`;
        e.onclick = (ev) => { if (ev.target.closest(".suppr")) return; this.courante = n; this.afficher(); };
        e.querySelector(".suppr").onclick = () => {
          if (!confirm(`Supprimer la note « ${n.titre || "Sans titre"} » ?`)) return;
          this.liste = this.liste.filter((x) => x !== n);
          if (this.courante === n) this.courante = null;
          this.sauver(); this.afficher();
        };
        l.appendChild(e);
      }
      $("noteTitre").value = this.courante.titre;
      $("noteTexte").value = this.courante.texte;
    },
    nouvelle(afficher = true) {
      const n = { titre: "", texte: "", maj: Date.now() };
      this.liste.unshift(n);
      this.courante = n;
      this.sauver();
      if (afficher) { this.afficher(); $("noteTitre").focus(); }
    },
    modifier() {
      if (!this.courante) return;
      this.courante.titre = $("noteTitre").value;
      this.courante.texte = $("noteTexte").value;
      this.courante.maj = Date.now();
      this.sauver();
      const actif = document.querySelector("#notesListe .element.actif .element-texte");
      if (actif) actif.firstChild.textContent = this.courante.titre || "Sans titre";
    },
  };
  $("noteTitre").addEventListener("input", () => notes.modifier());
  $("noteTexte").addEventListener("input", () => notes.modifier());
  $("notesNouvelle").onclick = () => notes.nouvelle();
  $("notesWord").onclick = async () => {
    const n = notes.courante;
    if (!n || !n.texte.trim()) { alert("La note est vide."); return; }
    const blob = await Outils.docxDepuisMarkdown(n.titre, n.texte);
    const f = await Outils.enregistrer(blob, Outils.nomPropre(n.titre || "Note", "docx"));
    A.noterDocument({ ...f, icone: "📄" });
    alert(`Document Word créé : ${f.nom}\n(dans ${f.ou})`);
  };
  $("notesAssistante").onclick = () => {
    const n = notes.courante;
    if (!n || !n.texte.trim()) { alert("Écris d'abord quelque chose dans la note."); return; }
    ouvrir("assistante");
    A.ecrire(`Améliore ce texte (fautes, clarté, ton professionnel) et redonne-le-moi prêt à utiliser :\n\n${n.texte}`, true);
  };

  // --------------------------------------------------------------- Affiches
  let affichePhoto = null;
  let afficheBlob = null;
  const form = $("afficheForm");
  form.photo.onchange = () => {
    const f = form.photo.files[0];
    if (!f) { affichePhoto = null; return; }
    const r = new FileReader();
    r.onload = () => { affichePhoto = r.result; };
    r.readAsDataURL(f);
  };
  function donneesAffiche() {
    const v = (n) => form[n].value.trim();
    return {
      accroche: v("accroche"), titre: v("titre"), sous_titre: v("sous_titre"), prix: v("prix"), appel: v("appel"),
      points: v("points").split("\n").map((s) => s.trim()).filter(Boolean),
      couleur: form.couleur.value, format: form.format.value, photo: affichePhoto ? 1 : undefined,
    };
  }
  async function apercuAffiche() {
    const d = donneesAffiche();
    if (!d.titre) { form.titre.focus(); form.titre.reportValidity(); return null; }
    afficheBlob = await Outils.creerAffiche(d, affichePhoto ? [affichePhoto] : [], A.reglages().coordonnees);
    const z = $("afficheApercuZone");
    z.innerHTML = "";
    const img = new Image();
    img.src = URL.createObjectURL(afficheBlob);
    img.alt = "Aperçu de l'affiche";
    z.appendChild(img);
    return afficheBlob;
  }
  $("afficheApercu").onclick = apercuAffiche;
  form.onsubmit = async (e) => {
    e.preventDefault();
    const b = await apercuAffiche();
    if (!b) return;
    const f = await Outils.enregistrer(b, Outils.nomPropre("Affiche " + form.titre.value.trim(), "png"));
    A.noterDocument({ ...f, icone: "🖼️" });
    const note = document.createElement("div");
    note.className = "petit";
    note.style.marginTop = "8px";
    note.textContent = `✔ Enregistrée : ${f.nom} (dans ${f.ou})`;
    $("afficheApercuZone").appendChild(note);
  };

  // ------------------------------------------------------------------ Guide
  const GUIDE = [
    { icone: "👋", titre: "Bienvenue dans ton mini-ordinateur", vue: null, etapes: [
      "Tout ce qu'il y a ici fonctionne <strong>sur cet ordinateur, sans internet</strong>. Rien de ce que tu écris ne sort d'ici.",
      "Le <strong>bureau</strong> montre tes applications. Clique sur une grosse icône pour l'ouvrir.",
      "La <strong>barre du bas</strong> fonctionne comme dans Windows : clique sur une icône pour passer d'une appli à l'autre, et sur le petit logo à gauche pour revenir au bureau.",
    ] },
    { icone: "🧠", titre: "Installer ou changer l'intelligence artificielle", vue: "modeles", etapes: [
      "La première fois, l'appli <strong>Modèles IA</strong> s'ouvre toute seule : elle a regardé ton ordinateur et te conseille un modèle <strong>✅ Recommandé</strong>.",
      "Clique sur <strong>Installer</strong> et garde la fenêtre ouverte : une barre montre l'avancement. Il faut internet une seule fois.",
      "Plus tard, tu peux installer un autre modèle et cliquer sur <strong>Utiliser ce modèle</strong> pour changer.",
    ] },
    { icone: "💬", titre: "Parler à l'assistante", vue: "assistante", etapes: [
      "Écris ta demande en bas, comme un texto à une collègue, puis appuie sur <strong>Entrée</strong>.",
      "Les boutons de l'accueil te donnent des idées : courriel à un client, rappel de paiement, corriger un texte…",
      "Sous chaque réponse : <strong>📋 Copier</strong> pour coller ailleurs (Outlook, Facebook), <strong>🔊 Écouter</strong> pour l'entendre, <strong>📄 Word</strong> et <strong>📊 Excel</strong> pour en faire un fichier.",
      "Pour lui montrer une photo ou un document, clique sur <strong>📎</strong>, ou colle une image avec <strong>Ctrl + V</strong>.",
      "Elle peut se tromper : <strong>relis</strong> avant d'envoyer quelque chose d'important.",
    ] },
    { icone: "📄", titre: "Créer des lettres, tableaux, affiches et vidéos", vue: "assistante", etapes: [
      "Demande-le simplement : « Fais-moi une lettre Word pour… », « Fais un tableau Excel des commandes », « Fais une affiche pour la promo d'hiver », « Fais une vidéo avec mes photos ».",
      "Le fichier est créé tout seul et rangé dans <strong>Mes documents</strong>. Le bouton <strong>Ouvrir</strong> l'affiche ici ; pour le modifier dans Word ou Excel, ouvre-le depuis la clé dans l'Explorateur Windows.",
      "Les <strong>calculs et les taxes</strong> sont faits par l'ordinateur, pas par l'IA : ils sont exacts.",
    ] },
    { icone: "🧮", titre: "Calculatrice avec taxes", vue: "calcul", etapes: [
      "Écris un calcul (ex. <em>149,99 × 2</em>) avec le clavier ou les touches.",
      "<strong>＋ Taxes</strong> ajoute la TPS et la TVQ. <strong>Taxes incluses ?</strong> retrouve le montant avant taxes.",
      "Tes derniers calculs restent dans l'historique.",
    ] },
    { icone: "📝", titre: "Bloc-notes", vue: "notes", etapes: [
      "Écris tes notes et brouillons : c'est <strong>enregistré tout seul</strong>.",
      "<strong>💬 Améliorer avec l'assistante</strong> corrige et embellit ton texte. <strong>📄 Mettre dans Word</strong> en fait un document.",
    ] },
    { icone: "🖼️", titre: "Créateur d'affiches", vue: "affiche", etapes: [
      "Remplis le titre, le prix et les points forts, ajoute une photo du produit.",
      "Choisis la couleur et le format (Facebook ou à imprimer), puis <strong>👀 Aperçu</strong>.",
      "<strong>💾 Enregistrer</strong> met l'affiche dans Mes documents, prête à publier ou à imprimer.",
    ] },
    { icone: "📁", titre: "Mes documents et la corbeille", vue: "documents", etapes: [
      "La première fois, clique sur <strong>Choisir le dossier</strong> et sélectionne <strong>Mes documents</strong> sur la clé.",
      "<strong>📥 Ajouter des fichiers</strong> copie des fichiers de l'ordinateur vers la clé. Tu peux aussi les <strong>glisser</strong> dans la fenêtre.",
      "Tu retrouves aussi ce dossier dans l'<strong>Explorateur Windows</strong> : ouvre la clé, puis « Mes documents ».",
      "Le bouton 🗑️ met un fichier à la <strong>corbeille</strong>. Dans la Corbeille, <strong>↩ Restaurer</strong> le récupère ; il n'est effacé que si tu vides la corbeille.",
    ] },
    { icone: "💡", titre: "Mini-cerveau", vue: "cerveau", etapes: [
      "L'assistante peut se tromper. Quand ça arrive, clique sur <strong>🧠 Corriger</strong> sous sa réponse et écris la bonne façon de faire.",
      "Tu peux aussi simplement lui dire « Non, c'est plutôt… » : elle retient la leçon toute seule.",
      "Elle relit ses leçons avant chaque réponse pour ne pas refaire deux fois la même erreur. Tu les vois toutes dans l'appli <strong>💡 Mini-cerveau</strong>, et tu peux en ajouter, les modifier ou les effacer.",
    ] },
    { icone: "📅", titre: "Agenda", vue: "agenda", etapes: [
      "Clique sur <strong>＋ Rendez-vous</strong>, écris quoi, choisis la date et l'heure. Tu peux aussi <strong>double-cliquer</strong> sur une journée.",
      "Choisis <strong>Me le rappeler</strong> : une petite fenêtre et un son t'avertissent 15 minutes avant (le mini-ordinateur doit être ouvert).",
      "Les rendez-vous du jour s'affichent sur le bureau, sous « 📅 Aujourd'hui ». Les jours fériés du Québec sont déjà dans le calendrier.",
      "Tu peux aussi demander à l'assistante : « Mets-moi un rendez-vous vendredi à 10 h avec le fournisseur ».",
    ] },
    { icone: "⚙️", titre: "Réglages", vue: "reglages", etapes: [
      "<strong>Thème</strong> : clair, sombre, ou automatique (clair le jour, sombre le soir).",
      "Choisis ta <strong>couleur</strong> et ton <strong>fond d'écran</strong>.",
      "Ajoute ta <strong>signature</strong> de courriel et les <strong>coordonnées</strong> du magasin : l'assistante s'en servira.",
      "Coche <strong>Lire les réponses à voix haute</strong> pour tout entendre. <strong>A− / A+</strong> dans l'assistante change la grosseur du texte.",
      "<strong>🔒 Mot de passe</strong> : crée un mot de passe et ton mini-ordinateur s'ouvrira sur un écran verrouillé, comme Windows. Le menu Démarrer a un bouton <strong>Verrouiller</strong>.",
    ] },
    { icone: "🛟", titre: "Si quelque chose ne marche pas", vue: null, etapes: [
      "Ferme la fenêtre, puis double-clique de nouveau sur <strong>▶️ START</strong> sur la clé.",
      "Si ça continue : redémarre l'ordinateur et réessaie.",
      "Avant de retirer la clé, <strong>ferme la fenêtre</strong> et attends quelques secondes.",
      (window.CONFIG_ASSISTANTE && window.CONFIG_ASSISTANTE.aide) ? "Et sinon… appelle " + window.CONFIG_ASSISTANTE.aide + " ! 😊" : "Et sinon… demande de l'aide à la personne qui t'a préparé la clé. 😊",
    ] },
  ];
  function afficherGuide() {
    const g = $("guide");
    g.innerHTML = "";
    GUIDE.forEach((s, i) => {
      const c = document.createElement("article");
      c.className = "guide-carte";
      c.innerHTML = `<div class="guide-num">${i + 1}</div><h2><span>${s.icone}</span> ${s.titre}</h2><ol>${s.etapes.map((e) => `<li>${e}</li>`).join("")}</ol>`;
      if (s.vue) {
        const b = document.createElement("button");
        b.className = "bouton"; b.textContent = "Essayer →";
        b.onclick = () => ouvrir(s.vue);
        c.appendChild(b);
      }
      g.appendChild(c);
    });
  }
  afficherGuide();
  $("guideFini").onclick = () => { try { localStorage.setItem("guide-vu", "1"); } catch {} ouvrir("bureau"); };

  // ------------------------------------------------------------ Modèles IA
  // Le service local (lanceur PowerShell) lit l'ordinateur, liste les modèles
  // installés, installe et active un modèle. Première fois : il sert aussi la
  // page (même adresse). Ensuite : il écoute à côté du moteur, port 8766.
  let SERVICE = "http://127.0.0.1:8766";
  const service = async (chemin, options) => {
    const r = await fetch(SERVICE + "/service/" + chemin, Object.assign({ cache: "no-store" }, options));
    if (!r.ok) throw new Error("Service indisponible (" + r.status + ")");
    return r.json();
  };
  const modeles = {
    catalogue: [], machine: null, liste: null, etat: null, minuterie: null, finVue: false,
    async charger() {
      if (!this.catalogue.length) this.catalogue = (await (await fetch("modeles.json", { cache: "no-store" })).json()).modeles;
      try { this.machine = await service("machine"); } catch { this.machine = null; }
      try { this.liste = await service("modeles"); } catch { this.liste = null; }
      try { this.etat = await service("installation"); } catch { this.etat = null; }
    },
    compat(m) {
      const mc = this.machine;
      const installe = this.liste && this.liste.installes.includes(m.id);
      if (!mc || !mc.ram_go) return { niveau: "inconnu", texte: "Je n'ai pas pu lire l'ordinateur" };
      // Ce qui est déjà téléchargé (installation coupée) ne compte plus.
      const deja = (this.liste && this.liste.deja && this.liste.deja[m.id]) || 0;
      const besoin = Math.max(0, m.taille_go - deja) + 0.6;
      if (!installe && mc.espace_go && mc.espace_go < besoin) return { niveau: "non", texte: `Pas assez d'espace sur la clé (il faut ${Math.ceil(besoin)} Go)` };
      if (mc.ram_go < m.ram_min * 0.85) return { niveau: "non", texte: `Ton PC n'a pas assez de mémoire (RAM) pour ce modèle : il faut au moins ${m.ram_min} Go, ton PC en a ${String(mc.ram_go).replace(".", ",")} Go.` };
      if (mc.ram_go >= m.ram_confort || (m.vram_conseillee > 0 && mc.vram_go >= m.vram_conseillee && mc.ram_go >= m.ram_min)) return { niveau: "ok", texte: "Parfait pour cet ordinateur" };
      return { niveau: "lent", texte: "Va fonctionner, mais lentement" };
    },
    recommande() {
      const ok = this.catalogue.filter((m) => this.compat(m).niveau === "ok");
      return (ok[ok.length - 1] || this.catalogue.find((m) => this.compat(m).niveau === "lent") || this.catalogue[0] || {}).id;
    },
    async afficher() {
      await this.charger();
      this.dessiner();
      this.suivre();
    },
    dessiner() {
      const mc = this.machine;
      $("machineInfos").innerHTML = mc ? [
        ["🧠 Mémoire", mc.ram_go ? `${String(mc.ram_go).replace(".", ",")} Go` : "inconnue"],
        ["🎮 Carte graphique", mc.gpu ? `${M.echapper(mc.gpu)} (${String(mc.vram_go).replace(".", ",")} Go)` : "aucune carte dédiée"],
        ["⚙️ Processeur", `${M.echapper(mc.processeur || "?")}${mc.coeurs ? ` — ${mc.coeurs} cœurs` : ""}`],
        ["💾 Espace libre sur la clé", mc.espace_go ? `${String(mc.espace_go).replace(".", ",")} Go` : "?"],
      ].map(([t, v]) => `<div><span class="petit">${t}</span><strong>${v}</strong></div>`).join("")
        : `<span class="petit">Le moteur de lecture du PC ne répond pas. Ferme la fenêtre et relance « ▶️ START ».</span>`;
      const aucun = !this.liste || !this.liste.installes.length;
      $("modelesIntro").classList.toggle("cache", !aucun);
      $("modelesPanne").classList.toggle("cache", !(this.liste && this.liste.panne));
      const enCours = this.etat && this.etat.etat === "encours";
      const reco = this.recommande();
      const z = $("cartesModeles");
      z.innerHTML = "";
      for (const m of this.catalogue) {
        const c = this.compat(m);
        const installe = this.liste && this.liste.installes.includes(m.id);
        const actif = this.liste && this.liste.actif === m.id;
        const carte = document.createElement("article");
        carte.className = "carte-modele" + (actif ? " actif" : "") + (m.id === reco && !installe ? " reco" : "");
        const badge = { ok: "✅", lent: "⚠️", non: "❌", inconnu: "❔" }[c.niveau];
        carte.innerHTML = `
          ${m.id === reco ? `<div class="ruban">✅ Recommandé</div>` : ""}
          <h3>${M.echapper(m.nom)}</h3>
          <div class="petit">${M.echapper(m.famille)} · environ ${String(m.taille_go).replace(".", ",")} Go</div>
          <p>${M.echapper(m.description)}</p>
          <div class="compat compat-${c.niveau}">${badge} ${M.echapper(c.texte)}</div>
          <div class="etat-modele">${actif ? "🟢 Utilisé en ce moment" : installe ? "💾 Installé sur la clé" : ""}</div>
          <div class="alex-boutons"></div>`;
        const zb = carte.querySelector(".alex-boutons");
        if (!installe) {
          const b = document.createElement("button");
          b.className = "bouton " + (m.id === reco ? "principal" : "");
          b.textContent = "⬇️ Installer";
          b.disabled = enCours || c.niveau === "non";
          if (c.niveau === "lent") b.title = "Il va fonctionner, mais lentement sur cet ordinateur.";
          b.onclick = () => this.installer(m, c);
          zb.appendChild(b);
        } else if (!actif) {
          const b = document.createElement("button");
          b.className = "bouton principal";
          b.textContent = "Utiliser ce modèle";
          b.disabled = enCours;
          b.onclick = () => this.activer(m);
          zb.appendChild(b);
        }
        z.appendChild(carte);
      }
      this.dessinerEtat();
    },
    dessinerEtat() {
      const e = this.etat;
      const zone = $("installation");
      this.pilule(e);
      if (!e || e.etat === "attente") { if (!this.finVue) zone.classList.add("cache"); return; }
      zone.classList.remove("cache");
      const titres = { encours: "⏳ Installation en cours", fini: "✅ C'est installé !", erreur: "⚠️ L'installation n'a pas fonctionné" };
      $("instTitre").textContent = titres[e.etat] || "Installation";
      if (e.etat === "encours" && e.etape) this.derniereEtape = e.etape;
      const etape = e.etat === "fini" ? 5 : (e.etape || this.derniereEtape || 1);
      // Les 4 étapes, cochées au fur et à mesure.
      const noms = ["Lire l'ordinateur", "Le moteur", "Le cerveau", "Les yeux (photos)"];
      $("instEtapes").innerHTML = noms.map((n, i) => {
        const k = i + 1;
        const classe = k < etape ? "fait" : k === etape && e.etat === "encours" ? "encours" : k === etape && e.etat === "erreur" ? "erreur" : "";
        const puce = classe === "fait" ? "✅" : classe === "encours" ? "⏳" : classe === "erreur" ? "⚠️" : "○";
        return `<li class="${classe}"><span class="puce">${puce}</span>${k}. ${n}</li>`;
      }).join("");
      $("instMessage").textContent = e.etat === "encours" ? `Étape ${etape} sur 4 — ${e.message || "…"}` : (e.message || "");
      const barre = $("instBarre");
      const p = e.etat === "fini" ? 100 : e.pourcent;
      barre.classList.toggle("indetermine", e.etat === "encours" && p == null);
      barre.style.width = p != null ? p + "%" : "35%";
      $("instProgres").setAttribute("aria-valuenow", p != null ? String(p) : "");
      $("instPourcent").textContent = p != null && e.etat !== "erreur" ? `${p} %` : "";
      $("instChiffres").textContent = e.etat === "encours" ? this.chiffres(e) : "";
      // Erreur : un bouton pour recommencer tout de suite (ce qui est déjà téléchargé est gardé).
      $("instReessayer").classList.toggle("cache", !(e.etat === "erreur" && e.modele));
      if (e.etat === "erreur") $("instConseil").classList.add("cache");
      $("instDetail").textContent = (e.machine || "") + (e.etat === "encours" ? " — Garde la fenêtre ouverte. Tu peux continuer à utiliser le reste du bureau." : "");
    },
    // « 1,2 Go sur 2,5 Go · 3,4 Mo/s · encore environ 6 min » + conseil si ça bloque.
    chiffres(e) {
      const conseil = $("instConseil");
      const go = (o) => o < 1073741824 ? `${Math.round(o / 1048576)} Mo` : `${(o / 1073741824).toFixed(o >= 10737418240 ? 0 : 1).replace(".", ",")} Go`;
      const recu = e.recu || 0, total = e.total || 0;
      if (!recu && !total) { conseil.classList.add("cache"); return ""; }
      const now = Date.now();
      const h = (this.historique = (this.historique || []).filter((x) => now - x.t < 15000 && x.recu <= recu));
      if (!h.length || h[h.length - 1].recu !== recu) h.push({ t: now, recu });
      if (!this.bouge || recu !== this.bouge.recu) this.bouge = { t: now, recu };
      let vitesse = 0;
      if (h.length > 1 && now - h[0].t > 1500) vitesse = (recu - h[0].recu) / ((now - h[0].t) / 1000);
      const morceaux = [total ? `${go(recu)} sur ${go(total)}` : `${go(recu)} reçus`];
      if (vitesse > 0) {
        const mo = vitesse / 1048576;
        morceaux.push(`${mo >= 10 ? Math.round(mo) : mo.toFixed(1).replace(".", ",")} Mo/s`);
        if (total > recu) {
          const s = (total - recu) / vitesse;
          const m = Math.round(s / 60);
          morceaux.push(s < 60 ? "encore moins d'une minute" : m < 60 ? `encore environ ${m} min` : `encore environ ${Math.floor(m / 60)} h ${String(m % 60).padStart(2, "0")}`);
        }
      }
      const fige = now - this.bouge.t > 60000;
      conseil.classList.toggle("cache", !fige);
      if (fige) conseil.textContent = "🐢 Rien n'a bougé depuis une minute. Vérifie que l'ordinateur est bien connecté à internet. Ne ferme pas la fenêtre : ça reprendra là où c'était rendu.";
      return morceaux.join(" · ");
    },
    // Petite pastille dans la barre des tâches : on voit l'avancement de partout.
    pilule(e) {
      const p = $("piluleInstallation");
      const encours = e && e.etat === "encours";
      p.classList.toggle("cache", !encours);
      if (!this.titreAvant) this.titreAvant = document.title;
      if (encours) {
        const txt = e.pourcent != null ? `${e.pourcent} %` : `étape ${e.etape || 1}/4`;
        $("pilulePourcent").textContent = txt;
        document.title = `⏳ ${txt} — installation de l'IA`;
      } else if (document.title.startsWith("⏳")) {
        document.title = this.titreAvant;
      }
    },
    suivre() {
      clearInterval(this.minuterie);
      this.minuterie = setInterval(async () => {
        const avant = this.etat && this.etat.etat;
        try { this.etat = await service("installation"); } catch { /* le service redémarre (passage au moteur) */ }
        this.dessinerEtat();
        // L'installation vient de s'arrêter (erreur) : on redessine les cartes
        // pour réactiver « Installer » / « Utiliser ce modèle » (réessayer).
        if (avant === "encours" && this.etat && this.etat.etat === "erreur") {
          try { this.liste = await service("modeles"); } catch {}
          this.dessiner();
        }
        if (this.etat && this.etat.etat === "fini") this.quandFini();
        else if (!this.etat || this.etat.etat !== "encours") { if (vueActive !== "modeles") clearInterval(this.minuterie); }
      }, 1000);
    },
    async installer(m, c) {
      if (c.niveau === "lent" && !confirm(`« ${m.nom} » va fonctionner sur cet ordinateur, mais lentement. L'installer quand même ?`)) return;
      if (!confirm(`Installer « ${m.nom} » (environ ${String(m.taille_go).replace(".", ",")} Go) ? Il faut internet pendant le téléchargement.`)) return;
      try { this.etat = await service("installer?id=" + encodeURIComponent(m.id), { method: "POST" }); } catch (e) { alert(e.message); return; }
      this.dessiner(); this.suivre();
      // On amène la progression sous ses yeux.
      setTimeout(() => $("installation").scrollIntoView({ behavior: "smooth", block: "center" }), 100);
    },
    async activer(m) {
      try { await service("activer?id=" + encodeURIComponent(m.id), { method: "POST" }); } catch (e) { alert(e.message); return; }
      $("installation").classList.remove("cache");
      $("instTitre").textContent = `🔄 Je passe à « ${m.nom} »…`;
      $("instMessage").textContent = "L'assistante se réveille avec son nouveau cerveau (de 20 secondes à une minute).";
      $("instBarre").classList.add("indetermine"); $("instBarre").style.width = "35%";
      this.attendreMoteur();
    },
    async quandFini() {
      if (this.finVue) return;
      this.finVue = true;
      clearInterval(this.minuterie);
      $("instMessage").textContent = "L'assistante se réveille… (de 20 secondes à une minute)";
      this.attendreMoteur();
    },
    attendreMoteur() {
      const essai = async () => {
        try {
          const r = await fetch("/health", { cache: "no-store" });
          if (r.ok) {
            SERVICE = "http://127.0.0.1:8766";
            $("instTitre").textContent = "✅ C'est prêt !";
            $("instMessage").textContent = "Ton assistante est prête à travailler.";
            $("instBarre").classList.remove("indetermine"); $("instBarre").style.width = "100%";
            $("instAller").classList.remove("cache");
            setTimeout(() => { this.finVue = false; this.charger().then(() => this.dessiner()); }, 1500);
            return;
          }
          // Réponse de notre petit serveur de secours : le moteur n'a pas
          // voulu démarrer avec ce modèle -> on le dit et on laisse choisir.
          // (Après un changement de modèle raté, le service est passé du port
          // 8766 à ce bureau de secours : on le retrouve à la même adresse.)
          if (r.status === 503) {
            const l = await fetch("/service/modeles", { cache: "no-store" }).then((x) => (x.ok ? x.json() : null)).catch(() => null);
            if (l && l.panne) {
              SERVICE = "";
              $("installation").classList.add("cache");
              this.finVue = false;
              await this.charger(); this.dessiner();
              return;
            }
          }
        } catch {}
        setTimeout(essai, 2000);
      };
      setTimeout(essai, 2000);
    },
  };
  $("instAller").onclick = () => ouvrir("assistante");
  $("instReessayer").onclick = async () => {
    const id = modeles.etat && modeles.etat.modele;
    if (!id) return;
    $("instReessayer").disabled = true;
    try { modeles.etat = await service("installer?id=" + encodeURIComponent(id), { method: "POST" }); modeles.historique = []; modeles.dessiner(); modeles.suivre(); }
    catch (e) { alert(e.message); }
    finally { $("instReessayer").disabled = false; }
  };
  $("piluleInstallation").onclick = () => ouvrir("modeles");
  $("bureauAgendaBloc").onclick = () => ouvrir("agenda");
  $("bureauAgendaBloc").onkeydown = (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); ouvrir("agenda"); } };

  // Icône « Mon assistante » sur le bureau Windows de l'ordinateur
  async function etatRaccourci() {
    try { return await service("raccourci-bureau"); } catch { return null; }
  }
  async function creerRaccourci(bouton) {
    try {
      const r = await service("raccourci-bureau", { method: "POST" });
      if (!r.ok) throw new Error();
      if (bouton) { bouton.textContent = "✔ C'est fait !"; bouton.disabled = true; }
      $("rRaccourciEtat").textContent = "✔ L'icône « Mon assistante » est sur le bureau de l'ordinateur.";
      $("widgetRaccourci").classList.add("cache");
    } catch { alert("Je n'ai pas pu mettre l'icône sur le bureau. Ferme la fenêtre et relance « ▶️ START »."); }
  }
  $("rRaccourci").onclick = () => creerRaccourci($("rRaccourci"));
  $("widgetRaccourci").onclick = () => creerRaccourci($("widgetRaccourci"));
  $("ouvrirReglages").addEventListener("click", async () => {
    const e = await etatRaccourci();
    if (e && e.existe) { $("rRaccourci").textContent = "✔ Déjà là"; $("rRaccourci").disabled = true; $("rRaccourciEtat").textContent = "✔ L'icône « Mon assistante » est sur le bureau de l'ordinateur."; }
  });
  // Sur le bureau du mini-ordi : on le propose tant que l'icône n'existe pas.
  setTimeout(async () => { const e = await etatRaccourci(); if (e && !e.existe) $("widgetRaccourci").classList.remove("cache"); }, 1500);

  // ----------------------------------------------------------- Espace Alex
  const VERSION = "1.0 — octobre 2026";
  const alex = {
    async moteur() {
      const z = $("alexMoteur");
      try {
        const h = await fetch("/health", { cache: "no-store" });
        let props = {};
        try { props = await (await fetch("/props", { cache: "no-store" })).json(); } catch {}
        const modele = String(props.model_path || props.default_generation_settings?.model || "").split(/[\\/]/).pop() || "inconnu";
        const ctx = props.default_generation_settings?.n_ctx || props.n_ctx || "?";
        const vision = props.modalities?.vision ? "oui" : (props.modalities ? "non" : "?");
        z.innerHTML = h.ok
          ? `<div>🟢 <strong>En marche</strong></div><div>Modèle : <code>${M.echapper(modele)}</code></div><div>Contexte : ${ctx} jetons · Vision : ${vision}</div>`
          : `<div>🟡 Le moteur se réveille (code ${h.status})</div>`;
      } catch { z.innerHTML = "<div>🔴 Moteur arrêté — relancer « ▶️ START »</div>"; }
    },
    afficher() {
      const a = A.avance();
      $("alexTemp").value = a.temperature; $("alexTempVal").textContent = Number(a.temperature).toFixed(2);
      $("alexMax").value = a.maxTokens; $("alexMemoire").value = a.messagesGardes; $("alexInstructions").value = a.instructions;
      $("alexInfos").innerHTML = `Version de la clé : ${VERSION}<br>Navigateur : ${M.echapper(navigator.userAgent.match(/Edg\/[\d.]+|Chrome\/[\d.]+/)?.[0] || "?")}<br>Conversations : ${A.conversations().length} · Documents créés : ${(A.documents() || []).length}<br>ti-lex · édition Famille — Produit du Québec.`;
      this.moteur();
    },
  };
  $("boutonAlex").onclick = () => ouvrir("alex");
  $("alexRafraichir").onclick = () => alex.moteur();
  $("alexTemp").oninput = () => ($("alexTempVal").textContent = Number($("alexTemp").value).toFixed(2));
  $("alexEnregistrer").onclick = () => {
    const v = { temperature: parseFloat($("alexTemp").value), maxTokens: parseInt($("alexMax").value, 10) || 1500,
      messagesGardes: Math.min(40, Math.max(2, parseInt($("alexMemoire").value, 10) || 12)), instructions: $("alexInstructions").value };
    try { localStorage.setItem("avance", JSON.stringify(v)); alert("Réglages enregistrés ✔"); } catch { alert("Enregistrement impossible."); }
  };
  $("alexDefaut").onclick = () => { try { localStorage.removeItem("avance"); } catch {} alex.afficher(); };
  const CLES = ["conversations", "documents", "reglages", "notes", "calc-hist", "avance", "guide-vu", "verrou", "agenda", "lecons"];
  $("alexExporter").onclick = () => {
    const data = { app: "mon-assistante", version: VERSION, date: new Date().toISOString(), donnees: {} };
    for (const k of CLES) { const v = localStorage.getItem(k); if (v !== null) data.donnees[k] = v; }
    const nom = `Sauvegarde assistante ${new Date().toLocaleDateString("fr-CA")}.json`;
    Outils.telecharger(new Blob([JSON.stringify(data)], { type: "application/json" }), nom);
  };
  $("alexImporter").onclick = () => $("alexFichier").click();
  $("alexFichier").onchange = async (e) => {
    const f = e.target.files[0]; e.target.value = "";
    if (!f) return;
    try {
      const data = JSON.parse(await f.text());
      if (data.app !== "mon-assistante" || !data.donnees) throw new Error("Ce fichier n'est pas une sauvegarde de l'assistante.");
      if (!confirm("Remplacer les conversations, notes et réglages actuels par cette sauvegarde ?")) return;
      for (const [k, v] of Object.entries(data.donnees)) if (CLES.includes(k)) localStorage.setItem(k, v);
      location.reload();
    } catch (err) { alert(err.message); }
  };
  $("alexFete").onclick = () => window.Fete && window.Fete.montrer();
  $("alexGuide").onclick = () => { try { localStorage.removeItem("guide-vu"); } catch {} alert("Le guide s'affichera au prochain démarrage."); };
  $("alexEffacerConv").onclick = () => {
    if (!confirm("Effacer toutes les conversations ? (Les fichiers dans Mes documents ne sont pas touchés.)")) return;
    localStorage.removeItem("conversations"); location.reload();
  };
  $("alexToutEffacer").onclick = () => {
    if (!confirm("Tout remettre à neuf : conversations, notes, réglages… (Les fichiers dans Mes documents restent.) Continuer ?")) return;
    for (const k of CLES) localStorage.removeItem(k);
    location.reload();
  };

  // ----------------------------------------------------------------- départ
  barreTaches();
  tuiles();
  horloge();
  setInterval(horloge, 1000);
  let dejaVu = false;
  try { dejaVu = localStorage.getItem("guide-vu") === "1"; } catch {}
  ouvrir(dejaVu ? "bureau" : "guide");
  if (!dejaVu) try { localStorage.setItem("guide-vu", "1"); } catch {}
  // Première fois (pas encore d'IA) : la page est servie par le service local
  // lui-même -> on lui parle à la même adresse et on ouvre « Modèles IA ».
  fetch("/service/modeles", { cache: "no-store" }).then((r) => {
    if (!r.ok) return;
    SERVICE = "";
    return r.json().then((l) => { if (!l.installes.length || l.panne) ouvrir("modeles"); });
  }).catch(() => {});
})();
