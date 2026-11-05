#!/usr/bin/env bash
# construire-apk.sh — Compiler CodexLocal vite, dans le terminal LexOS (Linux).
#
# Utilisation :
#   ./construire-apk.sh              APK CodexLocal, mode rapide
#   ./construire-apk.sh perso        APK TVA LexOS (appli perso)
#   ./construire-apk.sh tout         les deux APK
#   ./construire-apk.sh complet      comme GitHub : toutes les variantes CPU + tests
#   ./construire-apk.sh ... -i       installe aussi sur le téléphone branché (adb)
#   ./construire-apk.sh preparer     installe seulement ce qui manque (SDK, NDK, llama.cpp)
#   ./construire-apk.sh nettoyer     efface les compilations (si quelque chose va mal)
#
# Pourquoi c'est rapide :
#   - le « démon » Gradle reste allumé entre deux compilations (pas de redémarrage de Java) ;
#   - llama.cpp n'est compilé qu'en UNE variante CPU (-Prapide) au lieu de toutes ;
#   - les tests sont sautés en mode rapide ;
#   - le cache Gradle et la compilation en parallèle sont actifs ;
#   - seul ce qui a changé est recompilé : la 2e compilation prend souvent moins d'une minute.

set -euo pipefail
cd "$(dirname "$0")"

ORANGE=$'\e[38;5;208m'; VERT=$'\e[32m'; ROUGE=$'\e[31m'; GRIS=$'\e[90m'; FIN=$'\e[0m'
info()   { echo "${ORANGE}▸${FIN} $*"; }
ok()     { echo "${VERT}✔${FIN} $*"; }
erreur() { echo "${ROUGE}✘ $*${FIN}" >&2; exit 1; }

NDK_VERSION="29.0.13113456"
CMAKE_VERSION="3.31.6"
LLAMA_TAG="v0.6.0"

# ---------- Arguments ----------
MODE="rapide"; CIBLE="codexlocal"; INSTALLER=0
for arg in "$@"; do
  case "$arg" in
    perso)     CIBLE="perso" ;;
    tout)      CIBLE="tout" ;;
    complet)   MODE="complet" ;;
    -i|--installer) INSTALLER=1 ;;
    preparer)  MODE="preparer" ;;
    nettoyer)  MODE="nettoyer" ;;
    -h|--help|aide) sed -n '2,18p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) erreur "Option inconnue : $arg (voir ./construire-apk.sh aide)" ;;
  esac
done

# ---------- Java 17 ----------
trouver_java17() {
  if [ -n "${JAVA_HOME:-}" ] && "$JAVA_HOME/bin/java" -version 2>&1 | grep -q '"17'; then
    echo "$JAVA_HOME"; return
  fi
  for d in /usr/lib/jvm/java-17-openjdk-amd64 /usr/lib/jvm/java-17-openjdk /usr/lib/jvm/temurin-17-jdk-amd64 /usr/lib/jvm/jdk-17* "$HOME/AndroidDev/jdk17"; do
    [ -x "$d/bin/java" ] && { echo "$d"; return; }
  done
}
JAVA17="$(trouver_java17 || true)"
if [ -z "$JAVA17" ]; then
  erreur "Java 17 introuvable. Installe-le avec :  sudo apt install openjdk-17-jdk"
fi
export JAVA_HOME="$JAVA17"
export PATH="$JAVA_HOME/bin:$PATH"

# ---------- SDK Android ----------
for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Android/Sdk" "$HOME/AndroidDev/sdk" /opt/android-sdk; do
  if [ -n "$d" ] && [ -d "$d" ]; then export ANDROID_HOME="$d"; break; fi
done
if [ -z "${ANDROID_HOME:-}" ]; then
  erreur "SDK Android introuvable. Lance d'abord :  ./construire-apk.sh preparer"
fi
export ANDROID_SDK_ROOT="$ANDROID_HOME"
SDKMANAGER="$(find "$ANDROID_HOME" -type f -name sdkmanager -path '*bin*' 2>/dev/null | sort | tail -n1 || true)"

preparer() {
  info "Vérification des outils Android…"
  if [ -z "$SDKMANAGER" ]; then
    erreur "sdkmanager absent. Installe les « Command-line tools » dans $ANDROID_HOME/cmdline-tools/latest
  (https://developer.android.com/studio#command-tools), puis relance."
  fi
  local manque=()
  [ -d "$ANDROID_HOME/platforms/android-36" ]          || manque+=("platforms;android-36")
  [ -d "$ANDROID_HOME/build-tools/36.0.0" ]            || manque+=("build-tools;36.0.0")
  [ -d "$ANDROID_HOME/ndk/$NDK_VERSION" ]              || manque+=("ndk;$NDK_VERSION")
  [ -d "$ANDROID_HOME/cmake/$CMAKE_VERSION" ]          || manque+=("cmake;$CMAKE_VERSION")
  [ -d "$ANDROID_HOME/platform-tools" ]                || manque+=("platform-tools")
  if [ ${#manque[@]} -gt 0 ]; then
    info "Installation : ${manque[*]}"
    yes | "$SDKMANAGER" --licenses >/dev/null 2>&1 || true
    "$SDKMANAGER" "${manque[@]}"
  fi
  if [ ! -f lib/src/main/cpp/llama.cpp/CMakeLists.txt ]; then
    info "Récupération de llama.cpp $LLAMA_TAG (une seule fois)…"
    git clone --depth 1 --branch "$LLAMA_TAG" https://github.com/ggml-org/llama.cpp.git lib/src/main/cpp/llama.cpp
  fi
  ok "Outils prêts"
}

# Le gradle.properties du projet pointe vers Java sur l'ancien PC Windows.
# On le remplace pour cette compilation, sans modifier le fichier.
GRADLE=(./gradlew -Dorg.gradle.java.home="$JAVA_HOME" --parallel --build-cache)

if [ "$MODE" = "nettoyer" ]; then
  info "Nettoyage…"
  "${GRADLE[@]}" clean || true
  rm -rf lib/.cxx app/build lib/build shell/build
  ok "Propre. La prochaine compilation repartira de zéro."
  exit 0
fi

preparer
[ "$MODE" = "preparer" ] && exit 0
chmod +x gradlew

if [ ! -f app/codexlocal.keystore ]; then
  echo "${GRIS}Note : app/codexlocal.keystore absent → APK signé avec une clé de test."
  echo "      Il ne pourra pas mettre à jour CodexLocal déjà installé (désinstalle d'abord).${FIN}"
fi

# ---------- Compilation ----------
OPTIONS=()
TACHES=(:app:assembleDebug)
if [ "$MODE" = "rapide" ]; then
  OPTIONS+=(-Prapide)
else
  TACHES=(:app:testDebugUnitTest :app:assembleDebug)
fi

mkdir -p sorties
APK_SORTIE="app/build/outputs/apk/debug"
debut=$(date +%s)

CONSTRUITS=()
compiler() {   # $1 = nom du fichier final, $2… = options en plus
  local nom="$1"; shift
  info "Compilation de $nom ($MODE)…"
  "${GRADLE[@]}" "${TACHES[@]}" "${OPTIONS[@]}" "$@"
  cp "$(ls -t "$APK_SORTIE"/*.apk | head -n1)" "sorties/$nom"
  CONSTRUITS+=("sorties/$nom")
  ok "sorties/$nom  ($(du -h "sorties/$nom" | cut -f1))"
}

case "$CIBLE" in
  codexlocal) compiler CodexLocal.apk ;;
  perso)      compiler TVA-LexOS.apk -Pperso ;;
  tout)       compiler CodexLocal.apk; compiler TVA-LexOS.apk -Pperso ;;
esac

ok "Terminé en $(( $(date +%s) - debut )) s"

# ---------- Installation sur le téléphone ----------
if [ "$INSTALLER" = "1" ]; then
  ADB="$ANDROID_HOME/platform-tools/adb"
  command -v adb >/dev/null 2>&1 && ADB="adb"
  if ! "$ADB" get-state >/dev/null 2>&1; then
    erreur "Aucun téléphone détecté. Branche-le en USB et active le débogage USB."
  fi
  for apk in "${CONSTRUITS[@]}"; do
    info "Installation de $apk…"
    "$ADB" install -r -d "$apk" && ok "Installé"
  done
fi
