// Ti-Labo — le visage de l'IA : deux yeux orange qui vivent.
// repos : clignent et regardent autour · ecoute : regardent ce que tu ecris
// pense : cherchent en haut, plisses · parle : yeux rieurs qui sautillent · oups : yeux tristes (juste les yeux, sans visage dessine)
(function () {
  "use strict";
  const css = `
  .ti-yeux{--t:44px;display:inline-flex;align-items:center;justify-content:center;gap:calc(var(--t)*.16);
    width:var(--t);height:var(--t);border-radius:28%;background:radial-gradient(circle at 50% 35%,#1d0c03,#060302 70%);
    box-shadow:inset 0 0 0 1px rgba(255,122,26,.35),0 0 calc(var(--t)*.25) rgba(255,90,0,.18);flex:none;position:relative;overflow:hidden}
  .ti-yeux.grand{--t:150px;border-radius:30%;margin:0 auto}
  .ti-yeux .regard{display:flex;gap:calc(var(--t)*.16);transition:transform .35s cubic-bezier(.3,1.4,.5,1)}
  .ti-oeil{width:calc(var(--t)*.2);height:calc(var(--t)*.34);border-radius:calc(var(--t)*.1);
    background:linear-gradient(180deg,#ffb26b,#FF7A1A 55%,#e35d00);box-shadow:0 0 calc(var(--t)*.12) rgba(255,122,26,.75);
    transition:transform .12s ease,height .25s ease,border-radius .25s ease;transform-origin:50% 50%}
  .ti-yeux.cligne .ti-oeil{transform:scaleY(.08)}
  .ti-yeux.ecoute .ti-oeil{height:calc(var(--t)*.38)}
  .ti-yeux.pense .ti-oeil{height:calc(var(--t)*.2);border-radius:calc(var(--t)*.08)}
  .ti-yeux.pense .ti-oeil:last-child{height:calc(var(--t)*.26)}
  .ti-yeux.parle .ti-oeil{height:calc(var(--t)*.17);border-radius:calc(var(--t)*.12) calc(var(--t)*.12) calc(var(--t)*.03) calc(var(--t)*.03);
    animation:tiSautille .5s ease-in-out infinite alternate}
  .ti-yeux.oups .ti-oeil{height:calc(var(--t)*.22);border-radius:calc(var(--t)*.03) calc(var(--t)*.03) calc(var(--t)*.12) calc(var(--t)*.12);
    background:linear-gradient(180deg,#c2410c,#7c2d12)}
  .ti-yeux.oups .ti-oeil:first-child{transform:rotate(12deg)} .ti-yeux.oups .ti-oeil:last-child{transform:rotate(-12deg)}
  @keyframes tiSautille{to{transform:translateY(calc(var(--t)*-.04))}}
  .ti-yeux .points{position:absolute;bottom:14%;left:0;right:0;display:none;justify-content:center;gap:calc(var(--t)*.05)}
  .ti-yeux.pense .points{display:flex}
  .ti-yeux .points i{width:calc(var(--t)*.05);height:calc(var(--t)*.05);border-radius:50%;background:#FF7A1A;opacity:.3;animation:tiPoint 1.2s infinite}
  .ti-yeux .points i:nth-child(2){animation-delay:.2s}.ti-yeux .points i:nth-child(3){animation-delay:.4s}
  @keyframes tiPoint{40%{opacity:1}}
  @media (prefers-reduced-motion:reduce){.ti-yeux .regard,.ti-oeil{transition:none}.ti-yeux.parle .ti-oeil,.ti-yeux .points i{animation:none}}`;
  const st = document.createElement("style"); st.textContent = css; document.head.appendChild(st);

  const calme = matchMedia("(prefers-reduced-motion: reduce)").matches;
  const visages = [];
  let etat = "repos", minuterie = null;

  function creer(grand) {
    const d = document.createElement("div");
    d.className = "ti-yeux repos" + (grand ? " grand" : "");
    d.setAttribute("role", "img"); d.setAttribute("aria-label", "Le visage de l'IA");
    d.innerHTML = '<div class="regard"><span class="ti-oeil"></span><span class="ti-oeil"></span></div><div class="points"><i></i><i></i><i></i></div>';
    visages.push(d);
    return d;
  }
  const regarder = (x, y) => visages.forEach((v) => {
    const t = v.classList.contains("grand") ? 150 : 44;
    v.querySelector(".regard").style.transform = `translate(${x * t * .14}px,${y * t * .1}px)`;
  });
  function cligner() {
    visages.forEach((v) => v.classList.add("cligne"));
    setTimeout(() => visages.forEach((v) => v.classList.remove("cligne")), 130);
  }
  function vivre() {
    clearTimeout(minuterie);
    if (calme) return;
    const pas = () => {
      if (etat === "repos") {
        if (Math.random() < .45) cligner();
        else regarder(Math.random() * 2 - 1, Math.random() * 1.4 - .7);
      } else if (etat === "pense") {
        regarder(Math.random() < .5 ? -.8 : .8, -.8);
      } else if (etat === "ecoute") {
        if (Math.random() < .3) cligner();
      }
      minuterie = setTimeout(pas, etat === "pense" ? 700 : 1600 + Math.random() * 2600);
    };
    minuterie = setTimeout(pas, 900);
  }

  window.TiYeux = {
    /** Remplace un element (ex. le logo) par le visage. */
    remplacer(el, grand) { if (!el) return null; const v = creer(grand); el.replaceWith(v); return v; },
    /** repos | ecoute | pense | parle | oups */
    etat(nouveau) {
      if (nouveau === etat) return;
      etat = nouveau;
      visages.forEach((v) => { v.classList.remove("repos", "ecoute", "pense", "parle", "oups"); v.classList.add(nouveau); });
      if (nouveau === "ecoute") regarder(0, .8);
      else if (nouveau === "parle") regarder(0, 0);
      else if (nouveau === "repos") regarder(0, 0);
      else if (nouveau === "oups") regarder(0, .5);
      vivre();
    },
    get actuel() { return etat; },
  };
  vivre();
})();
