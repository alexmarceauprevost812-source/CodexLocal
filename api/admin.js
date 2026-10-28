// GET /api/admin?cle=...&action=compte|liste : réservé à l'organisateur.
// La clé n'est jamais écrite ici : seulement son empreinte SHA-256.
import { list, get } from "@vercel/blob";
import { DOSSIER, empreinte, json, stockagePret } from "./_commun.js";

const EMPREINTE_CLE = "d991058e3802027f4e2026f308fc697c0a5033d6c4d32747a0d60a12696abafc";

async function toutesLesFiches() {
  const fiches = [];
  let curseur;
  do {
    const page = await list({ prefix: DOSSIER, cursor: curseur, limit: 1000 });
    for (const b of page.blobs) {
      const r = await get(b.pathname, { access: "private", useCache: false });
      if (r && r.stream) fiches.push(JSON.parse(await new Response(r.stream).text()));
    }
    curseur = page.hasMore ? page.cursor : undefined;
  } while (curseur);
  return fiches.sort((a, b) => a.inscrit_le.localeCompare(b.inscrit_le));
}

export default async function handler(req, res) {
  const url = new URL(req.url, "http://x");
  if (empreinte(url.searchParams.get("cle") || "") !== EMPREINTE_CLE) return json(res, 403, { erreur: "Accès refusé." });
  if (!stockagePret()) return json(res, 503, { erreur: "Le stockage Blob n'est pas encore branché au projet." });
  const fiches = await toutesLesFiches();
  const action = url.searchParams.get("action") || "compte";
  if (action === "compte") return json(res, 200, { inscrits: fiches.length });
  if (action === "liste") return json(res, 200, { inscrits: fiches.length, fiches });
  return json(res, 400, { erreur: "Action inconnue." });
}
