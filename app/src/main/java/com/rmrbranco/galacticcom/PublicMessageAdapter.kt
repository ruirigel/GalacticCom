package com.rmrbranco.galacticcom

import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
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
    private val onLongClick: (PublicMessage) -> Unit // Added long click listener
) : ListAdapter<PublicMessage, PublicMessageAdapter.ViewHolder>(PublicMessageDiffCallback()) {

    private var selectedMessageId: String? = null

    fun setSelected(id: String?) {
        selectedMessageId = id
        notifyDataSetChanged()
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
        val replyButton: Button = view.findViewById(R.id.btn_reply)
        private val buttonContainer: LinearLayout = view.findViewById(R.id.button_container)

        fun bind(message: PublicMessage, isSelected: Boolean) {
            if (message.catastropheType != null) {
                catastropheWarningTextView.text = message.catastropheType
                catastropheWarningTextView.visibility = View.VISIBLE
            } else {
                catastropheWarningTextView.visibility = View.GONE
            }

            val hasArrived = message.arrivalTime == 0L || System.currentTimeMillis() >= message.arrivalTime

            val contentToDisplay = if (hasArrived) {
                message.message ?: ""
            } else {
                message.message ?: ""
            }

            val maxLength = 35
            val displayContent = if (contentToDisplay.length > maxLength) {
                contentToDisplay.take(maxLength) + "..."
            } else {
                contentToDisplay
            }

            messageContent.text = displayContent

            val whiteColor = ContextCompat.getColor(itemView.context, android.R.color.white)
            val senderPrefix = "by: "
            val fromSeparator = " from "
            val nickname = message.senderNickname ?: "Unknown"
            val galaxy = message.senderGalaxy ?: "Unknown"

            val senderText = "$senderPrefix$nickname$fromSeparator$galaxy"
            val senderSpannable = SpannableString(senderText)

            val nicknameStart = senderPrefix.length
            val nicknameEnd = nicknameStart + nickname.length
            senderSpannable.setSpan(ForegroundColorSpan(whiteColor), nicknameStart, nicknameEnd, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

            val galaxyStart = nicknameEnd + fromSeparator.length
            val galaxyEnd = galaxyStart + galaxy.length
            if (galaxyStart < senderText.length) {
                senderSpannable.setSpan(ForegroundColorSpan(whiteColor), galaxyStart, galaxyEnd.coerceAtMost(senderText.length), SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
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
                buttonContainer.visibility = View.GONE
            } else if (hasArrived) {
                countdownTextView.visibility = View.VISIBLE // Changed to VISIBLE
                countdownTextView.text = "ARRIVED" // Set text
                buttonContainer.visibility = View.VISIBLE
            } else {
                buttonContainer.visibility = View.GONE
                countdownTextView.visibility = View.VISIBLE

                val timeRemaining = message.arrivalTime - System.currentTimeMillis()
                val hours = TimeUnit.MILLISECONDS.toHours(timeRemaining)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60
                countdownTextView.text = String.format("Arriving in: %02d:%02d", hours, minutes).uppercase()
            }
            
            // Interaction logic
            if (hasArrived) {
                itemView.setOnClickListener { onViewClick(message) }
                itemView.setOnLongClickListener { 
                    onLongClick(message) 
                    true 
                }
                replyButton.setOnClickListener { onMessageClick(message) }
            } else {
                itemView.setOnClickListener(null)
                itemView.setOnLongClickListener(null)
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
        holder.bind(item, item.messageId == selectedMessageId)
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
