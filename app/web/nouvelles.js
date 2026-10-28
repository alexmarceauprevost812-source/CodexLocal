/* Ti-Labo — Nouvelles du matin
   Les titres du jour cherchés sur le web (Google News, sinon DuckDuckGo), présentés en bulletin. */
(function () {
  const RUBRIQUES = [
    { id: "une", titre: "À la une" },
    { id: "quebec", titre: "Québec" },
    { id: "region", titre: "Saguenay–Lac-Saint-Jean" },
    { id: "techno", titre: "Techno et IA" },
    { id: "monde", titre: "Monde" },
  ];
  const nv = { donnees: [], filtre: "tout", sujet: "", charge: false, horloge: null, bulletin: null };
  const q = (s) => document.querySelector(s);
  const echap = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  function salut() {
    const h = new Date().getHours();
    return h < 12 ? "Bon matin" : h < 18 ? "Bon après-midi" : "Bonsoir";
  }
  function ilYa(ts) {
    if (!ts) return "";
    const m = Math.max(0, Math.round((Date.now() / 1000 - ts) / 60));
    if (m < 1) return "à l'instant";
    if (m < 60) return "il y a " + m + " min";
    const h = Math.round(m / 60);
    if (h < 24) return "il y a " + h + " h";
    const j = Math.round(h / 24);
    return "il y a " + j + " j";
  }
  function majHorloge() {
    const d = new Date();
    q("#nv-date").textContent = d.toLocaleDateString("fr-CA", { weekday: "long", day: "numeric", month: "long", year: "numeric" });
    q("#nv-heure").textContent = d.toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
    q("#nv-salut").textContent = salut();
  }

  function squelettes() {
    q("#nv-grille").innerHTML = RUBRIQUES.map((r, i) => `
      <section class="nv-bloc squelette" style="--i:${i}">
        <div class="nv-bloc-tete"><b>${echap(r.titre)}</b></div>
        <div class="sq sq-grand"></div><div class="sq"></div><div class="sq"></div><div class="sq court"></div>
      </section>`).join("");
  }

  function rendreRubriques() {
    const liste = [{ id: "tout", titre: "Tout" }, ...RUBRIQUES];
    if (nv.sujet) liste.push({ id: "sujet", titre: nv.sujet });
    q("#nv-rubriques").innerHTML = liste.map((r) =>
      `<button class="nv-puce ${nv.filtre === r.id ? "actif" : ""}" data-r="${r.id}" aria-pressed="${nv.filtre === r.id}">${echap(r.titre)}</button>`).join("");
    document.querySelectorAll("#nv-rubriques [data-r]").forEach((b) => (b.onclick = () => { nv.filtre = b.dataset.r; rendreRubriques(); rendreGrille(); }));
  }

  function article(a, j, grand) {
    return `<a class="nv-art ${grand ? "grand" : ""}" href="${echap(a.url)}" target="_blank" rel="noopener noreferrer" style="--j:${j}">
      <span class="nv-meta"><b>${echap(a.source || "Web")}</b>${a.date ? `<span>· ${ilYa(a.date)}</span>` : ""}</span>
      <span class="nv-titre">${echap(a.titre)}</span>
      ${grand && a.extrait ? `<span class="nv-extrait">${echap(a.extrait)}</span>` : ""}
    </a>`;
  }

  function rendreGrille() {
    const blocs = nv.donnees.filter((d) => nv.filtre === "tout" || d.rubrique === nv.filtre);
    if (!blocs.length) { q("#nv-grille").innerHTML = `<p class="vide">Aucune nouvelle trouvée. Vérifie la connexion Internet, puis Actualiser.</p>`; return; }
    q("#nv-grille").innerHTML = blocs.map((d, i) => `
      <section class="nv-bloc ${nv.filtre !== "tout" ? "large" : ""}" style="--i:${i}" aria-label="${echap(d.titre)}">
        <div class="nv-bloc-tete"><b>${echap(d.titre)}</b><span>${d.articles.length} titres</span></div>
        ${d.articles.length ? d.articles.map((a, j) => article(a, j, j === 0)).join("") : `<p class="vide">Rien pour l'instant.</p>`}
      </section>`).join("");
  }

  function rendreBandeau() {
    const titres = nv.donnees.flatMap((d) => d.articles.slice(0, 3).map((a) => ({ r: d.titre, t: a.titre })));
    const piste = q("#nv-piste");
    if (!titres.length) { piste.innerHTML = ""; return; }
    const html = titres.map((x) => `<span class="nv-item"><b>${echap(x.r)}</b>${echap(x.t)}</span>`).join("");
    piste.innerHTML = html + html; // en double pour une boucle sans coupure
    requestAnimationFrame(() => {
      const largeur = piste.scrollWidth / 2;
      piste.style.setProperty("--duree", Math.max(30, Math.round(largeur / 60)) + "s");
      piste.classList.remove("roule"); void piste.offsetWidth; piste.classList.add("roule");
    });
  }

  async function charger(force) {
    if (nv.charge) return;
    nv.charge = true;
    const vue = q("#v-nouvelles");
    vue.classList.add("charge");
    q("#nv-actualiser").disabled = true;
    if (force || !nv.donnees.length) squelettes();
    const demandes = RUBRIQUES.map((r) => api("nouvelles?rubrique=" + r.id).catch(() => ({ rubrique: r.id, titre: r.titre, articles: [] })));
    if (nv.sujet) demandes.push(api("nouvelles?rubrique=sujet&sujet=" + encodeURIComponent(nv.sujet)).then((d) => ({ ...d, rubrique: "sujet" })).catch(() => ({ rubrique: "sujet", titre: nv.sujet, articles: [] })));
    nv.donnees = await Promise.all(demandes);
    nv.charge = false;
    vue.classList.remove("charge");
    q("#nv-actualiser").disabled = false;
    q("#nv-maj").textContent = "Mis à jour à " + new Date().toLocaleTimeString("fr-CA", { hour: "2-digit", minute: "2-digit" });
    rendreRubriques();
    rendreGrille();
    rendreBandeau();
  }

  // Bulletin : l'IA locale résume les titres comme un lecteur de nouvelles
  async function bulletin() {
    if (!nv.donnees.length) await charger();
    const zone = q("#nv-resume");
    if (nv.bulletin) { nv.bulletin.abort(); nv.bulletin = null; }
    const ctrl = new AbortController();
    nv.bulletin = ctrl;
    const titres = nv.donnees.map((d) => "## " + d.titre + "\n" + d.articles.slice(0, 5).map((a) => "- " + a.titre + (a.source ? " (" + a.source + ")" : "")).join("\n")).join("\n\n");
    zone.hidden = false;
    zone.innerHTML = `<div class="nv-resume-tete"><span class="nv-direct"><i></i>BULLETIN</span><span class="sous" style="margin:0">Résumé par ton IA locale à partir des titres ci-dessous</span></div><div class="nv-resume-texte curseur"></div>`;
    const texte = zone.querySelector(".nv-resume-texte");
    let tout = "";
    try {
      const r = await api("jasette", {
        mode: "jasette", plan: "direct", internet: false, memoire: false, schema: false,
        messages: [{ role: "user", content: "Tu es lecteur de nouvelles le matin, au Québec. À partir SEULEMENT des titres suivants, fais un bulletin court et clair en français québécois (5 à 8 points, une phrase chacun, en commençant par le plus important). N'invente rien qui n'est pas dans les titres.\n\n" + titres }],
      }, { flux: true, signal: ctrl.signal });
      await lireFlux(r, (o) => {
        if (o.texte) { tout += o.texte; texte.textContent = tout; }
        if (o.erreur) texte.textContent = o.erreur;
      });
    } catch (e) {
      if (e.name !== "AbortError") texte.textContent = e.message;
    }
    texte.classList.remove("curseur");
    if (nv.bulletin === ctrl) nv.bulletin = null;
  }

  function rendre() {
    majHorloge();
    clearInterval(nv.horloge);
    nv.horloge = setInterval(() => { if (q("#v-nouvelles").classList.contains("actif")) majHorloge(); }, 15000);
    rendreRubriques();
    const vieux = !nv.donnees.length || Date.now() - (nv.donnees[0]?.heure || 0) * 1000 > 10 * 60 * 1000;
    if (vieux) charger(); else { rendreGrille(); rendreBandeau(); }
  }

  q("#nv-actualiser").onclick = () => charger(true);
  q("#nv-bulletin").onclick = bulletin;
  q("#nv-sujet-form").onsubmit = (e) => {
    e.preventDefault();
    nv.sujet = q("#nv-sujet").value.trim().slice(0, 80);
    nv.filtre = nv.sujet ? "sujet" : "tout";
    charger(true);
  };

  window.TiNouvelles = { rendre };
})();
