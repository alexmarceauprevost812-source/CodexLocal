package com.codexlocal.editor

import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.codexlocal.MainActivity
import com.codexlocal.R
import com.codexlocal.databinding.ActivityEditorBinding
import com.codexlocal.project.Langages
import com.codexlocal.project.ProjectStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CodeEditorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditorBinding

    private var uri: Uri? = null
    private var nomFichier: String = "fichier"
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

        uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        nomFichier = intent.getStringExtra(EXTRA_NOM) ?: "fichier"

        val langage = Langages.pour(nomFichier)
        binding.toolbar.title = nomFichier.substringAfterLast('/')
        binding.toolbar.subtitle = langage.nom
        binding.toolbar.setNavigationOnClickListener { finish() }

        binding.badge.text = langage.etiquette
        (binding.badge.background?.mutate() as? GradientDrawable)?.setTint(langage.couleur)

        binding.editeur.definirLangage(Langages.pourColoration(nomFichier))
        binding.btnSave.setOnClickListener { enregistrer() }
        binding.btnAsk.setOnClickListener { envoyerAlAssistant() }

        charger()
    }

    // ------------------------------------------------------------------ lecture

    private fun charger() {
        val cible = uri
        if (cible == null) {
            binding.status.text = "Aucun fichier"
            return
        }
        lifecycleScope.launch {
            binding.status.text = "Chargement…"
            try {
                val texte = withContext(Dispatchers.IO) {
                    ProjectStore.lireTexte(this@CodeEditorActivity, cible)
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

    // -------------------------------------------------------------- enregistrer

    private fun enregistrer() {
        val cible = uri ?: return
        val nouveau = binding.editeur.text?.toString() ?: return
        val diff = DiffStats.comparer(contenuInitial, nouveau)

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    ProjectStore.ecrireTexte(this@CodeEditorActivity, cible, nouveau)
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
        val d1 = sb.length
        sb.append("+${diff.ajoutees}")
        sb.setSpan(
            ForegroundColorSpan(0xFF7EE787.toInt()), d1, sb.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        sb.append("  ")
        val d2 = sb.length
        sb.append("−${diff.supprimees}")
        sb.setSpan(
            ForegroundColorSpan(0xFFFF6B6B.toInt()), d2, sb.length,
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
        val langage = Langages.pourColoration(nomFichier)
        val message = buildString {
            append("Voici le fichier ")
            append(nomFichier.substringAfterLast('/'))
            append(" :\n\n```")
            append(langage)
            append('\n')
            append(extrait)
            append("\n```\n\nExplique-le et propose des améliorations.")
        }
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_MESSAGE, message)
            }
        )
    }

    companion object {
        const val EXTRA_URI = "fichier_uri"
        const val EXTRA_NOM = "fichier_nom"
    }
}
