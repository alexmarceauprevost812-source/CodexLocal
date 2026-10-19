package com.codexlocal.editor

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.inputmethod.BaseInputConnection
import androidx.appcompat.widget.AppCompatEditText
import com.codexlocal.SyntaxHighlighter

/**
 * Editeur de code : police monospace, gouttiere de numeros de ligne dessinee
 * dans la marge (elle suit le defilement sans synchronisation de vues) et
 * coloration syntaxique appliquee apres une pause de frappe.
 */
class CodeEditText @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.editTextStyle
) : AppCompatEditText(context, attrs, defStyleAttr) {

    private val peintureNumeros = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.MONOSPACE
        color = 0xFF606060.toInt()
        textAlign = Paint.Align.RIGHT
    }

    private val peintureFond = Paint().apply { color = 0xFF070707.toInt() }

    private var largeurGouttiere = 0
    private var langage = ""
    private var numeros = IntArray(0)

    private val actionColoration = Runnable { colorer() }

    init {
        typeface = Typeface.MONOSPACE
        setHorizontallyScrolling(false)
        setLineSpacing(dp(2f).toFloat(), 1.12f)
        setPadding(dp(54f), dp(10f), dp(10f), dp(10f))
        addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) = Unit
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                planifierColoration()
                invalidate()
            }
        })
    }

    fun definirLangage(nouveau: String) {
        langage = nouveau
        planifierColoration()
    }

    fun recolorerMaintenant() {
        removeCallbacks(actionColoration)
        colorer()
    }

    /** On attend une pause de frappe avant de recolorer. */
    private fun planifierColoration() {
        removeCallbacks(actionColoration)
        postDelayed(actionColoration, 700)
    }

    private fun colorer() {
        val editable = text ?: return
        if (editable.isEmpty()) return
        // on ne touche pas au texte pendant que le clavier compose (accents, prediction)
        if (BaseInputConnection.getComposingSpanStart(editable) != -1) return

        // seules nos propres couleurs sont retirees : celles d'autres sources restent
        SyntaxHighlighter.effacer(editable)
        SyntaxHighlighter.colorize(editable, 0, editable.toString(), langage)
    }

    /**
     * Pour chaque ligne VISUELLE (apres retour a la ligne automatique), le
     * numero de ligne logique a afficher, ou -1 si c'est une suite de ligne.
     */
    private fun recalculerNumeros() {
        val mise = layout ?: return
        val texte = text ?: return
        val n = mise.lineCount
        val arr = IntArray(n)
        var logique = 1
        for (i in 0 until n) {
            val debut = mise.getLineStart(i)
            val debutDeLigne = i == 0 ||
                (debut in 1..texte.length && texte[debut - 1] == '\n')
            if (debutDeLigne) {
                arr[i] = logique
                logique++
            } else {
                arr[i] = -1
            }
        }
        numeros = arr
    }

    private fun majGouttiere(nbLignes: Int) {
        val chiffres = maxOf(3, nbLignes.toString().length)
        peintureNumeros.textSize = textSize * 0.8f
        val largeur = (peintureNumeros.measureText("0") * chiffres).toInt() + dp(16f)
        if (largeur != largeurGouttiere) {
            largeurGouttiere = largeur
            setPadding(largeurGouttiere + dp(8f), paddingTop, paddingRight, paddingBottom)
        }
    }

    override fun onDraw(canvas: Canvas) {
        val mise = layout
        val texte = text
        if (mise != null && texte != null && mise.lineCount > 0) {
            if (numeros.size != mise.lineCount) recalculerNumeros()
            majGouttiere(mise.lineCount)

            canvas.drawRect(
                0f, 0f,
                largeurGouttiere.toFloat() + dp(4f), height.toFloat(),
                peintureFond
            )

            val premiere = mise.getLineForVertical(scrollY)
            val derniere = mise.getLineForVertical(scrollY + height)
            val decalage = extendedPaddingTop - scrollY
            for (i in premiere..derniere) {
                if (i < 0 || i >= numeros.size) continue
                val numero = numeros[i]
                if (numero <= 0) continue
                val y = mise.getLineBaseline(i) + decalage
                canvas.drawText(
                    numero.toString(),
                    largeurGouttiere.toFloat(),
                    y.toFloat(),
                    peintureNumeros
                )
            }
        }
        super.onDraw(canvas)
    }

    private fun dp(valeur: Float): Int =
        (valeur * resources.displayMetrics.density).toInt()
}
