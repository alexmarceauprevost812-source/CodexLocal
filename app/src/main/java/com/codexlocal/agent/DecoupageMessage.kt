package com.codexlocal.agent

import com.codexlocal.project.Langages

/** Un morceau d'une reponse de l'IA, pour l'affichage dans le chat. */
sealed interface BlocMessage {

    data class Texte(val texte: String) : BlocMessage

    /**
     * Du code, avec le titre (nom du fichier) quand on le connait. [ferme] est faux tant que
     * la fin du bloc n'est pas arrivee (reponse encore en cours, ou coupee).
     */
    data class Code(
        val titre: String?,
        val langage: String,
        val corps: String,
        val ferme: Boolean
    ) : BlocMessage
}

/**
 * Decoupe une reponse en texte et en blocs de code pour l'afficher : chaque fichier ecrit par l'IA
 * (`=== FICHIER: x ===` ou bloc ``` avec son nom) devient un bloc avec son TITRE, y compris
 * pendant qu'il s'ecrit. Code pur, sans dependance Android.
 */
object DecoupageMessage {

    private val ouvertureBloc = Regex("""^\s*```+(.*)$""")

    fun decouper(reponse: String): List<BlocMessage> {
        val blocs = ArrayList<BlocMessage>()
        val texte = ArrayList<String>()

        fun viderTexte() {
            if (texte.isNotEmpty()) {
                blocs.add(BlocMessage.Texte(texte.joinToString("\n")))
                texte.clear()
            }
        }

        val lignes = reponse.lines()
        var i = 0
        while (i < lignes.size) {
            val ligne = lignes[i]
            val ecrire = ActionsFichiers.marqueurEcrire.matchEntire(ligne)
            val lire = ActionsFichiers.marqueurLire.matchEntire(ligne)
            val fence = ouvertureBloc.matchEntire(ligne)
            when {
                ecrire != null -> {
                    viderTexte()
                    val chemin = com.codexlocal.studio.StudioParser.nettoyerChemin(ecrire.groupValues[1])
                    val corps = ArrayList<String>()
                    var ferme = false
                    i++
                    while (i < lignes.size) {
                        val l = lignes[i]
                        if (ActionsFichiers.marqueurFin.matches(l)) { ferme = true; i++; break }
                        if (ActionsFichiers.marqueurEcrire.matches(l) || ActionsFichiers.marqueurLire.matches(l)) {
                            ferme = true
                            break
                        }
                        corps.add(l)
                        i++
                    }
                    blocs.add(
                        BlocMessage.Code(
                            chemin.ifBlank { null }, langageDe(chemin, ""),
                            ActionsFichiers.contenuFichier(corps).trimEnd('\n'), ferme
                        )
                    )
                    continue
                }
                lire != null -> {
                    val chemin = com.codexlocal.studio.StudioParser.nettoyerChemin(lire.groupValues[1])
                    texte.add("📖 Lecture : $chemin")
                }
                fence != null -> {
                    val info = fence.groupValues[1].trim()
                    val aRetirer = texte.indexOfLast { it.isNotBlank() }
                    val titreAvant = if (aRetirer >= 0 && texte.size - aRetirer <= 2) {
                        NomsFichiers.nomDansLigne(texte[aRetirer])?.also { nom ->
                            // une ligne qui ne contient que le nom devient le titre du bloc, pas du texte en double
                            if (NomsFichiers.nomDansLigne(texte[aRetirer]) == nom && ligneDeNomSeul(texte[aRetirer], nom)) {
                                while (texte.size > aRetirer) texte.removeAt(texte.size - 1)
                            }
                        }
                    } else {
                        null
                    }
                    viderTexte()
                    val corps = ArrayList<String>()
                    var profondeur = 0
                    var ferme = false
                    i++
                    while (i < lignes.size) {
                        val l = lignes[i]
                        val f = ouvertureBloc.matchEntire(l)
                        if (f != null && f.groupValues[1].isNotBlank()) {
                            profondeur++
                        } else if (f != null && profondeur > 0) {
                            profondeur--
                        } else if (f != null) {
                            ferme = true
                            i++
                            break
                        }
                        corps.add(l)
                        i++
                    }
                    val titre = NomsFichiers.nomDansInfo(info) ?: titreAvant
                        ?: corps.firstOrNull()?.let { NomsFichiers.nomDansPremiereLigne(it) }
                    val langage = NomsFichiers.langageDansInfo(info).ifBlank { langageDe(titre.orEmpty(), "") }
                    blocs.add(
                        BlocMessage.Code(titre, langage, corps.joinToString("\n").trim('\n'), ferme)
                    )
                    continue
                }
                else -> texte.add(ligne)
            }
            i++
        }
        viderTexte()
        return blocs
    }

    private fun langageDe(chemin: String, defaut: String): String =
        if (chemin.isBlank()) defaut else Langages.pourColoration(chemin)

    /** Vrai quand la ligne n'est que le nom du fichier (avec decoration), sans phrase autour. */
    private fun ligneDeNomSeul(ligne: String, nom: String): Boolean {
        val reste = ligne.replace(nom, "")
        return reste.none { it.isLetterOrDigit() }
    }
}
