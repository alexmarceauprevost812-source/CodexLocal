package com.codexlocal.coloration

import java.util.Locale

/**
 * Analyse d'un langage « a la C » (Kotlin, Java, JavaScript, Python, shell, SQL...) : on lit le texte
 * de gauche a droite et on rend, pour chaque morceau qui compte, son [Genre].
 *
 * Contrairement a une grosse expression reguliere, la lecture suit le contexte : le nom qui vient apres
 * `fun` ou `class` est une declaration, ce qui suit un point est une propriete ou un appel, les
 * parentheses imbriquees changent de couleur a chaque niveau, une chaine peut contenir des echappements
 * et des variables.
 */
internal class BalayeurCode(
    private val code: String,
    private val profil: Profil,
    private val sortie: Sortie = Sortie(),
    private val base: Int = 0,
    private val couleurAlphaEnPremier: Boolean = true
) {
    private val n = code.length
    private var i = 0

    /** Les ouvrants encore ouverts : leur profondeur donne la couleur de la paire. */
    private val pile = ArrayList<Char>()

    /** Vrai juste apres un mot de declaration (`fun`, `class`...) : le mot suivant est le nom declare. */
    private var nomAttendu = false

    /** Dernier caractere significatif lu (pour reconnaitre `.nom`, `->nom`, `::nom`). */
    private var avant = ' '
    private var avantAvant = ' '

    fun analyser(): List<Jeton> {
        while (i < n) {
            val c = code[i]
            when {
                c == '\n' || c == ' ' || c == '\t' || c == '\r' -> i++
                lireCommentaire() -> Unit
                lireDirective() -> Unit
                c in profil.guillemets && lireChaine(c) -> Unit
                c.isDigit() || (c == '.' && i + 1 < n && code[i + 1].isDigit() && !estMot(avant)) -> lireNombre()
                c == '@' && profil.annotations && i + 1 < n && code[i + 1].isLetter() -> lireAnnotation()
                c == '$' && profil.variablesDollar && lireVariableDollar() -> Unit
                profil.options && c == '-' && lireOption() -> Unit
                c.isLetter() || c == '_' || (c == '$' && !profil.variablesDollar) -> lireMot()
                c == '(' || c == '[' || c == '{' -> ouvrir(c)
                c == ')' || c == ']' || c == '}' -> fermer()
                c in profil.ponctuationSeule -> { ponctuation(i, i + 1); i++ }
                c in OPERATEURS -> lireOperateurs()
                else -> { ponctuation(i, i + 1); i++ }
            }
        }
        return sortie.liste
    }

    // ------------------------------------------------------------------ commentaires, directives

    private fun commencePar(texte: String): Boolean = code.startsWith(texte, i)

    private fun lireCommentaire(): Boolean {
        for ((debut, fin) in profil.commentairesBloc) {
            if (commencePar(debut)) {
                val f = code.indexOf(fin, i + debut.length)
                val bout = if (f < 0) n else f + fin.length
                ajouter(i, bout, Genre.COMMENTAIRE)
                i = bout
                return true
            }
        }
        for (marque in profil.commentairesLigne) {
            if (commencePar(marque) && !(marque == "#" && profil.directives && debutDeLigne())) {
                val f = code.indexOf('\n', i)
                val bout = if (f < 0) n else f
                ajouter(i, bout, Genre.COMMENTAIRE)
                i = bout
                return true
            }
        }
        return false
    }

    private fun debutDeLigne(): Boolean {
        var k = i - 1
        while (k >= 0 && (code[k] == ' ' || code[k] == '\t')) k--
        return k < 0 || code[k] == '\n'
    }

    /** `#include <x.h>`, `#define`, `#region`... en debut de ligne. */
    private fun lireDirective(): Boolean {
        if (!profil.directives || code[i] != '#' || !debutDeLigne()) return false
        var k = i + 1
        while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
        val debutMot = k
        while (k < n && code[k].isLetter()) k++
        if (k == debutMot) return false
        ajouter(i, k, Genre.ANNOTATION)
        val nom = code.substring(debutMot, k)
        i = k
        if (nom == "include" || nom == "import") {
            var m = i
            while (m < n && (code[m] == ' ' || code[m] == '\t')) m++
            if (m < n && code[m] == '<') {
                val f = code.indexOf('>', m)
                val bout = if (f < 0 || code.indexOf('\n', m).let { it in 0 until f }) m else f + 1
                if (bout > m) {
                    ajouter(m, bout, Genre.TEXTE)
                    i = bout
                }
            }
        }
        avant = ' '
        return true
    }

    // ------------------------------------------------------------------ chaines

    private fun lireChaine(guillemet: Char): Boolean {
        val triple = profil.triples && code.startsWith("$guillemet$guillemet$guillemet", i)
        val debut = i
        val interieurDebut = i + (if (triple) 3 else 1)
        val interpole = guillemet in profil.interpolation
        val multiligne = triple || guillemet == '`'

        // Ou finit la chaine ? (une chaine non fermee va jusqu'a la fin de la ligne)
        var fin = n
        var ferme = false
        var e = interieurDebut
        while (e < n) {
            val ch = code[e]
            if (ch == '\\' && e + 1 < n) { e += 2; continue }
            if (triple) {
                if (code.startsWith("$guillemet$guillemet$guillemet", e)) { fin = e + 3; ferme = true; break }
            } else if (ch == guillemet) {
                fin = e + 1; ferme = true; break
            }
            if (ch == '\n' && !multiligne) { fin = e; break }
            e++
        }
        if (e >= n && !ferme) fin = n

        // Une apostrophe sans pareille sur la ligne n'ouvre pas une chaine (`'a` en Rust, texte libre...)
        if (!ferme && guillemet == '\'') {
            ponctuation(i, i + 1)
            i++
            return true
        }

        val interieurFin = if (ferme) fin - (if (triple) 3 else 1) else fin

        // cle JSON : "nom": ...
        if (profil.cleEntreGuillemets && ferme && suiviDeDeuxPoints(fin)) {
            ajouter(debut, fin, Genre.CLE)
            termineChaine(fin)
            return true
        }
        // couleur ecrite dans une chaine : "#FF9A3D"
        if (ferme && !triple && interieurFin - interieurDebut in 4..9 && code[interieurDebut] == '#') {
            val couleur = CouleursCode.depuisHex(
                code.substring(interieurDebut + 1, interieurFin), couleurAlphaEnPremier
            )
            if (couleur != null) {
                ajouter(debut, interieurDebut, Genre.TEXTE)
                ajouter(interieurDebut, interieurFin, Genre.COULEUR, couleur)
                ajouter(interieurFin, fin, Genre.TEXTE)
                termineChaine(fin)
                return true
            }
        }

        // cas general : du texte, avec des echappements et des variables
        var morceau = debut
        var k = interieurDebut
        while (k < interieurFin) {
            val ch = code[k]
            if (ch == '\\' && k + 1 < interieurFin) {
                ajouter(morceau, k, Genre.TEXTE)
                val longueur = longueurEchappement(k, interieurFin)
                ajouter(k, k + longueur, Genre.ECHAPPEMENT)
                k += longueur
                morceau = k
                continue
            }
            if (interpole && ch == '$' && k + 1 < interieurFin) {
                val suivant = code[k + 1]
                val finVariable = when {
                    suivant == '{' -> fermetureAccolade(k + 1, interieurFin)
                    profil.interpolationSimple && (suivant.isLetter() || suivant == '_') -> {
                        var f = k + 1
                        while (f < interieurFin && estMot(code[f])) f++
                        f
                    }
                    else -> -1
                }
                if (finVariable > 0) {
                    ajouter(morceau, k, Genre.TEXTE)
                    ajouter(k, finVariable, Genre.VARIABLE)
                    k = finVariable
                    morceau = k
                    continue
                }
            }
            k++
        }
        ajouter(morceau, fin, Genre.TEXTE)
        termineChaine(fin)
        return true
    }

    private fun suiviDeDeuxPoints(depuis: Int): Boolean {
        var k = depuis
        while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
        return k < n && code[k] == ':'
    }

    private fun longueurEchappement(k: Int, limite: Int): Int {
        val suivant = code[k + 1]
        var f = k + 2
        when (suivant) {
            'u', 'U' -> {
                if (f < limite && code[f] == '{') {
                    while (f < limite && code[f] != '}') f++
                    if (f < limite) f++
                } else {
                    var chiffres = 0
                    while (f < limite && chiffres < 8 && Character.digit(code[f], 16) >= 0) { f++; chiffres++ }
                }
            }
            'x' -> {
                var chiffres = 0
                while (f < limite && chiffres < 2 && Character.digit(code[f], 16) >= 0) { f++; chiffres++ }
            }
            in '0'..'7' -> {
                var chiffres = 1
                while (f < limite && chiffres < 3 && code[f] in '0'..'7') { f++; chiffres++ }
            }
        }
        return f - k
    }

    private fun fermetureAccolade(ouvrante: Int, limite: Int): Int {
        var profondeur = 0
        var k = ouvrante
        while (k < limite) {
            if (code[k] == '{') profondeur++
            if (code[k] == '}') { profondeur--; if (profondeur == 0) return k + 1 }
            k++
        }
        return limite
    }

    private fun termineChaine(fin: Int) {
        i = fin
        avant = '"'
        avantAvant = ' '
        nomAttendu = false
    }

    // ------------------------------------------------------------------ nombres, annotations, variables

    private fun lireNombre() {
        val debut = i
        var k = i
        if (code[k] == '0' && k + 1 < n && (code[k + 1] == 'x' || code[k + 1] == 'X')) {
            k += 2
            while (k < n && (Character.digit(code[k], 16) >= 0 || code[k] == '_')) k++
        } else if (code[k] == '0' && k + 1 < n && (code[k + 1] == 'b' || code[k + 1] == 'B')) {
            k += 2
            while (k < n && (code[k] == '0' || code[k] == '1' || code[k] == '_')) k++
        } else {
            while (k < n && (code[k].isDigit() || code[k] == '_')) k++
            if (k + 1 < n && code[k] == '.' && code[k + 1].isDigit()) {
                k++
                while (k < n && (code[k].isDigit() || code[k] == '_')) k++
            }
            if (k < n && (code[k] == 'e' || code[k] == 'E')) {
                var e = k + 1
                if (e < n && (code[e] == '+' || code[e] == '-')) e++
                if (e < n && code[e].isDigit()) {
                    while (e < n && code[e].isDigit()) e++
                    k = e
                }
            }
        }
        // suffixes de type : 10L, 3.5f, 7u, 0xFFUL
        while (k < n && "fFlLuUdDnN".indexOf(code[k]) >= 0 && !(k + 1 < n && code[k + 1].isLetter() && "fFlLuUdDnN".indexOf(code[k + 1]) < 0)) k++
        ajouter(debut, k, Genre.NOMBRE)
        i = k
        avantAvant = avant
        avant = '0'
        nomAttendu = false
    }

    private fun lireAnnotation() {
        var k = i + 1
        while (k < n && (estMot(code[k]) || (code[k] == '.' && k + 1 < n && code[k + 1].isLetter()))) k++
        ajouter(i, k, Genre.ANNOTATION)
        i = k
        avant = '@'
        avantAvant = ' '
    }

    /** `$nom`, `${nom}`, `$(commande)`, `$1`, `$@` : une variable (ou une substitution). */
    private fun lireVariableDollar(): Boolean {
        if (i + 1 >= n) return false
        val suivant = code[i + 1]
        var k = i + 1
        when {
            suivant == '{' -> k = fermetureAccolade(k, n)
            suivant == '(' -> { ajouter(i, i + 2, Genre.VARIABLE); i += 2; pile.add('('); return true }
            suivant.isLetter() || suivant == '_' -> { while (k < n && estMot(code[k])) k++ }
            suivant.isDigit() || suivant in "@*#?!$-" -> k++
            else -> return false
        }
        ajouter(i, k, Genre.VARIABLE)
        i = k
        avant = '$'
        avantAvant = ' '
        nomAttendu = false
        return true
    }

    /** `-x` ou `--option=valeur` apres un espace, dans un shell. */
    private fun lireOption(): Boolean {
        if (i > 0 && code[i - 1] != ' ' && code[i - 1] != '\t' && code[i - 1] != '\n') return false
        var k = i + 1
        if (k < n && code[k] == '-') k++
        if (k >= n || !code[k].isLetter()) return false
        while (k < n && (estMot(code[k]) || code[k] == '-' || code[k] == '.')) k++
        ajouter(i, k, Genre.ATTRIBUT)
        i = k
        avant = 'a'
        avantAvant = ' '
        return true
    }

    // ------------------------------------------------------------------ mots

    private fun estMot(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    private fun lireMot() {
        val debut = i
        var k = i
        while (k < n && (estMot(code[k]) || (profil.tiretDansMots && code[k] == '-' && k + 1 < n && code[k + 1].isLetter()))) k++
        // `defined?`, `empty?` (Ruby), `save!` : le ? ou ! final fait partie du nom
        if (k < n && (code[k] == '?' || code[k] == '!') && profil.motsCles.contains(code.substring(debut, k + 1))) k++
        val mot = code.substring(debut, k)
        val cle = if (profil.sensibleCasse) mot else mot.lowercase(Locale.ROOT)
        val suivantParenthese = prochainNonBlanc(k) == '('
        val apresPoint = profil.proprietes &&
            (avant == '.' || (profil.flecheMembre && avant == '>' && avantAvant == '-') || (avant == ':' && avantAvant == ':'))

        val recepteur = prochainNonBlanc(k) == '.'
        val genre: Genre? = when {
            nomAttendu && cle !in profil.motsCles && !recepteur -> Genre.DECLARATION
            apresPoint && cle !in profil.constantes -> if (suivantParenthese) Genre.FONCTION else Genre.PROPRIETE
            cle in profil.motsCles -> Genre.MOT_CLE
            cle in profil.constantes -> Genre.CONSTANTE
            cle in profil.types -> Genre.TYPE
            cle in profil.integres -> Genre.INTEGRE
            suivantParenthese -> Genre.FONCTION
            mot.length > 1 && mot.all { it.isUpperCase() || it.isDigit() || it == '_' } && mot.any { it.isLetter() } -> Genre.CONSTANTE
            profil.majusculeEstType && mot.length > 1 && mot[0].isUpperCase() -> Genre.TYPE
            else -> null
        }
        if (genre != null) ajouter(debut, k, genre)
        nomAttendu = if (genre == Genre.MOT_CLE) cle in profil.declarations else nomAttendu && recepteur
        avantAvant = avant
        avant = 'a'
        i = k
    }

    private fun prochainNonBlanc(depuis: Int): Char {
        var k = depuis
        while (k < n && (code[k] == ' ' || code[k] == '\t')) k++
        return if (k < n) code[k] else ' '
    }

    // ------------------------------------------------------------------ signes

    private fun ouvrir(c: Char) {
        val profondeur = pile.size
        pile.add(c)
        ajouter(i, i + 1, parenthese(profondeur))
        i++
        avantAvant = avant
        avant = c
        // `fun <T> foo` : le nom suit les chevrons, pas la parenthese
        if (c != '(') nomAttendu = false
    }

    private fun fermer() {
        if (pile.isEmpty()) {
            ajouter(i, i + 1, Genre.PONCTUATION)
        } else {
            pile.removeAt(pile.lastIndex)
            ajouter(i, i + 1, parenthese(pile.size))
        }
        i++
        avantAvant = avant
        avant = ')'
        nomAttendu = false
    }

    private fun parenthese(profondeur: Int) = when (profondeur % 3) {
        0 -> Genre.PAREN_1
        1 -> Genre.PAREN_2
        else -> Genre.PAREN_3
    }

    private fun lireOperateurs() {
        val debut = i
        var k = i
        while (k < n && code[k] in OPERATEURS) k++
        // une marque de commentaire ne fait pas partie de l'operateur (a=b//c)
        for (marque in profil.commentairesLigne + profil.commentairesBloc.map { it.first }) {
            val m = code.indexOf(marque, debut)
            if (m in (debut + 1) until k) { k = m }
        }
        if (k == debut) k = debut + 1
        ajouter(debut, k, Genre.OPERATEUR)
        avantAvant = if (k - debut >= 2) code[k - 2] else avant
        avant = code[k - 1]
        i = k
        nomAttendu = false
    }

    private fun ponctuation(debut: Int, fin: Int) {
        ajouter(debut, fin, Genre.PONCTUATION)
        avantAvant = avant
        avant = code[debut]
        if (code[debut] != ',' && code[debut] != '.') nomAttendu = false
    }

    private fun ajouter(debut: Int, fin: Int, genre: Genre, couleur: Int = 0) {
        sortie.ajouter(base + debut, base + fin, genre, couleur)
    }

    private companion object {
        const val OPERATEURS = "+-*/%=<>!&|^~?:"
    }
}
