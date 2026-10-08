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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codexlocal.databinding.ActivityMainBinding
import com.codexlocal.drawer.DrawerAdapter
import com.codexlocal.drawer.DrawerItem
import com.codexlocal.editor.CodeEditorActivity
import com.codexlocal.project.FichierProjet
import com.codexlocal.project.Langages
import com.codexlocal.project.Projet
import com.codexlocal.project.ProjectStore
import com.codexlocal.session.Session
import com.codexlocal.session.SessionStore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private val adapter = MessageAdapter(messages) { copyToClipboard(it) }
    private var job: Job? = null
    private var lastUiUpdate = 0L
    private var isGenerating = false
    private var modelSessionId: String? = null

    // ----- menu lateral : sessions et projets -----
    private val itemsTiroir = mutableListOf<DrawerItem>()
    private lateinit var tiroirAdapter: DrawerAdapter
    private var session: Session? = null
    private var projetActif: Projet? = null
    private var fichiersProjet: List<FichierProjet> = emptyList()

    private val choisirDossier =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let { ajouterProjet(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applyInsets(binding.root)
        prefs = Prefs(this)

        binding.toolbar.inflateMenu(R.menu.main)
        binding.toolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_new_chat -> {
                    nouvelleSession(); true
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
        setupModeSelector()

        binding.send.setOnClickListener {
            if (job?.isActive == true) stopGeneration() else send()
        }

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
        refreshStatus()
    }

    /** Appele quand l'editeur de code renvoie un fichier a analyser. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.getStringExtra(EXTRA_MESSAGE)?.let { binding.input.setText(it) }
    }

    // ------------------------------------------------------------------ UI

    private fun applyInsets(root: View) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
    }

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
                    "Local : " + File(cfg.localModelPath).name
                }
            else -> cfg.provider.label + " : " + cfg.model
        }
    }

    /** Theme noir/orange pour Codex ; noir/bleu ciel pour Chat. */
    private fun setupModeSelector() {
        binding.modeSelector.check(
            if (prefs.assistantMode == AssistantMode.CODEX) R.id.modeCodex else R.id.modeChat
        )
        binding.modeSelector.addOnButtonCheckedListener { _, checkedId, checked ->
            if (!checked) return@addOnButtonCheckedListener
            val mode = if (checkedId == R.id.modeChat) AssistantMode.CHAT else AssistantMode.CODEX
            changeMode(mode)
        }
        updateModeTheme()
    }

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

    private fun updateModeTheme() {
        val isChat = prefs.assistantMode == AssistantMode.CHAT
        val accent = getColor(if (isChat) R.color.sky_blue else R.color.orange)
        val orange = getColor(R.color.orange)
        val blue = getColor(R.color.sky_blue)
        val black = getColor(R.color.black)

        binding.toolbar.setTitleTextColor(accent)
        binding.status.setTextColor(accent)
        binding.input.backgroundTintList = ColorStateList.valueOf(accent)
        binding.input.hint = getString(
            if (isChat) R.string.hint_input_chat else R.string.hint_input
        )

        binding.modeCodex.backgroundTintList =
            ColorStateList.valueOf(if (isChat) black else orange)
        binding.modeCodex.setTextColor(if (isChat) orange else black)
        binding.modeCodex.strokeColor = ColorStateList.valueOf(orange)

        binding.modeChat.backgroundTintList =
            ColorStateList.valueOf(if (isChat) blue else black)
        binding.modeChat.setTextColor(if (isChat) black else blue)
        binding.modeChat.strokeColor = ColorStateList.valueOf(blue)
        adapter.chatMode = isChat
        adapter.notifyDataSetChanged()
        updateSendIcon()
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
        binding.modeCodex.isEnabled = !on
        binding.modeChat.isEnabled = !on
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

    private fun supprimerSession(id: String) {
        SessionStore.supprimer(this, id)
        if (session?.id == id) {
            session = null
            ouvrirSessionOuCreer()
        }
        rafraichirTiroir()
        Toast.makeText(this, R.string.msg_session_supprimee, Toast.LENGTH_SHORT).show()
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
        binding.titreTiroir.text = projet?.nom ?: getString(R.string.drawer_sous_titre)
        rafraichirTiroir()
        if (projet == null) return

        lifecycleScope.launch {
            val fichiers = withContext(Dispatchers.IO) {
                runCatching { ProjectStore.listerFichiers(this@MainActivity, projet) }
                    .getOrDefault(emptyList())
            }
            if (projetActif?.uri != projet.uri) return@launch
            fichiersProjet = fichiers
            rafraichirTiroir()
            binding.status.text = getString(R.string.msg_projet_ouvert, projet.nom) + " — " +
                getString(R.string.msg_fichiers_trouves, fichiers.size)
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

    private fun supprimerProjet(uri: String) {
        ProjectStore.supprimer(this, uri)
        if (projetActif?.uri == uri) {
            projetActif = null
            fichiersProjet = emptyList()
            binding.titreTiroir.text = getString(R.string.drawer_sous_titre)
        }
        rafraichirTiroir()
        Toast.makeText(this, R.string.msg_projet_supprime, Toast.LENGTH_SHORT).show()
    }

    // ============================================================== tiroir

    private fun rafraichirTiroir() {
        val nouveaux = mutableListOf<DrawerItem>()

        nouveaux.add(
            DrawerItem.Section(getString(R.string.drawer_sessions), "＋", CLE_NOUVELLE_SESSION)
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
            DrawerItem.Section(getString(R.string.drawer_projets), "＋", CLE_NOUVEAU_PROJET)
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
                DrawerItem.Section(getString(R.string.drawer_fichiers) + " — " + projet.nom)
            )
            if (fichiersProjet.isEmpty()) {
                nouveaux.add(DrawerItem.Info(getString(R.string.drawer_chargement)))
            } else {
                fichiersProjet.forEach {
                    nouveaux.add(
                        DrawerItem.LigneFichier(it.nom, it.chemin, it.uri, Langages.pour(it.nom))
                    )
                }
            }
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
            cle == CLE_OUVRIR_DOSSIER -> choisirDossier.launch(null)
            item is DrawerItem.LigneSession -> supprimerSession(item.id)
            item is DrawerItem.LigneProjet -> supprimerProjet(item.uri)
        }
    }

    private fun send() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        val cfg = prefs.config().forMode(prefs.assistantMode)

        binding.input.setText("")
        messages.add(Message.user(text))
        val assistant = Message.assistant()
        messages.add(assistant)
        adapter.notifyItemRangeInserted(messages.size - 2, 2)
        updateEmptyState()
        scrollToEnd()
        setGenerating(true)

        job = lifecycleScope.launch {
            try {
                if (cfg.provider == ProviderType.LOCAL) {
                    if (cfg.localModelPath.isBlank()) {
                        binding.status.text = getString(R.string.msg_no_model_loaded)
                        return@launch
                    }
                    // Le chargement ajuste aussi le prompt lors d'un passage Codex/Chat.
                    val alreadyLoaded = LocalEngine.isLoadedFor(cfg.localModelPath)
                    if (!alreadyLoaded) binding.status.text = getString(R.string.msg_loading_model)
                    LocalEngine.load(this@MainActivity, cfg.localModelPath, cfg) { msg ->
                        runOnUiThread { binding.status.text = msg }
                    }
                    if (modelSessionId != session?.id) {
                        LocalEngine.resetConversation(this@MainActivity, cfg)
                        modelSessionId = session?.id
                    }
                    if (!alreadyLoaded) {
                        binding.status.text =
                            getString(R.string.msg_model_loaded, File(cfg.localModelPath).name)
                    }
                    LocalEngine.applySampling(cfg)
                }

                val history = messages.dropLast(1)
                ChatRepository.stream(history, cfg).collect { chunk ->
                    assistant.content += chunk
                    val now = System.currentTimeMillis()
                    if (now - lastUiUpdate > 60) {
                        lastUiUpdate = now
                        adapter.notifyItemChanged(messages.size - 1)
                        scrollToEnd()
                    }
                }
                adapter.notifyItemChanged(messages.size - 1)
                scrollToEnd()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message ?: e.toString()
                binding.status.text = getString(R.string.msg_error, message)
                if (assistant.content.isEmpty()) {
                    assistant.content = "[erreur] $message"
                    adapter.notifyItemChanged(messages.size - 1)
                }
            } finally {
                setGenerating(false)
                sauverSession()
                rafraichirTiroir()
            }
        }
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

        private const val CLE_NOUVELLE_SESSION = "nouvelle_session"
        private const val CLE_NOUVEAU_PROJET = "nouveau_projet"
        private const val CLE_OUVRIR_DOSSIER = "ouvrir_dossier"
    }
}
