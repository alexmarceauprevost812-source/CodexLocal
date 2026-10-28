# -*- coding: utf-8 -*-
"""
Ti-Labo — bureau du laboratoire
Serveur local qui roule entierement depuis la cle USB-C.
Python standard seulement (aucun pip requis).
"""
import hashlib
import json
import math
import os
import re
import secrets
import shutil
import socket
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

# ---------------------------------------------------------------- chemins
APP = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.dirname(APP)                       # dossier TiLabo sur la cle
WEB = os.path.join(APP, "web")
OUTILS = os.path.join(RACINE, "outils")
PROJETS = os.path.join(RACINE, "projets")
DONNEES = os.path.join(RACINE, "donnees")
MEMOIRE = os.path.join(DONNEES, "memoire")
MODELES = os.path.join(RACINE, "ia", "modeles")
PARAMS_FICHIER = os.path.join(DONNEES, "parametres.json")

for d in (PROJETS, DONNEES, MEMOIRE, MODELES):
    os.makedirs(d, exist_ok=True)

WINDOWS = os.name == "nt"
ANDROID = "com.termux" in os.environ.get("PREFIX", "") or os.path.isdir("/data/data/com.termux")
EXE = ".exe" if WINDOWS else ""
OLLAMA_EXE = os.path.join(OUTILS, "ollama", "ollama" + EXE)
GIT_EXE = os.path.join(OUTILS, "git", "cmd", "git" + EXE)
PYTHON_DIR = os.path.join(OUTILS, "python")

PORT_APP = 8765
# Windows (cle) : Ollama a part sur 11500, avec les IA rangees sur la cle.
# Telephone (Termux) : on se branche sur l'Ollama deja installe (11434) -> memes IA que ton Codex local.
# Pour un autre port : variable TILABO_OLLAMA=127.0.0.1:PORT
_hote = os.environ.get("TILABO_OLLAMA", "127.0.0.1:%d" % (11434 if ANDROID else 11500))
HOTE_OLLAMA, PORT_OLLAMA = _hote.rsplit(":", 1)[0], int(_hote.rsplit(":", 1)[1])
JETON = secrets.token_hex(16)  # change a chaque lancement, protege l'API locale

FICHIERS_TEXTE = {
    ".py", ".txt", ".md", ".json", ".js", ".ts", ".html", ".css", ".bat", ".ps1",
    ".sh", ".toml", ".yaml", ".yml", ".ini", ".cfg", ".csv", ".xml", ".jsx", ".tsx",
    ".c", ".h", ".cpp", ".rs", ".go", ".java", ".kt", ".php", ".rb", ".lua", ".sql",
}
IGNORER = {".git", "__pycache__", "node_modules", ".venv", "venv", "dist", "build", ".idea", ".vscode"}

PARAMS_DEFAUT = {
    "moteur": "ollama",                       # "ollama" ou "openai" (LM Studio, llama.cpp, etc.)
    "url_openai": "http://127.0.0.1:1234/v1",
    "modele_jasette": "",
    "modele_codex": "",
    "modele_memoire": "nomic-embed-text",
    "police": "Segoe UI",
    "police_code": "Cascadia Mono",
    "github_token": "",
    "github_utilisateur": "",
    "internet": True,                         # l'IA locale peut aller chercher sur Internet
    "anthropic_key": "",                      # cle API Claude (optionnelle)
    "mot_de_passe": "",                       # empreinte PBKDF2 du mot de passe d'entree
}


# ---------------------------------------------------------------- parametres
_verrou = threading.Lock()


def lire_params():
    p = dict(PARAMS_DEFAUT)
    try:
        with open(PARAMS_FICHIER, "r", encoding="utf-8") as f:
            p.update(json.load(f))
    except (OSError, ValueError):
        pass
    return p


def ecrire_params(maj):
    with _verrou:
        p = lire_params()
        for k, v in maj.items():
            if k in PARAMS_DEFAUT:
                p[k] = v
        with open(PARAMS_FICHIER, "w", encoding="utf-8") as f:
            json.dump(p, f, ensure_ascii=False, indent=2)
        return p


def params_publics():
    p = lire_params()
    p["github_token"] = "********" if p.get("github_token") else ""
    p["anthropic_key"] = "********" if p.get("anthropic_key") else ""
    p["mot_de_passe"] = bool(p.get("mot_de_passe"))
    return p


# ---------------------------------------------------------------- securite chemins
def chemin_sur(relatif):
    """Retourne un chemin absolu garanti a l'interieur de projets/."""
    relatif = (relatif or "").replace("\\", "/").lstrip("/")
    absolu = os.path.realpath(os.path.join(PROJETS, relatif))
    base = os.path.realpath(PROJETS)
    if absolu != base and not absolu.startswith(base + os.sep):
        raise ValueError("Chemin hors du dossier projets")
    return absolu


def nom_valide(nom):
    nom = (nom or "").strip()
    if not re.fullmatch(r"[A-Za-z0-9_.\- ]{1,80}", nom) or nom in (".", ".."):
        raise ValueError("Nom invalide (lettres, chiffres, _ - . et espaces seulement)")
    return nom


# ---------------------------------------------------------------- environnement des outils
def env_outils():
    env = dict(os.environ)
    ajout = []
    if os.path.isdir(PYTHON_DIR):
        ajout += [PYTHON_DIR, os.path.join(PYTHON_DIR, "Scripts")]
    git_cmd = os.path.dirname(GIT_EXE)
    if os.path.isdir(git_cmd):
        ajout.append(git_cmd)
    if os.path.isdir(os.path.dirname(OLLAMA_EXE)):
        ajout.append(os.path.dirname(OLLAMA_EXE))
    env["PATH"] = os.pathsep.join(ajout + [env.get("PATH", "")])
    env["OLLAMA_HOST"] = "%s:%d" % (HOTE_OLLAMA, PORT_OLLAMA)
    if not ANDROID:
        env["OLLAMA_MODELS"] = MODELES
    env["PYTHONIOENCODING"] = "utf-8"
    env["GIT_TERMINAL_PROMPT"] = "0"
    return env


def git_commande():
    if os.path.isfile(GIT_EXE):
        return GIT_EXE
    return shutil.which("git")


# ---------------------------------------------------------------- Ollama portable
_proc_ollama = None


PORT_OLLAMA_PC = 11434  # l'Ollama installe sur le PC (ex. qwen3-coder:30b)


def ollama_url(chemin, cle=False):
    """Moteur choisi : les IA de la cle (par defaut) ou celles du PC."""
    if not cle and not ANDROID and lire_params().get("moteur") == "ollama_pc":
        return "http://127.0.0.1:%d%s" % (PORT_OLLAMA_PC, chemin)
    return "http://%s:%d%s" % (HOTE_OLLAMA, PORT_OLLAMA, chemin)


def ollama_vivant(cle=False):
    try:
        with urllib.request.urlopen(ollama_url("/api/version", cle), timeout=2) as r:
            return json.loads(r.read().decode()).get("version", "?")
    except Exception:
        return None


def demarrer_ollama():
    global _proc_ollama
    if ollama_vivant(cle=True):
        return True
    exe = OLLAMA_EXE if os.path.isfile(OLLAMA_EXE) else shutil.which("ollama")
    if not exe:
        return False
    drapeaux = 0x08000000 if WINDOWS else 0  # CREATE_NO_WINDOW
    _proc_ollama = subprocess.Popen(
        [exe, "serve"], env=env_outils(), cwd=RACINE,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, creationflags=drapeaux,
    )
    for _ in range(40):
        if ollama_vivant(cle=True):
            return True
        time.sleep(0.25)
    return False


def arreter_ollama():
    if _proc_ollama and _proc_ollama.poll() is None:
        _proc_ollama.terminate()


def http_json(url, donnees=None, methode=None, entetes=None, delai=30):
    corps = json.dumps(donnees).encode() if donnees is not None else None
    req = urllib.request.Request(url, data=corps, method=methode or ("POST" if corps else "GET"))
    req.add_header("Content-Type", "application/json")
    for k, v in (entetes or {}).items():
        req.add_header(k, v)
    with urllib.request.urlopen(req, timeout=delai) as r:
        texte = r.read().decode("utf-8")
        return json.loads(texte) if texte.strip() else {}


# ---------------------------------------------------------------- projets
def langage(dossier):
    compte = {}
    for _, _, fichiers in marcher(dossier, limite=400):
        for f in fichiers:
            ext = os.path.splitext(f)[1].lower()
            compte[ext] = compte.get(ext, 0) + 1
    noms = {".py": "Python", ".js": "JavaScript", ".ts": "TypeScript", ".html": "Web",
            ".rs": "Rust", ".go": "Go", ".java": "Java", ".c": "C", ".cpp": "C++",
            ".php": "PHP", ".rb": "Ruby", ".lua": "Lua", ".sh": "Shell", ".bat": "Batch"}
    meilleurs = sorted(((n, e) for e, n in compte.items() if e in noms), reverse=True)
    return noms[meilleurs[0][1]] if meilleurs else "—"


def marcher(dossier, limite=2000):
    n = 0
    for base, dossiers, fichiers in os.walk(dossier):
        dossiers[:] = sorted(d for d in dossiers if d not in IGNORER and not d.startswith("."))
        n += len(fichiers)
        yield base, dossiers, sorted(fichiers)
        if n > limite:
            break


def lire_resume(dossier):
    for nom in ("README.md", "readme.md", "LISEZMOI.md", "README.txt", "LISEZMOI.txt"):
        p = os.path.join(dossier, nom)
        if os.path.isfile(p):
            try:
                with open(p, "r", encoding="utf-8", errors="replace") as f:
                    lignes = [l.strip("# \n") for l in f.readlines()[:12] if l.strip()]
                return " ".join(lignes)[:220]
            except OSError:
                pass
    return ""


def liste_projets():
    res = []
    for nom in sorted(os.listdir(PROJETS), key=str.lower):
        p = os.path.join(PROJETS, nom)
        if not os.path.isdir(p) or nom.startswith("."):
            continue
        nb = sum(len(f) for _, _, f in marcher(p))
        res.append({
            "nom": nom,
            "fichiers": nb,
            "langage": langage(p),
            "resume": lire_resume(p),
            "git": os.path.isdir(os.path.join(p, ".git")),
            "modifie": os.path.getmtime(p),
            "indexe": os.path.isfile(os.path.join(MEMOIRE, nom + ".json")),
        })
    res.sort(key=lambda x: -x["modifie"])
    return res


def arbre(projet):
    racine = chemin_sur(projet)
    noeuds = []
    for base, dossiers, fichiers in marcher(racine, limite=1500):
        rel = os.path.relpath(base, racine).replace("\\", "/")
        prof = 0 if rel == "." else rel.count("/") + 1
        if rel != ".":
            noeuds.append({"type": "dossier", "chemin": projet + "/" + rel, "nom": os.path.basename(base), "prof": prof - 1})
        for f in fichiers:
            chemin = projet + "/" + (f if rel == "." else rel + "/" + f)
            noeuds.append({"type": "fichier", "chemin": chemin, "nom": f, "prof": prof,
                           "taille": os.path.getsize(os.path.join(base, f))})
    return noeuds


def est_texte(chemin):
    return os.path.splitext(chemin)[1].lower() in FICHIERS_TEXTE or os.path.basename(chemin).lower() in (
        "makefile", "dockerfile", ".gitignore", "license", "licence")


def lire_fichier(rel):
    p = chemin_sur(rel)
    if not os.path.isfile(p):
        raise ValueError("Fichier introuvable")
    if os.path.getsize(p) > 2_000_000:
        raise ValueError("Fichier trop gros pour l'editeur (2 Mo max)")
    with open(p, "rb") as f:
        brut = f.read()
    if b"\x00" in brut[:4000]:
        raise ValueError("Fichier binaire — pas modifiable ici")
    return brut.decode("utf-8", errors="replace")


def trouve_projets(question):
    """Codex : retrouve les projets qui parlent de la question."""
    mots = [m for m in re.findall(r"[a-zA-Z0-9àâäéèêëîïôöùûüç_]{3,}", question.lower())]
    resultats = []
    for pr in liste_projets():
        dossier = os.path.join(PROJETS, pr["nom"])
        score, indices = 0, []
        texte_nom = pr["nom"].lower() + " " + pr["resume"].lower()
        for m in mots:
            if m in texte_nom:
                score += 5
        for base, _, fichiers in marcher(dossier, limite=600):
            for f in fichiers:
                fl = f.lower()
                for m in mots:
                    if m in fl:
                        score += 3
                        rel = os.path.relpath(os.path.join(base, f), PROJETS).replace("\\", "/")
                        if rel not in indices:
                            indices.append(rel)
                p = os.path.join(base, f)
                if est_texte(f) and os.path.getsize(p) < 300_000:
                    try:
                        with open(p, "r", encoding="utf-8", errors="ignore") as fh:
                            contenu = fh.read().lower()
                    except OSError:
                        continue
                    for m in mots:
                        c = contenu.count(m)
                        if c:
                            score += min(c, 5)
                            rel = os.path.relpath(p, PROJETS).replace("\\", "/")
                            if rel not in indices and len(indices) < 6:
                                indices.append(rel)
        if score:
            resultats.append({"nom": pr["nom"], "score": score, "langage": pr["langage"],
                              "resume": pr["resume"], "fichiers": indices[:6]})
    resultats.sort(key=lambda r: -r["score"])
    return resultats[:8]


# ---------------------------------------------------------------- terminal
def executer(projet, commande):
    cwd = chemin_sur(projet) if projet else PROJETS
    drapeaux = 0x08000000 if WINDOWS else 0
    try:
        r = subprocess.run(commande, shell=True, cwd=cwd, env=env_outils(), capture_output=True,
                           timeout=180, creationflags=drapeaux)
    except subprocess.TimeoutExpired:
        return {"code": -1, "sortie": "(arrete apres 3 minutes)"}

    def dec(b):
        for enc in ("utf-8", "cp850", "cp1252"):
            try:
                return b.decode(enc)
            except UnicodeDecodeError:
                continue
        return b.decode("utf-8", errors="replace")

    sortie = dec(r.stdout) + dec(r.stderr)
    return {"code": r.returncode, "sortie": sortie[-60_000:]}


# ---------------------------------------------------------------- GitHub
def masquer(texte, jeton):
    return texte.replace(jeton, "********") if jeton else texte


def github_depots():
    p = lire_params()
    jeton = p.get("github_token")
    if not jeton:
        raise ValueError("Ajoute ton jeton GitHub dans GitHub > Connecter")
    depots = http_json("https://api.github.com/user/repos?per_page=100&sort=updated",
                       entetes={"Authorization": "Bearer " + jeton, "Accept": "application/vnd.github+json",
                                "User-Agent": "Ti-Labo"}, delai=20)
    return [{"nom": d["full_name"], "prive": d["private"], "url": d["clone_url"],
             "description": d.get("description") or "", "langage": d.get("language") or "—"} for d in depots]


def github_connecter(jeton):
    info = http_json("https://api.github.com/user",
                     entetes={"Authorization": "Bearer " + jeton, "User-Agent": "Ti-Labo"}, delai=15)
    ecrire_params({"github_token": jeton, "github_utilisateur": info.get("login", "")})
    return info.get("login", "")


def cloner(url, nom=None):
    git = git_commande()
    if not git:
        raise ValueError("Git est absent de la cle — relance Installer-Ti-Labo.bat")
    url = url.strip()
    if not re.match(r"^https://[A-Za-z0-9.\-]+/[\w.\-]+/[\w.\-]+?(\.git)?/?$", url):
        raise ValueError("Adresse invalide (ex. https://github.com/moi/projet)")
    nom = nom_valide(nom or url.rstrip("/").split("/")[-1].removesuffix(".git"))
    cible = os.path.join(PROJETS, nom)
    if os.path.exists(cible):
        raise ValueError("Un projet « %s » existe deja sur la cle" % nom)
    jeton = lire_params().get("github_token", "")
    url_auth = url
    if jeton and url.startswith("https://github.com/"):
        url_auth = url.replace("https://", "https://x-access-token:%s@" % jeton, 1)
    drapeaux = 0x08000000 if WINDOWS else 0
    r = subprocess.run([git, "clone", "--depth", "50", url_auth, cible], env=env_outils(),
                       capture_output=True, timeout=900, creationflags=drapeaux)
    sortie = masquer((r.stdout + r.stderr).decode("utf-8", errors="replace"), jeton)
    if r.returncode != 0:
        raise ValueError("Echec du clonage :\n" + sortie[-2000:])
    if jeton:  # ne jamais laisser le jeton dans .git/config
        subprocess.run([git, "-C", cible, "remote", "set-url", "origin", url], env=env_outils(),
                       capture_output=True, creationflags=drapeaux)
    return nom


# ---------------------------------------------------------------- memoire (index vectoriel local)
def embed(textes):
    p = lire_params()
    r = http_json(ollama_url("/api/embed"), {"model": p["modele_memoire"], "input": textes}, delai=600)
    return r["embeddings"]


def morceaux(texte, taille=1200, chevauche=150):
    i = 0
    while i < len(texte):
        yield texte[i:i + taille]
        i += taille - chevauche


def indexer(projet):
    racine = chemin_sur(projet)
    items = []
    for base, _, fichiers in marcher(racine, limite=1500):
        for f in fichiers:
            p = os.path.join(base, f)
            if not est_texte(f) or os.path.getsize(p) > 400_000:
                continue
            try:
                with open(p, "r", encoding="utf-8", errors="ignore") as fh:
                    contenu = fh.read()
            except OSError:
                continue
            rel = os.path.relpath(p, PROJETS).replace("\\", "/")
            for m in morceaux(contenu):
                if m.strip():
                    items.append({"fichier": rel, "texte": m})
    vecteurs = []
    for i in range(0, len(items), 32):
        vecteurs += embed([("Fichier " + it["fichier"] + "\n" + it["texte"]) for it in items[i:i + 32]])
    for it, v in zip(items, vecteurs):
        it["v"] = [round(x, 5) for x in v]
    with open(os.path.join(MEMOIRE, nom_valide(projet) + ".json"), "w", encoding="utf-8") as f:
        json.dump({"modele": lire_params()["modele_memoire"], "items": items}, f)
    return len(items)


def cosinus(a, b):
    num = sum(x * y for x, y in zip(a, b))
    da = math.sqrt(sum(x * x for x in a)) or 1
    db = math.sqrt(sum(y * y for y in b)) or 1
    return num / (da * db)


def chercher_memoire(question, projet=None, k=5):
    fichiers = [projet + ".json"] if projet else [f for f in os.listdir(MEMOIRE) if f.endswith(".json")]
    if not fichiers:
        return []
    q = embed([question])[0]
    res = []
    for nf in fichiers:
        chemin = os.path.join(MEMOIRE, os.path.basename(nf))
        if not os.path.isfile(chemin):
            continue
        with open(chemin, "r", encoding="utf-8") as f:
            data = json.load(f)
        for it in data["items"]:
            res.append((cosinus(q, it["v"]), it))
    res.sort(key=lambda x: -x[0])
    return [{"score": round(s, 3), "fichier": it["fichier"], "texte": it["texte"]} for s, it in res[:k]]


# ---------------------------------------------------------------- outils Internet de l'IA
import html as _html
import ipaddress
from html.parser import HTMLParser

NAVIGATEUR = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) TiLabo/1.0"


def url_publique(url):
    """Bloque localhost et les reseaux prives : l'IA ne peut pas fouiller le PC ni le reseau maison."""
    u = urllib.parse.urlparse(url)
    if u.scheme not in ("http", "https") or not u.hostname:
        return False
    try:
        for info in socket.getaddrinfo(u.hostname, None):
            ip = ipaddress.ip_address(info[4][0])
            if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast:
                return False
    except (socket.gaierror, ValueError):
        return False
    return True


def telecharger_texte(url, max_octets=1_500_000, delai=15):
    req = urllib.request.Request(url, headers={"User-Agent": NAVIGATEUR, "Accept-Language": "fr-CA,fr,en"})
    with urllib.request.urlopen(req, timeout=delai) as r:
        brut = r.read(max_octets)
        enc = r.headers.get_content_charset() or "utf-8"
        return brut.decode(enc, errors="replace"), r.headers.get_content_type()


class _Texte(HTMLParser):
    SAUTER = {"script", "style", "noscript", "svg", "nav", "footer", "header", "form", "iframe"}

    def __init__(self):
        super().__init__()
        self.morceaux, self.profondeur, self.dans_pre = [], 0, 0
        self.titre, self._t = "", False

    def handle_starttag(self, tag, attrs):
        if tag in self.SAUTER:
            self.profondeur += 1
        if tag == "title":
            self._t = True
        if tag in ("pre", "code"):
            self.dans_pre += 1
            if tag == "pre":
                self.morceaux.append("\n```\n")
        if tag in ("p", "br", "li", "h1", "h2", "h3", "h4", "tr", "div"):
            self.morceaux.append("\n")

    def handle_endtag(self, tag):
        if tag in self.SAUTER and self.profondeur:
            self.profondeur -= 1
        if tag == "title":
            self._t = False
        if tag in ("pre", "code") and self.dans_pre:
            self.dans_pre -= 1
            if tag == "pre":
                self.morceaux.append("\n```\n")

    def handle_data(self, data):
        if self._t:
            self.titre += data
        if not self.profondeur:
            self.morceaux.append(data if self.dans_pre else re.sub(r"\s+", " ", data))


def lire_page(url, limite=9000):
    if not url_publique(url):
        return {"erreur": "Adresse refusee (privee ou invalide)"}
    texte, type_ = telecharger_texte(url)
    if "html" in type_:
        p = _Texte()
        p.feed(texte)
        contenu = re.sub(r"\n\s*\n+", "\n\n", "".join(p.morceaux)).strip()
        return {"url": url, "titre": p.titre.strip()[:150], "texte": contenu[:limite]}
    return {"url": url, "titre": url, "texte": texte[:limite]}


def recherche_web(requete, n=6):
    """Recherche DuckDuckGo (version HTML, sans cle API)."""
    url = "https://html.duckduckgo.com/html/?" + urllib.parse.urlencode({"q": requete, "kl": "ca-fr"})
    page, _ = telecharger_texte(url)
    resultats = []
    for m in re.finditer(r'<a[^>]+class="result__a"[^>]+href="([^"]+)"[^>]*>(.*?)</a>(.*?)(?=<a[^>]+class="result__a"|$)',
                         page, re.S):
        lien = _html.unescape(m.group(1))
        if "uddg=" in lien:
            lien = urllib.parse.unquote(urllib.parse.parse_qs(urllib.parse.urlparse(lien).query).get("uddg", [lien])[0])
        if "duckduckgo.com/y.js" in lien:
            continue  # publicite
        extrait = re.search(r'class="result__snippet"[^>]*>(.*?)</a>', m.group(3), re.S)
        nettoie = lambda s: _html.unescape(re.sub(r"<[^>]+>", "", s)).strip()
        resultats.append({"titre": nettoie(m.group(2)), "url": lien,
                          "extrait": nettoie(extrait.group(1)) if extrait else ""})
        if len(resultats) >= n:
            break
    return resultats


def code_github(requete, n=5):
    jeton = lire_params().get("github_token")
    if not jeton:
        # Sans jeton, GitHub refuse la recherche de code : on passe par la recherche web.
        return recherche_web(requete + " site:github.com", n)
    r = http_json("https://api.github.com/search/code?" + urllib.parse.urlencode({"q": requete, "per_page": n}),
                  entetes={"Authorization": "Bearer " + jeton, "User-Agent": "Ti-Labo",
                           "Accept": "application/vnd.github.text-match+json"}, delai=20)
    sortie = []
    for it in r.get("items", [])[:n]:
        extrait = " … ".join(t.get("fragment", "") for t in it.get("text_matches", [])[:2])
        brut = it["html_url"].replace("github.com", "raw.githubusercontent.com").replace("/blob/", "/")
        sortie.append({"depot": it["repository"]["full_name"], "fichier": it["path"],
                       "url": it["html_url"], "url_brut": brut, "extrait": extrait[:600]})
    return sortie


OUTILS_IA = [
    {"type": "function", "function": {
        "name": "recherche_web",
        "description": "Cherche sur Internet. Utilise-le seulement si tu ne connais pas la reponse, si c'est recent, "
                       "ou pour trouver de la documentation ou des exemples de code.",
        "parameters": {"type": "object", "properties": {"requete": {"type": "string", "description": "mots-cles"}},
                       "required": ["requete"]}}},
    {"type": "function", "function": {
        "name": "lire_page",
        "description": "Lit le texte d'une page web (documentation, article, fichier de code brut).",
        "parameters": {"type": "object", "properties": {"url": {"type": "string"}}, "required": ["url"]}}},
    {"type": "function", "function": {
        "name": "chercher_code_github",
        "description": "Cherche des exemples de vrai code sur GitHub (ex. 'tkinter treeview language:python').",
        "parameters": {"type": "object", "properties": {"requete": {"type": "string"}}, "required": ["requete"]}}},
    {"type": "function", "function": {
        "name": "chercher_mes_projets",
        "description": "Cherche dans les projets d'Alex sur la cle USB (noms, fichiers, contenu).",
        "parameters": {"type": "object", "properties": {"requete": {"type": "string"}}, "required": ["requete"]}}},
]


def appeler_outil(nom, args, projet=None):
    try:
        if nom in ("lister_fichiers", "lire_fichier", "ecrire_fichier", "modifier_fichier", "chercher_dans_fichiers"):
            return outil_fichier(nom, args, projet)
        if nom == "recherche_web":
            return recherche_web(args.get("requete", ""))
        if nom == "lire_page":
            return lire_page(args.get("url", ""))
        if nom == "chercher_code_github":
            return code_github(args.get("requete", ""))
        if nom == "chercher_mes_projets":
            return trouve_projets(args.get("requete", ""))
        return {"erreur": "outil inconnu"}
    except Exception as e:
        return {"erreur": str(e)}


def etiquette_outil(nom, args):
    return {"recherche_web": "Recherche web : %s" % args.get("requete", ""),
            "lire_page": "Lecture : %s" % args.get("url", ""),
            "chercher_code_github": "Code GitHub : %s" % args.get("requete", ""),
            "chercher_mes_projets": "Mes projets : %s" % args.get("requete", ""),
            "lister_fichiers": "Regarde les fichiers du projet",
            "chercher_dans_fichiers": "Cherche « %s » dans le code" % args.get("motif", ""),
            "lire_fichier": "Lit %s" % args.get("chemin", ""),
            "ecrire_fichier": "Ecrit %s" % args.get("chemin", ""),
            "modifier_fichier": "Modifie %s" % args.get("chemin", ""),
            "executer_commande": "Veut lancer : %s" % args.get("commande", "")}.get(nom, nom)


# ---------------------------------------------------------------- Codex : lire, creer et ecrire les fichiers du projet
SESSIONS = os.path.join(DONNEES, "conversations")
os.makedirs(SESSIONS, exist_ok=True)
SCHEMAS = os.path.join(DONNEES, "schemas")
os.makedirs(SCHEMAS, exist_ok=True)
SAUVEGARDES = os.path.join(DONNEES, "sauvegardes")
os.makedirs(SAUVEGARDES, exist_ok=True)

OUTILS_FICHIERS_LECTURE = [
    {"type": "function", "function": {
        "name": "lister_fichiers",
        "description": "Liste les fichiers du projet ouvert (chemins relatifs au projet).",
        "parameters": {"type": "object", "properties": {}, "required": []}}},
    {"type": "function", "function": {
        "name": "lire_fichier",
        "description": "Lit un fichier du projet, avec les numeros de ligne. Lis toujours un fichier avant de le modifier. "
                       "Pour un gros fichier, donne debut et fin (numeros de ligne).",
        "parameters": {"type": "object", "properties": {
            "chemin": {"type": "string", "description": "ex. main.py ou src/app.js"},
            "debut": {"type": "integer", "description": "premiere ligne (optionnel)"},
            "fin": {"type": "integer", "description": "derniere ligne (optionnel)"}},
            "required": ["chemin"]}}},
    {"type": "function", "function": {
        "name": "chercher_dans_fichiers",
        "description": "Cherche un mot ou une expression reguliere dans tous les fichiers du projet. Donne chaque "
                       "fichier:ligne:texte trouve. Utilise-le pour trouver ou une fonction est definie ou utilisee.",
        "parameters": {"type": "object", "properties": {
            "motif": {"type": "string", "description": "texte ou regex, ex. def calculer|calculer\\("},
            "fichiers": {"type": "string", "description": "filtre optionnel sur le nom, ex. .py"}},
            "required": ["motif"]}}},
]
OUTILS_FICHIERS_ECRITURE = [
    {"type": "function", "function": {
        "name": "ecrire_fichier",
        "description": "Cree un nouveau fichier ou remplace TOUT le contenu d'un fichier du projet. Donne le contenu complet.",
        "parameters": {"type": "object", "properties": {
            "chemin": {"type": "string", "description": "chemin relatif au projet, ex. src/utils.py"},
            "contenu": {"type": "string", "description": "le contenu complet du fichier"}},
            "required": ["chemin", "contenu"]}}},
    {"type": "function", "function": {
        "name": "modifier_fichier",
        "description": "Remplace un passage exact d'un fichier existant par un nouveau texte. Le passage doit etre copie "
                       "exactement du fichier (lis-le d'abord) et n'apparaitre qu'une seule fois.",
        "parameters": {"type": "object", "properties": {
            "chemin": {"type": "string"},
            "ancien": {"type": "string", "description": "le texte exact a remplacer"},
            "nouveau": {"type": "string", "description": "le texte de remplacement"}},
            "required": ["chemin", "ancien", "nouveau"]}}},
]

INVITE_FICHIERS = ("\n\nTu travailles dans le projet « %s ». Ecris DIRECTEMENT dans les fichiers avec tes outils, "
                   "ne te contente jamais d'afficher du code.\n"
                   "METHODE (comme un developpeur senior) :\n"
                   "1. Comprends la demande exacte. Si un fichier est ouvert, c'est probablement lui.\n"
                   "2. Trouve le bon endroit : chercher_dans_fichiers pour localiser fonctions et usages, puis "
                   "lire_fichier sur les fichiers concernes (jamais modifier sans avoir lu).\n"
                   "3. Fais le plus petit changement correct : modifier_fichier avec un passage copie EXACTEMENT "
                   "(sans les numeros de ligne) et assez long pour etre unique. ecrire_fichier seulement pour un "
                   "nouveau fichier ou une reecriture complete.\n"
                   "4. Respecte le style existant (indentation, noms, langue des commentaires) et mets a jour les "
                   "imports et les autres fichiers touches par ton changement.\n"
                   "5. Lis le champ 'verification' apres chaque ecriture : s'il y a une erreur, corrige-la tout de suite.\n"
                   "6. Code complet et fonctionnel : pas de « ... », pas de TODO, pas de code invente qui n'existe pas.\n"
                   "7. Avant d'ecrire, demande-toi : est-ce le BON fichier ? Celui qui contient deja ce code, ou celui "
                   "qui est ouvert. Un nouveau fichier seulement si aucun ne convient.\n"
                   "8. Termine par UNE ligne qui nomme les fichiers changes. NE recopie PAS le code dans ta reponse.")
OUTILS_COMMANDE = [
    {"type": "function", "function": {
        "name": "executer_commande",
        "description": "Lance une commande dans le dossier du projet (ex. python main.py, pip install requests, "
                       "npm test, git status) pour tester ou verifier ton travail. L'utilisateur doit l'autoriser. "
                       "Pas de programme interactif ni de serveur qui ne s'arrete jamais.",
        "parameters": {"type": "object", "properties": {"commande": {"type": "string"}}, "required": ["commande"]}}},
]
INVITE_AGENT = ("\n\nMODE AGENT : tu travailles de facon autonome jusqu'a ce que la tache soit vraiment finie. "
                "1) explore les fichiers utiles, 2) ecris le code dans les bons fichiers, 3) teste-le avec "
                "executer_commande, 4) si ca plante, lis l'erreur, corrige et reteste, 5) termine par un court resume "
                "de ce qui a ete fait et de comment l'utiliser. Une commande refusee par l'utilisateur ne doit pas "
                "etre redemandee telle quelle.")
INVITE_TURBO = ("\n\nMODE TURBO : va droit au but et enchaine les etapes sans t'arreter pour demander. "
                "Lis plusieurs fichiers d'un coup quand c'est utile, ecris tout le code necessaire, teste avec "
                "executer_commande (python fichier.py, pytest, node fichier.js, npm test...), corrige jusqu'a ce que ca "
                "marche. Ne t'arrete que quand la tache est finie et verifiee.")
# Commandes de test qui ne modifient rien hors du projet : lancees sans demander en Turbo
_SANS_DANGER = re.compile(r"^(python3?|py)(\s+-3)?\s+(-m\s+(pytest|unittest|py_compile)\b[^|&;><`$]*|[\w./\\-]+\.py(\s+[\w./=-]+)*)$"
                          r"|^(pytest|node\s+[\w./\\-]+\.m?js|npm\s+(test|run\s+(test|build|lint))|git\s+(status|diff|log)(\s+[\w./-]+)*)$", re.I)


def commande_sans_danger(cmd):
    return bool(_SANS_DANGER.match(cmd.strip()))


_attentes = {}
_attentes_verrou = threading.Lock()


def attendre_autorisation(ident, delai=600):
    with _attentes_verrou:
        _attentes[ident] = {"evenement": threading.Event(), "ok": False}
    a = _attentes[ident]
    a["evenement"].wait(delai)
    with _attentes_verrou:
        _attentes.pop(ident, None)
    return a["ok"]


def repondre_autorisation(ident, ok):
    with _attentes_verrou:
        a = _attentes.get(ident)
    if not a:
        raise ValueError("Cette demande n'attend plus de reponse")
    a["ok"] = bool(ok)
    a["evenement"].set()


INVITE_FICHIERS_LECTURE = ("\n\nTu peux lister_fichiers et lire_fichier dans le projet « %s » pour bien comprendre "
                           "avant de faire ton plan. N'ecris rien tant que le plan n'est pas approuve.")


def chemin_projet(projet, rel):
    """Chemin absolu d'un fichier, garanti a l'interieur du projet."""
    base = chemin_sur(nom_valide(projet))
    rel = (rel or "").replace("\\", "/").strip().lstrip("/")
    if rel.startswith(projet + "/"):
        rel = rel[len(projet) + 1:]
    if not rel:
        raise ValueError("Chemin de fichier manquant")
    p = os.path.realpath(os.path.join(base, rel))
    if not p.startswith(os.path.realpath(base) + os.sep):
        raise ValueError("Ce chemin sort du projet")
    if any(seg in IGNORER for seg in rel.split("/")):
        raise ValueError("Dossier protege (%s)" % rel)
    return p, rel


def sauvegarder(projet, rel, absolu):
    ident = time.strftime("%Y%m%d-%H%M%S-") + secrets.token_hex(3)
    existait = os.path.isfile(absolu)
    ancien = ""
    if existait:
        with open(absolu, "r", encoding="utf-8", errors="replace") as f:
            ancien = f.read()
    with open(os.path.join(SAUVEGARDES, ident + ".json"), "w", encoding="utf-8") as f:
        json.dump({"projet": projet, "chemin": rel, "existait": existait, "contenu": ancien}, f, ensure_ascii=False)
    return ident, existait, ancien


def compter_lignes(ancien, nouveau):
    a, n = ancien.splitlines(), nouveau.splitlines()
    import difflib
    plus = moins = 0
    for l in difflib.ndiff(a, n):
        if l.startswith("+ "):
            plus += 1
        elif l.startswith("- "):
            moins += 1
    return plus, moins


def outil_fichier(nom, args, projet):
    if not projet:
        return {"erreur": "Aucun projet ouvert"}
    if nom == "lister_fichiers":
        base = chemin_sur(projet)
        return [os.path.relpath(os.path.join(b, f), base).replace("\\", "/")
                for b, _, fs in marcher(base, limite=800) for f in fs][:800]
    if nom == "chercher_dans_fichiers":
        return chercher_dans_projet(projet, args.get("motif") or "", args.get("fichiers") or "")
    p, rel = chemin_projet(projet, args.get("chemin"))
    if nom == "lire_fichier":
        if not os.path.isfile(p):
            return {"erreur": "Fichier introuvable : " + rel}
        lignes = lire_fichier(projet + "/" + rel).split("\n")
        debut = max(1, int(args.get("debut") or 1))
        fin = min(len(lignes), int(args.get("fin") or len(lignes)))
        if fin - debut > 1500:
            fin = debut + 1500
        texte = "\n".join("%5d| %s" % (i, lignes[i - 1]) for i in range(debut, fin + 1))
        info = {"chemin": rel, "lignes_total": len(lignes), "contenu": texte}
        if fin < len(lignes):
            info["note"] = "Fichier coupe a la ligne %d : relis avec debut=%d pour la suite." % (fin, fin + 1)
        info["rappel"] = "Les numeros '12| ' ne font PAS partie du fichier : ne les recopie jamais."
        return info
    if not est_texte(rel) and os.path.splitext(rel)[1]:
        return {"erreur": "Type de fichier non modifiable ici : " + rel}
    if nom == "ecrire_fichier":
        contenu = args.get("contenu")
        if not isinstance(contenu, str):
            return {"erreur": "Contenu manquant"}
        ident, existait, ancien = sauvegarder(projet, rel, p)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(contenu)
        plus, moins = compter_lignes(ancien, contenu)
        return {"ok": True, **verifier_code(rel, contenu),
                "_evenement": {"action": "modifie" if existait else "cree", "chemin": projet + "/" + rel,
                               "sauvegarde": ident, "plus": plus, "moins": moins}}
    if nom == "modifier_fichier":
        if not os.path.isfile(p):
            return {"erreur": "Fichier introuvable : " + rel + " (utilise ecrire_fichier pour le creer)"}
        ancien_txt, nouveau_txt = args.get("ancien") or "", args.get("nouveau") or ""
        with open(p, "r", encoding="utf-8", errors="replace") as f:
            contenu = f.read()
        # tolere les numeros de ligne recopies par erreur ("  12| ")
        ancien_txt = re.sub(r"(?m)^\s*\d+\| ", "", ancien_txt) if re.search(r"(?m)^\s*\d+\| ", ancien_txt) else ancien_txt
        n = contenu.count(ancien_txt) if ancien_txt else 0
        if n == 0 and ancien_txt.strip():
            # deuxieme chance : memes lignes, espaces de fin de ligne differents
            normal = lambda t: "\n".join(l.rstrip() for l in t.split("\n"))
            if normal(contenu).count(normal(ancien_txt)) == 1:
                contenu, ancien_txt = normal(contenu), normal(ancien_txt)
                n = 1
        if n == 0:
            proche = passage_proche(contenu, ancien_txt)
            return {"erreur": "Passage introuvable dans " + rel + ". Relis le fichier et copie le passage exactement."
                              + (" Le plus proche (lignes %d-%d) :\n%s" % proche if proche else "")}
        if n > 1:
            return {"erreur": "Passage trouve %d fois dans %s : ajoute des lignes autour pour qu'il soit unique." % (n, rel)}
        ident, _, ancien = sauvegarder(projet, rel, p)
        nouveau = contenu.replace(ancien_txt, nouveau_txt, 1)
        with open(p, "w", encoding="utf-8", newline="") as f:
            f.write(nouveau)
        plus, moins = compter_lignes(ancien, nouveau)
        return {"ok": True, **verifier_code(rel, nouveau),
                "_evenement": {"action": "modifie", "chemin": projet + "/" + rel,
                               "sauvegarde": ident, "plus": plus, "moins": moins}}
    return {"erreur": "outil inconnu"}


def chercher_dans_projet(projet, motif, filtre=""):
    if not motif:
        return {"erreur": "Motif vide"}
    try:
        rx = re.compile(motif, re.I)
    except re.error:
        rx = re.compile(re.escape(motif), re.I)
    base = chemin_sur(projet)
    res = []
    for b, _, fs in marcher(base, limite=2000):
        for f in fs:
            if filtre and filtre.lower() not in f.lower():
                continue
            p = os.path.join(b, f)
            if not est_texte(f) or os.path.getsize(p) > 1_000_000:
                continue
            try:
                with open(p, "r", encoding="utf-8", errors="ignore") as fh:
                    for i, l in enumerate(fh, 1):
                        if rx.search(l):
                            res.append("%s:%d: %s" % (os.path.relpath(p, base).replace("\\", "/"), i, l.rstrip()[:200]))
                            if len(res) >= 120:
                                return res + ["… (limite atteinte, precise le motif)"]
            except OSError:
                continue
    return res or ["Aucun resultat pour « %s »" % motif]


def passage_proche(contenu, cherche):
    """Trouve les lignes du fichier qui ressemblent le plus au passage introuvable."""
    import difflib
    lignes, n = contenu.split("\n"), max(1, len(cherche.split("\n")))
    meilleur, ou = 0.0, None
    for i in range(0, max(1, len(lignes) - n + 1)):
        r = difflib.SequenceMatcher(None, "\n".join(lignes[i:i + n]), cherche).quick_ratio()
        if r > meilleur:
            meilleur, ou = r, i
    if ou is None or meilleur < 0.5:
        return None
    return (ou + 1, ou + n, "\n".join(lignes[ou:ou + n])[:1500])


def verifier_code(rel, contenu):
    """Verifie tout de suite la syntaxe, pour que l'IA corrige elle-meme ses erreurs."""
    ext = os.path.splitext(rel)[1].lower()
    try:
        if ext == ".py":
            compile(contenu, rel, "exec")
        elif ext == ".json":
            json.loads(contenu)
        else:
            return {"verification": "non verifie (type %s)" % (ext or "?")}
    except SyntaxError as e:
        return {"verification": "ERREUR DE SYNTAXE ligne %s : %s — corrige-la maintenant." % (e.lineno, e.msg)}
    except ValueError as e:
        return {"verification": "JSON INVALIDE : %s — corrige-le maintenant." % e}
    return {"verification": "syntaxe OK"}


def squelette_projet(projet):
    """Fichiers du projet + liens entre eux (imports Python/JS, src/href HTML) pour le schema squelette."""
    base = chemin_sur(nom_valide(projet))
    fichiers = [os.path.relpath(os.path.join(b, f), base).replace("\\", "/")
                for b, _, fs in marcher(base, limite=400) for f in fs][:150]
    ensemble = set(fichiers)
    par_module = {}
    for f in fichiers:
        sans_ext = os.path.splitext(f)[0]
        par_module.setdefault(sans_ext.replace("/", "."), f)
        par_module.setdefault(os.path.basename(sans_ext), f)
    liens = set()
    for f in fichiers:
        p = os.path.join(base, f)
        ext = os.path.splitext(f)[1].lower()
        if ext not in (".py", ".js", ".mjs", ".ts", ".jsx", ".tsx", ".html", ".htm", ".css", ".vue") or os.path.getsize(p) > 400_000:
            continue
        try:
            with open(p, "r", encoding="utf-8", errors="ignore") as fh:
                texte = fh.read()
        except OSError:
            continue
        cibles = []
        if ext == ".py":
            for m in re.finditer(r"^\s*(?:from\s+([.\w]+)\s+import|import\s+([\w.]+))", texte, re.M):
                mod = (m.group(1) or m.group(2) or "").lstrip(".")
                if mod:
                    cibles.append(par_module.get(mod) or par_module.get(mod.split(".")[-1]))
        else:
            for m in re.finditer(r"""(?:import[^'"\n]*from\s*|import\s*\(?\s*|require\(\s*|src\s*=\s*|href\s*=\s*|@import\s+(?:url\()?)['"]([^'"#?]+)['"]""", texte):
                ref = m.group(1)
                if ref.startswith(("http:", "https:", "//", "data:", "mailto:")):
                    continue
                cand = os.path.normpath(os.path.join(os.path.dirname(f), ref)).replace("\\", "/").lstrip("./")
                for essai in (cand, cand + ".js", cand + ".ts", cand + "/index.js"):
                    if essai in ensemble:
                        cibles.append(essai)
                        break
        for c in cibles:
            if c and c != f:
                liens.add((f, c))
    return {"fichiers": fichiers, "liens": sorted(liens)[:200]}


def liste_sessions(mode=None, recherche=""):
    res = []
    q = (recherche or "").strip().lower()
    for f in os.listdir(SESSIONS):
        if not f.endswith(".json"):
            continue
        try:
            with open(os.path.join(SESSIONS, f), "r", encoding="utf-8") as fh:
                s = json.load(fh)
        except (OSError, ValueError):
            continue
        if mode and s.get("mode") != mode:
            continue
        extrait = ""
        if q:
            trouve = None
            for m in s.get("messages", []):
                t = m.get("content") or ""
                i = t.lower().find(q)
                if i >= 0:
                    trouve = t[max(0, i - 40):i + 80].replace("\n", " ")
                    break
            if trouve is None and q not in (s.get("titre") or "").lower():
                continue
            extrait = trouve or ""
        res.append({"id": s["id"], "mode": s.get("mode"), "titre": s.get("titre") or "Sans titre",
                    "date": s.get("date", 0), "nb": len(s.get("messages", [])), "extrait": extrait,
                    "modele": s.get("modele", ""), "projet": s.get("projet", "")})
    res.sort(key=lambda x: -x["date"])
    return res[:300]


def id_session(ident):
    if not re.fullmatch(r"[a-z0-9-]{8,40}", ident or ""):
        raise ValueError("Conversation invalide")
    return os.path.join(SESSIONS, ident + ".json")


def annuler_changement(ident):
    if not re.fullmatch(r"[0-9-]+[0-9a-f]{6}", ident or ""):
        raise ValueError("Sauvegarde invalide")
    f = os.path.join(SAUVEGARDES, ident + ".json")
    if not os.path.isfile(f):
        raise ValueError("Sauvegarde introuvable (deja annulee ?)")
    with open(f, "r", encoding="utf-8") as fh:
        s = json.load(fh)
    p, rel = chemin_projet(s["projet"], s["chemin"])
    if s["existait"]:
        with open(p, "w", encoding="utf-8", newline="") as fh:
            fh.write(s["contenu"])
    elif os.path.isfile(p):
        corbeille = os.path.join(DONNEES, "corbeille", time.strftime("%Y%m%d-%H%M%S"))
        os.makedirs(corbeille, exist_ok=True)
        shutil.move(p, os.path.join(corbeille, os.path.basename(p)))
    os.remove(f)
    return s["projet"] + "/" + rel


# ---------------------------------------------------------------- appels d'outils ecrits en texte
# Certaines petites IA locales (ex. qwen2.5-coder) ecrivent l'appel en JSON dans leur texte
# au lieu d'un vrai appel d'outil : on les reconnait et on les execute quand meme.
DEBUTS_APPEL = ("{", "<tool_call>", "```json", "```tool", "[{")


def ressemble_appel(texte):
    t = texte.lstrip()
    if not t:
        return None  # pas encore assez de texte pour decider
    for d in DEBUTS_APPEL:
        if t.startswith(d) or d.startswith(t[:len(d)]):
            return True if len(t) >= len(d) or t.startswith(d) else None
    return False


def _fin_objet(texte, i):
    # Fin d'un objet {...} qui commence a i, en tenant compte des chaines simples, doubles et triples
    pile, j, n = 0, i, len(texte)
    while j < n:
        c = texte[j]
        if texte.startswith('"""', j) or texte.startswith("'''", j):
            q = texte[j:j + 3]
            k = texte.find(q, j + 3)
            if k < 0:
                return -1
            j = k + 3
            continue
        if c in "\"'":
            k = j + 1
            while k < n and texte[k] != c:
                k += 2 if texte[k] == "\\" else 1
            j = k + 1
            continue
        if c in "{[":
            pile += 1
        elif c in "}]":
            pile -= 1
            if pile == 0:
                return j + 1
        j += 1
    return -1


def _lire_objet_souple(bout):
    # JSON strict, sinon JSON avec chaines a triples guillemets, sinon dictionnaire Python
    try:
        return json.loads(bout)
    except ValueError:
        pass
    def triple(m):
        contenu = m.group(2)
        if contenu.startswith("\n"):
            contenu = contenu[1:]
        return json.dumps(contenu)
    converti = re.sub(r'(\"\"\"|\'\'\')(.*?)\1', triple, bout, flags=re.S)
    try:
        return json.loads(converti)
    except ValueError:
        pass
    import ast
    try:
        py = re.sub(r"\btrue\b", "True", re.sub(r"\bfalse\b", "False", re.sub(r"\bnull\b", "None", bout)))
        return ast.literal_eval(py)
    except (ValueError, SyntaxError):
        return None


def appels_dans_texte(texte, noms):
    """Retourne (appels, reste_du_texte) au format Ollama."""
    appels, morceaux_gardes = [], []
    dec = json.JSONDecoder()
    i, debut_garde = 0, 0
    while i < len(texte):
        if texte[i] in "{[":
            try:
                obj, fin = dec.raw_decode(texte, i)
            except ValueError:
                fin = _fin_objet(texte, i)
                obj = _lire_objet_souple(texte[i:fin]) if fin > 0 else None
                if obj is None:
                    i += 1
                    continue
            liste = obj if isinstance(obj, list) else [obj]
            trouves = []
            for o in liste:
                if not isinstance(o, dict):
                    continue
                o = o.get("function", o) if isinstance(o.get("function"), dict) else o
                nom = o.get("name")
                args = o.get("arguments", o.get("parameters", {}))
                if isinstance(args, str):
                    try:
                        args = json.loads(args)
                    except ValueError:
                        args = {}
                if nom in noms and isinstance(args, dict):
                    trouves.append({"function": {"name": nom, "arguments": args}})
            if trouves:
                morceaux_gardes.append(texte[debut_garde:i])
                appels += trouves
                i = debut_garde = fin
                continue
        i += 1
    morceaux_gardes.append(texte[debut_garde:])
    reste = "".join(morceaux_gardes)
    reste = re.sub(r"</?tool_call>|```(?:json|tool[_\w]*)?\s*```|```(?:json|tool[_\w]*)\s*$", "", reste).strip()
    return appels, reste


# ---------------------------------------------------------------- invites systeme
INVITE_JASETTE = ("Tu es l'IA locale du Ti-Labo d'Alex, qui roule sur sa cle USB-C. "
                  "Reponds en francais quebecois naturel, clair et direct.")
INVITE_CODEX = ("Tu es le Codex du Ti-Labo, un agent de programmation comme Codex ou Claude Code. Tu ecris du CODE, "
                "pas de la conversation. Regles : 1) pas de schema, pas de diagramme, pas de plan (ca se fait dans la "
                "Jasette) ; 2) pas d'introduction ni de politesse ; 3) quand un projet est ouvert, ecris le code "
                "directement dans les fichiers avec tes outils ; sinon reponds uniquement par des blocs ``` complets "
                "avec le nom du fichier sur la ligne juste avant ; 4) ta seule phrase finale : une ligne qui dit quels "
                "fichiers ont change (ex. « main.py modifie, utils.py cree »). Reponds en francais.")
INVITE_OUTILS = ("\n\nTu as des outils : recherche_web, lire_page, chercher_code_github, chercher_mes_projets. "
                 "Utilise-les seulement si tu en as vraiment besoin (info recente, doc d'une librairie, exemple de code "
                 "que tu ne connais pas). Sinon reponds directement. Quand tu t'en sers, cite les liens a la fin "
                 "sous « Sources : ».")
INVITE_SCHEMA_TOUJOURS = ("\n\nSi on te demande un schema, un diagramme, une carte ou un plan visuel, reponds avec un bloc "
                          "```mermaid valide (flowchart TD, sequenceDiagram, mindmap, classDiagram, timeline...). Le Ti-Labo "
                          "l'anime et peut en faire une video. Mets les textes des boites entre guillemets et commence chaque "
                          "texte par un emoji d'outil de travail qui correspond (🧠 IA, 💾 donnees, ⚙️ moteur, 🛠️ outil, "
                          "📁 fichiers, 💻 code, 🧪 tests, 🔒 securite, 🌐 web, 📱 appli, 🚀 lancement, ✅ fini). Tu peux "
                          "colorer avec classDef.")
INVITE_SCHEMA = ("\n\nMODE SCHEMA : reponds avec UN schema clair dans un bloc ```mermaid (flowchart TD de preference), "
                 "8 a 20 boites, textes courts entre guillemets qui commencent par un emoji d'outil de travail "
                 "(🧠 ⚙️ 🛠️ 💾 📁 💻 🧪 🔒 🌐 📱 🚀 ✅), puis 2 ou 3 phrases d'explication.")
INVITE_PLAN = ("\n\nMODE PLAN : n'ecris PAS encore de code. Fais d'abord un plan court et clair : "
               "1) ce que tu as compris du but, 2) les etapes numerotees, 3) les fichiers a creer ou modifier, "
               "4) les questions ou choix a confirmer. Termine par : « Je code quand tu approuves le plan. »")
INVITE_APPROUVE = ("\n\nLe plan est approuve. Execute-le maintenant etape par etape, avec le code complet de chaque "
                   "fichier dans des blocs ``` (indique le nom du fichier juste avant chaque bloc).")


# ---------------------------------------------------------------- serveur HTTP
class Gestion(BaseHTTPRequestHandler):
    server_version = "TiLabo/1.0"

    def log_message(self, *a):
        pass

    # -- outils de reponse
    def envoyer(self, code, corps, type_="application/json; charset=utf-8"):
        if isinstance(corps, (dict, list)):
            corps = json.dumps(corps, ensure_ascii=False).encode("utf-8")
        elif isinstance(corps, str):
            corps = corps.encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", type_)
        self.send_header("Content-Length", str(len(corps)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        self.wfile.write(corps)

    def erreur(self, message, code=400):
        self.envoyer(code, {"erreur": str(message)})

    def debut_flux(self):
        if getattr(self, "_flux_ouvert", False):
            return
        self._flux_ouvert = True
        self.send_response(200)
        self.send_header("Content-Type", "application/x-ndjson; charset=utf-8")
        self.send_header("Cache-Control", "no-store")
        self.end_headers()

    def ligne(self, obj):
        self.wfile.write((json.dumps(obj, ensure_ascii=False) + "\n").encode("utf-8"))
        self.wfile.flush()

    def corps_json(self):
        n = int(self.headers.get("Content-Length") or 0)
        if n > 20_000_000:
            raise ValueError("Requete trop grosse")
        return json.loads(self.rfile.read(n).decode("utf-8") or "{}") if n else {}

    def hote_ok(self):
        hote = (self.headers.get("Host") or "").split(":")[0]
        return hote in ("127.0.0.1", "localhost")

    def autorise(self):
        return self.hote_ok() and secrets.compare_digest(self.headers.get("X-TiLabo", ""), JETON)

    # -- GET
    def do_GET(self):
        if not self.hote_ok():
            return self.erreur("Hote refuse", 403)
        u = urllib.parse.urlparse(self.path)
        q = {k: v[0] for k, v in urllib.parse.parse_qs(u.query).items()}
        if u.path in ("/", "/index.html"):
            with open(os.path.join(WEB, "index.html"), "r", encoding="utf-8") as f:
                page = f.read().replace("__JETON__", "" if mdp_actif() else JETON)
            return self.envoyer(200, page, "text/html; charset=utf-8")
        if u.path.startswith("/web/"):
            return self.statique(u.path[5:])
        if u.path.startswith("/images/"):
            if not secrets.compare_digest(q.get("t", ""), JETON):
                return self.erreur("Acces refuse", 403)
            nom = os.path.basename(urllib.parse.unquote(u.path[8:]))
            p = os.path.join(IMAGES_SORTIE, nom)
            if not nom.endswith(".png") or not os.path.isfile(p):
                return self.erreur("Introuvable", 404)
            with open(p, "rb") as f:
                return self.envoyer(200, f.read(), "image/png")
        if not u.path.startswith("/api/"):
            return self.erreur("Introuvable", 404)
        if not self.autorise():
            return self.erreur("Acces refuse", 403)
        try:
            route = u.path[5:]
            if route == "etat":
                return self.envoyer(200, {
                    "ollama": ollama_vivant(), "git": bool(git_commande()),
                    "python": sys.version.split()[0], "racine": RACINE,
                    "plateforme": "android" if ANDROID else ("windows" if WINDOWS else "linux"),
                    "lecteur": os.path.splitdrive(RACINE)[0] or "/",
                    "libre_go": round(shutil.disk_usage(RACINE).free / 1e9, 1),
                    "params": params_publics(),
                })
            if route == "projets":
                return self.envoyer(200, liste_projets())
            if route == "arbre":
                return self.envoyer(200, arbre(q.get("projet", "")))
            if route == "fichier":
                return self.envoyer(200, {"chemin": q["chemin"], "contenu": lire_fichier(q["chemin"])})
            if route == "ia/modeles":
                return self.envoyer(200, self.modeles())
            if route == "github/depots":
                return self.envoyer(200, github_depots())
            if route == "stockage":
                return self.envoyer(200, etat_stockage())
            if route == "polices":
                return self.envoyer(200, polices_windows())
            if route == "projet/squelette":
                return self.envoyer(200, squelette_projet(q.get("projet", "")))
            if route == "conversations":
                return self.envoyer(200, liste_sessions(q.get("mode"), q.get("q", "")))
            if route == "schemas":
                return self.envoyer(200, sorted((f[:-4] for f in os.listdir(SCHEMAS) if f.endswith(".mmd")), key=str.lower))
            if route == "images":
                return self.envoyer(200, {"moteur": bool(exe_sd()), "modeles": modeles_images(),
                                          "catalogue": CATALOGUE_IMAGES, "galerie": galerie()})
            return self.erreur("Route inconnue", 404)
        except Exception as e:
            return self.erreur(e)

    def statique(self, rel):
        p = os.path.realpath(os.path.join(WEB, rel))
        if not p.startswith(os.path.realpath(WEB) + os.sep) or not os.path.isfile(p):
            return self.erreur("Introuvable", 404)
        types = {".css": "text/css", ".js": "text/javascript", ".txt": "text/plain; charset=utf-8", ".svg": "image/svg+xml",
                 ".png": "image/png", ".woff2": "font/woff2", ".ico": "image/x-icon"}
        with open(p, "rb") as f:
            self.envoyer(200, f.read(), types.get(os.path.splitext(p)[1], "application/octet-stream"))

    def modeles(self):
        p = lire_params()
        res = []
        try:
            if p["moteur"] == "openai":
                r = http_json(p["url_openai"].rstrip("/") + "/models", delai=5)
                res = [{"nom": m["id"], "taille_go": None} for m in r.get("data", [])]
            else:
                r = http_json(ollama_url("/api/tags"), delai=5)
                res = [{"nom": m["name"], "taille_go": round(m.get("size", 0) / 1e9, 1),
                        "famille": m.get("details", {}).get("family", ""),
                        "params": m.get("details", {}).get("parameter_size", "")} for m in r.get("models", [])]
        except Exception:
            if not cle_claude():
                raise
        if cle_claude():
            res += [{"nom": c["nom"], "taille_go": None, "famille": "claude", "params": c["desc"], "nuage": True}
                    for c in MODELES_CLAUDE]
        return res

    # -- POST
    def do_POST(self):
        route = urllib.parse.urlparse(self.path).path[5:]
        if route == "verrou/entrer" and self.hote_ok():
            try:
                return self.envoyer(200, {"jeton": entrer(self.corps_json().get("mot_de_passe"))})
            except Exception as e:
                return self.erreur(e, 401)
        if not self.autorise():
            return self.erreur("Acces refuse", 403)
        try:
            d = self.corps_json()
            if route == "parametres":
                secrets_ = ("github_token", "anthropic_key", "mot_de_passe")
                ecrire_params({k: v for k, v in d.items() if k not in secrets_})
                return self.envoyer(200, params_publics())
            if route == "verrou/changer":
                return self.envoyer(200, {"actif": changer_mdp(d)})
            if route == "claude/cle":
                cle = (d.get("cle") or "").strip()
                if cle and not cle.startswith("sk-ant-"):
                    raise ValueError("Une cle Claude commence par sk-ant-")
                ecrire_params({"anthropic_key": cle})
                return self.envoyer(200, {"ok": True})
            if route == "images/telecharger":
                return self.operation_flux(telecharger_modele_image, d)
            if route == "images/creer":
                return self.operation_flux(creer_image, d)
            if route == "fichier":
                p = chemin_sur(d["chemin"])
                os.makedirs(os.path.dirname(p), exist_ok=True)
                with open(p, "w", encoding="utf-8", newline="") as f:
                    f.write(d["contenu"])
                return self.envoyer(200, {"ok": True})
            if route == "projet/nouveau":
                nom = nom_valide(d["nom"])
                p = chemin_sur(nom)
                if os.path.exists(p):
                    raise ValueError("Ce projet existe deja")
                os.makedirs(p)
                with open(os.path.join(p, "main.py"), "w", encoding="utf-8") as f:
                    f.write('print("Allo du Ti-Labo !")\n')
                with open(os.path.join(p, "README.md"), "w", encoding="utf-8") as f:
                    f.write("# %s\n\nNouveau projet cree dans le Ti-Labo.\n" % nom)
                return self.envoyer(200, {"nom": nom})
            if route == "projet/supprimer-fichier":
                p = chemin_sur(d["chemin"])
                corbeille = os.path.join(DONNEES, "corbeille", time.strftime("%Y%m%d-%H%M%S"))
                os.makedirs(corbeille, exist_ok=True)
                shutil.move(p, os.path.join(corbeille, os.path.basename(p)))
                return self.envoyer(200, {"ok": True, "corbeille": corbeille})
            if route == "terminal":
                return self.envoyer(200, executer(d.get("projet"), d["commande"]))
            if route == "codex/trouve":
                return self.envoyer(200, trouve_projets(d.get("question", "")))
            if route == "ia/supprimer":
                http_json(ollama_url("/api/delete"), {"model": d["nom"]}, methode="DELETE")
                return self.envoyer(200, {"ok": True})
            if route == "ia/telecharger":
                return self.flux_ollama("/api/pull", {"model": d["nom"], "stream": True}, brut=True)
            if route == "ia/prechauffer":
                # charge le modele en memoire tout de suite (requete vide)
                threading.Thread(target=lambda: http_json(ollama_url("/api/generate"),
                                 {"model": d.get("nom"), "keep_alive": "30m"}, delai=600), daemon=True).start()
                return self.envoyer(200, {"ok": True})
            if route == "ia/demarrer":
                return self.envoyer(200, {"ok": demarrer_ollama(), "version": ollama_vivant()})
            if route == "jasette":
                return self.jasette(d)
            if route == "github/connecter":
                return self.envoyer(200, {"utilisateur": github_connecter(d["jeton"].strip())})
            if route == "github/deconnecter":
                ecrire_params({"github_token": "", "github_utilisateur": ""})
                return self.envoyer(200, {"ok": True})
            if route == "github/cloner":
                return self.envoyer(200, {"nom": cloner(d["url"], d.get("nom"))})
            if route == "memoire/indexer":
                return self.envoyer(200, {"morceaux": indexer(d["projet"])})
            if route == "memoire/chercher":
                return self.envoyer(200, chercher_memoire(d["question"], d.get("projet")))
            if route == "ia/deplacer":
                return self.operation_flux(deplacer_gguf if d.get("type") == "gguf" else deplacer_ollama, d)
            if route == "agent/autoriser":
                repondre_autorisation(d.get("id"), d.get("ok"))
                return self.envoyer(200, {"ok": True})
            if route == "conversations/sauver":
                chemin = id_session(d.get("id"))
                msgs = []
                for m in (d.get("messages") or [])[-200:]:
                    if m.get("role") not in ("user", "assistant"):
                        continue
                    msgs.append({"role": m["role"], "content": str(m.get("content") or "")[:200000],
                                 "images": [x for x in (m.get("images") or []) if isinstance(x, str)][:4]})
                titre = re.sub(r"\s+", " ", str(d.get("titre") or ""))[:80]
                with open(chemin, "w", encoding="utf-8") as f:
                    json.dump({"id": d["id"], "mode": d.get("mode"), "titre": titre, "date": time.time(),
                               "modele": d.get("modele", ""), "projet": d.get("projet") or "", "messages": msgs},
                              f, ensure_ascii=False)
                return self.envoyer(200, {"ok": True})
            if route == "conversations/lire":
                with open(id_session(d.get("id")), "r", encoding="utf-8") as f:
                    return self.envoyer(200, json.load(f))
            if route == "conversations/supprimer":
                chemin = id_session(d.get("id"))
                corbeille = os.path.join(DONNEES, "corbeille", "conversations")
                os.makedirs(corbeille, exist_ok=True)
                shutil.move(chemin, os.path.join(corbeille, os.path.basename(chemin)))
                return self.envoyer(200, {"ok": True})
            if route == "schemas/enregistrer":
                nom = nom_valide(re.sub(r"[^A-Za-z0-9_.\- ]", "-", d.get("nom") or "")[:60].strip("-. ") or "schema")
                with open(os.path.join(SCHEMAS, nom + ".mmd"), "w", encoding="utf-8") as f:
                    f.write(d.get("code") or "")
                imgs = {k: v for k, v in (d.get("images") or {}).items()
                        if re.fullmatch(r"ti-image:\d{1,4}", k) and isinstance(v, str) and v.startswith("data:image/")}
                chemin_imgs = os.path.join(SCHEMAS, nom + ".images.json")
                if imgs:
                    with open(chemin_imgs, "w", encoding="utf-8") as f:
                        json.dump(imgs, f)
                elif os.path.isfile(chemin_imgs):
                    os.remove(chemin_imgs)
                return self.envoyer(200, {"nom": nom})
            if route == "schemas/lire":
                nom = nom_valide(d.get("nom") or "")
                with open(os.path.join(SCHEMAS, nom + ".mmd"), "r", encoding="utf-8") as f:
                    code = f.read()
                imgs = {}
                if os.path.isfile(os.path.join(SCHEMAS, nom + ".images.json")):
                    with open(os.path.join(SCHEMAS, nom + ".images.json"), "r", encoding="utf-8") as f:
                        imgs = json.load(f)
                return self.envoyer(200, {"nom": nom, "code": code, "images": imgs})
            if route == "fichier/annuler":
                return self.envoyer(200, {"chemin": annuler_changement(d.get("sauvegarde"))})
            if route == "ia/renommer":
                return self.envoyer(200, {"nom": renommer_ia(d)})
            if route == "gguf/demarrer":
                return self.envoyer(200, {"modele": demarrer_gguf(d["chemin"])})
            if route == "gguf/arreter":
                arreter_gguf()
                ecrire_params({"moteur": "ollama"})
                return self.envoyer(200, {"ok": True})
            if route == "ouvrir-dossier":
                p = chemin_sur(d.get("chemin", ""))
                if WINDOWS:
                    os.startfile(p if os.path.isdir(p) else os.path.dirname(p))
                return self.envoyer(200, {"ok": True})
            return self.erreur("Route inconnue", 404)
        except urllib.error.URLError as e:
            return self.erreur("Moteur d'IA injoignable (%s). Est-il demarre ?" % getattr(e, "reason", e))
        except Exception as e:
            return self.erreur(e)

    # -- flux
    def operation_flux(self, fonction, d):
        self.debut_flux()
        dernier = [0.0]

        def envoyer(o):
            if "fait" in o and o["fait"] < o["total"] and time.time() - dernier[0] < 0.3:
                return
            dernier[0] = time.time()
            self.ligne(o)
        try:
            fonction(d, envoyer)
        except Exception as e:
            self.ligne({"erreur": str(e)})

    def flux_ollama(self, chemin, donnees, brut=False):
        req = urllib.request.Request(ollama_url(chemin), data=json.dumps(donnees).encode(),
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=3600) as r:
            self.debut_flux()
            for l in r:
                if l.strip():
                    self.wfile.write(l if l.endswith(b"\n") else l + b"\n")
                    self.wfile.flush()

    def jasette(self, d):
        p = lire_params()
        mode = d.get("mode", "jasette")
        modele = d.get("modele") or p["modele_codex" if mode == "codex" else "modele_jasette"]
        if not modele:
            raise ValueError("Choisis un modele d'IA (IA locales sur la cle)")
        systeme = INVITE_CODEX if mode == "codex" else INVITE_JASETTE
        internet = bool(d.get("internet")) and bool(p.get("internet"))
        outils = []
        if internet:
            systeme += INVITE_OUTILS
            outils += OUTILS_IA
        projet = d.get("projet")
        if mode == "codex" and d.get("ecriture") and projet:
            try:
                chemin_sur(nom_valide(projet))
            except ValueError:
                projet = None
        else:
            projet = None
        if projet:
            outils += OUTILS_FICHIERS_LECTURE
            if d.get("plan") == "planifier":
                systeme += INVITE_FICHIERS_LECTURE % projet
            else:
                outils += OUTILS_FICHIERS_ECRITURE
                systeme += INVITE_FICHIERS % projet
            # La carte du projet des le depart : le Codex choisit le bon fichier au lieu de deviner
            liste = outil_fichier("lister_fichiers", {}, projet)
            if isinstance(liste, list) and liste:
                systeme += ("\n\nFichiers du projet « %s » (%d) :\n" % (projet, len(liste))
                            + "\n".join("- " + f for f in liste[:200])
                            + ("\n- … (%d autres)" % (len(liste) - 200) if len(liste) > 200 else "")
                            + "\nModifie le fichier existant qui correspond a la demande ; cree un nouveau fichier "
                              "seulement s'il n'en existe aucun qui convient. Utilise toujours ces chemins exacts.")
            if (d.get("fichier") or "").startswith(projet + "/"):
                systeme += "\nLe fichier ouvert dans l'editeur est « %s » : c'est probablement celui dont on parle." % d["fichier"][len(projet) + 1:]
        self._projet = projet
        self._lus, self._double_ok, self._nouveau_ok, self._relances = set(), {}, False, 0
        f_ouvert = d.get("fichier") or ""
        self._fichier_ouvert = f_ouvert[len(projet) + 1:] if projet and f_ouvert.startswith(projet + "/") else None
        self._tours = 6
        self._turbo = bool(d.get("turbo")) and mode == "codex"
        if self._turbo and projet:
            d["agent"] = True
        if projet and d.get("agent") and d.get("plan") != "planifier":
            outils += OUTILS_COMMANDE
            systeme += INVITE_AGENT
            self._tours = 40 if self._turbo else 30
            if self._turbo:
                systeme += INVITE_TURBO
        if mode == "codex":
            d["plan"] = None if d.get("plan") != "executer" else "executer"
        else:
            systeme += INVITE_SCHEMA if d.get("schema") else INVITE_SCHEMA_TOUJOURS
            if d.get("schema_actuel"):
                systeme += ("\n\nSCHEMA OUVERT dans le panneau a droite :\n```mermaid\n%s\n```\nSi la demande concerne "
                            "ce schema (ajouter, enlever, renommer, changer la disposition ou les couleurs), renvoie le "
                            "schema COMPLET modifie dans un seul bloc ```mermaid, puis une phrase sur ce qui a change."
                            % d["schema_actuel"][:8000])
        if d.get("plan") == "planifier":
            systeme += INVITE_PLAN
        elif d.get("plan") == "executer":
            systeme += INVITE_APPROUVE
        contexte = []
        if d.get("fichier"):
            try:
                contexte.append("Fichier ouvert « %s » :\n```\n%s\n```" % (d["fichier"], lire_fichier(d["fichier"])[:24000]))
            except ValueError:
                pass
        sources = []
        if d.get("memoire"):
            question = next((m["content"] for m in reversed(d["messages"]) if m["role"] == "user"), "")
            try:
                trouves = chercher_memoire(question, d.get("projet"), k=5)
                if trouves:
                    sources = sorted({t["fichier"] for t in trouves})
                    contexte.append("Extraits de la memoire de la cle :\n" + "\n\n".join(
                        "[%s]\n%s" % (t["fichier"], t["texte"]) for t in trouves))
            except Exception:
                pass
        if contexte:
            systeme += "\n\n" + "\n\n".join(contexte)

        # Les images ne sont gardees que sur les 4 derniers messages (economise la memoire du modele)
        hist = d["messages"][-30:]
        messages = [{"role": "system", "content": systeme}]
        for i, m in enumerate(hist):
            msg = {"role": m["role"], "content": m.get("content", "")}
            imgs = [x.split(",", 1)[-1] for x in (m.get("images") or [])][:4]
            if imgs and i >= len(hist) - 4:
                msg["images"] = imgs
            messages.append(msg)

        # Images : si l'IA choisie ne voit pas, une IA « vision » de la cle les decrit pour elle
        if any(m.get("images") for m in messages) and not modele.startswith("claude-") and not voit_images(modele, p):
            self.relais_vision(messages, p)
        if d.get("images_ids") and mode != "codex":
            ids = [i for i in d["images_ids"] if re.fullmatch(r"ti-image:\d{1,4}", str(i))][:8]
            if ids:
                messages[0]["content"] += (
                    "\n\nIMAGES DE L'UTILISATEUR utilisables dans un schema : %s. Pour mettre une image dans un "
                    "schema flowchart, cree un noeud comme ceci (copie exactement) :\n"
                    "IMG1@{ img: \"%s\", label: \"texte court\", pos: \"b\", w: 140, h: 140, constraint: \"on\" }\n"
                    "puis relie-le avec des fleches comme une boite normale." % (", ".join(ids), ids[-1]))

        if modele.startswith("claude-"):
            return self.jasette_claude(modele, messages, sources, outils)
        if p["moteur"] == "openai":
            return self.jasette_openai(p, modele, messages, sources, outils)
        return self.jasette_ollama(modele, messages, sources, outils)

    def executer_outil(self, nom, args):
        if not isinstance(args, dict):
            args = {}
        garde = self.garde_fichier(nom, args)
        if garde:
            self.ligne({"outil": garde["court"]})
            return {"erreur": garde["message"]}
        if nom == "executer_commande":
            cmd = (args.get("commande") or "").strip()
            projet = getattr(self, "_projet", None)
            if not cmd or not projet:
                return {"erreur": "Commande vide ou aucun projet ouvert"}
            ident = secrets.token_hex(8)
            auto = getattr(self, "_turbo", False) and commande_sans_danger(cmd)
            if auto:
                self.ligne({"outil": "Turbo : lance %s" % cmd})
            else:
                self.ligne({"autorisation": {"id": ident, "commande": cmd, "projet": projet}})
            if not auto and not attendre_autorisation(ident):
                self.ligne({"commande": {"commande": cmd, "refusee": True}})
                return {"refuse": True, "message": "L'utilisateur a refuse cette commande."}
            res = executer(projet, cmd)
            self.ligne({"commande": {"commande": cmd, "code": res["code"], "sortie": res["sortie"][-8000:]}})
            return {"code_sortie": res["code"], "sortie": res["sortie"][-12000:]}
        res = appeler_outil(nom, args, getattr(self, "_projet", None))
        if isinstance(res, dict) and res.get("ok") and nom in ("ecrire_fichier", "modifier_fichier"):
            try:
                self.__dict__.setdefault("_lus", set()).add(chemin_projet(self._projet, args.get("chemin"))[1])
            except (ValueError, TypeError):
                pass
        if isinstance(res, dict) and res.get("_evenement"):
            self.ligne({"fichier": res.pop("_evenement")})
        return res

    def garde_fichier(self, nom, args):
        """Garde-fous pour toujours ecrire dans le BON fichier."""
        projet = getattr(self, "_projet", None)
        if not projet or nom not in ("ecrire_fichier", "modifier_fichier", "lire_fichier"):
            return None
        try:
            p, rel = chemin_projet(projet, args.get("chemin"))
        except ValueError as e:
            return {"court": "Chemin refusé", "message": str(e)}
        lus = self.__dict__.setdefault("_lus", set())
        if nom == "lire_fichier":
            if os.path.isfile(p):
                lus.add(rel)
            return None
        existe = os.path.isfile(p)
        # 1. jamais modifier ou ecraser un fichier sans l'avoir lu dans cette demande
        if existe and rel not in lus:
            return {"court": "Doit lire %s avant d'y écrire" % rel,
                    "message": "Tu n'as pas encore lu « %s ». Lis-le d'abord avec lire_fichier, puis refais ta "
                               "modification au bon endroit." % rel}
        # 2. nouveau fichier : verifier qu'on ne cree pas un double d'un fichier existant
        if nom == "ecrire_fichier" and not existe:
            base = chemin_sur(projet)
            tous = [os.path.relpath(os.path.join(b, f), base).replace("\\", "/")
                    for b, _, fs in marcher(base, limite=800) for f in fs]
            meme_nom = [t for t in tous if os.path.basename(t).lower() == os.path.basename(rel).lower()]
            if meme_nom and not getattr(self, "_double_ok", {}).get(rel):
                self.__dict__.setdefault("_double_ok", {})[rel] = True
                return {"court": "Vérifie le bon fichier : %s existe déjà" % meme_nom[0],
                        "message": "Attention : « %s » existe deja dans le projet. Si c'est ce fichier qu'il faut changer, "
                                   "lis-le et modifie-le au lieu d'en creer un nouveau a « %s ». Si tu veux vraiment un "
                                   "nouveau fichier a cet endroit, refais exactement le meme appel." % (meme_nom[0], rel)}
            ouvert = getattr(self, "_fichier_ouvert", None)
            if ouvert and os.path.splitext(ouvert)[1] == os.path.splitext(rel)[1] and not getattr(self, "_nouveau_ok", False):
                self._nouveau_ok = True
                return {"court": "Vérifie : le fichier ouvert est %s" % ouvert,
                        "message": "Le fichier ouvert par l'utilisateur est « %s ». Si la demande le concerne, modifie-le "
                                   "au lieu de creer « %s ». Si un nouveau fichier est vraiment voulu, refais le meme appel."
                                   % (ouvert, rel)}
        return None

    def relais_vision(self, messages, p):
        vision = choisir_modele_vision(p)
        self.debut_flux()
        if not vision:
            self.ligne({"outil": "Cette IA ne voit pas les images — télécharge gemma3:4b (vision) dans IA locales"})
            for m in messages:
                if m.pop("images", None):
                    m["content"] += "\n[Une image etait jointe, mais aucune IA capable de voir les images n'est installee.]"
            return
        for m in messages:
            imgs = m.pop("images", None)
            if not imgs:
                continue
            self.ligne({"outil": "Vision : %s regarde l'image" % vision})
            try:
                r = http_json(ollama_url("/api/chat"), {
                    "model": vision, "stream": False, "keep_alive": "30m",
                    "messages": [{"role": "user", "images": imgs, "content":
                                  "Decris cette image en francais, de facon precise et utile : sujet, objets, texte "
                                  "visible (recopie-le), couleurs, style, disposition. 4 a 10 lignes, sans introduction."}]},
                    delai=600)
                desc = (r.get("message") or {}).get("content", "").strip()
            except Exception as e:
                desc = "(impossible de decrire l'image : %s)" % e
            m["content"] += "\n\n[Image jointe — description faite par %s :\n%s]" % (vision, desc)

    def options_ollama(self):
        # keep_alive : le modele reste en memoire -> plus de chargement de 10 a 60 s entre deux messages
        o = {"keep_alive": "30m"}
        if getattr(self, "_turbo", False):
            o["options"] = {"num_ctx": 16384, "temperature": 0.2, "top_p": 0.9}
        return o

    # -- Ollama : boucle d'outils puis reponse en flux
    def jasette_ollama(self, modele, messages, sources, outils):
        self.debut_flux()
        if sources:
            self.ligne({"sources": sources})
        if outils:
            # Ecriture en direct meme avec les outils : on lit le flux, on affiche le texte
            # au fur et a mesure et on ramasse les appels d'outils au passage.
            for _ in range(getattr(self, "_tours", 6)):
                req = urllib.request.Request(ollama_url("/api/chat"), headers={"Content-Type": "application/json"},
                                             data=json.dumps({"model": modele, "messages": messages,
                                                              "tools": outils, "stream": True,
                                                              **self.options_ollama()}).encode())
                try:
                    r = urllib.request.urlopen(req, timeout=3600)
                except urllib.error.HTTPError as e:
                    corps = e.read().decode("utf-8", "ignore")
                    if "does not support tools" in corps:
                        self.ligne({"outil": "Ce modele ne sait pas utiliser les outils (Internet, fichiers) — reponse simple"})
                        break
                    self.ligne({"erreur": corps or str(e)})
                    return
                texte, appels, stats = "", [], {}
                retenu = None  # None = on ne sait pas encore, True = on retient (appel ecrit en texte ?), False = on affiche
                noms = {o["function"]["name"] for o in outils}
                with r:
                    for l in r:
                        if not l.strip():
                            continue
                        try:
                            o = json.loads(l)
                        except ValueError:
                            continue
                        if o.get("error"):
                            self.ligne({"erreur": o["error"]})
                            return
                        m = o.get("message", {})
                        if m.get("content"):
                            texte += m["content"]
                            if retenu is None:
                                retenu = ressemble_appel(texte)
                                if retenu is False:
                                    self.ligne({"texte": texte})
                            elif retenu is False:
                                self.ligne({"texte": m["content"]})
                        appels += m.get("tool_calls") or []
                        if o.get("done"):
                            stats = o
                if not appels and retenu is not False:
                    appels, reste = appels_dans_texte(texte, noms)
                    if appels:
                        texte = reste
                    if reste and (not appels or retenu is None):
                        self.ligne({"texte": reste})
                elif retenu is None and texte:
                    self.ligne({"texte": texte})
                if not appels and re.search(r'"name"\s*:\s*"(%s)"' % "|".join(map(re.escape, noms)), texte) \
                        and getattr(self, "_relances", 0) < 2:
                    # Appel d'outil mal ecrit : on le dit a l'IA pour qu'elle recommence
                    self._relances = getattr(self, "_relances", 0) + 1
                    self.ligne({"outil": "Appel mal formé — le Codex recommence"})
                    messages.append({"role": "assistant", "content": texte})
                    messages.append({"role": "user", "content":
                        "Ton appel d'outil n'etait pas du JSON valide : rien n'a ete ecrit. Recommence avec un vrai "
                        "appel d'outil. Dans le JSON, le contenu est UNE chaine entre guillemets doubles avec \\n pour "
                        "les retours de ligne (jamais de triple guillemets). Ne dis pas que c'est fait avant que ce soit fait."})
                    continue
                if not appels:
                    self.ligne({"fini": True, "jetons_s": round(stats.get("eval_count", 0) / max(stats.get("eval_duration", 1) / 1e9, 0.001), 1)})
                    return
                messages.append({"role": "assistant", "content": texte, "tool_calls": appels})
                for a in appels:
                    f = a.get("function", {})
                    args = f.get("arguments") or {}
                    if isinstance(args, str):
                        try:
                            args = json.loads(args)
                        except ValueError:
                            args = {}
                    self.ligne({"outil": etiquette_outil(f.get("name"), args)})
                    res = self.executer_outil(f.get("name"), args)
                    if f.get("name") in ("recherche_web", "chercher_code_github") and isinstance(res, list):
                        self.ligne({"liens": [x.get("url") for x in res[:5] if x.get("url")]})
                    messages.append({"role": "tool", "content": json.dumps(res, ensure_ascii=False)[:14000],
                                     "tool_name": f.get("name")})
                if texte:
                    self.ligne({"texte": "\n\n"})
        req = urllib.request.Request(ollama_url("/api/chat"),
                                     data=json.dumps({"model": modele, "messages": messages, "stream": True,
                                                      **self.options_ollama()}).encode(),
                                     headers={"Content-Type": "application/json"})
        try:
            r = urllib.request.urlopen(req, timeout=3600)
        except urllib.error.HTTPError as e:
            self.ligne({"erreur": e.read().decode("utf-8", "ignore") or str(e)})
            return
        with r:
            for l in r:
                if not l.strip():
                    continue
                try:
                    o = json.loads(l)
                except ValueError:
                    continue
                if o.get("error"):
                    self.ligne({"erreur": o["error"]})
                    break
                t = o.get("message", {}).get("content", "")
                if t:
                    self.ligne({"texte": t})
                if o.get("done"):
                    self.ligne({"fini": True, "jetons_s": round(o.get("eval_count", 0) / max(o.get("eval_duration", 1) / 1e9, 0.001), 1)})

    # -- Claude (API Anthropic) : outils au besoin, puis reponse en flux
    def jasette_claude(self, modele, messages, sources, outils):
        if not cle_claude():
            raise ValueError("Ajoute ta cle API Claude dans Parametres")
        systeme, conv = messages_claude(messages)
        self.debut_flux()
        if sources:
            self.ligne({"sources": sources})
        try:
            for _ in range(max(8, getattr(self, "_tours", 8))):
                corps = {"model": modele, "max_tokens": 16000, "system": systeme, "messages": conv, "stream": True}
                if outils:
                    corps["tools"] = outils_claude(outils)
                blocs, en_cours, arret = [], None, None
                with requete_claude(corps) as r:
                    for l in r:
                        l = l.decode("utf-8", "ignore").strip()
                        if not l.startswith("data:"):
                            continue
                        try:
                            e = json.loads(l[5:])
                        except ValueError:
                            continue
                        t = e.get("type")
                        if t == "content_block_start":
                            en_cours = dict(e["content_block"])
                            if en_cours.get("type") == "tool_use":
                                en_cours["_json"] = ""
                            elif en_cours.get("type") == "text":
                                en_cours["text"] = ""
                        elif t == "content_block_delta" and en_cours is not None:
                            dl = e["delta"]
                            if dl.get("type") == "text_delta":
                                en_cours["text"] += dl["text"]
                                self.ligne({"texte": dl["text"]})
                            elif dl.get("type") == "input_json_delta":
                                en_cours["_json"] += dl.get("partial_json", "")
                        elif t == "content_block_stop" and en_cours is not None:
                            if en_cours.get("type") == "tool_use":
                                try:
                                    en_cours["input"] = json.loads(en_cours.pop("_json") or "{}")
                                except ValueError:
                                    en_cours["input"] = {}
                            if en_cours.get("type") in ("text", "tool_use"):
                                blocs.append(en_cours)
                            en_cours = None
                        elif t == "message_delta":
                            arret = e.get("delta", {}).get("stop_reason")
                        elif t == "error":
                            self.ligne({"erreur": e.get("error", {}).get("message", "erreur Claude")})
                            return
                if arret != "tool_use":
                    break
                conv.append({"role": "assistant", "content": blocs})
                resultats = []
                for b in blocs:
                    if b.get("type") != "tool_use":
                        continue
                    self.ligne({"outil": etiquette_outil(b["name"], b["input"])})
                    res = self.executer_outil(b["name"], b["input"])
                    if b["name"] in ("recherche_web", "chercher_code_github") and isinstance(res, list):
                        self.ligne({"liens": [x.get("url") for x in res[:5] if x.get("url")]})
                    resultats.append({"type": "tool_result", "tool_use_id": b["id"],
                                      "content": json.dumps(res, ensure_ascii=False)[:14000]})
                conv.append({"role": "user", "content": resultats})
                self.ligne({"texte": "\n\n"})
        except ValueError as e:
            self.ligne({"erreur": str(e)})
            return
        self.ligne({"fini": True})

    # -- Moteur compatible OpenAI (LM Studio, llama.cpp server, Jan…)
    def jasette_openai(self, p, modele, messages, sources, outils):
        base = p["url_openai"].rstrip("/")
        conv = []
        for m in messages:
            if m.get("images"):
                contenu = [{"type": "text", "text": m["content"]}] + [
                    {"type": "image_url", "image_url": {"url": "data:image/png;base64," + b}} for b in m["images"]]
                conv.append({"role": m["role"], "content": contenu})
            else:
                conv.append({"role": m["role"], "content": m["content"]})
        self.debut_flux()
        if sources:
            self.ligne({"sources": sources})
        if outils:
            for _ in range(getattr(self, "_tours", 6)):
                try:
                    r = http_json(base + "/chat/completions", {"model": modele, "messages": conv,
                                                               "tools": outils, "stream": False}, delai=1800)
                except urllib.error.HTTPError:
                    self.ligne({"outil": "Ce moteur refuse les outils — reponse sans recherche"})
                    break
                msg = r["choices"][0]["message"]
                appels = msg.get("tool_calls") or []
                if not appels:
                    texte = msg.get("content") or ""
                    for i in range(0, len(texte), 40):
                        self.ligne({"texte": texte[i:i + 40]})
                    self.ligne({"fini": True})
                    return
                conv.append({"role": "assistant", "content": msg.get("content") or "", "tool_calls": appels})
                for a in appels:
                    f = a["function"]
                    try:
                        args = json.loads(f.get("arguments") or "{}")
                    except ValueError:
                        args = {}
                    self.ligne({"outil": etiquette_outil(f["name"], args)})
                    res = self.executer_outil(f["name"], args)
                    conv.append({"role": "tool", "tool_call_id": a.get("id", ""),
                                 "content": json.dumps(res, ensure_ascii=False)[:14000]})
        req = urllib.request.Request(base + "/chat/completions",
                                     data=json.dumps({"model": modele, "messages": conv, "stream": True}).encode(),
                                     headers={"Content-Type": "application/json"})
        with urllib.request.urlopen(req, timeout=3600) as r:
            for l in r:
                l = l.decode("utf-8", errors="ignore").strip()
                if not l.startswith("data:"):
                    continue
                l = l[5:].strip()
                if l == "[DONE]":
                    break
                try:
                    delta = json.loads(l)["choices"][0]["delta"].get("content") or ""
                except (ValueError, KeyError, IndexError):
                    continue
                if delta:
                    self.ligne({"texte": delta})
        self.ligne({"fini": True})


# ---------------------------------------------------------------- telephone <-> cle USB-C : deplacer les IA
import hashlib

PORT_GGUF = 8081
_proc_gguf = None
_gguf_actif = {"chemin": None, "appli": False}


def dossier_ollama_local():
    return os.environ.get("OLLAMA_MODELS") or os.path.join(os.path.expanduser("~"), ".ollama", "models")


def dossiers_gguf_telephone():
    """Ou CodexLocal range ses .gguf (dossier partage), plus un dossier du Ti-Labo."""
    cands = []
    if ANDROID:
        cands += ["/storage/emulated/0/CodexLocal/models", "/sdcard/CodexLocal/models",
                  "/storage/emulated/0/Download"]
    cands.append(os.path.join(RACINE, "ia", "gguf"))
    vus, res = set(), []
    for c in cands:
        r = os.path.realpath(c)
        if r not in vus and os.path.isdir(c):
            vus.add(r)
            res.append(c)
    return res


def cles_usb():
    """Les cles USB branchees (Android : /storage/XXXX-XXXX ; Linux : /media)."""
    res = []
    if ANDROID:
        try:
            for n in sorted(os.listdir("/storage")):
                p = os.path.join("/storage", n)
                if n in ("emulated", "self") or not os.path.isdir(p):
                    continue
                if os.access(p, os.R_OK):
                    res.append(p)
        except OSError:
            pass
    elif not WINDOWS:
        for base in ("/media", "/run/media"):
            if os.path.isdir(base):
                for u in os.listdir(base):
                    pu = os.path.join(base, u)
                    if os.path.isdir(pu):
                        res += [os.path.join(pu, k) for k in os.listdir(pu) if os.path.isdir(os.path.join(pu, k))]
    return res


def ecriture_possible(dossier):
    try:
        os.makedirs(dossier, exist_ok=True)
        t = os.path.join(dossier, ".tilabo-test")
        with open(t, "w") as f:
            f.write("ok")
        os.remove(t)
        return True
    except OSError:
        return False


def dossier_ia_cle(cle):
    """Prefere <cle>/TiLabo/ia (le meme dossier que le Ti-Labo Windows).
    Si Android refuse l'ecriture a la racine, on prend le dossier de Termux sur la cle."""
    pref = os.path.join(cle, "TiLabo", "ia")
    if ecriture_possible(pref):
        return pref, False
    repli = os.path.join(cle, "Android", "data", "com.termux", "files", "TiLabo", "ia")
    if ecriture_possible(repli):
        return repli, True
    raise ValueError("Impossible d'ecrire sur la cle %s. Dans Termux : termux-setup-storage, "
                     "puis accorde « Acces a tous les fichiers »." % cle)


def libre_go(p):
    try:
        return round(shutil.disk_usage(p).free / 1e9, 1)
    except OSError:
        return None


def lister_gguf(dossier):
    try:
        return sorted(({"nom": f, "chemin": os.path.join(dossier, f),
                        "taille_go": round(os.path.getsize(os.path.join(dossier, f)) / 1e9, 2)}
                       for f in os.listdir(dossier) if f.lower().endswith(".gguf")), key=lambda x: x["nom"].lower())
    except OSError:
        return []


def chemin_manifeste(base, nom):
    """qwen3:8b -> manifests/registry.ollama.ai/library/qwen3/8b"""
    modele, _, tag = nom.partition(":")
    tag = tag or "latest"
    parts = modele.split("/")
    if "." in parts[0] and len(parts) > 1:
        hote, reste = parts[0], parts[1:]
    else:
        hote, reste = "registry.ollama.ai", parts
    if len(reste) == 1:
        reste = ["library"] + reste
    for seg in [hote] + reste + [tag]:
        if seg in ("", ".", "..") or "\\" in seg:
            raise ValueError("Nom de modele invalide")
    return os.path.join(base, "manifests", hote, *reste, tag)


def lister_ollama_dossier(base):
    res = []
    racine = os.path.join(base, "manifests")
    if not os.path.isdir(racine):
        return res
    for d, _, fichiers in os.walk(racine):
        for tag in fichiers:
            rel = os.path.relpath(os.path.join(d, tag), racine).replace("\\", "/").split("/")
            if len(rel) < 3:
                continue
            hote, chemin, tag = rel[0], rel[1:-1], rel[-1]
            if hote == "registry.ollama.ai" and chemin[0] == "library":
                chemin = chemin[1:]
            nom = ("" if hote == "registry.ollama.ai" else hote + "/") + "/".join(chemin) + ":" + tag
            try:
                with open(os.path.join(d, tag), "r", encoding="utf-8") as f:
                    m = json.load(f)
                taille = sum(c.get("size", 0) for c in m.get("layers", [])) + m.get("config", {}).get("size", 0)
            except (OSError, ValueError):
                continue
            res.append({"nom": nom, "taille_go": round(taille / 1e9, 2)})
    return sorted(res, key=lambda x: x["nom"])


def etat_stockage():
    tel_gguf = []
    for d in dossiers_gguf_telephone():
        for g in lister_gguf(d):
            g["dossier"] = d
            tel_gguf.append(g)
    cles = []
    for c in cles_usb():
        info = {"chemin": c, "libre_go": libre_go(c), "gguf": [], "ollama": [], "erreur": None}
        try:
            ia, limite = dossier_ia_cle(c)
            info.update({"ia": ia, "limite": limite,
                         "gguf": lister_gguf(os.path.join(ia, "gguf")),
                         "ollama": lister_ollama_dossier(os.path.join(ia, "modeles"))})
        except ValueError as e:
            info["erreur"] = str(e)
        cles.append(info)
    return {"plateforme": "android" if ANDROID else ("windows" if WINDOWS else "linux"),
            "tel_libre_go": libre_go(os.path.expanduser("~")),
            "dossiers_gguf": dossiers_gguf_telephone(),
            "dossier_codexlocal_ok": any("CodexLocal" in d for d in dossiers_gguf_telephone()),
            "tel_gguf": tel_gguf, "cles": cles, "gguf_actif": _gguf_actif["chemin"],
            "importes": [m["nom"] for m in (lister_ollama_dossier(MODELES) if not ANDROID else [])]}


def copier_verifie(src, dst, progres, attendu_sha=None):
    """Copie par blocs avec empreinte SHA-256, puis relit la copie pour verifier."""
    taille = os.path.getsize(src)
    if libre_go(os.path.dirname(dst)) is not None and shutil.disk_usage(os.path.dirname(dst)).free < taille + 50_000_000:
        raise ValueError("Pas assez de place a destination (%s Go requis)" % round(taille / 1e9, 1))
    tmp = dst + ".part"
    h = hashlib.sha256()
    fait = 0
    with open(src, "rb") as fi, open(tmp, "wb") as fo:
        while True:
            b = fi.read(8 * 1024 * 1024)
            if not b:
                break
            fo.write(b)
            h.update(b)
            fait += len(b)
            progres(fait, taille)
        fo.flush()
        os.fsync(fo.fileno())
    sha = h.hexdigest()
    if attendu_sha and sha != attendu_sha:
        os.remove(tmp)
        raise ValueError("Le fichier source est abime (empreinte differente) : rien n'a ete efface")
    h2 = hashlib.sha256()
    with open(tmp, "rb") as f:
        for b in iter(lambda: f.read(8 * 1024 * 1024), b""):
            h2.update(b)
    if h2.hexdigest() != sha:
        os.remove(tmp)
        raise ValueError("La copie est abimee (la cle a peut-etre ete debranchee) : rien n'a ete efface")
    os.replace(tmp, dst)
    return sha


def chemin_gguf_autorise(chemin):
    """Seulement les .gguf des dossiers connus du telephone ou des cles."""
    r = os.path.realpath(chemin)
    if not r.lower().endswith(".gguf") or not os.path.isfile(r):
        raise ValueError("Fichier .gguf introuvable")
    permis = [os.path.realpath(d) for d in dossiers_gguf_telephone()] + [os.path.realpath(c) for c in cles_usb()]
    if not any(r.startswith(p + os.sep) for p in permis):
        raise ValueError("Ce fichier n'est pas dans un dossier d'IA connu")
    return r


def cle_autorisee(cle):
    r = os.path.realpath(cle)
    if r not in [os.path.realpath(c) for c in cles_usb()]:
        raise ValueError("Cle USB introuvable (debranchee ?)")
    return r


def deplacer_gguf(d, envoyer):
    src = chemin_gguf_autorise(d["chemin"])
    garder = bool(d.get("garder"))
    if d["sens"] == "vers_cle":
        ia, _ = dossier_ia_cle(cle_autorisee(d["cle"]))
        dest_dir = os.path.join(ia, "gguf")
    else:
        tel = [x for x in dossiers_gguf_telephone() if "CodexLocal" in x] or [os.path.join(RACINE, "ia", "gguf")]
        dest_dir = tel[0]
    os.makedirs(dest_dir, exist_ok=True)
    dst = os.path.join(dest_dir, os.path.basename(src))
    if os.path.exists(dst):
        raise ValueError("« %s » existe deja a destination" % os.path.basename(src))
    if _gguf_actif["chemin"] == src and not garder:
        arreter_gguf()
    envoyer({"etape": "Copie de %s" % os.path.basename(src)})
    copier_verifie(src, dst, lambda f, t: envoyer({"fait": f, "total": t}))
    if not garder:
        os.remove(src)
    envoyer({"fini": True, "message": ("Copie" if garder else "Deplace") + " et verifie : " + os.path.basename(src)})


def deplacer_ollama(d, envoyer):
    nom = d["nom"]
    garder = bool(d.get("garder"))
    ia, _ = dossier_ia_cle(cle_autorisee(d["cle"]))
    cle_base = os.path.join(ia, "modeles")
    tel_base = dossier_ollama_local()
    src_base, dst_base = (tel_base, cle_base) if d["sens"] == "vers_cle" else (cle_base, tel_base)
    man_src = chemin_manifeste(src_base, nom)
    if not os.path.isfile(man_src):
        raise ValueError("Modele introuvable : " + nom)
    with open(man_src, "r", encoding="utf-8") as f:
        manifeste = json.load(f)
    couches = [manifeste["config"]] + manifeste.get("layers", [])
    for i, c in enumerate(couches):
        nom_blob = c["digest"].replace(":", "-")
        bsrc = os.path.join(src_base, "blobs", nom_blob)
        bdst = os.path.join(dst_base, "blobs", nom_blob)
        os.makedirs(os.path.dirname(bdst), exist_ok=True)
        if os.path.isfile(bdst) and os.path.getsize(bdst) == c.get("size"):
            continue
        envoyer({"etape": "Morceau %d/%d" % (i + 1, len(couches))})
        copier_verifie(bsrc, bdst, lambda f, t: envoyer({"fait": f, "total": t}),
                       attendu_sha=c["digest"].split(":", 1)[1])
    man_dst = chemin_manifeste(dst_base, nom)
    os.makedirs(os.path.dirname(man_dst), exist_ok=True)
    shutil.copyfile(man_src, man_dst)
    if not garder:
        if d["sens"] == "vers_cle":
            http_json(ollama_url("/api/delete"), {"model": nom}, methode="DELETE")
        else:
            # Sur la cle : on retire le manifeste et les morceaux qu'aucun autre modele n'utilise
            os.remove(man_src)
            utilises = set()
            for m in lister_ollama_dossier(src_base):
                with open(chemin_manifeste(src_base, m["nom"]), "r", encoding="utf-8") as f:
                    mm = json.load(f)
                utilises |= {x["digest"] for x in [mm["config"]] + mm.get("layers", [])}
            for c in couches:
                if c["digest"] not in utilises:
                    try:
                        os.remove(os.path.join(src_base, "blobs", c["digest"].replace(":", "-")))
                    except OSError:
                        pass
    envoyer({"fini": True, "message": ("Copie" if garder else "Deplace") + " et verifie : " + nom})


def moteur_appli():
    """Etat du moteur gguf de l'appli Android Ti-Labo (127.0.0.1:8081), ou None si elle ne roule pas."""
    if not ANDROID:
        return None
    try:
        with urllib.request.urlopen("http://127.0.0.1:%d/tilabo/etat" % PORT_GGUF, timeout=2) as r:
            d = json.loads(r.read().decode("utf-8"))
            return d if d.get("app") == "Ti-Labo" else None
    except Exception:
        return None


def arreter_gguf():
    global _proc_gguf
    if _gguf_actif.get("appli"):
        try:
            http_json("http://127.0.0.1:%d/tilabo/decharger" % PORT_GGUF, {}, delai=60)
        except Exception:
            pass
        _gguf_actif["appli"] = False
    if _proc_gguf and _proc_gguf.poll() is None:
        _proc_gguf.terminate()
        try:
            _proc_gguf.wait(5)
        except subprocess.TimeoutExpired:
            _proc_gguf.kill()
    _proc_gguf = None
    _gguf_actif["chemin"] = None


def nom_ollama_depuis_gguf(chemin):
    base = os.path.splitext(os.path.basename(chemin))[0].lower()
    base = re.sub(r"[^a-z0-9._-]+", "-", base).strip("-._") or "modele"
    return base[:60] + ":gguf"


def importer_gguf(chemin):
    """Windows/PC : on importe le .gguf dans l'Ollama de la cle (aucun autre outil requis)."""
    chemin = chemin_gguf_autorise(chemin)
    if not demarrer_ollama():
        raise ValueError("Le moteur Ollama de la cle ne demarre pas")
    exe = OLLAMA_EXE if os.path.isfile(OLLAMA_EXE) else shutil.which("ollama")
    nom = nom_ollama_depuis_gguf(chemin)
    modelfile = os.path.join(DONNEES, "Modelfile-import")
    with open(modelfile, "w", encoding="utf-8") as f:
        f.write('FROM "%s"\n' % chemin.replace("\\", "/"))
    drapeaux = 0x08000000 if WINDOWS else 0
    r = subprocess.run([exe, "create", nom, "-f", modelfile], env=env_outils(), capture_output=True,
                       timeout=3600, creationflags=drapeaux)
    if r.returncode != 0:
        raise ValueError("Import refuse : " + (r.stderr or r.stdout).decode("utf-8", "replace")[-600:])
    ecrire_params({"moteur": "ollama", "modele_jasette": nom, "modele_codex": nom})
    return nom


def renommer_ia(d):
    """Renomme un fichier .gguf, ou un modele Ollama (copie sous le nouveau nom, puis retire l'ancien)."""
    nouveau = (d.get("nouveau") or "").strip()
    if d.get("type") == "gguf":
        src = chemin_gguf_autorise(d["chemin"])
        base = re.sub(r"\.gguf$", "", nouveau, flags=re.I).strip()
        if not re.fullmatch(r"[A-Za-z0-9 _.\-]{1,80}", base) or base.strip(".") == "":
            raise ValueError("Nom invalide (lettres, chiffres, espaces, _ - . seulement)")
        dst = os.path.join(os.path.dirname(src), base + ".gguf")
        if os.path.exists(dst):
            raise ValueError("Un fichier porte deja ce nom")
        if _gguf_actif["chemin"] == src:
            arreter_gguf()
        os.rename(src, dst)
        return os.path.basename(dst)
    ancien = d["nom"]
    if not re.fullmatch(r"[a-z0-9][a-z0-9._\-/]{0,80}(:[A-Za-z0-9._\-]{1,40})?", nouveau):
        raise ValueError("Nom invalide (minuscules, chiffres, _ - . et un « : » pour la version, ex. mon-codex:latest)")
    if ":" not in nouveau:
        nouveau += ":latest"
    existants = {m["name"] for m in (http_json(ollama_url("/api/tags"), delai=5).get("models") or [])}
    if nouveau in existants:
        raise ValueError("Une IA porte deja ce nom")
    http_json(ollama_url("/api/copy"), {"source": ancien, "destination": nouveau})
    http_json(ollama_url("/api/delete"), {"model": ancien}, methode="DELETE")
    p = lire_params()
    maj = {k: nouveau for k in ("modele_jasette", "modele_codex") if p.get(k) == ancien}
    if maj:
        ecrire_params(maj)
    return nouveau


def demarrer_gguf(chemin):
    """Fait rouler un .gguf (ex. ceux de CodexLocal) avec llama-server, sans le copier."""
    global _proc_gguf
    chemin = chemin_gguf_autorise(chemin)
    # Appli Android Ti-Labo : son moteur llama.cpp integre lit le .gguf directement (plus rapide, sans Termux).
    if moteur_appli():
        try:
            r = http_json("http://127.0.0.1:%d/tilabo/charger" % PORT_GGUF, {"chemin": chemin}, delai=1800)
        except urllib.error.HTTPError as e:
            try:
                msg = json.loads(e.read().decode("utf-8"))["error"]["message"]
            except Exception:
                msg = str(e)
            raise ValueError("L'appli Ti-Labo n'a pas pu charger l'IA : " + msg)
        _gguf_actif["chemin"] = chemin
        _gguf_actif["appli"] = True
        nom = r.get("modele") or os.path.basename(chemin)
        ecrire_params({"moteur": "openai", "url_openai": "http://127.0.0.1:%d/v1" % PORT_GGUF,
                       "modele_jasette": nom, "modele_codex": nom})
        return nom
    exe = shutil.which("llama-server")
    if not exe:
        if not ANDROID:
            return importer_gguf(chemin)
        raise ValueError("llama-server est absent. Dans Termux : pkg install llama-cpp")
    arreter_gguf()
    _proc_gguf = subprocess.Popen([exe, "-m", chemin, "--host", "127.0.0.1", "--port", str(PORT_GGUF),
                                   "-c", "8192", "--jinja"],
                                  stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for _ in range(240):
        if _proc_gguf.poll() is not None:
            raise ValueError("llama-server s'est arrete (modele trop gros pour la memoire ?)")
        try:
            with urllib.request.urlopen("http://127.0.0.1:%d/health" % PORT_GGUF, timeout=2) as r:
                if r.status == 200:
                    break
        except Exception:
            pass
        time.sleep(0.5)
    _gguf_actif["chemin"] = chemin
    ecrire_params({"moteur": "openai", "url_openai": "http://127.0.0.1:%d/v1" % PORT_GGUF,
                   "modele_jasette": os.path.basename(chemin), "modele_codex": os.path.basename(chemin)})
    return os.path.basename(chemin)


# ---------------------------------------------------------------- Claude (cle API Anthropic)
MODELES_CLAUDE = [
    {"nom": "claude-opus-5-5", "desc": "Le plus fort (code complexe, plans)"},
    {"nom": "claude-sonnet-5-5", "desc": "Rapide et tres bon en code"},
    {"nom": "claude-haiku-5-5", "desc": "Le plus rapide et le moins cher"},
]


def cle_claude():
    return (lire_params().get("anthropic_key") or "").strip()


_capacites = {}


def capacites_modele(modele):
    if modele in _capacites:
        return _capacites[modele]
    try:
        r = http_json(ollama_url("/api/show"), {"model": modele}, delai=10)
        cap = set(r.get("capabilities") or [])
        if not cap:  # anciennes versions d'Ollama : on regarde la famille / le projecteur
            infos = json.dumps(r.get("model_info", {})) + json.dumps(r.get("projector_info", {}))
            if "vision" in infos or r.get("projector_info"):
                cap.add("vision")
    except Exception:
        cap = set()
    _capacites[modele] = cap
    return cap


def voit_images(modele, p):
    if p.get("moteur") == "openai":
        return True  # on ne peut pas le savoir : on essaie
    return "vision" in capacites_modele(modele)


def choisir_modele_vision(p):
    if p.get("moteur") == "openai":
        return None
    try:
        noms = [m["name"] for m in http_json(ollama_url("/api/tags"), delai=5).get("models", [])]
    except Exception:
        return None
    prefere = ["gemma3", "qwen2.5vl", "qwen3-vl", "llava", "minicpm-v", "llama3.2-vision", "moondream"]
    noms.sort(key=lambda n: next((i for i, x in enumerate(prefere) if n.startswith(x)), 99))
    for n in noms:
        if "vision" in capacites_modele(n):
            return n
    return None


def outils_claude(outils=None):
    return [{"name": o["function"]["name"], "description": o["function"]["description"],
             "input_schema": o["function"]["parameters"]} for o in (outils if outils is not None else OUTILS_IA)]


def messages_claude(messages):
    systeme, conv = "", []
    for m in messages:
        if m["role"] == "system":
            systeme += m["content"]
            continue
        if m.get("images"):
            contenu = [{"type": "image", "source": {"type": "base64", "media_type": "image/jpeg", "data": b}}
                       for b in m["images"]] + [{"type": "text", "text": m["content"] or "Regarde cette image."}]
        else:
            contenu = m["content"] or "…"
        conv.append({"role": m["role"], "content": contenu})
    return systeme, conv


def requete_claude(corps, flux=False):
    req = urllib.request.Request("https://api.anthropic.com/v1/messages", data=json.dumps(corps).encode(),
                                 headers={"Content-Type": "application/json", "x-api-key": cle_claude(),
                                          "anthropic-version": "2023-06-01"})
    try:
        return urllib.request.urlopen(req, timeout=600)
    except urllib.error.HTTPError as e:
        corps_err = e.read().decode("utf-8", "ignore")
        try:
            msg = json.loads(corps_err)["error"]["message"]
        except (ValueError, KeyError, TypeError):
            msg = corps_err[:300]
        if e.code == 401:
            msg = "Cle API Claude refusee — verifie-la dans Parametres"
        raise ValueError("Claude : " + msg)


# ---------------------------------------------------------------- images locales (stable-diffusion.cpp)
IMAGES_MODELES = os.path.join(RACINE, "ia", "images")
IMAGES_SORTIE = os.path.join(DONNEES, "images")
SD_DIR = os.path.join(OUTILS, "sd")
CATALOGUE_IMAGES = {
    "sd_turbo.safetensors": {
        "titre": "SD-Turbo", "taille_go": 5.2, "pas": 2, "cfg": 1.0, "dim": 512,
        "desc": "Tres rapide (1 a 4 etapes). Ideal pour un PC ordinaire.",
        "url": "https://huggingface.co/stabilityai/sd-turbo/resolve/main/sd_turbo.safetensors"},
    "v1-5-pruned-emaonly.safetensors": {
        "titre": "Stable Diffusion 1.5", "taille_go": 4.3, "pas": 20, "cfg": 7.0, "dim": 512,
        "desc": "Le classique : plus lent, plus de details.",
        "url": "https://huggingface.co/stable-diffusion-v1-5/stable-diffusion-v1-5/resolve/main/v1-5-pruned-emaonly.safetensors"},
}
for _d in (IMAGES_MODELES, IMAGES_SORTIE):
    os.makedirs(_d, exist_ok=True)


def exe_sd():
    for nom in ("sd-cli" + EXE, "sd" + EXE):
        for base, _, fichiers in os.walk(SD_DIR) if os.path.isdir(SD_DIR) else []:
            if nom in fichiers:
                return os.path.join(base, nom)
    return shutil.which("sd-cli") or shutil.which("sd")


def modeles_images():
    res = []
    for f in sorted(os.listdir(IMAGES_MODELES)):
        if f.lower().endswith((".safetensors", ".ckpt", ".gguf")):
            info = CATALOGUE_IMAGES.get(f, {})
            res.append({"nom": f, "titre": info.get("titre", f), "taille_go": round(os.path.getsize(os.path.join(IMAGES_MODELES, f)) / 1e9, 1),
                        "pas": info.get("pas", 20), "cfg": info.get("cfg", 7.0), "dim": info.get("dim", 512)})
    return res


def galerie():
    fichiers = sorted((f for f in os.listdir(IMAGES_SORTIE) if f.endswith(".png")), reverse=True)
    return [{"nom": f, "url": "/images/" + urllib.parse.quote(f)} for f in fichiers[:60]]


def telecharger_modele_image(d, envoyer):
    nom = d.get("nom")
    if nom not in CATALOGUE_IMAGES:
        raise ValueError("Modele d'image inconnu")
    dst = os.path.join(IMAGES_MODELES, nom)
    if os.path.isfile(dst):
        envoyer({"fini": True, "message": "Deja sur la cle"})
        return
    tmp = dst + ".part"
    deja = os.path.getsize(tmp) if os.path.isfile(tmp) else 0
    req = urllib.request.Request(CATALOGUE_IMAGES[nom]["url"], headers={"User-Agent": NAVIGATEUR})
    if deja:
        req.add_header("Range", "bytes=%d-" % deja)
    with urllib.request.urlopen(req, timeout=60) as r:
        if deja and r.status != 206:
            deja = 0
        total = deja + int(r.headers.get("Content-Length") or 0)
        with open(tmp, "ab" if deja else "wb") as f:
            fait = deja
            for b in iter(lambda: r.read(4 * 1024 * 1024), b""):
                f.write(b)
                fait += len(b)
                envoyer({"fait": fait, "total": total})
    os.replace(tmp, dst)
    envoyer({"fini": True, "message": CATALOGUE_IMAGES[nom]["titre"] + " est sur la cle"})


def creer_image(d, envoyer):
    exe = exe_sd()
    if not exe:
        raise ValueError("Le moteur d'images est absent. Relance Installer-Ti-Labo.bat (il ajoute stable-diffusion.cpp).")
    nom = os.path.basename(d.get("modele") or "")
    modele = os.path.join(IMAGES_MODELES, nom)
    if not os.path.isfile(modele):
        raise ValueError("Telecharge d'abord un modele d'image")
    prompt = (d.get("prompt") or "").strip()
    if not prompt:
        raise ValueError("Decris l'image a creer")
    largeur = max(256, min(1024, int(d.get("largeur") or 512)) // 64 * 64)
    hauteur = max(256, min(1024, int(d.get("hauteur") or 512)) // 64 * 64)
    pas = max(1, min(60, int(d.get("pas") or 20)))
    cfg = max(0.0, min(20.0, float(d.get("cfg") or 7.0)))
    graine = int(d.get("graine") if d.get("graine") not in (None, "") else -1)
    sortie = os.path.join(IMAGES_SORTIE, time.strftime("image-%Y%m%d-%H%M%S.png"))
    cmd = [exe, "-m", modele, "-p", prompt, "-o", sortie, "--steps", str(pas), "--cfg-scale", str(cfg),
           "-W", str(largeur), "-H", str(hauteur), "-s", str(graine)]
    if d.get("negatif"):
        cmd += ["-n", d["negatif"]]
    drapeaux = 0x08000000 if WINDOWS else 0
    envoyer({"etape": "Le moteur d'images charge le modele…"})
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, creationflags=drapeaux)
    tampon, dernier = b"", ""
    while True:
        c = proc.stdout.read(1)
        if not c:
            break
        if c in (b"\r", b"\n"):
            l = tampon.decode("utf-8", "ignore").strip()
            tampon = b""
            m = re.search(r"(\d+)/(\d+)", l)
            if m and ("it/s" in l or "s/it" in l or "|" in l):
                envoyer({"fait": int(m.group(1)), "total": int(m.group(2))})
            elif l:
                dernier = l
        else:
            tampon += c
    proc.wait()
    if proc.returncode != 0 or not os.path.isfile(sortie):
        raise ValueError("Echec de la creation : " + dernier[-300:])
    envoyer({"fini": True, "image": {"nom": os.path.basename(sortie), "url": "/images/" + urllib.parse.quote(os.path.basename(sortie))}})


# ---------------------------------------------------------------- mot de passe a l'entree
_essais = {"rates": 0, "bloque_jusqua": 0.0}


def hacher(mdp, sel=None):
    sel = sel or secrets.token_hex(16)
    h = hashlib.pbkdf2_hmac("sha256", mdp.encode("utf-8"), bytes.fromhex(sel), 300_000).hex()
    return sel + "$" + h


def mdp_actif():
    return bool(lire_params().get("mot_de_passe"))


def verifier_mdp(mdp):
    stocke = lire_params().get("mot_de_passe") or ""
    if "$" not in stocke:
        return False
    sel = stocke.split("$", 1)[0]
    return secrets.compare_digest(hacher(mdp, sel), stocke)


def entrer(mdp):
    maintenant = time.time()
    if maintenant < _essais["bloque_jusqua"]:
        raise ValueError("Trop d'essais. Reessaie dans %d secondes." % int(_essais["bloque_jusqua"] - maintenant))
    time.sleep(0.6)
    if verifier_mdp(mdp or ""):
        _essais["rates"] = 0
        return JETON
    _essais["rates"] += 1
    if _essais["rates"] >= 5:
        _essais["bloque_jusqua"] = maintenant + 60 * (_essais["rates"] - 4)
    raise ValueError("Mot de passe incorrect")


def changer_mdp(d):
    if mdp_actif() and not verifier_mdp(d.get("actuel") or ""):
        raise ValueError("Mot de passe actuel incorrect")
    nouveau = d.get("nouveau") or ""
    if not nouveau:
        ecrire_params({"mot_de_passe": ""})
        return False
    if len(nouveau) < 4:
        raise ValueError("Au moins 4 caracteres")
    ecrire_params({"mot_de_passe": hacher(nouveau)})
    return True


def polices_windows():
    base = ["Segoe UI", "Cascadia Mono", "Cascadia Code", "Consolas", "Arial", "Calibri", "Verdana",
            "Tahoma", "Trebuchet MS", "Georgia", "Courier New", "Lucida Console", "Comic Sans MS"]
    trouvees = set()
    if WINDOWS:
        try:
            import winreg
            for ruche in (winreg.HKEY_LOCAL_MACHINE, winreg.HKEY_CURRENT_USER):
                try:
                    k = winreg.OpenKey(ruche, r"SOFTWARE\Microsoft\Windows NT\CurrentVersion\Fonts")
                except OSError:
                    continue
                i = 0
                while True:
                    try:
                        nom = winreg.EnumValue(k, i)[0]
                    except OSError:
                        break
                    nom = re.sub(r"\s*\(.*\)$", "", nom)
                    nom = re.sub(r"\s+(Bold|Italic|Light|Semibold|SemiBold|Black|Regular|Medium|Thin|& .*).*$", "", nom)
                    if nom:
                        trouvees.add(nom.strip())
                    i += 1
        except Exception:
            pass
    return sorted(trouvees or set(base), key=str.lower)


def ouvrir_navigateur(url):
    if ANDROID and shutil.which("termux-open-url"):
        subprocess.Popen(["termux-open-url", url], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    else:
        webbrowser.open(url)


def port_libre(port):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        return s.connect_ex(("127.0.0.1", port)) != 0


def main():
    global PORT_APP
    while not port_libre(PORT_APP) and PORT_APP < 8800:
        PORT_APP += 1
    print("=" * 52)
    print("  Ti-Labo — bureau du laboratoire")
    print("  Cle : %s" % RACINE)
    print("=" * 52)
    threading.Thread(target=demarrer_ollama, daemon=True).start()
    serveur = ThreadingHTTPServer(("127.0.0.1", PORT_APP), Gestion)
    serveur.daemon_threads = True
    url = "http://127.0.0.1:%d/" % PORT_APP
    print("  Ouvert dans ton navigateur : %s" % url)
    print("  Ferme cette fenetre pour arreter le Ti-Labo.")
    if "--sans-navigateur" not in sys.argv:
        threading.Timer(0.8, lambda: ouvrir_navigateur(url)).start()
    try:
        serveur.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        arreter_gguf()
        arreter_ollama()


if __name__ == "__main__":
    main()
