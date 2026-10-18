package com.codexlocal.studio

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.codexlocal.Prefs
import com.codexlocal.R
import com.codexlocal.databinding.ActivityStudioBinding
import com.codexlocal.editor.CodeEditorActivity
import com.codexlocal.project.Dialogues
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Studio : un seul endroit pour gérer le projet (dossiers, fichiers), faire créer
 * des fichiers par l'IA et générer des images SVG enregistrées dans le projet.
 */
class StudioActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStudioBinding
    private lateinit var prefs: Prefs

    private var projet: Projet? = null
    private var dossiers: List<String> = emptyList()
    private var fichiers: List<FichierProjet> = emptyList()
    private val lignes = ArrayList<Ligne>()
    private lateinit var adapterEspace: ArrayAdapter<String>

    private var jobCode: Job? = null
    private var jobCrea: Job? = null
    private var propositions: List<FichierPropose> = emptyList()
    private var svgCourant: String? = null

    private data class Ligne(val texte: String, val fichier: FichierProjet?)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityStudioBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val barres = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(barres.left, barres.top, barres.right, barres.bottom)
            insets
        }
        prefs = Prefs(this)

        binding.toolbar.setNavigationOnClickListener { finish() }

        adapterEspace = ArrayAdapter(this, R.layout.item_studio_ligne, ArrayList<String>())
        binding.listeEspace.adapter = adapterEspace
        binding.listeEspace.setOnItemClickListener { _, _, position, _ ->
            lignes.getOrNull(position)?.fichier?.let { ouvrirFichier(it) }
        }

        binding.apercu.setBackgroundColor(Color.BLACK)
        binding.apercu.settings.apply {
            javaScriptEnabled = false
            blockNetworkLoads = true
            allowFileAccess = false
            allowContentAccess = false
        }

        binding.modes.addOnButtonCheckedListener { _, id, coche ->
            if (coche) afficherPanneau(id)
        }
        binding.modes.check(R.id.modeEspace)

        binding.btnChanger.setOnClickListener { changerProjet() }
        binding.btnProjet.setOnClickListener { nouveauProjet() }
        binding.btnDossier.setOnClickListener { nouveauDossier() }
        binding.btnFichier.setOnClickListener { nouveauFichier() }
        binding.btnGenererCode.setOnClickListener { lancerCode() }
        binding.btnCreerFichiers.setOnClickListener { creerFichiersProposes() }
        binding.btnGenererCrea.setOnClickListener { lancerCrea() }
        binding.btnEnregistrerSvg.setOnClickListener { enregistrerSvg() }

        val tous = ProjectStore.lister(this)
        val demande = intent.getStringExtra(EXTRA_PROJET_URI)
        val memorise = getSharedPreferences(PREFS_STUDIO, MODE_PRIVATE).getString(CLE_PROJET, null)
        projet = tous.firstOrNull { it.uri == demande }
            ?: tous.firstOrNull { it.uri == memorise }
            ?: tous.firstOrNull()
        rafraichir()
    }

    override fun onDestroy() {
        jobCode?.cancel()
        jobCrea?.cancel()
        super.onDestroy()
    }

    private fun afficherPanneau(id: Int) {
        binding.panneauEspace.isVisible = id == R.id.modeEspace
        binding.panneauCode.isVisible = id == R.id.modeCode
        binding.panneauCrea.isVisible = id == R.id.modeCrea
    }

    // ------------------------------------------------------------------ projet

    private fun choisir(p: Projet) {
        projet = p
        getSharedPreferences(PREFS_STUDIO, MODE_PRIVATE).edit().putString(CLE_PROJET, p.uri).apply()
        propositions = emptyList()
        binding.btnCreerFichiers.isEnabled = false
        rafraichir()
    }

    private fun rafraichir() {
        val p = projet
        binding.projetActuel.text = if (p == null) getString(R.string.studio_aucun_projet)
        else getString(R.string.studio_projet_actuel, p.nom)
        if (p == null) {
            dossiers = emptyList()
            fichiers = emptyList()
            reconstruireListe()
            return
        }
        lifecycleScope.launch {
            val charge = withContext(Dispatchers.IO) {
                runCatching {
                    Pair(
                        ProjectStore.listerDossiers(this@StudioActivity, p),
                        ProjectStore.listerFichiers(this@StudioActivity, p)
                    )
                }.getOrNull()
            }
            if (projet?.uri != p.uri) return@launch
            dossiers = charge?.first ?: emptyList()
            fichiers = charge?.second ?: emptyList()
            reconstruireListe()
        }
    }

    private fun reconstruireListe() {
        lignes.clear()
        dossiers.forEach { lignes.add(Ligne("📁 $it/", null)) }
        fichiers.forEach { lignes.add(Ligne("📄 ${it.chemin}", it)) }
        if (lignes.isEmpty() && projet != null) {
            lignes.add(Ligne(getString(R.string.studio_espace_vide), null))
        }
        adapterEspace.clear()
        adapterEspace.addAll(lignes.map { it.texte })
        adapterEspace.notifyDataSetChanged()
    }

    private fun changerProjet() {
        val tous = ProjectStore.lister(this)
        if (tous.isEmpty()) {
            Toast.makeText(this, R.string.studio_aucun_projet, Toast.LENGTH_SHORT).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.studio_choisir_projet)
            .setItems(tous.map { it.nom }.toTypedArray()) { _, i -> choisir(tous[i]) }
            .show()
    }

    private fun nouveauProjet() {
        Dialogues.saisir(
            activity = this,
            titre = getString(R.string.drawer_nouveau_projet),
            message = getString(R.string.msg_copie_usb, ProjectStore.dossierInterne(this).absolutePath),
            hint = getString(R.string.dialog_nom_projet),
            valider = { if (it.isBlank()) getString(R.string.dialog_nom_projet) else null }
        ) { nom ->
            val cree = ProjectStore.creerProjetInterne(this, nom)
            ProjectStore.ajouter(this, cree)
            choisir(cree)
            Toast.makeText(this, getString(R.string.msg_projet_cree, cree.nom), Toast.LENGTH_SHORT).show()
        }
    }

    private fun projetOuMessage(): Projet? {
        val p = projet
        if (p == null) Toast.makeText(this, R.string.studio_besoin_projet, Toast.LENGTH_SHORT).show()
        return p
    }

    private fun nouveauDossier() {
        val p = projetOuMessage() ?: return
        Dialogues.saisir(
            activity = this,
            titre = getString(R.string.drawer_nouveau_dossier),
            message = getString(R.string.msg_creer_dossier),
            hint = getString(R.string.hint_nom_dossier),
            valider = { Dialogues.erreurChemin(it) }
        ) { chemin ->
            lifecycleScope.launch {
                try {
                    val cree = withContext(Dispatchers.IO) {
                        ProjectStore.creerDossier(this@StudioActivity, p, chemin)
                    }
                    Toast.makeText(
                        this@StudioActivity,
                        getString(R.string.msg_dossier_cree, cree),
                        Toast.LENGTH_SHORT
                    ).show()
                    rafraichir()
                } catch (e: Exception) {
                    erreur(R.string.msg_echec_dossier, e)
                }
            }
        }
    }

    private fun nouveauFichier() {
        val p = projetOuMessage() ?: return
        Dialogues.saisir(
            activity = this,
            titre = getString(R.string.drawer_nouveau_fichier),
            message = getString(R.string.msg_creer_fichier),
            hint = getString(R.string.hint_nom_fichier),
            initial = "main.py",
            valider = { Dialogues.erreurChemin(it) }
        ) { chemin ->
            lifecycleScope.launch {
                try {
                    val cree = withContext(Dispatchers.IO) {
                        ProjectStore.creerFichier(this@StudioActivity, p, chemin, "")
                    }
                    Toast.makeText(
                        this@StudioActivity,
                        getString(R.string.msg_fichier_cree, cree.chemin),
                        Toast.LENGTH_SHORT
                    ).show()
                    rafraichir()
                    ouvrirFichier(cree)
                } catch (e: Exception) {
                    erreur(R.string.msg_echec_creation, e)
                }
            }
        }
    }

    private fun ouvrirFichier(f: FichierProjet) {
        startActivity(
            Intent(this, CodeEditorActivity::class.java)
                .putExtra(CodeEditorActivity.EXTRA_CHEMIN, f.uri)
                .putExtra(CodeEditorActivity.EXTRA_NOM, f.chemin)
                .putExtra(CodeEditorActivity.EXTRA_INTERNE, f.interne)
        )
    }

    private fun erreur(titre: Int, e: Exception) {
        MaterialAlertDialogBuilder(this)
            .setTitle(titre)
            .setMessage(e.message ?: e.toString())
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    // ----------------------------------------------------------------- code IA

    private fun lancerCode() {
        if (jobCode?.isActive == true) {
            jobCode?.cancel()
            return
        }
        val p = projetOuMessage() ?: return
        val demande = binding.demandeCode.text.toString().trim()
        if (demande.isEmpty()) return

        propositions = emptyList()
        binding.btnCreerFichiers.isEnabled = false
        binding.sortieCode.text = ""
        binding.statutCode.text = ""
        binding.btnGenererCode.setText(R.string.studio_arreter)

        val existants = fichiers.joinToString(", ") { it.chemin }.take(600)
        val requete = if (existants.isBlank()) demande
        else "$demande\n\nFichiers déjà présents (ne les réécris pas) : $existants"

        jobCode = lifecycleScope.launch {
            val brut = StringBuilder()
            var dernier = 0L
            try {
                StudioAi.generer(
                    this@StudioActivity, prefs, StudioPrompts.CODE, requete,
                    onStatus = { binding.statutCode.text = it }
                ) { morceau ->
                    brut.append(morceau)
                    val maintenant = System.currentTimeMillis()
                    if (maintenant - dernier > 100) {
                        dernier = maintenant
                        binding.sortieCode.text = brut
                    }
                }
            } catch (e: CancellationException) {
                // arrêt demandé : on garde ce qui a déjà été reçu
            } catch (e: Exception) {
                binding.statutCode.text = getString(R.string.msg_error, e.message ?: e.toString())
            } finally {
                binding.sortieCode.text = brut
                binding.btnGenererCode.setText(R.string.studio_generer)
                if (projet?.uri == p.uri) analyserCode(brut.toString())
            }
        }
    }

    private fun analyserCode(brut: String) {
        propositions = StudioParser.extraireFichiers(brut)
        if (propositions.isEmpty()) {
            if (brut.isNotBlank()) binding.statutCode.text = getString(R.string.studio_aucun_fichier)
            binding.btnCreerFichiers.isEnabled = false
        } else {
            binding.statutCode.text = getString(
                R.string.studio_fichiers_proposes,
                propositions.size,
                propositions.joinToString(", ") { it.chemin }
            )
            binding.btnCreerFichiers.isEnabled = true
        }
    }

    private fun creerFichiersProposes() {
        val p = projetOuMessage() ?: return
        val liste = propositions
        if (liste.isEmpty()) return
        binding.btnCreerFichiers.isEnabled = false
        lifecycleScope.launch {
            val (crees, refus) = withContext(Dispatchers.IO) {
                var ok = 0
                val refuses = ArrayList<String>()
                for (f in liste) {
                    try {
                        ProjectStore.creerFichier(this@StudioActivity, p, f.chemin, f.contenu)
                        ok++
                    } catch (e: Exception) {
                        refuses.add("${f.chemin} — ${e.message ?: e}")
                    }
                }
                Pair(ok, refuses)
            }
            propositions = emptyList()
            val detail = if (refus.isEmpty()) "" else "\n\n" + refus.joinToString("\n")
            MaterialAlertDialogBuilder(this@StudioActivity)
                .setTitle(R.string.studio_resultat_titre)
                .setMessage(getString(R.string.studio_resultat_msg, crees, refus.size) + detail)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            rafraichir()
        }
    }

    // ---------------------------------------------------------------- créatif

    private fun lancerCrea() {
        if (jobCrea?.isActive == true) {
            jobCrea?.cancel()
            return
        }
        projetOuMessage() ?: return
        val demande = binding.demandeCrea.text.toString().trim()
        if (demande.isEmpty()) return

        svgCourant = null
        binding.btnEnregistrerSvg.isEnabled = false
        binding.statutCrea.text = ""
        binding.btnGenererCrea.setText(R.string.studio_arreter)

        jobCrea = lifecycleScope.launch {
            val brut = StringBuilder()
            try {
                StudioAi.generer(
                    this@StudioActivity, prefs, StudioPrompts.SVG, demande,
                    onStatus = { binding.statutCrea.text = it }
                ) { morceau -> brut.append(morceau) }
            } catch (e: CancellationException) {
                // arrêt demandé
            } catch (e: Exception) {
                binding.statutCrea.text = getString(R.string.msg_error, e.message ?: e.toString())
            } finally {
                binding.btnGenererCrea.setText(R.string.studio_generer)
                afficherSvg(StudioParser.extraireSvg(brut.toString()), brut.isNotBlank())
            }
        }
    }

    private fun afficherSvg(svg: String?, reponseRecue: Boolean) {
        svgCourant = svg
        if (svg == null) {
            if (reponseRecue) binding.statutCrea.text = getString(R.string.studio_svg_absent)
            binding.btnEnregistrerSvg.isEnabled = false
            return
        }
        val page = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'>" +
            "<style>html,body{margin:0;height:100%;background:#000}svg{width:100%;height:100%}</style>" +
            "</head><body>$svg</body></html>"
        binding.apercu.loadDataWithBaseURL(null, page, "text/html", "utf-8", null)
        binding.statutCrea.text = getString(R.string.studio_svg_pret)
        binding.btnEnregistrerSvg.isEnabled = true
    }

    private fun enregistrerSvg() {
        val p = projetOuMessage() ?: return
        val svg = svgCourant ?: return
        Dialogues.saisir(
            activity = this,
            titre = getString(R.string.studio_svg_titre),
            message = getString(R.string.studio_svg_msg),
            hint = getString(R.string.studio_svg_hint),
            initial = getString(R.string.studio_svg_hint),
            valider = { chemin ->
                Dialogues.erreurChemin(chemin)
                    ?: if (chemin.lowercase().endsWith(".svg")) null
                    else getString(R.string.studio_svg_extension)
            }
        ) { chemin ->
            lifecycleScope.launch {
                try {
                    val cree = withContext(Dispatchers.IO) {
                        ProjectStore.creerFichier(this@StudioActivity, p, chemin, svg)
                    }
                    Toast.makeText(
                        this@StudioActivity,
                        getString(R.string.studio_enregistre, cree.chemin),
                        Toast.LENGTH_SHORT
                    ).show()
                    rafraichir()
                } catch (e: Exception) {
                    erreur(R.string.msg_echec_creation, e)
                }
            }
        }
    }

    companion object {
        const val EXTRA_PROJET_URI = "projet_uri"
        private const val PREFS_STUDIO = "codexlocal_studio"
        private const val CLE_PROJET = "projet"
    }
}
