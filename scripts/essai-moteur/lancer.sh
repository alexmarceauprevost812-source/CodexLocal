#!/bin/sh
# Essai du moteur natif (lib/src/main/cpp/ai_chat.cpp) sur un poste Linux, sans telephone ni vrai modele.
#
#   LLAMA_CPP=/chemin/vers/llama.cpp scripts/essai-moteur/lancer.sh
#
# Le dossier llama.cpp doit etre la meme version que celle du workflow (voir .github/workflows/android-apk.yml).
# Premiere fois : la bibliotheque est compilee dans $LLAMA_CPP/build (quelques minutes).
# Option --sans-essai : compile seulement, ne lance pas les series.
set -e
ICI=$(cd "$(dirname "$0")" && pwd)
RACINE=$(cd "$ICI/../.." && pwd)
: "${LLAMA_CPP:?indique le dossier de llama.cpp dans LLAMA_CPP}"
SORTIE=${SORTIE:-/tmp/essai-moteur}
JNI=${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}
mkdir -p "$SORTIE"

if [ ! -f "$LLAMA_CPP/build/bin/libllama.so" ]; then
  cmake -S "$LLAMA_CPP" -B "$LLAMA_CPP/build" -DCMAKE_BUILD_TYPE=Release -DLLAMA_BUILD_TESTS=OFF \
    -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_CURL=OFF \
    -DGGML_OPENMP=OFF -DLLAMA_OPENSSL=OFF
  cmake --build "$LLAMA_CPP/build" --target llama llama-common -j "$(nproc)"
fi

LLAMA_CPP="$LLAMA_CPP" python3 "$ICI/creer_modele.py" "$SORTIE/mini-brut.gguf"
LLAMA_CPP="$LLAMA_CPP" python3 "$ICI/creer_modele.py" "$SORTIE/mini-chatml.gguf" chatml

g++ -std=c++17 -O1 -Wno-deprecated-declarations -I"$ICI/stub" -I"$RACINE/lib/src/main/cpp" \
  -I"$JNI/include" -I"$JNI/include/linux" \
  -I"$LLAMA_CPP" -I"$LLAMA_CPP/common" -I"$LLAMA_CPP/include" -I"$LLAMA_CPP/ggml/include" \
  -I"$LLAMA_CPP/ggml/src" -I"$LLAMA_CPP/vendor" "$ICI/essai_moteur.cpp" -o "$SORTIE/essai_moteur" \
  -L"$LLAMA_CPP/build/bin" -lllama-common -lllama -lggml -lggml-base -lggml-cpu \
  -Wl,-rpath,"$LLAMA_CPP/build/bin" -lpthread

[ "$1" = "--sans-essai" ] && exit 0

# Quatre series : sans / avec gabarit de discussion, avec / sans decalage possible de la memoire.
code=0
for v in "brut:raw:shift" "brut:raw:noshift" "chatml:chatml:shift" "chatml:chatml:noshift"; do
  modele=${v%%:*}; reste=${v#*:}; gabarit=${reste%%:*}; decalage=${reste#*:}
  echo "### $modele / $decalage"
  "$SORTIE/essai_moteur" "$SORTIE/mini-$modele.gguf" "$gabarit" "$decalage" 2>/dev/null || code=1
done
exit $code
