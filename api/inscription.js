// POST /api/inscription : inscription gratuite au concours (aucun achat requis).
import { put, head } from "@vercel/blob";
import { DOSSIER, FIN_CONCOURS, empreinte, json, lireCorps, stockagePret } from "./_commun.js";

const PROVINCES = ["AB", "BC", "MB", "NB", "NL", "NS", "NT", "NU", "ON", "PE", "QC", "SK", "YT"];
const COURRIEL = /^[^\s@]{1,64}@[^\s@]{1,190}\.[a-z]{2,24}$/i;

export default async function handler(req, res) {
  if (req.method !== "POST") return json(res, 405, { erreur: "Méthode non permise." });
  if (Date.now() > FIN_CONCOURS.getTime()) return json(res, 410, { erreur: "Le concours est terminé. Merci !" });
  if (!stockagePret()) return json(res, 503, { erreur: "Les inscriptions ouvrent très bientôt. Reviens dans quelques heures !" });

  let d;
  try { d = await lireCorps(req); } catch { return json(res, 400, { erreur: "Demande invalide." }); }

  if (d.site) return json(res, 200, { ok: true }); // piège à robots : on fait semblant que ça a marché

  const prenom = String(d.prenom || "").trim().slice(0, 60);
  const courriel = String(d.courriel || "").trim().toLowerCase().slice(0, 254);
  const province = String(d.province || "").trim().toUpperCase();
  if (!prenom) return json(res, 400, { erreur: "Écris ton prénom." });
  if (!COURRIEL.test(courriel)) return json(res, 400, { erreur: "Ton adresse courriel ne semble pas valide." });
  if (!PROVINCES.includes(province)) return json(res, 400, { erreur: "Le concours est ouvert aux résidents du Canada seulement." });
  if (d.majeur !== true && d.majeur !== "on") return json(res, 400, { erreur: "Il faut avoir l'âge de la majorité pour participer." });
  if (d.reglement !== true && d.reglement !== "on") return json(res, 400, { erreur: "Il faut accepter le règlement pour participer." });

  const chemin = DOSSIER + empreinte(courriel) + ".json";
  try {
    if (await head(chemin).catch(() => null)) {
      return json(res, 200, { ok: true, deja: true, message: `Tu es déjà inscrit, ${prenom} ! Une seule participation par personne. Bonne chance 🍀` });
    }
    const fiche = {
      prenom, courriel, province,
      nouvelles: d.nouvelles === true || d.nouvelles === "on",
      inscrit_le: new Date().toISOString(),
    };
    await put(chemin, JSON.stringify(fiche), {
      access: "private", addRandomSuffix: false, allowOverwrite: false, contentType: "application/json",
    });
    return json(res, 200, { ok: true, message: `C'est fait, ${prenom} ! Tu es inscrit au tirage du 21 décembre. Bonne chance 🍀` });
  } catch (e) {
    if (String(e && e.message).match(/exist|precondition/i)) {
      return json(res, 200, { ok: true, deja: true, message: "Tu es déjà inscrit ! Une seule participation par personne. Bonne chance 🍀" });
    }
    console.error("inscription", e);
    return json(res, 500, { erreur: "Oups, l'inscription n'a pas marché. Réessaie dans une minute." });
  }
}
