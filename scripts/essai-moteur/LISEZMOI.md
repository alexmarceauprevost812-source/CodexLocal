# Essai du moteur natif sans téléphone

Le moteur d'IA (`lib/src/main/cpp/ai_chat.cpp`) gère la mémoire de la conversation : quand elle est pleine,
il oublie les plus anciens échanges pour que la session continue sans fin. Ce dossier permet de le vérifier
sur un ordinateur Linux, avec un mini modèle fabriqué au hasard (aucun vrai modèle nécessaire) :

```
LLAMA_CPP=/chemin/vers/llama.cpp scripts/essai-moteur/lancer.sh
```

`essai_moteur.cpp` rejoue des dizaines de messages de tailles variées (dont un message plus grand que toute
la mémoire) et, après chaque message et chaque réponse, vérifie que :

- la copie interne du cache (`kv_tokens`) correspond exactement aux positions du cache de llama.cpp ;
- le prompt système n'est jamais touché ;
- la fin du message de l'utilisateur est bien dans le cache, même quand il est coupé ;
- une réponse ne dépasse jamais le nombre de jetons demandé ;
- aucun message n'est refusé.

Les séries tournent avec et sans la possibilité de décaler le cache (pour simuler les modèles qui ne
savent pas le faire : le cache est alors reconstruit), avec et sans gabarit de discussion.
