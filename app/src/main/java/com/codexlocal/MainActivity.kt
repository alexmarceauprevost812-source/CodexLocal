package com.codexlocal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.res.ColorStateList
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codexlocal.bureau.ActionBureau
import com.codexlocal.bureau.BureauActivity
import com.codexlocal.agent.ActionFichier
import com.codexlocal.agent.ActionsFichiers
import com.codexlocal.agent.AgentProjet
import com.codexlocal.agent.Annulation
import com.codexlocal.agent.Bilan
import com.codexlocal.agent.DemandeCreation
import com.codexlocal.agent.AgentPrompts
import com.codexlocal.agent.Equipe
import com.codexlocal.agent.NommageIA
import com.codexlocal.agent.PanneauEquipe
import com.codexlocal.agent.Role
import com.codexlocal.databinding.ActivityMainBinding
import com.codexlocal.drawer.DrawerAdapter
import com.codexlocal.drawer.DrawerItem
import com.codexlocal.editor.CodeEditorActivity
import com.codexlocal.project.Dialogues
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.ImagesProjet
import com.codexlocal.project.Langages
import com.codexlocal.project.Projet
import com.codexlocal.studio.StudioActivity
import com.codexlocal.usage.CompteurUsage
import com.codexlocal.project.ProjectStore
import com.codexlocal.session.Session
import com.codexlocal.session.SessionStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val messages = mutableListOf<Message>()
    private val adapter = MessageAdapter(messages, ::copyToClipboard, ::enregistrerReponseCommeFichier)
    private var job: Job? = null
    private var lastUiUpdate = 0L
    private var isGenerating = false
    private var modelSessionId: String? = null

    // ----- boutons de mode (barre du haut) et panneau de l'equipe -----
    private lateinit var boutonChat: TextView
    private lateinit var boutonEquipe: TextView
    private lateinit var boutonBureau: TextView
    private lateinit var panneauEquipe: PanneauEquipe

    // ----- menu lateral : sessions et projets -----
    private val itemsTiroir = mutableListOf<DrawerItem>()
    private lateinit var tiroirAdapter: DrawerAdapter
    private var session: Session? = null
    private var projetActif: Projet? = null
    private var fichiersProjet: List<FichierProjet> = emptyList()
    private var dossiersProjet: List<String> = emptyList()

    private val choisirDossier =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { ajouterProjet(it) }
        }

    private val choisirImage =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) ajouterImage(uri, commeIcone = false)
        }

    private val choisirIcone =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) ajouterImage(uri, commeIcone = true)
        }

    /** Le Bureau renvoie la tuile choisie ; on l'execute ici, comme un appui sur le menu. */
    private val bureau =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultat ->
            ActionBureau.depuisCle(resultat.data?.getStringExtra(BureauActivity.EXTRA_ACTION))
                ?.let { executerBureau(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ChatWindowInsets.install(binding)
        prefs = Prefs(this)
        appliquerFond()

        binding.toolbar.inflateMenu(R.menu.main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_chat -> {
                    nouvelleSession(); true
                }
                R.id.action_studio -> {
                    ouvrirStudio(); true
                }
                R.id.action_projets -> {
                    binding.drawerLayout.openDrawer(binding.tiroir); true
                }
                R.id.action_ouvrir_dossier -> {
                    choisirDossier.launch(null); true
                }
                R.id.action_settings -> {
                    openSettings(); true
                }
                R.id.action_check_update -> {
                    checkUpdates(); true
                }
                else -> false
            }
        }

        binding.messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messages.adapter = adapter
        updateEmptyState()

        // ----- menu lateral -----
        binding.toolbar.setNavigationOnClickListener {
            binding.drawerLayout.openDrawer(binding.tiroir)
        }
        tiroirAdapter = DrawerAdapter(itemsTiroir, ::surLigneTiroir, ::surActionTiroir)
        binding.listeTiroir.layoutManager = LinearLayoutManager(this)
        binding.listeTiroir.adapter = tiroirAdapter
        ouvrirSessionOuCreer()
        rafraichirTiroir()
        configurerBoutonsMode()

        binding.send.setOnClickListener {
            if (job?.isActive == true) stopGeneration() else send()
        }
        binding.btnPlus.setOnClickListener { menuPlus() }

        // Premier lancement : on ouvre directement les reglages
        if (savedInstanceState == null) {
            val cfg = prefs.config()
            if (cfg.provider == ProviderType.LOCAL && cfg.localModelPath.isBlank()) openSettings()
        }

        // Verification silencieuse des mises a jour au demarrage
        if (prefs.autoUpdate) {
            UpdateFlow.verifier(lifecycleScope, this, silencieux = true) { message ->
                runOnUiThread { binding.status.text = message }
            }
        }

        // Message transmis par l'editeur de code
        intent?.getStringExtra(EXTRA_MESSAGE)?.let { binding.input.setText(it) }
    }

    override fun onPause() {
        super.onPause()
        sauverSession()
    }

    override fun onResume() {
        super.onResume()
        Updater.reprendreInstallation(this)
        appliquerFond()
        refreshStatus()
    }

    /** Fond d'ecran (35 % par defaut) ; le logo d'accueil n'est repete que sur une image perso. */
    private fun appliquerFond() {
        val opacite = prefs.fondOpacite
        FondEcran.appliquer(this, binding.fondEcran, opacite)
        binding.logoAccueil.isVisible = FondEcran.estPersonnalise(this) || opacite == 0
    }

    /** Appele quand l'editeur de code renvoie un fichier a analyser. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_MESSAGE)?.let { binding.input.setText(it) }
    }

    // ------------------------------------------------------------------ UI

    private fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))

    private fun checkUpdates() {
        UpdateFlow.verifier(lifecycleScope, this, silencieux = false) { message ->
            runOnUiThread { binding.status.text = message }
        }
    }

    private fun refreshStatus() {
        val cfg = prefs.config()
        binding.status.text = when (cfg.provider) {
            ProviderType.LOCAL ->
                if (cfg.localModelPath.isBlank()) {
                    getString(R.string.msg_no_model_loaded)
                } else {
                    "Local : " + File(cfg.localModelPath).name +
                        (LocalEngine.memoireUtilisee()?.let { " · mémoire $it %" } ?: "")
                }
            else -> cfg.provider.label + " : " + cfg.model + " · " +
                CompteurUsage.resume(CompteurUsage(this).etat(cfg.provider))
        }
    }

    /**
     * Deux boutons dans la barre du haut : 💬 pour discuter, ⛑️ pour l'equipe.
     * Un appui active le mode ; un nouvel appui sur le meme bouton revient au mode Codex.
     */
    private fun configurerBoutonsMode() {
        val menu = binding.toolbar.menu
        boutonChat = menu.findItem(R.id.action_mode_chat).actionView as TextView
        boutonEquipe = menu.findItem(R.id.action_mode_equipe).actionView as TextView
        boutonChat.text = "💬"
        boutonChat.contentDescription = getString(R.string.mode_chat)
        boutonEquipe.text = "⛑️"
        boutonEquipe.contentDescription = getString(R.string.mode_equipe)
        boutonBureau = menu.findItem(R.id.action_bureau).actionView as TextView
        boutonBureau.text = "🖥️"
        boutonBureau.contentDescription = getString(R.string.action_bureau)
        boutonBureau.setOnClickListener { ouvrirBureau() }
        listOf(boutonChat, boutonEquipe, boutonBureau).forEach { it.setTextColor(getColor(R.color.white)) }
        boutonChat.setOnClickListener { changeMode(basculer(AssistantMode.CHAT)) }
        boutonEquipe.setOnClickListener { changeMode(basculer(AssistantMode.EQUIPE)) }

        panneauEquipe = PanneauEquipe(
            binding.panneauEquipe,
            listOf(
                binding.equipeEtape1, binding.equipeEtape2, binding.equipeEtape3,
                binding.equipeEtape4, binding.equipeEtape5
            ),
            binding.equipeActivite,
            binding.equipeChrono
        )
        panneauEquipe.reinitialiser()
        updateModeTheme()
    }

    private fun basculer(mode: AssistantMode) =
        if (prefs.assistantMode == mode) AssistantMode.CODEX else mode

    private fun changeMode(mode: AssistantMode) {
        if (prefs.assistantMode == mode) return
        if (isGenerating) return

        // Chaque mode demarre une conversation propre pour eviter de melanger
        // l'historique de chat classique et les instructions de programmation.
        if (messages.isNotEmpty()) {
            sauverSession()
            val nouvelle = SessionStore.creer()
            SessionStore.enregistrer(this, nouvelle)
            session = nouvelle
            appliquerSession()
            rafraichirTiroir()
        }
        prefs.assistantMode = mode
        updateModeTheme()
        refreshStatus()
    }

    /** Theme noir/orange pour Codex et Equipe ; noir/bleu ciel pour Chat. */
    private fun updateModeTheme() {
        val mode = prefs.assistantMode
        val isChat = mode == AssistantMode.CHAT
        val accent = getColor(if (isChat) R.color.sky_blue else R.color.orange)

        binding.toolbar.setTitleTextColor(accent)
        binding.status.setTextColor(accent)
        binding.input.backgroundTintList = ColorStateList.valueOf(accent)
        binding.input.hint = getString(
            when (mode) {
                AssistantMode.CHAT -> R.string.hint_input_chat
                AssistantMode.EQUIPE -> R.string.hint_input_equipe
                AssistantMode.CODEX -> R.string.hint_input
            }
        )

        marquerBouton(boutonChat, mode == AssistantMode.CHAT, getColor(R.color.sky_blue))
        marquerBouton(boutonEquipe, mode == AssistantMode.EQUIPE, getColor(R.color.white))
        panneauEquipe.afficher(mode == AssistantMode.EQUIPE)
        if (mode == AssistantMode.EQUIPE && !isGenerating) {
            panneauEquipe.reinitialiser()
            panneauEquipe.activite("⛑️ L'équipe attend ta demande…")
        }
        adapter.chatMode = isChat
        adapter.notifyDataSetChanged()
        updateSendIcon()
    }

    /** Anneau autour du bouton du mode actif, bouton un peu estompe sinon. */
    private fun marquerBouton(bouton: TextView, actif: Boolean, couleur: Int) {
        if (actif) {
            bouton.setBackgroundResource(R.drawable.bg_mode_actif)
            bouton.backgroundTintList = ColorStateList.valueOf(couleur)
        } else {
            bouton.background = null
        }
        bouton.alpha = if (actif) 1f else 0.65f
    }

    private fun updateSendIcon() {
        val drawable = when {
            isGenerating && prefs.assistantMode == AssistantMode.CHAT ->
                R.drawable.ic_stop_circle_blue
            isGenerating -> R.drawable.ic_stop_circle
            prefs.assistantMode == AssistantMode.CHAT -> R.drawable.ic_send_circle_blue
            else -> R.drawable.ic_codex_circle
        }
        binding.send.setImageResource(drawable)
        binding.send.contentDescription = getString(
            if (isGenerating) R.string.stop else if (prefs.assistantMode == AssistantMode.CHAT)
                R.string.send else R.string.send_codex
        )
    }

    private fun setGenerating(on: Boolean) {
        isGenerating = on
        updateSendIcon()
        binding.input.isEnabled = !on
        boutonChat.isEnabled = !on
        boutonEquipe.isEnabled = !on
    }

    /** Affiche le logo d'accueil tant qu'aucun message n'existe. */
    private fun updateEmptyState() {
        binding.emptyState.isVisible = messages.isEmpty()
    }

    private fun scrollToEnd() {
        val count = adapter.itemCount
        if (count > 0) binding.messages.scrollToPosition(count - 1)
    }

    private fun copyToClipboard(message: Message) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("CodexLocal", message.content))
        Toast.makeText(this, R.string.msg_copied, Toast.LENGTH_SHORT).show()
    }

    // --------------------------------------------------------------- envoi

    // ============================================================ sessions

    private fun ouvrirSessionOuCreer() {
        val id = prefs.sessionActive
        session = if (id.isNotBlank()) SessionStore.charger(this, id) else null
        if (session == null) {
            session = SessionStore.lister(this).firstOrNull() ?: SessionStore.creer()
        }
        appliquerSession()
    }

    private fun appliquerSession() {
        val s = session ?: return
        prefs.sessionActive = s.id
        messages.clear()
        messages.addAll(s.messages)
        adapter.notifyDataSetChanged()
        updateEmptyState()
        scrollToEnd()
    }

    private fun nouvelleSession() {
        stopGeneration()
        sauverSession()
        val s = SessionStore.creer()
        SessionStore.enregistrer(this, s)
        session = s
        appliquerSession()
        rafraichirTiroir()
        binding.drawerLayout.closeDrawer(binding.tiroir)
    }

    private fun basculerSession(id: String) {
        binding.drawerLayout.closeDrawer(binding.tiroir)
        if (session?.id == id) return
        stopGeneration()
        sauverSession()
        session = SessionStore.charger(this, id)
        appliquerSession()
        rafraichirTiroir()
    }

    private fun sauverSession() {
        val s = session ?: return
        s.messages.clear()
        s.messages.addAll(messages)
        if (messages.isNotEmpty() && s.titre == "Nouvelle conversation") {
            s.titre = SessionStore.titreAutomatique(messages.first().content)
        }
        SessionStore.enregistrer(this, s)
    }

    /** Le « ✕ » d'une conversation : on demande avant d'effacer. */
    private fun confirmerSuppressionSession(id: String) {
        val titre = SessionStore.charger(this, id)?.titre ?: getString(R.string.drawer_sessions)
        Dialogues.confirmer(
            this, getString(R.string.suppr_session_titre),
            "« $titre »\n\n" + getString(R.string.suppr_irreversible),
            getString(R.string.btn_supprimer)
        ) { supprimerSessions(listOf(id)) }
    }

    /** Le « 🗑 » des sessions : cocher plusieurs conversations, puis confirmer. */
    private fun choisirSessionsASupprimer() {
        val sessions = SessionStore.lister(this)
        if (sessions.isEmpty()) {
            Toast.makeText(this, R.string.suppr_aucune_session, Toast.LENGTH_SHORT).show()
            return
        }
        Dialogues.choisirPlusieurs(
            this, getString(R.string.suppr_sessions_choix),
            sessions.map { it.titre }, getString(R.string.btn_supprimer)
        ) { positions ->
            val ids = positions.map { sessions[it].id }
            val nombre = resources.getQuantityString(R.plurals.suppr_n_conversations, ids.size, ids.size)
            Dialogues.confirmer(
                this, getString(R.string.suppr_confirmer_plusieurs, nombre),
                getString(R.string.suppr_irreversible), getString(R.string.btn_supprimer)
            ) { supprimerSessions(ids) }
        }
    }

    private fun supprimerSessions(ids: Collection<String>) {
        if (ids.isEmpty()) return
        val activeSupprimee = session?.id in ids
        if (activeSupprimee) stopGeneration()
        ids.forEach { SessionStore.supprimer(this, it) }
        if (activeSupprimee) {
            session = null
            ouvrirSessionOuCreer()
        }
        rafraichirTiroir()
        Toast.makeText(
            this,
            if (ids.size == 1) getString(R.string.msg_session_supprimee)
            else getString(
                R.string.suppr_sessions_faites,
                resources.getQuantityString(R.plurals.suppr_n_conversations, ids.size, ids.size)
            ),
            Toast.LENGTH_SHORT
        ).show()
    }

    // ============================================================ projets

    private fun ajouterProjet(uri: Uri) {
        // On demande lecture + ecriture ; certains fournisseurs n'accordent que la lecture.
        val ok = runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        }.getOrElse {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                true
            }.getOrDefault(false)
        }
        if (!ok) {
            binding.status.text = getString(R.string.msg_acces_refuse)
            return
        }
        val nom = uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/') ?: "Projet"
        val projet = Projet(nom, uri.toString(), System.currentTimeMillis(), interne = false)
        ProjectStore.ajouter(this, projet)
        rafraichirTiroir()
        ouvrirProjet(projet)
    }

    /** Cree un projet dans l'espace de l'appli (visible par USB). */
    private fun dialogNouveauProjet() {
        binding.drawerLayout.closeDrawer(binding.tiroir)
        val marge = (16 * resources.displayMetrics.density).toInt()
        val champ = EditText(this).apply {
            hint = getString(R.string.dialog_nom_projet)
            setTextColor(0xFFECECEC.toInt())
            setHintTextColor(0xFF5A5A5A.toInt())
            setSingleLine()
            setPadding(marge, marge, marge, marge)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_nouveau_projet)
            .setMessage(
                getString(R.string.msg_copie_usb, ProjectStore.dossierInterne(this).absolutePath)
            )
            .setView(champ)
            .setPositiveButton(R.string.btn_creer) { _, _ ->
                val nom = champ.text.toString().trim()
                if (nom.isNotBlank()) {
                    val projet = ProjectStore.creerProjetInterne(this, nom)
                    ProjectStore.ajouter(this, projet)
                    rafraichirTiroir()
                    ouvrirProjet(projet)
                    Toast.makeText(
                        this,
                        getString(R.string.msg_projet_cree, projet.nom),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(R.string.btn_cancel, null)
            .show()
    }

    private fun ouvrirProjet(projet: Projet?) {
        projetActif = projet
        fichiersProjet = emptyList()
        dossiersProjet = emptyList()
        binding.titreTiroir.text = projet?.nom ?: getString(R.string.drawer_sous_titre)
        rafraichirTiroir()
        if (projet == null) return

        lifecycleScope.launch {
            val fichiers = withContext(Dispatchers.IO) {
                runCatching { ProjectStore.listerFichiers(this@MainActivity, projet) }
                    .getOrDefault(emptyList())
            }
            val dossiers = withContext(Dispatchers.IO) {
                runCatching { ProjectStore.listerDossiers(this@MainActivity, projet) }
                    .getOrDefault(emptyList())
            }
            if (projetActif?.uri != projet.uri) return@launch
            fichiersProjet = fichiers
            dossiersProjet = dossiers
            rafraichirTiroir()
            binding.status.text = getString(R.string.msg_projet_ouvert, projet.nom) + " — " +
                getString(R.string.msg_fichiers_trouves, fichiers.size)
        }
    }

    /** Création dans le projet actif, avec sauvegarde puis ouverture dans l'éditeur. */
    private fun dialogNouveauFichier(
        contenuInitial: String = "",
        nomSuggere: String = "main.py"
    ) {
        val projet = projetActif
        if (projet == null) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.drawer_nouveau_fichier)
                .setMessage(R.string.msg_choisir_projet)
                .setPositiveButton(R.string.drawer_nouveau_projet) { _, _ -> dialogNouveauProjet() }
                .setNegativeButton(R.string.btn_cancel, null)
                .show()
            return
        }
        binding.drawerLayout.closeDrawer(binding.tiroir)
        val marge = (16 * resources.displayMetrics.density).toInt()
        val champ = EditText(this).apply {
            hint = getString(R.string.hint_nom_fichier)
            setText(nomSuggere)
            setSelection(text.length)
            setSingleLine()
            setTextColor(0xFFECECEC.toInt())
            setHintTextColor(0xFF8C8C8C.toInt())
            setPadding(marge, marge, marge, marge)
        }
        val fenetre = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.drawer_nouveau_fichier)
            .setMessage(R.string.msg_creer_fichier)
            .setView(champ)
            .setPositiveButton(R.string.btn_creer, null)
            .setNegativeButton(R.string.btn_cancel, null)
            .create()
        fenetre.setOnShowListener {
            fenetre.getButton(android.content.DialogInterface.BUTTON_POSITIVE)
                .setOnClickListener {
                    val chemin = champ.text.toString().trim()
                    try {
                        ProjectStore.validerCheminRelatif(chemin)
                    } catch (e: IllegalArgumentException) {
                        champ.error = e.message
                        return@setOnClickListener
                    }
                    fenetre.dismiss()
                    lifecycleScope.launch {
                        try {
                            val fichier = withContext(Dispatchers.IO) {
                                ProjectStore.creerFichier(
                                    this@MainActivity, projet, chemin, contenuInitial
                                )
                            }
                            Toast.makeText(this@MainActivity,
                                getString(R.string.msg_fichier_cree, fichier.chemin),
                                Toast.LENGTH_SHORT).show()
                            if (projetActif?.uri == projet.uri) ouvrirProjet(projet)
                            ouvrirFichier(fichier)
                        } catch (e: Exception) {
                            MaterialAlertDialogBuilder(this@MainActivity)
                                .setTitle(R.string.msg_echec_creation)
                                .setMessage(e.message ?: e.toString())
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                }
        }
        fenetre.show()
    }

    /** Extrait un bloc de code de l'IA. L'utilisateur choisit le nom avant enregistrement. */
    private fun enregistrerReponseCommeFichier(message: Message) {
        if (message.content.isBlank()) return
        val bloc = Regex("(?s)`{3}([^\\n`]*)\\r?\\n(.*?)\\r?\\n?`{3}").find(message.content)
        val langage = bloc?.groupValues?.get(1)?.trim()?.lowercase()?.substringBefore(' ') ?: ""
        val extension = when (langage) {
            "python", "py" -> "py"
            "javascript", "js" -> "js"
            "typescript", "ts" -> "ts"
            "html" -> "html"
            "css" -> "css"
            "json" -> "json"
            "java" -> "java"
            "kotlin", "kt" -> "kt"
            "bash", "sh", "shell" -> "sh"
            "xml" -> "xml"
            "sql" -> "sql"
            else -> "md"
        }
        val contenu = if (bloc == null) message.content else
            bloc.groupValues[2].trimEnd() + "\n"
        dialogNouveauFichier(contenu, if (bloc == null) "reponse.md" else "code.$extension")
    }

    /** Cree un dossier (ou une arborescence src/utils) dans le projet actif. */
    private fun dialogNouveauDossier() {
        val projet = projetActif
        if (projet == null) {
            Toast.makeText(this, R.string.studio_besoin_projet, Toast.LENGTH_SHORT).show()
            return
        }
        binding.drawerLayout.closeDrawer(binding.tiroir)
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
                        ProjectStore.creerDossier(this@MainActivity, projet, chemin)
                    }
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.msg_dossier_cree, cree),
                        Toast.LENGTH_SHORT
                    ).show()
                    if (projetActif?.uri == projet.uri) ouvrirProjet(projet)
                } catch (e: Exception) {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.msg_echec_dossier)
                        .setMessage(e.message ?: e.toString())
                        .setPositiveButton(android.R.string.ok, null)
                        .show()
                }
            }
        }
    }

    private fun ouvrirStudio(mode: String? = null) {
        binding.drawerLayout.closeDrawer(binding.tiroir)
        startActivity(
            Intent(this, StudioActivity::class.java)
                .putExtra(StudioActivity.EXTRA_PROJET_URI, projetActif?.uri)
                .putExtra(StudioActivity.EXTRA_MODE, mode)
        )
    }

    /** Bouton ＋ : une image du telephone entre dans le projet ouvert (et dans son APK). */
    private fun menuPlus() {
        if (projetActif == null) {
            Toast.makeText(this, R.string.plus_besoin_projet, Toast.LENGTH_LONG).show()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setItems(arrayOf(getString(R.string.plus_image), getString(R.string.plus_icone))) { _, choix ->
                if (choix == 0) choisirImage.launch("image/*") else choisirIcone.launch("image/*")
            }
            .show()
    }

    private fun ajouterImage(uri: android.net.Uri, commeIcone: Boolean) {
        val projet = projetActif ?: return
        lifecycleScope.launch {
            try {
                val chemin = withContext(Dispatchers.IO) {
                    if (commeIcone) {
                        ImagesProjet.definirIcone(this@MainActivity, projet, uri)
                    } else {
                        ImagesProjet.importer(this@MainActivity, projet, uri, nomAffiche(uri))
                    }
                }
                Toast.makeText(
                    this@MainActivity,
                    getString(if (commeIcone) R.string.plus_icone_definie else R.string.plus_image_ajoutee, chemin),
                    Toast.LENGTH_LONG
                ).show()
                if (projetActif?.uri == projet.uri) ouvrirProjet(projet)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.plus_echec, e.message ?: e.toString()),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /** Nom du fichier choisi, tel que le montre le telephone (pour garder un nom parlant). */
    private fun nomAffiche(uri: android.net.Uri): String? =
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun ouvrirBureau() {
        binding.drawerLayout.closeDrawer(binding.tiroir)
        bureau.launch(Intent(this, BureauActivity::class.java))
    }

    /** Execute le choix fait dans le Bureau (memes actions que le menu « ⋮ » et le tiroir). */
    private fun executerBureau(action: ActionBureau) {
        when (action) {
            ActionBureau.CODEX -> changeMode(AssistantMode.CODEX)
            ActionBureau.CHAT -> changeMode(AssistantMode.CHAT)
            ActionBureau.EQUIPE -> changeMode(AssistantMode.EQUIPE)
            ActionBureau.STUDIO -> ouvrirStudio()
            ActionBureau.TESTER -> ouvrirStudio(StudioActivity.MODE_TEST)
            ActionBureau.PROJETS -> binding.drawerLayout.openDrawer(binding.tiroir)
            ActionBureau.OUVRIR_DOSSIER -> choisirDossier.launch(null)
            ActionBureau.NOUVELLE -> nouvelleSession()
            ActionBureau.REGLAGES -> openSettings()
            ActionBureau.MISES_A_JOUR -> checkUpdates()
            ActionBureau.PARTAGER -> startActivity(Intent(this, PartageActivity::class.java))
        }
    }

    private fun ouvrirFichier(f: FichierProjet) {
        binding.drawerLayout.closeDrawer(binding.tiroir)
        startActivity(
            Intent(this, CodeEditorActivity::class.java)
                .putExtra(CodeEditorActivity.EXTRA_CHEMIN, f.uri)
                .putExtra(CodeEditorActivity.EXTRA_NOM, f.chemin)
                .putExtra(CodeEditorActivity.EXTRA_INTERNE, f.interne)
        )
    }

    /** Le « ✕ » d'un projet : on demande avant d'effacer. */
    private fun confirmerSuppressionProjet(uri: String) {
        val projet = ProjectStore.lister(this).firstOrNull { it.uri == uri } ?: return
        Dialogues.confirmerSuppressionProjet(this, projet) { apresSuppressionProjets(setOf(uri)) }
    }

    /** Le « 🗑 » des projets : cocher plusieurs projets, puis confirmer. */
    private fun choisirProjetsASupprimer() {
        val projets = ProjectStore.lister(this)
        if (projets.isEmpty()) {
            Toast.makeText(this, R.string.suppr_aucun_projet, Toast.LENGTH_SHORT).show()
            return
        }
        Dialogues.choisirPlusieurs(
            this, getString(R.string.suppr_projets_choix),
            projets.map { if (it.interne) it.nom else it.nom + "  (dossier du téléphone)" },
            getString(R.string.btn_supprimer)
        ) { positions ->
            val choisis = positions.map { projets[it] }
            val internes = choisis.count { it.interne }
            val externes = choisis.size - internes
            val lignes = mutableListOf<String>()
            if (internes > 0) lignes.add(
                getString(
                    R.string.suppr_projets_internes_msg,
                    resources.getQuantityString(R.plurals.suppr_n_projets, internes, internes)
                )
            )
            if (externes > 0) lignes.add(
                getString(
                    R.string.suppr_projets_externes_msg,
                    resources.getQuantityString(R.plurals.suppr_n_dossiers, externes, externes)
                )
            )
            val nombre = resources.getQuantityString(R.plurals.suppr_n_projets, choisis.size, choisis.size)
            Dialogues.confirmer(
                this, getString(R.string.suppr_confirmer_plusieurs, nombre),
                lignes.joinToString("\n\n"), getString(R.string.btn_supprimer)
            ) { supprimerProjets(choisis) }
        }
    }

    private fun supprimerProjets(projets: List<Projet>) {
        val echecs = projets.filterNot { ProjectStore.supprimerProjet(this, it) }
        val faits = projets.filter { it !in echecs }.map { it.uri }.toSet()
        apresSuppressionProjets(faits)
        if (echecs.isNotEmpty()) {
            Toast.makeText(
                this, getString(R.string.msg_suppression_echec, echecs.joinToString { it.nom }),
                Toast.LENGTH_LONG
            ).show()
        }
    }

    /** Met l'ecran a jour une fois ces projets supprimes (et ferme le projet ouvert s'il en fait partie). */
    private fun apresSuppressionProjets(uris: Set<String>) {
        if (projetActif?.uri in uris) {
            stopGeneration()
            projetActif = null
            fichiersProjet = emptyList()
            dossiersProjet = emptyList()
            binding.titreTiroir.text = getString(R.string.drawer_sous_titre)
        }
        rafraichirTiroir()
    }

    // ============================================================== tiroir

    private fun rafraichirTiroir() {
        val nouveaux = mutableListOf<DrawerItem>()

        nouveaux.add(DrawerItem.Bouton(getString(R.string.drawer_studio), CLE_STUDIO))
        nouveaux.add(
            DrawerItem.Section(
                getString(R.string.drawer_sessions), "＋", CLE_NOUVELLE_SESSION,
                "🗑", CLE_SUPPRIMER_SESSIONS
            )
        )
        val sessions = SessionStore.lister(this)
        if (sessions.isEmpty()) {
            nouveaux.add(DrawerItem.Info("Aucune conversation enregistrée"))
        } else {
            val format = SimpleDateFormat("d MMM HH:mm", Locale.CANADA_FRENCH)
            sessions.forEach { s ->
                nouveaux.add(
                    DrawerItem.LigneSession(
                        id = s.id,
                        titre = s.titre,
                        sous = format.format(Date(s.modifie)) + "  ·  " + s.messages.size + " msg",
                        actif = s.id == session?.id
                    )
                )
            }
        }

        nouveaux.add(
            DrawerItem.Section(
                getString(R.string.drawer_projets), "＋", CLE_NOUVEAU_PROJET,
                "🗑", CLE_SUPPRIMER_PROJETS
            )
        )
        val projets = ProjectStore.lister(this)
        if (projets.isEmpty()) {
            nouveaux.add(DrawerItem.Info(getString(R.string.drawer_aucun_projet)))
        } else {
            projets.forEach { p ->
                val ouvert = p.uri == projetActif?.uri
                nouveaux.add(
                    DrawerItem.LigneProjet(
                        uri = p.uri,
                        nom = p.nom,
                        sous = if (ouvert) getString(R.string.drawer_fermer_projet)
                        else getString(R.string.drawer_ouvrir_dossier),
                        actif = ouvert
                    )
                )
            }
        }

        val projet = projetActif
        if (projet != null) {
            nouveaux.add(
                DrawerItem.Section(
                    getString(R.string.drawer_fichiers) + " — " + projet.nom,
                    "＋", CLE_NOUVEAU_FICHIER
                )
            )
            dossiersProjet.forEach { nouveaux.add(DrawerItem.Info("📁 $it/")) }
            if (fichiersProjet.isEmpty()) {
                nouveaux.add(
                    DrawerItem.Info(
                        getString(
                            if (dossiersProjet.isEmpty()) R.string.drawer_chargement
                            else R.string.drawer_aucun_fichier
                        )
                    )
                )
            } else {
                fichiersProjet.forEach {
                    nouveaux.add(
                        DrawerItem.LigneFichier(it.nom, it.chemin, it.uri, Langages.pour(it.nom))
                    )
                }
            }
        }

        if (projetActif != null) {
            nouveaux.add(DrawerItem.Bouton(getString(R.string.drawer_nouveau_fichier), CLE_NOUVEAU_FICHIER))
            nouveaux.add(DrawerItem.Bouton(getString(R.string.drawer_nouveau_dossier), CLE_NOUVEAU_DOSSIER))
        }
        nouveaux.add(
            DrawerItem.Bouton(getString(R.string.drawer_nouveau_projet), CLE_NOUVEAU_PROJET)
        )
        nouveaux.add(
            DrawerItem.Bouton(getString(R.string.drawer_ouvrir_saf), CLE_OUVRIR_DOSSIER)
        )
        nouveaux.add(DrawerItem.Info(getString(R.string.drawer_espace)))
        nouveaux.add(DrawerItem.Info(ProjectStore.dossierInterne(this).absolutePath))

        if (::tiroirAdapter.isInitialized) tiroirAdapter.remplacer(nouveaux)
    }

    private fun surLigneTiroir(item: DrawerItem) {
        when (item) {
            is DrawerItem.LigneSession -> basculerSession(item.id)
            is DrawerItem.LigneFichier -> {
                val f = fichiersProjet.firstOrNull { it.uri == item.uri }
                if (f != null) ouvrirFichier(f)
            }
            is DrawerItem.LigneProjet ->
                if (item.uri == projetActif?.uri) {
                    ouvrirProjet(null)
                } else {
                    ouvrirProjet(ProjectStore.lister(this).firstOrNull { it.uri == item.uri })
                }
            else -> Unit
        }
    }

    private fun surActionTiroir(item: DrawerItem) {
        val cle = when (item) {
            is DrawerItem.Section -> item.cleAction
            is DrawerItem.Bouton -> item.cleAction
            else -> null
        }
        when {
            cle == CLE_NOUVELLE_SESSION -> nouvelleSession()
            cle == CLE_NOUVEAU_PROJET -> dialogNouveauProjet()
            cle == CLE_NOUVEAU_FICHIER -> dialogNouveauFichier()
            cle == CLE_NOUVEAU_DOSSIER -> dialogNouveauDossier()
            cle == CLE_STUDIO -> ouvrirStudio()
            cle == CLE_OUVRIR_DOSSIER -> choisirDossier.launch(null)
            cle == CLE_SUPPRIMER_SESSIONS -> choisirSessionsASupprimer()
            cle == CLE_SUPPRIMER_PROJETS -> choisirProjetsASupprimer()
            item is DrawerItem.LigneSession -> confirmerSuppressionSession(item.id)
            item is DrawerItem.LigneProjet -> confirmerSuppressionProjet(item.uri)
        }
    }

    private fun send() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        val mode = prefs.assistantMode
        val cfg = prefs.config().forMode(mode)

        binding.input.setText("")
        val user = Message.user(text)
        messages.add(user)
        adapter.notifyItemInserted(messages.size - 1)
        updateEmptyState()
        scrollToEnd()
        setGenerating(true)

        job = lifecycleScope.launch {
            val annulations = mutableListOf<Annulation>()
            try {
                if (mode == AssistantMode.EQUIPE) {
                    jouerEquipe(user, cfg, annulations)
                } else {
                    converser(user, mode, cfg, annulations)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                afficherErreur(e.message ?: e.toString())
            } finally {
                setGenerating(false)
                refreshStatus()
                sauverSession()
                rafraichirTiroir()
                if (annulations.isNotEmpty()) {
                    projetActif?.let { ouvrirProjet(it) }
                    proposerAnnulation(annulations)
                }
            }
        }
    }

    private fun afficherErreur(message: String) {
        binding.status.text = getString(R.string.msg_error, message)
        val dernier = messages.lastOrNull() ?: return
        if (dernier.isUser) return
        dernier.content = if (dernier.content.isBlank()) {
            "[erreur] $message"
        } else {
            dernier.content + "\n\n[erreur] $message"
        }
        adapter.notifyItemChanged(messages.size - 1)
    }

    // ------------------------------------------------- conversation + fichiers

    /**
     * Conversation normale. En mode Codex, l'IA cree, ecrit et lit les fichiers toute seule :
     * ce qu'elle ecrit est applique (dans un projet cree automatiquement s'il n'y en a pas), et
     * si elle demande a lire un fichier, son contenu lui est renvoye automatiquement (quelques
     * tours au plus).
     */
    private suspend fun converser(
        user: Message,
        mode: AssistantMode,
        base: AppConfig,
        annulations: MutableList<Annulation>
    ) {
        val codex = mode == AssistantMode.CODEX
        val projetDepart = if (codex) projetActif else null
        var projet = projetDepart
        val cfg = if (codex) avecActionsFichiers(base) else base
        if (codex) {
            val contexte = projetDepart?.let { p ->
                withContext(Dispatchers.IO) { AgentProjet.contexteProjet(this@MainActivity, p) }
            }
            // Le rappel de fin de message : les petits modeles suivent la derniere phrase, pas le debut.
            user.envoi = (if (contexte != null) contexte + "\n\n" else "") + user.content +
                "\n\n" + AgentPrompts.RAPPEL_FICHIERS
        }

        var tours = 0
        var relance = false
        while (true) {
            val assistant = Message.assistant()
            messages.add(assistant)
            adapter.notifyItemInserted(messages.size - 1)
            scrollToEnd()

            if (!preparerModele(cfg, repartirDeZero = false)) {
                retirerMessage(assistant)
                return
            }
            diffuser(assistant, messages.subList(0, messages.size - 1).toList(), cfg)
            if (!codex) return

            var actions = ActionsFichiers.extraire(assistant.content)
            // L'IA a seulement parle alors qu'on lui demandait de creer quelque chose : une seule relance.
            if (!relance && DemandeCreation.estUneDemande(user.content) &&
                actions.none { it is ActionFichier.Ecrire || it is ActionFichier.SansNom || it is ActionFichier.Lire }
            ) {
                relance = true
                val suite = Message.user(getString(R.string.agent_relance))
                suite.envoi = AgentPrompts.RELANCE_FICHIERS
                messages.add(suite)
                adapter.notifyItemInserted(messages.size - 1)
                continue
            }
            // Du code sans nom de fichier : l'IA choisit le nom, qui devient le titre du fichier.
            val sansNom = actions.filterIsInstance<ActionFichier.SansNom>()
            if (sansNom.isNotEmpty()) {
                val pris = HashSet<String>()
                projet?.let { p ->
                    pris += withContext(Dispatchers.IO) {
                        runCatching { ProjectStore.listerFichiers(this@MainActivity, p).map { it.chemin } }
                            .getOrDefault(emptyList())
                    }
                }
                pris += actions.filterIsInstance<ActionFichier.Ecrire>().map { it.chemin }
                // la liste des fichiers est limitee : chaque nom choisi est aussi verifie directement
                val projetNomme = projet
                val existe: (String) -> Boolean = { chemin ->
                    projetNomme != null && runCatching {
                        ProjectStore.trouverFichier(this@MainActivity, projetNomme, chemin) != null
                    }.getOrDefault(false)
                }
                val noms = nommerBlocs(sansNom, pris, base, cfg, existe)
                if (noms.isNotEmpty()) {
                    assistant.content = ActionsFichiers.avecTitres(assistant.content, noms)
                    val i = messages.indexOfFirst { it === assistant }
                    if (i >= 0) adapter.notifyItemChanged(i)
                    actions = ActionsFichiers.extraire(assistant.content)
                }
            }
            // Des fichiers sont ecrits mais aucun projet n'est ouvert : on en cree un tout seul.
            if (projet == null && actions.any { it is ActionFichier.Ecrire }) {
                projet = projetAutomatique("Codex_", R.string.codex_projet_auto)
            }
            if (projet == null) {
                // rien n'est ecrit sans projet, mais on dit pourquoi quand l'IA a donne du code sans nom
                val avertissements = actions.filterIsInstance<ActionFichier.SansNom>()
                    .map { AgentProjet.texteSansNom(it) }
                if (avertissements.isNotEmpty()) {
                    assistant.content += "\n\n" + Bilan(ignores = avertissements).resume()
                    val i = messages.indexOfFirst { it === assistant }
                    if (i >= 0) adapter.notifyItemChanged(i)
                    scrollToEnd()
                }
                return
            }

            val bilan = appliquerActions(projet, assistant, cfg, actions)
            annulations += bilan.annulations
            tours++
            if (bilan.lectures.isEmpty() || tours >= MAX_TOURS_LECTURE) return

            val suite = Message.user(
                "📖 Lecture : " + bilan.lectures.joinToString(", ") { it.chemin }
            )
            suite.envoi = bilan.texteLectures()
            messages.add(suite)
            adapter.notifyItemInserted(messages.size - 1)
        }
    }

    /**
     * Demande a l'IA le nom des blocs de code sans nom (une petite question par bloc).
     * Renvoie numero de ligne -> nom ; un nom par defaut est donne quand l'IA ne repond pas bien.
     * [pris] : les fichiers qui existent deja, dont le nom ne doit jamais etre repris ; [existe]
     * le verifie aussi pour chaque nom choisi.
     *
     * Modele local : la question est posee DANS la conversation en cours ([cfgConversation]), car
     * remettre sa memoire a zero ferait perdre le fil du dialogue. Service en ligne : question
     * a part, sans historique.
     */
    private suspend fun nommerBlocs(
        blocs: List<ActionFichier.SansNom>,
        pris: Set<String>,
        base: AppConfig,
        cfgConversation: AppConfig,
        existe: (String) -> Boolean
    ): Map<Int, String> {
        val local = cfgConversation.provider == ProviderType.LOCAL
        val cfg = if (local) {
            cfgConversation.copy(maxTokens = 320)
        } else {
            base.copy(systemPrompt = NommageIA.SYSTEME, maxTokens = 60)
        }
        val reponses = ArrayList<String?>()
        for ((i, bloc) in blocs.withIndex()) {
            if (i >= NommageIA.MAX_QUESTIONS) {
                reponses.add(null)
                continue
            }
            binding.status.text = getString(R.string.agent_nommage, i + 1, blocs.size)
            reponses.add(
                try {
                    if (!preparerModele(cfg, repartirDeZero = false)) {
                        null
                    } else {
                        val reponse = Message.assistant()
                        val historique = if (local) {
                            messages.toList() + Message.user(NommageIA.demandeDansLaConversation(bloc))
                        } else {
                            listOf(Message.user(NommageIA.demande(bloc, pris.toList())))
                        }
                        diffuser(reponse, historique, cfg)
                        reponse.content
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
            )
        }
        return withContext(Dispatchers.IO) { NommageIA.attribuer(blocs, reponses, pris, existe) }
    }

    /** Consignes de fichiers ajoutees au prompt, avec de la place pour des fichiers entiers. */
    private fun avecActionsFichiers(base: AppConfig) = base.copy(
        systemPrompt = base.systemPrompt + "\n\n" + AgentPrompts.FICHIERS,
        maxTokens = base.maxTokens.coerceAtLeast(
            if (base.provider == ProviderType.LOCAL) 3072 else 4096
        )
    )

    /** Charge le modele local si besoin. Faux (avec un message) quand il n'y en a pas. */
    private suspend fun preparerModele(cfg: AppConfig, repartirDeZero: Boolean): Boolean {
        if (cfg.provider != ProviderType.LOCAL) return true
        if (cfg.localModelPath.isBlank()) {
            binding.status.text = getString(R.string.msg_no_model_loaded)
            return false
        }
        val dejaCharge = LocalEngine.isLoadedFor(cfg.localModelPath)
        if (!dejaCharge) binding.status.text = getString(R.string.msg_loading_model)
        LocalEngine.load(this, cfg.localModelPath, cfg, repartirDeZero) { msg ->
            runOnUiThread { binding.status.text = msg }
        }
        if (repartirDeZero) {
            // Les roles de l'equipe ne partagent pas de conversation : la suivante repart a zero.
            modelSessionId = null
        } else if (modelSessionId != session?.id) {
            LocalEngine.resetConversation(this, cfg)
            modelSessionId = session?.id
        }
        if (!dejaCharge) {
            binding.status.text =
                getString(R.string.msg_model_loaded, File(cfg.localModelPath).name)
        }
        LocalEngine.applySampling(cfg)
        return true
    }

    /** Envoie `history` a l'IA et ecrit la reponse dans `assistant`, en direct. */
    private suspend fun diffuser(
        assistant: Message,
        history: List<Message>,
        cfg: AppConfig,
        surProgres: ((String) -> Unit)? = null
    ) {
        ChatRepository.stream(history, cfg, CompteurUsage(this)).collect { chunk ->
            assistant.content += chunk
            val now = System.currentTimeMillis()
            if (now - lastUiUpdate > 60) {
                lastUiUpdate = now
                val i = messages.indexOfFirst { it === assistant }
                if (i >= 0) adapter.notifyItemChanged(i)
                scrollToEnd()
                surProgres?.invoke(assistant.content)
            }
        }
        val i = messages.indexOfFirst { it === assistant }
        if (i >= 0) adapter.notifyItemChanged(i)
        scrollToEnd()
    }

    private fun retirerMessage(message: Message) {
        val i = messages.indexOfFirst { it === message }
        if (i >= 0) {
            messages.removeAt(i)
            adapter.notifyItemRemoved(i)
        }
        updateEmptyState()
    }

    /** Applique dans le projet ce que la reponse demande, et l'ecrit sous la reponse. */
    private suspend fun appliquerActions(
        projet: Projet,
        assistant: Message,
        cfg: AppConfig,
        actions: List<ActionFichier> = ActionsFichiers.extraire(assistant.content)
    ): Bilan {
        if (actions.isEmpty()) return Bilan()
        // Reponse proche de la limite de longueur : un fichier sans "=== FIN ===" est peut-etre coupe.
        val peutEtreCoupee = assistant.content.length >= cfg.maxTokens * 2
        val bilan = withContext(Dispatchers.IO) {
            AgentProjet.appliquer(this@MainActivity, projet, actions, ignorerIncomplets = peutEtreCoupee)
        }
        val resume = bilan.resume()
        if (resume.isNotEmpty()) {
            assistant.content += "\n\n" + resume
            val i = messages.indexOfFirst { it === assistant }
            if (i >= 0) adapter.notifyItemChanged(i)
            scrollToEnd()
        }
        return bilan
    }

    private fun proposerAnnulation(annulations: List<Annulation>) {
        val creations = annulations.count { it.ancien == null }
        Snackbar.make(
            binding.root,
            getString(R.string.agent_snackbar, creations, annulations.size - creations),
            12_000
        ).setAction(R.string.agent_annuler) {
            lifecycleScope.launch {
                val n = withContext(Dispatchers.IO) {
                    AgentProjet.annuler(this@MainActivity, annulations)
                }
                Toast.makeText(
                    this@MainActivity, getString(R.string.agent_annule, n), Toast.LENGTH_SHORT
                ).show()
                projetActif?.let { ouvrirProjet(it) }
            }
        }.setAnchorView(binding.input).show()
    }

    // ------------------------------------------------------------- equipe

    /** Le projet ouvert, ou un nouveau projet cree pour l'equipe s'il n'y en a pas. */
    private suspend fun projetPourEquipe(): Projet = projetAutomatique("Equipe_", R.string.equipe_projet_auto)

    /** Le projet ouvert, ou un nouveau projet (nom = [prefixe] + date) cree et ouvert tout seul. */
    private suspend fun projetAutomatique(prefixe: String, messageToast: Int): Projet {
        projetActif?.let { return it }
        val base = prefixe + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.CANADA_FRENCH).format(Date())
        val cree = withContext(Dispatchers.IO) {
            // jamais un dossier qui existe deja : sinon le « nouveau » projet rouvrirait l'ancien
            val dossiers = ProjectStore.dossierInterne(this@MainActivity)
            var nom = base
            var n = 2
            while (File(dossiers, nom).exists()) nom = base + "_" + n++
            ProjectStore.creerProjetInterne(this@MainActivity, nom)
        }
        ProjectStore.ajouter(this, cree)
        ouvrirProjet(cree)
        Toast.makeText(this, getString(messageToast, cree.nom), Toast.LENGTH_SHORT).show()
        return cree
    }

    /**
     * L'equipe au travail, une etape apres l'autre : le chef donne ses consignes, le
     * planificateur fait le plan, le redacteur ecrit les textes, le codeur ecrit le code
     * (fichiers crees dans le projet), puis le chef relit. Le panneau du haut montre tout
     * en direct.
     */
    private suspend fun jouerEquipe(
        user: Message,
        cfg: AppConfig,
        annulations: MutableList<Annulation>
    ) {
        val projet = projetPourEquipe()
        val demande = user.content
        var contexte = withContext(Dispatchers.IO) {
            AgentProjet.contexteProjet(this@MainActivity, projet)
        }

        panneauEquipe.reinitialiser()
        panneauEquipe.afficher(true)
        val debut = System.currentTimeMillis()
        val chrono = lifecycleScope.launch {
            while (isActive) {
                panneauEquipe.chrono((System.currentTimeMillis() - debut) / 1000)
                delay(1000)
            }
        }
        try {
            val (brief, _) = jouerRole(
                Role.CHEF, Equipe.demandeChef(demande, contexte), projet, cfg, annulations
            )
            val (plan, _) = jouerRole(
                Role.PLANIFICATEUR, Equipe.demandePlanificateur(demande, brief, contexte),
                projet, cfg, annulations
            )
            val (textes, bilanTextes) = jouerRole(
                Role.REDACTEUR, Equipe.demandeRedacteur(demande, brief, plan, contexte),
                projet, cfg, annulations
            )
            // Le redacteur a pu creer des fichiers : le codeur doit les voir.
            contexte = withContext(Dispatchers.IO) {
                AgentProjet.contexteProjet(this@MainActivity, projet)
            }
            val (_, bilanCode) = jouerRole(
                Role.CODEUR, Equipe.demandeCodeur(demande, brief, plan, textes, contexte),
                projet, cfg, annulations
            )
            val bilan = bilanTextes + bilanCode
            jouerRole(Role.REVUE, Equipe.demandeRevue(demande, plan, bilan), projet, cfg, annulations)
            panneauEquipe.fini(
                "✅ Équipe terminée — ${bilan.crees.size} fichier(s) créé(s), " +
                    "${bilan.modifies.size} modifié(s)"
            )
            binding.status.text = getString(R.string.msg_projet_ouvert, projet.nom)
        } finally {
            chrono.cancel()
        }
    }

    /** Fait travailler un role : message dans le chat, voyant du panneau, fichiers ecrits. */
    private suspend fun jouerRole(
        role: Role,
        requete: String,
        projet: Projet,
        base: AppConfig,
        annulations: MutableList<Annulation>
    ): Pair<String, Bilan> {
        panneauEquipe.commencer(role)
        binding.status.text =
            "${role.emoji} ${role.titre} — étape ${role.ordinal + 1}/${Equipe.NB_ETAPES}"
        val assistant = Message.assistant(role.entete)
        messages.add(assistant)
        adapter.notifyItemInserted(messages.size - 1)
        scrollToEnd()

        val cfg = base.copy(
            systemPrompt = role.systemPrompt(projetOuvert = true),
            maxTokens = role.maxTokens(base)
        )
        if (!preparerModele(cfg, repartirDeZero = true)) {
            error(getString(R.string.msg_no_model_loaded))
        }
        diffuser(assistant, listOf(Message.user(requete)), cfg) { contenu ->
            val ligne = Equipe.derniereLigne(contenu.removePrefix(role.entete))
            if (ligne.isNotEmpty()) panneauEquipe.activite("${role.emoji} $ligne")
        }
        val texte = assistant.content.removePrefix(role.entete).trim()

        var bilan = Bilan()
        if (role.ecrit) {
            bilan = appliquerActions(projet, assistant, cfg)
            annulations += bilan.annulations
            val premiere = bilan.resume().lineSequence().firstOrNull().orEmpty()
            if (premiere.isNotEmpty()) panneauEquipe.activite("${role.emoji} $premiere")
        }
        panneauEquipe.terminer(role)
        return texte to bilan
    }

    private fun stopGeneration() {
        job?.cancel()
        job = null
        setGenerating(false)
    }

    private fun newChat() {
        stopGeneration()
        messages.clear()
        adapter.notifyDataSetChanged()
        updateEmptyState()
        val cfg = prefs.config()
        if (cfg.provider == ProviderType.LOCAL && LocalEngine.isLoadedFor(cfg.localModelPath)) {
            lifecycleScope.launch {
                runCatching { LocalEngine.resetConversation(this@MainActivity, cfg) }
                binding.status.text = getString(R.string.msg_new_chat)
            }
        } else {
            binding.status.text = getString(R.string.msg_new_chat)
        }
    }

    companion object {
        /** Message pre-rempli, transmis par l'editeur de code. */
        const val EXTRA_MESSAGE = "message_initial"

        /** Combien de fois de suite l'IA peut demander a lire des fichiers pour une question. */
        private const val MAX_TOURS_LECTURE = 3

        private const val CLE_NOUVELLE_SESSION = "nouvelle_session"
        private const val CLE_NOUVEAU_PROJET = "nouveau_projet"
        private const val CLE_NOUVEAU_FICHIER = "nouveau_fichier"
        private const val CLE_NOUVEAU_DOSSIER = "nouveau_dossier"
        private const val CLE_STUDIO = "studio"
        private const val CLE_OUVRIR_DOSSIER = "ouvrir_dossier"
        private const val CLE_SUPPRIMER_SESSIONS = "supprimer_sessions"
        private const val CLE_SUPPRIMER_PROJETS = "supprimer_projets"
    }
}
