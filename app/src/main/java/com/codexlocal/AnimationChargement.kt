package com.codexlocal

import android.animation.ValueAnimator
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.google.android.material.progressindicator.LinearProgressIndicator

/**
 * Animation pendant le chargement d'un modele local :
 *  - une barre orange qui defile sous le texte d'etat ;
 *  - une petite roue qui tourne, le nom du modele et les secondes ecoulees ;
 *  - le texte qui « respire » doucement entre deux tons d'orange.
 *
 * Le chargement de llama.cpp ne donne pas de pourcentage : la barre defile sans fin
 * (pas de fausse progression), et le compteur de secondes montre que rien n'est bloque.
 * Tout est cree dans le code : aucun fichier de mise en page a modifier.
 */
class AnimationChargement(private val texte: TextView, private val nomModele: String) {

    private val roue = arrayOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
    private var barre: LinearProgressIndicator? = null
    private var respiration: ValueAnimator? = null
    private var couleurAvant = texte.currentTextColor
    private var debut = 0L
    private var etape = 0
    private var actif = false

    private val tic = object : Runnable {
        override fun run() {
            if (!actif) return
            val secondes = (SystemClock.elapsedRealtime() - debut) / 1000
            texte.text = "${roue[etape++ % roue.size]}  🧠 Chargement de $nomModele…  ${secondes} s"
            texte.postDelayed(this, 90)
        }
    }

    fun demarrer() {
        if (actif) return
        actif = true
        debut = SystemClock.elapsedRealtime()
        couleurAvant = texte.currentTextColor
        ajouterBarre()
        demarrerRespiration()
        texte.post(tic)
    }

    fun arreter() {
        if (!actif) return
        actif = false
        texte.removeCallbacks(tic)
        respiration?.cancel()
        respiration = null
        texte.setTextColor(couleurAvant)
        barre?.let { b ->
            b.hide()
            (b.parent as? ViewGroup)?.removeView(b)
        }
        barre = null
    }

    private fun ajouterBarre() {
        // La barre se place juste sous le texte quand il est dans une colonne (LinearLayout vertical).
        val parent = texte.parent as? LinearLayout ?: return
        if (parent.orientation != LinearLayout.VERTICAL) return
        val ctx = texte.context
        val b = LinearProgressIndicator(ctx).apply {
            isIndeterminate = true
            setIndicatorColor(ContextCompat.getColor(ctx, R.color.orange))
            trackColor = ContextCompat.getColor(ctx, R.color.orange_glow)
            trackThickness = (3 * resources.displayMetrics.density).toInt()
            trackCornerRadius = trackThickness
        }
        parent.addView(
            b,
            parent.indexOfChild(texte) + 1,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        b.show()
        barre = b
    }

    private fun demarrerRespiration() {
        val ctx = texte.context
        val de = ContextCompat.getColor(ctx, R.color.orange_dark)
        val a = ContextCompat.getColor(ctx, R.color.orange_bright)
        respiration = ValueAnimator.ofArgb(de, a).apply {
            duration = 900
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { texte.setTextColor(it.animatedValue as Int) }
            start()
        }
    }
}

/** Montre l'animation le temps de `bloc`, puis l'enleve, meme en cas d'erreur ou d'annulation. */
suspend fun <T> avecAnimationChargement(texte: TextView, nomModele: String, bloc: suspend () -> T): T {
    val animation = AnimationChargement(texte, nomModele)
    animation.demarrer()
    try {
        return bloc()
    } finally {
        animation.arreter()
    }
}
