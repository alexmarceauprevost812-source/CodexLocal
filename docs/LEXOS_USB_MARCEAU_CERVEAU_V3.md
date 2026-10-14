# LexOS-USB + MARCEAU APK — plan d'intégration

Objectif : une application Android pour Samsung S25 Ultra réunissant MARCEAU Chat, MARCEAU Codex, LexOS-USB et des modèles GGUF locaux, avec un cerveau partagé.

## Priorités
1. **Préserver les données** : aucune désinstallation ni effacement de l'application existante. Diagnostiquer les signatures APK avant de proposer une mise à jour. Une nouvelle signature ne peut pas remplacer une application signée différemment.
2. **LexOS-USB** : accès aux dossiers de la clé USB-C par Android Storage Access Framework (ACTION_OPEN_DOCUMENT_TREE), avec permissions persistantes accordées par l'utilisateur; aucune supposition d'accès libre au système de fichiers.
3. **Modèles GGUF** : sélection de modèles sur stockage interne ou USB. Vérifier les droits d'accès, la compatibilité de llama.cpp, la lecture des fichiers et les contraintes mémoire avant de promettre l'exécution directe depuis USB. Prévoir une copie vers un stockage autorisé si nécessaire, après consentement et contrôle de l'espace.
4. **Cerveau V3 partagé** : mémoire persistante commune aux modes Chat, Codex et LexOS-USB, avec provenance, contexte de projet, récupération pertinente et possibilité de suppression. Le stockage mémoire est distinct des poids des modèles.
5. **Codex opérationnel** : lecture de fichiers de projet autorisés, génération de changements structurés, aperçu du diff, confirmation explicite, sauvegarde préalable et écriture atomique si possible. Pas d'exécution arbitraire sans consentement.
6. **Synchronisation facultative PC/USB** : exporter/importer les mémoires et projets sous un format versionné; résoudre les conflits, ne jamais écraser silencieusement.

## État constaté au 14 octobre 2026
- L'APK propose déjà un sélecteur de dossier USB/SAF et un éditeur de fichiers.
- Des classes de mémoire V3 ont été ajoutées, mais ne sont pas encore branchées au moteur de conversation.
- Le mode Codex utilise encore essentiellement le flux de discussion; l'édition autonome validée n'est pas réalisée.
- Le pipeline GitHub produit des APK debug; la stabilité de signature et la version doivent être corrigées avant toute mise à jour sur le téléphone qui contient le modèle.

## Critères de validation
- Le modèle actuel reste accessible et fonctionnel après mise à jour.
- Une clé USB peut être autorisée, parcourue et réouverte après redémarrage.
- Les mémoires créées dans Chat sont récupérables dans Codex et inversement, selon le projet et les permissions.
- Chaque changement de fichier est affiché et approuvé avant écriture.
- Les échecs (USB retirée, espace insuffisant, GGUF incompatible, signature incorrecte) affichent une erreur sans perte de données.
