# -*- coding: utf-8 -*-
"""
Ti-Labo — bureau du laboratoire
Serveur local qui roule entierement depuis la cle USB-C.
Python standard seulement (aucun pip requis).
"""
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


def ollama_url(chemin):
    return "http://%s:%d%s" % (HOTE_OLLAMA, PORT_OLLAMA, chemin)


def ollama_vivant():
    try:
        with urllib.request.urlopen(ollama_url("/api/version"), timeout=2) as r:
            return json.loads(r.read().decode()).get("version", "?")
    except Exception:
        return None


def demarrer_ollama():
    global _proc_ollama
    if ollama_vivant():
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
        if ollama_vivant():
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


def appeler_outil(nom, args):
    try:
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
            "chercher_mes_projets": "Mes projets : %s" % args.get("requete", "")}.get(nom, nom)


# ---------------------------------------------------------------- invites systeme
INVITE_JASETTE = ("Tu es l'IA locale du Ti-Labo d'Alex, qui roule sur sa cle USB-C. "
                  "Reponds en francais quebecois naturel, clair et direct.")
INVITE_CODEX = ("Tu es le Codex du Ti-Labo : un assistant de programmation local. Reponds en francais. "
                "Explique simplement, donne du code complet et pret a coller dans des blocs ``` avec le langage. "
                "Quand on te donne un fichier, base-toi dessus et indique precisement quoi changer.")
INVITE_OUTILS = ("\n\nTu as des outils : recherche_web, lire_page, chercher_code_github, chercher_mes_projets. "
                 "Utilise-les seulement si tu en as vraiment besoin (info recente, doc d'une librairie, exemple de code "
                 "que tu ne connais pas). Sinon reponds directement. Quand tu t'en sers, cite les liens a la fin "
                 "sous « Sources : ».")
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
                page = f.read().replace("__JETON__", JETON)
            return self.envoyer(200, page, "text/html; charset=utf-8")
        if u.path.startswith("/web/"):
            return self.statique(u.path[5:])
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
            return self.erreur("Route inconnue", 404)
        except Exception as e:
            return self.erreur(e)

    def statique(self, rel):
        p = os.path.realpath(os.path.join(WEB, rel))
        if not p.startswith(os.path.realpath(WEB) + os.sep) or not os.path.isfile(p):
            return self.erreur("Introuvable", 404)
        types = {".css": "text/css", ".js": "text/javascript", ".svg": "image/svg+xml",
                 ".png": "image/png", ".woff2": "font/woff2", ".ico": "image/x-icon"}
        with open(p, "rb") as f:
            self.envoyer(200, f.read(), types.get(os.path.splitext(p)[1], "application/octet-stream"))

    def modeles(self):
        p = lire_params()
        if p["moteur"] == "openai":
            r = http_json(p["url_openai"].rstrip("/") + "/models", delai=5)
            return [{"nom": m["id"], "taille_go": None} for m in r.get("data", [])]
        r = http_json(ollama_url("/api/tags"), delai=5)
        return [{"nom": m["name"], "taille_go": round(m.get("size", 0) / 1e9, 1),
                 "famille": m.get("details", {}).get("family", ""),
                 "params": m.get("details", {}).get("parameter_size", "")} for m in r.get("models", [])]

    # -- POST
    def do_POST(self):
        if not self.autorise():
            return self.erreur("Acces refuse", 403)
        route = urllib.parse.urlparse(self.path).path[5:]
        try:
            d = self.corps_json()
            if route == "parametres":
                return self.envoyer(200, {k: v for k, v in ecrire_params(
                    {k: v for k, v in d.items() if k != "github_token"}).items() if k != "github_token"})
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
        if internet:
            systeme += INVITE_OUTILS
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

        if p["moteur"] == "openai":
            return self.jasette_openai(p, modele, messages, sources, internet)
        return self.jasette_ollama(modele, messages, sources, internet)

    # -- Ollama : boucle d'outils puis reponse en flux
    def jasette_ollama(self, modele, messages, sources, internet):
        self.debut_flux()
        if sources:
            self.ligne({"sources": sources})
        if internet:
            for _ in range(6):
                try:
                    r = http_json(ollama_url("/api/chat"), {"model": modele, "messages": messages,
                                                             "tools": OUTILS_IA, "stream": False}, delai=1800)
                except urllib.error.HTTPError as e:
                    corps = e.read().decode("utf-8", "ignore")
                    if "does not support tools" in corps:
                        self.ligne({"outil": "Ce modele ne sait pas utiliser Internet — reponse sans recherche"})
                        break
                    self.ligne({"erreur": corps or str(e)})
                    return
                msg = r.get("message", {})
                appels = msg.get("tool_calls") or []
                if not appels:
                    # Reponse finale deja calculee : on l'envoie en petits morceaux
                    texte = msg.get("content", "")
                    for i in range(0, len(texte), 40):
                        self.ligne({"texte": texte[i:i + 40]})
                    self.ligne({"fini": True})
                    return
                messages.append({"role": "assistant", "content": msg.get("content", ""), "tool_calls": appels})
                for a in appels:
                    f = a.get("function", {})
                    args = f.get("arguments") or {}
                    if isinstance(args, str):
                        try:
                            args = json.loads(args)
                        except ValueError:
                            args = {}
                    self.ligne({"outil": etiquette_outil(f.get("name"), args)})
                    res = appeler_outil(f.get("name"), args)
                    if f.get("name") in ("recherche_web", "chercher_code_github") and isinstance(res, list):
                        self.ligne({"liens": [x.get("url") for x in res[:5] if x.get("url")]})
                    messages.append({"role": "tool", "content": json.dumps(res, ensure_ascii=False)[:14000],
                                     "tool_name": f.get("name")})
        req = urllib.request.Request(ollama_url("/api/chat"),
                                     data=json.dumps({"model": modele, "messages": messages, "stream": True}).encode(),
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

    # -- Moteur compatible OpenAI (LM Studio, llama.cpp server, Jan…)
    def jasette_openai(self, p, modele, messages, sources, internet):
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
        if internet:
            for _ in range(6):
                try:
                    r = http_json(base + "/chat/completions", {"model": modele, "messages": conv,
                                                               "tools": OUTILS_IA, "stream": False}, delai=1800)
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
                    res = appeler_outil(f["name"], args)
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
_gguf_actif = {"chemin": None}


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


def arreter_gguf():
    global _proc_gguf
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
