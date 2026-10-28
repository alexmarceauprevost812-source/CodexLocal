package com.codexlocal

import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.chat.ImagesChat
import com.codexlocal.databinding.ItemMessageAssistantBinding
import com.codexlocal.databinding.ItemMessageUserBinding

class MessageAdapter(
    private val items: List<Message>,
    private val onCopy: (Message) -> Unit,
    private val onSaveFile: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    /** Appelé depuis MainActivity quand le thème Chat/Codex change. */
    var chatMode: Boolean = false

    override fun getItemViewType(position: Int) = if (items[position].isUser) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantHolder(ItemMessageAssistantBinding.inflate(inflater, parent, false)).also {
                // Liens cliquables dans les réponses (le texte reste sélectionnable).
                it.binding.bubble.movementMethod = LinkMovementMethod.getInstance()
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserHolder -> {
                holder.binding.bubble.text = msg.content
                holder.binding.bubble.setBackgroundResource(
                    if (chatMode) R.drawable.bg_user_chat else R.drawable.bg_user
                )
            }
            is AssistantHolder -> {
                val bulle = holder.binding.bubble
                val taille = (bulle.lineHeight * 1.25f).toInt().coerceAtLeast(24)
                bulle.text = if (msg.content.isEmpty()) {
                    bulle.context.getString(R.string.ia_travaille)
                } else {
                    MarkdownLite.format(msg.content) { source ->
                        ImagesChat.drawable(bulle.resources, source, taille) {
                            // L'image vient d'arriver : on redessine ce message s'il est encore là.
                            val index = items.indexOf(msg)
                            if (index >= 0) notifyItemChanged(index)
                        }
                    }
                }
                holder.binding.bubble.setBackgroundResource(
                    if (chatMode) R.drawable.bg_assistant_chat else R.drawable.bg_assistant
                )
                holder.binding.copy.setTextColor(
                    holder.itemView.context.getColor(
                        if (chatMode) R.color.sky_blue else R.color.orange
                    )
                )
                holder.binding.copy.setOnClickListener { onCopy(msg) }
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
    }
}
