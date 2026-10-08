# CodexLocal — guide d'utilisation

Petite appli Android d'assistant au code. Elle marche **sans internet** avec une
IA locale installée sur le téléphone, et peut aussi utiliser **Claude, OpenAI,
Gemini, Groq, OpenRouter…** quand il y a du réseau.

---

## 0. Le plus rapide : tout par USB en une fois

1. Branche le S25 Ultra en USB et active le **débogage USB** :
   Paramètres → À propos du téléphone → Informations sur le logiciel →
   appuie **7 fois** sur « Numéro de build », puis
   Options de développeur → **Débogage USB**.
2. Accepte la demande « Autoriser le débogage USB » qui s'affiche sur le téléphone.
3. Clic droit sur **`installer-par-usb.ps1`** → *Exécuter avec PowerShell*.
   Le script installe l'APK, puis copie le modèle que tu choisis dans le bon
   dossier du téléphone.
4. Sur le téléphone : CodexLocal → Réglages → **Rafraîchir** → choisis le
   modèle dans la liste → **Charger**.

Les modèles sont **déjà téléchargés** sur ce PC :

| Modèle | Chemin sur le PC |
|---|---|
| Qwen2.5-Coder 3B — recommandé | `C:\AndroidDev\models\qwen2.5-coder-3b-instruct-q4_k_m.gguf` |
| Qwen2.5-Coder 1.5B — rapide | `C:\AndroidDev\models\qwen2.5-coder-1.5b-instruct-q4_k_m.gguf` |

---

## 1. Installer l'APK à la main

1. Copie `CodexLocal-debug.apk` (ou `CodexLocal-release.apk`) sur le téléphone
   (câble USB, Google Drive, ou l'appli « Mes fichiers »).
2. Sur le téléphone, ouvre le fichier.
3. Android va demander d'autoriser les sources inconnues :
   **Paramètres → Applis → Accès spéciaux → Installer des applis inconnues →
   (ton gestionnaire de fichiers) → Autoriser**.
4. Installe, puis ouvre **CodexLocal**.

> L'APK est signé avec une clé personnelle (mot de passe `codexlocal`). Ça suffit
> pour une installation manuelle ; Play Store demanderait une autre clé.

---

## 2. Mettre une IA sur le téléphone (une seule fois, avec du réseau)

Au premier lancement, l'appli ouvre directement les **Réglages**.

### Méthode A — télécharger depuis le téléphone (le plus simple)

1. Réglages → **Modèle local** → champ URL → colle :

   ```
   https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF/resolve/main/qwen2.5-coder-3b-instruct-q4_k_m.gguf
   ```

2. Appuie sur **Télécharger** (≈ 2 Go, à faire en Wi-Fi).
3. Appuie sur **Charger**. Patiente 1 à 2 minutes.
4. Le statut affiche « Modèle local chargé : … ». C'est prêt.

### Méthode B — copier le fichier par USB (sans télécharger sur le téléphone)

1. Sur le PC, récupère un fichier `.gguf` (voir §4).
2. Branche le téléphone, autorise le transfert de fichiers.
3. Copie le `.gguf` dans :

   ```
   Ce PC\SAMSUNG-S25-ULTRA\Stockage interne\Android\data\com.codexlocal\files\models\
   ```

   (si le dossier `models` n'existe pas, ouvre d'abord l'appli une fois)
4. Dans l'appli : Réglages → **Rafraîchir**, choisis le modèle dans la liste,
   puis **Charger**.

### Méthode C — importer un fichier déjà sur le téléphone

Bouton **Importer un .gguf**, choisis le fichier. Il est copié dans le dossier
de l'appli (compte quelques minutes pour 2 Go).

---

## 3. Utiliser l'appli

* Tape ta question en bas, appuie sur ➤.
* Pendant la génération, ➤ devient ■ : appuie pour **arrêter**.
* **Copier** sous une réponse met tout le texte (code compris) dans le
  presse-papiers.
* Menu **Nouvelle conversation** : efface l'historique (le modèle reste chargé,
  c'est instantané).
* Menu **Réglages** : changer de moteur, de modèle, de clé API.

Les blocs de code sont affichés en vert sur fond sombre, en police monospace.

---

## 4. Quels modèles choisir

| Fichier | Taille | Vitesse sur S25 Ultra | Qualité en code |
|---|---|---|---|
| `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf` | ~1,0 Go | très rapide (15-25 mots/s) | correcte |
| `qwen2.5-coder-3b-instruct-q4_k_m.gguf` | ~2,0 Go | rapide (8-15 mots/s) | **recommandé** |
| `qwen2.5-coder-7b-instruct-q4_k_m.gguf` | ~4,5 Go | lente (3-6 mots/s) | la meilleure |

Licence Apache-2.0, aucun compte requis :
<https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF>

Tu peux garder plusieurs modèles dans le dossier et changer quand tu veux.

---

## 5. Ajouter Claude, OpenAI, Gemini… (quand il y a du réseau)

Réglages → **Moteur d'IA** → choisis le service, remplis URL de base, modèle et
clé API, puis **Enregistrer**.

| Service | URL de base | Exemple de modèle | Clé à créer sur |
|---|---|---|---|
| OpenRouter (modèles gratuits) | `https://openrouter.ai/api/v1` | `deepseek/deepseek-chat-v3.1:free` | openrouter.ai/keys |
| Groq (palier gratuit) | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` | console.groq.com/keys |
| Google Gemini (palier gratuit) | `https://generativelanguage.googleapis.com/v1beta` | `gemini-2.5-flash` | aistudio.google.com |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | platform.deepseek.com |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | platform.openai.com |
| Claude (Anthropic) | `https://api.anthropic.com/v1` | `claude-sonnet-4-5` | console.anthropic.com |
| Ollama sur ton PC | `http://192.168.1.xx:11434/v1` | `qwen2.5-coder:7b` | aucune |

Chaque service garde **sa propre** clé / URL / modèle : tu peux passer de l'un à
l'autre sans tout retaper. Les clés restent dans le stockage privé de l'appli.

---

## 6. Ce qu'il faut savoir

* **Hors-ligne** : seul le mode *Local* fonctionne sans réseau. Les autres modes
  échouent avec « HTTP … » ou « Unable to resolve host ».
* **Mémoire** : un modèle 3B Q4 a besoin d'environ 2,5 Go de RAM libre. Le
  S25 Ultra (12 Go) est très à l'aise ; évite juste de garder 30 applis ouvertes.
* **Vitesse** : tout se fait sur le processeur (pas de GPU pour l'instant).
  Le premier chargement d'un modèle prend 30 s à 2 min.
* **Chauffe** : une longue génération fait chauffer le téléphone, c'est normal.
* **Contexte** : 8192 tokens (environ 6000 mots d'historique). Au-delà, le
  moteur oublie le début de la conversation.
* **Température** : 0,2-0,4 pour du code (précis), 0,7-1,0 pour des idées.

---

## 7. Apparence

Thème **noir AMOLED** (fond `#000000`, pas de gris) avec accents **orange** :

* les **boutons** sont orange : pastille ronde orange pour envoyer (elle devient
  un carré ■ orange pendant la génération), et texte orange pour Copier,
  Charger, Importer, Télécharger, Rafraîchir, Annuler ;
* **Enregistrer** est un bouton plein orange ;
* les titres de sections des Réglages sont orange ;
* les bulles : la tienne a un liseré orange sur fond très sombre, celle de
  l'IA un liseré gris.

**Le code est coloré** automatiquement (langage déduit de l'étiquette du bloc,
```kotlin, ```python…) :

| Élément | Couleur |
|---|---|
| Mots-clés (`fun`, `def`, `if`, `SELECT`…) | orange |
| Chaînes de caractères | vert |
| Nombres | jaune orangé |
| Commentaires (`//`, `#`, `--`, `<!-- -->`) | gris, italique |
| Noms de fonctions | bleu |
| Types / classes (`MaClasse`) | cyan |
| Annotations (`@Override`) | violet |
| Balises XML / HTML | orange |

Les expressions régulières de coloration ont été testées sur Kotlin, Python,
XML et SQL avant livraison.

