package com.codexlocal.coloration

/** XML, HTML, SVG, layouts Android : balises, attributs, valeurs, commentaires, entites. */
internal class BalayeurBalisage(
    private val code: String,
    private val sortie: Sortie = Sortie(),
    private val base: Int = 0,
    /** Android ecrit les couleurs `#AARRGGBB`, le web `#RRGGBBAA`. */
    private val couleurAlphaEnPremier: Boolean
) {
    private val n = code.length
    private var i = 0
    private var attributCourant = ""

    fun analyser(): List<Jeton> {
        while (i < n) {
            val c = code[i]
            when {
                code.startsWith("<!--", i) -> bloc("-->", Genre.COMMENTAIRE)
                code.startsWith("<![CDATA[", i) -> bloc("]]>", Genre.TEXTE)
                c == '<' && i + 1 < n && (code[i + 1] == '!' || code[i + 1] == '?') -> declaration()
                c == '<' && i + 1 < n && (code[i + 1].isLetter() || code[i + 1] == '/' || code[i + 1] == '_') -> balise()
                c == '&' -> entite()
                else -> i++
            }
        }
        return sortie.liste
    }

    private fun ajouter(debut: Int, fin: Int, genre: Genre, couleur: Int = 0) {
        sortie.ajouter(base + debut, base + fin, genre, couleur)
    }

    private fun bloc(fermeture: String, genre: Genre) {
        val f = code.indexOf(fermeture, i)
        val bout = if (f < 0) n else f + fermeture.length
        ajouter(i, bout, genre)
        i = bout
    }

    /** `<!DOCTYPE html>`, `<?xml version="1.0"?>` */
    private fun declaration() {
        val instruction = code[i + 1] == '?'
        var k = i + 2
        while (k < n && (code[k].isLetterOrDigit() || code[k] == '-' || code[k] == ':')) k++
        ajouter(i, k, Genre.MOT_CLE)
        i = k
        attributs(finPrevue = if (instruction) "?>" else ">")
    }

    private fun entite() {
        var k = i + 1
        if (k < n && code[k] == '#') k++
        val debutNom = k
        while (k < n && code[k].isLetterOrDigit()) k++
        if (k < n && code[k] == ';' && k > debutNom) {
            ajouter(i, k + 1, Genre.ECHAPPEMENT)
            i = k + 1
        } else {
            i++
        }
    }

    private fun balise() {
        val ouverture = if (code[i + 1] == '/') 2 else 1
        ajouter(i, i + ouverture, Genre.PONCTUATION)
        var k = i + ouverture
        val debutNom = k
        while (k < n && !code[k].isWhitespace() && code[k] != '>' && code[k] != '/') k++
        val nom = code.substring(debutNom, k)
        ajouter(debutNom, k, Genre.BALISE)
        i = k
        attributs(finPrevue = ">")
        val autoFermee = i >= 2 && code.startsWith("/>", i - 2)
        if (ouverture == 1 && !autoFermee) {
            when (nom.lowercase()) {
                "script" -> contenuIncorpore("</script", "javascript")
                "style" -> contenuIncorpore("</style", "css")
            }
        }
    }

    /** Lit `nom="valeur" autre='x' seul` jusqu'a `>` ou `/>`. */
    private fun attributs(finPrevue: String) {
        while (i < n) {
            val c = code[i]
            when {
                c.isWhitespace() -> i++
                code.startsWith(finPrevue, i) -> {
                    ajouter(i, i + finPrevue.length, Genre.PONCTUATION)
                    i += finPrevue.length
                    return
                }
                c == '/' && i + 1 < n && code[i + 1] == '>' -> {
                    ajouter(i, i + 2, Genre.PONCTUATION)
                    i += 2
                    return
                }
                c == '<' -> return          // balise jamais fermee : on repart sur la suivante
                c == '"' || c == '\'' -> valeur()
                c == '=' -> { ajouter(i, i + 1, Genre.OPERATEUR); i++ }
                else -> {
                    var k = i
                    while (k < n && !code[k].isWhitespace() && code[k] != '=' && code[k] != '>' &&
                        code[k] != '/' && code[k] != '"' && code[k] != '\'' && code[k] != '<'
                    ) k++
                    if (k == i) k = i + 1
                    attributCourant = code.substring(i, k).lowercase()
                    ajouter(i, k, Genre.ATTRIBUT)
                    i = k
                }
            }
        }
    }

    private fun valeur() {
        val guillemet = code[i]
        val f = code.indexOf(guillemet, i + 1)
        val bout = if (f < 0) n else f + 1
        val debutInterieur = i + 1
        val finInterieur = if (f < 0) n else f
        val interieur = code.substring(debutInterieur, finInterieur)
        when {
            interieur.startsWith("#") && interieur.length in 4..9 &&
                CouleursCode.depuisHex(interieur.substring(1), couleurAlphaEnPremier) != null -> {
                ajouter(i, debutInterieur, Genre.TEXTE)
                ajouter(
                    debutInterieur, finInterieur, Genre.COULEUR,
                    CouleursCode.depuisHex(interieur.substring(1), couleurAlphaEnPremier)!!
                )
                ajouter(finInterieur, bout, Genre.TEXTE)
            }
            // @string/nom, @+id/bouton, ?attr/couleur : des references de ressources Android
            interieur.startsWith("@") || interieur.startsWith("?") -> {
                ajouter(i, debutInterieur, Genre.TEXTE)
                ajouter(debutInterieur, finInterieur, Genre.VARIABLE)
                ajouter(finInterieur, bout, Genre.TEXTE)
            }
            // style="color: #f00" : du CSS, avec ses couleurs
            attributCourant == "style" && f >= 0 && interieur.isNotBlank() -> {
                ajouter(i, debutInterieur, Genre.TEXTE)
                for (j in BalayeurCss(interieur, Sortie(), base + debutInterieur).analyser()) {
                    sortie.ajouter(j.debut, j.fin, j.genre, j.couleur)
                }
                ajouter(finInterieur, bout, Genre.TEXTE)
            }
            else -> ajouter(i, bout, Genre.TEXTE)
        }
        i = bout
    }

    /** Le contenu d'un `<script>` ou d'un `<style>` est analyse comme du JavaScript ou du CSS. */
    private fun contenuIncorpore(fermeture: String, langage: String) {
        val f = code.indexOf(fermeture, i, ignoreCase = true)
        val fin = if (f < 0) n else f
        if (fin > i) {
            val morceau = code.substring(i, fin)
            val jetons = if (langage == "css") {
                BalayeurCss(morceau, Sortie(), base + i).analyser()
            } else {
                BalayeurCode(morceau, Profils.pour(langage), Sortie(), base + i, couleurAlphaEnPremier = false).analyser()
            }
            for (j in jetons) sortie.ajouter(j.debut, j.fin, j.genre, j.couleur)
        }
        i = fin
    }
}
