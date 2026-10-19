#!/usr/bin/env bash
# Prepare la cle de signature de l'APK (app/codexlocal.keystore) pour la compilation GitHub.
#
# Pourquoi : Android n'accepte une mise a jour que si elle est signee avec LA MEME cle que la
# version deja installee. Sans cle fixe, chaque compilation en fabriquait une nouvelle et
# l'installation de la mise a jour echouait.
#
# Ordre de recherche :
#   1. le secret GitHub CODEXLOCAL_KEYSTORE_BASE64 (si le proprietaire en a mis un) ;
#   2. la cle gardee dans une « release brouillon » privee du depot (tag cle-de-signature),
#      visible seulement par les personnes qui ont acces en ecriture au depot ;
#   3. sinon, une cle est creee puis rangee dans cette release brouillon : toutes les
#      compilations suivantes la retrouvent.
#
# Variables : GH_TOKEN, GITHUB_REPOSITORY, CODEXLOCAL_KEYSTORE_BASE64 (facultative),
#             CLE_OBLIGATOIRE=1 pour echouer (au lieu d'avertir) quand aucune cle n'est disponible.
set -uo pipefail

CLE="app/codexlocal.keystore"
TAG="cle-de-signature"
NOM="codexlocal.keystore"

echec() {
  if [ "${CLE_OBLIGATOIRE:-0}" = "1" ]; then
    echo "::error title=Cle de signature::$1"
    exit 1
  fi
  echo "::warning title=Cle de signature::$1 Cet APK sera signe avec une cle de test : il ne pourra pas mettre a jour une installation existante."
  rm -f "$CLE"
  exit 0
}

# Identifiant de l'asset le plus ancien (celui qui fait foi si deux compilations l'ont cree en meme temps).
trouver_asset() {
  gh api "repos/${GITHUB_REPOSITORY}/releases?per_page=100" --paginate \
    --jq ".[] | select(.draft == true and .tag_name == \"${TAG}\") | .assets[] | select(.name == \"${NOM}\") | \"\(.created_at)\t\(.id)\"" \
    2>/dev/null | sort | head -n1 | cut -f2
}

telecharger_asset() {
  gh api -H "Accept: application/octet-stream" "repos/${GITHUB_REPOSITORY}/releases/assets/$1" > "$CLE.tmp" \
    && [ -s "$CLE.tmp" ] && mv "$CLE.tmp" "$CLE"
}

empreinte() {
  keytool -list -keystore "$CLE" -storepass codexlocal -alias codexlocal 2>/dev/null \
    | grep -i "SHA-\?256" | head -n1 | sed 's/^[^:]*: *//'
}

verifier_cle() {
  # La cle doit s'ouvrir avec le mot de passe et l'alias que build.gradle.kts attend.
  [ -n "$(empreinte)" ]
}

mkdir -p "$(dirname "$CLE")"
rm -f "$CLE" "$CLE.tmp"

# 1. secret GitHub
if [ -n "${CODEXLOCAL_KEYSTORE_BASE64:-}" ]; then
  printf '%s' "$CODEXLOCAL_KEYSTORE_BASE64" | base64 --decode > "$CLE" 2>/dev/null || true
  if verifier_cle; then
    echo "Cle de signature : secret GitHub CODEXLOCAL_KEYSTORE_BASE64 (SHA-256 $(empreinte))"
    exit 0
  fi
  echo "::warning title=Cle de signature::le secret CODEXLOCAL_KEYSTORE_BASE64 est illisible (mot de passe et alias attendus : codexlocal). Il est ignore."
  rm -f "$CLE"
fi

# 2. cle deja rangee dans la release brouillon
ID="$(trouver_asset)"
if [ -n "$ID" ] && telecharger_asset "$ID" && verifier_cle; then
  echo "Cle de signature : release brouillon « ${TAG} » (SHA-256 $(empreinte))"
  exit 0
fi
rm -f "$CLE" "$CLE.tmp"
if [ -n "$ID" ]; then
  echo "::warning title=Cle de signature::la cle rangee dans la release brouillon « ${TAG} » est illisible. Elle n'est PAS remplacee (pour ne pas perdre la cle d'origine) : a supprimer a la main si elle est vraiment perdue."
  echo "::error title=Cle de signature::cle rangee illisible"
  exit 1
fi

# 3. premiere fois : on cree la cle et on la range
echo "Aucune cle de signature trouvee : creation d'une cle fixe pour toutes les versions a venir."
if ! keytool -genkeypair -keystore "$CLE" -storetype PKCS12 -storepass codexlocal -keypass codexlocal \
      -alias codexlocal -keyalg RSA -keysize 2048 -validity 36500 \
      -dname "CN=CodexLocal, O=CodexLocal" >/dev/null 2>&1; then
  echec "Impossible de creer la cle de signature."
fi
if ! gh release create "$TAG" "$CLE" --repo "$GITHUB_REPOSITORY" --draft \
      --title "Cle de signature - NE PAS PUBLIER, NE PAS SUPPRIMER" \
      --notes "Cle qui signe toutes les versions de CodexLocal (c'est elle qui permet les mises a jour). Cette release est un brouillon prive : elle ne doit jamais etre publiee, et si elle est supprimee la prochaine compilation en cree une nouvelle (les mises a jour ne passeront alors plus sur les telephones deja installes)." \
      >/dev/null 2>&1; then
  echec "Impossible de ranger la cle dans une release brouillon (le jeton GitHub n'a pas le droit d'ecriture ?)."
fi

# Deux compilations simultanees ont pu creer deux cles : tout le monde reprend la plus ancienne.
sleep 3
ID="$(trouver_asset)"
if [ -n "$ID" ]; then
  rm -f "$CLE"
  if ! telecharger_asset "$ID" || ! verifier_cle; then
    echec "La cle rangee ne peut pas etre relue."
  fi
fi
echo "Cle de signature : nouvelle cle creee (SHA-256 $(empreinte))"
