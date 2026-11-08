package com.codexlocal.agent

/**
 * Un probleme repere par la relecture automatique dans un fichier que l'IA vient d'ecrire.
 * [ligne] vaut null quand il concerne le fichier entier.
 */
data class Probleme(val fichier: String, val ligne: Int?, val message: String) {
    fun texte(): String = fichier + (ligne?.let { " (ligne $it)" } ?: "") + " : " + message
}

/**
 * Relit les fichiers ecrits par l'IA et repere, SANS rien executer, les erreurs typiques des
 * petits modeles locaux : fichier abrege (« ... », « reste du code »), parentheses ou accolades
 * mal fermees, JSON invalide, page HTML qui reference un fichier inexistant, fichier existant
 * reecrit beaucoup plus court qu'avant.
 *
 * Priorite a la PRECISION : chaque fausse alerte coute un tour de generation (lent sur un
 * telephone), donc on ne signale que ce qui est presque certainement faux. Code pur, sans Android.
 */
object VerificateurCode {

    /** Nombre maximal de problemes renvoyes : une liste courte tient dans la memoire du modele. */
    const val MAX_PROBLEMES = 6

    /** Un fichier plus gros n'est pas relu (la relecture doit rester instantanee). */
    const val MAX_CARACTERES = 150_000

    /** Un fichier existant d'au moins ce nombre de lignes utiles, reecrit 3 fois plus court, est suspect. */
    const val LIGNES_MIN_ANCIEN = 60

    /**
     * @param ecrits chemin -> contenu des fichiers texte que l'IA vient d'ecrire
     * @param existants chemins de tous les fichiers du projet (images comprises)
     * @param anciens chemin -> contenu avant la reecriture, pour les fichiers qui existaient deja
     */
    fun verifier(
        ecrits: Map<String, String>,
        existants: Set<String> = emptySet(),
        anciens: Map<String, String> = emptyMap()
    ): List<Probleme> {
        val connus = existants + ecrits.keys
        val problemes = ArrayList<Probleme>()
        for ((chemin, texte) in ecrits) {
            if (texte.length > MAX_CARACTERES) continue
            problemes += verifierFichier(chemin, texte, connus, anciens[chemin])
            if (problemes.size >= MAX_PROBLEMES) break
        }
        return problemes.distinct().take(MAX_PROBLEMES)
    }

    // ------------------------------------------------------------------ un fichier

    private fun verifierFichier(
        chemin: String, texte: String, connus: Set<String>, ancien: String?
    ): List<Probleme> {
        val sortie = ArrayList<Probleme>()
        val ext = extension(chemin)

        if (ext in LANGAGES_CODE) {
            abrege(texte, ext)?.let { (ligne, extrait) ->
                sortie += Probleme(
                    chemin, ligne,
                    "morceau abrégé (« $extrait ») : le fichier doit être écrit EN ENTIER, " +
                        "sans « ... » ni « reste du code »"
                )
            }
        }
        if (ancien != null) perteDeContenu(chemin, ancien, texte)?.let { sortie += it }

        when (ext) {
            "json" -> if (!estJsonAvecCommentaires(chemin)) {
                JsonStrict(texte).valider()?.let { (l, m) -> sortie += Probleme(chemin, l, "JSON invalide : $m") }
            }
            "js", "mjs", "cjs", "ts" -> equilibre(texte, JS)?.let { (l, m) -> sortie += Probleme(chemin, l, m) }
            "css" -> equilibre(texte, CSS)?.let { (l, m) -> sortie += Probleme(chemin, l, m) }
            "py" -> equilibre(texte, PYTHON)?.let { (l, m) -> sortie += Probleme(chemin, l, m) }
            "java" -> equilibre(texte, JAVA)?.let { (l, m) -> sortie += Probleme(chemin, l, m) }
            "kt", "kts" -> equilibre(texte, KOTLIN)?.let { (l, m) -> sortie += Probleme(chemin, l, m) }
            "go" -> equilibre(texte, GO)?.let { (l, m) -> sortie += Probleme(chemin, l, m) }
            "html", "htm" -> verifierHtml(chemin, texte, connus, sortie)
        }
        return sortie
    }

    internal fun extension(chemin: String): String =
        chemin.substringAfterLast('/').substringAfterLast('.', "").lowercase()

    /** tsconfig.json, jsconfig.json : du JSON qui accepte les commentaires. */
    private fun estJsonAvecCommentaires(chemin: String): Boolean {
        val nom = chemin.substringAfterLast('/').lowercase()
        return nom.startsWith("tsconfig") || nom.startsWith("jsconfig")
    }

    // ------------------------------------------------------------------ fichier abrege

    private val LANGAGES_CODE = setOf(
        "js", "mjs", "cjs", "jsx", "ts", "tsx", "java", "kt", "kts", "go", "rs", "php", "rb", "lua",
        "sh", "py", "css", "html", "htm", "c", "h", "cpp", "hpp", "cs", "swift", "dart"
    )

    /** Appliquees au texte du commentaire (sans « // », « # »...) : un vrai « raccourci » commence ainsi. */
    private val ELISION = listOf(
        // « reste du code », « rest of the file », « suite du script »
        Regex(
            """^\W*(the |your |le |ton |votre )?(rest of( the| your)? (code|file|script|program|function|class)|""" +
                """reste du (code|fichier|programme|script|css|js|html)|suite du (code|fichier|programme|script))""",
            RegexOption.IGNORE_CASE
        ),
        // « autres fonctions inchangées »
        Regex(
            """^\W*autres? (fonctions?|m[ée]thodes?|styles?|r[èe]gles?|pages?)\b.{0,30}""" +
                """(inchang|identique|existant|unchanged|same|existing|\.\.\.|…)""",
            RegexOption.IGNORE_CASE
        ),
        // « ... existing code ... »
        Regex(
            """^\W*(\.\.\.|…)\s*(existing|existant|reste|rest|autres?|other|more|suite|previous|pr[ée]c[ée]dent)\b""",
            RegexOption.IGNORE_CASE
        ),
        // « ajoute ton code ici », « your code here »
        Regex(
            """^\W*(ton|votre|your|ajoute[rz]?|add|put|mets?)\b.{0,20}\b(code|logique|logic)\b.{0,15}\b(ici|here)\b""",
            RegexOption.IGNORE_CASE
        ),
        // « à compléter », « TODO: implement »
        Regex(
            """^\W*((à|a) compl[ée]ter|to be implemented|not implemented yet|""" +
                """todo:?\s*(implement|impl[ée]menter|compl[ée]ter)\W*(this|here|ici|me|it|la fonction|cette fonction|function)?\W*$)""",
            RegexOption.IGNORE_CASE
        )
    )

    /** Un raccourci est un commentaire COURT : une longue phrase qui parle du « reste du code » decrit, elle n'abrege pas. */
    private const val COMMENTAIRE_MAX = 50

    private fun texteDeCommentaire(ligne: String): String {
        var t = ligne
        for (m in listOf("<!--", "/*", "///", "//", "#", "--", "*")) {
            if (t.startsWith(m)) { t = t.removePrefix(m); break }
        }
        return t.removeSuffix("-->").removeSuffix("*/").trim()
    }

    private fun estCommentaire(ligne: String) =
        ligne.startsWith("//") || ligne.startsWith("#") || ligne.startsWith("/*") ||
            ligne.startsWith("*") || ligne.startsWith("<!--") || ligne.startsWith("--")

    /**
     * Premiere ligne qui abrege le fichier : un « ... » seul (valide en Python, ou c'est « Ellipsis »),
     * ou un commentaire du genre « reste du code ». `{ ...etat }` en JavaScript n'est pas concerne :
     * on ne regarde que les lignes entieres.
     */
    private fun abrege(texte: String, ext: String): Pair<Int, String>? {
        var numero = 0
        for (brut in texte.lineSequence()) {
            numero++
            val l = brut.trim()
            if (l.isEmpty()) continue
            if ((l == "..." || l == "…") && ext != "py") return numero to l
            if (estCommentaire(l)) {
                val texteCom = texteDeCommentaire(l)
                if (texteCom.length <= COMMENTAIRE_MAX && ELISION.any { it.containsMatchIn(texteCom) }) {
                    return numero to l.take(60)
                }
            }
        }
        return null
    }

    private fun perteDeContenu(chemin: String, ancien: String, nouveau: String): Probleme? {
        val avant = ancien.lineSequence().count { it.isNotBlank() }
        if (avant < LIGNES_MIN_ANCIEN) return null
        val apres = nouveau.lineSequence().count { it.isNotBlank() }
        if (apres * 100 > avant * 35) return null
        return Probleme(
            chemin, null,
            "le fichier est passé de $avant à $apres lignes : as-tu supprimé du contenu par erreur ? " +
                "Réécris-le en entier, avec tout son contenu d'origine plus ta modification"
        )
    }

    // ------------------------------------------------------------------ HTML

    private val RESSOURCE = Regex(
        """<(script|link|img|audio|video|source|iframe|embed)\b[^>]*?\b(?:src|href)\s*=\s*(["'])([^"'\n]*)\2""",
        setOf(RegexOption.IGNORE_CASE)
    )
    private val SCRIPT_EN_LIGNE = Regex("""<script\b([^>]*)>(.*?)</script\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val STYLE_EN_LIGNE = Regex("""<style\b([^>]*)>(.*?)</style\s*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val ATTR_SRC = Regex("""\bsrc\s*=""", RegexOption.IGNORE_CASE)
    private val ATTR_TYPE = Regex("""\btype\s*=\s*["']?([^"'\s>]+)""", RegexOption.IGNORE_CASE)

    private val EXTERNES = listOf(
        "http:", "https:", "//", "data:", "mailto:", "tel:", "javascript:", "blob:", "file:", "about:", "#"
    )
    private val EXT_TEXTE = setOf("js", "mjs", "css", "json", "svg", "html", "htm", "txt", "md")
    private val EXT_BINAIRE = setOf(
        "png", "jpg", "jpeg", "gif", "webp", "mp3", "wav", "ogg", "mp4", "webm",
        "woff", "woff2", "ttf", "otf", "wasm"
    )

    private fun verifierHtml(chemin: String, texte: String, connus: Set<String>, sortie: MutableList<Probleme>) {
        val dossier = chemin.substringBeforeLast('/', "")
        val dejaVus = HashSet<String>()
        for (m in RESSOURCE.findAll(texte)) {
            val brut = m.groupValues[3].trim()
            val cible = cibleLocale(dossier, brut) ?: continue
            if (cible in connus || !dejaVus.add(cible)) continue
            val ligne = ligneDe(texte, m.range.first)
            val casse = connus.firstOrNull { it.equals(cible, ignoreCase = true) }
            val message = when {
                casse != null ->
                    "« $brut » est appelé « $casse » dans le projet : respecte les majuscules et minuscules"
                extension(cible) in EXT_BINAIRE ->
                    "référence « $brut », mais ce fichier n'existe pas et tu ne peux pas créer une image " +
                        "ou un son : utilise seulement les fichiers listés dans le projet, ou un SVG, un emoji ou du CSS"
                else ->
                    "référence « $brut », mais ce fichier n'existe pas dans le projet : écris-le, ou corrige le nom"
            }
            sortie += Probleme(chemin, ligne, message)
        }
        for (m in SCRIPT_EN_LIGNE.findAll(texte)) {
            val attributs = m.groupValues[1]
            val contenu = m.groupValues[2]
            if (ATTR_SRC.containsMatchIn(attributs)) continue
            val type = ATTR_TYPE.find(attributs)?.groupValues?.get(1)?.lowercase()
            if (type != null && type !in TYPES_JS) continue
            if (contenu.isBlank() || "{{" in contenu || "{%" in contenu) continue
            val decalage = ligneDe(texte, m.groups[2]?.range?.first ?: 0) - 1
            Lecteur(contenu, JS, decalage).analyser()?.let { (l, msg) ->
                sortie += Probleme(chemin, l, "dans <script> : $msg")
            }
        }
        for (m in STYLE_EN_LIGNE.findAll(texte)) {
            val contenu = m.groupValues[2]
            if (contenu.isBlank() || "{{" in contenu || "{%" in contenu) continue
            val decalage = ligneDe(texte, m.groups[2]?.range?.first ?: 0) - 1
            Lecteur(contenu, CSS, decalage).analyser()?.let { (l, msg) ->
                sortie += Probleme(chemin, l, "dans <style> : $msg")
            }
        }
    }

    private val TYPES_JS = setOf("module", "text/javascript", "application/javascript")

    /**
     * Le chemin (relatif a la racine du projet) d'une ressource locale de la page, ou null quand
     * ce n'en est pas une (adresse en ligne, gabarit, favicon, extension inconnue...).
     */
    private fun cibleLocale(dossier: String, brut: String): String? {
        if (brut.isEmpty()) return null
        val minuscules = brut.lowercase()
        if (EXTERNES.any { minuscules.startsWith(it) }) return null
        if (brut.any { it == '{' || it == '$' || it == '+' || it == '%' || it == '<' }) return null
        val propre = brut.substringBefore('#').substringBefore('?')
        if (propre.isEmpty()) return null
        val ext = propre.substringAfterLast('.', "").lowercase()
        if ('/' in ext || (ext !in EXT_TEXTE && ext !in EXT_BINAIRE)) return null
        val segments = ArrayList<String>()
        if (!propre.startsWith("/")) segments.addAll(dossier.split('/').filter { it.isNotEmpty() })
        for (s in propre.split('/')) {
            when (s) {
                "", "." -> Unit
                ".." -> if (segments.isEmpty()) return null else segments.removeAt(segments.size - 1)
                else -> segments.add(s)
            }
        }
        return segments.joinToString("/")
    }

    private fun ligneDe(texte: String, position: Int): Int {
        var n = 1
        for (k in 0 until minOf(position, texte.length)) if (texte[k] == '\n') n++
        return n
    }

    // ------------------------------------------------------------------ parentheses et chaines

    /** Premier probleme d'equilibre (ligne, message), ou null. */
    private fun equilibre(texte: String, syntaxe: Syntaxe): Pair<Int, String>? =
        Lecteur(texte, syntaxe).analyser()

    private class Syntaxe(
        val commentaireLigne: String?,
        val commentaireBloc: Boolean,
        val triple: Boolean,
        val apostropheTriple: Boolean,
        val gabarit: Boolean,
        val backtick: Int, // 0 : rien, 1 : gabarit JavaScript avec ${}, 2 : chaine brute (Go), 3 : identifiant Kotlin
        val regex: Boolean
    )

    private val JS = Syntaxe("//", true, false, false, false, 1, true)
    private val CSS = Syntaxe(null, true, false, false, false, 0, false)
    private val PYTHON = Syntaxe("#", false, true, true, false, 0, false)
    private val JAVA = Syntaxe("//", true, true, false, false, 0, false)
    private val KOTLIN = Syntaxe("//", true, true, false, true, 3, false)
    private val GO = Syntaxe("//", true, false, false, false, 2, false)

    private val MOTS_AVANT_REGEX = setOf(
        "return", "typeof", "case", "do", "else", "in", "of", "delete", "void", "throw", "new", "yield", "await"
    )

    /**
     * Lit le code en sautant commentaires, chaines et expressions regulieres, et verifie que
     * ( [ { sont fermes dans le bon ordre. Une chaine non fermee sur sa ligne n'est PAS signalee
     * (on repart a la ligne suivante) : mieux vaut rater une erreur que fabriquer une fausse alerte.
     */
    private class Lecteur(private val t: String, private val s: Syntaxe, decalage: Int = 0) {
        private var i = 0
        private var ligne = 1 + decalage
        private val pile = ArrayList<Pair<Char, Int>>()
        private var erreur: Pair<Int, String>? = null

        // Dernier caractere significatif lu en mode code (hors espaces et commentaires), l'avant-dernier,
        // et le dernier mot : c'est ce qui dit si un « / » ouvre une regex ou est une division.
        private var prev = '\u0000'
        private var avant = '\u0000'
        private val mot = StringBuilder()

        private fun note(c: Char) {
            if (c.isWhitespace()) return
            val ident = c.isLetterOrDigit() || c == '_' || c == '$'
            if (ident && (prev.isLetterOrDigit() || prev == '_' || prev == '$')) mot.append(c)
            else { mot.setLength(0); if (ident) mot.append(c) }
            avant = prev
            prev = c
        }

        /** Apres une chaine, un gabarit ou une regex : une valeur vient d'etre lue (un « / » suivant divise). */
        private fun valeurLue() { mot.setLength(0); avant = prev; prev = '"' }

        fun analyser(): Pair<Int, String>? {
            code()
            if (erreur == null) {
                pile.lastOrNull()?.let { (c, l) ->
                    erreur = l to "« ${nom(c)} » ouvert ligne $l n'est jamais fermé (fichier coupé ou accolade oubliée ?)"
                }
            }
            return erreur
        }

        private fun nom(c: Char) = if (c == '$') "\${" else c.toString()

        /** Lit jusqu'a la fin du texte, ou jusqu'au `}` qui ferme un `${` (ouvert par l'appelant). */
        private fun code() {
            while (i < t.length && erreur == null) {
                val c = t[i]
                when {
                    c == '\n' -> { ligne++; i++ }
                    s.commentaireLigne != null && t.startsWith(s.commentaireLigne, i) ->
                        while (i < t.length && t[i] != '\n') i++
                    s.commentaireBloc && t.startsWith("/*", i) -> bloc()
                    s.triple && t.startsWith("\"\"\"", i) -> triple("\"\"\"")
                    s.apostropheTriple && t.startsWith("'''", i) -> triple("'''")
                    c == '"' || c == '\'' -> { chaine(c); valeurLue() }
                    c == '`' && s.backtick == 1 -> { gabaritJs(); valeurLue() }
                    c == '`' && s.backtick == 2 -> { brut(); valeurLue() }
                    c == '`' && s.backtick == 3 -> { identifiantKotlin(); valeurLue() }
                    c == '/' && s.regex && regexPossible() -> { regex(); valeurLue() }
                    c == '(' || c == '[' || c == '{' -> { pile.add(c to ligne); note(c); i++ }
                    c == ')' || c == ']' || c == '}' -> { note(c); if (fermer(c) != 0) return }
                    else -> { note(c); i++ }
                }
            }
        }

        /** 0 : ferme normalement ; 1 : fin d'une expression `${...}` ; -1 : erreur (enregistree). */
        private fun fermer(c: Char): Int {
            val attendu = when (c) { ')' -> '('; ']' -> '['; else -> '{' }
            val haut = pile.lastOrNull()
            if (haut == null) {
                erreur = ligne to "« $c » ligne $ligne ne ferme rien (aucun « $attendu » ouvert avant)"
                return -1
            }
            if (haut.first == '$') {
                if (c == '}') { pile.removeAt(pile.size - 1); i++; return 1 }
                erreur = ligne to "« $c » ligne $ligne alors que l'expression « \${ » ouverte ligne ${haut.second} n'est pas fermée"
                return -1
            }
            if (haut.first != attendu) {
                erreur = ligne to "« $c » ligne $ligne ne correspond pas à « ${haut.first} » ouvert ligne ${haut.second}"
                return -1
            }
            pile.removeAt(pile.size - 1)
            i++
            return 0
        }

        private fun bloc() {
            val debut = ligne
            val fin = t.indexOf("*/", i + 2)
            if (fin < 0) {
                erreur = debut to "commentaire /* ouvert ligne $debut, jamais fermé"
                return
            }
            for (k in i until fin) if (t[k] == '\n') ligne++
            i = fin + 2
        }

        /** Ouvre une expression `${...}` dans une chaine : `i` est sur le `$`. */
        private fun expression() {
            i += 2
            pile.add('$' to ligne)
            code()
        }

        private fun chaine(q: Char) {
            i++
            while (i < t.length && erreur == null) {
                val c = t[i]
                when {
                    c == '\\' -> { if (i + 1 < t.length && t[i + 1] == '\n') ligne++; i += 2 }
                    c == '\n' -> return // chaine non fermee : on ne l'accuse pas, on repart
                    c == q -> { i++; return }
                    c == '$' && s.gabarit && q == '"' && t.startsWith("{", i + 1) -> expression()
                    else -> i++
                }
            }
        }

        private fun triple(q: String) {
            val debut = ligne
            i += 3
            while (i < t.length && erreur == null) {
                val c = t[i]
                when {
                    c == '\\' -> { if (i + 1 < t.length && t[i + 1] == '\n') ligne++; i += 2 }
                    t.startsWith(q, i) -> { i += 3; return }
                    c == '\n' -> { ligne++; i++ }
                    c == '$' && s.gabarit && q == "\"\"\"" && t.startsWith("{", i + 1) -> expression()
                    else -> i++
                }
            }
            if (erreur == null) erreur = debut to "chaîne $q ouverte ligne $debut, jamais fermée"
        }

        private fun gabaritJs() {
            val debut = ligne
            i++
            while (i < t.length && erreur == null) {
                val c = t[i]
                when {
                    c == '\\' -> { if (i + 1 < t.length && t[i + 1] == '\n') ligne++; i += 2 }
                    c == '`' -> { i++; return }
                    c == '\n' -> { ligne++; i++ }
                    c == '$' && t.startsWith("{", i + 1) -> expression()
                    else -> i++
                }
            }
            if (erreur == null) erreur = debut to "texte entre accents graves ouvert ligne $debut, jamais fermé"
        }

        private fun brut() {
            val debut = ligne
            val fin = t.indexOf('`', i + 1)
            if (fin < 0) {
                erreur = debut to "texte entre accents graves ouvert ligne $debut, jamais fermé"
                return
            }
            for (k in i until fin) if (t[k] == '\n') ligne++
            i = fin + 1
        }

        /** Le `/` en [i] ouvre-t-il une expression reguliere JavaScript (et non une division) ? */
        private fun regexPossible(): Boolean {
            val p = prev
            if (p == '\u0000') return true
            if (p in "(,=:[!&|?{;~^") return true
            if (p == '>' && avant == '=') return true // fleche =>
            if (p.isLetterOrDigit() || p == '_' || p == '$') return mot.toString() in MOTS_AVANT_REGEX
            return false
        }

        /** Nom entre accents graves en Kotlin (`un test`) : une ligne, apostrophes comprises. */
        private fun identifiantKotlin() {
            i++
            while (i < t.length && t[i] != '\n') {
                if (t[i++] == '`') return
            }
        }

        private fun regex() {
            val debut = i
            i++
            var classe = false
            while (i < t.length) {
                val c = t[i]
                when {
                    c == '\n' -> { i = debut + 1; return } // pas une regex : simple division
                    c == '\\' -> i += 2
                    c == '[' -> { classe = true; i++ }
                    c == ']' -> { classe = false; i++ }
                    c == '/' && !classe -> {
                        i++
                        while (i < t.length && t[i].isLetter()) i++
                        return
                    }
                    else -> i++
                }
            }
            i = debut + 1
        }
    }

    // ------------------------------------------------------------------ JSON

    /** Validateur JSON strict (RFC 8259) : renvoie (ligne, explication) de la premiere erreur, ou null. */
    private class JsonStrict(private val t: String) {
        private class Echec(val ligne: Int, val texte: String) : RuntimeException(texte)

        private var i = 0
        private var ligne = 1

        fun valider(): Pair<Int, String>? = try {
            if (t.startsWith("﻿")) i = 1
            espaces()
            valeur(0)
            espaces()
            if (i < t.length) echec("du texte reste après la fin du JSON")
            null
        } catch (e: Echec) {
            e.ligne to e.texte
        }

        private fun echec(texte: String): Nothing = throw Echec(ligne, texte)

        private fun espaces() {
            while (i < t.length) {
                when (t[i]) {
                    ' ', '\t', '\r' -> i++
                    '\n' -> { ligne++; i++ }
                    '/' -> if (t.startsWith("//", i) || t.startsWith("/*", i)) {
                        echec("les commentaires ne sont pas permis en JSON")
                    } else return
                    else -> return
                }
            }
        }

        private fun valeur(profondeur: Int) {
            if (profondeur > 200) echec("trop d'imbrications")
            val c = t.getOrNull(i) ?: echec("fin du fichier : une valeur est attendue")
            when {
                c == '{' -> objet(profondeur)
                c == '[' -> tableau(profondeur)
                c == '"' -> chaine()
                c == '\'' -> echec("guillemets simples interdits : utilise des guillemets doubles")
                c == '-' || c in '0'..'9' -> nombre()
                t.startsWith("true", i) -> mot("true")
                t.startsWith("false", i) -> mot("false")
                t.startsWith("null", i) -> mot("null")
                else -> echec("valeur inattendue « $c »")
            }
        }

        private fun mot(m: String) {
            i += m.length
            val suite = t.getOrNull(i)
            if (suite != null && (suite.isLetterOrDigit() || suite == '_')) echec("valeur inattendue après « $m »")
        }

        private val NOMBRE = Regex("""-?(0|[1-9][0-9]*)(\.[0-9]+)?([eE][+-]?[0-9]+)?""")

        private fun nombre() {
            val m = NOMBRE.matchAt(t, i) ?: echec("nombre invalide")
            i += m.value.length
        }

        private fun chaine() {
            i++
            while (true) {
                val c = t.getOrNull(i) ?: echec("chaîne jamais fermée")
                when {
                    c == '"' -> { i++; return }
                    c == '\n' -> echec("retour à la ligne dans une chaîne : écris \\n")
                    c < ' ' -> echec("caractère de contrôle dans une chaîne")
                    c == '\\' -> {
                        val e = t.getOrNull(i + 1) ?: echec("chaîne jamais fermée")
                        if (e == 'u') {
                            val hex = t.substring(minOf(i + 2, t.length), minOf(i + 6, t.length))
                            if (hex.length < 4 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
                                echec("séquence \\u invalide")
                            }
                            i += 6
                        } else if (e in "\"\\/bfnrt") {
                            i += 2
                        } else {
                            echec("séquence \\$e invalide dans une chaîne")
                        }
                    }
                    else -> i++
                }
            }
        }

        private fun objet(profondeur: Int) {
            i++
            espaces()
            if (t.getOrNull(i) == '}') { i++; return }
            while (true) {
                espaces()
                when (val c = t.getOrNull(i)) {
                    '"' -> Unit
                    '}' -> echec("virgule en trop avant « } »")
                    '\'' -> echec("clé entre guillemets simples : utilise des guillemets doubles")
                    null -> echec("fin du fichier : « } » manquant")
                    else -> echec("clé attendue entre guillemets doubles, trouvé « $c »")
                }
                chaine()
                espaces()
                if (t.getOrNull(i) != ':') echec("« : » attendu après la clé")
                i++
                espaces()
                valeur(profondeur + 1)
                espaces()
                when (val c = t.getOrNull(i)) {
                    ',' -> i++
                    '}' -> { i++; return }
                    null -> echec("fin du fichier : « } » manquant")
                    else -> echec("« , » ou « } » attendu, trouvé « $c »")
                }
            }
        }

        private fun tableau(profondeur: Int) {
            i++
            espaces()
            if (t.getOrNull(i) == ']') { i++; return }
            while (true) {
                espaces()
                if (t.getOrNull(i) == ']') echec("virgule en trop avant « ] »")
                valeur(profondeur + 1)
                espaces()
                when (val c = t.getOrNull(i)) {
                    ',' -> i++
                    ']' -> { i++; return }
                    null -> echec("fin du fichier : « ] » manquant")
                    else -> echec("« , » ou « ] » attendu, trouvé « $c »")
                }
            }
        }
    }
}
