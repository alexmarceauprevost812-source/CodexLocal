// Ti-Labo — la carte meteo coloree dans la Jasette de la cle (meme carte que l'APK).
(function () {
  "use strict";
  const echap = t => String(t ?? "").replace(/[&<>"']/g, c => ({"&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"}[c]));
  const st = document.createElement("style");
  st.textContent = `  .tl-meteo{position:relative;overflow:hidden;border-radius:18px;padding:14px 16px;margin:6px 0 10px;color:#fff;white-space:normal;
    background:linear-gradient(135deg,var(--m1),var(--m2));box-shadow:0 8px 24px -10px var(--m1)}
  .tl-meteo .gros{display:flex;align-items:center;gap:12px}
  .tl-meteo .em{font-size:58px;line-height:1;filter:drop-shadow(0 3px 6px rgba(0,0,0,.3));animation:tlFlotte 3s ease-in-out infinite}
  @keyframes tlFlotte{50%{transform:translateY(-5px)}}
  .tl-meteo .temp{font-size:42px;font-weight:800;line-height:1}
  .tl-meteo .desc{font-size:15px;opacity:.95}
  .tl-meteo .lieu{font-size:13px;opacity:.85;margin-bottom:6px}
  .tl-meteo .details{display:flex;gap:12px;flex-wrap:wrap;font-size:13px;margin-top:10px;opacity:.95}
  .tl-meteo .jours{display:grid;grid-template-columns:repeat(4,1fr);gap:6px;margin-top:12px}
  .tl-meteo .jour{background:rgba(0,0,0,.22);border-radius:12px;padding:7px 4px;text-align:center;font-size:12px}
  .tl-meteo .jour .e{font-size:24px;display:block;margin:2px 0}
  .tl-meteo .jour b{font-size:13px}`;
  document.head.appendChild(st);
  const TL = {};
  const CODES = {0:["Ciel dégagé","☀️","🌙"],1:["Plutôt dégagé","🌤️","🌙"],2:["Partiellement nuageux","⛅","☁️"],3:["Couvert","☁️","☁️"],
    45:["Brouillard","🌫️"],48:["Brouillard givrant","🌫️"],51:["Bruine légère","🌦️"],53:["Bruine","🌦️"],55:["Bruine forte","🌧️"],
    56:["Bruine verglaçante","🧊"],57:["Bruine verglaçante","🧊"],61:["Pluie faible","🌦️"],63:["Pluie","🌧️"],65:["Forte pluie","🌧️"],
    66:["Pluie verglaçante","🧊"],67:["Pluie verglaçante","🧊"],71:["Neige faible","🌨️"],73:["Neige","❄️"],75:["Forte neige","❄️"],
    77:["Grains de neige","🌨️"],80:["Averses","🌦️"],81:["Averses","🌧️"],82:["Violentes averses","⛈️"],85:["Averses de neige","🌨️"],
    86:["Fortes averses de neige","❄️"],95:["Orage","⛈️"],96:["Orage avec grêle","⛈️"],99:["Orage avec grêle","⛈️"]};
  TL.codeMeteo = (c, jour) => { const x = CODES[c] || ["Météo", "🌡️"]; return {desc: x[0], emoji: (jour === 0 && x[2]) ? x[2] : x[1]}; };
  function couleurs(c, jour) {
    if (!jour) return ["#1e1b4b", "#4c1d95"];
    if (c >= 95) return ["#312e81", "#6d28d9"];
    if ([71, 73, 75, 77, 85, 86].includes(c)) return ["#0e7490", "#93c5fd"];
    if (c >= 51) return ["#1e3a8a", "#3b82f6"];
    if (c >= 45) return ["#475569", "#94a3b8"];
    if (c >= 2) return ["#2563eb", "#93c5fd"];
    return ["#ea580c", "#fbbf24"];
  }
  const JOURS = ["dim", "lun", "mar", "mer", "jeu", "ven", "sam"];
  TL.carteMeteo = m => {
    const a = m.actuel, j = m.jours, jour = a.is_day;
    const {desc, emoji} = TL.codeMeteo(a.weather_code, jour);
    const [c1, c2] = couleurs(a.weather_code, jour);
    const jours = (j.time || []).slice(0, 4).map((d, i) => {
      const x = TL.codeMeteo(j.weather_code[i], 1);
      const nom = i === 0 ? "auj." : JOURS[new Date(d + "T12:00").getDay()];
      return `<div class="jour">${nom}<span class="e">${x.emoji}</span><b>${Math.round(j.temperature_2m_max[i])}°</b> / ${Math.round(j.temperature_2m_min[i])}°` +
        (j.precipitation_probability_max && j.precipitation_probability_max[i] != null ? `<br>💧${j.precipitation_probability_max[i]}%` : "") + `</div>`;
    }).join("");
    return `<div class="tl-meteo" style="--m1:${c1};--m2:${c2}"><div class="lieu">📍 ${echap(m.lieu)}</div>` +
      `<div class="gros"><span class="em">${emoji}</span><div><div class="temp">${Math.round(a.temperature_2m)}°C</div><div class="desc">${desc}</div></div></div>` +
      `<div class="details"><span>🌡️ Ressenti ${Math.round(a.apparent_temperature)}°</span><span>💨 ${Math.round(a.wind_speed_10m)} km/h</span>` +
      `<span>💧 ${a.relative_humidity_2m}%</span>${a.precipitation > 0 ? `<span>☔ ${a.precipitation} mm</span>` : ""}</div>` +
      `<div class="jours">${jours}</div></div>`;
  };
  window.TiMeteo = {carte: TL.carteMeteo, code: TL.codeMeteo};
})();
