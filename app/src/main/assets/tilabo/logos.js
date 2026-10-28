// Ti-Labo — petits logos dans les reponses de la Jasette, comme des emojis.
// 1) Les noms connus (cryptomonnaies, langages, marques) recoivent leur logo juste avant le mot.
// 2) Sinon, si Internet est permis : une petite image du sujet de la question (Wikipedia).
// Sans Internet, les images ne chargent pas et disparaissent toutes seules : la reponse reste intacte.
(function () {
  "use strict";
  const css = `.ti-logo{height:1.15em;width:auto;max-width:2.2em;vertical-align:-.22em;margin:0 .22em 0 .05em;border-radius:3px;display:inline-block}
  .ti-sujet{height:1.6em;width:1.6em;object-fit:cover;vertical-align:-.45em;margin-right:.35em;border-radius:6px;box-shadow:0 0 0 1px rgba(255,255,255,.15)}`;
  const st = document.createElement("style"); st.textContent = css; document.head.appendChild(st);

  const crypto = (c) => "https://cdn.jsdelivr.net/npm/cryptocurrency-icons@0.18.1/svg/color/" + c + ".svg";
  const marque = (slug, couleur) => "https://cdn.simpleicons.org/" + slug + (couleur ? "/" + couleur : "");
  // [motif, image, sensible a la casse ?] : les sigles (BTC, SOL…) seulement en MAJUSCULES pour eviter « sol », « link »…
  const LISTE = [
    ["Bitcoin|BTC", crypto("btc")], ["Ethereum|Ether|ETH", crypto("eth")], ["Solana|SOL", crypto("sol"), true],
    ["Dogecoin|DOGE", crypto("doge"), true], ["Cardano|ADA", crypto("ada"), true], ["XRP|Ripple", crypto("xrp")],
    ["Litecoin|LTC", crypto("ltc")], ["Tether|USDT", crypto("usdt")], ["USDC", crypto("usdc")], ["BNB", crypto("bnb")],
    ["Polkadot", crypto("dot")], ["Monero|XMR", crypto("xmr")], ["Tron|TRX", crypto("trx"), true],
    ["Chainlink", crypto("link")], ["Avalanche|AVAX", crypto("avax"), true], ["Polygon|MATIC", crypto("matic"), true],
    ["Stellar|XLM", crypto("xlm"), true], ["Shiba Inu|SHIB", crypto("shib")],
    ["Python", marque("python")], ["JavaScript", marque("javascript")], ["TypeScript", marque("typescript")],
    ["Kotlin", marque("kotlin")], ["Rust", marque("rust", "white"), true], ["PHP", marque("php")], ["C\\+\\+", marque("cplusplus")],
    ["HTML5?|HTML", marque("html5")], ["CSS3?", marque("css3")], ["React", marque("react"), true], ["Vue\\.js", marque("vuedotjs")],
    ["Node\\.js|Node", marque("nodedotjs"), true], ["npm", marque("npm"), true], ["Docker", marque("docker")], ["Git", marque("git"), true],
    ["GitHub", marque("github", "white")], ["Linux", marque("linux")], ["Ubuntu", marque("ubuntu")], ["Kali( Linux)?", marque("kalilinux"), true],
    ["Debian", marque("debian")], ["Android", marque("android")], ["Apple|iPhone|iOS|macOS", marque("apple", "white"), true],
    ["Google", marque("google")], ["Chrome", marque("googlechrome"), true], ["Firefox", marque("firefoxbrowser")],
    ["YouTube", marque("youtube")], ["Facebook", marque("facebook")], ["Instagram", marque("instagram")],
    ["TikTok", marque("tiktok", "white")], ["Discord", marque("discord")], ["Spotify", marque("spotify")],
    ["Netflix", marque("netflix")], ["Steam", marque("steam", "white"), true], ["PlayStation", marque("playstation")],
    ["Xbox", marque("xbox")], ["Nintendo", marque("nintendo")], ["Tesla", marque("tesla")], ["Samsung", marque("samsung")],
    ["Ollama", marque("ollama", "white")], ["Claude", marque("claude"), true], ["Anthropic", marque("anthropic", "white")],
    ["ChatGPT|OpenAI", marque("openai", "white")], ["Supabase", marque("supabase")], ["Stripe", marque("stripe")],
    ["Raspberry Pi", marque("raspberrypi")], ["Arduino", marque("arduino")], ["SQLite", marque("sqlite")],
    ["PostgreSQL", marque("postgresql")], ["MySQL", marque("mysql")], ["Termux", null],
  ].filter((x) => x[1]).map(([motif, src, casse]) => ({
    re: new RegExp("(?<![\\p{L}\\d_])(" + motif + ")(?![\\p{L}\\d_])", casse ? "u" : "iu"),
    src,
  }));

  function textes(el) {
    const res = [], w = document.createTreeWalker(el, NodeFilter.SHOW_TEXT, {
      acceptNode: (n) => n.parentElement.closest("pre,code,a,button,svg,.schema-carte,.tl-sources,.tl-outil,.tl-meteo,textarea")
        ? NodeFilter.FILTER_REJECT : NodeFilter.FILTER_ACCEPT,
    });
    while (w.nextNode()) res.push(w.currentNode);
    return res;
  }
  function image(src, cls, titre) {
    const i = document.createElement("img");
    i.src = src; i.className = cls; i.alt = ""; i.title = titre || ""; i.loading = "lazy"; i.decoding = "async";
    i.referrerPolicy = "no-referrer";
    i.onerror = () => i.remove(); // hors-ligne ou logo introuvable : on n'affiche rien
    return i;
  }

  /** Ajoute un logo devant la 1re apparition de chaque nom connu (au plus 10 par reponse). */
  function logosConnus(el) {
    let n = 0;
    const faits = new Set();
    let encore = true;
    while (encore && n < 10) {
      encore = false;
      chercher: for (const t of textes(el)) {
        for (const L of LISTE) {
          if (faits.has(L.src)) continue;
          const m = L.re.exec(t.nodeValue);
          if (!m) continue;
          const reste = t.splitText(m.index);
          reste.parentNode.insertBefore(image(L.src, "ti-logo", m[1]), reste);
          faits.add(L.src); n++; encore = true;
          break chercher; // le texte a change : on reparcourt avec les morceaux a jour
        }
      }
    }
    return n;
  }

  const cache = new Map();
  const HORS_SUJET = /m[ée]t[ée]o|agenda|rendez-vous|\bcode\b|fonction|erreur|bug|script|traduis|corrige|calcule|\d+\s*[x×*+\/-]\s*\d+/i;
  /** « c'est quoi le bitcoin ? » -> « bitcoin » */
  function sujetDe(q) {
    return String(q || "").replace(/[?!.«»"]/g, " ")
      .replace(/^\s*(c'?est quoi|qu'?est[- ]ce (que|qu')|parle[- ]moi (de|du|des)|explique[- ]moi|explique|dis[- ]moi|qui (est|sont|était)|que sais[- ]tu (de|sur|du)|montre[- ]moi|raconte[- ]moi)\s*/i, "")
      .replace(/^\s*(le|la|les|l'|un|une|des|du|de)\s+/i, "")
      .trim().split(/\s+/).slice(0, 6).join(" ");
  }
  async function imageSujet(q) {
    const s = sujetDe(q);
    if (s.length < 3 || s.length > 60) return null;
    if (cache.has(s)) return cache.get(s);
    let r = null;
    try {
      const u = "https://fr.wikipedia.org/w/api.php?action=query&format=json&origin=*&generator=search&gsrlimit=1&prop=pageimages&piprop=thumbnail&pithumbsize=96&gsrsearch=" + encodeURIComponent(s);
      const j = await (await fetch(u)).json();
      const p = j.query && Object.values(j.query.pages || {})[0];
      if (p && p.thumbnail) r = {src: p.thumbnail.source, titre: p.title};
    } catch (_) {}
    cache.set(s, r);
    return r;
  }

  window.TiLogos = {
    /** el : la reponse affichee ; question : ce que la personne a demande ; internet : vrai si permis. */
    async activer(el, question, internet) {
      if (!el) return;
      let n = 0;
      try { n = logosConnus(el); } catch (_) {}
      if (n || !internet || !question || question.length > 140 || HORS_SUJET.test(question)) return;
      const img = await imageSujet(question);
      if (!img || !el.isConnected) return;
      const debut = el.querySelector("h1,h2,h3,h4,p,li") || el;
      debut.insertBefore(image(img.src, "ti-sujet", img.titre), debut.firstChild);
    },
    sujetDe,
  };
})();
