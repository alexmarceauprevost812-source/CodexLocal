package com.codexlocal

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.codexlocal.databinding.ItemMessageAssistantBinding
import com.codexlocal.databinding.ItemMessageUserBinding

class MessageAdapter(
    private val items: List<Message>,
    private val onCopy: (Message) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun getItemViewType(position: Int) = if (items[position].isUser) TYPE_USER else TYPE_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_USER) {
            UserHolder(ItemMessageUserBinding.inflate(inflater, parent, false))
        } else {
            AssistantHolder(ItemMessageAssistantBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = items[position]
        when (holder) {
            is UserHolder -> holder.binding.bubble.text = msg.content
            is AssistantHolder -> {
                holder.binding.bubble.text =
                    if (msg.content.isEmpty()) "…" else MarkdownLite.format(msg.content)
                holder.binding.copy.setOnClickListener { onCopy(msg) }
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
