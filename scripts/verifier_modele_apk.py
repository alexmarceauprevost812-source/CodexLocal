#!/usr/bin/env python3
"""Verifie le modele d'application du Studio (module :shell) apres sa compilation.

Le Studio de CodexLocal copie ce modele, remplace l'identifiant et le nom dans son
AndroidManifest.xml binaire, y glisse les fichiers du projet (assets/www) puis le signe.
Si l'un des reperes manque, la construction d'APK echouerait sur le telephone :
on le detecte ici, des la compilation.
"""
import sys
import zipfile

REPERE_ID = "com.codexlocal.app.p00000000"
REPERE_NOM = "CXLNOMAPPPLACEHOLDER0000"
REQUIS = ["AndroidManifest.xml", "classes.dex", "resources.arsc", "assets/www/index.html"]


def contient(donnees: bytes, texte: str) -> bool:
    # Les chaines d'un manifeste binaire sont en UTF-8 ou en UTF-16 selon le compilateur.
    return texte.encode("utf-8") in donnees or texte.encode("utf-16-le") in donnees


def main(chemin: str) -> int:
    erreurs = []
    with zipfile.ZipFile(chemin) as apk:
        noms = set(apk.namelist())
        for nom in REQUIS:
            if nom not in noms:
                erreurs.append(f"entree absente : {nom}")
        if "resources.arsc" in noms and apk.getinfo("resources.arsc").compress_type != zipfile.ZIP_STORED:
            erreurs.append("resources.arsc doit etre non compresse (Android 11 et plus)")
        icones = [n for n in noms if "ic_launcher" in n and n.endswith(".png")]
        if len(icones) < 5:
            erreurs.append(f"icones du lanceur attendues (5 densites), trouvees : {len(icones)}")
        if "AndroidManifest.xml" in noms:
            manifeste = apk.read("AndroidManifest.xml")
            for repere in (REPERE_ID, REPERE_NOM):
                if not contient(manifeste, repere):
                    erreurs.append(f"repere absent du manifeste : {repere}")
            if contient(manifeste, "android.permission.INTERNET"):
                erreurs.append("le modele ne doit demander aucune permission reseau")
    if erreurs:
        print("Modele d'APK invalide :")
        for e in erreurs:
            print(" -", e)
        return 1
    print(f"Modele d'APK valide : {chemin}")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage : verifier_modele_apk.py <modele-shell.apk>")
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
