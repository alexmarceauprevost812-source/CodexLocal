package com.codexlocal.coloration

/** Markdown : titres, listes, citations, code (avec le langage du bloc), liens, gras. */
internal class BalayeurMarkdown(
    private val code: String,
    private val sortie: Sortie = Sortie(),
    private val base: Int = 0
) {
    private val n = code.length

    fun analyser(): List<Jeton> {
        var debut = 0
        var clotureOuverte: String? = null
        var langageBloc = ""
        var debutContenu = 0
        while (debut <= n) {
            var fin = code.indexOf('\n', debut)
            if (fin < 0) fin = n
            var p = debut
            while (p < fin && (code[p] == ' ' || code[p] == '\t')) p++
            val cloture = if (code.startsWith("```", p)) "```" else if (code.startsWith("~~~", p)) "~~~" else null

            if (clotureOuverte != null) {
                if (cloture == clotureOuverte && code.substring(p, fin).trim().all { it == cloture[0] }) {
                    contenuDeBloc(debutContenu, debut, langageBloc)
                    ajouter(p, fin, Genre.PONCTUATION)
                    clotureOuverte = null
                }
            } else if (cloture != null) {
                var t = p
                while (t < fin && code[t] == cloture[0]) t++
                ajouter(p, t, Genre.PONCTUATION)
                val langage = code.substring(t, fin).trim()
                if (langage.isNotEmpty()) {
                    var d = t
                    while (d < fin && code[d] == ' ') d++
                    ajouter(d, trimFin(d, fin), Genre.TYPE)
                }
                clotureOuverte = cloture
                langageBloc = langage
                debutContenu = minOf(fin + 1, n)
            } else {
                ligne(debut, p, fin)
            }
            if (fin >= n) break
            debut = fin + 1
        }
        if (clotureOuverte != null) contenuDeBloc(debutContenu, n, langageBloc)
        return sortie.liste
    }

    private fun ajouter(debut: Int, fin: Int, genre: Genre, couleur: Int = 0) {
        sortie.ajouter(base + debut, base + fin, genre, couleur)
    }

    private fun trimFin(debut: Int, fin: Int): Int {
        var f = fin
        while (f > debut && code[f - 1].isWhitespace()) f--
        return f
    }

    private fun contenuDeBloc(debut: Int, fin: Int, langage: String) {
        if (fin <= debut) return
        val nom = Profils.normaliser(langage)
        if (nom == "markdown" || nom.isEmpty()) return
        for (j in AnalyseurCode.analyser(code.substring(debut, fin), nom)) {
            sortie.ajouter(base + debut + j.debut, base + debut + j.fin, j.genre, j.couleur)
        }
    }

    // ------------------------------------------------------------------ lignes

    private fun ligne(debut: Int, p: Int, fin: Int) {
        if (p >= fin) return
        var k = p
        // titre : # a ###### suivis d'une espace
        var d = p
        while (d < fin && code[d] == '#') d++
        if (d in (p + 1)..(p + 6) && (d >= fin || code[d] == ' ') && p - debut <= 3) {
            ajouter(p, trimFin(p, fin), Genre.TITRE)
            return
        }
        // ligne horizontale
        val trait = code[p]
        if ((trait == '-' || trait == '*' || trait == '_') && code.substring(p, fin).all { it == trait || it == ' ' } &&
            code.substring(p, fin).count { it == trait } >= 3
        ) {
            ajouter(p, trimFin(p, fin), Genre.PONCTUATION)
            return
        }
        // citation : le texte est grise
        if (k < fin && code[k] == '>') {
            while (k < fin && code[k] == '>') {
                ajouter(k, k + 1, Genre.OPERATEUR)
                k++
                while (k < fin && code[k] == ' ') k++
            }
            ajouter(k, trimFin(k, fin), Genre.COMMENTAIRE)
            return
        }
        // liste a puces ou numerotee
        if (k < fin && (code[k] == '-' || code[k] == '*' || code[k] == '+') && k + 1 < fin && code[k + 1] == ' ') {
            ajouter(k, k + 1, Genre.OPERATEUR)
            k += 2
            k = caseACocher(k, fin)
        } else if (k < fin && code[k].isDigit()) {
            var e = k
            while (e < fin && code[e].isDigit() && e - k < 9) e++
            if (e < fin && (code[e] == '.' || code[e] == ')') && e + 1 < fin && code[e + 1] == ' ') {
                ajouter(k, e + 1, Genre.OPERATEUR)
                k = caseACocher(e + 2, fin)
            }
        }
        inline(k, fin)
    }

    private fun caseACocher(depuis: Int, fin: Int): Int {
        if (depuis + 2 < fin && code[depuis] == '[' && code[depuis + 2] == ']' && code[depuis + 1] in " xX") {
            ajouter(depuis, depuis + 3, Genre.PONCTUATION)
            return depuis + 3
        }
        return depuis
    }

    // ------------------------------------------------------------------ dans une ligne

    private fun inline(debut: Int, fin: Int) {
        var k = debut
        while (k < fin) {
            val c = code[k]
            when {
                c == '`' -> k = codeEnLigne(k, fin)
                c == '\\' && k + 1 < fin && !code[k + 1].isLetterOrDigit() && code[k + 1] != ' ' -> {
                    ajouter(k, k + 2, Genre.ECHAPPEMENT)
                    k += 2
                }
                c == '!' && k + 1 < fin && code[k + 1] == '[' -> {
                    ajouter(k, k + 1, Genre.PONCTUATION)
                    k++
                }
                c == '[' -> k = lien(k, fin)
                (c == '*' || c == '_') && k + 1 < fin && code[k + 1] == c -> k = gras(k, fin, c)
                code.startsWith("http://", k) || code.startsWith("https://", k) -> k = adresse(k, fin)
                else -> k++
            }
        }
    }

    private fun codeEnLigne(debut: Int, fin: Int): Int {
        var longueur = 0
        while (debut + longueur < fin && code[debut + longueur] == '`') longueur++
        val marque = "`".repeat(longueur)
        val f = code.indexOf(marque, debut + longueur)
        if (f < 0 || f + longueur > fin) return debut + longueur
        ajouter(debut, f + longueur, Genre.TEXTE)
        return f + longueur
    }

    private fun lien(debut: Int, fin: Int): Int {
        val fermante = code.indexOf(']', debut)
        if (fermante < 0 || fermante >= fin || fermante + 1 >= fin || code[fermante + 1] != '(') return debut + 1
        val fermeture = code.indexOf(')', fermante + 2)
        if (fermeture < 0 || fermeture >= fin) return debut + 1
        ajouter(debut, debut + 1, Genre.PONCTUATION)
        ajouter(debut + 1, fermante, Genre.LIEN)
        ajouter(fermante, fermante + 2, Genre.PONCTUATION)
        ajouter(fermante + 2, fermeture, Genre.TEXTE)
        ajouter(fermeture, fermeture + 1, Genre.PONCTUATION)
        return fermeture + 1
    }

    private fun gras(debut: Int, fin: Int, c: Char): Int {
        val marque = "$c$c"
        val f = code.indexOf(marque, debut + 2)
        if (f < 0 || f + 2 > fin || f == debut + 2 || code[debut + 2] == ' ') return debut + 2
        ajouter(debut, f + 2, Genre.MOT_CLE)
        return f + 2
    }

    private fun adresse(debut: Int, fin: Int): Int {
        var e = debut
        while (e < fin && !code[e].isWhitespace() && code[e] != ')' && code[e] != '>' && code[e] != ']') e++
        ajouter(debut, e, Genre.LIEN)
        return e
    }
}
