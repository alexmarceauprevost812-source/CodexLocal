package com.codexlocal.coloration

/**
 * CSS, SCSS, LESS : selecteurs, proprietes, valeurs (nombres avec unite, couleurs, fonctions),
 * regles @, variables. Une « instruction » va jusqu'au premier `{`, `;` ou `}` : si c'est `{`, c'est un
 * selecteur (ou l'en-tete d'une regle @), sinon une declaration.
 */
internal class BalayeurCss(
    private val code: String,
    private val sortie: Sortie = Sortie(),
    private val base: Int = 0
) {
    private val n = code.length
    private var i = 0
    private val pile = ArrayList<Char>()

    fun analyser(): List<Jeton> {
        while (i < n) {
            val c = code[i]
            when {
                c.isWhitespace() || c == ';' -> { if (c == ';') ponctuation(); i++ }
                code.startsWith("/*", i) -> commentaire()
                c == '}' -> { fermerBloc(); i++ }
                code.startsWith("//", i) && estCommentaireLigne() -> commentaireLigne()
                else -> instruction()
            }
        }
        return sortie.liste
    }

    private fun ajouter(debut: Int, fin: Int, genre: Genre, couleur: Int = 0) {
        sortie.ajouter(base + debut, base + fin, genre, couleur)
    }

    private fun ponctuation() = ajouter(i, i + 1, Genre.PONCTUATION)

    private fun commentaire() {
        val f = code.indexOf("*/", i + 2)
        val bout = if (f < 0) n else f + 2
        ajouter(i, bout, Genre.COMMENTAIRE)
        i = bout
    }

    /** `//` commente en SCSS/LESS, mais pas dans `url(http://...)`. */
    private fun estCommentaireLigne(): Boolean = i == 0 || code[i - 1] != ':'

    private fun commentaireLigne() {
        val f = code.indexOf('\n', i)
        val bout = if (f < 0) n else f
        ajouter(i, bout, Genre.COMMENTAIRE)
        i = bout
    }

    private fun fermerBloc() {
        if (pile.isNotEmpty()) pile.removeAt(pile.lastIndex)
        ajouter(i, i + 1, parenthese(pile.size))
    }

    private fun parenthese(profondeur: Int) = when (profondeur % 3) {
        0 -> Genre.PAREN_1
        1 -> Genre.PAREN_2
        else -> Genre.PAREN_3
    }

    /** Ou finit l'instruction qui commence ici, et par quel signe (`{`, `;`, `}` ou la fin du texte). */
    private fun finInstruction(): Pair<Int, Char> {
        var k = i
        var parentheses = 0
        while (k < n) {
            val c = code[k]
            when {
                c == '"' || c == '\'' -> {
                    var e = k + 1
                    while (e < n && code[e] != c && code[e] != '\n') { if (code[e] == '\\') e++; e++ }
                    k = e
                }
                code.startsWith("/*", k) -> { val f = code.indexOf("*/", k + 2); k = if (f < 0) n else f + 1 }
                c == '(' -> parentheses++
                c == ')' -> if (parentheses > 0) parentheses--
                parentheses == 0 && (c == '{' || c == ';' || c == '}') -> return k to c
            }
            k++
        }
        return n to ' '
    }

    private fun instruction() {
        val (fin, signe) = finInstruction()
        if (signe == '{') {
            enTete(i, fin)
            val profondeur = pile.size
            pile.add('{')
            ajouter(fin, fin + 1, parenthese(profondeur))
            i = fin + 1
        } else {
            declaration(i, fin)
            i = fin
        }
    }

    // ------------------------------------------------------------------ selecteurs et regles @

    private fun enTete(debut: Int, fin: Int) {
        var k = debut
        if (k < fin && code[k] == '@') {
            var e = k + 1
            while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-')) e++
            ajouter(k, e, Genre.MOT_CLE)
            valeurs(e, fin)
            return
        }
        while (k < fin) {
            val c = code[k]
            when {
                c.isWhitespace() -> k++
                code.startsWith("/*", k) -> {
                    val f = code.indexOf("*/", k + 2)
                    val bout = if (f < 0 || f + 2 > fin) fin else f + 2
                    ajouter(k, bout, Genre.COMMENTAIRE)
                    k = bout
                }
                c == '.' || c == '#' || c == '%' -> {
                    var e = k + 1
                    while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-' || code[e] == '_')) e++
                    ajouter(k, e, Genre.SELECTEUR)
                    k = e
                }
                c == ':' -> {
                    var e = k + 1
                    if (e < fin && code[e] == ':') e++
                    while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-')) e++
                    ajouter(k, e, Genre.INTEGRE)
                    k = e
                    if (k < fin && code[k] == '(') k = parentheseSelecteur(k, fin)
                }
                c == '[' -> {
                    val f = code.indexOf(']', k)
                    val bout = if (f < 0 || f >= fin) fin else f + 1
                    ajouter(k, bout, Genre.ATTRIBUT)
                    k = bout
                }
                c == '&' || c == '*' -> { ajouter(k, k + 1, Genre.SELECTEUR); k++ }
                c.isLetter() -> {
                    var e = k
                    while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-' || code[e] == '_')) e++
                    ajouter(k, e, Genre.BALISE)
                    k = e
                }
                c == '$' || c == '@' -> {
                    var e = k + 1
                    while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-' || code[e] == '_')) e++
                    ajouter(k, e, Genre.VARIABLE)
                    k = e
                }
                else -> { ajouter(k, k + 1, if (c == ',') Genre.PONCTUATION else Genre.OPERATEUR); k++ }
            }
        }
    }

    /** `:not(.a)`, `:nth-child(2n+1)` : la parenthese est lue comme un petit selecteur. */
    private fun parentheseSelecteur(debut: Int, fin: Int): Int {
        var profondeur = 0
        var k = debut
        while (k < fin) {
            if (code[k] == '(') profondeur++
            if (code[k] == ')') { profondeur--; if (profondeur == 0) { k++; break } }
            k++
        }
        ajouter(debut, debut + 1, Genre.PONCTUATION)
        enTete(debut + 1, maxOf(debut + 1, k - 1))
        if (k - 1 > debut) ajouter(k - 1, k, Genre.PONCTUATION)
        return k
    }

    // ------------------------------------------------------------------ declarations

    private fun declaration(debut: Int, fin: Int) {
        var k = debut
        while (k < fin && code[k].isWhitespace()) k++
        val deuxPoints = trouverDeuxPoints(k, fin)
        if (deuxPoints < 0) {
            // `@import "x"`, `$variable` seule, ou texte sans « : » : on lit tout comme une valeur
            if (k < fin && code[k] == '@') {
                var e = k + 1
                while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-')) e++
                ajouter(k, e, Genre.MOT_CLE)
                valeurs(e, fin)
            } else {
                valeurs(k, fin)
            }
            return
        }
        var finNom = deuxPoints
        while (finNom > k && code[finNom - 1].isWhitespace()) finNom--
        val nom = code.substring(k, finNom)
        ajouter(k, finNom, if (nom.startsWith("--") || nom.startsWith("$") || nom.startsWith("@")) Genre.VARIABLE else Genre.PROPRIETE)
        ajouter(deuxPoints, deuxPoints + 1, Genre.PONCTUATION)
        valeurs(deuxPoints + 1, fin)
    }

    /**
     * Position du « : » qui suit le nom d'une propriete, ou -1 s'il n'y en a pas. Un nom de propriete ne
     * contient ni espace ni parenthese ni guillemet : on s'arrete des qu'on en rencontre un.
     */
    private fun trouverDeuxPoints(debut: Int, fin: Int): Int {
        var k = debut
        while (k < fin) {
            val c = code[k]
            if (c == ':') return k
            if (c.isWhitespace()) {
                var e = k
                while (e < fin && code[e].isWhitespace()) e++
                return if (e < fin && code[e] == ':') e else -1
            }
            if (c == '(' || c == '"' || c == '\'') return -1
            k++
        }
        return -1
    }

    // ------------------------------------------------------------------ valeurs

    private fun valeurs(debut: Int, fin: Int) {
        var k = debut
        while (k < fin) {
            val c = code[k]
            when {
                c.isWhitespace() -> k++
                code.startsWith("/*", k) -> {
                    val f = code.indexOf("*/", k + 2)
                    val bout = if (f < 0 || f + 2 > fin) fin else f + 2
                    ajouter(k, bout, Genre.COMMENTAIRE)
                    k = bout
                }
                c == '"' || c == '\'' -> {
                    var e = k + 1
                    while (e < fin && code[e] != c) { if (code[e] == '\\') e++; e++ }
                    val bout = minOf(e + 1, fin)
                    ajouter(k, bout, Genre.TEXTE)
                    k = bout
                }
                c == '#' -> {
                    var e = k + 1
                    while (e < fin && Character.digit(code[e], 16) >= 0) e++
                    val couleur = if (e - k - 1 in 3..8) CouleursCode.depuisHex(code.substring(k + 1, e), alphaEnPremier = false) else null
                    if (couleur != null && (e >= fin || !code[e].isLetterOrDigit())) {
                        ajouter(k, e, Genre.COULEUR, couleur)
                    } else {
                        e = k + 1
                        while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-' || code[e] == '_')) e++
                        ajouter(k, e, Genre.SELECTEUR)
                    }
                    k = e
                }
                c == '!' -> {
                    var e = k + 1
                    while (e < fin && code[e].isLetter()) e++
                    ajouter(k, e, Genre.MOT_CLE)
                    k = e
                }
                c.isDigit() || (c == '.' && k + 1 < fin && code[k + 1].isDigit()) ||
                    ((c == '-' || c == '+') && k + 1 < fin && (code[k + 1].isDigit() || code[k + 1] == '.') && avantEstSeparateur(k, debut)) -> {
                    var e = k + 1
                    while (e < fin && (code[e].isDigit() || code[e] == '.')) e++
                    while (e < fin && (code[e].isLetter() || code[e] == '%')) e++
                    ajouter(k, e, Genre.NOMBRE)
                    k = e
                }
                c == '$' || c == '@' || (c == '-' && k + 1 < fin && code[k + 1] == '-') -> {
                    var e = k + 1
                    while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-' || code[e] == '_')) e++
                    ajouter(k, e, Genre.VARIABLE)
                    k = e
                }
                c.isLetter() || c == '_' || c == '-' -> k = mot(k, fin)
                c == '(' || c == ')' -> {
                    val genre = if (c == '(') { val p = pile.size; pile.add('('); parenthese(p) } else {
                        if (pile.isNotEmpty() && pile.last() == '(') pile.removeAt(pile.lastIndex)
                        parenthese(pile.size)
                    }
                    ajouter(k, k + 1, genre)
                    k++
                }
                c == ',' || c == '/' -> { ajouter(k, k + 1, Genre.PONCTUATION); k++ }
                else -> { ajouter(k, k + 1, Genre.OPERATEUR); k++ }
            }
        }
    }

    private fun avantEstSeparateur(k: Int, debut: Int): Boolean =
        k == debut || code[k - 1].isWhitespace() || code[k - 1] == '(' || code[k - 1] == ','

    /** Un mot d'une valeur : `solid`, `flex`, ou une fonction `rgb(...)`, `var(--x)`, `calc(...)`. */
    private fun mot(debut: Int, fin: Int): Int {
        var e = debut
        while (e < fin && (code[e].isLetterOrDigit() || code[e] == '-' || code[e] == '_')) e++
        if (e == debut) { ajouter(debut, debut + 1, Genre.OPERATEUR); return debut + 1 }
        val nom = code.substring(debut, e)
        if (e < fin && code[e] == '(') {
            val couleur = couleurDeFonction(nom, e, fin)
            if (couleur != null) {
                ajouter(debut, couleur.first, Genre.COULEUR, couleur.second)
                return couleur.first
            }
            ajouter(debut, e, Genre.FONCTION)
            return e
        }
        // les mots-valeurs (solid, flex, none...) gardent la couleur normale du texte
        return e
    }

    /** `rgb(255, 0, 0)` / `rgba(...)` / `hsl(...)` avec des nombres : toute l'expression devient une couleur. */
    private fun couleurDeFonction(nom: String, ouvrante: Int, fin: Int): Pair<Int, Int>? {
        val minuscule = nom.lowercase()
        if (minuscule != "rgb" && minuscule != "rgba" && minuscule != "hsl" && minuscule != "hsla") return null
        val f = code.indexOf(')', ouvrante)
        if (f < 0 || f >= fin) return null
        val nombres = code.substring(ouvrante + 1, f)
            .split(',', ' ', '/')
            .filter { it.isNotBlank() }
            .map { it.trim().removeSuffix("%").removeSuffix("deg").toDoubleOrNull() ?: return null }
        if (nombres.size < 3) return null
        val alpha = if (nombres.size >= 4) {
            val brut = code.substring(ouvrante + 1, f).split(',', ' ', '/').filter { it.isNotBlank() }[3]
            if (brut.endsWith("%")) nombres[3] / 100.0 else nombres[3]
        } else 1.0
        val a = (alpha.coerceIn(0.0, 1.0) * 255).toInt()
        val couleur = if (minuscule.startsWith("rgb")) {
            val pourcentages = code.substring(ouvrante + 1, f).contains('%')
            fun canal(v: Double) = (if (pourcentages) v * 2.55 else v).toInt().coerceIn(0, 255)
            CouleursCode.opaque(canal(nombres[0]), canal(nombres[1]), canal(nombres[2]), a)
        } else {
            val (r, g, b) = teinteVersRgb(nombres[0], nombres[1] / 100.0, nombres[2] / 100.0)
            CouleursCode.opaque(r, g, b, a)
        }
        return (f + 1) to couleur
    }

    private fun teinteVersRgb(teinte: Double, saturation: Double, luminosite: Double): Triple<Int, Int, Int> {
        val h = ((teinte % 360) + 360) % 360 / 360.0
        val s = saturation.coerceIn(0.0, 1.0)
        val l = luminosite.coerceIn(0.0, 1.0)
        if (s == 0.0) { val v = (l * 255).toInt(); return Triple(v, v, v) }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        fun canal(t0: Double): Int {
            var t = t0
            if (t < 0) t += 1
            if (t > 1) t -= 1
            val v = when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
            return (v * 255).toInt().coerceIn(0, 255)
        }
        return Triple(canal(h + 1.0 / 3), canal(h), canal(h - 1.0 / 3))
    }
}
