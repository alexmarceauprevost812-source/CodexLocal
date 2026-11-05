#!/usr/bin/env bash
# envoyer-ia.sh — Envoyer sur le téléphone les IA locales déjà présentes sur l'ordi ou la clé USB.
#
# Cherche :
#   - les fichiers .gguf dans ton dossier personnel et sur les clés USB branchées ;
#   - les modèles Ollama (ollama list).
# Puis te laisse choisir, et les copie dans CodexLocal et/ou TVA LexOS par USB (adb).
#
# Utilisation :  ./envoyer-ia.sh

set -uo pipefail

ORANGE=$'\e[38;5;208m'; VERT=$'\e[32m'; ROUGE=$'\e[31m'; GRIS=$'\e[90m'; GRAS=$'\e[1m'; FIN=$'\e[0m'
info()   { echo "${ORANGE}▸${FIN} $*"; }
ok()     { echo "${VERT}✔${FIN} $*"; }
avert()  { echo "${ORANGE}!${FIN} $*"; }
erreur() { echo "${ROUGE}✘ $*${FIN}" >&2; exit 1; }
taille() { numfmt --to=iec --suffix=o "$1" 2>/dev/null || echo "$1 octets"; }

# ---------- Téléphone ----------
ADB="$HOME/Android/Sdk/platform-tools/adb"
command -v adb >/dev/null 2>&1 && ADB="adb"
[ -x "$ADB" ] || command -v "$ADB" >/dev/null 2>&1 || erreur "adb introuvable. Lance d'abord ./construire-apk.sh preparer"
"$ADB" get-state >/dev/null 2>&1 || erreur "Aucun téléphone détecté. Branche-le en USB et active le débogage USB."
ok "Téléphone détecté"

# ---------- Applis installées ----------
declare -A NOMS=([com.codexlocal.prive]="CodexLocal (prive)" [com.codexlocal]="CodexLocal" [com.codexlocal.perso]="TVA LexOS")
APPS=()
INSTALLEES="$("$ADB" shell pm list packages --user 0 2>/dev/null)"
for pkg in com.codexlocal.prive com.codexlocal com.codexlocal.perso; do
  echo "$INSTALLEES" | grep -qx "package:$pkg" && APPS+=("$pkg")
done
[ ${#APPS[@]} -gt 0 ] || erreur "Aucune appli CodexLocal ni TVA LexOS sur le téléphone. Installe-la d'abord (./construire-apk.sh -i)."

# ---------- Recherche des IA sur l'ordi ----------
info "Recherche des IA sur l'ordi et les clés USB…"
CHEMINS=(); LIBELLES=(); NOMSFICHIER=()

while IFS= read -r -d '' f; do
  nom="$(basename "$f")"
  # Les « projecteurs » de vision ne sont pas des IA à charger seules.
  case "$nom" in *mmproj*|*.part) continue ;; esac
  CHEMINS+=("$f"); NOMSFICHIER+=("$nom")
  LIBELLES+=("$nom  ${GRIS}($(taille "$(stat -c %s "$f")") · $(dirname "$f"))${FIN}")
done < <(find "$HOME" /media /run/media /mnt -xdev -type f -iname '*.gguf' -size +50M -print0 2>/dev/null)

if command -v ollama >/dev/null 2>&1; then
  while read -r modele _; do
    [ -z "$modele" ] || [ "$modele" = "NAME" ] && continue
    blob="$(ollama show --modelfile "$modele" 2>/dev/null | awk '/^FROM \//{print $2; exit}')"
    [ -n "$blob" ] && [ -r "$blob" ] || continue
    CHEMINS+=("$blob"); NOMSFICHIER+=("${modele//[:\/]/-}.gguf")
    LIBELLES+=("$modele  ${GRIS}($(taille "$(stat -c %s "$blob")") · Ollama)${FIN}")
  done < <(ollama list 2>/dev/null)
fi

[ ${#CHEMINS[@]} -gt 0 ] || erreur "Aucune IA trouvée (.gguf ou Ollama). Branche ta clé USB, ou télécharge un modèle avec : ollama pull qwen2.5-coder:3b"

echo
echo "${GRAS}IA trouvées :${FIN}"
for i in "${!LIBELLES[@]}"; do printf "  %s%2d%s) %s\n" "$ORANGE" $((i+1)) "$FIN" "${LIBELLES[$i]}"; done
echo
read -rp "Numéros à envoyer (ex. : 1 3), ou « tout » : " CHOIX
[ "$CHOIX" = "tout" ] && CHOIX="$(seq -s ' ' 1 ${#CHEMINS[@]})"

SELECTION=()
for n in $CHOIX; do
  [[ "$n" =~ ^[0-9]+$ ]] && [ "$n" -ge 1 ] && [ "$n" -le ${#CHEMINS[@]} ] || erreur "Choix invalide : $n"
  SELECTION+=($((n-1)))
done
[ ${#SELECTION[@]} -gt 0 ] || erreur "Rien de choisi."

# ---------- Applis de destination ----------
echo
echo "${GRAS}Dans quelle appli ?${FIN}"
for i in "${!APPS[@]}"; do printf "  %s%d%s) %s\n" "$ORANGE" $((i+1)) "$FIN" "${NOMS[${APPS[$i]}]}"; done
printf "  %s%d%s) toutes\n" "$ORANGE" $(( ${#APPS[@]} + 1 )) "$FIN"
read -rp "Choix : " C
if [ "$C" = "$(( ${#APPS[@]} + 1 ))" ]; then DEST=("${APPS[@]}")
elif [[ "$C" =~ ^[0-9]+$ ]] && [ "$C" -ge 1 ] && [ "$C" -le ${#APPS[@]} ]; then DEST=("${APPS[$((C-1))]}")
else erreur "Choix invalide."; fi

# Le dossier doit avoir été créé par l'appli elle-même, sinon elle n'a pas le droit de lire dedans.
for pkg in "${DEST[@]}"; do
  if ! "$ADB" shell "[ -d /sdcard/Android/data/$pkg/files/models ]"; then
    erreur "Le dossier des modèles de ${NOMS[$pkg]} n'existe pas encore.
  Ouvre l'appli sur le téléphone → Réglages (ça crée le dossier), puis relance ./envoyer-ia.sh"
  fi
done

# ---------- Envoi ----------
LIBRE=$("$ADB" shell df -k /sdcard 2>/dev/null | awk 'NR==2{print $4*1024}')
for i in "${SELECTION[@]}"; do
  src="${CHEMINS[$i]}"; nom="${NOMSFICHIER[$i]}"; octets=$(stat -c %s "$src")
  [ "$octets" -gt 5000000000 ] && avert "$nom fait $(taille "$octets") : risque d'être lent ou trop gros pour la mémoire du téléphone."
  for pkg in "${DEST[@]}"; do
    cible="/sdcard/Android/data/$pkg/files/models/$nom"
    deja=$("$ADB" shell "stat -c %s '$cible' 2>/dev/null" | tr -d '\r')
    if [ "$deja" = "$octets" ]; then ok "$nom déjà dans ${NOMS[$pkg]}"; continue; fi
    if [ -n "${LIBRE:-}" ] && [ "$LIBRE" -lt "$octets" ]; then
      erreur "Plus assez de place sur le téléphone pour $nom ($(taille "$octets") requis, $(taille "$LIBRE") libres)."
    fi
    info "Envoi de $nom → ${NOMS[$pkg]} ($(taille "$octets"))…"
    "$ADB" push "$src" "$cible" | tail -n1
    fin=$("$ADB" shell "stat -c %s '$cible' 2>/dev/null" | tr -d '\r')
    if [ "$fin" = "$octets" ]; then ok "Copie complète"; LIBRE=$(( ${LIBRE:-0} - octets ))
    else avert "Copie incomplète ($fin / $octets octets) : relance le script."; fi
  done
done

echo
ok "Terminé. Sur le téléphone : Réglages → Rafraîchir → choisis le modèle → Charger."
