package com.codexlocal

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import android.view.animation.OvershootInterpolator
import com.codexlocal.chat.ImagesChat
import com.codexlocal.chat.ProjectionTexte
import com.codexlocal.databinding.ItemMessageAssistantBinding
import com.codexlocal.databinding.ItemMessageUserBinding

class MessageAdapter(
    private val items: List<Message>,
    private val onCopy: (Message) -> Unit,
    private val onSaveFile: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Appelé depuis MainActivity quand le thème Chat/Codex change. */
    var chatMode: Boolean = false

    /** L'IA écrit la dernière bulle (APK perso : effet projection or → blanc). */
    var enCours: Boolean = false
        set(v) { field = v; if (!v) projection.reinitialiser() }

    private val projection = ProjectionTexte()
    // Par identité : Message est une data class dont le contenu change pendant l'écriture.
    private val animes: MutableSet<Message> = java.util.Collections.newSetFromMap(java.util.IdentityHashMap())

    /** Durée (ms) du dernier rendu de la dernière réponse (Markdown + couleurs), sur le fil d'affichage. */
    var coutRenduMs: Long = 0L
        private set

    /** APK perso : les nouvelles bulles glissent et rebondissent à leur arrivée. */
    private fun animerEntree(vue: android.view.View, msg: Message) {
        if (!BuildConfig.PERSO || !animes.add(msg)) return
        val d = vue.resources.displayMetrics.density
        vue.alpha = 0f
        vue.translationX = (if (msg.isUser) 60f else -60f) * d
        vue.scaleX = 0.92f; vue.scaleY = 0.92f
        vue.animate().alpha(1f).translationX(0f).scaleX(1f).scaleY(1f)
            .setDuration(420).setInterpolator(OvershootInterpolator(1.4f)).start()
    }

    override fun getItemViewType(position: Int) = if (items[position].isUser) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantHolder(ItemMessageAssistantBinding.inflate(inflater, parent, false)).also {
                // Liens cliquables dans les réponses (le texte reste sélectionnable).
                it.binding.bubble.movementMethod = LinkMovementMethod.getInstance()
                if (BuildConfig.PERSO) {
                    // 🔊 Lire la réponse à voix haute (voix québécoise).
                    val rangee = it.binding.copy.parent as ViewGroup
                    val lire = android.widget.Button(parent.context, null, android.R.attr.borderlessButtonStyle).apply {
                        text = "🔊 Lire"
                        isAllCaps = false
                        textSize = 13f
                        tag = TAG_LIRE
                    }
                    rangee.addView(lire, 0)
                }
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserHolder -> {
                animerEntree(holder.itemView, msg)
                holder.binding.bubble.text = msg.content
                holder.binding.bubble.setBackgroundResource(
                    if (chatMode) R.drawable.bg_user_chat else R.drawable.bg_user
                )
            }
            is AssistantHolder -> {
                val debutRendu = System.nanoTime()
                val bulle = holder.binding.bubble
                animerEntree(holder.itemView, msg)
                val taille = (bulle.lineHeight * 1.25f).toInt().coerceAtLeast(24)
                bulle.text = if (msg.content.isEmpty()) {
                    bulle.context.getString(R.string.ia_travaille)
                } else {
                    // Hors ligne (Recherche Internet décochée) : aucune image n'est téléchargée.
                    val enLigne = Prefs(bulle.context).rechercheWebAuto
                    MarkdownLite.format(msg.content) { source ->
                        if (!enLigne) return@format null
                        ImagesChat.drawable(bulle.resources, source, taille) {
                            // L'image vient d'arriver : on redessine ce message s'il est encore là.
                            val index = items.indexOf(msg)
                            if (index >= 0) notifyItemChanged(index)
                        }
                    }
                }
                if (BuildConfig.PERSO && enCours && msg === items.lastOrNull() && msg.content.isNotEmpty()) {
                    // Le nouveau texte arrive en or lumineux, puis redevient blanc.
                    projection.appliquer(msg, bulle.text)
                    projection.animer(bulle)
                }
                // Coût du dernier rendu de la réponse en cours : règle le rythme de rafraîchissement.
                if (msg === items.lastOrNull()) coutRenduMs = (System.nanoTime() - debutRendu) / 1_000_000
                holder.binding.bubble.setBackgroundResource(
                    if (chatMode) R.drawable.bg_assistant_chat else R.drawable.bg_assistant
                )
                holder.binding.copy.setTextColor(
                    holder.itemView.context.getColor(
                        if (chatMode) R.color.sky_blue else R.color.orange
                    )
                )
                holder.binding.copy.setOnClickListener { onCopy(msg) }
                (holder.binding.copy.parent as ViewGroup).findViewWithTag<android.widget.Button>(TAG_LIRE)?.apply {
                    setTextColor(context.getColor(if (chatMode) R.color.sky_blue else R.color.orange))
                    isEnabled = msg.content.isNotBlank()
                    setOnClickListener {
                        if (com.codexlocal.chat.LecteurVoix.parle()) com.codexlocal.chat.LecteurVoix.arreter()
                        else com.codexlocal.chat.LecteurVoix.lire(context, msg.content)
                    }
                }
                holder.binding.btnSaveFile.setTextColor(
                    holder.itemView.context.getColor(
                        if (chatMode) R.color.sky_blue else R.color.orange
                    )
                )
                holder.binding.btnSaveFile.isEnabled = msg.content.isNotBlank()
                holder.binding.btnSaveFile.setOnClickListener { onSaveFile(msg) }
            }
        }
    }

    override fun getItemCount() = items.size

    class UserHolder(val binding: ItemMessageUserBinding) : RecyclerView.ViewHolder(binding.root)

    class AssistantHolder(val binding: ItemMessageAssistantBinding) :
        RecyclerView.ViewHolder(binding.root)

    private companion object {
        const val TYPE_USER = 0
        const val TYPE_ASSISTANT = 1
        const val TAG_LIRE = "lire_voix"
    }
}
