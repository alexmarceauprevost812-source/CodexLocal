// Réglages de départ de l'assistante (édition Famille de ti-lex).
// Tout se règle aussi dans l'appli : au premier démarrage, l'assistante demande
// le prénom (et la fête, facultatif). Ce qui est choisi dans « Réglages » a priorité.
window.CONFIG_ASSISTANTE = {
  // Prénom de la personne : laissé vide, il est demandé au premier démarrage.
  prenom: "",
  // Nom affiché de l'assistante, en haut de la fenêtre.
  nomAssistante: "Mon assistante",
  // Travail (facultatif), pour que l'assistante comprenne le contexte.
  // Exemple bidon : entreprise « Boutique Le Petit Bleuet », rôle « adjointe », ville « Saint-Bidon ».
  entreprise: "",
  role: "",
  ville: "",
  contexte: "",
  // Signature ajoutée au bas des courriels et lettres (une ligne par élément).
  signature: "",
  // Fête (MM-JJ), facultatif : confettis et message ce jour-là.
  fete: "",
  // Mot affiché le jour de la fête (vide = « De la part de ton assistante »).
  feteDe: "",
  // Qui appeler en cas de problème (ex. le nom de la personne qui a préparé la clé). Vide = message général.
  aide: "",

  // Moteur IA (ne pas changer sauf si tu sais pourquoi).
  temperature: 0.7,
  maxTokens: 1500,
  // Combien d'échanges récents on renvoie au modèle (la mémoire de la conversation).
  messagesGardes: 12,
};
