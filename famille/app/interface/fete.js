// Le jour de la fête de la personne (Réglages ou premier démarrage) : confettis et petit mot,
// une fois par jour ce jour-là (et sur demande, à partir du bureau).
(function () {
  "use strict";
  const $ = (id) => document.getElementById(id);
  const CFG = window.CONFIG_ASSISTANTE || {};
  const A = () => window.Assistante;
  const jourFete = () => (A() && A().reglages().fete) || CFG.fete || ""; // MM-JJ, vide = pas de fête
  const mmjj = (d) => `${String(d.getMonth() + 1).padStart(2, "0")}-${String(d.getDate()).padStart(2, "0")}`;
  const estFete = () => !!jourFete() && mmjj(new Date()) === jourFete();
  const prenom = () => (A() && A().reglages().prenom) || CFG.prenom || "";

  // ------------------------------------------------------------ confettis
  let anim = null;
  function confettis(canvas, duree) {
    const reduit = window.matchMedia && matchMedia("(prefers-reduced-motion: reduce)").matches;
    const g = canvas.getContext("2d");
    const taille = () => { canvas.width = innerWidth * devicePixelRatio; canvas.height = innerHeight * devicePixelRatio; };
    taille(); addEventListener("resize", taille);
    const couleurs = ["#ff4d6d", "#ffd166", "#06d6a0", "#118ab2", "#f78c6b", "#c77dff", "#ffffff"];
    const n = reduit ? 60 : 220;
    const p = Array.from({ length: n }, () => ({
      x: Math.random() * canvas.width, y: -Math.random() * canvas.height, r: (4 + Math.random() * 6) * devicePixelRatio,
      vy: (1.5 + Math.random() * 3) * devicePixelRatio, vx: (Math.random() - 0.5) * 1.6 * devicePixelRatio,
      a: Math.random() * Math.PI, va: (Math.random() - 0.5) * 0.2, c: couleurs[(Math.random() * couleurs.length) | 0], forme: Math.random() < 0.3 ? "rond" : "carre",
    }));
    const fin = performance.now() + duree;
    cancelAnimationFrame(anim);
    const tour = (t) => {
      g.clearRect(0, 0, canvas.width, canvas.height);
      const encore = t < fin;
      let vivants = 0;
      for (const q of p) {
        q.x += q.vx + Math.sin(q.a) * 0.6; q.y += q.vy; q.a += q.va;
        if (q.y > canvas.height + 20) { if (!encore) continue; q.y = -20; q.x = Math.random() * canvas.width; }
        vivants++;
        g.save(); g.translate(q.x, q.y); g.rotate(q.a); g.fillStyle = q.c;
        if (q.forme === "rond") { g.beginPath(); g.arc(0, 0, q.r / 2, 0, Math.PI * 2); g.fill(); } else g.fillRect(-q.r / 2, -q.r / 4, q.r, q.r / 2);
        g.restore();
      }
      if (vivants) anim = requestAnimationFrame(tour); else removeEventListener("resize", taille);
    };
    anim = requestAnimationFrame(tour);
  }

  // -------------------------------------------------------------- surprise
  function montrer() {
    const p = prenom();
    $("feteTitre").textContent = p ? `Bonne fête ${p} !` : "Bonne fête !";
    if (CFG.feteDe) $("feteDe").textContent = CFG.feteDe;
    A() && A().ecrireDoucement ? A().ecrireDoucement($("feteTitre"), $("feteTitre").textContent + " 🎉") : 0;
    $("fete").classList.remove("cache");
    $("fete").setAttribute("aria-hidden", "false");
    confettis($("feteConfettis"), 9000);
    setTimeout(() => $("feteMerci").focus(), 60);
  }
  function fermer() {
    $("fete").classList.add("cache");
    $("fete").setAttribute("aria-hidden", "true");
    try { speechSynthesis.cancel(); } catch {}
  }
  $("feteMerci").onclick = fermer;
  $("feteEcouter").onclick = () => {
    try {
      speechSynthesis.cancel();
      const texte = `${$("feteTitre").textContent.replace(/[🎉]/gu, "")} ${$("feteDe").textContent.replace(/[👀]/gu, "")} ${$("feteSouhait").textContent}`;
      const u = new SpeechSynthesisUtterance(texte);
      const v = speechSynthesis.getVoices().filter((x) => x.localService !== false && /^fr/i.test(x.lang));
      u.voice = v.find((x) => /CA/i.test(x.lang)) || v[0] || null; u.lang = (u.voice && u.voice.lang) || "fr-CA";
      speechSynthesis.speak(u);
    } catch {}
  };
  document.addEventListener("keydown", (e) => { if (e.key === "Escape" && !$("fete").classList.contains("cache")) fermer(); });

  // Une fois par jour de fête, dès que le mini-ordinateur est déverrouillé.
  function peutEtre() {
    if (!estFete()) return;
    const cle = new Date().getFullYear() + "-" + jourFete();
    let deja = null; try { deja = localStorage.getItem("fete-vue"); } catch {}
    if (deja === cle) return;
    const essayer = () => {
      if (document.documentElement.classList.contains("verrouille")) { setTimeout(essayer, 800); return; }
      try { localStorage.setItem("fete-vue", cle); } catch {}
      montrer();
    };
    setTimeout(essayer, 900);
  }
  // Toute la journée : un bouton sur le bureau pour revoir la surprise.
  function bouton() { $("widgetFete").classList.toggle("cache", !estFete()); }
  $("widgetFete").onclick = montrer;
  bouton(); peutEtre();
  setInterval(() => { bouton(); peutEtre(); }, 60000);

  window.Fete = { montrer, estFete };
})();
