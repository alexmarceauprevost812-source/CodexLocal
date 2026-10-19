package com.codexlocal

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

/**
 * « Partager mes IA » : met les modeles .gguf dans un dossier public commun
 * pour que LexOS Terminal (ou une autre appli) puisse les reutiliser sans les
 * retelecharger.
 *
 * Interface construite en code pour rester isolee du reste de l'appli.
 */
class PartageActivity : AppCompatActivity() {

    private val orange = 0xFFFF7A00.toInt()
    private val noir = 0xFF0A0A0A.toInt()
    private val gris = 0xFFBFC7D5.toInt()

    private lateinit var statut: TextView
    private lateinit var bouton: Button
    private lateinit var chemin: TextView

    /** Retour de l'ecran systeme « Acces a tous les fichiers ». */
    private val demandePermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (PartageModeles.peutPartager()) {
                activerEtDeplacer()
            } else {
                Toast.makeText(
                    this,
                    "Permission refusee. Le partage a besoin de « Acces a tous les fichiers ».",
                    Toast.LENGTH_LONG
                ).show()
                rafraichir()
            }
        }

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Partager mes IA"

        val racine = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(noir)
            val p = dp(20)
            setPadding(p, p, p, p)
        }

        racine.addView(TextView(this).apply {
            text = "🔗 Partager mes IA"
            setTextColor(orange)
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
        })

        racine.addView(TextView(this).apply {
            text = "Range tes modeles d'IA dans un dossier commun pour que " +
                "LexOS Terminal puisse les utiliser sans les retelecharger."
            setTextColor(gris)
            textSize = 15f
            val m = dp(10)
            setPadding(0, m, 0, m)
        })

        statut = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(0, dp(6), 0, dp(6))
        }
        racine.addView(statut)

        bouton = Button(this).apply {
            setTextColor(Color.WHITE)
            setBackgroundColor(orange)
            textSize = 16f
            setOnClickListener { surClic() }
        }
        racine.addView(bouton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        racine.addView(TextView(this).apply {
            text = "Dans LexOS, les modeles partages apparaissent ici :"
            setTextColor(gris)
            textSize = 13f
            setPadding(0, dp(22), 0, dp(4))
        })

        chemin = TextView(this).apply {
            text = PartageModeles.CHEMIN_TERMUX
            setTextColor(orange)
            textSize = 15f
            setTypeface(Typeface.MONOSPACE)
            setTextIsSelectable(true)
        }
        racine.addView(chemin)

        setContentView(ScrollView(this).apply {
            setBackgroundColor(noir)
            addView(racine)
        })

        rafraichir()
    }

    override fun onResume() {
        super.onResume()
        rafraichir()
    }

    @SuppressLint("SetTextI18n")
    private fun rafraichir() {
        if (PartageModeles.actif(this)) {
            val n = ModelStore.list(this).size
            statut.text = "✅ Partage actif. $n modele(s) dans le dossier commun."
            statut.setTextColor(0xFF76B900.toInt())
            bouton.text = "Re-synchroniser mes modeles"
            chemin.visibility = View.VISIBLE
        } else {
            statut.text = "Le partage n'est pas encore active."
            statut.setTextColor(gris)
            bouton.text = "Activer le partage"
            chemin.visibility = View.GONE
        }
    }

    private fun surClic() {
        if (PartageModeles.peutPartager()) {
            activerEtDeplacer()
        } else {
            // Ouvre l'ecran systeme pour accorder « Acces a tous les fichiers ».
            val intent = Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:$packageName")
            )
            runCatching { demandePermission.launch(intent) }.onFailure {
                // Repli : l'ecran general de la permission.
                demandePermission.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun activerEtDeplacer() {
        PartageModeles.definirDemande(this, true)
        bouton.isEnabled = false
        statut.text = "Partage en cours…"
        statut.setTextColor(Color.WHITE)
        Thread {
            val res = runCatching { PartageModeles.deplacerTout(this) }
            runOnUiThread {
                bouton.isEnabled = true
                res.onSuccess {
                    Toast.makeText(
                        this,
                        if (it.deplaces > 0) "${it.deplaces} modele(s) partage(s) !"
                        else "Partage active.",
                        Toast.LENGTH_LONG
                    ).show()
                }.onFailure {
                    Toast.makeText(this, "Erreur : ${it.message}", Toast.LENGTH_LONG).show()
                }
                rafraichir()
            }
        }.start()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
