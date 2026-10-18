# Installer les mises à jour Android

Dans CodexLocal 1.5, ouvrir les réglages et choisir **Installer un APK téléchargé**.
Sélectionner l’APK de la nouvelle version. Si Android demande l’autorisation
d’installer des applications de cette source, l’activer et revenir dans CodexLocal :
l’installation reprend. Android demande ensuite la confirmation finale.
La conversation et les réglages sont conservés lors d’une mise à jour compatible.

## Mises à jour automatiques

Le dépôt est public. À chaque push sur `main` qui change le code, GitHub compile
l'APK, puis le job **publier-release** crée une release `v<versionName>` avec l'APK.
Au démarrage, CodexLocal interroge `releases/latest` et propose la mise à jour
(option « Vérifier automatiquement » dans les réglages, activée par défaut).

Rien à modifier à la main : la version suit le numéro de compilation GitHub
(`1.5.<numéro>`, `versionCode` = 100 + numéro). Chaque fusion dans `main` produit
donc une nouvelle release plus récente que la précédente, et l'appli la propose.

## Condition nécessaire : garder la même clé de signature

Android accepte une mise à jour seulement si le nom du paquet est identique,
le `versionCode` augmente et la signature est compatible avec l’application
installée. Ne pas désinstaller l’application pour contourner un refus : cela
pourrait supprimer ses données.

Les clés debug créées par des runners GitHub différents ne sont pas stables.
Le propriétaire doit conserver **la clé qui a signé l’APK déjà installé** et
configurer sa représentation base64 comme secret GitHub Actions
`CODEXLOCAL_KEYSTORE_BASE64`. Le workflow réutilise alors cette clé pour les APK.
La configuration existante attend le fichier `app/codexlocal.keystore`, l’alias
`codexlocal` et les mots de passe `codexlocal`. Une clé avec d’autres paramètres
nécessite d’adapter la configuration de signature.

Sans ce secret, le workflow produit un APK de test et affiche un avertissement;
sa capacité à remplacer une installation précédente n’est pas garantie.
Une nouvelle clé ne remplace pas celle d’une ancienne installation.
