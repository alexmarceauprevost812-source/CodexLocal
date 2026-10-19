package com.codexlocal.project

import android.app.Activity
import android.content.DialogInterface
import android.widget.EditText
import android.widget.ListView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.codexlocal.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    /** Une question « Supprimer ? » : `onOk` n'est appele que si on confirme. */
    fun confirmer(
        activity: Activity,
        titre: String,
        message: String,
        libellePositif: String,
        onOk: () -> Unit
    ) {
        MaterialAlertDialogBuilder(activity)
            .setTitle(titre)
            .setMessage(message)
            .setPositiveButton(libellePositif) { _, _ -> onOk() }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    /**
     * Une liste a cocher. Le bouton d'action reste grise tant que rien n'est coche ; « Tout
     * cocher » / « Tout decocher » coche ou decoche toute la liste. `onChoisis` recoit les
     * positions cochees, dans l'ordre de la liste.
     */
    fun choisirPlusieurs(
        activity: Activity,
        titre: String,
        libelles: List<String>,
        libelleAction: String,
        onChoisis: (List<Int>) -> Unit
    ) {
        val fenetre = MaterialAlertDialogBuilder(activity)
            .setTitle(titre)
            .setMultiChoiceItems(libelles.toTypedArray(), BooleanArray(libelles.size), null)
            .setPositiveButton(libelleAction, null)
            .setNeutralButton(R.string.suppr_cocher_tout, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
        fenetre.setOnShowListener {
            val liste: ListView = fenetre.listView
            val action = fenetre.getButton(DialogInterface.BUTTON_POSITIVE)
            val tout = fenetre.getButton(DialogInterface.BUTTON_NEUTRAL)

            fun cochees(): List<Int> = (0 until libelles.size).filter { liste.isItemChecked(it) }
            fun majBoutons() {
                val n = cochees().size
                action.isEnabled = n > 0
                tout.setText(if (n == libelles.size) R.string.suppr_decocher_tout else R.string.suppr_cocher_tout)
            }

            majBoutons()
            liste.setOnItemClickListener { _, _, _, _ -> majBoutons() }
            tout.setOnClickListener {
                val toutCocher = cochees().size != libelles.size
                for (i in libelles.indices) liste.setItemChecked(i, toutCocher)
                majBoutons()
            }
            action.setOnClickListener {
                val choisies = cochees()
                if (choisies.isEmpty()) return@setOnClickListener
                fenetre.dismiss()
                onChoisis(choisies)
            }
        }
        fenetre.show()
    }

    /**
     * Confirme la suppression d'un projet : un projet interne est efface avec tous ses fichiers,
     * un dossier du telephone est seulement retire de la liste. `avant` est appele juste avant
     * l'effacement (pour arreter ce qui travaille dans le projet) ; l'effacement se fait en
     * arriere-plan, car un projet peut contenir des milliers de fichiers ; `onFait` est ensuite
     * appele sur l'ecran. En cas d'echec un message l'indique.
     */
    fun confirmerSuppressionProjet(
        activity: AppCompatActivity,
        projet: Projet,
        avant: () -> Unit = {},
        onFait: () -> Unit
    ) {
        val interne = projet.interne
        confirmer(
            activity,
            activity.getString(
                if (interne) R.string.suppr_projet_titre else R.string.suppr_projet_retirer_titre,
                projet.nom
            ),
            activity.getString(if (interne) R.string.suppr_projet_msg else R.string.suppr_projet_retirer_msg),
            activity.getString(if (interne) R.string.btn_supprimer else R.string.btn_retirer)
        ) {
            avant()
            activity.lifecycleScope.launch {
                val ok = withContext(Dispatchers.IO) { ProjectStore.supprimerProjet(activity, projet) }
                if (ok) {
                    android.widget.Toast.makeText(
                        activity,
                        if (interne) R.string.msg_projet_efface else R.string.msg_projet_supprime,
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                    onFait()
                } else {
                    android.widget.Toast.makeText(
                        activity, activity.getString(R.string.msg_suppression_echec, projet.nom),
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
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
