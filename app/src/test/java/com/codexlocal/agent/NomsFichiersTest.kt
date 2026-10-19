package com.codexlocal.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NomsFichiersTest {

    @Test fun recognisesFileNames() {
        listOf(
            "index.html", "js/app.js", "src/main/Main.kt", "Dockerfile", "docs/Makefile", ".gitignore",
            "style.css", "gradle.properties", "package.json", "README.md", "app.test.ts", "mon fichier".replace(' ', '_') + ".py"
        ).forEach { assertTrue(it, NomsFichiers.ressembleAUnFichier(it)) }
    }

    @Test fun rejectsWhatIsNotAFile() {
        listOf(
            "", "bonjour", "exemple.com", "3.14", "v1.2", "a b.txt", "../secret.txt", "/etc/passwd",
            "http://x.fr/a.html", "x.", "Voici.", "html", "python", "fin.Ensuitelongue"
        ).forEach { assertFalse(it, NomsFichiers.ressembleAUnFichier(it)) }
    }

    @Test fun titleLineInAllTheUsualShapes() {
        mapOf(
            "index.html" to "index.html",
            "**index.html**" to "index.html",
            "`index.html`" to "index.html",
            "### index.html" to "index.html",
            "### 2. css/style.css" to "css/style.css",
            "1. **main.py**" to "main.py",
            "- `src/app.js`" to "src/app.js",
            "Fichier : main.py" to "main.py",
            "**Fichier 1 :** index.html" to "index.html",
            "File: utils.py" to "utils.py",
            "📄 index.html" to "index.html",
            "index.html :" to "index.html",
            "index.html — la page d'accueil" to "index.html",
            "style.css (les couleurs)" to "style.css",
            "Crée le fichier index.html :" to "index.html",
            "Voici le fichier `main.py` :" to "main.py",
            "Create a file named app.js" to "app.js"
        ).forEach { (ligne, nom) -> assertEquals(ligne, nom, NomsFichiers.nomDansLigne(ligne)) }
    }

    @Test fun ordinarySentencesAreNotTitles() {
        listOf(
            "Voici comment faire :",
            "Lance ensuite la commande suivante :",
            "Ce code utilise le fichier exemple.com pour tout",
            "Voici un exemple avec main.py qui est très simple et très utile pour tout le monde ici",
            "",
            "   "
        ).forEach { assertNull(it, NomsFichiers.nomDansLigne(it)) }
    }

    @Test fun nameInTheOpeningLine() {
        assertEquals("main.py", NomsFichiers.nomDansInfo("python title=\"main.py\""))
        assertEquals("src/App.tsx", NomsFichiers.nomDansInfo("tsx filename=src/App.tsx"))
        assertEquals("index.html", NomsFichiers.nomDansInfo("html:index.html"))
        assertEquals("main.py", NomsFichiers.nomDansInfo("main.py"))
        assertEquals("a.js", NomsFichiers.nomDansInfo("js {file=a.js}"))
        assertNull(NomsFichiers.nomDansInfo("python"))
        assertNull(NomsFichiers.nomDansInfo(""))
    }

    @Test fun languageInTheOpeningLine() {
        assertEquals("python", NomsFichiers.langageDansInfo("python title=\"main.py\""))
        assertEquals("kotlin", NomsFichiers.langageDansInfo("kotlin"))
        assertEquals("", NomsFichiers.langageDansInfo("main.py"))
        assertEquals("", NomsFichiers.langageDansInfo(""))
    }

    @Test fun nameInTheFirstLineComment() {
        mapOf(
            "<!-- index.html -->" to "index.html",
            "// main.js" to "main.js",
            "# main.py" to "main.py",
            "// file: src/App.kt" to "src/App.kt",
            "/* style.css */" to "style.css",
            "-- schema.sql" to "schema.sql"
        ).forEach { (ligne, nom) -> assertEquals(ligne, nom, NomsFichiers.nomDansPremiereLigne(ligne)) }
        listOf("// ajoute les points", "# TODO", "val a = 1", "<html>", "").forEach {
            assertNull(it, NomsFichiers.nomDansPremiereLigne(it))
        }
    }
}
