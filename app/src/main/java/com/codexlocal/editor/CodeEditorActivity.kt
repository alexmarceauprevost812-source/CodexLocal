package com.codexlocal.editor

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.codexlocal.MainActivity
import com.codexlocal.R
import com.codexlocal.databinding.ActivityEditorBinding
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Langages
import com.codexlocal.project.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding
    private lateinit var fichier: FichierProjet
    private var contenuInitial: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityEditorBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val barres = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(barres.left, barres.top, barres.right, barres.bottom)
            insets
        }

        val chemin = intent.getStringExtra(EXTRA_CHEMIN) ?: ""
        val nom = intent.getStringExtra(EXTRA_NOM) ?: chemin.substringAfterLast('/')
        val interne = intent.getBooleanExtra(EXTRA_INTERNE, true)
        fichier = FichierProjet(nom.substringAfterLast('/'), nom, chemin, 0L, interne)

        val langage = Langages.pour(fichier.nom)
        binding.toolbar.title = fichier.nom
        binding.toolbar.subtitle = langage.nom
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.badge.text = langage.etiquette
        (binding.badge.background?.mutate() as? GradientDrawable)?.setTint(langage.couleur)

        binding.editeur.definirLangage(Langages.pourColoration(fichier.nom))
        binding.btnSave.setOnClickListener { enregistrer() }
        binding.btnAsk.setOnClickListener { envoyerAlAssistant() }

        charger()
    }

    // ----------------------------------------------------------------- lecture

    private fun charger() {
        lifecycleScope.launch {
            binding.status.text = "Chargement…"
            try {
                val texte = withContext(Dispatchers.IO) {
                    ProjectStore.lireTexte(this@CodeEditorActivity, fichier)
                }
                contenuInitial = texte
                binding.editeur.setText(texte)
                binding.editeur.recolorerMaintenant()
                binding.status.text = getString(R.string.editor_lignes, texte.lines().size)
            } catch (e: Exception) {
                binding.status.text = getString(R.string.editor_error, e.message ?: "")
            }
        }
    }

    // ------------------------------------------------------------- enregistrer

    private fun enregistrer() {
        val nouveau = binding.editeur.text?.toString() ?: return
        val diff = DiffStats.comparer(contenuInitial, nouveau)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ProjectStore.ecrireTexte(this@CodeEditorActivity, fichier, nouveau)
                }
                contenuInitial = nouveau
                binding.editeur.recolorerMaintenant()
                binding.status.text =
                    getString(R.string.editor_lignes, nouveau.lines().size) + "  —  enregistré"
                afficherDiff(diff)
                Toast.makeText(
                    this@CodeEditorActivity,
                    "Enregistré  ${diff.resume()}",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(
                    this@CodeEditorActivity,
                    getString(R.string.editor_error, e.message ?: ""),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** +12 en vert, −3 en rouge. */
    private fun afficherDiff(diff: DiffStats.Resultat) {
        val sb = SpannableStringBuilder()
        val debut1 = sb.length
        sb.append("+${diff.ajoutees}")
        sb.setSpan(
            ForegroundColorSpan(0xFF7EE787.toInt()), debut1, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.append("  ")
        val debut2 = sb.length
        sb.append("−${diff.supprimees}")
        sb.setSpan(
            ForegroundColorSpan(0xFFFF6B6B.toInt()), debut2, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        binding.diff.text = sb
    }

    // ---------------------------------------------------------------------- IA

    private fun envoyerAlAssistant() {
        val contenu = binding.editeur.text?.toString() ?: return
        val extrait = if (contenu.length > 12_000) {
            contenu.take(12_000) + "\n… (tronqué)"
        } else {
            contenu
        }
        val message = buildString {
            append("Voici le fichier ")
            append(fichier.nom)
            append(" :\n\n```")
            append(Langages.pourColoration(fichier.nom))
            append('\n')
            append(extrait)
            append("\n```\n\nTu es MARCEAU Codex, assistant de programmation. ")
            append("Analyse ce fichier, identifie les problèmes et propose une version corrigée COMPLÈTE ")
            append("dans un seul bloc de code avec le langage approprié. ")
            append("Ne prétends pas avoir modifié le projet : le résultat doit être vérifié ")
            append("et enregistré manuellement par l'utilisateur. ")
            append("Conserve les fonctions existantes sauf si leur modification est nécessaire.")
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_MESSAGE, message)
            }
        )
    }

    companion object {
        const val EXTRA_CHEMIN = "fichier_chemin"
        const val EXTRA_NOM = "fichier_nom"
        const val EXTRA_INTERNE = "fichier_interne"
    }
}
