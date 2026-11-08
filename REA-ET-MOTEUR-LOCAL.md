# REA et moteur d’application local

## Créer une application sur le téléphone

Dans CodexLocal : **⋮ → Créer une appli · moteur local**.
Un nouveau projet indépendant est créé avec une application fonctionnelle :
navigation entre pages, notes enregistrées localement, effets 2D et schéma SVG.
Le Studio s’ouvre sur ce projet : son aperçu et son constructeur d’APK existants
servent à le tester puis à l’installer. Retourne au mode Codex pour demander
à ton modèle local d’adapter les fichiers du projet.

Le moteur JavaScript **LexApp v1** est livré dans l’APK de CodexLocal et copié
dans les projets sous `moteurs/lexapp-v1.js`. Il fonctionne sans CDN et sans
téléchargement de modèle supplémentaire. Il s’agit d’un moteur d’application
HTML/CSS/JavaScript exécuté par le WebView Android du Studio.

Les consignes de Codex, de l’Équipe et du Studio décrivent son API au modèle.
Quand une proposition de l’IA référence le moteur, son fichier est ajouté
automatiquement s’il manque. Une version déjà présente dans le projet est
conservée. La copie par l’agent utilise ses actions de fichiers habituelles et
fait partie de l’annulation. L’efficacité de génération dépend du modèle choisi.

L’API comprend `LexApp.mount`, `el`, `save`, `load`, `animate`, `particles` et
`diagram`. `mount` reçoit les pages et leurs fonctions `render(root)`;
chaque fonction peut retourner son nettoyage. Les particules sont arrêtées
en quittant la page et mises en pause quand l’application est masquée.
Les effets respectent la préférence système de réduction des animations.
Le stockage local appartient à chaque application; sauvegarder indique si
le stockage est indisponible. Les schémas dessinent les nœuds et liens fournis,
sans déduire de relations. Ce moteur ne fournit pas de génération d’images ni de 3D.

## Connecter REA sur le PC

> Une appli dédiée, **Codex REA** (`-Prea`), ouvre directement ce mode et fonctionne **sans PC** grâce à l'analyse locale du téléphone (sans adresse REA, le mode 🔍 utilise ce moteur local) : voir la section « Codex REA » du README.

Le mode **🔍 REA** fonctionne depuis la version 1.6 comme une boucle d'agent façon Codex :
le modèle planifie, appelle les outils d'analyse du PC, lit les résultats, corrige ses appels
et conclut, avec les mêmes actions de fichiers que Codex sur le téléphone.

- Jusqu'à **4 appels indépendants par réponse** et **24 appels par demande**; une phrase de plan est permise.
- Un appel mal formé ou une erreur d'outil est **renvoyé au modèle** pour qu'il se corrige;
  trois erreurs de suite arrêtent la demande. À la fin du budget, le modèle doit conclure.
- Aucun fichier du téléphone n'est écrit dans une réponse qui contient un appel REA :
  le modèle les renvoie après avoir reçu les résultats.
- Le modèle reçoit le catalogue réel des outils autorisés et leurs schémas; preuves et
  diagnostics lui sont renvoyés, les résultats trop longs sont tronqués et signalés.

Le premier catalogue comprend uniquement les outils disponibles parmi :
`inspect_artifact`, `inspect_apk_signature`, `analyze_javascript_application`,
`inspect_analysis_view`, `get_evidence_bundle`, `list_unknowns`.
Les fonctions d’exécution de programmes et de modification du PC ne sont pas
exposées par ce client. Les modifications de projet restent celles de CodexLocal
sur le téléphone. L’analyse d’APK complète par JADX n’est pas intégrée ici.

REA s’installe séparément sur le PC depuis [morluto/rea](https://github.com/morluto/rea).
Avec une version disposant du transport HTTP documenté :

```bash
rea mcp --http --host 127.0.0.1 --port 8766
```

L’interface HTTP native de REA n’a pas d’authentification. Garde cette écoute
sur le PC en boucle locale. Pour y accéder depuis Android, utilise un tunnel
SSH existant, par exemple depuis un terminal Android disposant de SSH :

```bash
ssh -N -L 8766:127.0.0.1:8766 marceau@ADRESSE_DU_PC
```

Dans **⋮ → Connexion REA**, renseigne `http://127.0.0.1:8766/mcp`.
Une adresse HTTPS avec certificat valide et un proxy authentifié est aussi
acceptée; le jeton optionnel devient l’en-tête Bearer. Les adresses HTTP
distantes, les redirections, les identifiants et jetons dans l’URL sont refusés.
Le tunnel ou proxy et le serveur PC doivent être configurés séparément.

Donne à l’IA le chemin exact du fichier ou dossier **sur le PC**, par exemple :
« Avec REA, examine `/home/marceau/projets/mon-app` et explique son organisation ».
Les fichiers du téléphone ne sont pas téléversés automatiquement. Les preuves
vont au fournisseur IA sélectionné (modèle local ou API distante).
Une connexion MCP est initialisée par demande, puis fermée même sur erreur.
Les erreurs et les résultats tronqués sont signalés; aucune réussite n’est simulée.

## Vérification

Tests Android : `:app:testDebugUnitTest` (protocole JSON/SSE, session HTTP,
filtrage des outils, URL, création du projet et conservation du moteur existant).
Test du moteur dans Chromium : `node scripts/test_moteur_local.cjs` avec
Playwright installé. Il vérifie navigation, sauvegarde après rechargement,
effets, schémas, largeur mobile, réduction des animations et absence de réseau externe.
