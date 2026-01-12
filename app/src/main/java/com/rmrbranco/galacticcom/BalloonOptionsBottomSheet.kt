package com.rmrbranco.galacticcom

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.concurrent.TimeUnit

class BalloonOptionsBottomSheet : BottomSheetDialogFragment() {

    private var message: PublicMessage? = null
    private var balloonView: View? = null // Reference to the balloon view for animations
    
    // Callbacks to delegate actions back to HomeFragment
    var onReplyClick: ((PublicMessage) -> Unit)? = null
    var onDeleteClick: ((PublicMessage, View?) -> Unit)? = null

    private var dialogCountdownHandler: Handler? = null
    private var dialogCountdownRunnable: Runnable? = null

    companion object {
        fun newInstance(message: PublicMessage): BalloonOptionsBottomSheet {
            val fragment = BalloonOptionsBottomSheet()
            fragment.message = message
            return fragment
        }
    }
    
    fun setBalloonViewReference(view: View) {
        this.balloonView = view
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        // Make the container transparent so our rounded corners show
        dialog.setOnShowListener {
            val d = it as BottomSheetDialog
            val bottomSheet = d.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
            
            // Ensure the sheet expands fully to fit the content (Msg)
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
        return inflater.inflate(R.layout.dialog_balloon_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val msg = message ?: run {
            dismiss()
            return
        }

        setupUI(view, msg)
    }

    private fun binaryToText(binary: String): String {
        return try {
            binary.split(" ").joinToString("") { 
                Integer.parseInt(it, 2).toChar().toString() 
            }
        } catch (e: Exception) {
            binary
        }
    }

    private fun setupUI(view: View, message: PublicMessage) {
        val catastropheWarningTextView = view.findViewById<TextView>(R.id.dialog_catastrophe_warning)
        val messageContent = view.findViewById<TextView>(R.id.dialog_message_content)
        val fullMessageTextView = view.findViewById<TextView>(R.id.dialog_full_message_content)
        val messageSender = view.findViewById<TextView>(R.id.dialog_message_sender)
        val timestamp = view.findViewById<TextView>(R.id.dialog_message_timestamp)
        val countdownTextView = view.findViewById<TextView>(R.id.dialog_countdown)
        val arrivedStatusTextView = view.findViewById<TextView>(R.id.dialog_arrived_status)
        
        val btnInterceptReply = view.findViewById<Button>(R.id.btn_intercept_reply)
        // btnDelete removed from XML, so removing from code

        catastropheWarningTextView.text = message.catastropheType
        catastropheWarningTextView.isVisible = message.catastropheType != null
        
        val neonCyanColor = ContextCompat.getColor(requireContext(), R.color.neon_cyan)
        val hasArrived = message.arrivalTime == 0L || System.currentTimeMillis() >= message.arrivalTime

        val rawContent = message.message ?: ""
        val decryptedContent = binaryToText(rawContent)
        
        // Subject: Encrypted (Binary) + Truncated to 15 chars
        val subjectText = rawContent.take(15) + if (rawContent.length > 15) "..." else ""
        
        messageContent.text = SpannableString("Subject: $subjectText").apply {
            setSpan(ForegroundColorSpan(neonCyanColor), 0, 8, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // Sender Info
        messageSender.text = SpannableString("from: ${message.senderGalaxy} by ${message.senderNickname}").apply {
            setSpan(ForegroundColorSpan(neonCyanColor), 0, 5, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            indexOf(" by ").takeIf { it != -1 }?.let {
                setSpan(ForegroundColorSpan(neonCyanColor), it, it + 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        
        // Full Message Content Setup: Always visible
        fullMessageTextView.isVisible = true

        (message.timestamp as? Long)?.let {
            timestamp.text = TimeUtils.getRelativeTimeSpanString(it)
        }

        // Countdown Logic
        dialogCountdownHandler?.removeCallbacksAndMessages(null)
        
        if (!hasArrived) {
            btnInterceptReply.isGone = true
            // Delete button logic removed
            // Hide full message if not arrived yet
            fullMessageTextView.isGone = true
            arrivedStatusTextView.isGone = true // Hide ARRIVED status
            countdownTextView.isVisible = true
            
            // Set ENCRYPTED content while traveling
            fullMessageTextView.text = SpannableString("Msg: $rawContent").apply {
                setSpan(ForegroundColorSpan(neonCyanColor), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            
            dialogCountdownHandler = Handler(Looper.getMainLooper())
            dialogCountdownRunnable = object : Runnable {
                override fun run() {
                    val remaining = message.arrivalTime - System.currentTimeMillis()
                    if (remaining > 0) {
                        val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                        countdownTextView.text = String.format("Arriving: %02d:%02d", hours, minutes).uppercase()
                        dialogCountdownHandler?.postDelayed(this, 1000)
                    } else {
                        // Arrived
                        countdownTextView.isGone = true
                        btnInterceptReply.isVisible = true
                        // btnDelete.isVisible = true -> Removed
                        fullMessageTextView.isVisible = true
                        arrivedStatusTextView.isVisible = true // Show ARRIVED status
                        
                        // DECRYPT content now
                        fullMessageTextView.text = SpannableString("Msg: $decryptedContent").apply {
                            setSpan(ForegroundColorSpan(neonCyanColor), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                }
            }.also { dialogCountdownHandler?.post(it) }
        } else {
            countdownTextView.isGone = true
            btnInterceptReply.isVisible = true
            // btnDelete.isVisible = true -> Removed
            fullMessageTextView.isVisible = true
            arrivedStatusTextView.isVisible = true // Show ARRIVED status
            
            // DECRYPTED content
            fullMessageTextView.text = SpannableString("Msg: $decryptedContent").apply {
                setSpan(ForegroundColorSpan(neonCyanColor), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        // btnDelete click listener removed

        btnInterceptReply.setOnClickListener {
            onReplyClick?.invoke(message)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        dialogCountdownHandler?.removeCallbacksAndMessages(null)
    }
}