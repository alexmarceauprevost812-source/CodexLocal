package com.codexlocal

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import kotlin.random.Random

/**
 * Les deux yeux de démon du thème Halloween.
 *  - Couleur au choix (Réglages › Les yeux).
 *  - « Toujours animés » : lueur pulsée, clignements, regard qui bouge.
 *    Sinon : immobiles, ils s'animent seulement quand on les touche.
 *  - Toucher : réaction (éclat, double clignement, yeux plissés).
 *  - Quand l'IA travaille ([setPensee]) : plissés et plus brillants.
 * Dessinés dans une boîte de 150 × 60, mise à l'échelle de la vue.
 */
class YeuxDemon @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    data class Couleur(val cle: String, val nom: String, val couleur: Int)

    companion object {
        const val COULEUR_DEFAUT = "orange"

        val COULEURS = listOf(
            Couleur("orange", "Orange", 0xFFFF7A00.toInt()),
            Couleur("bleu_fonce", "Bleu foncé", 0xFF2F55FF.toInt()),
            Couleur("bleu_ciel", "Bleu ciel", 0xFF38BDF8.toInt()),
            Couleur("rose", "Rose", 0xFFFF4FB4.toInt()),
            Couleur("violet", "Violet", 0xFFA855F7.toInt()),
            Couleur("rouge", "Rouge", 0xFFFF2A2A.toInt()),
            Couleur("jaune", "Jaune", 0xFFFFE14D.toInt()),
            Couleur("or", "Or", 0xFFE8B84A.toInt()),
            Couleur("vert", "Vert néon", 0xFF00E676.toInt()),
            Couleur("blanc", "Blanc", 0xFFF5F5F5.toInt()),
            Couleur("cyan", "Cyan", 0xFF00E5FF.toInt()),
            Couleur(ARC_EN_CIEL, "🌈 Arc-en-ciel (6 couleurs)", 0xFFFF7A00.toInt()),
        )

        const val ARC_EN_CIEL = "arcenciel"

        /** Les 6 couleurs qui défilent en mode arc-en-ciel. */
        private val ARC = intArrayOf(
            0xFFFF2A2A.toInt(), 0xFFFF7A00.toInt(), 0xFFFFE14D.toInt(),
            0xFF00E676.toInt(), 0xFF38BDF8.toInt(), 0xFFA855F7.toInt(),
        )

        /** Modèles de visage : clé → nom affiché. */
        val MODELES = listOf(
            "mechant" to "😈 Méchant",
            "gentil" to "😊 Gentil",
            "lunettes" to "😎 Lunettes turbo cool",
        )

        fun couleurDe(cle: String): Int =
            (COULEURS.firstOrNull { it.cle == cle } ?: COULEURS.first()).couleur
    }

    private var couleur = couleurDe(COULEUR_DEFAUT)
    private var arcEnCiel = false
    private var modele = "mechant"
    private var toujoursAnimes = true
    private val pinceauReflet = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = 0xF0FFFFFF.toInt() }
    private val pinceauVerre = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pinceauCadre = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f; strokeJoin = Paint.Join.ROUND }

    // Yeux gentils : grands ovales doux.
    private val gentilGauche = Path().apply { addOval(android.graphics.RectF(14f, 8f, 62f, 56f), Path.Direction.CW) }
    private val gentilDroit = Path().apply { addOval(android.graphics.RectF(88f, 8f, 136f, 56f), Path.Direction.CW) }

    // Lunettes turbo cool : deux verres en trapèze arrondi + pont.
    private val verreGauche = Path().apply {
        moveTo(2f, 14f); lineTo(68f, 14f); cubicTo(70f, 34f, 62f, 54f, 40f, 54f)
        cubicTo(16f, 54f, 4f, 40f, 2f, 14f); close()
    }
    private val verreDroit = Path().apply {
        moveTo(148f, 14f); lineTo(82f, 14f); cubicTo(80f, 34f, 88f, 54f, 110f, 54f)
        cubicTo(134f, 54f, 146f, 40f, 148f, 14f); close()
    }

    /** Couleur actuelle (qui défile en mode arc-en-ciel). */
    private fun couleurActuelle(): Int {
        if (!arcEnCiel) return couleur
        val t = (android.os.SystemClock.uptimeMillis() % 9000L) / 9000f * ARC.size
        val i = t.toInt() % ARC.size
        return android.animation.ArgbEvaluator().evaluate(t - t.toInt(), ARC[i], ARC[(i + 1) % ARC.size]) as Int
    }

    private val pinceau = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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
    /** Plissement : 1 = normal, plus petit = plissés. */
    private var plissement = 1f
    /** Éclat ajouté à la lueur lors d'une réaction au toucher. */
    private var eclat = 0f
    private var lueur = 1f
    /** Décalage horizontal du regard (−1 à 1). */
    private var regard = 0f

    private var pensee = false

    private var clignement: ValueAnimator? = null
    private var pulsation: ValueAnimator? = null
    private var animRegard: ValueAnimator? = null
    private var animPlissement: ValueAnimator? = null
    private var animEclat: ValueAnimator? = null

    private val prochainClignement = Runnable { cligner(); planifierClignement() }
    private val prochainRegard = Runnable { regarderAilleurs(); planifierRegard() }

    init {
        // La lueur (ombre portée) demande un rendu logiciel.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        isClickable = true
        contentDescription = context.getString(R.string.app_name)
        setOnClickListener { reagir() }
        lireReglages()
    }

    private fun lireReglages() {
        val prefs = Prefs(context)
        couleur = couleurDe(prefs.yeuxCouleur)
        arcEnCiel = prefs.yeuxCouleur == ARC_EN_CIEL
        modele = prefs.yeuxModele
        toujoursAnimes = prefs.yeuxToujoursAnimes
    }

    /** Relit la couleur et le mode d'animation choisis dans les Réglages. */
    fun appliquerReglages() {
        lireReglages()
        if (isAttachedToWindow) demarrerVie()
        invalidate()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        demarrerVie()
    }

    override fun onDetachedFromWindow() {
        arreterVie()
        clignement?.cancel()
        animPlissement?.cancel()
        animEclat?.cancel()
        super.onDetachedFromWindow()
    }

    /** Vrai quand la vue et tous ses parents sont visibles à l'écran. */
    private var visibleAEcran = false

    // Cachés (accueil remplacé par la conversation, écran en arrière-plan) :
    // on coupe toutes les animations pour épargner la batterie et l'inférence.
    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        visibleAEcran = isVisible
        if (isVisible) demarrerVie() else arreterVie()
    }

    private fun demarrerVie() {
        arreterVie()
        if (!visibleAEcran) return
        if (toujoursAnimes || pensee) {
            demarrerPulsation()
        } else {
            lueur = 0.8f
        }
        if (toujoursAnimes) {
            planifierClignement()
            planifierRegard()
        } else if (regard != 0f) {
            regarderVers(0f)
        }
    }

    private fun arreterVie() {
        removeCallbacks(prochainClignement)
        removeCallbacks(prochainRegard)
        pulsation?.cancel()
        animRegard?.cancel()
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
        if (isAttachedToWindow) demarrerVie()
    }

    /** Réaction au toucher : éclat de lueur, double clignement, coup d'œil. */
    fun reagir() {
        animEclat?.cancel()
        animEclat = ValueAnimator.ofFloat(0f, 1.6f, 0f).apply {
            duration = 650
            addUpdateListener { eclat = it.animatedValue as Float; invalidate() }
            start()
        }
        cligner(double = true)
        regarderVers(if (Random.nextBoolean()) -1f else 1f)
        postDelayed({ regarderVers(0f) }, 500)
    }

    private fun demarrerPulsation() {
        pulsation?.cancel()
        val (min, max, duree) = if (pensee) Triple(0.8f, 1.5f, 520L) else Triple(0.6f, 1f, 1800L)
        pulsation = ValueAnimator.ofFloat(min, max).apply {
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

    fun cligner(double: Boolean = Random.nextInt(5) == 0) {
        clignement?.cancel()
        val valeurs = if (double) floatArrayOf(1f, 0.06f, 1f, 0.06f, 1f) else floatArrayOf(1f, 0.06f, 1f)
        clignement = ValueAnimator.ofFloat(*valeurs).apply {
            duration = if (double) 420 else 210
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
        regarderVers(cible)
    }

    private fun regarderVers(cible: Float) {
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

        val teinte = couleurActuelle()
        pinceau.color = teinte
        pinceau.setShadowLayer(16f * echelle * (lueur + eclat) + 3f, 0f, 0f, teinte)

        canvas.save()
        canvas.translate(dx + regard * 8f * echelle, dy)
        canvas.scale(echelle, echelle)
        if (modele == "lunettes") {
            dessinerLunettes(canvas, teinte, echelle)
            canvas.restore()
            if (arcEnCiel || modele == "lunettes") postInvalidateOnAnimation()
            return
        }
        // Clignement + plissement (+ léger plissement pendant l'éclat) autour du milieu des yeux.
        val hauteurOeil = ouverture * plissement * (1f - 0.15f * eclat.coerceAtMost(1f))
        canvas.scale(1f, hauteurOeil, 75f, 34f)
        if (modele == "gentil") {
            canvas.drawPath(gentilGauche, pinceau)
            canvas.drawPath(gentilDroit, pinceau)
            // Reflets brillants : un regard doux et vivant.
            canvas.drawCircle(48f, 22f, 7f, pinceauReflet)
            canvas.drawCircle(122f, 22f, 7f, pinceauReflet)
            canvas.drawCircle(30f, 42f, 3.2f, pinceauReflet)
            canvas.drawCircle(104f, 42f, 3.2f, pinceauReflet)
        } else {
            canvas.drawPath(oeilGauche, pinceau)
            canvas.drawPath(oeilDroit, pinceau)
        }
        canvas.restore()
        if (arcEnCiel) postInvalidateOnAnimation()
    }

    /** Lunettes de soleil : verres sombres, monture lumineuse, reflet qui balaie les verres. */
    private fun dessinerLunettes(canvas: Canvas, teinte: Int, echelle: Float) {
        pinceauVerre.shader = android.graphics.LinearGradient(
            0f, 14f, 0f, 54f, 0xFF1A1A1A.toInt(), 0xFF050505.toInt(), android.graphics.Shader.TileMode.CLAMP
        )
        canvas.drawPath(verreGauche, pinceauVerre)
        canvas.drawPath(verreDroit, pinceauVerre)
        pinceauCadre.color = teinte
        pinceauCadre.setShadowLayer(12f * echelle * (lueur + eclat) / echelle + 3f, 0f, 0f, teinte)
        canvas.drawPath(verreGauche, pinceauCadre)
        canvas.drawPath(verreDroit, pinceauCadre)
        canvas.drawLine(68f, 20f, 82f, 20f, pinceauCadre)
        // Reflet « turbo » : une bande claire traverse les verres, plus vite quand l'IA travaille.
        val cycle = if (pensee) 1300L else 2600L
        val p = (android.os.SystemClock.uptimeMillis() % cycle) / cycle.toFloat()
        val x = -30f + p * 220f
        canvas.save()
        val masque = Path().apply { addPath(verreGauche); addPath(verreDroit) }
        canvas.clipPath(masque)
        val reflet = Path().apply { moveTo(x, 10f); lineTo(x + 14f, 10f); lineTo(x - 6f, 58f); lineTo(x - 20f, 58f); close() }
        canvas.drawPath(reflet, pinceauReflet.apply { alpha = 120 })
        canvas.drawPath(Path().apply { moveTo(x + 20f, 10f); lineTo(x + 25f, 10f); lineTo(x + 5f, 58f); lineTo(x, 58f); close() }, pinceauReflet)
        pinceauReflet.alpha = 240
        canvas.restore()
        // Petite lueur de couleur au bas des verres.
        pinceau.alpha = 90
        canvas.drawOval(20f, 44f, 56f, 52f, pinceau)
        canvas.drawOval(94f, 44f, 130f, 52f, pinceau)
        pinceau.alpha = 255
    }
}
