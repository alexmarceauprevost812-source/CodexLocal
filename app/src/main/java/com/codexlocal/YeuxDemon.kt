package com.codexlocal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.random.Random

/**
 * Les deux yeux de démon orange, toujours vivants :
 *  - ils brillent en pulsant doucement,
 *  - ils clignent de temps en temps (et quand on les touche),
 *  - ils regardent à gauche et à droite,
 *  - quand l'IA travaille ([setPensee]), ils plissent et brillent plus fort.
 * Dessinés dans une boîte de 150 × 60, mise à l'échelle de la vue.
 */
class YeuxDemon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val couleur = ContextCompat.getColor(context, R.color.orange)

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = couleur
    }

    // Œil gauche : le haut descend vers le centre (sourcils froncés), pointe intérieure.
    private val oeilGauche = Path().apply {
        moveTo(4f, 10f)
        lineTo(68f, 30f)
        cubicTo(64f, 46f, 50f, 54f, 34f, 52f)
        cubicTo(16f, 50f, 6f, 36f, 4f, 10f)
        close()
    }

    // Œil droit : miroir de l'œil gauche.
    private val oeilDroit = Path().apply {
        moveTo(146f, 10f)
        lineTo(82f, 30f)
        cubicTo(86f, 46f, 100f, 54f, 116f, 52f)
        cubicTo(134f, 50f, 144f, 36f, 146f, 10f)
        close()
    }

    /** 1 = ouverts, 0 = fermés (clignement). */
    private var ouverture = 1f
    /** Plissement : 1 = normal, plus petit = yeux plissés (pensée). */
    private var plissement = 1f
    /** Intensité de la lueur. */
    private var lueur = 1f
    /** Décalage horizontal du regard, en unités de la boîte (−1 à 1). */
    private var regard = 0f

    private var pensee = false

    private var clignement: ValueAnimator? = null
    private var pulsation: ValueAnimator? = null
    private var animRegard: ValueAnimator? = null
    private var animPlissement: ValueAnimator? = null

    private val prochainClignement = Runnable { cligner(); planifierClignement() }
    private val prochainRegard = Runnable { regarderAilleurs(); planifierRegard() }

    init {
        // La lueur (ombre portée) demande un rendu logiciel.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        contentDescription = context.getString(R.string.app_name)
        setOnClickListener { cligner() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        demarrerPulsation()
        planifierClignement()
        planifierRegard()
    }

    override fun onDetachedFromWindow() {
        removeCallbacks(prochainClignement)
        removeCallbacks(prochainRegard)
        clignement?.cancel()
        pulsation?.cancel()
        animRegard?.cancel()
        animPlissement?.cancel()
        super.onDetachedFromWindow()
    }

    /** L'IA travaille : yeux plissés, lueur plus forte et plus rapide. */
    fun setPensee(actif: Boolean) {
        if (pensee == actif) return
        pensee = actif
        animPlissement?.cancel()
        animPlissement = ValueAnimator.ofFloat(plissement, if (actif) 0.62f else 1f).apply {
            duration = 260
            addUpdateListener { plissement = it.animatedValue as Float; invalidate() }
            start()
        }
        if (isAttachedToWindow) demarrerPulsation()
    }

    private fun demarrerPulsation() {
        pulsation?.cancel()
        val (min, duree) = if (pensee) 0.8f to 520L else 0.6f to 1800L
        pulsation = ValueAnimator.ofFloat(min, if (pensee) 1.5f else 1f).apply {
            duration = duree
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { lueur = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun planifierClignement() {
        removeCallbacks(prochainClignement)
        postDelayed(prochainClignement, 2200L + Random.nextLong(3800L))
    }

    private fun planifierRegard() {
        removeCallbacks(prochainRegard)
        postDelayed(prochainRegard, 1500L + Random.nextLong(3500L))
    }

    fun cligner() {
        clignement?.cancel()
        // Parfois un double clignement, pour plus de vie.
        val valeurs = if (Random.nextInt(5) == 0) floatArrayOf(1f, 0.06f, 1f, 0.06f, 1f)
        else floatArrayOf(1f, 0.06f, 1f)
        clignement = ValueAnimator.ofFloat(*valeurs).apply {
            duration = if (valeurs.size > 3) 420 else 210
            addUpdateListener { ouverture = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    private fun regarderAilleurs() {
        // Pendant la pensée, le regard reste fixé droit devant.
        val cible = if (pensee) 0f else when (Random.nextInt(4)) {
            0 -> -1f
            1 -> 1f
            else -> 0f
        }
        animRegard?.cancel()
        animRegard = ValueAnimator.ofFloat(regard, cible).apply {
            duration = 320
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { regard = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val densite = resources.displayMetrics.density
        val largeur = resolveSize((200 * densite).toInt(), widthMeasureSpec)
        val hauteur = resolveSize((80 * densite).toInt(), heightMeasureSpec)
        setMeasuredDimension(largeur, hauteur)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val marge = 0.16f
        val disponibleL = width * (1 - 2 * marge)
        val disponibleH = height * (1 - 2 * marge)
        val echelle = minOf(disponibleL / 150f, disponibleH / 60f)
        val dx = (width - 150f * echelle) / 2f
        val dy = (height - 60f * echelle) / 2f

        pinceau.setShadowLayer(16f * echelle * lueur + 3f, 0f, 0f, couleur)

        canvas.save()
        canvas.translate(dx + regard * 8f * echelle, dy)
        canvas.scale(echelle, echelle)
        // Clignement + plissement : on écrase verticalement autour du milieu des yeux.
        canvas.scale(1f, ouverture * plissement, 75f, 34f)
        canvas.drawPath(oeilGauche, pinceau)
        canvas.drawPath(oeilDroit, pinceau)
        canvas.restore()
    }
}
