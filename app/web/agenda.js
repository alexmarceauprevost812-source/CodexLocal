/* Ti-Labo — Agenda (style agenda Samsung, theme TI-LEX-AL)
   Le meme fichier sert au Ti-Labo de la cle (vue « Agenda ») et a l'appli Android (page /agenda).
   Donnees : agenda.json  {version, evenements:[{id, titre, debut, fin, journee, couleur, lieu, note,
   rappel, repete, modifie, supprime}]}  — debut/fin : "AAAA-MM-JJ" ou "AAAA-MM-JJTHH:MM". */
(function () {
  "use strict";
  const COULEURS = [["#FF7A1A", "Orange TI-LEX"], ["#e5484d", "Rouge"], ["#ec4899", "Rose"], ["#8b5cf6", "Mauve"], ["#3b82f6", "Bleu"],
    ["#38bdf8", "Ciel"], ["#22c55e", "Vert"], ["#eab308", "Jaune"], ["#a16207", "Brun"], ["#6b7280", "Gris"]];
  const MOIS = ["janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"];
  const JOURS = ["D", "L", "M", "M", "J", "V", "S"];
  const JOURS_LONG = ["dimanche", "lundi", "mardi", "mercredi", "jeudi", "vendredi", "samedi"];
  const RAPPELS = [[null, "Aucun rappel"], [0, "À l'heure de l'événement"], [5, "5 minutes avant"], [10, "10 minutes avant"],
    [15, "15 minutes avant"], [30, "30 minutes avant"], [60, "1 heure avant"], [1440, "1 jour avant"]];
  const REPETE = [["", "Ne pas répéter"], ["jour", "Chaque jour"], ["semaine", "Chaque semaine"], ["mois", "Chaque mois"], ["annee", "Chaque année"]];

  const echap = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
  const deux = (n) => String(n).padStart(2, "0");
  const iso = (d) => d.getFullYear() + "-" + deux(d.getMonth() + 1) + "-" + deux(d.getDate());
  const jourDe = (s) => { const [a, m, j] = s.slice(0, 10).split("-").map(Number); return new Date(a, m - 1, j); };
  const heureDe = (s) => (s && s.length > 10 ? s.slice(11, 16) : "");
  const ajouter = (d, n) => { const x = new Date(d); x.setDate(x.getDate() + n); return x; };
  const nouvelId = () => "ev-" + Date.now().toString(36) + "-" + Math.random().toString(36).slice(2, 7);

  // ------------------------------------------------------------ jours feries et fetes du Quebec
  function paques(a) {
    const b = a % 19, c = Math.floor(a / 100), d = a % 100, e = Math.floor(c / 4), f = c % 4, g = Math.floor((c + 8) / 25),
      h = Math.floor((c - g + 1) / 3), i = (19 * b + c - e - h + 15) % 30, k = Math.floor(d / 4), l = d % 4,
      m = (32 + 2 * f + 2 * k - i - l) % 7, n = Math.floor((b + 11 * i + 22 * m) / 451), mois = Math.floor((i + m - 7 * n + 114) / 31);
    return new Date(a, mois - 1, ((i + m - 7 * n + 114) % 31) + 1);
  }
  const nieme = (a, mois, jourSem, n) => { const d = new Date(a, mois, 1); d.setDate(1 + ((7 + jourSem - d.getDay()) % 7) + 7 * (n - 1)); return d; };
  const cacheFeries = {};
  function feries(a) {
    if (cacheFeries[a]) return cacheFeries[a];
    const p = paques(a), r = {};
    const mettre = (d, nom, ferie = true) => { r[iso(d)] = { nom, ferie }; };
    mettre(new Date(a, 0, 1), "Jour de l'An 🎉");
    mettre(ajouter(p, -2), "Vendredi saint");
    mettre(p, "Pâques 🐣", false);
    mettre(ajouter(p, 1), "Lundi de Pâques");
    const patriotes = new Date(a, 4, 24); while (patriotes.getDay() !== 1) patriotes.setDate(patriotes.getDate() - 1);
    mettre(patriotes, "Journée nationale des patriotes");
    mettre(new Date(a, 5, 24), "Fête nationale ⚜️");
    mettre(new Date(a, 6, 1), "Fête du Canada 🍁");
    mettre(nieme(a, 8, 1, 1), "Fête du Travail");
    mettre(nieme(a, 9, 1, 2), "Action de grâce 🦃");
    mettre(new Date(a, 11, 25), "Noël 🎄");
    mettre(new Date(a, 1, 14), "Saint-Valentin ❤️", false);
    mettre(nieme(a, 4, 0, 2), "Fête des mères 💐", false);
    mettre(nieme(a, 5, 0, 3), "Fête des pères 👔", false);
    mettre(new Date(a, 9, 31), "Halloween 🎃", false);
    mettre(new Date(a, 11, 31), "Veille du jour de l'An 🥂", false);
    return (cacheFeries[a] = r);
  }
  const ferieDu = (s) => feries(Number(s.slice(0, 4)))[s];

  // ------------------------------------------------------------ occurrences (evenements sur plusieurs jours, repetitions)
  function occurrences(evts, du, au) {
    const res = {}; // "AAAA-MM-JJ" -> [{ev, debutJour, finJour}]
    const d0 = jourDe(du), d1 = jourDe(au);
    for (const ev of evts) {
      if (ev.supprime) continue;
      const deb = jourDe(ev.debut), fin = jourDe(ev.fin || ev.debut);
      const duree = Math.max(0, Math.round((fin - deb) / 864e5));
      const departs = [];
      if (!ev.repete) departs.push(deb);
      else {
        let x = new Date(deb), garde = 0;
        while (x <= d1 && garde++ < 4000) {
          if (ajouter(x, duree) >= d0) departs.push(new Date(x));
          if (ev.repete === "jour") x = ajouter(x, 1);
          else if (ev.repete === "semaine") x = ajouter(x, 7);
          else if (ev.repete === "mois") { const j = deb.getDate(); x = new Date(x.getFullYear(), x.getMonth() + 1, 1); x.setDate(Math.min(j, new Date(x.getFullYear(), x.getMonth() + 1, 0).getDate())); }
          else x = new Date(x.getFullYear() + 1, deb.getMonth(), deb.getDate());
        }
      }
      for (const dep of departs) {
        for (let k = 0; k <= duree; k++) {
          const j = ajouter(dep, k);
          if (j < d0 || j > d1) continue;
          (res[iso(j)] = res[iso(j)] || []).push({ ev, premier: k === 0, dernier: k === duree, plusieurs: duree > 0 });
        }
      }
    }
    for (const k in res) res[k].sort((a, b) => (b.ev.journee || b.plusieurs) - (a.ev.journee || a.plusieurs) || heureDe(a.ev.debut).localeCompare(heureDe(b.ev.debut)));
    return res;
  }

  // ------------------------------------------------------------ composant
  function monter(racine, backend) {
    const et = { mois: new Date(new Date().getFullYear(), new Date().getMonth(), 1), choisi: iso(new Date()), vue: "mois", evts: [], cle: null };
    racine.classList.add("ag");
    racine.innerHTML = `
      <div class="ag-tete">
        <button class="ag-titre" data-a="titre"><b></b><span></span></button>
        <button class="ag-ico" data-a="prec" aria-label="Mois précédent">‹</button>
        <button class="ag-ico" data-a="suiv" aria-label="Mois suivant">›</button>
        <button class="ag-ico" data-a="auj" aria-label="Aujourd'hui"><span class="ag-auj">${new Date().getDate()}</span></button>
      </div>
      <div class="ag-vues">
        <button data-v="mois" class="on">Mois</button><button data-v="liste">Liste</button>
        <button class="ag-sync" data-a="sync" hidden>🔄 Clé USB-C</button>
      </div>
      <div class="ag-zone" style="flex:1;display:flex;flex-direction:column;min-height:0"></div>
      <button class="ag-fab" data-a="nouveau" aria-label="Nouvel événement">+</button>`;
    const zone = racine.querySelector(".ag-zone");
    const q = (s) => racine.querySelector(s);

    function message(t) {
      const m = document.createElement("div"); m.className = "ag-message"; m.textContent = t;
      racine.appendChild(m); setTimeout(() => m.remove(), 2600);
    }

    async function charger() {
      try {
        const r = await backend.charger();
        et.evts = r.evenements || [];
        et.cle = r.cle || null;
      } catch (e) { message("⚠ " + e.message); }
      const s = q('[data-a="sync"]');
      s.hidden = !(backend.synchro && et.cle && et.cle.branchee);
      dessiner();
    }

    function dessiner() {
      q(".ag-titre b").textContent = MOIS[et.mois.getMonth()];
      q(".ag-titre span").textContent = et.mois.getFullYear();
      racine.querySelectorAll("[data-v]").forEach((b) => b.classList.toggle("on", b.dataset.v === et.vue));
      if (et.vue === "liste") return dessinerListe();
      const debut = ajouter(et.mois, -et.mois.getDay());
      const fin = ajouter(debut, 41);
      const occ = occurrences(et.evts, iso(debut), iso(fin));
      const auj = iso(new Date());
      let h = `<div class="ag-sem">${JOURS.map((j) => `<div>${j}</div>`).join("")}</div><div class="ag-mois">`;
      for (let i = 0; i < 42; i++) {
        const d = ajouter(debut, i), s = iso(d), liste = occ[s] || [], f = ferieDu(s);
        const cls = ["ag-case", d.getMonth() !== et.mois.getMonth() && "autre", d.getDay() === 0 && "dim", d.getDay() === 6 && "sam",
          s === auj && "auj", s === et.choisi && "choisi", f && f.ferie && "ferie"].filter(Boolean).join(" ");
        const max = f ? 2 : 3;
        let evh = f ? `<div class="ag-ev ferie">${echap(f.nom)}</div>` : "";
        evh += liste.slice(0, max).map((o) => `<div class="ag-ev ${o.ev.journee || o.plusieurs ? "" : "leger"}" style="--c:${echap(o.ev.couleur || "#FF7A1A")}">${echap(o.ev.titre || "(sans titre)")}</div>`).join("");
        if (liste.length > max) evh += `<div class="ag-plus">+${liste.length - max}</div>`;
        h += `<div class="${cls}" data-j="${s}"><div class="ag-num">${d.getDate()}</div>${evh}</div>`;
      }
      h += `</div><div class="ag-jour"></div>`;
      zone.innerHTML = h;
      dessinerJour(occ);
    }

    function ligneEv(o) {
      const ev = o.ev;
      const h = ev.journee || o.plusieurs && !o.premier && !o.dernier ? "Toute la journée" :
        (o.premier || !o.plusieurs ? heureDe(ev.debut) : "") + (ev.fin && heureDe(ev.fin) && (o.dernier || !o.plusieurs) ? "<br>" + heureDe(ev.fin) : "");
      return `<div class="ag-item" data-id="${echap(ev.id)}" style="--c:${echap(ev.couleur || "#FF7A1A")}"><div class="barre"></div>
        <div class="h">${h || "Toute la journée"}</div>
        <div class="t"><b>${echap(ev.titre || "(sans titre)")}</b>${ev.lieu ? `<small>📍 ${echap(ev.lieu)}</small>` : ""}${ev.repete ? `<small>🔁 ${REPETE.find((r) => r[0] === ev.repete)[1]}</small>` : ""}</div></div>`;
    }

    function dessinerJour(occ) {
      const d = jourDe(et.choisi), f = ferieDu(et.choisi), liste = (occ || occurrences(et.evts, et.choisi, et.choisi))[et.choisi] || [];
      let h = `<h3>${JOURS_LONG[d.getDay()]} ${d.getDate()} ${MOIS[d.getMonth()]}</h3>`;
      if (f) h += `<div class="ag-item" style="--c:#e5484d"><div class="barre"></div><div class="h">Toute la journée</div><div class="t"><b>${echap(f.nom)}</b><small>${f.ferie ? "Jour férié au Québec" : "Fête"}</small></div></div>`;
      h += liste.map(ligneEv).join("");
      if (!liste.length && !f) h += `<div class="ag-vide">Rien de prévu ✨ — touche + pour ajouter.</div>`;
      const p = q(".ag-jour"); if (p) p.innerHTML = h;
    }

    function dessinerListe() {
      const auj = new Date(), fin = ajouter(auj, 90);
      const occ = occurrences(et.evts, iso(auj), iso(fin));
      let h = `<div class="ag-liste">`, n = 0;
      for (let i = 0; i <= 90; i++) {
        const d = ajouter(auj, i), s = iso(d), liste = occ[s] || [], f = ferieDu(s);
        if (!liste.length && !(f && f.ferie)) continue;
        n++;
        h += `<div class="jourT"><span class="n ${i === 0 ? "auj" : ""}">${d.getDate()}</span><small>${JOURS_LONG[d.getDay()]} · ${MOIS[d.getMonth()]}${i === 0 ? " · aujourd'hui" : ""}</small></div>`;
        if (f && f.ferie) h += `<div class="ag-item" style="--c:#e5484d"><div class="barre"></div><div class="h">Férié</div><div class="t"><b>${echap(f.nom)}</b></div></div>`;
        h += liste.map(ligneEv).join("");
      }
      if (!n) h += `<div class="ag-vide">Rien dans les 3 prochains mois. Touche + pour ajouter un événement.</div>`;
      zone.innerHTML = h + "</div>";
    }

    // ------------------------------------------------------------ fiche et editeur
    function fiche(id) {
      const ev = et.evts.find((e) => e.id === id); if (!ev) return;
      const d = jourDe(ev.debut);
      const quand = `${JOURS_LONG[d.getDay()]} ${d.getDate()} ${MOIS[d.getMonth()]} ${d.getFullYear()}` +
        (ev.journee ? " · toute la journée" : " · " + heureDe(ev.debut) + (heureDe(ev.fin) ? " – " + heureDe(ev.fin) : "")) +
        (ev.fin && ev.fin.slice(0, 10) !== ev.debut.slice(0, 10) ? "\njusqu'au " + jourDe(ev.fin).getDate() + " " + MOIS[jourDe(ev.fin).getMonth()] : "");
      const r = RAPPELS.find((x) => x[0] === (ev.rappel ?? null));
      const f = document.createElement("div"); f.className = "ag-fiche";
      f.innerHTML = `<div class="f" style="--c:${echap(ev.couleur || "#FF7A1A")}"><h2>${echap(ev.titre || "(sans titre)")}</h2>
        <p>🗓️ ${echap(quand)}</p>${ev.lieu ? `<p>📍 ${echap(ev.lieu)}</p>` : ""}${ev.repete ? `<p>🔁 ${REPETE.find((x) => x[0] === ev.repete)[1]}</p>` : ""}
        ${r && r[0] !== null ? `<p>🔔 ${r[1]}</p>` : ""}${ev.note ? `<p>📝 ${echap(ev.note)}</p>` : ""}
        <div class="actions"><button class="ag-btn rouge" data-f="sup">Supprimer</button><button class="ag-btn plein" data-f="mod">Modifier</button></div></div>`;
      f.onclick = (e) => { if (e.target === f) f.remove(); };
      f.querySelector('[data-f="mod"]').onclick = () => { f.remove(); editeur(ev); };
      f.querySelector('[data-f="sup"]').onclick = async () => {
        if (!confirm("Supprimer « " + (ev.titre || "cet événement") + " » ?")) return;
        f.remove();
        try { await backend.supprimer(ev.id); message("🗑️ Supprimé"); } catch (e) { message("⚠ " + e.message); }
        charger();
      };
      racine.appendChild(f);
    }

    function editeur(ev) {
      const neuf = !ev;
      if (neuf) {
        const h = new Date(); const prochaine = deux(Math.min(23, h.getHours() + 1));
        ev = { id: nouvelId(), titre: "", debut: et.choisi + "T" + prochaine + ":00", fin: et.choisi + "T" + deux(Math.min(23, h.getHours() + 2)) + ":00",
          journee: false, couleur: "#FF7A1A", lieu: "", note: "", rappel: 10, repete: "" };
      }
      const e = document.createElement("div"); e.className = "ag-ed";
      e.innerHTML = `<div class="barre"><button class="ag-btn" data-e="annuler">Annuler</button><b>${neuf ? "Nouvel événement" : "Modifier"}</b>
        <button class="ag-btn plein" data-e="ok">Enregistrer</button></div>
        <div class="corps">
          <input type="text" class="grand" data-c="titre" placeholder="Titre" value="${echap(ev.titre)}" maxlength="120">
          <div class="ag-couleurs">${COULEURS.map(([c, n]) => `<button type="button" style="--c:${c}" title="${n}" data-coul="${c}" class="${c === ev.couleur ? "on" : ""}"></button>`).join("")}</div>
          <div class="ligne"><span>🕑</span><span class="lab">Toute la journée</span><label class="ag-inter"><input type="checkbox" data-c="journee" ${ev.journee ? "checked" : ""}><i></i></label></div>
          <div class="ligne"><span>▶</span><div class="dates"><input type="date" data-c="dd" value="${ev.debut.slice(0, 10)}"><input type="time" data-c="hd" value="${heureDe(ev.debut) || "09:00"}"></div></div>
          <div class="ligne"><span>■</span><div class="dates"><input type="date" data-c="df" value="${(ev.fin || ev.debut).slice(0, 10)}"><input type="time" data-c="hf" value="${heureDe(ev.fin) || "10:00"}"></div></div>
          <div class="ligne"><span>🔔</span><select data-c="rappel">${RAPPELS.map(([v, n]) => `<option value="${v ?? ""}" ${(ev.rappel ?? null) === v ? "selected" : ""}>${n}</option>`).join("")}</select></div>
          <div class="ligne"><span>🔁</span><select data-c="repete">${REPETE.map(([v, n]) => `<option value="${v}" ${(ev.repete || "") === v ? "selected" : ""}>${n}</option>`).join("")}</select></div>
          <div class="ligne"><span>📍</span><input type="text" data-c="lieu" placeholder="Lieu" value="${echap(ev.lieu)}"></div>
          <div class="ligne" style="align-items:flex-start"><span>📝</span><textarea data-c="note" rows="3" placeholder="Note">${echap(ev.note)}</textarea></div>
        </div>`;
      const c = (n) => e.querySelector(`[data-c="${n}"]`);
      let couleur = ev.couleur || "#FF7A1A";
      e.querySelectorAll("[data-coul]").forEach((b) => b.onclick = () => {
        couleur = b.dataset.coul; e.querySelectorAll("[data-coul]").forEach((x) => x.classList.toggle("on", x === b));
      });
      const majHeures = () => e.querySelectorAll('input[type="time"]').forEach((t) => t.style.display = c("journee").checked ? "none" : "");
      c("journee").onchange = majHeures; majHeures();
      c("dd").onchange = () => { if (c("df").value < c("dd").value) c("df").value = c("dd").value; };
      e.querySelector('[data-e="annuler"]').onclick = () => e.remove();
      e.querySelector('[data-e="ok"]').onclick = async () => {
        const journee = c("journee").checked;
        const debut = c("dd").value + (journee ? "" : "T" + (c("hd").value || "09:00"));
        let fin = (c("df").value || c("dd").value) + (journee ? "" : "T" + (c("hf").value || c("hd").value || "10:00"));
        if (!c("dd").value) return message("Choisis une date");
        if (fin < debut) fin = debut;
        const r = c("rappel").value;
        const sauve = { ...ev, titre: c("titre").value.trim() || "(sans titre)", debut, fin, journee, couleur,
          lieu: c("lieu").value.trim(), note: c("note").value.trim(), rappel: r === "" ? null : Number(r), repete: c("repete").value || "" };
        try { await backend.enregistrer(sauve); } catch (err) { return message("⚠ " + err.message); }
        e.remove();
        et.choisi = debut.slice(0, 10);
        const d = jourDe(debut); et.mois = new Date(d.getFullYear(), d.getMonth(), 1);
        message(neuf ? "✅ Ajouté à l'agenda" : "✅ Modifié");
        charger();
      };
      racine.appendChild(e);
      if (neuf) setTimeout(() => c("titre").focus(), 60);
    }

    // ------------------------------------------------------------ evenements
    function changerMois(n) {
      et.mois = new Date(et.mois.getFullYear(), et.mois.getMonth() + n, 1);
      dessiner();
      const m = q(".ag-mois"); if (m) { m.classList.add(n > 0 ? "glisse-g" : "glisse-d"); }
    }
    let dernierToucher = { j: null, t: 0 };
    racine.addEventListener("click", async (ev) => {
      const a = ev.target.closest("[data-a]"), v = ev.target.closest("[data-v]"), cas = ev.target.closest(".ag-case"), item = ev.target.closest(".ag-item[data-id]");
      if (v) { et.vue = v.dataset.v; dessiner(); return; }
      if (item) { fiche(item.dataset.id); return; }
      if (cas) {
        const j = cas.dataset.j, maintenant = Date.now();
        if (dernierToucher.j === j && maintenant - dernierToucher.t < 380) { editeur(null); return; } // double touche = nouvel evenement
        dernierToucher = { j, t: maintenant };
        et.choisi = j;
        const d = jourDe(j);
        if (d.getMonth() !== et.mois.getMonth()) { et.mois = new Date(d.getFullYear(), d.getMonth(), 1); dessiner(); return; }
        racine.querySelectorAll(".ag-case.choisi").forEach((x) => x.classList.remove("choisi"));
        cas.classList.add("choisi");
        dessinerJour();
        return;
      }
      if (!a) return;
      if (a.dataset.a === "prec") changerMois(-1);
      else if (a.dataset.a === "suiv") changerMois(1);
      else if (a.dataset.a === "auj" || a.dataset.a === "titre") {
        const d = new Date(); et.mois = new Date(d.getFullYear(), d.getMonth(), 1); et.choisi = iso(d); et.vue = "mois"; dessiner();
      } else if (a.dataset.a === "nouveau") editeur(null);
      else if (a.dataset.a === "sync") {
        a.disabled = true; a.textContent = "🔄 …";
        try { const r = await backend.synchro(); message("✅ Agenda synchronisé avec la clé (" + (r.total ?? "") + " événements)"); }
        catch (e) { message("⚠ " + e.message); }
        a.disabled = false; a.textContent = "🔄 Clé USB-C";
        charger();
      }
    });
    // glisser a gauche / a droite pour changer de mois (comme Samsung)
    let x0 = null, y0 = null;
    racine.addEventListener("touchstart", (e) => { if (e.target.closest(".ag-mois")) { x0 = e.touches[0].clientX; y0 = e.touches[0].clientY; } }, { passive: true });
    racine.addEventListener("touchend", (e) => {
      if (x0 === null) return;
      const dx = e.changedTouches[0].clientX - x0, dy = e.changedTouches[0].clientY - y0; x0 = null;
      if (Math.abs(dx) > 60 && Math.abs(dx) > Math.abs(dy) * 1.5) changerMois(dx < 0 ? 1 : -1);
    }, { passive: true });

    charger();
    return { recharger: charger };
  }

  /** Resume des prochains evenements (pour que l'IA reponde « qu'est-ce que j'ai demain ? »). */
  function resume(evts, jours = 14) {
    const auj = new Date(), occ = occurrences(evts, iso(auj), iso(ajouter(auj, jours)));
    const l = [];
    for (let i = 0; i <= jours; i++) {
      const d = ajouter(auj, i), s = iso(d);
      for (const o of occ[s] || []) l.push(`${JOURS_LONG[d.getDay()]} ${s}${o.ev.journee ? "" : " " + heureDe(o.ev.debut)} : ${o.ev.titre}${o.ev.lieu ? " (" + o.ev.lieu + ")" : ""}`);
      const f = ferieDu(s); if (f && f.ferie) l.push(`${JOURS_LONG[d.getDay()]} ${s} : ${f.nom} (férié)`);
    }
    return l;
  }

  let instance = null;
  window.TiAgenda = {
    monter, occurrences, resume, feries,
    // Ti-Labo de la cle : la vue « Agenda » (fonction api() d'app.js)
    rendre() {
      const el = document.querySelector("#ag-racine");
      if (!el) return;
      if (instance) return instance.recharger();
      instance = monter(el, {
        charger: () => window.api("agenda"),
        enregistrer: (ev) => window.api("agenda", { evenement: ev }),
        supprimer: (id) => window.api("agenda", { supprimer: id }),
      });
    },
  };
})();
