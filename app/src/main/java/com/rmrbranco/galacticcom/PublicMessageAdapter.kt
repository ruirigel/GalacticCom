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
    private val onViewClick: (PublicMessage) -> Unit
) : ListAdapter<PublicMessage, PublicMessageAdapter.ViewHolder>(PublicMessageDiffCallback()) {

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
        val deleteButton: Button = view.findViewById(R.id.btn_delete_item)
        val viewButton: Button = view.findViewById(R.id.btn_view_item)
        private val buttonContainer: LinearLayout = view.findViewById(R.id.button_container)

        fun bind(message: PublicMessage) {
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

            // Msg label is now in XML (tv_message_label), so we just set the content here
            messageContent.text = displayContent

            val whiteColor = ContextCompat.getColor(itemView.context, android.R.color.white)
            val senderPrefix = "by: "
            val fromSeparator = " from "
            val nickname = message.senderNickname ?: "Unknown"
            val galaxy = message.senderGalaxy ?: "Unknown"

            // Format: by: [nickname] from [Galaxyname]
            // "by:" and "from" are neon_cyan (default from XML), nickname and galaxy are white
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
                return
            }

            if (hasArrived) {
                countdownTextView.visibility = View.INVISIBLE
                buttonContainer.visibility = View.VISIBLE
                itemView.isClickable = true
            } else {
                buttonContainer.visibility = View.INVISIBLE
                countdownTextView.visibility = View.VISIBLE
                itemView.isClickable = false

                val timeRemaining = message.arrivalTime - System.currentTimeMillis()
                val hours = TimeUnit.MILLISECONDS.toHours(timeRemaining)
                val minutes = TimeUnit.MILLISECONDS.toMinutes(timeRemaining) % 60
                countdownTextView.text = String.format("Arriving in: %02d:%02d", hours, minutes).uppercase()
            }

            replyButton.setOnClickListener { onMessageClick(message) }
            deleteButton.setOnClickListener { onDeleteClick(message) }
            viewButton.setOnClickListener { onViewClick(message) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_public_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
