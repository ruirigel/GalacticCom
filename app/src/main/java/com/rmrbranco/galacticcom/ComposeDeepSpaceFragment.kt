package com.rmrbranco.galacticcom

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.data.managers.BadgeProgressManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import java.util.Calendar
import kotlin.math.max

class ComposeDeepSpaceFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private val currentUserId: String by lazy { auth.currentUser!!.uid }

    private lateinit var messageEditText: EditText
    private lateinit var sendButton: ImageButton
    private lateinit var sentMessagesRecyclerView: RecyclerView
    private lateinit var emptySentTextView: TextView
    private lateinit var sentMessagesTitle: TextView
    private lateinit var edgeLightingView: EdgeLightingView

    private lateinit var sentAdapter: SentMessageAdapter
    private var userNickname: String? = null
    private var userGalaxy: String? = null
    private var userExperience: Long = 0L

    private var mediaPlayer: MediaPlayer? = null
    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null
    
    // Firebase listeners
    private lateinit var userRef: DatabaseReference
    private lateinit var sentRef: DatabaseReference
    private var userListener: ValueEventListener? = null
    private var sentListener: ValueEventListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        return inflater.inflate(R.layout.fragment_compose, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        messageEditText = view.findViewById(R.id.et_message)
        sendButton = view.findViewById(R.id.btn_send)
        sentMessagesRecyclerView = view.findViewById(R.id.rv_sent_messages)
        emptySentTextView = view.findViewById(R.id.tv_empty_sent)
        sentMessagesTitle = view.findViewById(R.id.sent_messages_title)
        edgeLightingView = view.findViewById(R.id.edge_lighting_view)

        mediaPlayer = MediaPlayer.create(context, R.raw.sending_signal)

        setupRecyclerView()
        loadUserDataAndMessages()
        setupUI(view)

        sendButton.setOnClickListener { 
            checkPublicMessageLimitAndSend()
        }
    }

    private fun checkPublicMessageLimitAndSend() {
        sendButton.isEnabled = false
        hideKeyboard()
        val messageText = messageEditText.text.toString().trim()
        if (messageText.isEmpty()) {
            Toast.makeText(context, "Message cannot be empty.", Toast.LENGTH_SHORT).show()
            sendButton.isEnabled = true
            return
        }

        if (userNickname == null || userGalaxy == null) {
            Toast.makeText(context, "Please wait, loading data...", Toast.LENGTH_SHORT).show()
            sendButton.isEnabled = true
            return
        }

        val actionLogsRef = database.getReference("users/$currentUserId/actionLogs")
        actionLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastMessageTimestamp = snapshot.child("lastPublicMessageTimestamp").getValue(Long::class.java) ?: 0L
                var messageCountToday = snapshot.child("publicMessageCountToday").getValue(Int::class.java) ?: 0

                val today = Calendar.getInstance()
                val lastMessageDay = Calendar.getInstance().apply { timeInMillis = lastMessageTimestamp }

                if (today.get(Calendar.DAY_OF_YEAR) != lastMessageDay.get(Calendar.DAY_OF_YEAR) ||
                    today.get(Calendar.YEAR) != lastMessageDay.get(Calendar.YEAR)) {
                    messageCountToday = 0 // Reset counter for the new day
                }

                val dailyLimit = SettingsManager.getPublicMessageDailyLimit()

                if (messageCountToday < dailyLimit) {
                    // Limit not reached, proceed to find a user and send
                    sendMessageToAnotherUserGalaxy(messageCountToday, actionLogsRef)
                } else {
                    // Daily limit reached. Check Inventory for Daily Licenses (item_003).
                    val inventoryRef = database.getReference("users/$currentUserId/inventory/daily_licenses")
                    inventoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(invSnapshot: DataSnapshot) {
                            val licenses = invSnapshot.getValue(Int::class.java) ?: 0
                            if (licenses > 0) {
                                // Consume one license
                                inventoryRef.setValue(licenses - 1).addOnSuccessListener {
                                    Toast.makeText(context, "Used 1 License. Remaining: ${licenses - 1}", Toast.LENGTH_SHORT).show()
                                    sendMessageToAnotherUserGalaxy(messageCountToday, actionLogsRef)
                                }
                            } else {
                                Toast.makeText(context, "You have reached your daily transmission limit.", Toast.LENGTH_SHORT).show()
                                sendButton.isEnabled = true
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(context, "Failed to verify inventory.", Toast.LENGTH_SHORT).show()
                            sendButton.isEnabled = true
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to verify daily limit. Please try again.", Toast.LENGTH_SHORT).show()
                sendButton.isEnabled = true
            }
        })
    }

    // UPDATED FUNCTION: Read from 'galaxy_presence' instead of 'users'
    private fun sendMessageToAnotherUserGalaxy(currentCount: Int, logsRef: DatabaseReference) {
        database.getReference("galaxy_presence").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) {
                    sendButton.isEnabled = true
                    return
                }

                val inhabitedGalaxies = mutableListOf<String>()
                
                // Collect all galaxies that have at least one user
                for (galaxySnapshot in snapshot.children) {
                    if (galaxySnapshot.hasChildren()) {
                        galaxySnapshot.key?.let { inhabitedGalaxies.add(it) }
                    }
                }

                if (inhabitedGalaxies.isEmpty()) {
                    // Fallback: If no presence data, pick a random known galaxy name
                    val randomFallback = CosmicNameGenerator.generateGalaxyName()
                    sendMessage(randomFallback, currentCount, logsRef)
                    return
                }

                // Pick a random inhabited galaxy
                val targetGalaxy = inhabitedGalaxies.random()
                sendMessage(targetGalaxy, currentCount, logsRef)
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    Toast.makeText(context, "Error finding other inhabitants.", Toast.LENGTH_SHORT).show()
                }
                sendButton.isEnabled = true
            }
        })
    }

    private fun sendMessage(targetGalaxy: String, currentCount: Int, logsRef: DatabaseReference) {
        val originalMessageText = messageEditText.text.toString().trim()

        val processedResult = CatastropheEngine.processMessage(originalMessageText)
        val corruptedMessageText = processedResult.corruptedMessage
        val additionalDelayMillis = processedResult.additionalDelaySeconds * 1000L
        
        val galaxyInboxKey = targetGalaxy.replace(" ", "_")
        val messagesRef = database.getReference("public_broadcasts").child(galaxyInboxKey)
        val messageId = messagesRef.push().key ?: ""

        val inventoryRef = database.getReference("users/$currentUserId/inventory/hyperwave_booster")
        inventoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(invSnapshot: DataSnapshot) {
                val boosters = invSnapshot.getValue(Int::class.java) ?: 0
                val useBooster = boosters > 0

                val baseWaitTime = 24 * 60 * 60 * 1000L
                val discountPerHour = 10 
                val discountInMillis = (userExperience / discountPerHour) * 60 * 60 * 1000L
                val minWaitTime = 1 * 60 * 60 * 1000L

                val calculatedWaitTime = max(minWaitTime, baseWaitTime - discountInMillis)
                
                // Apply Hyperwave Booster logic
                val finalWaitTime = if (useBooster) 0L else calculatedWaitTime
                val arrivalTime = System.currentTimeMillis() + finalWaitTime + additionalDelayMillis

                Log.d("ComposeFragment", "User XP: $userExperience, Discount: ${discountInMillis / (60*60*1000)} hours, Final Wait: ${finalWaitTime / (60*60*1000)} hours")

                val publicMessage = PublicMessage(
                    messageId = messageId,
                    senderId = currentUserId,
                    message = toBinary(corruptedMessageText),
                    senderNickname = userNickname,
                    senderGalaxy = userGalaxy,
                    timestamp = ServerValue.TIMESTAMP,
                    arrivalTime = arrivalTime,
                    catastropheType = processedResult.catastropheType
                )

                messagesRef.child(messageId).setValue(publicMessage).addOnSuccessListener {
                    // Update daily message count in Firebase on successful send
                    val updates = mapOf(
                        "lastPublicMessageTimestamp" to ServerValue.TIMESTAMP,
                        "publicMessageCountToday" to (currentCount + 1)
                    )
                    logsRef.updateChildren(updates)
                    
                    // Badge Logic: Record Broadcast
                    BadgeProgressManager.recordBroadcast()
                    
                    // Badge Logic: Record Murano Progress (Target Galaxy)
                    BadgeProgressManager.recordMessageSentToGalaxy(targetGalaxy)
                    
                    if (useBooster) {
                        inventoryRef.setValue(boosters - 1)
                        Toast.makeText(context, "Hyperwave Booster used! Transmission is instant.", Toast.LENGTH_SHORT).show()
                    }

                    edgeLightingView.startAnimation(true)
                    mediaPlayer?.start()
                    ExperienceUtils.incrementExperience(currentUserId)
                    saveMessageToUserHistory(messageId, originalMessageText, targetGalaxy, processedResult.catastropheType, arrivalTime)
                    messageEditText.text.clear()

                }.addOnFailureListener { 
                    if (isAdded) {
                        Toast.makeText(context, "Failed to send message.", Toast.LENGTH_SHORT).show()
                    }
                }.addOnCompleteListener {
                    if (isAdded) {
                        sendButton.isEnabled = true
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                 // Fallback if inventory check fails: proceed without booster
                 Log.e("ComposeFragment", "Failed to check booster inventory", error.toException())
                 // Proceed with normal logic... (duplicated for safety or just call sendMessageInner... but for now, simple fallback is acceptable or just letting the user retry)
                 if (isAdded) {
                     Toast.makeText(context, "Failed to access inventory. Please try again.", Toast.LENGTH_SHORT).show()
                     sendButton.isEnabled = true
                 }
            }
        })
    }
    
    // The rest of the file (UI setup, RecyclerView, dialogs, etc.) remains unchanged.
    // ...
    @SuppressLint("ClickableViewAccessibility")
    private fun setupUI(view: View) { if (view !is EditText) { view.setOnTouchListener { v, event -> hideKeyboard(); false } }; if (view is ViewGroup) { for (i in 0 until view.childCount) { val innerView = view.getChildAt(i); setupUI(innerView) } } }
    private fun hideKeyboard() { val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager; imm.hideSoftInputFromWindow(view?.windowToken, 0) }
    private fun setupRecyclerView() { sentAdapter = SentMessageAdapter { message -> showDeleteConfirmationDialog(message) }; sentMessagesRecyclerView.layoutManager = LinearLayoutManager(context); sentMessagesRecyclerView.adapter = sentAdapter }
    private fun toBinary(text: String): String { return text.map { char -> Integer.toBinaryString(char.code).padStart(8, '0') }.joinToString(" ") }
    private fun showDeleteConfirmationDialog(message: SentMessage) { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_custom); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title); val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message); val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button); dialog.findViewById<View>(R.id.galaxy_stats_layout).visibility = View.GONE; dialog.findViewById<View>(R.id.dialog_subtitle).visibility = View.GONE; titleTextView.text = "Delete Transmission Log"; messageTextView.text = "Are you sure you want to delete this message from your log? This action cannot be undone."; negativeButton.text = "Cancel"; positiveButton.text = "Delete"; negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { deleteSentMessage(message); dialog.dismiss() }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun deleteSentMessage(message: SentMessage) { val userSentMessagesRef = database.getReference("users").child(currentUserId).child("sent_messages"); userSentMessagesRef.child(message.messageId).removeValue().addOnSuccessListener { Toast.makeText(context, "Message deleted from log", Toast.LENGTH_SHORT).show() }.addOnFailureListener { Toast.makeText(context, "Failed to delete message from log", Toast.LENGTH_SHORT).show() }; val galaxyInboxKey = message.sentToGalaxy.replace(" ", "_"); val publicBroadcastsRef = database.getReference("public_broadcasts").child(galaxyInboxKey); publicBroadcastsRef.child(message.messageId).removeValue() }
    private fun startTitleLoadingAnimation() { titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }; titleLoadingRunnable = object : Runnable { private var dotCount = 0; override fun run() { dotCount = (dotCount + 1) % 4; val dots = when (dotCount) { 1 -> "."; 2 -> ".."; 3 -> "..."; else -> "" }; sentMessagesTitle.text = "Transmit$dots"; titleLoadingHandler.postDelayed(this, 500) } }; titleLoadingHandler.post(titleLoadingRunnable!!) }
    private fun stopTitleLoadingAnimation() { titleLoadingHandler.removeCallbacksAndMessages(null); sentMessagesTitle.text = "Transmit" }
    private fun loadUserDataAndMessages() { startTitleLoadingAnimation(); userRef = database.getReference("users").child(currentUserId); userListener = object : ValueEventListener { override fun onDataChange(snapshot: DataSnapshot) { userNickname = snapshot.child("nickname").getValue(String::class.java); userGalaxy = snapshot.child("galaxy").getValue(String::class.java); userExperience = snapshot.child("experiencePoints").getValue(Long::class.java) ?: 0L } override fun onCancelled(error: DatabaseError) { if (isAdded) { Toast.makeText(context, "Failed to load user data.", Toast.LENGTH_SHORT).show() } } }; userRef.addValueEventListener(userListener!!); sentRef = userRef.child("sent_messages"); sentListener = object : ValueEventListener { override fun onDataChange(snapshot: DataSnapshot) { if (!isAdded) return; val sentMessages = snapshot.children.mapNotNull { it.getValue(SentMessage::class.java) }.reversed(); sentAdapter.submitList(sentMessages) { if (sentMessages.isNotEmpty()) { sentMessagesRecyclerView.scrollToPosition(0) } }; checkEmptyState(sentMessages) } override fun onCancelled(error: DatabaseError) { if (isAdded) { Toast.makeText(context, "Failed to load sent messages.", Toast.LENGTH_SHORT).show() }; checkEmptyState(emptyList()) } }; sentRef.orderByChild("timestamp").addValueEventListener(sentListener!!) }
    private fun checkEmptyState(list: List<Any>) { stopTitleLoadingAnimation(); if (list.isEmpty()) { emptySentTextView.visibility = View.VISIBLE; sentMessagesRecyclerView.visibility = View.GONE } else { emptySentTextView.visibility = View.GONE; sentMessagesRecyclerView.visibility = View.VISIBLE } }
    private fun saveMessageToUserHistory(messageId: String, content: String, targetGalaxy: String, catastropheType: String?, arrivalTime: Long) { val sentMessage = SentMessage(messageId, content, targetGalaxy, ServerValue.TIMESTAMP, catastropheType, arrivalTime); database.getReference("users").child(currentUserId).child("sent_messages").child(messageId).setValue(sentMessage) }
    override fun onDestroyView() { super.onDestroyView(); userListener?.let { userRef.removeEventListener(it) }; sentListener?.let { sentRef.removeEventListener(it) }; mediaPlayer?.release(); mediaPlayer = null; titleLoadingHandler.removeCallbacksAndMessages(null) }
}