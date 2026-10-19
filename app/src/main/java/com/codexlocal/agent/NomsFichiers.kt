package com.codexlocal.agent

import com.codexlocal.project.Langages
import com.codexlocal.studio.StudioParser

/**
 * Retrouve le NOM d'un fichier dans ce que l'IA ecrit, sous toutes les formes que prennent les
 * petits modeles : titre juste avant le bloc de code (`**index.html**`, `### 2. css/style.css`,
 * `Fichier : main.py`), nom dans la ligne d'ouverture (```` ```python title="main.py" ````,
 * ```` ```main.py ````), ou commentaire en premiere ligne du code (`<!-- index.html -->`).
 * Code pur, sans dependance Android.
 */
object NomsFichiers {

    private val CHEMIN = Regex("""^[\p{L}\p{N}_.@+\-]+(?:/[\p{L}\p{N}_.@+\-]+)*$""")

    /** Fichiers connus qui n'ont pas d'extension. */
    private val SANS_EXTENSION = setOf(
        "dockerfile", "makefile", "gemfile", "rakefile", "procfile", "license", "licence", "readme",
        "cmakelists.txt"
    )

    /** Fichiers dont le nom commence par un point. */
    private val POINT_CONNUS = setOf(
        "gitignore", "env", "htaccess", "gitattributes", "editorconfig", "dockerignore", "npmrc", "babelrc",
        "eslintrc", "prettierrc"
    )

    /** Fins de noms de domaine : « exemple.com » n'est pas un fichier. */
    private val DOMAINES = setOf("com", "org", "net", "io", "fr", "ca", "edu", "gov")

    /** Debut de ligne a ignorer : titre markdown, puce, numero, citation, emoji. */
    private val DEBUT = Regex("""^(?:[#>\-*+•–—]+\s*|\d+\s*[.)]\s*|\p{So}+\s*|\p{Cs}+\s*)+""")

    /** « Fichier : », « File - », « nom du fichier = »... */
    private val ETIQUETTE = Regex(
        """^(?:(?:le|la|un|ton|ce|mon)\s+)?(?:fichier|file|filename|nom(?:\s+du\s+fichier)?|name|titre|title|chemin|path)""" +
            """(?:\s*(?:n°|no|#)?\s*\d+)?\s*[:：=\-–—]\s*""",
        RegexOption.IGNORE_CASE
    )

    /** Mots qui annoncent un fichier dans une phrase (« Crée le fichier index.html »). */
    private val ANNONCE = Regex("""\b(?:fichier|file|filename|nom|chemin|path|cr[ée]+e?[rz]?|create|save|enregistre\w*|ajoute\w*|ecris|écris|write)\b""",
        RegexOption.IGNORE_CASE)

    /** Vrai quand [brut] a la forme d'un chemin de fichier (`index.html`, `js/app.js`, `Dockerfile`). */
    fun ressembleAUnFichier(brut: String): Boolean {
        val t = StudioParser.nettoyerChemin(brut)
        if (t.isEmpty() || t.length > 100) return false
        if (!CHEMIN.matches(t) || t.contains("..") || t.startsWith("/")) return false
        val nom = t.substringAfterLast('/')
        if (nom.lowercase() in SANS_EXTENSION) return true
        val point = nom.lastIndexOf('.')
        if (point < 0) return false
        val extension = nom.substring(point + 1)
        if (point == 0) return extension.lowercase() in POINT_CONNUS
        if (!extension.any { it.isLetter() } || !extension.all { it.isLetterOrDigit() }) return false
        if (extension.lowercase() in DOMAINES) return false
        // les extensions longues connues (gradle.properties) comptent aussi
        return extension.length in 1..8 || Langages.estTexte(nom)
    }

    /**
     * Le nom de fichier annonce par une ligne de texte, ou null. La ligne est soit le nom seul
     * (avec decoration), soit « nom — description », soit une phrase qui annonce le fichier.
     */
    fun nomDansLigne(ligne: String): String? {
        if (ligne.length > 140) return null
        var s = DEBUT.replace(ligne.trim(), "").trim()
        s = s.trimEnd(':', '：', ' ').replace("**", "").replace("__", "")
        s = s.trim('`', '"', '\'', '*', '_', '(', ')', '[', ']', ' ')
        s = ETIQUETTE.replace(s, "").trim('`', '"', '\'', '*', '_', ' ')
        if (s.isEmpty()) return null

        // « index.html (page d'accueil) », « index.html — page d'accueil »
        val premier = s.substringBefore(' ')
        if (s != premier && ressembleAUnFichier(premier)) {
            val reste = s.substring(premier.length).trimStart()
            if (reste.startsWith("(") || reste.startsWith("-") || reste.startsWith("–") ||
                reste.startsWith("—") || reste.startsWith(":")
            ) {
                return StudioParser.nettoyerChemin(premier)
            }
        }
        val dernier = s.substringAfterLast(' ').trim('`', '"', '\'', '*', '_', '(', ')', ':')
        if (ressembleAUnFichier(dernier)) {
            if (s == dernier || s.trim('`', '"', '\'', '*', '_', '(', ')', ':', ' ') == dernier) {
                return StudioParser.nettoyerChemin(dernier)
            }
            val avant = s.substring(0, s.length - s.substringAfterLast(' ').length)
            return if (ANNONCE.containsMatchIn(avant)) StudioParser.nettoyerChemin(dernier) else null
        }
        // « Crée le fichier `main.py` pour lancer ça » : le nom est au milieu de la phrase
        if (!ANNONCE.containsMatchIn(s)) return null
        val candidats = s.split(' ')
            .map { it.trim('`', '"', '\'', '*', '_', '(', ')', ':', ',', ';', '!', '?', '.') }
            .filter { ressembleAUnFichier(it) }
            .map { StudioParser.nettoyerChemin(it) }
            .distinct()
        return candidats.singleOrNull()
    }

    private val CLE_VALEUR = Regex(
        """(?:title|filename|file|name|path|fichier|nom)\s*=\s*["']?([^"'\s}]+)["']?""",
        RegexOption.IGNORE_CASE
    )

    /** Le nom de fichier ecrit dans la ligne d'ouverture d'un bloc, apres les ```. */
    fun nomDansInfo(info: String): String? {
        val i = info.trim().trim('{', '}', ' ')
        CLE_VALEUR.find(i)?.groupValues?.get(1)?.let { valeur ->
            if (ressembleAUnFichier(valeur)) return StudioParser.nettoyerChemin(valeur)
        }
        for (jeton in i.split(Regex("""[\s:,]+""")).filter { it.isNotBlank() }) {
            val propre = jeton.trim('"', '\'', '`', '{', '}', '[', ']')
            if (ressembleAUnFichier(propre)) return StudioParser.nettoyerChemin(propre)
        }
        return null
    }

    /** Le langage ecrit dans la ligne d'ouverture (« python » dans ```` ```python title="a.py" ````), ou vide. */
    fun langageDansInfo(info: String): String {
        val premier = info.trim().trim('{', '}').split(Regex("""[\s:,=]+""")).firstOrNull().orEmpty()
        return if (premier.isEmpty() || ressembleAUnFichier(premier)) "" else premier.trim('.', '"', '\'')
    }

    private val COMMENTAIRE_NOM = Regex(
        """^\s*(?://+|#+|--|;+|%+|/\*+|<!--|\*)\s*(?:(?:file|filename|fichier|path|chemin)\s*[:=]?\s*)?(\S+?)\s*(?:\*/|-->)?\s*$""",
        RegexOption.IGNORE_CASE
    )

    /** Le nom de fichier ecrit en commentaire sur la premiere ligne du code (`<!-- index.html -->`). */
    fun nomDansPremiereLigne(ligne: String): String? {
        val m = COMMENTAIRE_NOM.matchEntire(ligne) ?: return null
        val candidat = m.groupValues[1]
        return if (ressembleAUnFichier(candidat)) StudioParser.nettoyerChemin(candidat) else null
    }
}
