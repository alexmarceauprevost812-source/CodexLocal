# Ti-Labo — mon ti-univers

Laboratoire de dev local : bureau, Jasette, Codex, Studio, IA locales, GitHub, Mémoire.
Mode Plan avant de coder, accès Internet pour l'IA (au besoin), vision (images).

Cette branche contient **uniquement** le Ti-Labo. Le code de l'appli CodexLocal reste sur `main`.

## Téléphone (Android + Termux)

```bash
pkg install -y git python
termux-setup-storage
git clone -b ti-labo --single-branch https://github.com/alexmarceauprevost812-source/CodexLocal ~/TiLabo
bash ~/TiLabo/demarrer-telephone.sh
```

Dépôt privé : nom d'utilisateur GitHub + **jeton** comme mot de passe.
Mise à jour : `cd ~/TiLabo && git pull`. Détails : [LISEZMOI-TELEPHONE.txt](LISEZMOI-TELEPHONE.txt).

- Utilise directement les `.gguf` de CodexLocal (llama-server) et l'Ollama de Termux.
- Déplace les IA du téléphone vers la clé USB-C (et les ramène), avec vérification avant effacement.
- Appli Android : **Ti-Labo.apk** (onglet Releases, « Ti-Labo … (Android) »), avec son propre moteur gguf (llama.cpp) — sources dans `android/`, compilée par GitHub (Releases).

## Clé USB-C (Windows)

Copie ce dossier sur la clé, lance `Installer-Ti-Labo.bat` une fois, puis `Demarrer-Ti-Labo.bat`.
Détails : [LISEZMOI.txt](LISEZMOI.txt).

## Édition Famille (« Mon assistante ») — dossier `famille/`

L'assistante simple pour tout le monde, sur une clé USB-C et un PC Windows ordinaire :
un seul fichier **▶️ START**, un mini-bureau façon Windows, l'IA qui s'installe depuis l'écran
avec le choix des modèles que le PC peut faire rouler (« Ton PC n'a pas assez de mémoire (RAM)
pour ce modèle » pour les trop gros), courriels, lettres Word, tableaux Excel, affiches, vidéo MP4,
agenda, mini-cerveau, mot de passe. Le prénom est demandé au premier démarrage.

Préparer une clé : copier le contenu de `famille/` à la racine de la clé, puis double-cliquer
« ▶️ START ». Les réglages de départ (prénom, entreprise, fête…) sont dans `famille/app/interface/config.js`.
