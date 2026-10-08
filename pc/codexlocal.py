#!/usr/bin/env python3
# MARCEAU CodexLocal PC - Python 3.10+ standard library only.
# This program discovers existing CLIs; it never installs an agent for you.
"""Interface terminal CodexLocal pour Windows, Kali, Ubuntu et macOS."""

from __future__ import annotations

import argparse
import os
import shutil
import subprocess
import sys
from pathlib import Path

AGENTS = ("ollama", "codex", "claude", "gemini")
COLOR_ORANGE = "\033[38;5;208m"
COLOR_BLUE = "\033[96m"
COLOR_WHITE = "\033[97m"
COLOR_RESET = "\033[0m"

CODEX_INSTRUCTIONS = (
    "Tu es MARCEAU CodexLocal en mode CODEX. Reponds en francais. "
    "Propose du code complet et fiable, explique les corrections et "
    "precise les tests a realiser. Ne modifie pas de fichier sans validation."
)
CHAT_INSTRUCTIONS = (
    "Tu es MARCEAU CodexLocal en mode CHAT. Reponds en francais, "
    "de facon naturelle et utile. Ne propose du code que si demande."
)


def available_agents() -> list[str]:
    return [name for name in AGENTS if shutil.which(name)]


def list_ollama_models() -> list[str]:
    if not shutil.which("ollama"):
        return []
    try:
        p = subprocess.run(
            ["ollama", "list"], capture_output=True, text=True, timeout=8,
            check=False, encoding="utf-8", errors="replace"
        )
    except (OSError, subprocess.TimeoutExpired):
        return []
    if p.returncode != 0:
        return []
    lines = p.stdout.splitlines()
    return [
        line.split()[0] for line in lines[1:]
        if line.split() and not line.lstrip().startswith("NAME")
    ]


def choose_ollama_model(models: list[str]) -> str:
    # Ne telecharge rien : n'utilise que les modeles deja presents.
    candidates = ("qwen2.5-coder:7b", "qwen2.5-coder", "qwen3-coder", "qwen", "coder")
    for preferred in candidates:
        for name in models:
            if preferred in name.lower():
                return name
    return models[0] if models else ""


def choose_agent(wanted: str) -> str:
    agents = available_agents()
    if wanted != "auto":
        if wanted not in agents:
            raise RuntimeError(
                f"Agent '{wanted}' introuvable dans le PATH. Disponibles : "
                + (", ".join(agents) or "aucun")
            )
        return wanted
    if "ollama" in agents and list_ollama_models():
        return "ollama"
    for name in ("codex", "claude", "gemini"):
        if name in agents:
            return name
    raise RuntimeError("Aucun agent pret a l'emploi. Essaie /agents ou --list-agents.")


def build_command(agent: str, prompt: str, model: str) -> list[str]:
    if agent == "ollama":
        if not model:
            raise RuntimeError("Aucun modele Ollama local. Installe d'abord un modele.")
        return ["ollama", "run", model, prompt]
    if agent == "codex":
        # Lecture seule par defaut : pas de modification automatique des fichiers.
        return ["codex", "exec", "--sandbox", "read-only", "--skip-git-repo-check", prompt]
    if agent == "claude":
        return ["claude", "-p", "--permission-mode", "plan", prompt]
    if agent == "gemini":
        return ["gemini", "-p", prompt]
    raise ValueError(f"Agent non pris en charge : {agent}")


def run_agent(agent: str, mode: str, text: str, model: str, workdir: Path) -> int:
    instructions = CODEX_INSTRUCTIONS if mode == "codex" else CHAT_INSTRUCTIONS
    prompt = f"{instructions}\n\nDemande utilisateur :\n{text}"
    argv = build_command(agent, prompt, model)
    print(f"\n[{agent} · {mode}] Réponse en cours...\n", flush=True)
    try:
        # Pas de shell : aucune interpretation des commandes contenues dans la demande.
        result = subprocess.run(argv, cwd=workdir, check=False)
        return result.returncode
    except KeyboardInterrupt:
        print("\nInterrompu.")
        return 130
    except OSError as exc:
        print(f"Impossible de lancer {agent} : {exc}", file=sys.stderr)
        return 1


def show_agents(selected: str, model: str) -> None:
    found = available_agents()
    print("Agents detectes dans le PATH :", ", ".join(found) if found else "aucun")
    if "ollama" in found:
        models = list_ollama_models()
        print("Modeles Ollama locaux :", ", ".join(models) if models else "aucun ou serveur inaccessible")
    print("Agent actif :", selected, "| Modele :", model or "defaut de l'agent")
    print("Les agents en ligne ont besoin de leur authentification habituelle.")


def compose_multiline() -> str:
    """Zone de saisie multi-ligne pour les vrais terminaux sans GUI."""
    print("\n╭──────── ESPACE D'ÉCRITURE MARCEAU ────────╮")
    print("│ Écris sur plusieurs lignes. Commandes :")
    print("│ /envoyer pour envoyer ; /annuler pour annuler.")
    print("╰────────────────────────────────────────────╯")
    lines: list[str] = []
    while True:
        try:
            line = input("│ ")
        except (EOFError, KeyboardInterrupt):
            print("\nÉcriture annulée.")
            return ""
        if line.strip() == "/annuler":
            return ""
        if line.strip() == "/envoyer":
            return "\n".join(lines).strip()
        lines.append(line)


def main() -> int:
    parser = argparse.ArgumentParser(description="MARCEAU CodexLocal — assistant terminal PC")
    parser.add_argument("--agent", choices=("auto",) + AGENTS, default="auto")
    parser.add_argument("--mode", choices=("codex", "chat"), default="codex")
    parser.add_argument("--model", default="", help="Nom d'un modele Ollama installe")
    parser.add_argument("--workdir", type=Path, default=Path.cwd())
    parser.add_argument("--once", default="", help="Une seule question, sans boucle interactive")
    parser.add_argument("--list-agents", action="store_true")
    parser.add_argument(
        "--terminal", action="store_true",
        help="Forcer le mode texte dans le terminal (avec /ecrire pour plusieurs lignes)"
    )
    parser.add_argument(
        "--gui", action="store_true",
        help="Ouvrir la fenêtre graphique avec la grande zone d'écriture"
    )
    args = parser.parse_args()
    if not args.workdir.is_dir():
        parser.error("--workdir doit pointer vers un dossier existant")
    if args.list_agents:
        show_agents("aucun", args.model)
        return 0

    # Lancement sans argument : la fenêtre graphique est le choix par défaut.
    # --terminal conserve la ligne de commande historique pour SSH ou un PC sans écran.
    auto_gui = len(sys.argv) == 1
    if args.gui or (auto_gui and not args.terminal):
        window = Path(__file__).with_name("codexlocal_gui.py")
        result = subprocess.run(
            [sys.executable, str(window), "--workdir", str(args.workdir)],
            check=False,
        )
        if result.returncode == 0 or args.gui:
            return result.returncode
        print("Fenêtre graphique indisponible : retour au terminal.", file=sys.stderr)

    try:
        agent = choose_agent(args.agent)
    except RuntimeError as exc:
        print(str(exc), file=sys.stderr)
        return 2
    mode = args.mode
    model = args.model
    if agent == "ollama" and not model:
        model = choose_ollama_model(list_ollama_models())
    if agent == "ollama" and not model:
        print("Ollama ne contient aucun modele local disponible.", file=sys.stderr)
        return 2
    if args.once:
        return run_agent(agent, mode, args.once, model, args.workdir)

    color_enabled = sys.stdout.isatty() and os.getenv("NO_COLOR") is None
    orange = COLOR_ORANGE if color_enabled else ""
    blue = COLOR_BLUE if color_enabled else ""
    reset = COLOR_RESET if color_enabled else ""
    print(f"{orange}╔══════════════════════════════╗")
    print("║   MARCEAU CODEXLOCAL PC     ║")
    print(f"╚══════════════════════════════╝{reset}")
    print("Mode Codex </> = orange | Chat = bleu ciel")
    print("Pour écrire plusieurs lignes : /ecrire (terminer par /envoyer)")
    print("Commandes : /ecrire, /agents, /use NOM, /model NOM, /mode chat|codex, /help, /quit")
    show_agents(agent, model)
    while True:
        accent = blue if mode == "chat" else orange
        try:
            message = input(f"{accent}MARCEAU ({mode}/{agent}) > {reset}").strip()
        except (EOFError, KeyboardInterrupt):
            print("\nAu revoir !")
            return 0
        if not message:
            continue
        if message in ("/quit", "/exit", "/q"):
            return 0
        if message == "/help":
            print("/ecrire : zone d'écriture multiligne, /envoyer pour terminer")
            print("/agents : detection | /use agent : choisir | /model nom : choisir Ollama")
            print("/mode chat ou /mode codex : changer | /quit : quitter")
            continue
        if message in ("/ecrire", "/compose"):
            message = compose_multiline()
            if not message:
                continue
        if message == "/agents":
            show_agents(agent, model)
            continue
        if message.startswith("/mode "):
            wanted_mode = message.split(maxsplit=1)[1].lower()
            if wanted_mode not in ("chat", "codex"):
                print("Choix : chat ou codex")
                continue
            mode = wanted_mode
            print("Mode actif :", mode)
            continue
        if message.startswith("/use "):
            name = message.split(maxsplit=1)[1].lower()
            try:
                agent = choose_agent(name)
                if agent == "ollama" and not model:
                    model = choose_ollama_model(list_ollama_models())
                print("Agent actif :", agent)
            except RuntimeError as exc:
                print(exc)
            continue
        if message.startswith("/model "):
            model = message.split(maxsplit=1)[1].strip()
            print("Modele Ollama choisi :", model)
            continue
        code = run_agent(agent, mode, message, model, args.workdir)
        if code:
            print(f"[Erreur] Code retour de l'agent : {code}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
