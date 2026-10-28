// Outils partagés du concours. Les inscriptions sont gardées dans un stockage Blob PRIVÉ de Vercel
// (un fichier JSON par personne, nommé par l'empreinte de son courriel : une seule inscription chacun).
import { createHash } from "node:crypto";

export const FIN_CONCOURS = new Date("2026-12-21T04:59:59Z"); // 20 décembre 2026, 23 h 59 (heure de l'Est)
export const DOSSIER = "concours-2026/";

export function empreinte(texte) {
  return createHash("sha256").update(texte).digest("hex");
}

export function stockagePret() {
  return Boolean(process.env.BLOB_READ_WRITE_TOKEN || process.env.BLOB_STORE_ID);
}

export function json(res, code, objet) {
  res.statusCode = code;
  res.setHeader("Content-Type", "application/json; charset=utf-8");
  res.setHeader("Cache-Control", "no-store");
  res.end(JSON.stringify(objet));
}

export async function lireCorps(req) {
  if (req.body && typeof req.body === "object") return req.body;
  const morceaux = [];
  let taille = 0;
  for await (const m of req) {
    taille += m.length;
    if (taille > 8000) throw new Error("trop gros");
    morceaux.push(m);
  }
  const brut = Buffer.concat(morceaux).toString("utf8");
  try { return JSON.parse(brut || "{}"); } catch { return Object.fromEntries(new URLSearchParams(brut)); }
}
