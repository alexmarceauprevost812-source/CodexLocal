# Mini Studio LexOS — base 0.1

Prototype autonome HTML/CSS/JavaScript ajouté comme asset de CodexLocal.
Il comprend plusieurs projets, notes/code texte, sauvegarde locale et export texte.
Il n'est **pas encore intégré** dans la navigation Android : ouvrir index.html dans un navigateur de développement pour l'essayer.

## Limites
- Aucun modèle GGUF chargé, renommé, inspecté ni publié.
- Aucun appel réseau, API, serveur REA ni exécution de code utilisateur.
- Les données sont conservées dans localStorage du contexte d'origine ; l'export sert de sauvegarde.
- La saisie par prompt/confirm et l'export navigateur devront être adaptés à WebView Android avant intégration.
- La connexion REA existante de CodexLocal est une fonction distincte ; l'analyse REA sera une étape ultérieure.

## Vérification
Depuis la racine du dépôt : `node --check app/src/main/assets/mini-studio/app.js`.
Ouvrir index.html, créer deux projets, enregistrer, recharger et vérifier la conservation des notes.
Ne pas fusionner avant les tests Android et une validation sur appareil.
