#!/usr/bin/env python3
"""MARCEAU CodexLocal Desktop: GUI noir/orange (Codex) et noir/bleu (Chat).

Python 3.10+, Tkinter standard, sans service Web ajoute.
Les agents utilises sont les CLI deja presents dans le PATH du PC.
"""
from __future__ import annotations

import argparse
import queue
import subprocess
import sys
import threading
import tkinter as tk
from pathlib import Path
from tkinter import filedialog, messagebox, ttk

from codexlocal import (
    CHAT_INSTRUCTIONS,
    CODEX_INSTRUCTIONS,
    available_agents,
    build_command,
    choose_ollama_model,
    list_ollama_models,
)

BLACK = "#000000"
PANEL = "#101010"
SURFACE = "#0A0A0A"
TEXT = "#ECECEC"
MUTED = "#9C9C9C"
ORANGE = "#FF7A00"
SKY = "#38BDF8"
FONT = "Segoe UI"
LOGO = Path(__file__).resolve().parents[1] / "app/src/main/res/drawable-nodpi/logo_marceau.png"


def make_prompt(mode: str, history: list[tuple[str, str]], latest: str) -> str:
    """Garde le contexte recent ; le CLI externe est sans session garantie."""
    instructions = CODEX_INSTRUCTIONS if mode == "codex" else CHAT_INSTRUCTIONS
    lines = []
    for role, value in history[-10:]:
        if value.strip():
            label = "Utilisateur" if role == "user" else "Assistant"
            lines.append(f"{label}: {value[:3000]}")
    recent_context = "\n".join(lines)[-9500:]
    # Conserver les instructions de securite et de mode meme si le fil est long.
    return (
        instructions + "\n\nHistorique récent :\n" + recent_context +
        "\n\nUtilisateur: " + latest[-3000:] + "\nAssistant:"
    )


class CodexDesktop:
    def __init__(self, root: tk.Tk, workdir: Path) -> None:
        self.root = root
        self.workdir = workdir
        self.mode = "codex"
        self.accent = ORANGE
        self.conversations: dict[str, list[tuple[str, str]]] = {
            "codex": [], "chat": []
        }
        self.agent_var = tk.StringVar(value="auto")
        self.model_var = tk.StringVar(value="")
        self.status_var = tk.StringVar(value="Prêt • agents installés sur cet ordinateur")
        self.events: queue.Queue[tuple[str, object]] = queue.Queue()
        self.process: subprocess.Popen[str] | None = None
        self.busy = False
        self.last_response = ""
        self.logo_image: tk.PhotoImage | None = None
        self._build()
        self._scan_agents()
        self._render_conversation()
        self.root.after(80, self._drain_queue)
        self.root.protocol("WM_DELETE_WINDOW", self._on_close)

    def _build(self) -> None:
        self.root.title("MARCEAU — CodexLocal PC")
        self.root.geometry("1100x780")
        self.root.minsize(740, 560)
        self.root.configure(bg=BLACK)

        header = tk.Frame(self.root, bg=BLACK, height=100, padx=22, pady=12)
        header.pack(fill="x")
        header.pack_propagate(False)
        self.menu_btn = tk.Button(
            header, text="☰", command=self._show_menu, font=(FONT, 25),
            bg=BLACK, fg=ORANGE, activebackground=BLACK,
            activeforeground=ORANGE, bd=0, cursor="hand2",
        )
        self.menu_btn.pack(side="left", padx=(0, 20))
        if LOGO.is_file():
            try:
                img = tk.PhotoImage(file=str(LOGO))
                factor = max(1, (max(img.width(), img.height()) + 79) // 80)
                self.logo_image = img.subsample(factor)
                tk.Label(header, image=self.logo_image, bg=BLACK).pack(
                    side="left", padx=(0, 20)
                )
            except tk.TclError:
                self._fallback_logo(header)
        else:
            self._fallback_logo(header)

        self.title_label = tk.Label(
            header, text="NOUVELLE CONVERSATION", font=(FONT, 19, "bold"),
            bg=BLACK, fg=TEXT, anchor="w",
        )
        self.title_label.pack(side="left", fill="x", expand=True)
        self.new_btn = tk.Button(
            header, text="+ Nouveau", command=self._new_chat, bg=BLACK,
            fg=ORANGE, activebackground=PANEL, activeforeground=ORANGE,
            relief="flat", bd=0, font=(FONT, 11, "bold"), cursor="hand2",
        )
        self.new_btn.pack(side="right", padx=(10, 0))

        self.separator = tk.Frame(self.root, height=2, bg=ORANGE)
        self.separator.pack(fill="x")
        controls = tk.Frame(self.root, bg=SURFACE, padx=24, pady=10)
        controls.pack(fill="x")
        tk.Label(
            controls, text="AGENT", bg=SURFACE, fg=MUTED, font=(FONT, 9, "bold")
        ).pack(side="left")
        self.agent_choice = ttk.Combobox(
            controls, textvariable=self.agent_var, values=["auto"],
            width=13, state="readonly",
        )
        self.agent_choice.pack(side="left", padx=(8, 12))
        self.agent_choice.bind("<<ComboboxSelected>>", self._agent_changed)

        tk.Label(
            controls, text="MODÈLE", bg=SURFACE, fg=MUTED, font=(FONT, 9, "bold")
        ).pack(side="left")
        self.model_choice = ttk.Combobox(
            controls, textvariable=self.model_var, values=[], width=28
        )
        self.model_choice.pack(side="left", padx=(8, 12))
        tk.Button(
            controls, text="↻", command=self._scan_agents,
            bg=SURFACE, fg=MUTED, bd=0, font=(FONT, 15), cursor="hand2",
        ).pack(side="left")
        self.chat_button = tk.Button(
            controls, text="Chat ➤", command=lambda: self._set_mode("chat"),
            font=(FONT, 11, "bold"), width=11, cursor="hand2", bd=0
        )
        self.chat_button.pack(side="right", padx=(6, 0))
        self.codex_button = tk.Button(
            controls, text="Codex </>", command=lambda: self._set_mode("codex"),
            font=(FONT, 11, "bold"), width=13, cursor="hand2", bd=0
        )
        self.codex_button.pack(side="right", padx=(0, 4))

        tk.Label(
            self.root, textvariable=self.status_var,
            bg=SURFACE, fg=MUTED, font=(FONT, 10), anchor="w", padx=24,
        ).pack(fill="x", ipady=6)

        # Ecran recentré ; marges gauche/droite identiques.
        body = tk.Frame(self.root, bg=BLACK, padx=24, pady=10)
        body.pack(fill="both", expand=True)
        self.conversation_border = tk.Frame(
            body, bg=PANEL, highlightbackground=ORANGE, highlightthickness=1
        )
        self.conversation_border.pack(fill="both", expand=True)
        self.conversation = tk.Text(
            self.conversation_border, bg=PANEL, fg=TEXT,
            insertbackground=TEXT, bd=0, highlightthickness=0,
            wrap="word", padx=28, pady=22, state="disabled",
            font=(FONT, 12), spacing1=4, spacing3=10,
            cursor="arrow", selectbackground="#314154",
        )
        scrollbar = tk.Scrollbar(
            self.conversation_border, command=self.conversation.yview,
            bg=PANEL, troughcolor=BLACK, activebackground=ORANGE,
            relief="flat",
        )
        self.conversation.configure(yscrollcommand=scrollbar.set)
        scrollbar.pack(side="right", fill="y")
        self.conversation.pack(side="left", fill="both", expand=True)
        self.conversation.tag_configure(
            "meta", font=(FONT, 10, "bold"), foreground=ORANGE, spacing1=16
        )
        self.conversation.tag_configure(
            "user", foreground=TEXT, justify="right", lmargin1=44,
            lmargin2=44, rmargin=14, spacing3=12
        )
        self.conversation.tag_configure(
            "assistant", foreground=TEXT, lmargin1=10, lmargin2=10,
            rmargin=44, spacing3=12
        )

        # Zone d'ecriture multi-ligne remontée du bas et plus haute que sur Android.
        self.composer_border = tk.Frame(
            body, bg=SURFACE, highlightbackground=ORANGE, highlightthickness=2
        )
        self.composer_border.pack(fill="x", pady=(15, 12))
        compose_top = tk.Frame(self.composer_border, bg=SURFACE)
        compose_top.pack(fill="x", padx=14, pady=(12, 5))
        self.input = tk.Text(
            compose_top, height=4, wrap="word", bg=SURFACE, fg=TEXT,
            insertbackground=ORANGE, selectbackground="#604020",
            font=(FONT, 12), relief="flat", bd=0, highlightthickness=0,
            undo=True, padx=8, pady=6,
        )
        self.input.pack(side="left", fill="both", expand=True)
        self.input.bind("<Control-Return>", self._ctrl_enter)
        self.input.bind("<Control-KP_Enter>", self._ctrl_enter)
        self.send_button = tk.Button(
            compose_top, text="</>", command=self._send,
            bg=ORANGE, fg=BLACK, font=(FONT, 17, "bold"),
            width=4, height=2, cursor="hand2", bd=0,
            activebackground=ORANGE, activeforeground=BLACK,
        )
        self.send_button.pack(side="right", padx=(12, 0), pady=(4, 0))

        compose_footer = tk.Frame(self.composer_border, bg=SURFACE)
        compose_footer.pack(fill="x", padx=20, pady=(0, 11))
        self.help_label = tk.Label(
            compose_footer, text="Ctrl + Entrée : envoyer   •   Entrée : nouvelle ligne",
            bg=SURFACE, fg=MUTED, font=(FONT, 9), anchor="w"
        )
        self.help_label.pack(side="left")
        self.copy_btn = tk.Button(
            compose_footer, text="Copier la réponse", command=self._copy_answer,
            bg=SURFACE, fg=ORANGE, bd=0, font=(FONT, 9),
            cursor="hand2", activebackground=SURFACE
        )
        self.copy_btn.pack(side="right")

        self._apply_theme()
        self.input.focus_set()

    def _fallback_logo(self, parent: tk.Frame) -> None:
        tk.Label(
            parent, text="◈ MARCEAU ◈", fg=ORANGE, bg=BLACK,
            font=(FONT, 17, "bold"),
        ).pack(side="left", padx=(0, 22))

    def _show_menu(self) -> None:
        menu = tk.Menu(self.root, tearoff=False, bg=PANEL, fg=TEXT,
                       activebackground=ORANGE, activeforeground=BLACK)
        menu.add_command(label="Nouvelle conversation", command=self._new_chat)
        menu.add_command(label="Changer de dossier de travail", command=self._choose_folder)
        menu.add_command(label="Copier la dernière réponse", command=self._copy_answer)
        menu.add_separator()
        menu.add_command(label="Quitter", command=self._on_close)
        try:
            menu.tk_popup(
                self.menu_btn.winfo_rootx(),
                self.menu_btn.winfo_rooty() + self.menu_btn.winfo_height(),
            )
        finally:
            menu.grab_release()

    def _choose_folder(self) -> None:
        folder = filedialog.askdirectory(
            title="Choisir le dossier du projet", initialdir=str(self.workdir)
        )
        if folder:
            self.workdir = Path(folder)
            self.status_var.set(f"Dossier : {self.workdir}")

    def _scan_agents(self) -> None:
        found = available_agents()
        options = ["auto", *found]
        self.agent_choice.configure(values=options)
        if self.agent_var.get() not in options:
            self.agent_var.set("auto")
        self.status_var.set(
            "Agents installés : " + (", ".join(found) if found else "aucun détecté")
        )
        if "ollama" in found:
            threading.Thread(
                target=lambda: self.events.put(("models", list_ollama_models())),
                daemon=True,
            ).start()

    def _agent_changed(self, _event: object = None) -> None:
        self.status_var.set("Agent sélectionné : " + self.agent_var.get())

    def _set_mode(self, mode: str) -> None:
        if self.busy or mode == self.mode:
            return
        self.mode = mode
        self._apply_theme()
        self._render_conversation()

    def _apply_theme(self) -> None:
        self.accent = SKY if self.mode == "chat" else ORANGE
        self.separator.configure(bg=self.accent)
        self.conversation_border.configure(highlightbackground=self.accent)
        self.composer_border.configure(highlightbackground=self.accent)
        self.input.configure(insertbackground=self.accent)
        self.send_button.configure(
            text="➤" if self.mode == "chat" else "</>", bg=self.accent,
            activebackground=self.accent,
        )
        self.copy_btn.configure(fg=self.accent)
        self.conversation.tag_configure("meta", foreground=self.accent)
        self.codex_button.configure(
            bg=ORANGE if self.mode == "codex" else PANEL,
            fg=BLACK if self.mode == "codex" else ORANGE,
            activebackground=ORANGE, activeforeground=BLACK
        )
        self.chat_button.configure(
            bg=SKY if self.mode == "chat" else PANEL,
            fg=BLACK if self.mode == "chat" else SKY,
            activebackground=SKY, activeforeground=BLACK,
        )
        self.help_label.configure(
            text="Ctrl + Entrée : envoyer  •  Mode " +
                 ("Chat (bleu ciel)" if self.mode == "chat" else "Codex (orange)")
        )

    def _new_chat(self) -> None:
        if self.busy:
            self.status_var.set("Arrête la génération avant une nouvelle conversation.")
            return
        self.conversations[self.mode] = []
        self.last_response = ""
        self.title_label.configure(text="NOUVELLE CONVERSATION")
        self._render_conversation()
        self.status_var.set("Nouvelle conversation démarrée.")

    def _render_conversation(self) -> None:
        self.conversation.configure(state="normal")
        self.conversation.delete("1.0", "end")
        history = self.conversations[self.mode]
        if not history:
            self.conversation.insert("end", "MARCEAU CODEXLOCAL\n", "meta")
            self.conversation.insert(
                "end",
                "Assistant de code local • Windows / Ubuntu / Kali\n\n"
                "Choisis un agent en haut, puis écris ton message.\n"
                "Le modèle local Ollama fonctionne sans Internet une fois installé.\n\n",
                "assistant",
            )
        else:
            for i, (role, content) in enumerate(history):
                self.conversation.insert(
                    "end", ("VOUS\n" if role == "user" else "MARCEAU\n"), "meta"
                )
                self.conversation.insert("end", content, role)
                if role == "assistant" and self.busy and i == len(history) - 1:
                    self.conversation.mark_set("stream_pos", "end-1c")
                    self.conversation.mark_gravity("stream_pos", tk.RIGHT)
                self.conversation.insert("end", "\n\n")
        self.conversation.configure(state="disabled")
        self.conversation.see("end")

    def _ctrl_enter(self, _event: tk.Event) -> str:
        self._send()
        return "break"

    def _send(self) -> None:
        if self.busy:
            self._stop()
            return
        message = self.input.get("1.0", "end-1c").strip()
        if not message:
            return
        agent = self.agent_var.get()
        model = self.model_var.get().strip()
        history = self.conversations[self.mode]
        prompt = make_prompt(self.mode, history, message)
        if len(history) == 0:
            self.title_label.configure(text=(message[:39] + "…") if len(message) > 39 else message)
        history.append(("user", message))
        history.append(("assistant", ""))
        self.input.delete("1.0", "end")
        self.busy = True
        self.send_button.configure(text="■")
        self.codex_button.configure(state="disabled")
        self.chat_button.configure(state="disabled")
        self.agent_choice.configure(state="disabled")
        self.status_var.set("Génération en cours…")
        self._render_conversation()
        threading.Thread(
            target=self._worker, args=(agent, model, prompt, self.workdir),
            daemon=True,
        ).start()

    def _worker(self, agent: str, model: str, prompt: str, workdir: Path) -> None:
        try:
            found = available_agents()
            if agent == "auto":
                agent = (
                    "ollama" if "ollama" in found and list_ollama_models()
                    else next((a for a in ("codex", "claude", "gemini") if a in found), "")
                )
            if not agent or agent not in found:
                raise RuntimeError(
                    "Aucun agent utilisable. Vérifie l'installation dans le PATH."
                )
            if agent == "ollama":
                models = list_ollama_models()
                if not model:
                    model = choose_ollama_model(models)
                if model not in models:
                    raise RuntimeError(
                        "Modèle Ollama non installé : " + (model or "(aucun)")
                    )
            cmd = build_command(agent, prompt, model)
            self.events.put(("agent", agent))
            # subprocess sans shell : aucun texte utilisateur n'est execute par le GUI.
            process = subprocess.Popen(
                cmd, cwd=str(workdir), stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT, text=True, encoding="utf-8",
                errors="replace", bufsize=1,
            )
            self.process = process
            assert process.stdout is not None
            for line in process.stdout:
                self.events.put(("delta", line))
            code = process.wait()
            self.events.put(("done", code))
        except (OSError, RuntimeError, ValueError) as exc:
            self.events.put(("error", str(exc)))
        finally:
            self.process = None

    def _drain_queue(self) -> None:
        try:
            for _ in range(150):
                kind, data = self.events.get_nowait()
                if kind == "models":
                    models = list(data) if isinstance(data, list) else []
                    self.model_choice.configure(values=models)
                    if not self.model_var.get() and models:
                        self.model_var.set(choose_ollama_model(models))
                elif kind == "agent":
                    self.status_var.set(f"Agent : {data} • génération…")
                elif kind == "delta":
                    chunk = str(data)
                    conversation = self.conversations[self.mode]
                    if conversation and conversation[-1][0] == "assistant":
                        role, current = conversation[-1]
                        conversation[-1] = (role, current + chunk)
                        self.conversation.configure(state="normal")
                        self.conversation.insert("stream_pos", chunk, "assistant")
                        self.conversation.configure(state="disabled")
                        self.conversation.see("end")
                elif kind == "error":
                    self._finish(f"Erreur : {data}")
                elif kind == "done":
                    status = (
                        "Réponse terminée." if data == 0
                        else f"Agent terminé avec code {data}."
                    )
                    self._finish(status)
        except queue.Empty:
            pass
        try:
            self.root.after(80, self._drain_queue)
        except tk.TclError:
            pass

    def _finish(self, status: str) -> None:
        self.busy = False
        self.send_button.configure(
            text="➤" if self.mode == "chat" else "</>"
        )
        self.codex_button.configure(state="normal")
        self.chat_button.configure(state="normal")
        self.agent_choice.configure(state="readonly")
        conversation = self.conversations[self.mode]
        if conversation and conversation[-1][0] == "assistant":
            self.last_response = conversation[-1][1]
        self.status_var.set(status)
        self._render_conversation()

    def _stop(self) -> None:
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()
            self.status_var.set("Arrêt demandé…")
        else:
            self.status_var.set("La génération est en cours de démarrage…")

    def _copy_answer(self) -> None:
        if not self.last_response:
            self.status_var.set("Aucune réponse à copier.")
            return
        self.root.clipboard_clear()
        self.root.clipboard_append(self.last_response)
        self.status_var.set("Dernière réponse copiée.")

    def _on_close(self) -> None:
        if self.process is not None and self.process.poll() is None:
            self.process.terminate()
        self.root.destroy()


def main() -> int:
    parser = argparse.ArgumentParser(description="MARCEAU CodexLocal PC — fenêtre graphique")
    parser.add_argument(
        "--workdir", type=Path, default=Path.cwd(), help="Dossier de travail des agents CLI"
    )
    args = parser.parse_args()
    if not args.workdir.is_dir():
        parser.error("Le dossier --workdir n'existe pas.")
    try:
        root = tk.Tk()
    except tk.TclError as exc:
        print(f"Écran graphique indisponible : {exc}", file=sys.stderr)
        print("Utilise python pc/codexlocal.py en mode terminal.", file=sys.stderr)
        return 1
    CodexDesktop(root, args.workdir.resolve())
    root.mainloop()
    return 0


if __name__ == "__main__":
    sys.exit(main())
