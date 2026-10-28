/* Ti-Labo — coloration du code, 100 % hors-ligne (aucune librairie externe) */
"use strict";

(function () {
  const MOTS = {
    python: "False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield match case self print len range",
    js: "async await break case catch class const continue debugger default delete do else export extends false finally for function if import in instanceof let new null of return static super switch this throw true try typeof undefined var void while with yield console document window",
    css: "important",
    bash: "if then else elif fi for while do done case esac function return in export local echo cd sudo",
    c: "auto break case char const continue default do double else enum extern float for goto if int long register return short signed sizeof static struct switch typedef union unsigned void volatile while class public private protected namespace using new delete true false nullptr include define fn let mut pub impl use mod match struct enum trait self Self",
  };
  const ALIAS = {
    py: "python", python: "python", js: "js", javascript: "js", ts: "js", typescript: "js", jsx: "js", tsx: "js",
    json: "js", mjs: "js", css: "css", scss: "css", html: "html", htm: "html", xml: "html", svg: "html", vue: "html",
    sh: "bash", bash: "bash", zsh: "bash", shell: "bash", bat: "bash", ps1: "bash", powershell: "bash",
    c: "c", h: "c", cpp: "c", "c++": "c", java: "c", kt: "c", cs: "c", go: "c", rs: "c", rust: "c", php: "c",
  };

  const esc = (t) => t.replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
  const span = (cls, t) => `<span class="tk-${cls}">${esc(t)}</span>`;

  function langueDe(nomOuLangue) {
    const x = String(nomOuLangue || "").toLowerCase();
    const ext = x.includes(".") ? x.split(".").pop() : x;
    return ALIAS[ext] || (x.endsWith("makefile") || x.endsWith("dockerfile") ? "bash" : "texte");
  }

  function regles(lang) {
    const com = {
      python: "#[^\\n]*", bash: "#[^\\n]*", js: "\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?(?:\\*\\/|$)",
      c: "\\/\\/[^\\n]*|\\/\\*[\\s\\S]*?(?:\\*\\/|$)|^\\s*#\\w+", css: "\\/\\*[\\s\\S]*?(?:\\*\\/|$)",
    }[lang];
    const chaines = lang === "python"
      ? "[rbfuRBFU]{0,2}(?:\"\"\"[\\s\\S]*?(?:\"\"\"|$)|'''[\\s\\S]*?(?:'''|$)|\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?)"
      : "\"(?:\\\\.|[^\"\\\\\\n])*\"?|'(?:\\\\.|[^'\\\\\\n])*'?|`(?:\\\\.|[^`\\\\])*`?";
    const mots = MOTS[lang] ? "\\b(?:" + MOTS[lang].split(" ").join("|") + ")\\b" : null;
    const parts = [];
    if (com) parts.push(["c", com]);
    parts.push(["s", chaines]);
    if (lang === "python") parts.push(["d", "@[\\w.]+"]);
    if (lang === "css") parts.push(["a", "[\\w-]+(?=\\s*:)"], ["f", "[.#]?[\\w-]+(?=[^{};]*\\{)"]);
    if (mots) parts.push(["k", mots]);
    parts.push(["n", "\\b(?:0x[\\da-fA-F]+|\\d+(?:\\.\\d+)?(?:e[+-]?\\d+)?)\\b"]);
    parts.push(["f", "\\b[A-Za-z_]\\w*(?=\\s*\\()"]);
    if (lang === "bash") parts.push(["a", "\\$\\{?\\w+\\}?|\\s-{1,2}[\\w-]+"]);
    if (lang === "python" || lang === "js" || lang === "c") parts.push(["t", "\\b[A-Z][A-Za-z0-9_]*\\b"]);
    return new RegExp(parts.map(([, r]) => "(" + r + ")").join("|"), "gm");
    // (l'ordre des groupes suit "parts" ; voir colorerTexte)
  }

  const cache = {};
  function colorerTexte(code, lang) {
    if (lang === "html") return colorerHtml(code);
    if (lang === "texte") return esc(code);
    const cle = lang;
    if (!cache[cle]) {
      const re = regles(lang);
      const classes = [];
      // reconstruit la liste des classes dans le meme ordre que regles()
      const com = ["python", "bash", "js", "c", "css"].includes(lang);
      if (com) classes.push("c");
      classes.push("s");
      if (lang === "python") classes.push("d");
      if (lang === "css") classes.push("a", "f");
      if (MOTS[lang]) classes.push("k");
      classes.push("n", "f");
      if (lang === "bash") classes.push("a");
      if (lang === "python" || lang === "js" || lang === "c") classes.push("t");
      cache[cle] = { re, classes };
    }
    const { re, classes } = cache[cle];
    re.lastIndex = 0;
    let sortie = "", dernier = 0, m;
    while ((m = re.exec(code))) {
      if (m[0] === "") { re.lastIndex++; continue; }
      sortie += esc(code.slice(dernier, m.index));
      let k = 1;
      while (k < m.length && m[k] === undefined) k++;
      sortie += span(classes[k - 1] || "p", m[0]);
      dernier = m.index + m[0].length;
    }
    return sortie + esc(code.slice(dernier));
  }

  function colorerHtml(code) {
    const re = /(<!--[\s\S]*?(?:-->|$))|(<\/?)([\w:-]+)((?:\s+[\w:@.-]+(?:\s*=\s*(?:"[^"]*"?|'[^']*'?|[^\s>]+))?)*)\s*(\/?>)?/gi;
    let sortie = "", dernier = 0, m;
    while ((m = re.exec(code))) {
      sortie += esc(code.slice(dernier, m.index));
      if (m[1]) sortie += span("c", m[1]);
      else {
        sortie += span("p", m[2]) + span("k", m[3]);
        sortie += (m[4] || "").replace(/([\w:@.-]+)(\s*=\s*)?("[^"]*"?|'[^']*'?|[^\s>]+)?|(\s+)/g, (t, a, eg, v, sp) =>
          sp ? sp : span("a", a) + (eg ? esc(eg) : "") + (v ? span("s", v) : ""));
        if (m[5]) sortie += span("p", m[5]);
      }
      dernier = m.index + m[0].length;
    }
    return sortie + esc(code.slice(dernier));
  }

  window.TiCouleurs = { colorer: colorerTexte, langueDe };
})();
