package com.rmrbranco.galacticcom

import android.os.CountDownTimer
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

data class SentMessage(
    val messageId: String = "",
    val content: String = "",
    val sentToGalaxy: String = "",
    val timestamp: Any? = null,
    val catastropheType: String? = null, 
    val arrivalTime: Long? = null 
)

class SentMessageAdapter(
    private val onItemClick: (SentMessage) -> Unit,
    private val onItemLongClick: (SentMessage) -> Unit
) : ListAdapter<SentMessage, SentMessageAdapter.ViewHolder>(SentMessageDiffCallback()) {

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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val contentTextView: TextView = view.findViewById(R.id.tv_sent_message_content)
        private val galaxyTextView: TextView = view.findViewById(R.id.tv_sent_to_galaxy)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_sent_timestamp)
        private val arrivalStatusTextView: TextView = view.findViewById(R.id.tv_arrival_status)
        private val catastropheWarningTextView: TextView = view.findViewById(R.id.tv_catastrophe_warning) 
        private var countDownTimer: CountDownTimer? = null 

        fun bind(
            sentMessage: SentMessage,
            isSelected: Boolean,
            onItemClick: (SentMessage) -> Unit,
            onItemLongClick: (SentMessage) -> Unit
        ) {
            countDownTimer?.cancel()

            if (sentMessage.catastropheType != null) {
                catastropheWarningTextView.text = sentMessage.catastropheType
                catastropheWarningTextView.visibility = View.VISIBLE
            } else {
                catastropheWarningTextView.visibility = View.GONE
            }

            val binaryContent = toBinary(sentMessage.content)
            // Limit to 15 chars as requested
            val maxLength = 15
            contentTextView.text = if (binaryContent.length > maxLength) {
                binaryContent.take(maxLength) + "..."
            } else {
                binaryContent
            }

            val toPrefix = "to: "
            val fullText = "$toPrefix${sentMessage.sentToGalaxy}"
            val spannable = SpannableString(fullText)
            
            val whiteColor = ContextCompat.getColor(itemView.context, android.R.color.white)
            spannable.setSpan(
                ForegroundColorSpan(whiteColor), 
                toPrefix.length, 
                fullText.length, 
                SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            galaxyTextView.text = spannable

            val timestamp = sentMessage.timestamp as? Long
            if (timestamp != null) {
                timestampTextView.text = TimeUtils.getRelativeTimeSpanString(timestamp)
            } else {
                timestampTextView.text = ""
            }

            val arrivalTime = sentMessage.arrivalTime
            if (arrivalTime != null && arrivalTime > System.currentTimeMillis()) {
                val remainingTime = arrivalTime - System.currentTimeMillis()
                arrivalStatusTextView.visibility = View.VISIBLE
                countDownTimer = object : CountDownTimer(remainingTime, 1000) {
                    override fun onTick(millisUntilFinished: Long) {
                        arrivalStatusTextView.text = "ARRIVING IN: ${TimeUtils.formatDuration(millisUntilFinished)}"
                    }

                    override fun onFinish() {
                        arrivalStatusTextView.text = "ARRIVED"
                    }
                }.start()
            } else {
                arrivalStatusTextView.visibility = View.VISIBLE
                arrivalStatusTextView.text = "ARRIVED"
            }

            // Handle Selection Visuals
            if (isSelected) {
                itemView.setBackgroundResource(R.drawable.item_inbox_selected_background)
            } else {
                itemView.setBackgroundResource(R.drawable.item_neon_background)
            }

            itemView.setOnClickListener { onItemClick(sentMessage) }
            itemView.setOnLongClickListener { 
                onItemLongClick(sentMessage)
                true
            }
        }

        fun cancelCountdown() {
            countDownTimer?.cancel()
        }

        private fun toBinary(text: String): String {
            return text.map { char ->
                Integer.toBinaryString(char.code).padStart(8, '0')
            }.joinToString(" ")
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sent_message, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.bind(item, selectedMessageIds.contains(item.messageId), onItemClick, onItemLongClick)
    }
    
    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)
        holder.cancelCountdown()
    }
}

class SentMessageDiffCallback : DiffUtil.ItemCallback<SentMessage>() {
    override fun areItemsTheSame(oldItem: SentMessage, newItem: SentMessage): Boolean {
        return oldItem.messageId == newItem.messageId
    }

    override fun areContentsTheSame(oldItem: SentMessage, newItem: SentMessage): Boolean {
        return oldItem == newItem
    }
}
