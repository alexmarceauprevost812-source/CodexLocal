/* LexApp v1 — moteur local CodexLocal, sans dépendance ni téléchargement. */
(function (global) {
  'use strict';
  const reduced = () => global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)').matches;
  let serial = 0;
  function el(tag, text, attrs = {}) {
    const node = document.createElement(tag);
    if (text != null) node.textContent = String(text);
    Object.entries(attrs).forEach(([key, value]) => node.setAttribute(key, String(value)));
    return node;
  }
  function style() {
    if (document.getElementById('lexapp-style-v1')) return;
    const css = el('style', `
      .lexapp{font:16px system-ui,sans-serif;color:#fff;background:#080808;min-height:100vh;box-sizing:border-box;padding:20px;max-width:960px;margin:auto}
      .lexapp *{box-sizing:border-box}.lexapp h1{color:#ff7900;font-size:1.6rem}.lexapp nav{display:flex;gap:8px;flex-wrap:wrap;margin:20px 0}
      .lexapp button{font:inherit;min-height:48px;padding:10px 16px;border-radius:12px;border:1px solid #ff7900;background:#18100b;color:white;cursor:pointer}
      .lexapp button[aria-current=page]{background:#ff7900;color:#090909}.lexapp :focus-visible{outline:3px solid #7eddff;outline-offset:3px}
      .lexapp section{border:1px solid #383838;border-radius:16px;padding:20px;margin:12px 0}.lexapp input,.lexapp textarea{font:inherit;width:100%;padding:12px;border:1px solid #777;border-radius:8px;background:#111;color:white;margin:8px 0}
      .lexapp svg{width:100%;height:auto;display:block}.lexapp canvas{display:block;width:100%;height:220px;border-radius:12px;background:#111}
      .lexapp .lex-notice{color:#ccc;font-size:.9rem}.lexapp main{overflow-wrap:anywhere}
      @media(prefers-reduced-motion:reduce){.lexapp *{scroll-behavior:auto!important;animation:none!important;transition:none!important}}
    `, {id: 'lexapp-style-v1'});
    document.head.append(css);
  }
  function mount(root, config) {
    if (!root || !Array.isArray(config.pages) || !config.pages.length) throw Error('Pages requises');
    const ids = config.pages.map(p => p.id);
    if (new Set(ids).size !== ids.length || config.pages.some(p => typeof p.render !== 'function')) throw Error('Pages invalides');
    style(); root.replaceChildren(); root.classList.add('lexapp');
    root.append(el('h1', config.title || 'Mon application'));
    const nav = el('nav', null, {'aria-label': 'Pages de l’application'});
    const content = el('main', null, {tabindex: '-1'});
    let cleanup = null, destroyed = false;
    const buttons = new Map();
    function show(id, focus = false) {
      if (destroyed) return;
      const page = config.pages.find(p => p.id === id);
      if (!page) throw Error('Page inconnue');
      if (typeof cleanup === 'function') cleanup();
      cleanup = null; content.replaceChildren();
      buttons.forEach((button, key) => { if (key === id) button.setAttribute('aria-current', 'page'); else button.removeAttribute('aria-current'); });
      cleanup = page.render(content);
      if (focus) content.focus();
    }
    config.pages.forEach(page => {
      const button = el('button', page.title || page.id, {type: 'button'});
      button.addEventListener('click', () => show(page.id, true));
      buttons.set(page.id, button); nav.append(button);
    });
    root.append(nav, content); show(config.pages[0].id);
    return {show, destroy() { if (destroyed) return; destroyed = true; if (typeof cleanup === 'function') cleanup(); root.replaceChildren(); root.classList.remove('lexapp'); }};
  }
  function save(key, value) {
    try { global.localStorage.setItem('lexapp:' + key, JSON.stringify(value)); return true; } catch (_) { return false; }
  }
  function load(key, fallback) {
    try { const value = global.localStorage.getItem('lexapp:' + key); return value == null ? fallback : JSON.parse(value); } catch (_) { return fallback; }
  }
  function animate(node, effect = 'appear', duration = 450) {
    const frames = {
      appear: [{opacity: 0, transform: 'translateY(14px)'}, {opacity: 1, transform: 'translateY(0)'}],
      pulse: [{transform: 'scale(1)'}, {transform: 'scale(1.08)'}, {transform: 'scale(1)'}],
      turn: [{transform: 'rotate(0deg)'}, {transform: 'rotate(360deg)'}]
    };
    if (!frames[effect]) throw Error('Effet inconnu');
    if (reduced() || !node.animate) return {cancel() {}};
    return node.animate(frames[effect], {duration: Math.max(100, Math.min(5000, Number(duration) || 450)), easing: 'ease-out'});
  }
  function particles(canvas, options = {}) {
    const ctx = canvas.getContext('2d');
    if (!ctx) throw Error('Canvas 2D indisponible');
    const count = Math.max(1, Math.min(120, Number(options.count) || 35));
    const dots = Array.from({length: count}, () => ({x: Math.random(), y: Math.random(), speed: .02 + Math.random() * .05}));
    let frame = null, last = null, stopped = false;
    function draw(now) {
      const width = canvas.clientWidth || 300, height = canvas.clientHeight || 220;
      const ratio = Math.min(global.devicePixelRatio || 1, 2);
      if (canvas.width !== Math.round(width * ratio) || canvas.height !== Math.round(height * ratio)) {
        canvas.width = Math.round(width * ratio); canvas.height = Math.round(height * ratio);
      }
      ctx.setTransform(ratio, 0, 0, ratio, 0, 0); ctx.clearRect(0, 0, width, height);
      const dt = last == null ? 0 : Math.min((now - last) / 1000, .05); last = now;
      ctx.fillStyle = options.color || '#ff7900';
      dots.forEach(dot => { dot.y = (dot.y + dt * dot.speed) % 1; ctx.beginPath(); ctx.arc(dot.x * width, dot.y * height, 2.5, 0, Math.PI * 2); ctx.fill(); });
    }
    function tick(now) { frame = null; if (stopped || document.hidden || reduced()) return; draw(now); frame = global.requestAnimationFrame(tick); }
    function resume() { if (frame != null) global.cancelAnimationFrame(frame); frame = null; last = null; if (!stopped) { draw(0); if (!document.hidden && !reduced()) frame = global.requestAnimationFrame(tick); } }
    document.addEventListener('visibilitychange', resume);
    const motion = global.matchMedia && global.matchMedia('(prefers-reduced-motion: reduce)');
    if (motion && motion.addEventListener) motion.addEventListener('change', resume);
    resume();
    return () => { stopped = true; if (frame != null) global.cancelAnimationFrame(frame); document.removeEventListener('visibilitychange', resume); if (motion && motion.removeEventListener) motion.removeEventListener('change', resume); };
  }
  function diagram(root, config) {
    const nodes = config.nodes || [], edges = config.edges || [];
    if (nodes.length > 60 || edges.length > 120) throw Error('Schéma trop grand');
    const positions = new Map();
    nodes.forEach((node, i) => {
      if (node.id == null || positions.has(node.id)) throw Error('Identifiant de nœud invalide');
      positions.set(node.id, {x: 30 + (i % 2) * 260, y: 35 + Math.floor(i / 2) * 130, node});
    });
    edges.forEach(edge => { if (!positions.has(edge.from) || !positions.has(edge.to)) throw Error('Lien vers un nœud absent'); });
    const ns = 'http://www.w3.org/2000/svg';
    function svgEl(tag, attrs, text) { const node = document.createElementNS(ns, tag); Object.entries(attrs || {}).forEach(([key, value]) => node.setAttribute(key, String(value))); if (text != null) node.textContent = String(text); return node; }
    const svg = svgEl('svg', {viewBox: `0 0 520 ${Math.max(170, Math.ceil(nodes.length / 2) * 130 + 30)}`, role: 'img'});
    const title = svgEl('title', {id: 'lex-title-' + (++serial)}, config.title || 'Schéma');
    svg.setAttribute('aria-labelledby', title.id); svg.append(title);
    const markerId = 'lex-arrow-' + serial, defs = svgEl('defs'), marker = svgEl('marker', {id: markerId, markerWidth: 10, markerHeight: 10, refX: 8, refY: 3, orient: 'auto', markerUnits: 'strokeWidth'});
    marker.append(svgEl('path', {d: 'M0,0 L0,6 L9,3 z', fill: '#ff7900'})); defs.append(marker); svg.append(defs);
    edges.forEach(edge => {
      const a = positions.get(edge.from), b = positions.get(edge.to);
      const sameRow = a.y === b.y;
      const forward = a.x < b.x;
      const x1 = sameRow ? a.x + (forward ? 200 : 0) : a.x + 100;
      const x2 = sameRow ? b.x + (forward ? 0 : 200) : b.x + 100;
      const down = b.y > a.y;
      const y1 = sameRow ? a.y + 36 : a.y + (down ? 72 : 0);
      const y2 = sameRow ? b.y + 36 : b.y + (down ? 0 : 72);
      svg.append(svgEl('path', {d: `M${x1},${y1} L${x2},${y2}`, stroke: '#ff7900', 'stroke-width': 2, fill: 'none', 'marker-end': `url(#${markerId})`}));
    });
    positions.forEach(({x, y, node}) => {
      svg.append(svgEl('rect', {x, y, width: 200, height: 72, rx: 12, fill: '#171717', stroke: '#ff7900'}));
      const text = svgEl('text', {x: x + 100, y: y + 28, fill: '#fff', 'text-anchor': 'middle', 'font-size': 15, 'font-family': 'sans-serif'});
      const label = String(node.label == null ? node.id : node.label);
      const words = label.split(/\s+/); const lines = [''];
      words.forEach(word => { const i = lines.length - 1; if ((lines[i] + ' ' + word).trim().length > 21 && lines[i]) lines.push(word); else lines[i] = (lines[i] + ' ' + word).trim(); });
      lines.slice(0, 2).forEach((line, i) => text.append(svgEl('tspan', {x: x + 100, dy: i ? 21 : 0}, line.length > 22 ? line.slice(0, 21) + '…' : line)));
      const tooltip = svgEl('title', {}, label); text.append(tooltip); svg.append(text);
    });
    root.replaceChildren(svg); return svg;
  }
  global.LexApp = Object.freeze({version: '1.0.0', mount, el, save, load, animate, particles, diagram});
})(window);
