# Turbo REA Mobile — plan d'intégration

Statut : préparation, aucune fonctionnalité REA annoncée comme opérationnelle.
Branche : feature/rea-mobile. Ne pas fusionner dans main avant validation.

## Objectif
Transformer les réponses GGUF de CodexLocal en travail de programmation productif, supervisé et mesurable sur Android, sans perdre les projets, modèles ni conversations existants.

## Étapes
1. Cartographier le flux local llama.cpp, les projets Android SAF et les actions de création de fichiers.
2. Introduire un orchestrateur de tâches (planifier → lire contexte → proposer patch → valider → appliquer → tester → résumer) indépendant du fournisseur de modèle.
3. Ajouter une interface Turbo affichant plan, progression, diff, journaux et arrêt immédiat.
4. Définir des outils à permissions minimales : lecture projet, édition par patch avec aperçu, tests en bac à sable lorsqu'un environnement d'exécution est disponible. Pas d'exécution shell arbitraire.
5. Stocker la mémoire de projet localement, avec effacement et export explicites; ne pas indexer les modèles GGUF ni les secrets.
6. Tester les annulations, les chemins interdits, la reprise après interruption, le hors-ligne, la pression mémoire et la compatibilité des données.
7. Compiler une APK de test signée correctement, vérifier l'identifiant de paquet, le versionCode et la compatibilité de signature avant toute mise à jour.

## Critères d'acceptation
- Un modèle GGUF peut proposer une modification réelle, inspectable et applicable à un projet de test.
- Les modifications nécessitant écriture ont une confirmation et un retour arrière lorsque possible.
- Les modèles privés et conversations restent intacts.
- Le mode Chat/Codex existant continue de fonctionner.
- Tests automatisés et APK de test disponibles avant proposition de fusion.

## Risque de signature
La configuration Gradle contient des identifiants de signature codés en dur. Ne pas réutiliser une clé exposée pour une distribution de confiance; prévoir un secret CI protégé et un plan de migration de signature sans prétendre pouvoir mettre à jour une installation signée avec une autre clé.
