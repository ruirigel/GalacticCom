package com.rmrbranco.galacticcom

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.CountDownTimer
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SentMessageOptionsBottomSheet : BottomSheetDialogFragment() {

    private var messageId: String = ""
    private var content: String = ""
    private var sentToGalaxy: String = ""
    private var timestamp: Long = 0L
    private var catastropheType: String? = null
    private var arrivalTime: Long = 0L

    private var countDownTimer: CountDownTimer? = null

    companion object {
        fun newInstance(sentMessage: SentMessage): SentMessageOptionsBottomSheet {
            val fragment = SentMessageOptionsBottomSheet()
            val args = Bundle()
            args.putString("messageId", sentMessage.messageId)
            args.putString("content", sentMessage.content)
            args.putString("sentToGalaxy", sentMessage.sentToGalaxy)
            args.putLong("timestamp", (sentMessage.timestamp as? Long) ?: 0L)
            args.putString("catastropheType", sentMessage.catastropheType)
            args.putLong("arrivalTime", sentMessage.arrivalTime ?: 0L)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            messageId = it.getString("messageId", "")
            content = it.getString("content", "")
            sentToGalaxy = it.getString("sentToGalaxy", "")
            timestamp = it.getLong("timestamp", 0L)
            catastropheType = it.getString("catastropheType")
            arrivalTime = it.getLong("arrivalTime", 0L)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val d = it as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
            
            bottomSheet?.let { sheet ->
                val behavior = BottomSheetBehavior.from(sheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_sent_message_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
    }

    private fun toBinary(text: String): String {
        return text.map { char ->
            Integer.toBinaryString(char.code).padStart(8, '0')
        }.joinToString(" ")
    }

    private fun setupUI(view: View) {
        val warningTextView = view.findViewById<TextView>(R.id.dialog_catastrophe_warning)
        val destinationTextView = view.findViewById<TextView>(R.id.dialog_message_destination)
        val timestampTextView = view.findViewById<TextView>(R.id.dialog_message_timestamp)
        val subjectTextView = view.findViewById<TextView>(R.id.dialog_subject_content)
        val fullContentTextView = view.findViewById<TextView>(R.id.dialog_full_decrypted_content)
        val statusTextView = view.findViewById<TextView>(R.id.dialog_status)

        val neonCyanColor = ContextCompat.getColor(requireContext(), R.color.neon_cyan)

        // Catastrophe Warning
        warningTextView.text = catastropheType
        warningTextView.isVisible = catastropheType != null

        // Destination with coloring
        val destinationPrefix = "to: "
        val destinationText = "$destinationPrefix$sentToGalaxy"
        val destSpannable = SpannableString(destinationText)
        val whiteColor = ContextCompat.getColor(requireContext(), android.R.color.white)
        destSpannable.setSpan(ForegroundColorSpan(whiteColor), destinationPrefix.length, destinationText.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        destinationTextView.text = destSpannable

        // Timestamp
        if (timestamp != 0L) {
            timestampTextView.text = TimeUtils.getRelativeTimeSpanString(timestamp)
        } else {
            timestampTextView.text = ""
        }

        // Subject: Binary Truncated to 15 chars
        val binaryContent = toBinary(content)
        val subjectText = if (binaryContent.length > 15) {
            binaryContent.take(15) + "..."
        } else {
            binaryContent
        }
        subjectTextView.text = subjectText
        
        // Full Content: Decrypted
        // Only "Msg:" prefix should be colored Neon Cyan
        val msgPrefix = "Msg: "
        val fullMsgText = "$msgPrefix$content"
        val msgSpannable = SpannableString(fullMsgText)
        msgSpannable.setSpan(ForegroundColorSpan(neonCyanColor), 0, msgPrefix.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        
        fullContentTextView.text = msgSpannable

        // Status / Countdown
        if (arrivalTime > System.currentTimeMillis()) {
            val remainingTime = arrivalTime - System.currentTimeMillis()
            countDownTimer = object : CountDownTimer(remainingTime, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    if (isAdded) {
                        statusTextView.text = "ARRIVING IN: ${TimeUtils.formatDuration(millisUntilFinished)}"
                    }
                }

                override fun onFinish() {
                    if (isAdded) {
                        statusTextView.text = "ARRIVED"
                    }
                }
            }.start()
        } else {
            statusTextView.text = "ARRIVED"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        countDownTimer?.cancel()
    }
}