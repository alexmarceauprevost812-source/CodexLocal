package com.codexlocal.agent

import com.codexlocal.studio.StudioParser

/** Ce que l'IA demande de faire dans le projet. */
sealed interface ActionFichier {

    /**
     * Creer ou remplacer un fichier. `complet` est faux quand le bloc n'a pas ete ferme par
     * `=== FIN ===` : la reponse a pu etre coupee avant la fin du fichier.
     */
    data class Ecrire(val chemin: String, val contenu: String, val complet: Boolean = true) :
        ActionFichier

    /** Lire un fichier existant : son contenu est renvoye a l'IA au tour suivant. */
    data class Lire(val chemin: String) : ActionFichier

    /**
     * Un gros bloc de code sans nom de fichier : on ne sait pas ou l'ecrire. L'IA peut lui en
     * choisir un (voir [NommageIA]) ; sinon rien n'est cree. [ligneOuverture] est le numero de la
     * ligne ``` qui ouvre le bloc dans la reponse, [contenu] son code.
     */
    data class SansNom(
        val langage: String,
        val lignes: Int,
        val contenu: String = "",
        val ligneOuverture: Int = -1,
        val complet: Boolean = true
    ) : ActionFichier
}

/**
 * Lecture des reponses de l'IA. Code pur, sans dependance Android.
 *
 * Format :
 * ```
 * === FICHIER: src/main.py ===
 * (contenu complet)
 * === FIN ===
 * === LIRE: README.md ===
 * ```
 *
 * Les petits modeles suivent mal ce format : quand il n'y a aucun marqueur, on lit aussi les blocs
 * de code ``` dont le nom de fichier est ecrit juste avant, dans la ligne d'ouverture ou en
 * commentaire (voir [NomsFichiers]).
 */
object ActionsFichiers {

    const val MAX_ECRITURES = 20
    const val MAX_LECTURES = 4

    internal val marqueurEcrire =
        Regex("""^\s*={2,}\s*FICHIER\s*:\s*(.+?)\s*=*\s*$""", RegexOption.IGNORE_CASE)
    internal val marqueurLire =
        Regex("""^\s*={2,}\s*LIRE\s*:\s*(.+?)\s*=*\s*$""", RegexOption.IGNORE_CASE)
    internal val marqueurFin =
        Regex("""^\s*={2,}\s*FIN\s*={2,}\s*$""", RegexOption.IGNORE_CASE)

    /** Langages dont un gros bloc de code est presque toujours un fichier a creer. */
    private val LANGAGES_DE_FICHIER = setOf(
        "html", "css", "scss", "js", "javascript", "jsx", "ts", "typescript", "tsx", "json", "xml", "yaml", "yml",
        "py", "python", "kt", "kotlin", "java", "php", "c", "cpp", "csharp", "cs", "go", "rust", "rs", "swift",
        "dart", "ruby", "rb", "sql", "lua", "md", "markdown", "toml", "ini"
    )

    private val ouvertureBloc = Regex("""^\s*```+(.*)$""")

    /** Nombre de ` au debut de la ligne : un bloc ouvert avec 4 ne se ferme qu'avec 4 ou plus. */
    internal fun nombreDeBackticks(ligne: String): Int = ligne.trimStart().takeWhile { it == '`' }.length

    /** Les actions de la reponse : marqueurs `=== FICHIER ===`, sinon blocs de code ayant un nom de fichier. */
    fun extraire(reponse: String): List<ActionFichier> {
        val parMarqueurs = extraireMarqueurs(reponse)
        return if (parMarqueurs.isEmpty()) extraireBlocsDeCode(reponse) else parMarqueurs
    }

    private fun extraireMarqueurs(reponse: String): List<ActionFichier> {
        val resultat = ArrayList<ActionFichier>()
        var chemin: String? = null
        val lignes = ArrayList<String>()
        var ecritures = 0
        var lectures = 0

        fun fermer(complet: Boolean) {
            val c = chemin ?: return
            if (c.isNotBlank() && ecritures < MAX_ECRITURES) {
                resultat.add(ActionFichier.Ecrire(c, contenuFichier(lignes), complet))
                ecritures++
            }
            chemin = null
            lignes.clear()
        }

        for (ligne in reponse.lines()) {
            val ecrire = marqueurEcrire.matchEntire(ligne)
            val lire = marqueurLire.matchEntire(ligne)
            when {
                ecrire != null -> {
                    fermer(true)
                    chemin = StudioParser.nettoyerChemin(ecrire.groupValues[1])
                }
                lire != null -> {
                    fermer(true)
                    val c = StudioParser.nettoyerChemin(lire.groupValues[1])
                    val dejaDemande = resultat.any { it is ActionFichier.Lire && it.chemin == c }
                    if (c.isNotBlank() && lectures < MAX_LECTURES && !dejaDemande) {
                        resultat.add(ActionFichier.Lire(c))
                        lectures++
                    }
                }
                marqueurFin.matches(ligne) -> fermer(true)
                chemin != null -> lignes.add(ligne)
            }
        }
        fermer(false)
        return resultat
    }

    /**
     * Les blocs de code ``` d'une reponse qui donne un nom de fichier : dans la ligne d'ouverture,
     * sur la ligne (ou les deux) juste avant, ou en commentaire sur la premiere ligne du code.
     * Un gros bloc de langage « de fichier » sans nom devient [ActionFichier.SansNom].
     */
    internal fun extraireBlocsDeCode(reponse: String): List<ActionFichier> {
        val resultat = ArrayList<ActionFichier>()
        var enBloc = false
        var profondeur = 0
        var longueur = 0
        var info = ""
        var titreAvant: String? = null
        var avant1 = ""
        var avant2 = ""
        var ecritures = 0
        var ouverture = -1
        val contenu = ArrayList<String>()

        fun fermer(complet: Boolean) {
            val corps = contenu.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
            val nom = NomsFichiers.nomDansInfo(info) ?: titreAvant
                ?: corps.firstOrNull()?.let { NomsFichiers.nomDansPremiereLigne(it) }
            if (nom != null) {
                if (corps.isNotEmpty() && ecritures < MAX_ECRITURES) {
                    resultat.add(ActionFichier.Ecrire(nom, corps.joinToString("\n") + "\n", complet))
                    ecritures++
                }
            } else {
                val langage = NomsFichiers.langageDansInfo(info).lowercase()
                val utiles = corps.count { it.isNotBlank() }
                if (langage in LANGAGES_DE_FICHIER && utiles >= 4) {
                    resultat.add(
                        ActionFichier.SansNom(langage, utiles, corps.joinToString("\n") + "\n", ouverture, complet)
                    )
                }
            }
            enBloc = false
            profondeur = 0
            longueur = 0
            info = ""
            titreAvant = null
            avant1 = ""
            avant2 = ""
            contenu.clear()
        }

        reponse.lines().forEachIndexed { numero, ligne ->
            val fence = ouvertureBloc.matchEntire(ligne)
            when {
                !enBloc && fence != null -> {
                    enBloc = true
                    ouverture = numero
                    longueur = nombreDeBackticks(ligne)
                    info = fence.groupValues[1].trim()
                    titreAvant = NomsFichiers.nomDansLigne(if (avant1.isNotBlank()) avant1 else avant2)
                }
                !enBloc -> {
                    avant2 = avant1
                    avant1 = ligne
                }
                fence != null && longueur >= 4 -> {
                    // bloc ouvert avec 4 ` ou plus : seul un ` assez long ferme, le reste est du fichier
                    if (fence.groupValues[1].isBlank() && nombreDeBackticks(ligne) >= longueur) {
                        fermer(true)
                    } else {
                        contenu.add(ligne)
                    }
                }
                fence != null && fence.groupValues[1].isNotBlank() -> {
                    // un bloc ouvert a l'interieur du fichier (README qui montre du code)
                    profondeur++
                    contenu.add(ligne)
                }
                fence != null && profondeur > 0 -> {
                    profondeur--
                    contenu.add(ligne)
                }
                fence != null -> fermer(true)
                else -> contenu.add(ligne)
            }
        }
        if (enBloc) fermer(false)
        return resultat
    }

    /**
     * La reponse avec, juste avant chaque ligne d'ouverture donnee (numero de ligne -> nom), le
     * nom du fichier en gras : c'est la forme que [extraire] et l'affichage reconnaissent.
     */
    fun avecTitres(reponse: String, noms: Map<Int, String>): String {
        if (noms.isEmpty()) return reponse
        val resultat = ArrayList<String>()
        reponse.lines().forEachIndexed { numero, ligne ->
            noms[numero]?.let { resultat.add("**$it**") }
            resultat.add(ligne)
        }
        return resultat.joinToString("\n")
    }

    /**
     * Le contenu d'un fichier : lignes vides aux extremites retirees, et clôture ```
     * eventuelle enlevee (le texte qui suivrait la cloture est du commentaire, pas du fichier).
     */
    internal fun contenuFichier(lignes: List<String>): String {
        var l = lignes.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        if (l.isNotEmpty() && l.first().trimStart().startsWith("```")) {
            val fin = l.indexOfLast { it.trim() == "```" }
            l = if (fin > 0) l.subList(1, fin) else l.drop(1)
        }
        return if (l.isEmpty()) "" else l.joinToString("\n") + "\n"
    }
}
