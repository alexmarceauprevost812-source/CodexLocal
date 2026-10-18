package com.codexlocal.project

import android.app.Activity
import android.content.DialogInterface
import android.widget.EditText
import com.codexlocal.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Fenêtres de saisie partagées entre l'écran principal et le Studio. */
object Dialogues {

    /**
     * Demande un texte. `valider` renvoie un message d'erreur (la fenêtre reste
     * ouverte) ou null si le texte est accepté ; `onOk` reçoit alors le texte.
     */
    fun saisir(
        activity: Activity,
        titre: String,
        message: String?,
        hint: String,
        initial: String = "",
        valider: (String) -> String? = { null },
        onOk: (String) -> Unit
    ) {
        val marge = (16 * activity.resources.displayMetrics.density).toInt()
        val champ = EditText(activity).apply {
            this.hint = hint
            setText(initial)
            setSelection(text.length)
            setSingleLine()
            setTextColor(0xFFECECEC.toInt())
            setHintTextColor(0xFF8C8C8C.toInt())
            setPadding(marge, marge, marge, marge)
        }
        val constructeur = MaterialAlertDialogBuilder(activity)
            .setTitle(titre)
            .setView(champ)
            .setPositiveButton(R.string.btn_creer, null)
            .setNegativeButton(R.string.btn_cancel, null)
        if (message != null) constructeur.setMessage(message)
        val fenetre = constructeur.create()
        fenetre.setOnShowListener {
            fenetre.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener {
                val texte = champ.text.toString().trim()
                val erreur = valider(texte)
                if (erreur != null) {
                    champ.error = erreur
                    return@setOnClickListener
                }
                fenetre.dismiss()
                onOk(texte)
            }
        }
        fenetre.show()
    }

    /** Erreur lisible si le chemin relatif n'est pas accepté par le projet, sinon null. */
    fun erreurChemin(chemin: String): String? =
        try {
            ProjectStore.validerCheminRelatif(chemin)
            null
        } catch (e: IllegalArgumentException) {
            e.message
        }
}
