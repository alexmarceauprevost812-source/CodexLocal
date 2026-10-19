package com.codexlocal.coloration

/** Fichiers de reglages ecrits ligne par ligne : YAML, TOML, INI / properties / .env. */
internal class BalayeurDonnees(
    private val code: String,
    private val mode: Mode,
    private val sortie: Sortie = Sortie(),
    private val base: Int = 0
) {
    enum class Mode { YAML, TOML, INI }

    private val n = code.length
    private val pile = ArrayList<Char>()

    fun analyser(): List<Jeton> {
        var debut = 0
        var blocIndent = -1               // YAML : indentation de la ligne qui ouvre un bloc « | » ou « > »
        while (debut <= n) {
            var fin = code.indexOf('\n', debut)
            if (fin < 0) fin = n
            val finUtile = if (fin > debut && code[fin - 1] == '\r') fin - 1 else fin
            blocIndent = ligne(debut, finUtile, blocIndent)
            if (fin >= n) break
            debut = fin + 1
        }
        return sortie.liste
    }

    private fun ajouter(debut: Int, fin: Int, genre: Genre, couleur: Int = 0) {
        sortie.ajouter(base + debut, base + fin, genre, couleur)
    }

    /** Traite une ligne [debut, fin) ; renvoie l'indentation du bloc de texte en cours (-1 s'il n'y en a pas). */
    private fun ligne(debut: Int, fin: Int, blocIndent: Int): Int {
        var p = debut
        while (p < fin && (code[p] == ' ' || code[p] == '\t')) p++
        if (p >= fin) return blocIndent
        val indent = p - debut

        if (mode == Mode.YAML && blocIndent >= 0) {
            if (indent > blocIndent) { ajouter(p, fin, Genre.TEXTE); return blocIndent }
        }
        val c = code[p]
        if (c == '#' || (mode == Mode.INI && (c == ';' || c == '!'))) {
            ajouter(p, fin, Genre.COMMENTAIRE)
            return if (mode == Mode.YAML) blocIndent else -1
        }
        return when (mode) {
            Mode.YAML -> ligneYaml(p, fin, indent)
            Mode.TOML -> { ligneToml(p, fin); -1 }
            Mode.INI -> { ligneIni(p, fin); -1 }
        }
    }

    // ------------------------------------------------------------------ YAML

    private fun ligneYaml(debut: Int, fin: Int, indent: Int): Int {
        var p = debut
        if (indent == 0 && (code.startsWith("---", p) || code.startsWith("...", p))) {
            ajouter(p, p + 3, Genre.PONCTUATION)
            p += 3
        }
        // marqueurs de liste « - »
        while (p < fin && code[p] == '-' && (p + 1 >= fin || code[p + 1] == ' ')) {
            ajouter(p, p + 1, Genre.OPERATEUR)
            p++
            while (p < fin && code[p] == ' ') p++
        }
        if (p >= fin) return -1
        val cle = finDeCle(p, fin)
        if (cle >= 0) {
            ajouter(p, cle, Genre.CLE)
            val deuxPoints = positionDeuxPoints(cle, fin)
            ajouter(deuxPoints, deuxPoints + 1, Genre.PONCTUATION)
            p = deuxPoints + 1
        }
        return if (valeur(p, fin, yaml = true)) indent else -1
    }

    /** Fin du nom de cle YAML qui commence en [debut] (avant le « : »), ou -1 si la ligne n'est pas `cle: ...`. */
    private fun finDeCle(debut: Int, fin: Int): Int {
        var k = debut
        if (code[k] == '"' || code[k] == '\'') {
            val guillemet = code[k]
            k++
            while (k < fin && code[k] != guillemet) { if (code[k] == '\\') k++; k++ }
            k++
            if (k > fin) return -1
            var e = k
            while (e < fin && code[e] == ' ') e++
            return if (e < fin && code[e] == ':' && (e + 1 >= fin || code[e + 1] == ' ')) k else -1
        }
        while (k < fin) {
            val c = code[k]
            if (c == ':' && (k + 1 >= fin || code[k + 1] == ' ')) {
                var e = k
                while (e > debut && code[e - 1] == ' ') e--
                return if (e > debut) e else -1
            }
            if (c == '#' && k > debut && code[k - 1] == ' ') return -1
            if (c == '[' || c == '{' || (c == '"' && k > debut)) return -1
            k++
        }
        return -1
    }

    private fun positionDeuxPoints(depuis: Int, fin: Int): Int {
        var k = depuis
        while (k < fin && code[k] != ':') k++
        return k
    }

    // ------------------------------------------------------------------ TOML

    private fun ligneToml(debut: Int, fin: Int) {
        if (code[debut] == '[') {
            val f = code.indexOf(']', debut)
            val bout = if (f < 0 || f >= fin) fin else (if (code.startsWith("]]", f)) f + 2 else f + 1)
            ajouter(debut, bout, Genre.TITRE)
            valeur(bout, fin, yaml = false)
            return
        }
        cleEtValeur(debut, fin, '=')
    }

    // ------------------------------------------------------------------ INI, properties, .env

    private fun ligneIni(debut: Int, fin: Int) {
        if (code[debut] == '[') {
            val f = code.indexOf(']', debut)
            val bout = if (f < 0 || f >= fin) fin else f + 1
            ajouter(debut, bout, Genre.TITRE)
            return
        }
        var p = debut
        if (code.startsWith("export ", p)) {
            ajouter(p, p + 6, Genre.MOT_CLE)
            p += 7
            while (p < fin && code[p] == ' ') p++
        }
        var s = p
        while (s < fin && code[s] != '=' && code[s] != ':') s++
        cleEtValeur(p, fin, if (s < fin) code[s] else '=')
    }

    /** `cle = valeur` (ou `cle: valeur`) : la cle, le signe, puis la valeur. */
    private fun cleEtValeur(debut: Int, fin: Int, signe: Char) {
        var s = debut
        if (code[s] == '"' || code[s] == '\'') {
            val g = code[s]
            s++
            while (s < fin && code[s] != g) s++
            s = minOf(s + 1, fin)
        }
        while (s < fin && code[s] != signe) s++
        if (s >= fin) {
            // une ligne sans « = » : une cle seule (properties) ou une suite de tableau TOML
            if (mode == Mode.TOML) valeur(debut, fin, yaml = false) else ajouter(debut, trim(debut, fin), Genre.CLE)
            return
        }
        var finCle = s
        while (finCle > debut && code[finCle - 1] == ' ') finCle--
        ajouter(debut, finCle, Genre.CLE)
        ajouter(s, s + 1, Genre.OPERATEUR)
        valeur(s + 1, fin, yaml = false)
    }

    private fun trim(debut: Int, fin: Int): Int {
        var f = fin
        while (f > debut && code[f - 1].isWhitespace()) f--
        return f
    }

    // ------------------------------------------------------------------ valeurs

    /**
     * Lit la valeur de [debut] a [fin]. Renvoie vrai si c'est l'ouverture d'un bloc de texte YAML
     * (`|` ou `>`) dont les lignes suivantes, plus indentees, sont du texte.
     */
    private fun valeur(debut: Int, fin: Int, yaml: Boolean): Boolean {
        var k = debut
        var premier = true
        while (k < fin) {
            val c = code[k]
            when {
                c == ' ' || c == '\t' -> k++
                c == '#' && k > 0 && (code[k - 1] == ' ' || code[k - 1] == '\t') -> {
                    ajouter(k, fin, Genre.COMMENTAIRE)
                    return false
                }
                c == '"' || c == '\'' -> k = chaine(k, fin)
                (c == '|' || c == '>') && premier && yaml && finDeBlocScalaire(k, fin) -> {
                    ajouter(k, trim(k, fin), Genre.OPERATEUR)
                    return true
                }
                yaml && (c == '&' || c == '*') -> {
                    var e = k + 1
                    while (e < fin && !code[e].isWhitespace() && code[e] !in ",]}") e++
                    ajouter(k, e, Genre.VARIABLE)
                    k = e
                }
                c == '!' && yaml -> {
                    var e = k + 1
                    while (e < fin && !code[e].isWhitespace()) e++
                    ajouter(k, e, Genre.ANNOTATION)
                    k = e
                }
                c == '$' && k + 1 < fin && code[k + 1] == '{' -> {
                    val f = code.indexOf('}', k)
                    val bout = if (f < 0 || f >= fin) fin else f + 1
                    ajouter(k, bout, Genre.VARIABLE)
                    k = bout
                }
                c == '$' && k + 1 < fin && (code[k + 1].isLetter() || code[k + 1] == '_') && mode == Mode.INI -> {
                    var e = k + 1
                    while (e < fin && (code[e].isLetterOrDigit() || code[e] == '_')) e++
                    ajouter(k, e, Genre.VARIABLE)
                    k = e
                }
                c == '[' || c == '{' -> { val p = pile.size; pile.add(c); ajouter(k, k + 1, parenthese(p)); k++ }
                c == ']' || c == '}' -> {
                    if (pile.isNotEmpty()) pile.removeAt(pile.lastIndex)
                    ajouter(k, k + 1, parenthese(pile.size))
                    k++
                }
                c == ',' || (c == ':' && !yaml) -> { ajouter(k, k + 1, Genre.PONCTUATION); k++ }
                else -> k = mot(k, fin)
            }
            if (c != ' ' && c != '\t') premier = false
        }
        return false
    }

    private fun finDeBlocScalaire(debut: Int, fin: Int): Boolean {
        var k = debut + 1
        while (k < fin && (code[k] == '-' || code[k] == '+' || code[k].isDigit())) k++
        while (k < fin && code[k] == ' ') k++
        return k >= fin || code[k] == '#'
    }

    private fun parenthese(profondeur: Int) = when (profondeur % 3) {
        0 -> Genre.PAREN_1
        1 -> Genre.PAREN_2
        else -> Genre.PAREN_3
    }

    private fun chaine(debut: Int, fin: Int): Int {
        val g = code[debut]
        var e = debut + 1
        while (e < fin && code[e] != g) { if (code[e] == '\\' && g == '"') e++; e++ }
        val bout = if (e < fin) e + 1 else fin
        val ferme = e < fin
        val interieurFin = if (ferme) bout - 1 else bout
        if (ferme && interieurFin - debut - 1 in 4..9 && code[debut + 1] == '#') {
            val couleur = CouleursCode.depuisHex(code.substring(debut + 2, interieurFin), alphaEnPremier = true)
            if (couleur != null) {
                ajouter(debut, debut + 1, Genre.TEXTE)
                ajouter(debut + 1, interieurFin, Genre.COULEUR, couleur)
                ajouter(interieurFin, bout, Genre.TEXTE)
                return bout
            }
        }
        ajouter(debut, bout, Genre.TEXTE)
        return bout
    }

    /** Un mot sans guillemets : nombre, booleen, date... ou simple texte (sans couleur). */
    private fun mot(debut: Int, fin: Int): Int {
        var e = debut
        while (e < fin && code[e] != ' ' && code[e] != '\t' && code[e] != ',' && code[e] != ']' && code[e] != '}' &&
            !(code[e] == '#' && e > debut && code[e - 1] == ' ')
        ) e++
        if (e == debut) e = debut + 1
        val mot = code.substring(debut, e)
        val couleur = if (mode != Mode.YAML && mot.length in 4..9 && mot[0] == '#') {
            CouleursCode.depuisHex(mot.substring(1), alphaEnPremier = true)
        } else null
        when {
            couleur != null -> ajouter(debut, e, Genre.COULEUR, couleur)
            mot.lowercase() in CONSTANTES -> ajouter(debut, e, Genre.CONSTANTE)
            esNombre(mot) -> ajouter(debut, e, Genre.NOMBRE)
        }
        return e
    }

    private fun esNombre(mot: String): Boolean {
        if (mot.isEmpty()) return false
        val propre = mot.replace("_", "")
        if (propre.toDoubleOrNull() != null && (propre[0].isDigit() || propre[0] == '-' || propre[0] == '+' || propre[0] == '.')) return true
        if (propre.startsWith("0x") && propre.length > 2 && propre.drop(2).all { Character.digit(it, 16) >= 0 }) return true
        // dates et heures TOML / YAML : 2024-01-31, 12:30:00
        return propre.length >= 5 && propre[0].isDigit() && propre.all { it.isDigit() || it in "-:TZ.+" }
    }

    private companion object {
        val CONSTANTES = setOf("true", "false", "null", "yes", "no", "on", "off", "~", "nan", "inf")
    }
}
