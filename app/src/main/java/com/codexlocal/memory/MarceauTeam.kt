package com.codexlocal.memory

/** Rôles séquentiels de LexOS Mémoire V3 adaptés au moteur Android.
 * L'exécution du modèle reste à raccorder au moteur IA existant.
 */
enum class MarceauRole(val title: String) {
    ARCHITECTE("Planifier"), CODEUR("Exécuter"), REVISEUR("Vérifier")
}

data class MarceauTask(val project: String, val objective: String, val role: MarceauRole)

object MarceauTeam {
    val workflow = listOf(MarceauRole.ARCHITECTE, MarceauRole.CODEUR, MarceauRole.REVISEUR)
    fun tasks(project: String, objective: String): List<MarceauTask> =
        workflow.map { MarceauTask(project, objective, it) }
}
