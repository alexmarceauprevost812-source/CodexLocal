#!/usr/bin/env bash
# Ti-Labo sur le telephone (Android, dans Termux)
# Se branche sur l'Ollama deja installe dans Termux : les memes IA que ton Codex local.
#
# Usage, dans Termux :   bash ~/TiLabo/demarrer-telephone.sh
set -e
ICI="$(cd "$(dirname "$0")" && pwd)"
PORT_OLLAMA="${TILABO_OLLAMA_PORT:-11434}"

echo ""
echo "  TI-LABO - version telephone"
echo "  Dossier : $ICI"
echo ""

# Python et Git (une seule fois)
for p in python git; do
  if ! command -v "$p" >/dev/null 2>&1; then
    echo "== Installation de $p ..."
    pkg install -y "$p"
  fi
done
command -v termux-open-url >/dev/null 2>&1 || pkg install -y termux-tools >/dev/null 2>&1 || true
# llama-server : fait rouler directement les .gguf de CodexLocal (sans les copier)
command -v llama-server >/dev/null 2>&1 || pkg install -y llama-cpp >/dev/null 2>&1 || true
# Acces au stockage (IA de CodexLocal + cle USB-C)
[ -d "$HOME/storage" ] || echo "!! Lance une fois  termux-setup-storage  pour voir tes IA CodexLocal et ta cle USB-C"

# Ollama : on reutilise celui de ton Codex local s'il roule deja, sinon on le demarre
if curl -s -o /dev/null "http://127.0.0.1:$PORT_OLLAMA/api/version" 2>/dev/null; then
  echo "== Ollama roule deja (port $PORT_OLLAMA) - on prend les memes IA"
elif command -v ollama >/dev/null 2>&1; then
  echo "== Demarrage d'Ollama ..."
  OLLAMA_HOST="127.0.0.1:$PORT_OLLAMA" nohup ollama serve >"$ICI/donnees-ollama.log" 2>&1 &
  for _ in $(seq 1 30); do
    curl -s -o /dev/null "http://127.0.0.1:$PORT_OLLAMA/api/version" 2>/dev/null && break
    sleep 1
  done
else
  echo "== Pas d'Ollama : pas grave, tes .gguf de CodexLocal marchent avec llama-server"
fi

# Garde le telephone eveille pendant que le Ti-Labo roule (sinon Android coupe Termux)
command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock || true

export TILABO_OLLAMA="127.0.0.1:$PORT_OLLAMA"
echo ""
echo "  Ouvre Chrome sur :  http://127.0.0.1:8765"
echo "  (Ctrl+C dans Termux pour arreter)"
echo ""
python "$ICI/app/server.py" "$@"
command -v termux-wake-unlock >/dev/null 2>&1 && termux-wake-unlock || true
