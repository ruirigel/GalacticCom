package com.rmrbranco.galacticcom

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class InboxAdapter(
    private val onConversationClick: (InboxConversation) -> Unit,
    private val onDeleteClick: (InboxConversation) -> Unit
) : ListAdapter<InboxConversation, InboxAdapter.ViewHolder>(InboxDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nicknameTextView: TextView = view.findViewById(R.id.tv_interlocutor_nickname)
        private val lastMessageTextView: TextView = view.findViewById(R.id.tv_last_message)
        private val deleteButton: Button = view.findViewById(R.id.btn_delete_conversation)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_conversation_timestamp)
        private val avatarImageView: ImageView = view.findViewById(R.id.iv_avatar)

        fun bind(
            conversation: InboxConversation,
            onClick: (InboxConversation) -> Unit,
            onDelete: (InboxConversation) -> Unit
        ) {
            nicknameTextView.text = conversation.otherUserNickname

            val avatar = AlienAvatarGenerator.generate(conversation.otherUserAvatarSeed, 128, 128)
            avatarImageView.setImageBitmap(avatar)

            lastMessageTextView.text = conversation.lastMessage ?: ""

            itemView.setOnClickListener { onClick(conversation) }
            deleteButton.setOnClickListener { onDelete(conversation) }

            val timestamp = conversation.lastMessageTimestamp
            if (timestamp != null) {
                timestampTextView.text = TimeUtils.getRelativeTimeSpanString(timestamp)
            } else {
                timestampTextView.text = ""
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_inbox_conversation, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position), onConversationClick, onDeleteClick)
    }
}

class InboxDiffCallback : DiffUtil.ItemCallback<InboxConversation>() {
    override fun areItemsTheSame(oldItem: InboxConversation, newItem: InboxConversation): Boolean {
        return oldItem.conversationId == newItem.conversationId
    }

    override fun areContentsTheSame(oldItem: InboxConversation, newItem: InboxConversation): Boolean {
        return oldItem == newItem
    }
}
