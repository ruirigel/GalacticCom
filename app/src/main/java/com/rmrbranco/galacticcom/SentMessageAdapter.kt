package com.rmrbranco.galacticcom

import android.app.Dialog
import android.os.CountDownTimer
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
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
    val catastropheType: String? = null, // Novo campo
    val arrivalTime: Long? = null // Tempo de chegada da mensagem
)

class SentMessageAdapter(private val onDeleteClick: (SentMessage) -> Unit) : ListAdapter<SentMessage, SentMessageAdapter.ViewHolder>(SentMessageDiffCallback()) {

    class ViewHolder(view: View, val onDeleteClick: (SentMessage) -> Unit) : RecyclerView.ViewHolder(view) {
        private val contentTextView: TextView = view.findViewById(R.id.tv_sent_message_content)
        private val galaxyTextView: TextView = view.findViewById(R.id.tv_sent_to_galaxy)
        private val timestampTextView: TextView = view.findViewById(R.id.tv_sent_timestamp)
        private val arrivalStatusTextView: TextView = view.findViewById(R.id.tv_arrival_status)
        private val catastropheWarningTextView: TextView = view.findViewById(R.id.tv_catastrophe_warning) // Novo TextView
        private val viewMessageButton: Button = view.findViewById(R.id.btn_view_sent_message)
        private val deleteButton: Button = view.findViewById(R.id.btn_delete_sent_message)
        private var countDownTimer: CountDownTimer? = null // Para a contagem regressiva

        fun bind(sentMessage: SentMessage) {
            // Cancela qualquer contagem regressiva anterior
            countDownTimer?.cancel()

            // Exibe o aviso de catástrofe se existir
            if (sentMessage.catastropheType != null) {
                catastropheWarningTextView.text = sentMessage.catastropheType
                catastropheWarningTextView.visibility = View.VISIBLE
            } else {
                catastropheWarningTextView.visibility = View.GONE
            }

            val binaryContent = toBinary(sentMessage.content)
            val maxLength = 35
            contentTextView.text = if (binaryContent.length > maxLength) {
                binaryContent.take(maxLength) + "..."
            } else {
                binaryContent
            }

            val toPrefix = "to: "
            val fullText = "$toPrefix${sentMessage.sentToGalaxy}"
            val spannable = SpannableString(fullText)
            
            // Base color is neon_cyan from XML. We only need to color the Galaxy Name white.
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

            viewMessageButton.setOnClickListener {
                val dialog = Dialog(itemView.context)
                dialog.setContentView(R.layout.dialog_view_sent_message)
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                val toGalaxyTextView: TextView = dialog.findViewById(R.id.tv_to_galaxy)
                val messageContent: TextView = dialog.findViewById(R.id.tv_message_content)
                val closeButton: Button = dialog.findViewById(R.id.btn_close)

                // Populate the dialog views
                toGalaxyTextView.text = "To: ${sentMessage.sentToGalaxy}"
                messageContent.text = "Msg: ${sentMessage.content}"
                
                closeButton.setOnClickListener { dialog.dismiss() }
                dialog.show()
                dialog.window?.setLayout((itemView.context.resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
            }

            deleteButton.setOnClickListener { onDeleteClick(sentMessage) }
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
        return ViewHolder(view, onDeleteClick)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
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
