// Petit lecteur Markdown : transforme le texte de l'IA en blocs (titres,
// paragraphes, listes, tableaux). Les mêmes blocs servent à l'affichage ET à
// la création des fichiers Word et Excel, pour que le document ressemble à la
// réponse. Tout le texte est échappé : l'IA ne peut pas injecter de HTML.
(function () {
  "use strict";

  function lignesTableau(ligne) {
    let l = ligne.trim();
    if (l.startsWith("|")) l = l.slice(1);
    if (l.endsWith("|")) l = l.slice(0, -1);
    return l.split("|").map((c) => c.trim());
  }

  const estSeparateur = (l) => /^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$/.test(l);

  function blocs(texte) {
    const lignes = String(texte || "").replace(/\r\n?/g, "\n").split("\n");
    const out = [];
    let para = [];
    const finirPara = () => {
      if (para.length) out.push({ type: "p", text: para.join("\n") });
      para = [];
    };
    for (let i = 0; i < lignes.length; i++) {
      const l = lignes[i];
      if (/^\s*```/.test(l)) {
        finirPara();
        const code = [];
        for (i++; i < lignes.length && !/^\s*```/.test(lignes[i]); i++) code.push(lignes[i]);
        out.push({ type: "code", text: code.join("\n") });
        continue;
      }
      if (!l.trim()) { finirPara(); continue; }
      const h = l.match(/^\s*(#{1,4})\s+(.*)$/);
      if (h) { finirPara(); out.push({ type: "h", level: h[1].length, text: h[2].replace(/\s*#+\s*$/, "") }); continue; }
      if (/^\s*([-*_])\s*\1\s*\1[\s\1]*$/.test(l)) { finirPara(); out.push({ type: "hr" }); continue; }
      if (l.includes("|") && i + 1 < lignes.length && estSeparateur(lignes[i + 1])) {
        finirPara();
        const rows = [lignesTableau(l)];
        for (i += 2; i < lignes.length && lignes[i].includes("|") && lignes[i].trim(); i++) rows.push(lignesTableau(lignes[i]));
        i--;
        out.push({ type: "table", rows });
        continue;
      }
      const ul = l.match(/^\s*[-*•]\s+(.*)$/);
      const ol = l.match(/^\s*(\d+)[.)]\s+(.*)$/);
      if (ul || ol) {
        finirPara();
        const type = ul ? "ul" : "ol";
        const items = [];
        for (; i < lignes.length; i++) {
          const m = type === "ul" ? lignes[i].match(/^\s*[-*•]\s+(.*)$/) : lignes[i].match(/^\s*\d+[.)]\s+(.*)$/);
          if (m) items.push(m[1]);
          else if (lignes[i].trim() && /^\s{2,}/.test(lignes[i]) && items.length) items[items.length - 1] += " " + lignes[i].trim();
          else break;
        }
        i--;
        out.push({ type, items });
        continue;
      }
      para.push(l);
    }
    finirPara();
    return out;
  }

  // Texte en ligne -> morceaux { text, bold, italic, code }
  function morceaux(texte) {
    const res = [];
    const re = /(\*\*[^*]+\*\*|__[^_]+__|\*[^*\s][^*]*\*|_[^_\s][^_]*_|`[^`]+`)/g;
    let dernier = 0;
    let m;
    const s = String(texte || "");
    while ((m = re.exec(s))) {
      if (m.index > dernier) res.push({ text: s.slice(dernier, m.index) });
      const t = m[0];
      if (t.startsWith("**") || t.startsWith("__")) res.push({ text: t.slice(2, -2), bold: true });
      else if (t.startsWith("`")) res.push({ text: t.slice(1, -1), code: true });
      else res.push({ text: t.slice(1, -1), italic: true });
      dernier = m.index + t.length;
    }
    if (dernier < s.length) res.push({ text: s.slice(dernier) });
    return res;
  }

  const echapper = (s) => String(s).replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));

  function enLigne(texte) {
    return morceaux(texte).map((m) => {
      const t = echapper(m.text).replace(/\n/g, "<br>");
      if (m.bold) return `<strong>${t}</strong>`;
      if (m.italic) return `<em>${t}</em>`;
      if (m.code) return `<code>${t}</code>`;
      return t;
    }).join("");
  }

  function html(texte) {
    return blocs(texte).map((b) => {
      switch (b.type) {
        case "h": return `<h${b.level + 2}>${enLigne(b.text)}</h${b.level + 2}>`;
        case "p": return `<p>${enLigne(b.text)}</p>`;
        case "ul": return `<ul>${b.items.map((x) => `<li>${enLigne(x)}</li>`).join("")}</ul>`;
        case "ol": return `<ol>${b.items.map((x) => `<li>${enLigne(x)}</li>`).join("")}</ol>`;
        case "code": return `<pre><code>${echapper(b.text)}</code></pre>`;
        case "hr": return "<hr>";
        case "table": {
          const [tete, ...corps] = b.rows;
          return `<div class="tableau"><table><thead><tr>${tete.map((c) => `<th>${enLigne(c)}</th>`).join("")}</tr></thead>` +
            `<tbody>${corps.map((r) => `<tr>${r.map((c) => `<td>${enLigne(c)}</td>`).join("")}</tr>`).join("")}</tbody></table></div>`;
        }
        default: return "";
      }
    }).join("");
  }

  // Texte simple, sans les symboles Markdown (pour « Copier »).
  function texteSimple(texte) {
    return blocs(texte).map((b) => {
      const plat = (t) => morceaux(t).map((m) => m.text).join("");
      switch (b.type) {
        case "h": case "p": return plat(b.text);
        case "ul": return b.items.map((x) => "• " + plat(x)).join("\n");
        case "ol": return b.items.map((x, i) => `${i + 1}. ${plat(x)}`).join("\n");
        case "table": return b.rows.map((r) => r.map(plat).join("\t")).join("\n");
        case "code": return b.text;
        default: return "";
      }
    }).filter(Boolean).join("\n\n");
  }

  window.Markdown = { blocs, morceaux, html, texteSimple, echapper };
})();
