package com.rmrbranco.galacticcom

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import java.util.concurrent.TimeUnit

class PublicMessageAdapter(
    private val onMessageClick: (PublicMessage) -> Unit,
    private val onDeleteClick: (PublicMessage) -> Unit,
    private val onViewClick: (PublicMessage) -> Unit,
    private val onLongClick: (PublicMessage) -> Unit 
) : ListAdapter<PublicMessage, PublicMessageAdapter.ViewHolder>(PublicMessageDiffCallback()) {

    private val selectedMessageIds = mutableSetOf<String>()

    fun toggleSelection(id: String) {
        if (selectedMessageIds.contains(id)) {
            selectedMessageIds.remove(id)
        } else {
            selectedMessageIds.add(id)
        }
        notifyDataSetChanged()
    }

    fun clearSelection() {
        selectedMessageIds.clear()
        notifyDataSetChanged()
    }

    fun getSelectedCount(): Int = selectedMessageIds.size

    fun getSelectedIds(): List<String> = selectedMessageIds.toList()

    fun isSelected(id: String): Boolean = selectedMessageIds.contains(id)
    
    // Kept for compatibility if HomeFragment still calls setSelected(null) for single item, 
    // but effectively we should use clearSelection()
    fun setSelected(id: String?) {
        if (id == null) clearSelection()
        else toggleSelection(id)
    }

    private fun textToBinary(text: String): String {
        return text.map { char ->
            Integer.toBinaryString(char.code).padStart(8, '0')
        }.joinToString(" ")
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val catastropheWarningTextView: TextView = view.findViewById(R.id.tv_catastrophe_warning)
        private val messageContent: TextView = view.findViewById(R.id.tv_message_content)
        private val messageSender: TextView = view.findViewById(R.id.tv_message_sender)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_public_message_timestamp)
        val countdownTextView: TextView = view.findViewById(R.id.tv_countdown)
        private val messageLabel: TextView = view.findViewById(R.id.tv_message_label)

        fun bind(message: PublicMessage, isSelected: Boolean) {
            if (message.catastropheType != null) {
                catastropheWarningTextView.text = message.catastropheType
                catastropheWarningTextView.visibility = View.VISIBLE
            } else {
                catastropheWarningTextView.visibility = View.GONE
            }

            val hasArrived = message.arrivalTime == 0L || System.currentTimeMillis() >= message.arrivalTime

            // Update Label to "Subject:"
            messageLabel.text = "Subject:"

            // Display content (encrypted binary)
            val contentToDisplay = message.message ?: ""

            // Limit to 13 chars
            val maxLength = 13
            val displayContent = if (contentToDisplay.length > maxLength) {
                contentToDisplay.take(maxLength) + "..."
            } else {
                contentToDisplay
            }

            messageContent.text = displayContent

            val whiteColor = ContextCompat.getColor(itemView.context, android.R.color.white)
            // Format: "From: Galaxy by: Nickname"
            val senderPrefix = "From: "
            val fromSeparator = " by: "
            val nickname = message.senderNickname ?: "Unknown"
            val galaxy = message.senderGalaxy ?: "Unknown"
            
            val senderText = "$senderPrefix$galaxy$fromSeparator$nickname"
            val senderSpannable = SpannableString(senderText)
            
            val galaxyStart = senderPrefix.length
            val galaxyEnd = galaxyStart + galaxy.length
            senderSpannable.setSpan(ForegroundColorSpan(whiteColor), galaxyStart, galaxyEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

            val nicknameStart = galaxyEnd + fromSeparator.length
            val nicknameEnd = nicknameStart + nickname.length
            if (nicknameStart < senderText.length) {
                senderSpannable.setSpan(ForegroundColorSpan(whiteColor), nicknameStart, nicknameEnd.coerceAtMost(senderText.length), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }

            messageSender.text = senderSpannable

            val timestamp = message.timestamp as? Long
            if (timestamp != null) {
                timestampTextView.text = TimeUtils.getRelativeTimeSpanString(timestamp)
            } else {
                timestampTextView.text = ""
            }

            if (message.senderId == FirebaseAuth.getInstance().currentUser?.uid) {
                countdownTextView.visibility = View.GONE
            } else if (hasArrived) {
                countdownTextView.visibility = View.VISIBLE // Changed to VISIBLE
                countdownTextView.text = "ARRIVED" // Set text
            } else {
                countdownTextView.visibility = View.VISIBLE

                val timeRemaining = message.arrivalTime - System.currentTimeMillis()
                val hours = TimeUnit.MILLISECONDS.toHours(timeRemaining)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60
                countdownTextView.text = String.format("Arriving in: %02d:%02d", hours, minutes).uppercase()
            }
            
            // Interaction logic
            itemView.setOnClickListener { onViewClick(message) }
            itemView.setOnLongClickListener { 
                onLongClick(message) 
                true 
            }
            
            // Handle Selection Visuals
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.item_inbox_selected_background)
            } else {
                itemView.setBackgroundResource(R.drawable.item_neon_background)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_public_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        // Access selectedMessageIds from the outer class
        holder.bind(item, selectedMessageIds.contains(item.messageId))
    }
}

class PublicMessageDiffCallback : DiffUtil.ItemCallback<PublicMessage>() {
    override fun areItemsTheSame(oldItem: PublicMessage, newItem: PublicMessage): Boolean {
        return oldItem.messageId == newItem.messageId
    }

    override fun areContentsTheSame(oldItem: PublicMessage, newItem: PublicMessage): Boolean {
        return oldItem == newItem
    }
}
