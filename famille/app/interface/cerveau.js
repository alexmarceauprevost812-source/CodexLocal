// Mini-cerveau : les leçons que l'assistante a apprises de ses erreurs.
// Elles sont relues avant chaque réponse, pour ne pas refaire 2 fois la même
// erreur. Elle peut les voir, les corriger ou les effacer dans l'appli.
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const ech = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const MAX = 60;

  const lire = () => { try { const v = JSON.parse(localStorage.getItem("lecons") || "[]"); return Array.isArray(v) ? v : []; } catch { return []; } };
  const ecrire = (l) => { try { localStorage.setItem("lecons", JSON.stringify(l)); return true; } catch { return false; } };
  const pareil = (a, b) => a.trim().toLowerCase().replace(/\s+/g, " ").replace(/[.!]+$/, "") === b.trim().toLowerCase().replace(/\s+/g, " ").replace(/[.!]+$/, "");

  function ajouter(texte, source) {
    texte = String(texte || "").trim().replace(/\s+/g, " ").slice(0, 300);
    if (texte.length < 4) throw new Error("La leçon est trop courte.");
    const l = lire();
    const deja = l.find((x) => pareil(x.texte, texte));
    if (deja) { deja.date = new Date().toISOString(); deja.fois = (deja.fois || 1) + 1; }
    else l.unshift({ id: "l" + Date.now().toString(36) + Math.random().toString(36).slice(2, 5), texte, source: source || "elle", date: new Date().toISOString(), fois: 1 });
    // Au-delà de MAX, on oublie les plus vieilles.
    if (!ecrire(l.slice(0, MAX))) throw new Error("Impossible d'enregistrer la leçon.");
    afficher();
    return deja ? "deja" : "nouvelle";
  }
  function modifier(id, texte) { const l = lire(); const x = l.find((y) => y.id === id); if (x && texte.trim()) { x.texte = texte.trim().slice(0, 300); ecrire(l); } afficher(); }
  function oublier(id) { ecrire(lire().filter((x) => x.id !== id)); afficher(); }

  // Pour la consigne de l'IA : les leçons les plus récentes, sans dépasser ~3000 caractères.
  function pourConsigne() {
    const l = lire(); const out = []; let n = 0;
    for (const x of l) { if (n + x.texte.length > 3000) break; out.push("- " + x.texte); n += x.texte.length; }
    return out;
  }

  // ------------------------------------------------------------ l'appli
  function afficher() {
    const z = $("cerveauListe"); if (!z) return;
    const l = lire();
    $("cerveauCompte").textContent = l.length ? `${l.length} leçon${l.length > 1 ? "s" : ""} apprise${l.length > 1 ? "s" : ""}` : "";
    z.innerHTML = "";
    if (!l.length) { z.innerHTML = `<p class="petit">Pas encore de leçon. Quand l'assistante se trompe, clique sur <strong>🧠 Corriger</strong> sous sa réponse, ou dis-lui simplement « Non, c'est plutôt… » : elle s'en souviendra.</p>`; return; }
    for (const x of l) {
      const e = document.createElement("div"); e.className = "lecon";
      const d = new Date(x.date).toLocaleDateString("fr-CA", { day: "numeric", month: "long" });
      e.innerHTML = `<span class="lecon-qui" title="${x.source === "assistante" ? "Retenu par l'assistante pendant une conversation" : "Ajouté par toi"}">${x.source === "assistante" ? "🤖" : "👩"}</span><div class="lecon-texte"><div>${ech(x.texte)}</div><div class="petit">${d}${x.fois > 1 ? ` · rappelé ${x.fois} fois` : ""}</div></div>`;
      const m = document.createElement("button"); m.className = "bouton fantome petit-bouton"; m.textContent = "✏️"; m.title = "Modifier";
      m.onclick = () => { const t = prompt("Modifier la leçon :", x.texte); if (t !== null) modifier(x.id, t); };
      const s = document.createElement("button"); s.className = "bouton fantome petit-bouton"; s.textContent = "🗑️"; s.title = "Oublier cette leçon";
      s.onclick = () => { if (confirm("Oublier cette leçon ?")) oublier(x.id); };
      e.append(m, s); z.appendChild(e);
    }
  }
  $("cerveauForm").addEventListener("submit", (e) => {
    e.preventDefault();
    try { ajouter($("cerveauNouvelle").value, "elle"); $("cerveauNouvelle").value = ""; $("cerveauErreur").textContent = ""; }
    catch (err) { $("cerveauErreur").textContent = err.message; }
  });

  // ------------------------------- « 🧠 Corriger » sous une réponse
  let refaire = null;
  function corriger(peutRefaire, refaireFn) {
    refaire = peutRefaire ? refaireFn : null;
    $("leconTexte").value = "";
    $("leconRefaire").checked = !!peutRefaire;
    $("leconRefaire").closest("label").classList.toggle("cache", !peutRefaire);
    $("leconErreur").textContent = "";
    $("dialogueLecon").showModal();
    setTimeout(() => $("leconTexte").focus(), 30);
  }
  $("formLecon").addEventListener("submit", (e) => {
    if (e.submitter && e.submitter.value === "annuler") return;
    e.preventDefault();
    const t = $("leconTexte").value.trim();
    try { ajouter(t, "elle"); } catch (err) { $("leconErreur").textContent = err.message; return; }
    $("dialogueLecon").close("ok");
    if ($("leconRefaire").checked && refaire) refaire(t);
  });

  window.Cerveau = { ajouter, lire, oublier, pourConsigne, afficher, corriger };
})();
