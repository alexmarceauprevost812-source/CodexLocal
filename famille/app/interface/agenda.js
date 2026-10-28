// Agenda : calendrier du mois, rendez-vous avec rappels, jours fériés du
// Québec et la fête de la personne. Tout reste dans le profil de la clé.
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const CFG = window.CONFIG_ASSISTANTE || {};
  const A = () => window.Assistante;
  const ech = (t) => String(t ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  // ------------------------------------------------------------- données
  const lire = () => { try { const v = JSON.parse(localStorage.getItem("agenda") || "[]"); return Array.isArray(v) ? v : []; } catch { return []; } };
  const ecrire = (l) => { try { localStorage.setItem("agenda", JSON.stringify(l)); return true; } catch { return false; } };
  const iso = (d) => `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const deIso = (s) => { const [a, m, j] = s.split("-").map(Number); return new Date(a, m - 1, j); };
  const aujourdhui = () => iso(new Date());
  // La date doit exister vraiment (pas de 30 février) : elle doit revenir identique.
  const valideDate = (s) => /^\d{4}-\d{2}-\d{2}$/.test(s || "") && iso(deIso(s)) === s;
  const valideHeure = (s) => /^([01]\d|2[0-3]):[0-5]\d$/.test(s || "");
  const heureFr = (h) => (h ? h.replace(":", " h ") : "");
  const dateLongue = (s) => { const t = deIso(s).toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric" }); return t.charAt(0).toUpperCase() + t.slice(1); };
  const trier = (l) => l.sort((x, y) => (x.date + (x.heure || "00:00")).localeCompare(y.date + (y.heure || "00:00")));

  function ajouter(r) {
    const date = String(r.date || "").trim();
    if (!valideDate(date)) throw new Error("La date doit être au format AAAA-MM-JJ.");
    const heure = String(r.heure || "").trim();
    if (heure && !valideHeure(heure)) throw new Error("L'heure doit être au format HH:MM (ex. 14:30).");
    const titre = String(r.titre || "").trim().slice(0, 140);
    if (!titre) throw new Error("Il faut un titre au rendez-vous.");
    const rdv = { id: "r" + Date.now().toString(36) + Math.random().toString(36).slice(2, 6), date, heure, titre,
      note: String(r.note || "").trim().slice(0, 1000), rappel: heure ? (Number.isFinite(+r.rappel) ? +r.rappel : 15) : -1 };
    const l = lire(); l.push(rdv);
    if (!ecrire(trier(l))) throw new Error("Impossible d'enregistrer dans l'agenda.");
    rafraichir();
    return rdv;
  }
  function modifier(id, champs) {
    const l = lire(); const r = l.find((x) => x.id === id); if (!r) return;
    Object.assign(r, champs);
    if (!r.heure) r.rappel = -1;
    ecrire(trier(l)); rafraichir();
  }
  function supprimer(id) { ecrire(lire().filter((x) => x.id !== id)); rafraichir(); }
  const du = (date) => lire().filter((r) => r.date === date);

  // ---------------------------------------- jours fériés du Québec + fête
  function paques(an) { // algorithme de Meeus (calendrier grégorien)
    const a = an % 19, b = Math.floor(an / 100), c = an % 100, d = Math.floor(b / 4), e = b % 4, f = Math.floor((b + 8) / 25),
      g = Math.floor((b - f + 1) / 3), h = (19 * a + b - d - g + 15) % 30, i = Math.floor(c / 4), k = c % 4,
      l = (32 + 2 * e + 2 * i - h - k) % 7, m = Math.floor((a + 11 * h + 22 * l) / 451), mois = Math.floor((h + l - 7 * m + 114) / 31);
    return new Date(an, mois - 1, ((h + l - 7 * m + 114) % 31) + 1);
  }
  const plus = (d, n) => new Date(d.getFullYear(), d.getMonth(), d.getDate() + n);
  const nieme = (an, mois, n) => { const d = new Date(an, mois, 1); while (d.getDay() !== 1) d.setDate(d.getDate() + 1); d.setDate(d.getDate() + 7 * (n - 1)); return d; }; // n-ième lundi
  const cache = {};
  function speciaux(an) {
    if (cache[an]) return cache[an];
    const p = paques(an), patriotes = new Date(an, 4, 24); while (patriotes.getDay() !== 1) patriotes.setDate(patriotes.getDate() - 1);
    const s = {};
    const f = (d, t) => (s[iso(d)] = { ferie: true, titre: t, icone: "🇨🇦" });
    f(new Date(an, 0, 1), "Jour de l'An"); f(plus(p, -2), "Vendredi saint"); f(plus(p, 1), "Lundi de Pâques");
    f(patriotes, "Journée nationale des patriotes"); s[iso(new Date(an, 5, 24))] = { ferie: true, titre: "Fête nationale du Québec", icone: "⚜️" };
    f(new Date(an, 6, 1), "Fête du Canada"); f(nieme(an, 8, 1), "Fête du Travail"); f(nieme(an, 9, 2), "Action de grâce");
    f(new Date(an, 11, 25), "Noël");
    const fete = (A() && A().reglages().fete) || CFG.fete || "";
    const prenom = (A() && A().reglages().prenom) || CFG.prenom || "";
    if (fete) s[`${an}-${fete}`] = { fete: true, titre: prenom ? `Fête de ${prenom}` : "Ta fête", icone: "🎂" };
    return (cache[an] = s);
  }
  const special = (date) => speciaux(+date.slice(0, 4))[date];

  // --------------------------------------------------------------- écran
  let mois = new Date(); mois.setDate(1);
  let choisi = aujourdhui();
  const JOURS = ["lun.", "mar.", "mer.", "jeu.", "ven.", "sam.", "dim."];

  function dessinerMois() {
    const t = mois.toLocaleDateString("fr-CA", { month: "long", year: "numeric" });
    $("agMois").textContent = t.charAt(0).toUpperCase() + t.slice(1);
    const g = $("agGrille"); g.innerHTML = "";
    for (const j of JOURS) { const e = document.createElement("div"); e.className = "agenda-entete"; e.textContent = j; g.appendChild(e); }
    const debut = new Date(mois); const decal = (debut.getDay() + 6) % 7; debut.setDate(1 - decal);
    const tous = lire(); const auj = aujourdhui();
    for (let i = 0; i < 42; i++) {
      const d = plus(debut, i), k = iso(d), sp = special(k), rdvs = tous.filter((r) => r.date === k);
      if (i >= 35 && d.getMonth() !== mois.getMonth()) break;
      const b = document.createElement("button");
      b.type = "button";
      b.className = "agenda-case" + (d.getMonth() !== mois.getMonth() ? " dehors" : "") + (k === auj ? " auj" : "") + (k === choisi ? " choisie" : "") + (sp && sp.ferie ? " ferie" : "") + (sp && sp.fete ? " anniv" : "") + (rdvs.length ? " a-rdv" : "");
      b.setAttribute("aria-label", dateLongue(k) + (sp ? ` — ${sp.titre}` : "") + (rdvs.length ? ` — ${rdvs.length} rendez-vous` : ""));
      b.innerHTML = `<span class="agenda-num">${d.getDate()}</span>` + (sp ? `<span class="agenda-special" title="${ech(sp.titre)}">${sp.icone}</span>` : "")
        + rdvs.slice(0, 3).map((r) => `<span class="agenda-pastille">${r.heure ? heureFr(r.heure) + " " : ""}${ech(r.titre)}</span>`).join("")
        + (rdvs.length > 3 ? `<span class="agenda-plus">+${rdvs.length - 3}</span>` : "");
      b.onclick = () => { choisi = k; dessinerMois(); dessinerJour(); };
      b.ondblclick = () => ouvrirFormulaire(null, k);
      g.appendChild(b);
    }
  }
  function dessinerJour() {
    const sp = special(choisi);
    $("agJourTitre").textContent = dateLongue(choisi);
    const z = $("agJourListe"); z.innerHTML = "";
    if (sp) { const e = document.createElement("div"); e.className = "agenda-special-jour" + (sp.fete ? " anniv" : ""); e.textContent = `${sp.icone} ${sp.titre}${sp.ferie ? " (jour férié)" : ""}`; z.appendChild(e); }
    const rdvs = du(choisi);
    if (!rdvs.length) { const e = document.createElement("p"); e.className = "petit"; e.textContent = "Rien de prévu ce jour-là."; z.appendChild(e); }
    for (const r of rdvs) {
      const e = document.createElement("div"); e.className = "agenda-rdv";
      e.innerHTML = `<div class="agenda-rdv-heure">${r.heure ? heureFr(r.heure) : "Toute la journée"}</div><div class="agenda-rdv-texte"><strong>${ech(r.titre)}</strong>${r.note ? `<div class="petit">${ech(r.note)}</div>` : ""}${r.rappel >= 0 ? `<div class="petit">🔔 ${r.rappel ? `Rappel ${r.rappel >= 60 ? r.rappel / 60 + " h" : r.rappel + " min"} avant` : "Rappel à l'heure"}</div>` : ""}</div>`;
      const m = document.createElement("button"); m.className = "bouton fantome petit-bouton"; m.textContent = "✏️"; m.title = "Modifier"; m.onclick = () => ouvrirFormulaire(r);
      e.appendChild(m); z.appendChild(e);
    }
  }
  function afficher() { dessinerMois(); dessinerJour(); }
  function rafraichir() {
    if (!$("vue-agenda").classList.contains("cache")) afficher();
    resumeBureau();
  }

  // --------------------------------------------------------- formulaire
  let enEdition = null;
  function ouvrirFormulaire(r, date) {
    enEdition = r ? r.id : null;
    $("rdvTitreDialogue").textContent = r ? "✏️ Modifier le rendez-vous" : "📅 Nouveau rendez-vous";
    $("rdvDate").value = r ? r.date : (date || choisi);
    $("rdvHeure").value = r ? r.heure : "";
    $("rdvTitre").value = r ? r.titre : "";
    $("rdvNote").value = r ? r.note : "";
    $("rdvRappel").value = String(r ? Math.max(r.rappel, -1) : 15);
    $("rdvSupprimer").classList.toggle("cache", !r);
    $("rdvErreur").textContent = "";
    $("dialogueRdv").showModal();
    setTimeout(() => $("rdvTitre").focus(), 30);
  }
  $("formRdv").addEventListener("submit", (e) => {
    if (e.submitter && e.submitter.value === "annuler") return;
    e.preventDefault();
    const v = { date: $("rdvDate").value, heure: $("rdvHeure").value, titre: $("rdvTitre").value, note: $("rdvNote").value, rappel: +$("rdvRappel").value };
    if (!$("rdvHeure").value) v.rappel = -1;
    try {
      if (enEdition) {
        if (!valideDate(v.date)) throw new Error("Choisis une date.");
        if (!v.titre.trim()) throw new Error("Écris un titre (ex. : Rendez-vous chez le dentiste).");
        modifier(enEdition, { date: v.date, heure: v.heure, titre: v.titre.trim(), note: v.note.trim(), rappel: v.rappel });
      } else ajouter(v);
      choisi = v.date; mois = deIso(v.date); mois.setDate(1);
      $("dialogueRdv").close("ok"); afficher();
    } catch (err) { $("rdvErreur").textContent = err.message; }
  });
  $("rdvSupprimer").onclick = () => {
    if (!enEdition || !confirm("Supprimer ce rendez-vous ?")) return;
    supprimer(enEdition); $("dialogueRdv").close("ok"); afficher();
  };
  $("agPrec").onclick = () => { mois.setMonth(mois.getMonth() - 1); dessinerMois(); };
  $("agSuiv").onclick = () => { mois.setMonth(mois.getMonth() + 1); dessinerMois(); };
  $("agAujourdhui").onclick = () => { mois = new Date(); mois.setDate(1); choisi = aujourdhui(); afficher(); };
  $("agAjouter").onclick = () => ouvrirFormulaire(null, choisi);
  $("agAjouterJour").onclick = () => ouvrirFormulaire(null, choisi);

  // ------------------------------------------- « Aujourd'hui » sur le bureau
  function resumeBureau() {
    const z = $("bureauAgenda"); if (!z) return;
    const auj = aujourdhui(), sp = special(auj), rdvs = du(auj);
    const lignes = [];
    if (sp) lignes.push(`<div class="agenda-mini ${sp.fete ? "anniv" : ""}">${sp.icone} ${ech(sp.titre)}</div>`);
    for (const r of rdvs.slice(0, 4)) lignes.push(`<div class="agenda-mini"><strong>${r.heure ? heureFr(r.heure) : "Journée"}</strong> ${ech(r.titre)}</div>`);
    if (rdvs.length > 4) lignes.push(`<div class="petit">et ${rdvs.length - 4} autre(s)…</div>`);
    if (!lignes.length) lignes.push(`<div class="petit">Rien à l'agenda aujourd'hui.</div>`);
    z.innerHTML = lignes.join("");
  }

  // ------------------------------------------------------------- rappels
  const dejaVus = () => { try { return JSON.parse(localStorage.getItem("agenda-rappels") || "{}"); } catch { return {}; } };
  function carillon() {
    try {
      const ctx = new (window.AudioContext || window.webkitAudioContext)();
      [660, 880].forEach((f, i) => {
        const o = ctx.createOscillator(), g = ctx.createGain();
        o.frequency.value = f; o.type = "sine"; o.connect(g); g.connect(ctx.destination);
        const t = ctx.currentTime + i * 0.22;
        g.gain.setValueAtTime(0.0001, t); g.gain.exponentialRampToValueAtTime(0.18, t + 0.02); g.gain.exponentialRampToValueAtTime(0.0001, t + 0.5);
        o.start(t); o.stop(t + 0.55);
      });
      setTimeout(() => ctx.close(), 1500);
    } catch {}
  }
  function toast(titre, texte) {
    const t = document.createElement("div");
    t.className = "toast-agenda"; t.setAttribute("role", "alert");
    t.innerHTML = `<div class="toast-titre">${ech(titre)}</div><div>${ech(texte)}</div><div class="toast-actions"><button class="bouton petit-bouton">Voir l'agenda</button><button class="bouton fantome petit-bouton">OK</button></div>`;
    const [voir, ok] = t.querySelectorAll("button");
    voir.onclick = () => { t.remove(); window.ouvrirAppli && window.ouvrirAppli("agenda"); };
    ok.onclick = () => t.remove();
    $("toasts").appendChild(t);
    carillon();
  }
  function verifierRappels() {
    if (document.documentElement.classList.contains("verrouille")) return;
    const maintenant = new Date(), auj = aujourdhui(), demain = iso(plus(new Date(), 1)), hier = iso(plus(new Date(), -1));
    const vus = dejaVus(), garder = {};
    for (const [k, v] of Object.entries(vus)) if (k.split("|")[0] >= hier) garder[k] = v;
    // Aujourd'hui ET demain : un rappel « 1 h avant » d'un rendez-vous à 0 h 30
    // tombe la veille au soir. L'heure de début vient de la date du rendez-vous.
    for (const r of lire().filter((x) => x.date === auj || x.date === demain)) {
      if (!r.heure || r.rappel < 0) continue;
      const [h, m] = r.heure.split(":").map(Number);
      const debut = deIso(r.date); debut.setHours(h, m, 0, 0);
      const avant = new Date(debut.getTime() - r.rappel * 60000);
      const cle = `${r.date}|${r.id}|${r.heure}|${r.rappel}`;
      // Fenêtre de 10 minutes : on ne sonne pas pour un rendez-vous déjà loin derrière.
      if (maintenant >= avant && maintenant - avant < 10 * 60000 && !garder[cle]) {
        garder[cle] = 1;
        toast(r.rappel ? `📅 Dans ${r.rappel >= 60 ? r.rappel / 60 + " h" : r.rappel + " minutes"}` : "📅 C'est l'heure !", `${r.date !== auj ? "Demain, " : ""}${heureFr(r.heure)} — ${r.titre}`);
      }
    }
    try { localStorage.setItem("agenda-rappels", JSON.stringify(garder)); } catch {}
  }
  setInterval(verifierRappels, 20000);
  setTimeout(verifierRappels, 3000);
  // À minuit, le bureau passe au nouveau jour.
  let jourVu = aujourdhui();
  setInterval(() => { if (aujourdhui() !== jourVu) { jourVu = aujourdhui(); resumeBureau(); } }, 60000);

  resumeBureau();
  window.Agenda = { afficher, ajouter, supprimer, du, lire, special, resumeBureau, dateLongue,
    // Pour l'assistante : les rendez-vous d'une période, en texte.
    texte(debut, fin) {
      const d0 = valideDate(debut) ? debut : aujourdhui();
      const d1 = valideDate(fin) ? fin : d0;
      const l = lire().filter((r) => r.date >= d0 && r.date <= d1);
      if (!l.length) return `Rien à l'agenda du ${d0} au ${d1}.`;
      return l.map((r) => `${r.date}${r.heure ? " " + r.heure : ""} — ${r.titre}${r.note ? " (" + r.note + ")" : ""}`).join("\n");
    },
  };
})();
