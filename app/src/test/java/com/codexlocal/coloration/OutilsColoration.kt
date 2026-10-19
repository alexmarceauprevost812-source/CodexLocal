package com.codexlocal.coloration

import org.junit.Assert.assertTrue

/** Les morceaux colores d'un code, sous la forme (texte, genre). */
internal fun lire(code: String, langage: String): List<Pair<String, Genre>> =
    AnalyseurCode.analyser(code, langage).map { code.substring(it.debut, it.fin) to it.genre }

/** Le genre du n-ieme morceau colore qui vaut exactement [morceau], ou null s'il n'est pas colore. */
internal fun genreDe(code: String, langage: String, morceau: String, occurrence: Int = 0): Genre? =
    lire(code, langage).filter { it.first == morceau }.getOrNull(occurrence)?.second

/** Tous les morceaux d'un genre donne. */
internal fun morceauxDe(code: String, langage: String, genre: Genre): List<String> =
    lire(code, langage).filter { it.second == genre }.map { it.first }

/** Verifie ce que l'ecran suppose : tries, sans chevauchement, non vides, dans le texte, sans couper un emoji. */
internal fun verifierJetons(code: String, jetons: List<Jeton>, contexte: String = "") {
    var precedent = 0
    for (j in jetons) {
        val ou = "$contexte $j dans «${code.take(60)}»"
        assertTrue("jeton vide ou a l'envers : $ou", j.debut < j.fin)
        assertTrue("jeton hors du texte : $ou", j.debut >= 0 && j.fin <= code.length)
        assertTrue("jetons dans le desordre ou qui se chevauchent : $ou", j.debut >= precedent)
        assertTrue("coupe un emoji en deux (debut) : $ou", !coupeEmoji(code, j.debut))
        assertTrue("coupe un emoji en deux (fin) : $ou", !coupeEmoji(code, j.fin))
        precedent = j.fin
    }
}

private fun coupeEmoji(code: String, position: Int): Boolean =
    position > 0 && position < code.length &&
        Character.isHighSurrogate(code[position - 1]) && Character.isLowSurrogate(code[position])

/** Un petit exemple par langage : sert aux tests de robustesse. */
internal val EXEMPLES: List<Pair<String, String>> = listOf(
    "kotlin" to "package a\nimport b.c\n\n/** doc */\n@Composable\nfun greet(name: String, n: Int = 3): List<String> {\n    val x = \"Hi \$name \${n + 1}\\n\" // fin\n    return listOf(x).map { it.length }\n}\nconst val MAX_SIZE = 0xFF_L\nenum class Color { RED }\nfun String.shout() = uppercase()",
    "java" to "public class A<T> extends B implements C {\n  @Override public void run() { String s = \"x\\t\"; int n = 0b101; }\n}",
    "python" to "@dataclass\nclass Foo(Base):\n    def run(self, x: int = 5):\n        \"\"\"doc\"\"\"\n        print(f'a{x}')  # hi\n        return True if x else None",
    "javascript" to "const a = `x \${b.c(1)} y`;\nfunction foo(a, b) { return a?.b.c + 1.5e3 }\nclass App extends React.Component {}\nconsole.log(\"#FF9A3D\", 'it\\'s');\nconst r = /ab+c/gi;",
    "typescript" to "interface A { x: number; y?: string }\nexport const f = <T,>(a: T): T => a;\ntype B = A | null;",
    "bash" to "#!/bin/bash\nNAME=\"world\"\necho \"Hello \$NAME \${HOME}\" | grep -i --color=auto 'x'\nfor f in *.txt; do cp -r \$f /tmp; done\nif [ -f \$(pwd)/a ]; then exit 1; fi",
    "c" to "#include <stdio.h>\n#define MAX 10\nint main(void) {\n  char c = 'x'; printf(\"%d\\n\", MAX);\n  return 0;\n}",
    "cpp" to "template <typename T>\nclass Box { public: T v; Box(T t) : v(t) {} };\nauto x = std::make_unique<Box<int>>(3);",
    "csharp" to "using System;\nnamespace A { class P { static void Main() { var s = \$\"a{1}\"; Console.WriteLine(@\"x\"); } } }",
    "go" to "package main\nimport \"fmt\"\nfunc main() { x := []int{1, 2}; fmt.Println(`raw`, x) }",
    "rust" to "fn main<'a>(x: &'a str) -> Option<u32> { println!(\"hi\"); None }\n#[derive(Debug)]\nstruct S { a: i32 }",
    "swift" to "import UIKit\nstruct A { var x: Int = 0; func f() -> String { \"v\\(x)\" } }",
    "dart" to "void main() { var s = 'a\${1 + 2}'; print(s); }",
    "php" to "<?php\n\$a = \"x\$b\"; function f(\$x) { return \$x->y; }\n?>",
    "ruby" to "class A\n  def b(x) = puts \"v#{x}\" # c\nend\n:sym",
    "powershell" to "\$a = Get-ChildItem -Path . | Where-Object { \$_.Length -gt 5 }\nif (\$a) { Write-Host \"ok\" } # c",
    "sql" to "SELECT name, COUNT(*) FROM users WHERE age >= 18 -- adults\nGROUP BY name;",
    "lua" to "local function f(a) return a .. 'x' end -- c\n--[[ bloc ]]",
    "groovy" to "def x = 'a'\ntask build { doLast { println \"v\$x\" } }",
    "scala" to "object A extends App { val x: Int = 3; def f = x }",
    "perl" to "my \$x = 'a'; print \"v\$x\\n\"; # c",
    "r" to "x <- c(1, 2); f <- function(a) a + 1 # c",
    "cmake" to "cmake_minimum_required(VERSION 3.22)\nproject(a)\nadd_library(b SHARED \${SRC})",
    "makefile" to "CC=gcc\nall: main.o\n\t\$(CC) -o a main.o # c",
    "dockerfile" to "FROM alpine:3.19\nRUN echo \"a\" && apk add b\nCMD [\"sh\"]",
    "json" to "{\n  \"name\": \"x\", \"n\": [1, 2.5, true, null], \"c\": \"#ff0000\"\n}",
    "html" to "<!DOCTYPE html>\n<html lang=\"fr\"><body style=\"color:#ff0000\"><p class='a'>Hi &amp; bye<br/></p><script>let a = 1;</script><style>p { color: red; }</style><!-- c --></body></html>",
    "xml" to "<?xml version=\"1.0\"?>\n<LinearLayout android:id=\"@+id/main\" android:background=\"#FF9A3D\">\n  <!-- c --><TextView android:text=\"@string/hi\"/>\n</LinearLayout>",
    "css" to "/* c */\n:root { --main: #ff9a3d; }\n.btn:hover > a, #id { color: rgba(255, 0, 0, .5); margin: -4px 0 12px; background: var(--main) !important; }\n@media (max-width: 600px) { body { font: 12px/1.5 \"Arial\"; } }",
    "yaml" to "# conf\nname: app\nversion: 1.2\nitems:\n  - a: true\n  - \"b\": null\nrun: |\n  echo \"x: y\"\nnext: &anc 5\nref: *anc # end",
    "toml" to "[package]\nname = \"x\"\nversion = \"0.1.0\"\ndeps = [1, 2]\n# c",
    "ini" to "; c\n[sec]\nkey=value\ncolor=#ff0000\nn = 5",
    "markdown" to "# Titre\n\nSome **bold** and `code` and [lien](http://x.y) text.\n- item\n1. un\n> cite\n\n```kotlin\nval a = 1\n```\n",
    "plain" to "du texte 123 \"avec\" des (signes)",
    "inconnu" to "foo(\"bar\") // baz 12"
)
