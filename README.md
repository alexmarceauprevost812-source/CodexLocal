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
- Appli Android : [`apk/Ti-Labo.apk`](apk/Ti-Labo.apk), avec son propre moteur gguf (llama.cpp) — sources dans `android/`, compilée par GitHub (Releases).

## Clé USB-C (Windows)

Copie ce dossier sur la clé, lance `Installer-Ti-Labo.bat` une fois, puis `Demarrer-Ti-Labo.bat`.
Détails : [LISEZMOI.txt](LISEZMOI.txt).
