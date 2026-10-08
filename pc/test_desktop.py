"""Tests headless pour l'interface PC, sans ouvrir une fenetre Tk."""
import unittest

from codexlocal_gui import make_prompt
from codexlocal import build_command


class DesktopPromptTests(unittest.TestCase):
    def test_codex_has_code_instructions(self):
        prompt = make_prompt("codex", [], "Écris une fonction Python")
        self.assertIn("CODEX", prompt)
        self.assertIn("Écris une fonction Python", prompt)
        self.assertIn("Assistant:", prompt)

    def test_chat_has_conversation_instructions(self):
        prompt = make_prompt("chat", [], "Salut !")
        self.assertIn("CHAT", prompt)
        self.assertIn("Salut !", prompt)

    def test_recent_history_is_included(self):
        prompt = make_prompt(
            "chat", [("user", "Qui es-tu ?"), ("assistant", "MARCEAU")], "Merci"
        )
        self.assertIn("Qui es-tu ?", prompt)
        self.assertIn("MARCEAU", prompt)
        self.assertIn("Utilisateur: Merci", prompt)

    def test_large_history_does_not_remove_mode(self):
        prompt = make_prompt("codex", [("user", "x" * 30000)] * 8, "ok")
        self.assertTrue(prompt.startswith("Tu es MARCEAU CodexLocal en mode CODEX"))
        self.assertLess(len(prompt), 15000)

    def test_codex_cli_remains_read_only(self):
        command = build_command("codex", "Salut", "")
        self.assertIn("read-only", command)
        self.assertNotIn("danger-full-access", command)

    def test_ollama_command_does_not_use_shell(self):
        self.assertEqual(
            build_command("ollama", "bonjour", "qwen2.5-coder:7b"),
            ["ollama", "run", "qwen2.5-coder:7b", "bonjour"],
        )


if __name__ == "__main__":
    unittest.main()
