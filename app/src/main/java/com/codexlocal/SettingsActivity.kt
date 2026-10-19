package com.codexlocal

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.codexlocal.databinding.ActivitySettingsBinding
import com.codexlocal.usage.CompteurUsage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

class SettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: Prefs

    private var modelFiles: List<File> = emptyList()
    private var editingProvider: ProviderType = ProviderType.LOCAL
    private var busyJob: Job? = null
    private lateinit var compteur: CompteurUsage

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importModel(it) }
        }

    private val pickFond =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri == null) return@registerForActivityResult
            lifecycleScope.launch {
                try {
                    withContext(Dispatchers.IO) { FondEcran.enregistrer(this@SettingsActivity, uri) }
                    Toast.makeText(this@SettingsActivity, R.string.fond_applique, Toast.LENGTH_SHORT).show()
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Toast.makeText(
                        this@SettingsActivity,
                        getString(R.string.fond_erreur, e.message ?: ""),
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

    private val pickUpdate =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) lifecycleScope.launch {
                try {
                    val apk = Updater.importer(this@SettingsActivity, uri)
                    val launched = Updater.installer(this@SettingsActivity, apk)
                    binding.tvLocalStatus.text = if (launched) "Installation lancée…"
                        else getString(R.string.update_permission)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    binding.tvLocalStatus.text = "Installation impossible : ${e.message}"
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        prefs = Prefs(this)
        compteur = CompteurUsage(this)

        binding.tvLocalDir.text = getString(R.string.local_dir_hint) +
            "\n" + ModelStore.modelsDir(this).absolutePath

        setupCommon()
        setupFond()
        setupLocal()
        setupCatalog()
        setupProvider()
        binding.btnUsageZero.setOnClickListener {
            compteur.remettreAZero(editingProvider)
            Toast.makeText(this, R.string.usage_zero_fait, Toast.LENGTH_SHORT).show()
            afficherUsage(editingProvider)
        }

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
        Updater.reprendreInstallation(this)
        refreshModels()
    }

    // ------------------------------------------------------------ commun

    private fun setupCommon() {
        binding.sbTemperature.progress = (prefs.temperature * 10).toInt().coerceIn(0, 20)
        updateTempLabel()
        binding.sbTemperature.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    updateTempLabel()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
        )
        binding.etMaxTokens.setText(prefs.maxTokens.toString())
        binding.etSystemPrompt.setText(prefs.systemPrompt)
    }

    private fun currentTemp(): Float = binding.sbTemperature.progress / 10f

    private fun updateTempLabel() {
        binding.tvTemperature.text =
            getString(R.string.label_temperature) + " : " +
            String.format(Locale.US, "%.1f", currentTemp())
    }

    // -------------------------------------------------------- fournisseur

    private fun setupProvider() {
        editingProvider = prefs.provider
        when (editingProvider) {
            ProviderType.LOCAL -> binding.rbLocal.isChecked = true
            ProviderType.OPENAI -> binding.rbOpenai.isChecked = true
            ProviderType.ANTHROPIC -> binding.rbAnthropic.isChecked = true
            ProviderType.GEMINI -> binding.rbGemini.isChecked = true
        }
        loadRemoteFields(editingProvider)
        afficherUsage(editingProvider)

        binding.rgProvider.setOnCheckedChangeListener { _, checkedId ->
            val next = when (checkedId) {
                R.id.rbOpenai -> ProviderType.OPENAI
                R.id.rbAnthropic -> ProviderType.ANTHROPIC
                R.id.rbGemini -> ProviderType.GEMINI
                else -> ProviderType.LOCAL
            }
            if (next == editingProvider) return@setOnCheckedChangeListener
            saveRemoteFields(editingProvider)
            editingProvider = next
            loadRemoteFields(next)
            afficherUsage(next)
            updateSections()
        }
        updateSections()
    }

    private fun loadRemoteFields(p: ProviderType) {
        binding.etBaseUrl.setText(prefs.baseUrl(p))
        binding.etModel.setText(prefs.model(p))
        binding.etApiKey.setText(prefs.apiKey(p))
    }

    private fun saveRemoteFields(p: ProviderType) {
        prefs.setBaseUrl(p, binding.etBaseUrl.text.toString().trim())
        prefs.setModel(p, binding.etModel.text.toString().trim())
        prefs.setApiKey(p, binding.etApiKey.text.toString().trim())
        if (p != ProviderType.LOCAL) {
            compteur.definirLimites(
                p,
                binding.etLimiteJour.text.toString().trim().toLongOrNull() ?: 0L,
                binding.etLimiteMois.text.toString().trim().toLongOrNull() ?: 0L
            )
        }
    }

    /** Compteur et limites du fournisseur affiche (rien a surveiller en local). */
    private fun afficherUsage(p: ProviderType) {
        val local = p == ProviderType.LOCAL
        listOf<View>(
            binding.tvUsageTitre, binding.pbUsage, binding.etLimiteJour, binding.etLimiteMois,
            binding.tvUsageNote, binding.btnUsageZero
        ).forEach { it.visibility = if (local) View.GONE else View.VISIBLE }
        if (local) {
            binding.tvUsage.text = getString(R.string.usage_local)
            return
        }
        val e = compteur.etat(p)
        binding.tvUsage.text = getString(
            R.string.usage_resume,
            CompteurUsage.nombre(e.jour.jetons), e.jour.requetes.toInt(),
            CompteurUsage.nombre(e.mois.jetons), e.mois.requetes.toInt()
        )
        binding.etLimiteJour.setText(if (e.limiteJour > 0) e.limiteJour.toString() else "")
        binding.etLimiteMois.setText(if (e.limiteMois > 0) e.limiteMois.toString() else "")
        val pourcent = e.pourcentMax
        binding.pbUsage.visibility = if (pourcent == null) View.GONE else View.VISIBLE
        binding.pbUsage.progress = (pourcent ?: 0).coerceAtMost(100)
    }

    private fun updateSections() {
        val isLocal = editingProvider == ProviderType.LOCAL
        listOf<View>(binding.etBaseUrl, binding.etModel, binding.etApiKey).forEach {
            it.isEnabled = !isLocal
            it.alpha = if (isLocal) 0.4f else 1f
        }
        listOf<View>(
            binding.spModels, binding.spCatalog, binding.btnLoad, binding.btnUnload,
            binding.btnImport, binding.btnDownload, binding.btnRefresh, binding.etDownloadUrl
        ).forEach {
            it.isEnabled = isLocal
            it.alpha = if (isLocal) 1f else 0.4f
        }
    }

    // ---------------------------------------------------------- fond d'ecran

    private fun setupFond() {
        binding.btnFondChoisir.setOnClickListener { pickFond.launch("image/*") }
        binding.btnFondDefaut.setOnClickListener {
            FondEcran.reinitialiser(this)
            Toast.makeText(this, R.string.fond_applique, Toast.LENGTH_SHORT).show()
        }
        binding.slFondOpacite.value = prefs.fondOpacite.toFloat()
        binding.tvFondOpacite.text = getString(R.string.fond_visibilite, prefs.fondOpacite)
        binding.slFondOpacite.addOnChangeListener { _, valeur, _ ->
            val pourcent = valeur.toInt()
            prefs.fondOpacite = pourcent
            binding.tvFondOpacite.text = getString(R.string.fond_visibilite, pourcent)
        }
    }

    // --------------------------------------------------------------- local

    private fun setupLocal() {
        binding.btnRefresh.setOnClickListener { refreshModels() }
        binding.btnImport.setOnClickListener { pickModel.launch(arrayOf("*/*")) }
        binding.btnDownload.setOnClickListener { downloadModel() }
        binding.btnLoad.setOnClickListener { loadModel() }
        binding.btnUnload.setOnClickListener {
            LocalEngine.unload()
            Toast.makeText(this, "Modèle déchargé", Toast.LENGTH_SHORT).show()
            refreshModels()
        }

        binding.swAutoUpdate.isChecked = prefs.autoUpdate
        binding.swAutoUpdate.setOnCheckedChangeListener { _, coche -> prefs.autoUpdate = coche }
        binding.btnCheckUpdate.setOnClickListener {
            UpdateFlow.verifier(lifecycleScope, this, silencieux = false) { message ->
                runOnUiThread { binding.tvLocalStatus.text = message }
            }
        }
        binding.btnInstallApk.setOnClickListener { pickUpdate.launch(arrayOf("*/*")) }
    }

    /** Liste des modeles telechargeables en un clic. */
    private fun setupCatalog() {
        binding.spCatalog.adapter =
            ArrayAdapter(this, R.layout.item_spinner, ModelCatalog.libelles)
        binding.spCatalog.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                binding.etDownloadUrl.visibility =
                    if (position == ModelCatalog.indexAutre) View.VISIBLE else View.GONE
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    private fun refreshModels() {
        modelFiles = ModelStore.list(this)
        val names = if (modelFiles.isEmpty()) {
            listOf("(aucun fichier .gguf dans le dossier)")
        } else {
            modelFiles.map { it.name }
        }
        binding.spModels.adapter = ArrayAdapter(this, R.layout.item_spinner, names)

        val current = prefs.localModelPath
        val index = modelFiles.indexOfFirst { it.absolutePath == current }
        if (index >= 0) binding.spModels.setSelection(index)

        val loaded = LocalEngine.loadedPath
        binding.tvLocalStatus.text = if (loaded != null) {
            getString(R.string.msg_model_loaded, File(loaded).name)
        } else {
            getString(R.string.msg_no_model_loaded)
        }
    }

    private fun loadModel() {
        val index = binding.spModels.selectedItemPosition
        if (modelFiles.isEmpty() || index !in modelFiles.indices) {
            Toast.makeText(this, R.string.msg_choose_model, Toast.LENGTH_SHORT).show()
            return
        }
        val file = modelFiles[index]
        prefs.localModelPath = file.absolutePath
        prefs.provider = ProviderType.LOCAL
        binding.rbLocal.isChecked = true
        editingProvider = ProviderType.LOCAL
        binding.tvLocalStatus.text = getString(R.string.msg_loading_model)

        busyJob = lifecycleScope.launch {
            try {
                LocalEngine.load(this@SettingsActivity, file.absolutePath, prefs.config()) { msg ->
                    runOnUiThread { binding.tvLocalStatus.text = msg }
                }
                binding.tvLocalStatus.text = getString(R.string.msg_model_loaded, file.name)
            } catch (e: Exception) {
                binding.tvLocalStatus.text =
                    getString(R.string.msg_error, e.message ?: e.toString())
            }
        }
    }

    private fun importModel(uri: Uri) {
        val name = displayName(uri)
        busyJob = lifecycleScope.launch {
            try {
                binding.tvLocalStatus.text = "Copie de $name…"
                val dest = withContext(Dispatchers.IO) {
                    ModelStore.importFromUri(this@SettingsActivity, uri, name) { done ->
                        runOnUiThread {
                            binding.tvLocalStatus.text = "Copie : ${done / 1_048_576} Mo"
                        }
                    }
                }
                prefs.localModelPath = dest.absolutePath
                refreshModels()
                binding.tvLocalStatus.text = "Importé : ${dest.name} — chargement…"
                chargerLeModele(dest)
            } catch (e: Exception) {
                binding.tvLocalStatus.text =
                    getString(R.string.msg_error, e.message ?: e.toString())
            }
        }
    }

    private fun downloadModel() {
        val position = binding.spCatalog.selectedItemPosition
        val url: String
        val name: String

        if (position == ModelCatalog.indexAutre) {
            val saisie = binding.etDownloadUrl.text.toString().trim()
            if (!saisie.startsWith("http")) {
                Toast.makeText(this, "URL invalide", Toast.LENGTH_SHORT).show()
                return
            }
            url = saisie
            name = saisie.substringAfterLast('/').substringBefore('?').ifBlank { "modele.gguf" }
        } else {
            val modele = ModelCatalog.modeles[position]
            url = modele.url
            name = modele.fichier
        }

        binding.btnDownload.isEnabled = false

        busyJob = lifecycleScope.launch {
            try {
                val dest = File(ModelStore.modelsDir(this@SettingsActivity), name)
                withContext(Dispatchers.IO) {
                    ModelStore.download(url, dest) { done, total ->
                        val text = if (total > 0) {
                            "${done / 1_048_576} / ${total / 1_048_576} Mo"
                        } else {
                            "${done / 1_048_576} Mo"
                        }
                        runOnUiThread { binding.tvLocalStatus.text = "Téléchargement : $text" }
                    }
                }
                prefs.localModelPath = dest.absolutePath
                refreshModels()
                binding.tvLocalStatus.text = "Téléchargé : ${dest.name} — chargement…"
                chargerLeModele(dest)
            } catch (e: Exception) {
                binding.tvLocalStatus.text =
                    getString(R.string.msg_error, e.message ?: e.toString())
            } finally {
                binding.btnDownload.isEnabled = true
            }
        }
    }

    /** Charge le modele juste apres son installation : un clic et c'est pret. */
    private suspend fun chargerLeModele(fichier: File) {
        try {
            LocalEngine.load(this, fichier.absolutePath, prefs.config()) { msg ->
                runOnUiThread { binding.tvLocalStatus.text = msg }
            }
            binding.tvLocalStatus.text = getString(R.string.msg_model_loaded, fichier.name)
        } catch (e: Exception) {
            binding.tvLocalStatus.text =
                getString(R.string.msg_error, e.message ?: e.toString())
        }
    }

    private fun displayName(uri: Uri): String {
        runCatching {
            contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && cursor.moveToFirst()) {
                    val value = cursor.getString(index)
                    if (!value.isNullOrBlank()) return value
                }
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "modele.gguf"
    }

    // -------------------------------------------------------------- save

    private fun save() {
        saveRemoteFields(editingProvider)
        prefs.provider = editingProvider
        prefs.temperature = currentTemp()
        prefs.maxTokens = binding.etMaxTokens.text.toString().trim()
            .toIntOrNull()?.coerceIn(64, 8192) ?: 1024
        prefs.systemPrompt = binding.etSystemPrompt.text.toString().ifBlank { DEFAULT_SYSTEM_PROMPT }
        Toast.makeText(this, "Enregistré", Toast.LENGTH_SHORT).show()
        finish()
    }
}
