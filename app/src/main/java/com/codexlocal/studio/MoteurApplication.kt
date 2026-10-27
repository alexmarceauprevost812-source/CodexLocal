package com.codexlocal.studio

import android.content.Context
import com.codexlocal.agent.ActionFichier
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore

/** Small offline application runtime copied into real project files, previewed and packaged normally. */
object MoteurApplication {
    const val CHEMIN = "moteurs/lexapp-v1.js"
    const val CONSIGNE =
        "Moteur local disponible : LexApp v1 (navigation, stockage local, animations 2D et schémas SVG). " +
        "Pour l'utiliser dans index.html, ajoute <script src=\"moteurs/lexapp-v1.js\"></script> avant ton script. " +
        "Le fichier moteur est fourni automatiquement : ne le réécris pas. " +
        "API : LexApp.el(tag,texte,attributs) crée un élément DOM; " +
        "LexApp.mount(element,{title,pages:[{id,title,render(root){...}}]}) crée les pages et la navigation. " +
        "render peut renvoyer une fonction de nettoyage appelée au changement de page. " +
        "LexApp.save(cle,valeur) renvoie vrai si sauvegardé; LexApp.load(cle,valeurParDefaut) relit le JSON. " +
        "LexApp.animate(element,'appear'|'pulse'|'turn',450) renvoie une animation annulable. " +
        "LexApp.particles(canvas,{count:35,color:'#ff7900'}) renvoie une fonction pour arrêter l'effet. " +
        "LexApp.diagram(element,{title,nodes:[{id:'a',label:'Départ'},{id:'b',label:'Fin'}]," +
        "edges:[{from:'a',to:'b'}]}) dessine un schéma SVG, deux colonnes, liens fléchés. " +
        "Ces outils fonctionnent sans CDN, dans l'aperçu et l'APK du Studio. " +
        "Crée une application complète dans index.html, avec viewport, vrais boutons et actions."

    private fun source(context: Context) = context.assets.open(CHEMIN)
        .bufferedReader(Charsets.UTF_8).use { it.readText() }

    /** Same file-action pipeline as model output, so installation is included in Undo. */
    fun completerActions(context: Context, projet: Projet, actions: List<ActionFichier>): List<ActionFichier> {
        val ecritures = actions.filterIsInstance<ActionFichier.Ecrire>()
        if (ecritures.none { it.complet && it.contenu.contains(CHEMIN) } ||
            ecritures.any { it.chemin == CHEMIN } ||
            ProjectStore.trouverFichier(context, projet, CHEMIN) != null) return actions
        return listOf(ActionFichier.Ecrire(CHEMIN, source(context))) + actions
    }

    fun completerPropositions(context: Context, projet: Projet, fichiers: List<FichierPropose>): List<FichierPropose> {
        if (fichiers.none { it.contenu.contains(CHEMIN) } || fichiers.any { it.chemin == CHEMIN } ||
            ProjectStore.trouverFichier(context, projet, CHEMIN) != null) return fichiers
        return listOf(FichierPropose(CHEMIN, source(context))) + fichiers
    }

    /** Called only for a new project, never replaces an existing application's entry point. */
    fun creerBase(context: Context, projet: Projet) {
        require(ProjectStore.trouverFichier(context, projet, "index.html") == null) { "Le projet contient déjà une application." }
        ProjectStore.creerFichier(context, projet, CHEMIN, source(context))
        val page = context.assets.open("moteurs/index.html").bufferedReader(Charsets.UTF_8).use { it.readText() }
        ProjectStore.creerFichier(context, projet, "index.html", page)
    }
}
