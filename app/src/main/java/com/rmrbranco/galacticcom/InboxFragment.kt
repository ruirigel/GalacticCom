package com.rmrbranco.galacticcom

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.rmrbranco.galacticcom.data.managers.BadgeProgressManager
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import java.util.concurrent.CountDownLatch
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

class InboxFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage
    private val currentUserId: String by lazy { auth.currentUser!!.uid }
    private var userRef: DatabaseReference? = null

    private lateinit var conversationsRecyclerView: RecyclerView
    private lateinit var emptyInboxTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var inboxAdapter: InboxAdapter

    private var actionMode: ActionMode? = null
    private var selectedConversation: InboxConversation? = null

    // Conversation Data
    private var blockList: Set<String> = emptySet()
    private var conversationDetailsListener: ValueEventListener? = null


    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()
        userRef = database.reference.child("users").child(currentUserId)
        return inflater.inflate(R.layout.fragment_inbox, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        conversationsRecyclerView = view.findViewById(R.id.rv_conversations)
        emptyInboxTextView = view.findViewById(R.id.tv_empty_inbox)
        titleTextView = view.findViewById(R.id.inbox_title)

        setupRecyclerView()
        checkForInvitationsAndLoadConversations()
        
        // Handle initial navigation trigger only once
        handleNavigationTrigger()
    }

    override fun onResume() {
        super.onResume()
        val bottomNavView = activity?.findViewById<BottomNavigationView>(R.id.bottom_nav_view)
        bottomNavView?.visibility = View.VISIBLE
    }
    
    private fun handleNavigationTrigger() {
        if (arguments?.getBoolean("navigateToConversation") == true) {
            val bundle = bundleOf(
                "recipientId" to arguments?.getString("senderId"),
                "originalMessageId" to arguments?.getString("originalMessageId"),
                "originalGalaxyName" to arguments?.getString("originalGalaxyName"),
                "isPrivate" to arguments?.getBoolean("isPrivate", false),
                "quotedMessageContent" to arguments?.getString("quotedMessageContent"),
                "quotedMessageAuthor" to arguments?.getString("quotedMessageAuthor")
            )
            // Clear the trigger argument to prevent re-triggering on back navigation
            arguments?.putBoolean("navigateToConversation", false)
            
            findNavController().navigate(R.id.action_inbox_to_conversation, bundle)
        }
    }

    private fun setupRecyclerView() {
        inboxAdapter = InboxAdapter(
            onConversationClick = { conversation ->
                if (actionMode != null) {
                    actionMode?.finish()
                }
                val action = InboxFragmentDirections.actionInboxToConversation(
                    recipientId = conversation.otherUserId,
                    conversationId = conversation.conversationId
                )
                findNavController().navigate(action)
            },
            onConversationLongClick = { conversation ->
                selectedConversation = conversation
                inboxAdapter.setSelected(conversation.conversationId)
                
                if (actionMode == null) {
                    actionMode = view?.startActionMode(object : ActionMode.Callback {
                        override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                            mode.menuInflater.inflate(R.menu.menu_inbox_selection, menu)
                            mode.title = "Selected"
                            
                            val deleteItem = menu.findItem(R.id.action_delete_conversation)
                            deleteItem?.icon?.let { icon ->
                                val wrapped = DrawableCompat.wrap(icon)
                                icon.mutate()
                                DrawableCompat.setTint(
                                    wrapped, 
                                    ContextCompat.getColor(requireContext(), R.color.neon_cyan)
                                )
                                deleteItem.icon = wrapped
                            }
                            return true
                        }

                        override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                            return false
                        }

                        override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                            return when (item.itemId) {
                                R.id.action_delete_conversation -> {
                                    selectedConversation?.let { showDeleteConfirmationDialog(it) }
                                    mode.finish()
                                    true
                                }
                                else -> false
                            }
                        }

                        override fun onDestroyActionMode(mode: ActionMode) {
                            actionMode = null
                            selectedConversation = null
                            inboxAdapter.setSelected(null)
                        }
                    })
                }
            }
        )
        val layoutManager = LinearLayoutManager(context)
        conversationsRecyclerView.layoutManager = layoutManager
        conversationsRecyclerView.adapter = inboxAdapter

        // Detect clicks on empty space to close ActionMode
        val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: MotionEvent): Boolean {
                if (actionMode != null) {
                    val child = conversationsRecyclerView.findChildViewUnder(e.x, e.y)
                    if (child == null) {
                        actionMode?.finish()
                        return true
                    }
                }
                return false
            }
        })

        conversationsRecyclerView.addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                return gestureDetector.onTouchEvent(e)
            }
            override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) {}
            override fun onRequestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {}
        })
    }

    private fun startTitleLoadingAnimation() {
        titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }
        titleLoadingRunnable = object : Runnable {
            private var dotCount = 0
            override fun run() {
                dotCount = (dotCount + 1) % 4
                val dots = when (dotCount) { 1 -> "."; 2 -> ".."; 3 -> "..."; else -> "" }
                titleTextView.text = "Comms$dots"
                titleLoadingHandler.postDelayed(this, 500)
            }
        }
        titleLoadingHandler.post(titleLoadingRunnable!!)
    }

    private fun stopTitleLoadingAnimation() {
        titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }
        titleLoadingRunnable = null;
        titleTextView.text = "Comms"
    }

    private fun showDeleteConfirmationDialog(conversation: InboxConversation) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message)
        val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button)
        val galaxyStatsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout)

        titleTextView.text = "Delete Conversation"
        messageTextView.text = "Are you sure you want to delete this conversation? This action cannot be undone."
        negativeButton.text = "Cancel"
        positiveButton.text = "Delete"

        galaxyStatsLayout.visibility = View.GONE
        subtitleTextView.visibility = View.GONE
        messageTextView.gravity = Gravity.START
        titleTextView.gravity = Gravity.START

        negativeButton.setOnClickListener { dialog.dismiss() }
        positiveButton.setOnClickListener { deleteConversation(conversation); dialog.dismiss() }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

private fun deleteConversation(conversation: InboxConversation) {
    val conversationRef = database.reference.child("conversations").child(conversation.conversationId)

    conversationRef.child("messages").addListenerForSingleValueEvent(object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            snapshot.children.forEach { messageSnapshot ->
                val message = messageSnapshot.getValue(ChatMessage::class.java)
                message?.gifUrl?.let { if (it.isNotEmpty() && it.startsWith("https")) storage.getReferenceFromUrl(it).delete() }
                message?.imageUrl?.let { if (it.isNotEmpty() && it.startsWith("https")) storage.getReferenceFromUrl(it).delete() }
                message?.voiceMessageUrl?.let { if (it.isNotEmpty() && it.startsWith("https")) storage.getReferenceFromUrl(it).delete() }
            }

            conversationRef.removeValue().addOnSuccessListener {
                val otherUserId = conversation.otherUserId
                database.reference.child("users").child(currentUserId).child("conversations").child(conversation.conversationId).removeValue()
                // Attempt to remove for other user (might fail due to rules, but handled by auto-cleanup now)
                database.reference.child("users").child(otherUserId).child("conversations").child(conversation.conversationId).removeValue()

                // Remove local encryption keys
                val prefs = context?.getSharedPreferences("CryptoPrefs", Context.MODE_PRIVATE)
                prefs?.edit()
                    ?.remove("private_key_${conversation.conversationId}")
                    ?.remove("public_key_${conversation.conversationId}")
                    ?.apply()

                // Also remove the corresponding hidden public message
                val originalMessageId = arguments?.getString("originalMessageId")
                if (originalMessageId != null) {
                    database.reference.child("users").child(currentUserId).child("hiddenPublicMessages").child(originalMessageId).removeValue()
                }
                
                // Badge Logic: Severed Conversation
                val logsRef = database.getReference("users/$currentUserId/actionLogs/severedConversationsCount")
                logsRef.runTransaction(object : Transaction.Handler {
                    override fun doTransaction(mutableData: MutableData): Transaction.Result {
                        val current = mutableData.getValue(Int::class.java) ?: 0
                        mutableData.value = current + 1
                        return Transaction.success(mutableData)
                    }
                    override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
                })

                Toast.makeText(context, "Conversation deleted", Toast.LENGTH_SHORT).show()
            }.addOnFailureListener {
                Toast.makeText(context, "Failed to delete conversation", Toast.LENGTH_SHORT).show()
            }
        }

        override fun onCancelled(error: DatabaseError) {
            Toast.makeText(context, "Failed to access messages for deletion.", Toast.LENGTH_SHORT).show()
        }
    })
}

    private fun checkForInvitationsAndLoadConversations() {
        startTitleLoadingAnimation()

        val invitationsRef = database.reference.child("invitations").child(currentUserId)
        invitationsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val latch = CountDownLatch(snapshot.childrenCount.toInt())
                    snapshot.children.forEach { invitationSnapshot ->
                        val conversationId = invitationSnapshot.key
                        if (conversationId != null) {
                            database.reference.child("users").child(currentUserId).child("conversations").child(conversationId).setValue(true)
                                .addOnCompleteListener { latch.countDown() }
                        } else { latch.countDown() }
                    }
                    Thread {
                        latch.await()
                        invitationsRef.removeValue()
                        Handler(Looper.getMainLooper()).post { loadConversations() }
                    }.start()
                } else {
                    loadConversations()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                // If invitation check fails, proceed anyway
                loadConversations()
            }
        })
    }

    private fun loadConversations() {
        // ROBUST LOADING: Fetch block lists individually to allow partial success
        val latch = CountDownLatch(2)
        val blockedUserIds = mutableSetOf<String>()
        val usersWhoBlockedMeIds = mutableSetOf<String>()

        userRef?.child("blockedUsers")?.get()?.addOnSuccessListener { snapshot ->
            snapshot.children.mapNotNullTo(blockedUserIds) { it.key }
            latch.countDown()
        }?.addOnFailureListener { latch.countDown() }

        userRef?.child("usersWhoBlockedMe")?.get()?.addOnSuccessListener { snapshot ->
            snapshot.children.mapNotNullTo(usersWhoBlockedMeIds) { it.key }
            latch.countDown()
        }?.addOnFailureListener { latch.countDown() }

        Thread {
            latch.await()
            blockList = blockedUserIds + usersWhoBlockedMeIds
            Handler(Looper.getMainLooper()).post {
                attachConversationsListener()
            }
        }.start()
    }

    private fun attachConversationsListener() {
        val userConversationsRef = database.reference.child("users").child(currentUserId).child("conversations")
        conversationDetailsListener = userConversationsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // DEBUG: Check what we found
                Log.d("InboxFragment", "Found ${snapshot.childrenCount} raw conversations")
                
                if (!snapshot.exists()) {
                    checkEmptyState(emptyList())
                    stopTitleLoadingAnimation()
                    return
                }

                val conversationsData = snapshot.children.mapNotNull { data ->
                    data.key?.let { it to (data.child("lastSeenTimestamp").getValue(Long::class.java) ?: 0L) }
                }.toMap()

                val conversationIds = conversationsData.keys.filter { getOtherUserId(it) !in blockList }
                
                // DEBUG: Check filtering
                Log.d("InboxFragment", "Conversations after block list filter: ${conversationIds.size}")
                
                if (conversationIds.isEmpty()) {
                    checkEmptyState(emptyList())
                    stopTitleLoadingAnimation()
                    return
                }

                val conversations = mutableListOf<InboxConversation>()
                val latch = CountDownLatch(conversationIds.size)

                conversationIds.forEach { convId ->
                    val lastSeenTimestamp = conversationsData[convId] ?: 0L
                    fetchConversationDetails(convId, lastSeenTimestamp) { conversation ->
                        if (conversation != null) {
                            conversations.add(conversation)
                        } else {
                            // Only remove locally/skip. Do not delete from DB automatically here to avoid accidents during sync errors.
                        }
                        latch.countDown()
                    }
                }

                Thread {
                    latch.await()
                    val sortedConversations = conversations.sortedByDescending { it.lastMessageTimestamp }
                    Handler(Looper.getMainLooper()).post {
                        inboxAdapter.submitList(sortedConversations)
                        checkEmptyState(sortedConversations)
                        stopTitleLoadingAnimation()
                    }
                }.start()
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("InboxFragment", "Error loading conversations: ${error.message}")
                stopTitleLoadingAnimation()
                Toast.makeText(context, "Failed to load conversations: ${error.message}", Toast.LENGTH_LONG).show()
            }
        })
    }

    private fun getOtherUserId(conversationId: String): String? {
        val ids = conversationId.split('_')
        return if (ids.size == 2 && ids[0] == currentUserId) ids[1] else if (ids.size == 2) ids[0] else null
    }

    private fun getKeyPairForConversation(conversationId: String): KeyPair? {
        val prefs = activity?.getSharedPreferences("CryptoPrefs", Context.MODE_PRIVATE) ?: return null
        val privateKeyPrefKey = "private_key_${conversationId}"
        val publicKeyPrefKey = "public_key_${conversationId}"

        val privateKeyEncoded = prefs.getString(privateKeyPrefKey, null)
        val publicKeyEncoded = prefs.getString(publicKeyPrefKey, null)

        return if (privateKeyEncoded != null && publicKeyEncoded != null) {
            try {
                val keyFactory = KeyFactory.getInstance("EC")
                val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyEncoded)))
                val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyEncoded)))
                KeyPair(publicKey, privateKey)
            } catch (e: Exception) {
                Log.e("Crypto", "Error reconstructing KeyPair", e)
                null
            }
        } else {
            null
        }
    }


    // UPDATED FUNCTION: Fetch public fields individually to respect security rules
    private fun fetchConversationDetails(convId: String, lastSeenTimestamp: Long, callback: (InboxConversation?) -> Unit) {
        val otherUserId = getOtherUserId(convId) ?: return callback(null)
        val otherUserRef = database.reference.child("users").child(otherUserId)

        // Parallel fetch for nickname and avatarSeed
        val latch = CountDownLatch(2)
        var nickname = "Unknown"
        var avatarSeed = otherUserId

        otherUserRef.child("nickname").get().addOnSuccessListener { 
            nickname = it.getValue(String::class.java) ?: "Unknown"
            latch.countDown()
        }.addOnFailureListener { latch.countDown() }

        otherUserRef.child("avatarSeed").get().addOnSuccessListener { 
            avatarSeed = it.getValue(String::class.java) ?: otherUserId
            latch.countDown()
        }.addOnFailureListener { latch.countDown() }

        // Process conversation AFTER user details are fetched
        Thread {
            latch.await()
            Handler(Looper.getMainLooper()).post {
                database.reference.child("conversations").child(convId).get().addOnSuccessListener { convSnapshot ->
                    if (!convSnapshot.exists()) {
                        callback(null)
                        return@addOnSuccessListener
                    }

                    val isPrivate = convSnapshot.child("isPrivate").getValue(Boolean::class.java) ?: true
                    var lastMessageTimestamp: Long = System.currentTimeMillis()
                    val messageSnapshot = convSnapshot.child("messages").children.lastOrNull()
                    val message = messageSnapshot?.getValue(ChatMessage::class.java)

                    val hasUnreadMessages = if (message != null) {
                        (message.timestamp as? Long ?: 0L) > lastSeenTimestamp
                    } else {
                        false
                    }

                    val buildAndInvokeCallback: (String) -> Unit = { finalMessageText ->
                        val maxLength = 35
                        val truncatedMessage = if (finalMessageText.length > maxLength) finalMessageText.take(maxLength) + "..." else finalMessageText
                        lastMessageTimestamp = message?.timestamp as? Long ?: convSnapshot.child("lastSeenTimestamp").getValue(Long::class.java) ?: System.currentTimeMillis()
                        callback(InboxConversation(convId, otherUserId, nickname, avatarSeed, truncatedMessage, lastMessageTimestamp, hasUnreadMessages, isPrivate))
                    }

                    if (message != null) {
                        val messageContent = when {
                            !message.gifUrl.isNullOrEmpty() -> "GIF"
                            !message.voiceMessageUrl.isNullOrEmpty() -> "Voice Message"
                            !message.imageUrl.isNullOrEmpty() -> "Image"
                            else -> message.messageText
                        }

                        if (isPrivate && message.messageText.isNotEmpty() && messageContent == message.messageText) {
                            val myKeyPair = getKeyPairForConversation(convId)
                            if (myKeyPair == null) {
                                buildAndInvokeCallback("[Encrypted Message]")
                                return@addOnSuccessListener
                            }

                            database.reference.child("conversations").child(convId).child("public_keys").child(otherUserId).get()
                                .addOnSuccessListener { theirPublicKeySnapshot ->
                                    val theirPublicKeyEncoded = theirPublicKeySnapshot.getValue(String::class.java)
                                    val decryptedText = if (theirPublicKeyEncoded != null) {
                                        try {
                                            val theirPublicKey = CryptoManager.decodePublicKeyFromBase64(theirPublicKeyEncoded)
                                            val sharedSecret = CryptoManager.getSharedSecret(myKeyPair.private, theirPublicKey)
                                            CryptoManager.decrypt(message.messageText, sharedSecret) ?: "[Encrypted Message]"
                                        } catch (e: Exception) {
                                            Log.e("Crypto", "Decryption failed in Inbox for conv $convId", e)
                                            "[Encrypted Message]"
                                        }
                                    } else {
                                        "[Encrypted Message]"
                                    }
                                    buildAndInvokeCallback(decryptedText)
                                }
                                .addOnFailureListener {
                                    buildAndInvokeCallback("[Encrypted Message]")
                                }
                        } else {
                            buildAndInvokeCallback(messageContent)
                        }
                    } else {
                        callback(null)
                    }

                }.addOnFailureListener {
                     // Even if conversation load fails (e.g. auth error), return placeholders so UI doesn't hang
                     callback(InboxConversation(convId, otherUserId, nickname, avatarSeed, "...", System.currentTimeMillis(), false, false))
                }
            }
        }.start()
    }


    private fun checkEmptyState(list: List<Any>) {
        if (list.isEmpty()) {
            emptyInboxTextView.visibility = View.VISIBLE
            conversationsRecyclerView.visibility = View.GONE
        } else {
            emptyInboxTextView.visibility = View.GONE
            conversationsRecyclerView.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        titleLoadingHandler.removeCallbacksAndMessages(null)
        conversationDetailsListener?.let { userRef?.child("conversations")?.removeEventListener(it) }
        actionMode?.finish()
    }
}