package com.codexlocal.chat

import android.os.SystemClock
import android.text.Spannable
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import android.widget.TextView

/**
 * Écriture « projection » (APK perso) : chaque nouveau morceau de texte apparaît en jaune or
 * lumineux, comme projeté, puis s'éteint doucement en blanc une fois posé.
 */
class ProjectionSpan(private val debut: Long) : CharacterStyle(), UpdateAppearance {

    /** 0 = vient d'arriver, 1 = posé (blanc normal). */
    fun progres(): Float = ((SystemClock.uptimeMillis() - debut) / DUREE.toFloat()).coerceIn(0f, 1f)

    override fun updateDrawState(tp: TextPaint) {
        val p = progres()
        if (p >= 1f) return
        // Entrée rapide (fondu), lueur or, puis retour au blanc.
        val apparition = (p / 0.15f).coerceAtMost(1f)
        val t = ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
        val r = lerp(0xFF, 0xEC, t); val g = lerp(0xC8, 0xEC, t); val b = lerp(0x2E, 0xEC, t)
        val alpha = (90 + 165 * apparition).toInt()
        tp.color = (alpha shl 24) or (r shl 16) or (g shl 8) or b
        val lueur = (1f - t) * 14f + 2f
        tp.setShadowLayer(lueur, 0f, 0f, ((200 * (1f - t)).toInt() shl 24) or 0xFFB300)
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()

    companion object {
        const val DUREE = 1100L
    }
}

/** Suit le texte qui arrive pour un message en cours d'écriture. */
class ProjectionTexte {
    private var cle: Any? = null
    private var longueur = 0
    private val morceaux = ArrayList<Pair<Int, Long>>()

    fun reinitialiser() { cle = null; longueur = 0; morceaux.clear() }

    /** Ajoute les lueurs au texte formaté [texte] du message [message]. */
    fun appliquer(message: Any, texte: CharSequence) {
        val maintenant = SystemClock.uptimeMillis()
        if (cle !== message) { reinitialiser(); cle = message; longueur = 0 }
        if (texte.length > longueur) { morceaux += longueur to maintenant; longueur = texte.length }
        else if (texte.length < longueur) longueur = texte.length
        morceaux.removeAll { maintenant - it.second > ProjectionSpan.DUREE }
        val s = texte as? Spannable ?: return
        for ((i, m) in morceaux.withIndex()) {
            val fin = morceaux.getOrNull(i + 1)?.first ?: s.length
            val debut = m.first.coerceAtMost(s.length)
            if (fin > debut) s.setSpan(ProjectionSpan(m.second), debut, fin.coerceAtMost(s.length), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    private val enMarche = java.util.WeakHashMap<TextView, Boolean>()

    /** Redessine la bulle à chaque image tant qu'une lueur est encore visible (une seule boucle par bulle). */
    fun animer(vue: TextView) {
        if (enMarche[vue] == true) return
        enMarche[vue] = true
        val boucle = object : Runnable {
            override fun run() {
                val s = vue.text as? Spannable
                val actifs = s != null && s.getSpans(0, s.length, ProjectionSpan::class.java).any { it.progres() < 1f }
                vue.invalidate()
                if (actifs && vue.isAttachedToWindow) vue.postOnAnimation(this) else enMarche.remove(vue)
            }
        }
        vue.postOnAnimation(boucle)
    }
}
