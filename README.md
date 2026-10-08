# CodexLocal

Assistant de programmation Android qui fonctionne **hors-ligne** avec un modèle
local (GGUF via llama.cpp), et qui peut aussi utiliser **Claude, OpenAI,
Gemini, Groq, OpenRouter, DeepSeek, Ollama…** quand il y a du réseau.

## Ce que fait l'appli

| Mode | Réseau requis | Clé API | Où tourne le modèle |
|---|---|---|---|
| **Local (GGUF)** | ❌ non | ❌ non | sur le téléphone (llama.cpp) |
| API compatible OpenAI | ✅ oui | ✅ oui | serveur distant |
| Claude (Anthropic) | ✅ oui | ✅ oui | serveur distant |
| Google Gemini | ✅ oui | ✅ oui | serveur distant |

Le mode local est le mode « chalet sans réseau » : une fois le fichier `.gguf`
copié sur le téléphone, l'appli n'a plus besoin d'internet du tout.

## Modes d'interface : Codex et Chat

Dans l'ecran principal, le selecteur **Codex | Chat** permet de changer de mode.

- **Codex** : interface noir et orange, bouton d'envoi `</>`, consignes orientees programmation et corrections de code.
- **Chat** : interface noir et bleu ciel, bulles de discussion bleues, bouton d'envoi classique, consignes de conversation generale.
- Le modele choisi (GGUF local ou fournisseur API) reste le meme.
- Le mode choisi est memorise. Lors du changement de mode, une nouvelle session est creee si la conversation en cours contient des messages ; les anciens messages restent dans l'historique du volet lateral.
- Pendant la generation, le bouton devient un bouton **Arreter** dans la couleur du mode actif.

Ces changements concernent les sources Android ; il faut compiler et installer un nouvel APK pour en profiter.

## CodexLocal PC (terminal Python, Windows et Linux)

Cette version utilise les **agents CLI deja installes** sur l'ordinateur.
Elle ne les telecharge pas et ne partage pas automatiquement les conversations
avec l'APK Android.

### Windows (PowerShell)

```powershell
git clone https://github.com/alexmarceauprevost812-source/CodexLocal.git
cd CodexLocal
python pc/codexlocal.py --list-agents
python pc/codexlocal.py
```

### Kali Linux / Ubuntu

```bash
git clone https://github.com/alexmarceauprevost812-source/CodexLocal.git
cd CodexLocal
python3 pc/codexlocal.py --list-agents
python3 pc/codexlocal.py
```

Commandes : `/agents`, `/use ollama`, `/use codex`,
`/use claude`, `/use gemini`, `/mode chat`, `/mode codex`,
`/model qwen2.5-coder:7b`, `/help`, `/quit`.

Pour une question unique :
```bash
python3 pc/codexlocal.py --agent ollama --model qwen2.5-coder:7b --once "Explique ce script Python"
```

- **Ollama** : le modele doit etre deja installe ; utilisation locale apres installation.
- **Codex CLI** : le lancement utilise une sandbox de lecture seule par defaut.
- **Claude Code** : le lancement utilise le mode plan par defaut.
- **Gemini CLI** : utilise la configuration et les autorisations propres a Gemini.
- Ces agents peuvent demander connexion, compte ou abonnement selon la configuration.
- Les dossiers et fichiers ne sont jamais partages automatiquement avec l'APK.

### APK Android (compilation sur GitHub)

Le workflow [CodexLocal Android APK + PC checks](.github/workflows/android-apk.yml)
compile l'application Android en **APK debug** pour processeurs arm64.
Dans GitHub → **Actions**, ouvrir le dernier lancement reussi, puis
telecharger l'artefact `CodexLocal-Android-Debug-APK` et extraire
`app-debug.apk`. L'APK est signe avec la cle de test Android (debug),
ce n'est pas une version destinee a la publication sur Play Store.

Une compilation reussie ne garantit pas que le moteur GGUF puisse charger
un gros modele sur chaque telephone ; tester sur l'appareil cible.

## Architecture

```
CodexLocal/
├── app/                       UI, réglages, fournisseurs distants
│   └── src/main/java/com/codexlocal/
│       ├── MainActivity.kt        écran de conversation
│       ├── SettingsActivity.kt    réglages (moteur, modèle, clés API)
│       ├── Config.kt              préférences + configuration
│       ├── ChatRepository.kt      choisit le fournisseur
│       ├── LocalEngine.kt         enveloppe du moteur llama.cpp
│       ├── ModelStore.kt          import / téléchargement des .gguf
│       ├── MarkdownLite.kt        coloration des blocs de code
│       └── providers/             OpenAI-compatible, Anthropic, Gemini
└── lib/                       moteur natif (JNI + llama.cpp vendorisé)
    └── src/main/cpp/
        ├── CMakeLists.txt         compile llama.cpp en .so
        ├── ai_chat.cpp            pont JNI (chargement, génération, température)
        └── llama.cpp/             sources llama.cpp v0.6.0
```

Points techniques importants :

* `android:extractNativeLibs="true"` est **obligatoire** : les backends GGML
  (`.so` séparés, dispatch CPU à l'exécution) sont chargés dynamiquement depuis
  `nativeLibraryDir` par `ggml_backend_load_all_from_path()`.
* Compilation avec `GGML_BACKEND_DL=ON` + `GGML_CPU_ALL_VARIANTS=ON` : llama.cpp
  génère plusieurs variantes du backend CPU (armv8-a, dotprod, i8mm…) et choisit
  la meilleure au démarrage.
* `GGML_CPU_KLEIDIAI=ON` : noyaux ARM optimisés (gros gain sur Snapdragon).
* Le moteur garde l'historique dans son cache KV : l'appli ne lui envoie que le
  dernier message. « Nouvelle conversation » appelle `resetConversation()`
  (vidage du cache KV) sans recharger le modèle.

## Compiler

Prérequis : JDK 17, Android SDK (platform 36, build-tools 36.0.0),
NDK 29.0.13113456, CMake 3.31.6.

### 1. Récupérer les sources de llama.cpp

Elles ne sont pas versionnées dans ce dépôt (169 Mo). Un script s'en charge :

```powershell
.\recuperer-llama-cpp.ps1
```

Ou à la main :

```powershell
git clone --depth 1 --branch v0.6.0 https://github.com/ggml-org/llama.cpp.git lib/src/main/cpp/llama.cpp
```

### 2. Compiler

```powershell
$env:JAVA_HOME = 'C:\AndroidDev\jdk17'
$env:ANDROID_HOME = 'C:\AndroidDev\sdk'
cd C:\AndroidDev\CodexLocal
C:\AndroidDev\gradle-8.14.3\bin\gradle.bat assembleDebug    # APK de test
C:\AndroidDev\gradle-8.14.3\bin\gradle.bat assembleRelease  # APK signé
```

Sorties :

* `app/build/outputs/apk/debug/app-debug.apk`
* `app/build/outputs/apk/release/app-release.apk` (signé avec `app/codexlocal.keystore`,
  mot de passe `codexlocal`)

## Modèles conseillés (Samsung S25 Ultra, 12 Go de RAM)

| Fichier | Taille | Vitesse | Qualité code |
|---|---|---|---|
| `qwen2.5-coder-1.5b-instruct-q4_k_m.gguf` | ~1,0 Go | très rapide | correcte |
| `qwen2.5-coder-3b-instruct-q4_k_m.gguf` | ~2,0 Go | rapide | **recommandé** |
| `qwen2.5-coder-7b-instruct-q4_k_m.gguf` | ~4,7 Go | lente | élevée |
| `qwen2.5-coder-7b-instruct-q6_k.gguf` | ~6,25 Go | très lente | **qualité maximale du catalogue pour le code** |

Tous sous licence Apache-2.0, téléchargeables sans compte :
<https://huggingface.co/Qwen/Qwen2.5-Coder-3B-Instruct-GGUF>

## Mode code haute qualité (7B Q6_K)

Pour privilégier la précision du code sur la vitesse, le catalogue propose maintenant
**Qwen2.5-Coder 7B Instruct Q6_K** en premier (environ **6,25 Go**).

1. Mettre à jour CodexLocal vers une version de l'APK compilée avec ce catalogue.
2. Ouvrir **Réglages → Local (hors-ligne)**.
3. Choisir **Qwen2.5-Coder 7B Q6_K — qualité MAX (plus lent)**.
4. Appuyer sur **Télécharger** : l'application télécharge le fichier GGUF puis tente de le charger.

Sur une version plus ancienne de l'APK, choisir **Autre adresse (URL personnalisée)**
et coller cette adresse officielle :

`https://huggingface.co/Qwen/Qwen2.5-Coder-7B-Instruct-GGUF/resolve/main/qwen2.5-coder-7b-instruct-q6_k.gguf`

Prévoir au moins 8 Go d'espace disponible et environ 12 Go de RAM sur le téléphone,
sans garantie de fonctionnement selon la mémoire disponible et les applis ouvertes.
Le téléchargement nécessite du réseau une seule fois ; l'inférence est ensuite locale.
Si le chargement échoue, essayer la version **7B Q4_K_M** (4,7 Go) ou **3B Q4_K_M**.

## Installer le modèle sur le téléphone

Deux méthodes, depuis **Réglages → Modèle local** :

1. **Import** : bouton « Importer un .gguf » (le fichier doit être sur le téléphone).
2. **Téléchargement** : coller l'URL directe du `.gguf`, bouton « Télécharger ».

Ou par USB, sans passer par l'appli : copier le `.gguf` dans
`/sdcard/Android/data/com.codexlocal/files/models/`
puis « Rafraîchir » dans les réglages.

## Clés API (mode distant)

| Fournisseur | URL de base | Exemple de modèle | Palier gratuit |
|---|---|---|---|
| Groq | `https://api.groq.com/openai/v1` | `llama-3.3-70b-versatile` | oui |
| OpenRouter | `https://openrouter.ai/api/v1` | `deepseek/deepseek-chat-v3.1:free` | oui (`:free`) |
| DeepSeek | `https://api.deepseek.com/v1` | `deepseek-chat` | non |
| OpenAI | `https://api.openai.com/v1` | `gpt-4o-mini` | non |
| Anthropic | `https://api.anthropic.com/v1` | `claude-sonnet-4-5` | non |
| Gemini | `https://generativelanguage.googleapis.com/v1beta` | `gemini-2.5-flash` | oui |
| Ollama (PC du réseau local) | `http://192.168.x.x:11434/v1` | `qwen2.5-coder:7b` | oui (local) |

Les clés sont stockées dans les `SharedPreferences` privées de l'appli
(dossier interne, non lisible par les autres applis). Elles ne partent que vers
le service que tu as configuré.
