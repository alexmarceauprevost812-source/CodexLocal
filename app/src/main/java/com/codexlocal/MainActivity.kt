package com.codexlocal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.codexlocal.databinding.ActivityMainBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: Prefs

    private val messages = mutableListOf<Message>()
    private val adapter = MessageAdapter(messages) { copyToClipboard(it) }
    private var job: Job? = null
    private var lastUiUpdate = 0L

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
                    newChat(); true
                }
                R.id.action_settings -> {
                    openSettings(); true
                }
                else -> false
            }
        }

        binding.messages.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        binding.messages.adapter = adapter

        binding.send.setOnClickListener {
            if (job?.isActive == true) stopGeneration() else send()
        }

        // Premier lancement : on ouvre directement les reglages
        if (savedInstanceState == null) {
            val cfg = prefs.config()
            if (cfg.provider == ProviderType.LOCAL && cfg.localModelPath.isBlank()) openSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
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

    private fun setGenerating(on: Boolean) {
        binding.send.setImageResource(
            if (on) R.drawable.ic_stop_circle else R.drawable.ic_send_circle
        )
        binding.input.isEnabled = !on
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

    private fun send() {
        val text = binding.input.text.toString().trim()
        if (text.isEmpty()) return
        val cfg = prefs.config()

        binding.input.setText("")
        messages.add(Message.user(text))
        val assistant = Message.assistant()
        messages.add(assistant)
        adapter.notifyItemRangeInserted(messages.size - 2, 2)
        scrollToEnd()
        setGenerating(true)

        job = lifecycleScope.launch {
            try {
                if (cfg.provider == ProviderType.LOCAL) {
                    if (cfg.localModelPath.isBlank()) {
                        binding.status.text = getString(R.string.msg_no_model_loaded)
                        return@launch
                    }
                    if (!LocalEngine.isLoadedFor(cfg.localModelPath)) {
                        binding.status.text = getString(R.string.msg_loading_model)
                        LocalEngine.load(this@MainActivity, cfg.localModelPath, cfg) { msg ->
                            runOnUiThread { binding.status.text = msg }
                        }
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
}
