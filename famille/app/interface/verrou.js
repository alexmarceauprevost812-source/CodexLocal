// Mot de passe du mini-ordinateur : écran verrouillé comme Windows.
// Le mot de passe n'est jamais gardé tel quel : on garde seulement une
// empreinte (PBKDF2-SHA-256 avec un sel), dans le profil de la clé.
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const A = window.Assistante;
  const html = document.documentElement;
  const TOURS = 150000;

  const lire = () => { try { return JSON.parse(localStorage.getItem("verrou") || "null"); } catch { return null; } };
  const ecrire = (v) => {
    try { v ? localStorage.setItem("verrou", JSON.stringify(v)) : localStorage.removeItem("verrou"); return true; }
    catch { return false; }
  };
  const b64 = (buf) => btoa(String.fromCharCode(...new Uint8Array(buf)));
  const deB64 = (s) => Uint8Array.from(atob(s), (c) => c.charCodeAt(0));

  async function empreinte(mdp, sel, tours) {
    const cle = await crypto.subtle.importKey("raw", new TextEncoder().encode(mdp), "PBKDF2", false, ["deriveBits"]);
    return b64(await crypto.subtle.deriveBits({ name: "PBKDF2", hash: "SHA-256", salt: sel, iterations: tours }, cle, 256));
  }
  async function verifier(mdp) {
    const v = lire();
    if (!v) return true;
    return (await empreinte(mdp, deB64(v.sel), v.tours || TOURS)) === v.hash;
  }
  async function definir(mdp, indice) {
    const sel = crypto.getRandomValues(new Uint8Array(16));
    const ancien = lire() || {};
    return ecrire({ sel: b64(sel), hash: await empreinte(mdp, sel, TOURS), tours: TOURS, indice: indice || "", delai: ancien.delai || 0 });
  }

  // ------------------------------------------------------------ écran verrouillé
  let echecs = 0, minuterieAttente = null;
  // L'attente après 5 erreurs survit à un rechargement de la page.
  let attenteJusqua = (() => { try { return Number(localStorage.getItem("verrou-attente")) || 0; } catch { return 0; } })();
  const prenom = () => (A && A.reglages().prenom) || "";

  function heure() {
    const d = new Date();
    $("verrouHeure").textContent = d.toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
    $("verrouDate").textContent = d.toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long" });
  }
  function bloquerReste(oui) {
    for (const e of document.body.children) if (e.id !== "ecranVerrou" && e.tagName !== "SCRIPT") e.inert = oui;
  }
  function verrouiller() {
    if (!lire()) return;
    for (const d of document.querySelectorAll("dialog[open]")) d.close("annuler");
    if (!$("menuDemarrer").classList.contains("cache")) $("boutonDemarrer").click();
    try { window.speechSynthesis && speechSynthesis.cancel(); } catch {}
    html.classList.add("verrouille");
    bloquerReste(true);
    const p = prenom();
    $("verrouNom").textContent = p || "Bienvenue";
    $("verrouAvatar").textContent = (p || "🔒").charAt(0).toUpperCase();
    $("verrouMdp").value = ""; $("verrouMessage").textContent = "";
    heure();
    const salut = (p ? `Bon retour, ${p} ! ` : "Bon retour ! ") + "Entre ton mot de passe.";
    if (A && A.ecrireDoucement) A.ecrireDoucement($("verrouSalut"), salut); else $("verrouSalut").textContent = salut;
    if (Date.now() < attenteJusqua) compteARebours();
    setTimeout(() => $("verrouMdp").focus(), 50);
  }
  function deverrouiller() {
    html.classList.remove("verrouille");
    bloquerReste(false);
    echecs = 0;
    try { localStorage.removeItem("verrou-attente"); } catch {}
    activite();
  }
  function compteARebours() {
    clearInterval(minuterieAttente);
    const tic = () => {
      const s = Math.ceil((attenteJusqua - Date.now()) / 1000);
      if (s <= 0) { clearInterval(minuterieAttente); $("verrouOk").disabled = false; $("verrouMdp").disabled = false; $("verrouMessage").textContent = "Tu peux réessayer."; $("verrouMdp").focus(); return; }
      $("verrouMessage").textContent = `Trop d'essais. Attends ${s} secondes, puis réessaie.`;
    };
    $("verrouOk").disabled = true; $("verrouMdp").disabled = true;
    tic(); minuterieAttente = setInterval(tic, 1000);
  }
  $("verrouForm").addEventListener("submit", async (e) => {
    e.preventDefault();
    if (Date.now() < attenteJusqua) return;
    const mdp = $("verrouMdp").value;
    if (!mdp) { $("verrouMessage").textContent = "Écris ton mot de passe, puis appuie sur Entrée."; return; }
    $("verrouOk").disabled = true;
    let bon = false;
    try { bon = await verifier(mdp); } catch { bon = false; }
    $("verrouOk").disabled = false;
    if (bon) { deverrouiller(); return; }
    echecs++;
    $("verrouMdp").value = "";
    $("ecranVerrou").classList.remove("secoue"); void $("ecranVerrou").offsetWidth; $("ecranVerrou").classList.add("secoue");
    if (echecs % 5 === 0) {
      attenteJusqua = Date.now() + 30000;
      try { localStorage.setItem("verrou-attente", String(attenteJusqua)); } catch {}
      compteARebours(); return;
    }
    $("verrouMessage").textContent = "Ce n'est pas le bon mot de passe. Vérifie les majuscules et réessaie.";
    $("verrouMdp").focus();
  });
  $("verrouOublie").onclick = () => {
    const v = lire() || {};
    $("verrouMessage").textContent = v.indice
      ? `Ton indice : « ${v.indice} ». Sinon, demande à la personne qui t'a préparé la clé : elle peut le remettre à zéro.`
      : "Demande à la personne qui t'a préparé la clé : elle peut le remettre à zéro.";
  };

  // ---------------------------------------------- verrouillage après inactivité
  let derniere = Date.now();
  function activite() { derniere = Date.now(); }
  for (const t of ["pointerdown", "pointermove", "keydown", "wheel", "touchstart"]) document.addEventListener(t, activite, { passive: true, capture: true });
  setInterval(() => {
    const v = lire();
    if (!v || !v.delai || html.classList.contains("verrouille")) return;
    if (Date.now() - derniere >= v.delai * 60000) verrouiller();
  }, 10000);

  // ------------------------------------------------------------------ Réglages
  function afficherEtat() {
    const v = lire();
    $("rMdpTitre").textContent = v ? "Mot de passe activé ✔" : "Aucun mot de passe";
    $("rMdpEtat").textContent = v
      ? "Ton mini-ordinateur s'ouvre sur un écran verrouillé. Il te demande ton mot de passe."
      : "Ajoute un mot de passe pour que personne d'autre n'ouvre ton mini-ordinateur.";
    $("rMdpCreer").textContent = v ? "🔑 Changer" : "🔑 Créer";
    $("rMdpOptions").classList.toggle("cache", !v);
    $("rMdpDelai").value = String((v && v.delai) || 0);
    $("menuVerrouiller").classList.toggle("cache", !v);
  }
  $("ouvrirReglages").addEventListener("click", afficherEtat);

  let modeDialogue = "creer"; // creer | changer | enlever
  function ouvrirDialogue(mode) {
    modeDialogue = mode;
    const f = $("formMdp");
    f.reset();
    $("mdpErreur").textContent = "";
    for (const id of ["mdpAncien", "mdpNouveau", "mdpConfirmer"]) $(id).type = "password";
    const avec = !!lire();
    $("mdpAncienBloc").classList.toggle("cache", !avec);
    const nouveau = mode !== "enlever";
    for (const id of ["mdpNouveau", "mdpConfirmer", "mdpIndice"]) $(id).closest("label").classList.toggle("cache", !nouveau);
    $("mdpVoir").closest("label").classList.toggle("cache", false);
    $("mdpTitre").textContent = mode === "enlever" ? "Enlever le mot de passe" : avec ? "🔑 Changer le mot de passe" : "🔑 Créer un mot de passe";
    $("mdpEnregistrer").textContent = mode === "enlever" ? "Enlever" : "Enregistrer";
    if (avec && nouveau) $("mdpIndice").value = (lire() || {}).indice || "";
    $("dialogueMdp").showModal();
    setTimeout(() => (avec ? $("mdpAncien") : $("mdpNouveau")).focus(), 30);
  }
  $("rMdpCreer").onclick = () => ouvrirDialogue(lire() ? "changer" : "creer");
  $("rMdpEnlever").onclick = () => ouvrirDialogue("enlever");
  $("rMdpVerrouiller").onclick = () => verrouiller();
  $("menuVerrouiller").onclick = () => verrouiller();
  $("rMdpDelai").onchange = () => {
    const v = lire(); if (!v) return;
    v.delai = parseInt($("rMdpDelai").value, 10) || 0;
    ecrire(v); activite();
  };
  $("mdpVoir").onchange = () => {
    for (const id of ["mdpAncien", "mdpNouveau", "mdpConfirmer"]) $(id).type = $("mdpVoir").checked ? "text" : "password";
  };
  $("formMdp").addEventListener("submit", async (e) => {
    if (e.submitter && e.submitter.value === "annuler") return;
    e.preventDefault();
    const err = (t) => { $("mdpErreur").textContent = t; };
    if (!window.crypto || !crypto.subtle) { err("Ce navigateur ne permet pas de protéger un mot de passe ici."); return; }
    $("mdpEnregistrer").disabled = true;
    try {
      if (lire() && !(await verifier($("mdpAncien").value))) { err("Le mot de passe actuel n'est pas le bon."); $("mdpAncien").value = ""; $("mdpAncien").focus(); return; }
      if (modeDialogue === "enlever") {
        ecrire(null);
        $("dialogueMdp").close("ok"); afficherEtat();
        return;
      }
      const n = $("mdpNouveau").value, c = $("mdpConfirmer").value;
      if (n.length < 4) { err("Choisis au moins 4 caractères."); $("mdpNouveau").focus(); return; }
      if (n !== c) { err("Les deux mots de passe ne sont pas pareils. Réessaie."); $("mdpConfirmer").value = ""; $("mdpConfirmer").focus(); return; }
      const indice = $("mdpIndice").value.trim();
      if (indice && indice.toLowerCase().includes(n.toLowerCase())) { err("L'indice ne doit pas contenir le mot de passe."); $("mdpIndice").focus(); return; }
      if (!(await definir(n, indice))) { err("Impossible d'enregistrer le mot de passe."); return; }
      $("dialogueMdp").close("ok");
      afficherEtat();
      alert("Mot de passe enregistré ✔\nÀ la prochaine ouverture, ton mini-ordinateur te le demandera.");
    } catch (ex) { err("Oups : " + ex.message); }
    finally { $("mdpEnregistrer").disabled = false; }
  });

  // ------------------------------------------------------------------- départ
  afficherEtat();
  setInterval(() => { if (html.classList.contains("verrouille")) heure(); }, 15000);
  if (lire()) verrouiller(); else html.classList.remove("verrouille");
})();
