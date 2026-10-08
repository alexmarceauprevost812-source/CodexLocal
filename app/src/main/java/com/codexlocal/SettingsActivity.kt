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

    private val pickModel =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { importModel(it) }
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

        binding.tvLocalDir.text = getString(R.string.local_dir_hint) +
            "\n" + ModelStore.modelsDir(this).absolutePath

        setupCommon()
        setupLocal()
        setupCatalog()
        setupProvider()

        binding.btnSave.setOnClickListener { save() }
        binding.btnCancel.setOnClickListener { finish() }
    }

    override fun onResume() {
        super.onResume()
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
