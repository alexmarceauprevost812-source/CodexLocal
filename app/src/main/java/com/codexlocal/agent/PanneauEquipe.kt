package com.codexlocal.agent

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import com.codexlocal.R

/**
 * Bandeau du haut de l'ecran : les cinq etapes de l'equipe, ce qui s'ecrit en ce moment,
 * et le temps ecoule. Il ne fait qu'afficher ; MainActivity lui dit quoi montrer.
 */
class PanneauEquipe(
    private val conteneur: View,
    private val etapes: List<TextView>,
    private val vueActivite: TextView,
    private val vueChrono: TextView
) {
    private enum class Etat { ATTENTE, EN_COURS, FAIT }

    private val roles = Role.values()

    init {
        require(etapes.size == roles.size) { "Un voyant par role" }
    }

    /** Le tag garde la demande d'affichage : ChatWindowInsets peut masquer le panneau (clavier). */
    fun afficher(visible: Boolean) {
        conteneur.tag = visible
        conteneur.isVisible = visible
    }

    /** Tout le monde en attente : le travail n'a pas encore commence. */
    fun reinitialiser() {
        roles.forEach { montrer(it, Etat.ATTENTE) }
        vueActivite.text = ""
        vueChrono.text = ""
    }

    fun commencer(role: Role) {
        montrer(role, Etat.EN_COURS)
        vueActivite.text = "${role.emoji} ${role.titre} : se met au travail…"
    }

    fun terminer(role: Role) = montrer(role, Etat.FAIT)

    /** Texte du moment (derniere ligne ecrite, fichier cree…). */
    fun activite(texte: String) {
        vueActivite.text = texte
    }

    fun chrono(secondes: Long) {
        vueChrono.text = "⏱ ${secondes / 60}:${(secondes % 60).toString().padStart(2, '0')}"
    }

    fun fini(resume: String) {
        vueActivite.text = resume
    }

    private fun montrer(role: Role, etat: Etat) {
        val vue = etapes[role.ordinal]
        val ctx = vue.context
        when (etat) {
            Etat.ATTENTE -> {
                vue.text = "${role.emoji} ${role.court}"
                vue.setTextColor(ctx.getColor(R.color.grey))
                vue.setTypeface(null, Typeface.NORMAL)
                vue.background = null
            }
            Etat.EN_COURS -> {
                vue.text = "${role.emoji} ${role.court}…"
                vue.setTextColor(ctx.getColor(R.color.orange))
                vue.setTypeface(null, Typeface.BOLD)
                vue.setBackgroundResource(R.drawable.bg_equipe_actif)
            }
            Etat.FAIT -> {
                vue.text = "✓ ${role.court}"
                vue.setTextColor(ctx.getColor(R.color.equipe_ok))
                vue.setTypeface(null, Typeface.NORMAL)
                vue.background = null
            }
        }
    }
}
