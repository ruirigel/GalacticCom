package com.rmrbranco.galacticcom

import android.animation.ObjectAnimator
import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.Animation
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.data.model.PirateMerchant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.*
import java.util.concurrent.TimeUnit

class HomeFragment : Fragment() {

    // Firebase & Auth
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase

    // Views
    private lateinit var galaxyNameTextView: TextView
    private lateinit var galaxyPreviewTitleTextView: TextView
    private lateinit var publicMessagesRecyclerView: RecyclerView
    private lateinit var emptyHomeTextView: TextView
    private lateinit var travelStatusTextView: TextView
    private lateinit var galaxyPreviewView: View
    private lateinit var messagesView: View
    private lateinit var fabToggleView: FloatingActionButton
    private lateinit var fabPirateStore: FloatingActionButton
    private lateinit var balloonViews: List<View> // Changed from BalloonTextView
    private lateinit var galaxyLogo: ImageView
    private lateinit var edgeLightingView: EdgeLightingView

    // Listeners & Refs
    private var messagesListener: ChildEventListener? = null
    private var messagesRef: DatabaseReference? = null
    private var userStatusListener: ValueEventListener? = null
    private var userRef: DatabaseReference? = null
    private var merchantListener: ValueEventListener? = null
    private var merchantRef: DatabaseReference? = null
    private lateinit var authStateListener: FirebaseAuth.AuthStateListener

    // State
    private lateinit var messageAdapter: PublicMessageAdapter
    private var mediaPlayer: MediaPlayer? = null
    private var currentGalaxyName: String? = null
    private val handler = Handler(Looper.getMainLooper())
    private val displayedBalloons = mutableSetOf<String>()
    private val messageList = mutableListOf<PublicMessage>()
    private var blockList: Set<String> = emptySet()
    private var merchantBalloonView: View? = null // Changed from BalloonTextView
    
    // Animation
    private val balloonAnimators = mutableMapOf<View, ObjectAnimator>()


    // Other state variables
    private var dialogCountdownHandler: Handler? = null
    private var dialogCountdownRunnable: Runnable? = null
    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null
    private var fadeInAnimation: Animation? = null
    private var hasPlayedInitialAnimation = false
    private var isMessagesViewVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        setupAuthStateListener()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val useVisualView = sharedPreferences.getBoolean("default_view_is_visual", true)
        isMessagesViewVisible = !useVisualView
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindViews(view)
        setupUIState()
        setupListeners()
        fadeInAnimation = AnimationUtils.loadAnimation(context, R.anim.fade_in)
        mediaPlayer = MediaPlayer.create(context, R.raw.receive_signal)
        setupRecyclerView()
    }
    
    override fun onResume() {
        super.onResume()
        // Resume animations for visible balloons
        balloonAnimators.forEach { (view, animator) ->
            if (view.isVisible && animator.isPaused) {
                animator.resume()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Pause all animations to save battery
        balloonAnimators.values.forEach { if (it.isRunning) it.pause() }
    }

    private fun bindViews(view: View) {
        galaxyNameTextView = view.findViewById(R.id.tv_galaxy_name)
        galaxyPreviewTitleTextView = view.findViewById(R.id.tv_galaxy_preview_title)
        publicMessagesRecyclerView = view.findViewById(R.id.rv_public_messages)
        emptyHomeTextView = view.findViewById(R.id.tv_empty_home)
        travelStatusTextView = view.findViewById(R.id.tv_travel_status)
        galaxyPreviewView = view.findViewById(R.id.galaxy_preview_view)
        messagesView = view.findViewById(R.id.messages_view)
        fabToggleView = view.findViewById(R.id.fab_toggle_view)
        fabPirateStore = view.findViewById(R.id.fab_pirate_store)
        galaxyLogo = view.findViewById(R.id.galaxy_logo_preview)
        edgeLightingView = view.findViewById(R.id.edge_lighting_view)
        balloonViews = (1..14).map { i ->
            // Find the LinearLayout now
            view.findViewById<View>(resources.getIdentifier("balloon$i", "id", requireContext().packageName))
        }
    }

    private fun setupUIState() {
        startTitleLoadingAnimation()
        fabPirateStore.isGone = true // Hide by default
        if (hasPlayedInitialAnimation) {
            galaxyLogo.alpha = 1.0f
        } else {
            view?.post { playGalaxyArrivalAnimation() }
            hasPlayedInitialAnimation = true
        }
        if (isMessagesViewVisible) {
            galaxyPreviewView.isGone = true
            messagesView.isVisible = true
            fabToggleView.setImageResource(R.drawable.logotipo)
        } else {
            galaxyPreviewView.isVisible = true
            messagesView.isGone = true
            fabToggleView.setImageResource(R.drawable.ic_list)
        }
    }

    private fun setupListeners() {
        fabToggleView.setOnClickListener {
            isMessagesViewVisible = !isMessagesViewVisible
            if (isMessagesViewVisible) {
                galaxyPreviewView.isGone = true
                messagesView.isVisible = true
                fabToggleView.setImageResource(R.drawable.logotipo)
                balloonViews.forEach { 
                    it.isGone = true
                    stopFloatingAnimation(it)
                }
                displayedBalloons.clear()
            } else {
                galaxyPreviewView.isVisible = true
                messagesView.isGone = true
                fabToggleView.setImageResource(R.drawable.ic_list)
                messageList.forEach { showBalloonNotification(it, withAnimation = false) }
                listenForMerchantStatus() // Re-check merchant status when switching to visual view
            }
        }
        fabPirateStore.setOnClickListener {
            findNavController().navigate(R.id.action_home_to_pirate_store)
        }
    }

    override fun onStart() {
        super.onStart()
        auth.addAuthStateListener(authStateListener)
    }

    override fun onStop() {
        super.onStop()
        auth.removeAuthStateListener(authStateListener)
        clearListenersAndData()
    }

    private fun setupAuthStateListener() {
        authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                userRef = database.reference.child("users").child(user.uid)
                if (view != null) {
                    listenForUserStatus()
                }
            } else {
                clearListenersAndData()
                showErrorState("You are not signed in.")
            }
        }
    }

    private fun clearListenersAndData() {
        userStatusListener?.let { userRef?.removeEventListener(it) }
        messagesListener?.let { messagesRef?.removeEventListener(it) }
        merchantListener?.let { merchantRef?.removeEventListener(it) }
        userStatusListener = null
        messagesListener = null
        merchantListener = null
        // Não limpamos currentGalaxyName aqui para evitar problemas de reentrada
        // currentGalaxyName = null
        messageList.clear()
        if (view != null) {
            updateAdapterAndEmptyView(emptyList())
        }
    }

    private fun showMessagesState(galaxyName: String?) {
        travelStatusTextView.isGone = true
        galaxyNameTextView.isVisible = true
        galaxyPreviewTitleTextView.isVisible = true
        if (galaxyName == null) {
            showErrorState("You are not in a galaxy. Travel to one to see broadcasts.")
            return
        }
        if (galaxyName != currentGalaxyName || messagesListener == null) {
            currentGalaxyName = galaxyName
            listenForGalaxyMessages(galaxyName)
            listenForMerchantStatus() // Listen for merchant when entering a galaxy
        }
    }
    
    // Animation Helpers
    private fun startFloatingAnimation(view: View) {
        if (balloonAnimators.containsKey(view)) {
             val anim = balloonAnimators[view]
             if (anim?.isStarted == true) {
                 if (anim.isPaused) anim.resume()
                 return
             }
             anim?.start()
             return
        }
        
        val offsetPx = 8f * resources.displayMetrics.density
        val animator = ObjectAnimator.ofFloat(view, "translationY", 0f, -offsetPx).apply {
            duration = (2000L..3500L).random()
            startDelay = (0L..1500L).random() // Adds random start delay to desynchronize
            repeatCount = ObjectAnimator.INFINITE
            repeatMode = ObjectAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        balloonAnimators[view] = animator
        animator.start()
    }

    private fun stopFloatingAnimation(view: View) {
        balloonAnimators[view]?.cancel()
        view.translationY = 0f
    }

    // --- NEW MERCHANT LOGIC ---

    private fun listenForMerchantStatus() {
        merchantRef = database.getReference("merchants/captain_silas")
        merchantListener?.let { merchantRef?.removeEventListener(it) } // Remove previous listener

        merchantListener = merchantRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val merchant = snapshot.getValue(PirateMerchant::class.java) ?: return
                val currentTime = System.currentTimeMillis()

                if (currentTime > merchant.visibleUntil) {
                    // Time expired, wait for server to move him. Hide for now.
                    fabPirateStore.hide()
                    hideMerchantBalloon()
                } else {
                    // Merchant is active, check if they are in the user's galaxy
                    updateMerchantVisibility(merchant)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("HomeFragment", "Merchant listener cancelled", error.toException())
            }
        })
    }

    // REMOVED: private fun relocateMerchant(...) as it is now handled by Cloud Functions

    private fun updateMerchantVisibility(merchant: PirateMerchant) {
        if (merchant.currentGalaxy == currentGalaxyName && !isMessagesViewVisible) {
            fabPirateStore.show()
            showMerchantBalloon(merchant)
        } else {
            fabPirateStore.hide()
            hideMerchantBalloon()
        }
    }

    private fun showMerchantBalloon(merchant: PirateMerchant) {
        if (merchantBalloonView != null && merchantBalloonView!!.isVisible) return // Already showing

        // Try to find an empty balloon first
        var targetBalloon = balloonViews.firstOrNull { it.isGone }

        // If no empty balloon, forcefully replace an existing one (the first one)
        if (targetBalloon == null) {
            val balloonToReplace = balloonViews.firstOrNull { it.isVisible && it != merchantBalloonView }
            if (balloonToReplace != null) {
                // Clear any existing data or animations on the balloon we are taking over
                balloonToReplace.clearAnimation()
                (balloonToReplace.tag as? PublicMessage)?.messageId?.let { displayedBalloons.remove(it) }
                targetBalloon = balloonToReplace
            }
        }

        merchantBalloonView = targetBalloon
        
        merchantBalloonView?.let { balloonLayout ->
            val balloonText = balloonLayout.findViewById<TextView>(R.id.balloon1_text) // Assuming IDs are consistent, which they are not
            val balloonIcon = balloonLayout.findViewById<ImageView>(R.id.balloon1_icon) // This needs a better way

            // A better way to find the views inside each balloon
            val textId = resources.getIdentifier(balloonLayout.resources.getResourceName(balloonLayout.id).replace("balloon","balloon") + "_text", "id", requireContext().packageName)
            val iconId = resources.getIdentifier(balloonLayout.resources.getResourceName(balloonLayout.id).replace("balloon","balloon") + "_icon", "id", requireContext().packageName)
            val realText = balloonLayout.findViewById<TextView>(textId)
            val realIcon = balloonLayout.findViewById<ImageView>(iconId)

            balloonLayout.isVisible = true
            startFloatingAnimation(balloonLayout)
            
            realIcon.setImageResource(R.drawable.ic_pirate_ship)
            realIcon.clearColorFilter() // Ensure no tint from messages
            realText.text = merchant.shipName
            balloonLayout.setOnClickListener {
                findNavController().navigate(R.id.action_home_to_pirate_store)
            }
            balloonLayout.startAnimation(fadeInAnimation)
        }
    }


    private fun hideMerchantBalloon() {
        merchantBalloonView?.let {
            if (it.isVisible) {
                hideBalloonWithAnimation(it) { merchantBalloonView = null }
            }
        }
    }

    private fun listenForGalaxyMessages(galaxyName: String) {
        if (view == null) return
        messagesListener?.let { messagesRef?.removeEventListener(it) }
        messageList.clear()
        displayedBalloons.clear()
        balloonViews.forEach { 
            it.isGone = true
            stopFloatingAnimation(it)
        }
        messageAdapter.submitList(emptyList())
        hideMerchantBalloon() // Hide merchant balloon when galaxy changes

        messagesRef = database.reference.child("public_broadcasts").child(galaxyName)
        startTitleLoadingAnimation()

        val currentUserId = auth.currentUser?.uid ?: return

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val userSnapshot = userRef!!.get().await()
                val hiddenMessageIds = userSnapshot.child("hiddenPublicMessages").children.mapNotNull { it.key }.toSet()

                val userConversations = userSnapshot.child("conversations").children.mapNotNull { it.key }

                val activeConversations = userConversations.map { convId ->
                    async {
                        val exists = try {
                            database.getReference("conversations").child(convId).get().await().exists()
                        } catch (e: Exception) {
                            false
                        }
                        if (exists) convId else null
                    }
                }.awaitAll().filterNotNull().toSet()

                val convPartnerIds = activeConversations.mapNotNull { convId ->
                    val ids = convId.split('_')
                    if (ids.size == 2) if (ids[0] == currentUserId) ids[1] else ids[0] else null
                }.toSet()

                val blockedUsers = userSnapshot.child("blockedUsers").children.mapNotNull { it.key }.toSet()
                val usersWhoBlockedMe = userSnapshot.child("usersWhoBlockedMe").children.mapNotNull { it.key }.toSet()
                blockList = blockedUsers + usersWhoBlockedMe

                withContext(Dispatchers.Main) {
                    stopTitleLoadingAnimation(galaxyName)
                    attachMessageListeners(currentUserId, hiddenMessageIds, convPartnerIds)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Log.e("HomeFragment", "Failed to fetch user data for messages", e)
                    showErrorState("Could not load galaxy data.")
                }
            }
        }
    }

    private fun showBalloonNotification(message: PublicMessage, withAnimation: Boolean) {
        if (isMessagesViewVisible) return
        if (message.messageId == null || displayedBalloons.contains(message.messageId)) return

        val availableBalloons = balloonViews.filter { it.isGone && it != merchantBalloonView }

        availableBalloons.firstOrNull()?.let { balloonLayout ->
            displayedBalloons.add(message.messageId!!)
            if (withAnimation) {
                balloonLayout.startAnimation(fadeInAnimation)
            }
            balloonLayout.isVisible = true
            startFloatingAnimation(balloonLayout)

            // Dynamically find the TextView and ImageView within the LinearLayout
            val layoutIdName = resources.getResourceEntryName(balloonLayout.id)
            val textId = resources.getIdentifier("${layoutIdName}_text", "id", requireContext().packageName)
            val iconId = resources.getIdentifier("${layoutIdName}_icon", "id", requireContext().packageName)
            val balloonText = balloonLayout.findViewById<TextView>(textId)
            val balloonIcon = balloonLayout.findViewById<ImageView>(iconId)

            val nickname = message.senderNickname?.replaceFirstChar { it.titlecase(Locale.getDefault()) } ?: ""
            val hasArrived = message.arrivalTime == 0L || System.currentTimeMillis() >= message.arrivalTime

            balloonText.text = nickname
            balloonIcon.setColorFilter(if (hasArrived) ContextCompat.getColor(requireContext(), R.color.material_red_500) else Color.GRAY)

            balloonLayout.tag = message
            balloonLayout.setOnClickListener { showBalloonOptionsDialog(it.tag as PublicMessage, it) }
            val hideRunnable = Runnable { hideBalloonWithAnimation(balloonLayout, this::fillEmptyBalloons) }
            val delay = if (hasArrived) 43200000L else (message.arrivalTime - System.currentTimeMillis()).takeIf { it > 0 } ?: 43200000L
            handler.postDelayed(hideRunnable, delay)
        }
    }

    private fun hideBalloonWithAnimation(balloon: View, onAnimationEnd: () -> Unit) { // Changed from BalloonTextView
        val fadeOut = AnimationUtils.loadAnimation(context, R.anim.fade_out).apply {
            setAnimationListener(object : Animation.AnimationListener {
                override fun onAnimationStart(animation: Animation?) {}
                override fun onAnimationEnd(animation: Animation?) {
                    balloon.isGone = true
                    stopFloatingAnimation(balloon)
                    (balloon.tag as? PublicMessage)?.messageId?.let { displayedBalloons.remove(it) }
                    onAnimationEnd.invoke()
                }
                override fun onAnimationRepeat(animation: Animation?) {}
            })
        }
        balloon.startAnimation(fadeOut)
    }

    private fun playGalaxyArrivalAnimation() { galaxyLogo.alpha = 0f; galaxyLogo.animate().alpha(1.0f).setDuration(300).setInterpolator(DecelerateInterpolator()).start() }
    private fun startTitleLoadingAnimation() { titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }; titleLoadingRunnable = object : Runnable { private var dotCount = 0; override fun run() { dotCount = (dotCount + 1) % 4; val dots = ".".repeat(dotCount); val titleText = "Broadcast in$dots"; galaxyNameTextView.text = titleText; galaxyPreviewTitleTextView.text = titleText; titleLoadingHandler.postDelayed(this, 500) } }; titleLoadingHandler.post(titleLoadingRunnable!!) }
    private fun stopTitleLoadingAnimation(galaxyName: String) { titleLoadingHandler.removeCallbacksAndMessages(null); val titleText = "Broadcast in $galaxyName"; galaxyNameTextView.text = titleText; galaxyPreviewTitleTextView.text = titleText }
    
    private fun listenForUserStatus() { 
        userStatusListener?.let { userRef?.removeEventListener(it) }; 
        userStatusListener = userRef?.addValueEventListener(object : ValueEventListener { 
            override fun onDataChange(snapshot: DataSnapshot) { 
                val travelCompletionTimestamp = snapshot.child("travelCompletionTimestamp").getValue(Long::class.java)
                val galaxyName = snapshot.child("galaxy").getValue(String::class.java)
                val travelType = snapshot.child("travelType").getValue(String::class.java)
                
                // Show traveling state ONLY if timestamp is active AND travelType is INTERGALACTIC
                if (travelCompletionTimestamp != null && System.currentTimeMillis() < travelCompletionTimestamp && travelType == "INTERGALACTIC") { 
                    showTravelingState(travelCompletionTimestamp, galaxyName) 
                } else { 
                    showMessagesState(galaxyName) 
                } 
            } 
            override fun onCancelled(error: DatabaseError) { Log.e("HomeFragment", "User status listener cancelled", error.toException()); showErrorState("Could not load user data.") } 
        }) 
    }
    
    // CHANGED: Instead of navigating, just show a message.
    private fun showTravelingState(completionTimestamp: Long, currentGalaxy: String?) {
        val remainingTime = completionTimestamp - System.currentTimeMillis()
        if (remainingTime > 0) {
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remainingTime)
            travelStatusTextView.text = "Intergalactic Travel in progress... ($seconds s)"
            travelStatusTextView.isVisible = true
            
            // Allow the user to CLICK the status to go to the animation screen if they want
            travelStatusTextView.setOnClickListener {
                findNavController().navigate(R.id.action_home_to_travel, bundleOf("completion_timestamp" to completionTimestamp, "currentUserGalaxy" to currentGalaxy, "targetGalaxy" to "Unknown Galaxy"))
            }
            
            // Hide other content
            publicMessagesRecyclerView.isGone = true
            galaxyNameTextView.isGone = true
            emptyHomeTextView.isGone = true
            
        } else { 
            showMessagesState(currentGalaxy) 
        } 
    }

    private fun attachMessageListeners(currentUserId: String, hiddenIds: Set<String>, convPartnerIds: Set<String>) {
        var initialDataLoaded = false
        messagesListener = messagesRef?.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!initialDataLoaded) return
                val message = snapshot.getValue(PublicMessage::class.java)?.also { it.messageId = snapshot.key }
                if (message == null || messageList.any { it.messageId == message.messageId }) return

                val shouldDisplay = message.senderId != currentUserId && message.messageId !in hiddenIds && message.senderId !in convPartnerIds && message.senderId !in blockList

                if (shouldDisplay) {
                    edgeLightingView.startAnimation(false)
                    messageList.add(0, message)
                    updateAdapterAndEmptyView(messageList.sortedByDescending { (it.timestamp as? Long) ?: 0L })
                    showBalloonNotification(message, withAnimation = true)
                    mediaPlayer?.start()
                }
            }
            override fun onChildRemoved(snapshot: DataSnapshot) { if (messageList.removeAll { it.messageId == snapshot.key }) { updateAdapterAndEmptyView(messageList) } }
            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) { Log.e("HomeFragment", "Message listener cancelled", error.toException()) }
        })

        messagesRef?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val initialMessages = snapshot.children.mapNotNull { data ->
                    data.getValue(PublicMessage::class.java)?.also { it.messageId = data.key }
                }.filter { message ->
                    val isHidden = message.messageId in hiddenIds
                    val isChatting = message.senderId in convPartnerIds
                    val isBlocked = message.senderId in blockList
                    val isMe = message.senderId == currentUserId

                    if (isHidden && !isChatting) {
                        if (message.messageId != null) {
                             userRef?.child("hiddenPublicMessages")?.child(message.messageId!!)?.removeValue()
                        }
                        !isMe && !isBlocked
                    } else {
                        !isMe && !isHidden && !isChatting && !isBlocked
                    }
                }
                updateAdapterAndEmptyView(initialMessages.sortedByDescending { (it.timestamp as? Long) ?: 0L })
                messageList.forEach { showBalloonNotification(it, withAnimation = false) }
                initialDataLoaded = true
            }
            override fun onCancelled(error: DatabaseError) { Log.e("HomeFragment", "Initial data load cancelled.", error.toException()); showErrorState("Could not load messages.") }
        })
    }
    private fun updateAdapterAndEmptyView(messages: List<PublicMessage>) { messageList.clear(); messageList.addAll(messages); messageAdapter.submitList(messageList.toList()); emptyHomeTextView.isGone = messageList.isNotEmpty(); publicMessagesRecyclerView.isVisible = messageList.isNotEmpty(); if(messageList.isEmpty()) { emptyHomeTextView.text = "Deep space is quiet...\nNo foreign transmissions detected." } }
    private fun binaryToText(binary: String): String { return try { binary.split(" ").joinToString("") { Integer.parseInt(it, 2).toChar().toString() } } catch (e: Exception) { binary } }
    private fun showBalloonOptionsDialog(message: PublicMessage, balloon: View?) { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_balloon_options); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val catastropheWarningTextView = dialog.findViewById<TextView>(R.id.dialog_catastrophe_warning); val messageContent = dialog.findViewById<TextView>(R.id.dialog_message_content); val messageSender = dialog.findViewById<TextView>(R.id.dialog_message_sender); val timestamp = dialog.findViewById<TextView>(R.id.dialog_message_timestamp); val countdownTextView = dialog.findViewById<TextView>(R.id.dialog_countdown); val btnView = dialog.findViewById<Button>(R.id.btn_view); val btnInterceptReply = dialog.findViewById<Button>(R.id.btn_intercept_reply); val btnClose = dialog.findViewById<Button>(R.id.btn_close); val btnDelete = dialog.findViewById<Button>(R.id.btn_delete); catastropheWarningTextView.text = message.catastropheType; catastropheWarningTextView.isVisible = message.catastropheType != null; val neonCyanColor = ContextCompat.getColor(requireContext(), R.color.neon_cyan); val hasArrived = message.arrivalTime == 0L || System.currentTimeMillis() >= message.arrivalTime; val contentToDisplay = message.message ?: ""; val displayContent = contentToDisplay.take(35) + if (contentToDisplay.length > 35) "..." else ""; messageContent.text = SpannableString("Msg: $displayContent").apply { setSpan(ForegroundColorSpan(neonCyanColor), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) }; messageSender.text = SpannableString("from: ${message.senderGalaxy} by ${message.senderNickname}").apply { setSpan(ForegroundColorSpan(neonCyanColor), 0, 5, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE); indexOf(" by ").takeIf { it != -1 }?.let { setSpan(ForegroundColorSpan(neonCyanColor), it, it + 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) } }; (message.timestamp as? Long)?.let { timestamp.text = TimeUtils.getRelativeTimeSpanString(it) }; dialogCountdownHandler?.removeCallbacksAndMessages(null); if (!hasArrived) { btnView.isGone = true; btnInterceptReply.isGone = true; btnDelete.isGone = true; countdownTextView.isVisible = true; dialogCountdownHandler = Handler(Looper.getMainLooper()); dialogCountdownRunnable = object : Runnable { override fun run() { val remaining = message.arrivalTime - System.currentTimeMillis(); if (remaining > 0) { val hours = TimeUnit.MILLISECONDS.toHours(remaining); val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60; countdownTextView.text = String.format("Arriving in: %02d:%02d", hours, minutes).uppercase(); dialogCountdownHandler?.postDelayed(this, 1000) } else { countdownTextView.isGone = true; btnView.isVisible = true; btnInterceptReply.isVisible = true; btnDelete.isVisible = true; val newContent = message.message ?: ""; messageContent.text = SpannableString("Msg: ${newContent.take(35) + if (newContent.length > 35) "..." else ""}").apply { setSpan(ForegroundColorSpan(neonCyanColor), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) } } } }.also { dialogCountdownHandler?.post(it) } } else { countdownTextView.isGone = true; btnView.isVisible = true; btnInterceptReply.isVisible = true; btnDelete.isVisible = true }; dialog.setOnDismissListener { dialogCountdownHandler?.removeCallbacksAndMessages(null) }; btnView.setOnClickListener { showViewMessageDialog(message); dialog.dismiss() }; btnClose.setOnClickListener { dialog.dismiss() }; btnDelete.setOnClickListener { dialog.dismiss(); showDeleteMessageConfirmationDialog(message, balloon) }; btnInterceptReply.setOnClickListener { if (message.senderId in blockList) { Toast.makeText(context, "You cannot start a conversation with a blocked user.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; val messageText = binaryToText(message.message ?: ""); findNavController().navigate(R.id.inboxFragment, bundleOf("navigateToConversation" to true, "senderId" to message.senderId, "originalMessageId" to message.messageId, "originalGalaxyName" to message.senderGalaxy, "quotedMessageContent" to messageText, "quotedMessageAuthor" to message.senderNickname)); dialog.dismiss() }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun showDeleteMessageConfirmationDialog(message: PublicMessage, balloon: View?) { Dialog(requireContext()).apply { setContentView(R.layout.dialog_custom); window?.setBackgroundDrawableResource(android.R.color.transparent); findViewById<TextView>(R.id.dialog_title).text = "Confirm Deletion"; findViewById<TextView>(R.id.dialog_message).text = "Are you sure you want to delete this transmission? It will be hidden from you permanently."; findViewById<Button>(R.id.dialog_negative_button).text = "CANCEL"; findViewById<Button>(R.id.dialog_positive_button).text = "DELETE"; findViewById<View>(R.id.dialog_subtitle).isGone = true; findViewById<View>(R.id.galaxy_stats_layout).isGone = true; findViewById<TextView>(R.id.dialog_message).gravity = Gravity.START; findViewById<TextView>(R.id.dialog_title).gravity = Gravity.START; findViewById<Button>(R.id.dialog_negative_button).setOnClickListener { dismiss(); balloon?.let { showBalloonOptionsDialog(message, it) } }; findViewById<Button>(R.id.dialog_positive_button).setOnClickListener { deleteMessage(message, balloon); dismiss() }; show(); window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) } }
    private fun deleteMessage(message: PublicMessage, balloon: View?) { val messageId = message.messageId ?: return; val userId = auth.currentUser?.uid ?: return; if (messageList.removeAll { it.messageId == messageId }) { updateAdapterAndEmptyView(messageList.toList()) }; val balloonToHide = balloon ?: balloonViews.find { (it.tag as? PublicMessage)?.messageId == messageId }; balloonToHide?.let { hideBalloonWithAnimation(it, this::fillEmptyBalloons) }; userRef?.child("hiddenPublicMessages")?.child(messageId)?.setValue(true)?.addOnSuccessListener { Toast.makeText(context, "Transmission deleted.", Toast.LENGTH_SHORT).show() } }
    private fun fillEmptyBalloons() { messageList.firstOrNull { it.messageId !in displayedBalloons }?.let { showBalloonNotification(it, withAnimation = false) } }
    private fun showErrorState(message: String) { if (!isAdded) return; currentGalaxyName?.let { stopTitleLoadingAnimation(it) }; publicMessagesRecyclerView.isGone = true; travelStatusTextView.isGone = true; galaxyNameTextView.isGone = true; emptyHomeTextView.text = message; emptyHomeTextView.isVisible = true }
    private fun showViewMessageDialog(message: PublicMessage) { Dialog(requireContext()).apply { setContentView(R.layout.dialog_view_message); window?.setBackgroundDrawableResource(android.R.color.transparent); val neonCyanColor = ContextCompat.getColor(context, R.color.neon_cyan); findViewById<TextView>(R.id.tv_dialog_sender).text = SpannableString("from: ${message.senderGalaxy} by ${message.senderNickname}").apply { setSpan(ForegroundColorSpan(neonCyanColor), 0, 5, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE); indexOf(" by ").takeIf { it != -1 }?.let { setSpan(ForegroundColorSpan(neonCyanColor), it, it + 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) } }; findViewById<TextView>(R.id.tv_message_content).text = SpannableString("Msg: ${binaryToText(message.message ?: "")}").apply { setSpan(ForegroundColorSpan(neonCyanColor), 0, 4, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE) }; findViewById<Button>(R.id.btn_close).setOnClickListener { dismiss() }; show(); window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) } }
    private fun setupRecyclerView() { messageAdapter = PublicMessageAdapter( onMessageClick = { message -> if (message.senderId == auth.currentUser?.uid) { Toast.makeText(context, "You cannot start a conversation with yourself.", Toast.LENGTH_SHORT).show() } else if (message.senderId in blockList) { Toast.makeText(context, "You cannot start a conversation with a blocked user.", Toast.LENGTH_SHORT).show() } else { val messageText = binaryToText(message.message ?: ""); findNavController().navigate(R.id.inboxFragment, bundleOf("navigateToConversation" to true, "senderId" to message.senderId, "originalMessageId" to message.messageId, "originalGalaxyName" to message.senderGalaxy, "quotedMessageContent" to messageText, "quotedMessageAuthor" to message.senderNickname)) } }, onDeleteClick = { message -> showDeleteMessageConfirmationDialog(message, null) }, onViewClick = { message -> showViewMessageDialog(message) } ); publicMessagesRecyclerView.layoutManager = LinearLayoutManager(context); publicMessagesRecyclerView.adapter = messageAdapter }
    override fun onDestroyView() { 
        super.onDestroyView() 
        handler.removeCallbacksAndMessages(null) 
        titleLoadingHandler.removeCallbacksAndMessages(null) 
        dialogCountdownHandler?.removeCallbacksAndMessages(null) 
        mediaPlayer?.release() 
        mediaPlayer = null 
        merchantBalloonView = null
        balloonAnimators.values.forEach { it.cancel() }
        balloonAnimators.clear()
    }

}
