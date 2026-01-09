package com.rmrbranco.galacticcom

import android.graphics.Typeface
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

class InboxAdapter(
    private val onConversationClick: (InboxConversation) -> Unit,
    private val onConversationLongClick: (InboxConversation) -> Unit
) : ListAdapter<InboxConversation, InboxAdapter.ViewHolder>(InboxDiffCallback()) {

    private var selectedConversationId: String? = null

    fun setSelected(id: String?) {
        selectedConversationId = id
        notifyDataSetChanged()
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nicknameTextView: TextView = view.findViewById(R.id.tv_interlocutor_nickname)
        private val lastMessageTextView: TextView = view.findViewById(R.id.tv_last_message)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_conversation_timestamp)
        private val avatarImageView: ImageView = view.findViewById(R.id.iv_avatar)
        val containerView: View = itemView

        // Capture initial typefaces (from theme/xml) to preserve custom font family (Orbitron)
        private val nicknameTypeface = nicknameTextView.typeface
        private val lastMessageTypeface = lastMessageTextView.typeface

        fun bind(
            conversation: InboxConversation,
            isSelected: Boolean,
            onClick: (InboxConversation) -> Unit,
            onLongClick: (InboxConversation) -> Unit
        ) {
            nicknameTextView.text = conversation.otherUserNickname

            val avatar = AlienAvatarGenerator.generate(conversation.otherUserAvatarSeed, 128, 128)
            avatarImageView.setImageBitmap(avatar)

            lastMessageTextView.text = conversation.lastMessage ?: ""

            // Handle Unread Visuals
            if (conversation.hasUnreadMessages) {
                nicknameTextView.setTypeface(nicknameTypeface, Typeface.BOLD)
                lastMessageTextView.setTypeface(lastMessageTypeface, Typeface.BOLD)
            } else {
                nicknameTextView.setTypeface(nicknameTypeface, Typeface.NORMAL)
                lastMessageTextView.setTypeface(lastMessageTypeface, Typeface.NORMAL)
            }

            // Handle Selection Visuals
            if (isSelected) {
                containerView.setBackgroundResource(R.drawable.item_inbox_selected_background)
            } else {
                containerView.setBackgroundResource(R.drawable.item_neon_background)
            }

            itemView.setOnClickListener { onClick(conversation) }
            itemView.setOnLongClickListener { 
                onLongClick(conversation)
                true
            }

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
        val item = getItem(position)
        holder.bind(item, item.conversationId == selectedConversationId, onConversationClick, onConversationLongClick)
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
