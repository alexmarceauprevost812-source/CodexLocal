// Premier démarrage : l'assistante demande le prénom de la nouvelle personne (et sa fête, facultatif).
// Tout se change ensuite dans « Réglages ». Rien n'est envoyé ailleurs : c'est gardé dans le profil de la clé.
(function () {
  "use strict";
  const A = () => window.Assistante;

  function montrer() {
    const d = document.createElement("dialog");
    d.className = "dialogue";
    d.innerHTML = `
      <form method="dialog">
        <h2>👋 Bienvenue !</h2>
        <p class="petit">Je suis ton assistante. Elle fonctionne sur cet ordinateur, sans internet : tout ce que tu écris reste ici.</p>
        <label>Comment tu t'appelles ? <input id="bvPrenom" autocomplete="off" required maxlength="40" placeholder="Ton prénom"></label>
        <label>Ta fête (facultatif, pour une petite surprise ce jour-là) <input type="date" id="bvFete"></label>
        <label>Ton travail (facultatif) <input id="bvRole" autocomplete="off" maxlength="60" placeholder="Ex. : adjointe administrative"></label>
        <div class="actions-dialogue">
          <button class="bouton principal" value="ok">C'est parti !</button>
        </div>
      </form>`;
    document.body.appendChild(d);
    d.addEventListener("cancel", (e) => e.preventDefault()); // le prénom est demandé une seule fois, mais il faut répondre
    d.addEventListener("close", () => {
      const r = A().reglages();
      r.prenom = d.querySelector("#bvPrenom").value.trim();
      const fete = d.querySelector("#bvFete").value;
      if (fete) r.fete = fete.slice(5);
      const role = d.querySelector("#bvRole").value.trim();
      if (role) r.role = role;
      A().sauverReglages();
      d.remove();
      // le bureau et l'écran de verrouillage affichent le nouveau prénom
      const nom = document.getElementById("menuNom");
      if (nom) nom.textContent = r.prenom || "Moi";
      const av = document.getElementById("menuAvatar");
      if (av && r.prenom) av.textContent = r.prenom.charAt(0).toUpperCase();
    });
    d.showModal();
    setTimeout(() => d.querySelector("#bvPrenom").focus(), 60);
  }

  function essayer() {
    if (!A() || !A().reglages) return setTimeout(essayer, 300);
    if (A().reglages().prenom) return;
    // on attend que l'écran de verrouillage soit passé
    if (document.documentElement.classList.contains("verrouille")) return setTimeout(essayer, 800);
    montrer();
  }
  setTimeout(essayer, 700);
})();
