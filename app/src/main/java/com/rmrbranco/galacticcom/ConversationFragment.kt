package com.rmrbranco.galacticcom

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.Log
import android.view.*
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.rmrbranco.galacticcom.data.managers.BadgeProgressManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.KeyFactory
import java.security.KeyPair
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.*
import javax.crypto.SecretKey

class ConversationFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var storage: FirebaseStorage

    private val args: ConversationFragmentArgs by navArgs()
    private var conversationId: String? = null
    private val currentUserId: String by lazy { auth.currentUser!!.uid }
    private var recipientId: String? = null
    private var isPrivate: Boolean = true
    private var conversationExists: Boolean = false

    // Encryption properties
    private var sharedSecretKey: SecretKey? = null
    private var keyExchangeListener: ValueEventListener? = null
    private var publicKeysRef: DatabaseReference? = null

    private lateinit var messagesRecyclerView: RecyclerView
    private lateinit var replyEditText: EditText
    private lateinit var sendReplyButton: ImageButton
    private lateinit var typingIndicator: TextView
    private lateinit var loadingProgressBar: ProgressBar
    private lateinit var attachImageButton: ImageButton
    private lateinit var emojiButton: ImageButton
    private lateinit var scrollToBottomButton: ImageButton

    // Custom Toolbar Views
    private lateinit var avatarImageView: ImageView
    private lateinit var usernameTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var settingsButton: ImageButton


    private lateinit var quotedMessageContainer: FrameLayout
    private lateinit var quotedAuthorTextView: TextView
    private lateinit var quotedMessageTextView: TextView
    private lateinit var quotedGifImageView: ImageView
    private lateinit var cancelReplyButton: ImageButton
    private lateinit var quotedVoiceMessageContainer: LinearLayout
    private lateinit var quotedVoiceDurationTextView: TextView

    private lateinit var conversationAdapter: ConversationAdapter
    private val chatMessages = mutableListOf<DisplayMessage>()
    private var messageListener: ChildEventListener? = null
    private var messagesRef: DatabaseReference? = null
    private var isListenerAttached = false

    // Pagination variables
    private var isLoadingMore = false
    private var lastLoadedMessageKey: String? = null
    private val MESSAGES_PER_PAGE = 20

    private var currentUserNickname: String = ""
    private var recipientNickname: String = ""
    private var currentUserAvatarSeed: String = ""
    private var recipientAvatarSeed: String = ""

    private var typingStatusRef: DatabaseReference? = null
    private var typingListener: ValueEventListener? = null
    private val typingHandler = Handler(Looper.getMainLooper())
    private var isTypingStateSent = false // Optimization to avoid spamming DB

    private var actionMode: ActionMode? = null
    private var messageToEdit: ChatMessage? = null
    private var messageToReply: ChatMessage? = null

    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null

    // Voice Message Components
    private lateinit var voiceMessageButton: ImageButton
    private lateinit var voiceMessageChronometer: Chronometer
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var recordingStartTime: Long = 0
    private var mediaPlayer: MediaPlayer? = null
    private var currentlyPlayingMessageId: String? = null
    private val seekBarUpdateHandler = Handler(Looper.getMainLooper())
    private var seekBarUpdateRunnable: Runnable? = null
    private var adapterDataObserver: RecyclerView.AdapterDataObserver? = null
    
    // Voice Recording Limit
    private val recordingLimitHandler = Handler(Looper.getMainLooper())
    private val recordingLimitRunnable = Runnable {
        if (isRecording) {
            stopRecordingAndSend()
            Toast.makeText(context, "Maximum recording time reached (1 min)", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Prevention of duplicate messages
    private var isSendingMessage = false

    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (!isGranted) {
            Toast.makeText(context, "Permission for recording audio is required for this feature.", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { handleImageSelection(it) }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            findNavController().popBackStack()
            return null
        }
        database = FirebaseDatabase.getInstance()
        storage = FirebaseStorage.getInstance()

        return inflater.inflate(R.layout.fragment_conversation, container, false)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (auth.currentUser == null) {
            Toast.makeText(context, getString(R.string.authentication_error), Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
            return
        }

        bindViews(view)
        setupClickListeners()
        setupInputProperties()

        args.recipientId?.let { recId ->
            this.recipientId = recId
            this.conversationId = getConversationId(currentUserId, recId)

            setupRecyclerView()
            setupTypingIndicator()
            setupRichContentReceiver()

            database.reference.child("conversations").child(conversationId!!).child("isPrivate").get().addOnSuccessListener {
                conversationExists = it.exists()
                isPrivate = it.getValue(Boolean::class.java) ?: false
                loadNicknamesAndListen() // Load nicknames after determining if private and if exists
            }

            // New logic to handle incoming quote from HomeFragment
            val quotedContent = arguments?.getString("quotedMessageContent")
            val quotedAuthor = arguments?.getString("quotedMessageAuthor")
            if (!quotedContent.isNullOrEmpty() && !quotedAuthor.isNullOrEmpty()) {
                setupInitialQuote(quotedContent, quotedAuthor)
            }

        } ?: run {
            Toast.makeText(context, getString(R.string.invalid_recipient), Toast.LENGTH_LONG).show()
            findNavController().popBackStack()
        }
    }

    private fun bindViews(view: View) {
        messagesRecyclerView = view.findViewById(R.id.rv_messages)
        replyEditText = view.findViewById(R.id.et_reply)
        sendReplyButton = view.findViewById(R.id.btn_send_reply)
        typingIndicator = view.findViewById(R.id.tv_typing_indicator)
        loadingProgressBar = view.findViewById(R.id.pb_loading)
        attachImageButton = view.findViewById(R.id.btn_attach_image)
        emojiButton = view.findViewById(R.id.btn_emoji)
        scrollToBottomButton = view.findViewById(R.id.fab_scroll_to_bottom)

        // Toolbar Views
        backButton = view.findViewById(R.id.btn_back)
        avatarImageView = view.findViewById(R.id.iv_avatar)
        usernameTextView = view.findViewById(R.id.tv_username)
        settingsButton = view.findViewById(R.id.btn_settings)

        quotedMessageContainer = view.findViewById(R.id.quoted_message_container)
        quotedAuthorTextView = view.findViewById(R.id.tv_quoted_author)
        quotedMessageTextView = view.findViewById(R.id.tv_quoted_text)
        quotedGifImageView = view.findViewById(R.id.iv_quoted_gif)
        cancelReplyButton = view.findViewById(R.id.btn_cancel_reply)
        quotedVoiceMessageContainer = view.findViewById(R.id.quoted_voice_message_container)
        quotedVoiceDurationTextView = view.findViewById(R.id.tv_quoted_voice_duration)
        voiceMessageButton = view.findViewById(R.id.btn_voice_message)
        voiceMessageChronometer = view.findViewById(R.id.chronometer_voice_message)
    }

    private fun setupInputProperties() {
        replyEditText.imeOptions = EditorInfo.IME_ACTION_SEND
        replyEditText.setRawInputType(InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        backButton.setOnClickListener { findNavController().popBackStack() }
        settingsButton.setOnClickListener { findNavController().navigate(R.id.settingsFragment) }
        
        cancelReplyButton.setOnClickListener { cancelReply() }
        attachImageButton.setOnClickListener { pickImageLauncher.launch("image/*") }
        emojiButton.setOnClickListener { 
            replyEditText.requestFocus()
            val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(replyEditText, InputMethodManager.SHOW_IMPLICIT)
            // Note: Cannot force system keyboard to open specifically in emoji tab programmatically
            // A custom emoji picker view would be required for full functionality
            Toast.makeText(context, "Use the emoji key on your keyboard", Toast.LENGTH_SHORT).show()
        }
        scrollToBottomButton.setOnClickListener { messagesRecyclerView.smoothScrollToPosition(conversationAdapter.itemCount - 1) }
        sendReplyButton.setOnClickListener { checkPrivateMessageLimitAndSend() }
        avatarImageView.setOnClickListener { navigateToUserProfile() }

        voiceMessageButton.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        startRecording()
                    } else {
                        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isRecording) {
                        stopRecordingAndSend()
                    }
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }
    
    // ... (Lifecycle and other setup methods)
    
    private fun resetSendingState() {
        isSendingMessage = false
        sendReplyButton.isEnabled = true
        sendReplyButton.alpha = 1.0f
    }
    
    private fun checkPrivateMessageLimitAndSend() {
        // Prevent duplicate clicks
        if (isSendingMessage) return
        val replyText = replyEditText.text.toString().trim()
        if (replyText.isEmpty()) return
        
        // Lock UI
        isSendingMessage = true
        sendReplyButton.isEnabled = false
        sendReplyButton.alpha = 0.5f

        if (isPrivate && sharedSecretKey == null) {
            Toast.makeText(context, "Cannot send message. Secure connection not yet established.", Toast.LENGTH_SHORT).show()
            resetSendingState()
            return
        }

        val actionLogsRef = database.getReference("users/$currentUserId/actionLogs")
        actionLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastMessageTimestamp = snapshot.child("lastPrivateMessageTimestamp").getValue(Long::class.java) ?: 0L
                var messageCountToday = snapshot.child("privateMessageCountToday").getValue(Int::class.java) ?: 0

                val today = Calendar.getInstance()
                val lastMessageDay = Calendar.getInstance().apply { timeInMillis = lastMessageTimestamp }

                if (today.get(Calendar.DAY_OF_YEAR) != lastMessageDay.get(Calendar.DAY_OF_YEAR) ||
                    today.get(Calendar.YEAR) != lastMessageDay.get(Calendar.YEAR)) {
                    messageCountToday = 0 // Reset counter for the new day
                }

                val dailyLimit = SettingsManager.getPrivateMessageDailyLimit()

                if (messageCountToday < dailyLimit || messageToEdit != null) {
                    sendOrUpdateReply(messageCountToday, actionLogsRef)
                } else {
                    // Daily limit reached. Check Inventory for Clandestine Cargo.
                    val inventoryRef = database.getReference("users/$currentUserId/inventory/private_messages")
                    inventoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(invSnapshot: DataSnapshot) {
                            val extraMessages = invSnapshot.getValue(Int::class.java) ?: 0
                            if (extraMessages > 0) {
                                // Consume one extra message credit
                                inventoryRef.setValue(extraMessages - 1).addOnSuccessListener {
                                    Toast.makeText(context, "Used 1 Clandestine Cargo credit. Remaining: ${extraMessages - 1}", Toast.LENGTH_SHORT).show()
                                    // Send message without blocking
                                    sendOrUpdateReply(messageCountToday, actionLogsRef)
                                }.addOnFailureListener {
                                    Toast.makeText(context, "Failed to update inventory.", Toast.LENGTH_SHORT).show()
                                    resetSendingState()
                                }
                            } else {
                                Toast.makeText(context, "Daily limit reached. Visit the Pirate Store to buy more.", Toast.LENGTH_LONG).show()
                                resetSendingState()
                            }
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(context, "Failed to verify inventory.", Toast.LENGTH_SHORT).show()
                            resetSendingState()
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to verify daily limit. Please try again.", Toast.LENGTH_SHORT).show()
                resetSendingState()
            }
        })
    }
    
    private fun sendOrUpdateReply(currentCount: Int? = null, logsRef: DatabaseReference? = null) {
        val replyText = replyEditText.text.toString().trim()
        if (replyText.isEmpty()) {
            resetSendingState()
            return
        }

        typingStatusRef?.child(currentUserId)?.setValue(false)
        typingHandler.removeCallbacksAndMessages(null)
        isTypingStateSent = false
        
        // Ensure our public key is published before sending, just in case
        if (!conversationExists) {
             val myKeyPair = getKeyPairForConversation()
             val myPublicKeyEncoded = CryptoManager.encodeKeyToBase64(myKeyPair.public)
             publicKeysRef?.child(currentUserId)?.setValue(myPublicKeyEncoded)
             conversationExists = true // Now it exists potentially
        }

        val textToSend = if (isPrivate) {
            sharedSecretKey?.let { CryptoManager.encrypt(replyText, it) }
        } else {
            replyText
        }

        if (isPrivate && textToSend == null) {
            Toast.makeText(context, getString(R.string.encryption_failed), Toast.LENGTH_SHORT).show()
            resetSendingState()
            return
        }

        if (messageToEdit != null) {
            messageToEdit?.id?.let {
                val updateMap = mapOf("messageText" to textToSend, "isEdited" to true)
                messagesRef?.child(it)?.updateChildren(updateMap)?.addOnSuccessListener {
                    replyEditText.text.clear()
                    messageToEdit = null
                    resetSendingState()
                }?.addOnFailureListener {
                    Toast.makeText(context, "Failed to edit message", Toast.LENGTH_SHORT).show()
                    resetSendingState()
                }
            } ?: resetSendingState()
        } else {
            val currentConvId = conversationId ?: run { resetSendingState(); return }
            val recId = recipientId ?: run { resetSendingState(); return }
            val isFirstMessage = conversationAdapter.itemCount == 0
            val messageRef = database.reference.child("conversations").child(currentConvId).child("messages").push()

            val chatMessage = if (messageToReply != null) {
                val quotedText = if(isPrivate) sharedSecretKey?.let { CryptoManager.encrypt(messageToReply!!.messageText, it)} else messageToReply!!.messageText
                ChatMessage(
                    senderId = currentUserId, messageText = textToSend ?: "", timestamp = ServerValue.TIMESTAMP,
                    quotedMessageText = quotedText,
                    quotedMessageAuthor = if (messageToReply?.senderId == currentUserId) currentUserNickname else recipientNickname,
                    quotedMessageGifUrl = messageToReply?.gifUrl, quotedMessageVoiceUrl = messageToReply?.voiceMessageUrl,
                    quotedMessageVoiceDuration = messageToReply?.voiceMessageDuration
                )
            } else {
                ChatMessage(currentUserId, textToSend ?: "", ServerValue.TIMESTAMP)
            }

            replyEditText.text.clear()
            cancelReply()

            messageRef.setValue(chatMessage).addOnSuccessListener {
                ExperienceUtils.incrementExperience(currentUserId)
                
                if (currentCount != null && logsRef != null) {
                    val updates = mapOf(
                        "lastPrivateMessageTimestamp" to ServerValue.TIMESTAMP,
                        "privateMessageCountToday" to (currentCount + 1)
                    )
                    logsRef.updateChildren(updates)
                }

                if (isFirstMessage && args.originalMessageId != null) {
                    initiateConversationAndSendInvitation(currentConvId, recId)
                } else if (args.originalMessageId != null) {
                    hidePublicMessage()
                }
                resetSendingState()
            }.addOnFailureListener { 
                Toast.makeText(context, getString(R.string.failed_to_send_reply), Toast.LENGTH_SHORT).show()
                resetSendingState()
            }
        }
    }

    private fun setupInitialQuote(content: String, author: String) {
        messageToReply = ChatMessage(senderId = recipientId ?: "", messageText = content, timestamp = 0L)
        quotedMessageContainer.visibility = View.VISIBLE
        quotedAuthorTextView.text = author
        quotedMessageTextView.text = content
        quotedMessageTextView.visibility = View.VISIBLE
        quotedGifImageView.visibility = View.GONE
        quotedVoiceMessageContainer.visibility = View.GONE
        replyEditText.requestFocus()
        val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(replyEditText, InputMethodManager.SHOW_IMPLICIT)
    }

    // --- ENCRYPTION KEY EXCHANGE ---
    private fun initKeyExchange() {
        if (isPrivate) {
            replyEditText.isEnabled = false
            replyEditText.hint = "Establishing secure channel..."
        }

        val myKeyPair = getKeyPairForConversation()
        publicKeysRef = database.reference.child("conversations").child(conversationId!!).child("public_keys")

        val myPublicKeyEncoded = CryptoManager.encodeKeyToBase64(myKeyPair.public)
        
        // Prevent DB pollution: Only write public key if conversation already exists
        if (conversationExists) {
            publicKeysRef!!.child(currentUserId).setValue(myPublicKeyEncoded)
        }

        // Se nao for privado, carrega logo as mensagens (para convites)
        if (!isPrivate) {
            attachListeners()
        }

        keyExchangeListener = publicKeysRef!!.child(recipientId!!).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val theirPublicKeyEncoded = snapshot.getValue(String::class.java)
                if (theirPublicKeyEncoded != null) {
                    try {
                        val theirPublicKey = CryptoManager.decodePublicKeyFromBase64(theirPublicKeyEncoded)
                        sharedSecretKey = CryptoManager.getSharedSecret(myKeyPair.private, theirPublicKey)
                        Log.d("Crypto", "Shared secret generated successfully.")

                        // Upgrade conversation to private if it wasn't already
                        if (!isPrivate) {
                            isPrivate = true
                            database.reference.child("conversations").child(conversationId!!).child("isPrivate").setValue(true)
                            // A re-conexão será feita abaixo apenas se necessário
                        }

                        replyEditText.isEnabled = true
                        replyEditText.hint = getString(R.string.type_a_reply)
                        
                        // Garante que estamos a ouvir com a nova chave (se aplicável)
                        // Apenas faz re-attach se ainda não estiver anexado ou se acabou de mudar para privado
                        attachListeners()

                    } catch (e: Exception) {
                        Log.e("Crypto", "Error generating shared secret", e)
                        if (isPrivate) {
                             Toast.makeText(context, "Failed to create secure channel.", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Crypto", "Key exchange listener cancelled", error.toException())
                 if (isPrivate) {
                    Toast.makeText(context, "Failed to retrieve security keys.", Toast.LENGTH_LONG).show()
                 }
            }
        })
    }
    
    private fun getKeyPairForConversation(): KeyPair {
        val prefs = requireActivity().getSharedPreferences("CryptoPrefs", Context.MODE_PRIVATE)
        val privateKeyPrefKey = "private_key_${conversationId}"
        val publicKeyPrefKey = "public_key_${conversationId}"

        val privateKeyEncoded = prefs.getString(privateKeyPrefKey, null)
        val publicKeyEncoded = prefs.getString(publicKeyPrefKey, null)

        return if (privateKeyEncoded != null && publicKeyEncoded != null) {
            val keyFactory = KeyFactory.getInstance("EC")
            val privateKey = keyFactory.generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyEncoded)))
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyEncoded)))
            KeyPair(publicKey, privateKey)
        } else {
            val keyPair = CryptoManager.generateKeyPair()
            prefs.edit()
                .putString(privateKeyPrefKey, Base64.getEncoder().encodeToString(keyPair.private.encoded))
                .putString(publicKeyPrefKey, Base64.getEncoder().encodeToString(keyPair.public.encoded))
                .apply()
            keyPair
        }
    }


    override fun onResume() {
        super.onResume()
        attachListeners()
        markConversationAsRead()
        // Update AppLifecycleObserver to prevent push notifications for this conversation
        conversationId?.let { AppLifecycleObserver.currentConversationId = it }
    }

    override fun onPause() {
        super.onPause()
        stopAudio()
        detachListeners()
        // Clear AppLifecycleObserver to allow push notifications again
        AppLifecycleObserver.currentConversationId = null
    }

    private fun attachListeners() {
        // Evitar re-anexar se já estiver anexado e a configuração for a mesma
        // Mas como a chave pode ter mudado, fazemos o detach por segurança, mas tentamos ser rápidos
        if (isListenerAttached) {
             detachListeners()
        }

        if (isPrivate && sharedSecretKey == null) {
            // Ainda a aguardar chave, não anexa nada exceto se for para mostrar vazio
            return
        }

        isListenerAttached = true
        Log.d("ConversationFragment", "Attaching listeners...")
        listenForMessages()
        listenForTypingStatus()
    }

    private fun detachListeners() {
        if (messageListener != null) {
            messagesRef?.removeEventListener(messageListener!!)
            messageListener = null
        }
        if (typingListener != null) {
            recipientId?.let { rId ->
                typingStatusRef?.child(rId)?.removeEventListener(typingListener!!)
            }
            typingListener = null
        }
        // Nota: Não removemos o keyExchangeListener aqui porque ele deve persistir enquanto o fragmento vive
        // ou ser gerido separadamente. Mas para simplificar, mantemos como estava.
        
        if (auth.currentUser != null) {
            typingStatusRef?.child(currentUserId)?.onDisconnect()?.cancel()
        }
        isListenerAttached = false
        Log.d("ConversationFragment", "All listeners detached.")
    }
    
    private suspend fun mapToDisplayMessage(data: DataSnapshot, updateSeenStatus: Boolean): DisplayMessage? {
        // Executar parsing e desencriptação em background
        return withContext(Dispatchers.Default) {
            val message = data.getValue(ChatMessage::class.java)?.apply { id = data.key } ?: return@withContext null
            
            // Side-effect: atualização de base de dados (deve ser rápido, mas idealmente fora daqui)
            if (updateSeenStatus && message.senderId == recipientId && !message.isSeen) {
                // Dispara e esquece, não bloqueia
                data.ref.child("isSeen").setValue(true)
            }

            val decryptedMessageText = if (isPrivate && message.messageText.isNotEmpty()) {
                try {
                    sharedSecretKey?.let { CryptoManager.decrypt(message.messageText, it) } ?: message.messageText
                } catch (e: Exception) {
                    message.messageText 
                }
            } else {
                message.messageText
            }

            val decryptedQuotedText = if (isPrivate && !message.quotedMessageText.isNullOrEmpty()) {
                try {
                    sharedSecretKey?.let { CryptoManager.decrypt(message.quotedMessageText!!, it) } ?: message.quotedMessageText
                } catch (e: Exception) {
                    message.quotedMessageText
                }
            } else {
                message.quotedMessageText
            }

            // Correção para ordenação de mensagens pendentes (ServerValue.TIMESTAMP)
            // Se timestamp não for Long (é um Map), usa tempo atual para ficar no fundo
            val safeTimestamp = if (message.timestamp is Long) message.timestamp else System.currentTimeMillis()
            val messageWithSafeTimestamp = message.copy(timestamp = safeTimestamp)

            val decryptedMessage = messageWithSafeTimestamp.copy(messageText = decryptedMessageText, quotedMessageText = decryptedQuotedText)
            
            // FIX CRÍTICO: .copy() não copia propriedades fora do construtor. ID estava a perder-se aqui.
            decryptedMessage.id = message.id 
            
            val isSentByCurrentUser = decryptedMessage.senderId == currentUserId
            
            DisplayMessage(
                chatMessage = decryptedMessage, 
                isSentByCurrentUser = isSentByCurrentUser, 
                senderNickname = if (isSentByCurrentUser) currentUserNickname else recipientNickname, 
                senderAvatarSeed = if (isSentByCurrentUser) currentUserAvatarSeed else recipientAvatarSeed, 
                recipientAvatarSeed = if (isSentByCurrentUser) recipientAvatarSeed else currentUserAvatarSeed, 
                isEdited = decryptedMessage.isEdited
            )
        }
    }
    
    private fun navigateToUserProfile() { recipientId?.let { val userProfileDialog = UserProfileDialogFragment.newInstance(it); userProfileDialog.show(parentFragmentManager, "UserProfileDialogFragment") } }
    private fun hideKeyboard() { val imm = context?.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager; imm?.hideSoftInputFromWindow(view?.windowToken, 0) }
    private fun markConversationAsRead() { conversationId?.let { convId -> database.reference.child("users").child(currentUserId).child("conversations").child(convId).child("lastSeenTimestamp").setValue(System.currentTimeMillis()) } }
    private fun getConversationId(userId1: String, userId2: String): String { return if (userId1 < userId2) "${userId1}_${userId2}" else "${userId2}_${userId1}" }
    
    private fun setupRecyclerView() { 
        conversationAdapter = ConversationAdapter(lifecycleScope, this::onPlayVoiceMessage, this::onSeek, this::hideKeyboard)
        val layoutManager = LinearLayoutManager(context).apply { stackFromEnd = true }
        messagesRecyclerView.layoutManager = layoutManager
        messagesRecyclerView.adapter = conversationAdapter
        
        adapterDataObserver = object : RecyclerView.AdapterDataObserver() { 
            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) { 
                val lastVisibleItem = layoutManager.findLastCompletelyVisibleItemPosition()
                // Se a inserção for no final E (estávamos no fundo OU é a primeira carga), faz scroll
                if (positionStart + itemCount >= conversationAdapter.itemCount) {
                     messagesRecyclerView.scrollToPosition(conversationAdapter.itemCount - 1)
                }
            } 
        }
        conversationAdapter.registerAdapterDataObserver(adapterDataObserver!!)
        
        messagesRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() { 
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) { 
                super.onScrolled(recyclerView, dx, dy)
                val firstVisibleItemPosition = layoutManager.findFirstCompletelyVisibleItemPosition()
                val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                val itemCount = conversationAdapter.itemCount
                
                if (!isLoadingMore && firstVisibleItemPosition == 0 && dy < 0) { 
                    loadMoreMessages() 
                }
                scrollToBottomButton.isVisible = itemCount > 0 && lastVisibleItemPosition < itemCount - 1 
            }
            
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) { 
                super.onScrollStateChanged(recyclerView, newState)
                if (newState == RecyclerView.SCROLL_STATE_IDLE) { 
                    val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    val itemCount = conversationAdapter.itemCount
                    if (itemCount > 0 && lastVisibleItemPosition == itemCount - 1) { 
                        markMessagesAsSeenUpTo(lastVisibleItemPosition) 
                    } 
                } 
            } 
        })
        
        conversationAdapter.setOnSelectionListener { selectedCount -> 
            if (selectedCount > 0) { 
                if (actionMode == null) { 
                    actionMode = (activity as? AppCompatActivity)?.startSupportActionMode(actionModeCallback) 
                }
                updateActionModeTitle(selectedCount) 
            } else { 
                actionMode?.finish() 
            } 
        } 
    }
    
    private fun loadMoreMessages() { if (isLoadingMore || lastLoadedMessageKey == null) { loadingProgressBar.visibility = View.GONE; return }; isLoadingMore = true; loadingProgressBar.visibility = View.VISIBLE; val query = messagesRef?.orderByKey()?.endBefore(lastLoadedMessageKey)?.limitToLast(MESSAGES_PER_PAGE); query?.addListenerForSingleValueEvent(object : ValueEventListener { override fun onDataChange(snapshot: DataSnapshot) { if (!snapshot.hasChildren()) { lastLoadedMessageKey = null; loadingProgressBar.visibility = View.GONE; isLoadingMore = false; return }; lifecycleScope.launch { val olderMessages = snapshot.children.mapNotNull { data -> mapToDisplayMessage(data, true) }; val currentLayoutManager = messagesRecyclerView.layoutManager as LinearLayoutManager; val firstVisibleItemPosition = currentLayoutManager.findFirstVisibleItemPosition(); val firstView = currentLayoutManager.findViewByPosition(firstVisibleItemPosition); val topOffset = firstView?.top ?: 0; chatMessages.addAll(0, olderMessages); val newLastKey = snapshot.children.first().key; if (newLastKey == lastLoadedMessageKey) { lastLoadedMessageKey = null } else { lastLoadedMessageKey = newLastKey }; conversationAdapter.submitList(chatMessages.toList()) { val newItemsCount = olderMessages.size; currentLayoutManager.scrollToPositionWithOffset(firstVisibleItemPosition + newItemsCount, topOffset) }; loadingProgressBar.visibility = View.GONE; isLoadingMore = false } } override fun onCancelled(error: DatabaseError) { loadingProgressBar.visibility = View.GONE; isLoadingMore = false; Toast.makeText(context, "Failed to load older messages.", Toast.LENGTH_SHORT).show() } }) }
    private fun updateActionModeTitle(selectedCount: Int) { actionMode?.title = "$selectedCount selected"; actionMode?.invalidate() }
    private fun startTitleLoadingAnimation(title: String) { titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }; titleLoadingRunnable = object : Runnable { private var dotCount = 0; override fun run() { dotCount = (dotCount + 1) % 4; val dots = ".".repeat(dotCount); usernameTextView.text = "$title$dots"; titleLoadingHandler.postDelayed(this, 500) } }; titleLoadingHandler.post(titleLoadingRunnable!!) }
    private fun stopTitleLoadingAnimation(title: String) { titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }; titleLoadingRunnable = null; usernameTextView.text = title }
    
    // --- UPDATED METHOD: Granular Fetching for Privacy Compatibility ---
    private fun loadNicknamesAndListen() {
        val usersRef = database.reference.child("users")
        val recId = recipientId ?: return

        // Fetch Current User Data (Can read own full node)
        usersRef.child(currentUserId).get().addOnSuccessListener { myDataSnapshot ->
            currentUserNickname = myDataSnapshot.child("nickname").getValue(String::class.java) ?: getString(R.string.me)
            currentUserAvatarSeed = myDataSnapshot.child("avatarSeed").getValue(String::class.java) ?: currentUserId

            // Fetch Recipient Data - Granularly (Can only read public nodes)
            val recipientRef = usersRef.child(recId)
            
            // Parallel fetches or sequential? Sequential is safer for UI logic flow here.
            recipientRef.child("nickname").get().addOnSuccessListener { nickSnapshot ->
                recipientNickname = nickSnapshot.getValue(String::class.java) ?: getString(R.string.other)
                usernameTextView.text = recipientNickname
                
                recipientRef.child("avatarSeed").get().addOnSuccessListener { seedSnapshot ->
                    recipientAvatarSeed = seedSnapshot.getValue(String::class.java) ?: recId
                    
                    if (isAdded) {
                        lifecycleScope.launch(Dispatchers.Default) {
                            val avatar = AlienAvatarGenerator.generate(recipientAvatarSeed, 256, 256)
                            withContext(Dispatchers.Main) {
                                avatarImageView.setImageBitmap(avatar)
                            }
                        }
                    }
                    initKeyExchange()
                }.addOnFailureListener { 
                    // Fallback if blocked or failed
                    recipientAvatarSeed = recId
                    initKeyExchange()
                }
            }.addOnFailureListener {
                // Fallback if blocked or failed
                recipientNickname = getString(R.string.other)
                usernameTextView.text = recipientNickname
                initKeyExchange()
            }
            
        }.addOnFailureListener {
            Toast.makeText(context, "Failed to load user data.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun listenForMessages() {
        loadingProgressBar.visibility = View.VISIBLE

        val convId = conversationId ?: return
        messagesRef = database.reference.child("conversations").child(convId).child("messages")

        val query = messagesRef!!.orderByKey().limitToLast(MESSAGES_PER_PAGE)
        
        // Check once if we have messages to hide loader if empty
        query.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                loadingProgressBar.visibility = View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                 if (!isAdded) return
                 loadingProgressBar.visibility = View.GONE
            }
        })
        
        messageListener = query.addChildEventListener(object : ChildEventListener {

            private fun updateAndSortList(operation: () -> Unit) {
                operation()
                chatMessages.sortBy { (it.chatMessage.timestamp as? Long) ?: 0L }
                conversationAdapter.submitList(chatMessages.toList()) {
                    val layoutManager = messagesRecyclerView.layoutManager as LinearLayoutManager
                    val lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition()
                    // Force scroll if we are at the bottom or very close to it
                    if (lastVisibleItemPosition == -1 || lastVisibleItemPosition >= chatMessages.size - 2) {
                        messagesRecyclerView.post {
                            messagesRecyclerView.scrollToPosition(chatMessages.size - 1)
                        }
                    }
                }
            }

            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isListenerAttached) return
                // Update the lastLoadedMessageKey if it's the first item
                if (lastLoadedMessageKey == null) {
                    lastLoadedMessageKey = snapshot.key
                }
                
                loadingProgressBar.visibility = View.GONE // Hide loading once data starts arriving

                lifecycleScope.launch {
                    val newDisplayMessage = mapToDisplayMessage(snapshot, true) ?: return@launch
                    updateAndSortList {
                        val existingIndex = chatMessages.indexOfFirst { it.chatMessage.id == newDisplayMessage.chatMessage.id }
                        if (existingIndex == -1) {
                             chatMessages.add(newDisplayMessage)
                        } else {
                            chatMessages[existingIndex] = newDisplayMessage
                        }
                    }
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                if (!isListenerAttached) return
                lifecycleScope.launch {
                    val updatedDisplayMessage = mapToDisplayMessage(snapshot, false) ?: return@launch
                    updateAndSortList {
                        val index = chatMessages.indexOfFirst { it.chatMessage.id == updatedDisplayMessage.chatMessage.id }
                        if (index != -1) {
                            chatMessages[index] = updatedDisplayMessage
                        }
                    }
                }
            }

            override fun onChildRemoved(snapshot: DataSnapshot) {
                if (!isListenerAttached) return
                val removedMessageId = snapshot.key ?: return
                updateAndSortList {
                    chatMessages.removeAll { it.chatMessage.id == removedMessageId }
                }
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {
                Log.e("ConversationFragment", "Error listening for new messages: ${error.message}")
            }
        })
    }

    private fun setupTypingIndicator() {
        replyEditText.addTextChangedListener(object : TextWatcher {
            private val typingTimeout = Runnable { 
                typingStatusRef?.child(currentUserId)?.setValue(false)
                isTypingStateSent = false
            }
            
            override fun afterTextChanged(s: Editable?) {
                typingHandler.removeCallbacks(typingTimeout)
                val hasText = s.toString().isNotEmpty()
                
                // Optimized: Only send to DB if state changed
                if (hasText != isTypingStateSent) {
                    typingStatusRef?.child(currentUserId)?.setValue(hasText)
                    isTypingStateSent = hasText
                }
                
                if (hasText) {
                    typingHandler.postDelayed(typingTimeout, 2000)
                } else if (messageToEdit != null) {
                    messageToEdit = null
                }
                
                sendReplyButton.visibility = if (hasText) View.VISIBLE else View.GONE
                voiceMessageButton.visibility = if (hasText) View.GONE else View.VISIBLE
                attachImageButton.visibility = if (hasText) View.GONE else View.VISIBLE
                emojiButton.visibility = if (hasText) View.VISIBLE else View.GONE
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }
    
    private fun listenForTypingStatus() {
        val convId = conversationId ?: return
        val recId = recipientId ?: return
        typingStatusRef = database.reference.child("conversations").child(convId).child("typing_status")
        typingListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isListenerAttached) return
                val isTyping = snapshot.getValue(Boolean::class.java) ?: false
                typingIndicator.visibility = if (isTyping) View.VISIBLE else View.GONE
                if (isTyping) typingIndicator.text = getString(R.string.typing_indicator, recipientNickname)
            }
            override fun onCancelled(error: DatabaseError) {
                 if (!isListenerAttached) return
                typingIndicator.visibility = View.GONE
            }
        }
        typingStatusRef?.child(recId)?.addValueEventListener(typingListener!!)
    }

    private fun setupRichContentReceiver() { val acceptedMimeTypes = arrayOf("image/gif"); ViewCompat.setOnReceiveContentListener(replyEditText, acceptedMimeTypes) { _, payload -> val partition = payload.partition { item -> item.uri != null }; val uriContent = partition.first; val remaining = partition.second; if (uriContent != null) { val clip = uriContent.clip; for (i in 0 until clip.itemCount) { handleGifSelection(clip.getItemAt(i).uri) } }; remaining } }
    
    private fun handleImageSelection(localImageUri: Uri) {
        val fiveMB = 5 * 1024 * 1024
        if (context?.contentResolver?.openInputStream(localImageUri)?.available() ?: 0 > fiveMB) {
            Toast.makeText(context, "Image size cannot exceed 5MB", Toast.LENGTH_SHORT).show()
            return
        }
        val messageId = messagesRef?.push()?.key ?: return
        val messageText = replyEditText.text.toString().trim()
        replyEditText.text.clear()
        uploadImageAndUpdateMessage(localImageUri, messageId, messageText)
    }

    private fun uploadImageAndUpdateMessage(localImageUri: Uri, messageId: String, messageText: String) {
        val storageRef = storage.reference.child("images/${UUID.randomUUID()}")
        Toast.makeText(context, "Uploading image...", Toast.LENGTH_SHORT).show()
        storageRef.putFile(localImageUri).continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (!isAdded || context == null) return@addOnCompleteListener // Safety check

            if (task.isSuccessful) {
                val downloadUri = task.result
                val finalMessage = ChatMessage(
                    senderId = currentUserId,
                    messageText = messageText,
                    imageUrl = downloadUri.toString(),
                    timestamp = ServerValue.TIMESTAMP
                )
                messagesRef?.child(messageId)?.setValue(finalMessage)
            } else {
                Toast.makeText(context, "Failed to upload image.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleGifSelection(localGifUri: Uri) {
        val messageId = messagesRef?.push()?.key ?: return
        val messageText = replyEditText.text.toString().trim()
        replyEditText.text.clear()
        uploadGifAndUpdateMessage(localGifUri, messageId, messageText)
    }

    private fun uploadGifAndUpdateMessage(localGifUri: Uri, messageId: String, messageText: String) {
        val storageRef = storage.reference.child("gifs/${UUID.randomUUID()}.gif")
        Toast.makeText(context, "Uploading GIF...", Toast.LENGTH_SHORT).show()
        storageRef.putFile(localGifUri).continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (!isAdded || context == null) return@addOnCompleteListener // Safety check

            if (task.isSuccessful) {
                val downloadUri = task.result
                val finalMessage = ChatMessage(
                    senderId = currentUserId,
                    messageText = messageText,
                    gifUrl = downloadUri.toString(),
                    timestamp = ServerValue.TIMESTAMP
                )
                messagesRef?.child(messageId)?.setValue(finalMessage)
            } else {
                Toast.makeText(context, "Failed to upload GIF.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initiateConversationAndSendInvitation(convId: String, recipientId: String) { val convRef = database.reference.child("conversations").child(convId); convRef.child("isPrivate").setValue(isPrivate); val userConvData = mapOf("lastSeenTimestamp" to System.currentTimeMillis()); database.reference.child("users").child(currentUserId).child("conversations").child(convId).setValue(userConvData); database.reference.child("invitations").child(recipientId).child(convId).setValue(true); hidePublicMessage() }
    private fun hidePublicMessage() { args.originalMessageId?.let { database.reference.child("users").child(currentUserId).child("hiddenPublicMessages").child(it).setValue(true) } }
    private val actionModeCallback = object : ActionMode.Callback { override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean { mode.menuInflater.inflate(R.menu.conversation_selection_menu, menu); return true } override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean { val selectedCount = conversationAdapter.getSelectedItemsCount(); val selectedMessages = conversationAdapter.getSelectedMessages(); menu.findItem(R.id.action_copy).isVisible = selectedMessages.any { it.messageText.isNotEmpty() }; if (selectedCount == 1) { val message = selectedMessages.first(); val isMyMessage = message.senderId == currentUserId; menu.findItem(R.id.action_reply).isVisible = true; menu.findItem(R.id.action_edit).isVisible = isMyMessage && message.messageText != getString(R.string.message_deleted); menu.findItem(R.id.action_delete).isVisible = isMyMessage } else { menu.findItem(R.id.action_reply).isVisible = false; menu.findItem(R.id.action_edit).isVisible = false; menu.findItem(R.id.action_delete).isVisible = selectedMessages.all { it.senderId == currentUserId } }; return true } override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean { return when (item.itemId) { R.id.action_reply -> { startReplyingToMessage(); mode.finish(); true } R.id.action_edit -> { startEditingMessage(); mode.finish(); true } R.id.action_delete -> { showDeleteConfirmationDialog(); true } R.id.action_copy -> { copySelectedMessagesToClipboard(); mode.finish(); true } else -> false } } override fun onDestroyActionMode(mode: ActionMode) { actionMode = null; conversationAdapter.clearSelection() } }
    private fun showDeleteConfirmationDialog() { val selectedMessages = conversationAdapter.getSelectedMessages(); if (selectedMessages.isEmpty()) { return };
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(requireContext()).setView(dialogView).create()

        val titleTextView = dialogView.findViewById<TextView>(R.id.dialog_title)
        val subtitleTextView = dialogView.findViewById<TextView>(R.id.dialog_subtitle)
        val messageTextView = dialogView.findViewById<TextView>(R.id.dialog_message)
        val positiveButton = dialogView.findViewById<Button>(R.id.dialog_positive_button)
        val negativeButton = dialogView.findViewById<Button>(R.id.dialog_negative_button)
        val galaxyStatsLayout = dialogView.findViewById<LinearLayout>(R.id.galaxy_stats_layout)

        galaxyStatsLayout.visibility = View.GONE
        subtitleTextView.visibility = View.GONE

        titleTextView.text = "Delete Messages"
        messageTextView.text = "Are you sure you want to delete ${selectedMessages.size} message(s)? This action cannot be undone."
        positiveButton.text = "Delete"
        negativeButton.text = "Cancel"

        positiveButton.setOnClickListener {
            softDeleteSelectedMessages()
            actionMode?.finish()
            dialog.dismiss()
        }

        negativeButton.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }
    private fun copySelectedMessagesToClipboard() { val textToCopy = conversationAdapter.getSelectedMessages().sortedBy { it.timestamp as? Long ?: 0 }.mapNotNull { it.messageText }.joinToString(separator = "\n"); if (textToCopy.isNotEmpty()) { val clipboard = context?.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager; val clip = ClipData.newPlainText("Copied Messages", textToCopy); clipboard?.setPrimaryClip(clip); Toast.makeText(context, "Messages copied", Toast.LENGTH_SHORT).show() } }
    private fun startReplyingToMessage() { val selected = conversationAdapter.getSelectedMessages().firstOrNull() ?: return; messageToReply = selected; quotedMessageContainer.visibility = View.VISIBLE; quotedAuthorTextView.text = if (selected.senderId == currentUserId) currentUserNickname else recipientNickname; when { selected.voiceMessageUrl != null -> { quotedMessageTextView.visibility = View.GONE; quotedGifImageView.visibility = View.GONE; quotedVoiceMessageContainer.visibility = View.VISIBLE; quotedVoiceDurationTextView.text = TimeUtils.formatDuration(selected.voiceMessageDuration ?: 0) } selected.gifUrl != null -> { quotedMessageTextView.text = "GIF"; quotedMessageTextView.visibility = View.VISIBLE; quotedVoiceMessageContainer.visibility = View.GONE; quotedGifImageView.visibility = View.VISIBLE; Glide.with(this).load(selected.gifUrl).into(quotedGifImageView) } else -> { val decryptedQuotedText = if (isPrivate && selected.quotedMessageText != null) { sharedSecretKey?.let { CryptoManager.decrypt(selected.quotedMessageText!!, it)}} else { selected.quotedMessageText }; quotedMessageTextView.text = decryptedQuotedText ?: selected.messageText; quotedMessageTextView.visibility = View.VISIBLE; quotedVoiceMessageContainer.visibility = View.GONE; quotedGifImageView.visibility = View.GONE } }; replyEditText.requestFocus() }
    private fun cancelReply() { messageToReply = null; quotedMessageContainer.visibility = View.GONE }
    private fun startEditingMessage() { val selected = conversationAdapter.getSelectedMessages().firstOrNull() ?: return; messageToEdit = selected; val decryptedText = if (isPrivate && selected.messageText.isNotEmpty()) { sharedSecretKey?.let { CryptoManager.decrypt(selected.messageText, it) } ?: selected.messageText } else { selected.messageText }; replyEditText.setText(decryptedText); replyEditText.requestFocus(); replyEditText.setSelection(replyEditText.text.length) }
    private fun softDeleteSelectedMessages() {
        val deletedText = getString(R.string.message_deleted)
        val messagesToDelete = conversationAdapter.getSelectedMessages()
        
        // Finish action mode immediately to clear selection
        actionMode?.finish()

        messagesToDelete.forEach { message ->
            // Delete media from storage if applicable
            message.imageUrl?.takeIf { it.startsWith("https") }?.let { storage.getReferenceFromUrl(it).delete() }
            message.gifUrl?.takeIf { it.startsWith("https") }?.let { storage.getReferenceFromUrl(it).delete() }
            message.voiceMessageUrl?.takeIf { it.startsWith("https") }?.let { storage.getReferenceFromUrl(it).delete() }

            message.id?.let { messageId ->
                // Encrypt "Message deleted" text if conversation is private
                val textToUpdate = if (isPrivate && sharedSecretKey != null) {
                    CryptoManager.encrypt(deletedText, sharedSecretKey!!) ?: deletedText
                } else {
                    deletedText
                }

                val updates = mapOf<String, Any?>(
                    "messageText" to textToUpdate,
                    "quotedMessageText" to null,
                    "quotedMessageAuthor" to null,
                    "gifUrl" to null,
                    "imageUrl" to null,
                    "quotedMessageGifUrl" to null,
                    "voiceMessageUrl" to null,
                    "voiceMessageDuration" to null,
                    "isEdited" to false,
                    "isSeen" to false
                )
                
                // Update Firebase
                messagesRef?.child(messageId)?.updateChildren(updates)
                
                // Optimistically update local list for instant feedback
                val index = chatMessages.indexOfFirst { it.chatMessage.id == messageId }
                if (index != -1) {
                    val oldDisplayMessage = chatMessages[index]
                    val updatedChatMessage = oldDisplayMessage.chatMessage.copy(
                        messageText = deletedText, // Locally we show decrypted text immediately
                        quotedMessageText = null,
                        quotedMessageAuthor = null,
                        gifUrl = null,
                        imageUrl = null,
                        quotedMessageGifUrl = null,
                        voiceMessageUrl = null,
                        voiceMessageDuration = null,
                        isEdited = false,
                        isSeen = false
                    )
                    // FIX: Ensure ID is preserved after copy
                    updatedChatMessage.id = messageId
                    
                    val updatedDisplayMessage = oldDisplayMessage.copy(chatMessage = updatedChatMessage)
                    chatMessages[index] = updatedDisplayMessage
                }
            }
        }
        // Force adapter refresh
        conversationAdapter.submitList(chatMessages.toList())
    }
    private fun markMessagesAsSeenUpTo(position: Int) { if (position < 0) return; var listModified = false; for (i in 0..position) { chatMessages.getOrNull(i)?.let { displayMessage -> val message = displayMessage.chatMessage; if (message.id != null && message.senderId == recipientId && !message.isSeen) { messagesRef?.child(message.id!!)?.child("isSeen")?.setValue(true); val updatedMessage = message.copy(isSeen = true); chatMessages[i] = displayMessage.copy(chatMessage = updatedMessage); listModified = true } } }; if (listModified) { conversationAdapter.submitList(chatMessages.toList()) } }
    private fun maybeScrollToBottom() { val layoutManager = messagesRecyclerView.layoutManager as? LinearLayoutManager ?: return; val lastVisible = layoutManager.findLastVisibleItemPosition(); val itemCount = conversationAdapter.itemCount; if (itemCount > 0 && (lastVisible == -1 || lastVisible >= itemCount - 2)) { messagesRecyclerView.post { messagesRecyclerView.scrollToPosition(itemCount - 1) } } }
    private fun startRecording() { 
        try { 
            hideKeyboard(); 
            audioFile = File(requireContext().cacheDir, "${UUID.randomUUID()}.3gp"); 
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { MediaRecorder(requireContext()) } else { @Suppress("DEPRECATION") MediaRecorder() }.apply { 
                setAudioSource(MediaRecorder.AudioSource.MIC); 
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP); 
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB); 
                setOutputFile(audioFile?.absolutePath); 
                prepare(); 
                start() 
            }; 
            isRecording = true; 
            recordingStartTime = System.currentTimeMillis(); 
            replyEditText.visibility = View.INVISIBLE; 
            voiceMessageChronometer.visibility = View.VISIBLE; 
            voiceMessageChronometer.base = android.os.SystemClock.elapsedRealtime(); 
            voiceMessageChronometer.start() 
            
            // Start 60s limit timer
            recordingLimitHandler.postDelayed(recordingLimitRunnable, 60000)
            
        } catch (e: IOException) { 
            Toast.makeText(context, "Recording failed to start", Toast.LENGTH_SHORT).show(); 
            isRecording = false 
        } catch (e: IllegalStateException) { 
            Toast.makeText(context, "MediaRecorder is not properly configured.", Toast.LENGTH_SHORT).show(); 
            isRecording = false 
        } 
    }
    private fun stopRecordingAndSend() { 
        // Remove limit timer
        recordingLimitHandler.removeCallbacks(recordingLimitRunnable)
        
        if (!isRecording) return; 
        try { mediaRecorder?.apply { stop(); release() } } catch (e: Exception) { } finally { mediaRecorder = null; isRecording = false }; 
        val duration = System.currentTimeMillis() - recordingStartTime; 
        voiceMessageChronometer.stop(); 
        voiceMessageChronometer.visibility = View.GONE; 
        replyEditText.visibility = View.VISIBLE; 
        if (audioFile != null && duration > 1000) { 
            sendVoiceMessage(Uri.fromFile(audioFile), duration) 
        } else { 
            Toast.makeText(context, "Recording too short", Toast.LENGTH_SHORT).show() 
        } 
    }
    
    private fun sendVoiceMessage(audioUri: Uri, duration: Long) {
        val messageId = messagesRef?.push()?.key ?: return
        uploadVoiceMessageAndUpdateMessage(audioUri, messageId, duration)
    }

    private fun uploadVoiceMessageAndUpdateMessage(localAudioUri: Uri, messageId: String, duration: Long) {
        val storageRef = storage.reference.child("voice_messages/${UUID.randomUUID()}.3gp")
        Toast.makeText(context, "Uploading voice message...", Toast.LENGTH_SHORT).show()
        storageRef.putFile(localAudioUri).continueWithTask { task ->
            if (!task.isSuccessful) {
                task.exception?.let { throw it }
            }
            storageRef.downloadUrl
        }.addOnCompleteListener { task ->
            if (!isAdded || context == null) return@addOnCompleteListener // Safety check

            if (task.isSuccessful) {
                val downloadUri = task.result
                val finalMessage = ChatMessage(
                    senderId = currentUserId,
                    timestamp = ServerValue.TIMESTAMP,
                    voiceMessageUrl = downloadUri.toString(),
                    voiceMessageDuration = duration
                )
                messagesRef?.child(messageId)?.setValue(finalMessage)
                
                // Badge Logic: Add Voice Seconds
                BadgeProgressManager.addVoiceSeconds(duration / 1000)
            } else {
                Toast.makeText(context, "Failed to upload voice message.", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun onSeek(message: ChatMessage, progress: Int) { if (message.id == currentlyPlayingMessageId && mediaPlayer != null) { mediaPlayer?.seekTo(progress) } }
    fun onPlayVoiceMessage(message: ChatMessage) { val messageId = message.id ?: return; if (mediaPlayer?.isPlaying == true && currentlyPlayingMessageId == messageId) { mediaPlayer?.pause(); conversationAdapter.setPlaybackState(messageId, false, mediaPlayer?.currentPosition ?: 0); seekBarUpdateRunnable?.let { seekBarUpdateHandler.removeCallbacks(it) } } else if (mediaPlayer != null && !mediaPlayer!!.isPlaying && currentlyPlayingMessageId == messageId) { mediaPlayer?.start(); conversationAdapter.setPlaybackState(messageId, true, mediaPlayer?.currentPosition ?: 0); seekBarUpdateRunnable?.let { seekBarUpdateHandler.post(it) } } else { stopAudio(); playAudio(message) } }
    private fun playAudio(message: ChatMessage) { val url = message.voiceMessageUrl ?: return; val messageId = message.id ?: return; mediaPlayer = MediaPlayer().apply { try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).setUsage(AudioAttributes.USAGE_MEDIA).build()) }; setDataSource(url); prepareAsync(); setOnPreparedListener { it.start(); currentlyPlayingMessageId = messageId; conversationAdapter.setPlaybackState(messageId, true, 0); seekBarUpdateRunnable = object : Runnable { override fun run() { if (mediaPlayer != null && mediaPlayer!!.isPlaying) { val currentPosition = mediaPlayer!!.currentPosition; updateSeekBarProgress(messageId, currentPosition); seekBarUpdateHandler.postDelayed(this, 100) } } }; seekBarUpdateHandler.post(seekBarUpdateRunnable!!) }; setOnCompletionListener { stopAudio() }; setOnErrorListener { _, _, _ -> stopAudio(); Toast.makeText(context, "Could not play audio", Toast.LENGTH_SHORT).show(); true } } catch (e: IOException) { stopAudio(); Toast.makeText(context, "Could not play audio", Toast.LENGTH_SHORT).show() } } }
    private fun updateSeekBarProgress(messageId: String, progress: Int) { val index = chatMessages.indexOfFirst { it.chatMessage.id == messageId }; if (index != -1) { val layoutManager = messagesRecyclerView.layoutManager as? LinearLayoutManager; if (layoutManager != null && index >= layoutManager.findFirstVisibleItemPosition() && index <= layoutManager.findLastVisibleItemPosition()) { val viewHolder = messagesRecyclerView.findViewHolderForAdapterPosition(index) as? ConversationAdapter.MessageViewHolder; viewHolder?.updateSeekBar(progress) } } }
    private fun stopAudio() { if (mediaPlayer != null) { seekBarUpdateRunnable?.let { seekBarUpdateHandler.removeCallbacks(it) }; mediaPlayer?.release(); mediaPlayer = null; conversationAdapter.stopPlayback(); currentlyPlayingMessageId = null } }
    
    override fun onDestroyView() {
        super.onDestroyView()
        // restoreMainToolbar() removed
        actionMode?.finish()
        detachListeners() // Ensure listeners are detached
        
        if (keyExchangeListener != null && publicKeysRef != null && recipientId != null) {
            publicKeysRef!!.child(recipientId!!).removeEventListener(keyExchangeListener!!)
        }

        titleLoadingHandler.removeCallbacksAndMessages(null)
        recordingLimitHandler.removeCallbacksAndMessages(null) // Clean up handler
        mediaRecorder?.release()
        mediaRecorder = null
        mediaPlayer?.release()
        mediaPlayer = null
        adapterDataObserver?.let { conversationAdapter.unregisterAdapterDataObserver(it) }
    }
}