package com.codexlocal.studio

/** Un fichier proposé par l'IA, avant sa création dans le projet. */
data class FichierPropose(val chemin: String, val contenu: String)

/** Lecture des réponses de l'IA du Studio. Code pur, sans dépendance Android. */
object StudioParser {

    const val MAX_FICHIERS = 30

    private val marqueur =
        Regex("""^\s*={2,}\s*FICHIER\s*:\s*(.+?)\s*=*\s*$""", RegexOption.IGNORE_CASE)

    private val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val svgBloc = Regex("<svg\\b.*?</svg\\s*>", options)
    private val scripts = Regex("<script\\b.*?</script\\s*>", options)
    private val images = Regex("<image\\b[^>]*?(/>|>.*?</image\\s*>)", options)
    private val objetsEtrangers = Regex("<foreignObject\\b.*?</foreignObject\\s*>", options)
    private val gestionnaires =
        Regex("\\son[a-z]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)", RegexOption.IGNORE_CASE)
    private val liensScript = Regex(
        "(xlink:)?href\\s*=\\s*(\"\\s*javascript:[^\"]*\"|'\\s*javascript:[^']*')",
        RegexOption.IGNORE_CASE
    )

    /** Fichiers de la forme `=== FICHIER: chemin ===` suivi de leur contenu. */
    fun extraireFichiers(reponse: String): List<FichierPropose> {
        val resultat = ArrayList<FichierPropose>()
        var chemin: String? = null
        val lignes = ArrayList<String>()

        fun fermer() {
            val c = chemin ?: return
            if (c.isNotBlank() && resultat.size < MAX_FICHIERS) {
                resultat.add(FichierPropose(c, nettoyerContenu(lignes)))
            }
            lignes.clear()
        }

        for (ligne in reponse.lines()) {
            val m = marqueur.matchEntire(ligne)
            if (m != null) {
                fermer()
                chemin = nettoyerChemin(m.groupValues[1])
            } else if (chemin != null) {
                lignes.add(ligne)
            }
        }
        fermer()
        return resultat
    }

    internal fun nettoyerChemin(brut: String): String =
        brut.trim().trim('`', '"', '\'', '*').trim().removePrefix("./")

    /** Enlève les lignes vides aux extrémités et une éventuelle clôture ```. */
    internal fun nettoyerContenu(lignes: List<String>): String {
        val l = lignes.toMutableList()
        while (l.isNotEmpty() && l.first().isBlank()) l.removeAt(0)
        while (l.isNotEmpty() && l.last().isBlank()) l.removeAt(l.size - 1)
        if (l.size >= 2 && l.first().trim().startsWith("```") && l.last().trim() == "```") {
            l.removeAt(l.size - 1)
            l.removeAt(0)
        }
        return if (l.isEmpty()) "" else l.joinToString("\n") + "\n"
    }

    /** Le premier SVG de la réponse, débarrassé de tout ce qui peut s'exécuter. */
    fun extraireSvg(reponse: String): String? =
        svgBloc.find(reponse)?.value?.let(::nettoyerSvg)

    fun nettoyerSvg(svg: String): String {
        var s = svg
        s = scripts.replace(s, "")
        s = objetsEtrangers.replace(s, "")
        s = images.replace(s, "")
        s = gestionnaires.replace(s, "")
        s = liensScript.replace(s, "")
        if (!s.contains("xmlns=")) {
            s = s.replaceFirst(Regex("<svg", RegexOption.IGNORE_CASE),
                "<svg xmlns=\"http://www.w3.org/2000/svg\"")
        }
        return s
    }
}
