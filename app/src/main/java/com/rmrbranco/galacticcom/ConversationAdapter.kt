package com.rmrbranco.galacticcom

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ConversationAdapter(
    private val scope: CoroutineScope,
    private val onPlayVoiceMessage: (ChatMessage) -> Unit,
    private val onSeek: (ChatMessage, Int) -> Unit,
    private val onMessageClick: (DisplayMessage, View) -> Unit
) : ListAdapter<DisplayMessage, ConversationAdapter.MessageViewHolder>(MessageDiffCallback()) {

    val selectedItems = mutableSetOf<String>()
    private var selectionListener: ((Int) -> Unit)? = null

    private var playingMessageId: String? = null
    private var isAudioPlaying: Boolean = false
    private val voiceMessageProgress = mutableMapOf<String, Int>()

    init {
        setHasStableIds(true)
    }

    companion object {
        private const val VIEW_TYPE_SENT = 1
        private const val VIEW_TYPE_RECEIVED = 2
        private const val PAYLOAD_SELECTION = "PAYLOAD_SELECTION"
    }
    
    override fun getItemId(position: Int): Long {
        return getItem(position).uniqueId
    }

    fun setOnSelectionListener(listener: (Int) -> Unit) {
        this.selectionListener = listener
    }

    fun getSelectedItemsCount(): Int = selectedItems.size

    fun getSelectedMessages(): List<ChatMessage> {
        return currentList.filter { selectedItems.contains(it.chatMessage.id) }.map { it.chatMessage }
    }

    fun clearSelection() {
        val previouslySelected = selectedItems.toList()
        selectedItems.clear()
        previouslySelected.forEach { id ->
            val index = currentList.indexOfFirst { it.chatMessage.id == id }
            if (index != -1) notifyItemChanged(index, PAYLOAD_SELECTION)
        }
        selectionListener?.invoke(0)
    }

    private fun toggleSelection(position: Int) {
        if (position == RecyclerView.NO_POSITION) return
        val item = getItem(position)
        item.chatMessage.id?.let { messageId ->
            if (selectedItems.contains(messageId)) selectedItems.remove(messageId) else selectedItems.add(messageId)
            notifyItemChanged(position, PAYLOAD_SELECTION)
            selectionListener?.invoke(selectedItems.size)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (getItem(position).isSentByCurrentUser) VIEW_TYPE_SENT else VIEW_TYPE_RECEIVED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_SENT) R.layout.item_chat_message_sent else R.layout.item_chat_message_received
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return if (viewType == VIEW_TYPE_SENT) SentViewHolder(view) else ReceivedViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            holder.updateSelectionState(selectedItems.contains(getItem(position).chatMessage.id))
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onViewRecycled(holder: MessageViewHolder) {
        super.onViewRecycled(holder)
        if (holder is ReceivedViewHolder) {
            holder.avatarJob?.cancel()
        }
    }

    fun setPlaybackState(messageId: String, isPlaying: Boolean, progress: Int) {
        val previousPlayingId = playingMessageId
        playingMessageId = if (isPlaying) messageId else null
        isAudioPlaying = isPlaying
        voiceMessageProgress[messageId] = progress

        previousPlayingId?.let { id ->
            val index = currentList.indexOfFirst { it.chatMessage.id == id }
            if (index != -1) notifyItemChanged(index)
        }
        val index = currentList.indexOfFirst { it.chatMessage.id == messageId }
        if (index != -1) notifyItemChanged(index)
    }

    fun stopPlayback() {
        val previouslyPlayingId = playingMessageId
        playingMessageId = null
        isAudioPlaying = false

        if (previouslyPlayingId != null) {
            val index = currentList.indexOfFirst { it.chatMessage.id == previouslyPlayingId }
            if (index != -1) {
                voiceMessageProgress.remove(previouslyPlayingId)
                notifyItemChanged(index)
            }
        }
    }

    private var recyclerView: RecyclerView? = null
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    abstract class MessageViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        abstract fun bind(displayMessage: DisplayMessage)
        abstract fun updateSelectionState(isSelected: Boolean)
        open fun updateSeekBar(progress: Int) {}
    }

    inner class SentViewHolder(view: View) : MessageViewHolder(view) {
        private val messageBubble: LinearLayout = view.findViewById(R.id.ll_message_bubble)
        private val messageText: TextView = view.findViewById(R.id.tv_chat_message)
        private val timestampText: TextView = view.findViewById(R.id.tv_chat_timestamp)
        private val messageStatusText: TextView = view.findViewById(R.id.tv_message_status)
        private val quotedMessageView: View = view.findViewById(R.id.quoted_message_view)
        private val quotedAuthorText: TextView = view.findViewById(R.id.tv_quoted_author)
        private val quotedMessageText: TextView = view.findViewById(R.id.tv_quoted_text)
        private val quotedGifImageView: ImageView = view.findViewById(R.id.iv_quoted_gif_in_bubble)
        private val chatImageView: ImageView = view.findViewById(R.id.iv_chat_image)
        private val gifImageView: ImageView = view.findViewById(R.id.iv_chat_gif)
        private val mediaLoadingIndicator: ProgressBar = view.findViewById(R.id.pb_media_loading)
        private val voiceMessageContainer: LinearLayout = view.findViewById(R.id.voice_message_container)
        private val playVoiceMessageButton: ImageButton = view.findViewById(R.id.btn_play_voice_message)
        private val voiceMessageSeekBar: SeekBar = view.findViewById(R.id.seekbar_voice_message)
        private val voiceMessageDuration: TextView = view.findViewById(R.id.tv_voice_message_duration)
        private val quotedVoiceMessageContainer: LinearLayout? = view.findViewById(R.id.quoted_voice_message_container_bubble)
        private val quotedVoiceDuration: TextView? = view.findViewById(R.id.tv_quoted_voice_duration_bubble)

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    if (selectedItems.isNotEmpty()) {
                        toggleSelection(position)
                    } else {
                        onMessageClick(getItem(position), messageBubble)
                    }
                }
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleSelection(position)
                }
                true
            }
        }

        override fun bind(displayMessage: DisplayMessage) {
            val timestamp = displayMessage.chatMessage.timestamp as? Long ?: System.currentTimeMillis()
            timestampText.text = TimeUtils.getRelativeTimeSpanString(timestamp)

            val hasMedia = displayMessage.chatMessage.imageUrl != null || displayMessage.chatMessage.gifUrl != null || displayMessage.chatMessage.voiceMessageUrl != null

            if (displayMessage.isSending && hasMedia) {
                mediaLoadingIndicator.visibility = View.VISIBLE
                timestampText.visibility = View.GONE
                messageStatusText.visibility = View.GONE
            } else {
                mediaLoadingIndicator.visibility = View.GONE
                timestampText.visibility = View.VISIBLE
                when {
                    displayMessage.chatMessage.isEdited -> {
                        messageStatusText.text = "Edited"
                        messageStatusText.visibility = View.VISIBLE
                    }
                    displayMessage.chatMessage.isSeen -> {
                        messageStatusText.text = "Seen"
                        messageStatusText.visibility = View.VISIBLE
                    }
                    else -> {
                        messageStatusText.visibility = View.GONE
                    }
                }
            }

            messageText.visibility = View.GONE
            gifImageView.visibility = View.GONE
            chatImageView.visibility = View.GONE
            voiceMessageContainer.visibility = View.GONE

            val isPlaying = displayMessage.chatMessage.id == playingMessageId && isAudioPlaying

            if (displayMessage.chatMessage.voiceMessageUrl != null) {
                voiceMessageContainer.visibility = View.VISIBLE
                voiceMessageDuration.text = TimeUtils.formatDuration(displayMessage.chatMessage.voiceMessageDuration ?: 0)
                playVoiceMessageButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
                playVoiceMessageButton.setOnClickListener { onPlayVoiceMessage(displayMessage.chatMessage) }
                voiceMessageSeekBar.max = (displayMessage.chatMessage.voiceMessageDuration ?: 0).toInt()
                voiceMessageSeekBar.progress = voiceMessageProgress[displayMessage.chatMessage.id] ?: 0
                voiceMessageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) onSeek(displayMessage.chatMessage, progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            } else if (displayMessage.chatMessage.gifUrl != null) {
                gifImageView.visibility = View.VISIBLE
                Glide.with(itemView.context).asGif().load(displayMessage.chatMessage.gifUrl).into(gifImageView)
            } else if (displayMessage.chatMessage.imageUrl != null) {
                chatImageView.visibility = View.VISIBLE
                Glide.with(itemView.context).load(displayMessage.chatMessage.imageUrl).into(chatImageView)
            } else {
                messageText.visibility = View.VISIBLE
                messageText.text = displayMessage.chatMessage.messageText
            }

            if (displayMessage.chatMessage.quotedMessageAuthor != null) {
                quotedMessageView.visibility = View.VISIBLE
                quotedAuthorText.text = displayMessage.chatMessage.quotedMessageAuthor
                when {
                    displayMessage.chatMessage.quotedMessageVoiceUrl != null -> {
                        quotedMessageText.visibility = View.GONE
                        quotedGifImageView.visibility = View.GONE
                        quotedVoiceMessageContainer?.visibility = View.VISIBLE
                        quotedVoiceDuration?.text = TimeUtils.formatDuration(displayMessage.chatMessage.quotedMessageVoiceDuration ?: 0)
                    }
                    displayMessage.chatMessage.quotedMessageGifUrl != null -> {
                        quotedMessageText.text = "GIF"
                        quotedMessageText.visibility = View.VISIBLE
                        quotedVoiceMessageContainer?.visibility = View.GONE
                        quotedGifImageView.visibility = View.VISIBLE
                        Glide.with(itemView.context).asGif().load(displayMessage.chatMessage.quotedMessageGifUrl).into(quotedGifImageView)
                    }
                    else -> {
                        quotedMessageText.text = displayMessage.chatMessage.quotedMessageText
                        quotedMessageText.visibility = View.VISIBLE
                        quotedVoiceMessageContainer?.visibility = View.GONE
                        quotedGifImageView.visibility = View.GONE
                    }
                }
            } else {
                quotedMessageView.visibility = View.GONE
            }

            updateSelectionState(selectedItems.contains(displayMessage.chatMessage.id))
        }

        override fun updateSelectionState(isSelected: Boolean) {
            val backgroundRes = if (isSelected) R.drawable.item_neon_background_selected else R.drawable.item_neon_background
            messageBubble.setBackgroundResource(backgroundRes)
        }

        override fun updateSeekBar(progress: Int) {
            voiceMessageSeekBar.progress = progress
            voiceMessageDuration.text = TimeUtils.formatDuration(progress.toLong())
        }
    }

    inner class ReceivedViewHolder(view: View) : MessageViewHolder(view) {
        private val messageBubble: LinearLayout = view.findViewById(R.id.ll_message_bubble)
        private val messageText: TextView = view.findViewById(R.id.tv_chat_message)
        private val timestampText: TextView = view.findViewById(R.id.tv_chat_timestamp)
        private val messageStatusText: TextView = view.findViewById(R.id.tv_message_status)
        private val senderNicknameText: TextView = view.findViewById(R.id.tv_sender_nickname)
        private val quotedMessageView: View = view.findViewById(R.id.quoted_message_view)
        private val quotedAuthorText: TextView = view.findViewById(R.id.tv_quoted_author)
        private val quotedMessageText: TextView = view.findViewById(R.id.tv_quoted_text)
        private val quotedGifImageView: ImageView = view.findViewById(R.id.iv_quoted_gif_in_bubble)
        private val chatImageView: ImageView = view.findViewById(R.id.iv_chat_image)
        private val gifImageView: ImageView = view.findViewById(R.id.iv_chat_gif)
        private val mediaLoadingIndicator: ProgressBar = view.findViewById(R.id.pb_media_loading)
        private val voiceMessageContainer: LinearLayout = view.findViewById(R.id.voice_message_container)
        private val playVoiceMessageButton: ImageButton = view.findViewById(R.id.btn_play_voice_message)
        private val voiceMessageSeekBar: SeekBar = view.findViewById(R.id.seekbar_voice_message)
        private val voiceMessageDuration: TextView = view.findViewById(R.id.tv_voice_message_duration)
        private val quotedVoiceMessageContainer: LinearLayout? = view.findViewById(R.id.quoted_voice_message_container_bubble)
        private val quotedVoiceDuration: TextView? = view.findViewById(R.id.tv_quoted_voice_duration_bubble)
        private val avatarImageView: ImageView = view.findViewById(R.id.iv_avatar)
        internal var avatarJob: Job? = null

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    if (selectedItems.isNotEmpty()) {
                        toggleSelection(position)
                    } else {
                        onMessageClick(getItem(position), messageBubble)
                    }
                }
            }
            itemView.setOnLongClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleSelection(position)
                }
                true
            }
        }

        override fun bind(displayMessage: DisplayMessage) {
            senderNicknameText.text = displayMessage.senderNickname
            senderNicknameText.visibility = View.VISIBLE
            val timestamp = displayMessage.chatMessage.timestamp as? Long ?: System.currentTimeMillis()
            timestampText.text = TimeUtils.getRelativeTimeSpanString(timestamp)

            messageStatusText.visibility = if (displayMessage.chatMessage.isEdited) View.VISIBLE else View.GONE
            messageStatusText.text = if (displayMessage.chatMessage.isEdited) "Edited" else ""

            avatarJob?.cancel()
            avatarJob = scope.launch {
                val avatar = AlienAvatarGenerator.generate(displayMessage.senderAvatarSeed, 128, 128)
                avatarImageView.setImageBitmap(avatar)
            }

            val hasMedia = displayMessage.chatMessage.imageUrl != null || displayMessage.chatMessage.gifUrl != null || displayMessage.chatMessage.voiceMessageUrl != null
            mediaLoadingIndicator.visibility = if (displayMessage.isSending && hasMedia) View.VISIBLE else View.GONE


            messageText.visibility = View.GONE
            gifImageView.visibility = View.GONE
            chatImageView.visibility = View.GONE
            voiceMessageContainer.visibility = View.GONE

            val isPlaying = displayMessage.chatMessage.id == playingMessageId && isAudioPlaying

            if (displayMessage.chatMessage.voiceMessageUrl != null) {
                voiceMessageContainer.visibility = View.VISIBLE
                voiceMessageDuration.text = TimeUtils.formatDuration(displayMessage.chatMessage.voiceMessageDuration ?: 0)
                playVoiceMessageButton.setImageResource(if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play_arrow)
                playVoiceMessageButton.setOnClickListener { onPlayVoiceMessage(displayMessage.chatMessage) }
                voiceMessageSeekBar.max = (displayMessage.chatMessage.voiceMessageDuration ?: 0).toInt()
                voiceMessageSeekBar.progress = voiceMessageProgress[displayMessage.chatMessage.id] ?: 0
                voiceMessageSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) onSeek(displayMessage.chatMessage, progress)
                    }
                    override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                    override fun onStopTrackingTouch(seekBar: SeekBar?) {}
                })
            } else if (displayMessage.chatMessage.gifUrl != null) {
                gifImageView.visibility = View.VISIBLE
                Glide.with(itemView.context).asGif().load(displayMessage.chatMessage.gifUrl).into(gifImageView)
            } else if (displayMessage.chatMessage.imageUrl != null) {
                chatImageView.visibility = View.VISIBLE
                Glide.with(itemView.context).load(displayMessage.chatMessage.imageUrl).into(chatImageView)
            } else {
                messageText.visibility = View.VISIBLE
                messageText.text = displayMessage.chatMessage.messageText
            }

            if (displayMessage.chatMessage.quotedMessageAuthor != null) {
                quotedMessageView.visibility = View.VISIBLE
                quotedAuthorText.text = displayMessage.chatMessage.quotedMessageAuthor
                when {
                    displayMessage.chatMessage.quotedMessageVoiceUrl != null -> {
                        quotedMessageText.visibility = View.GONE
                        quotedGifImageView.visibility = View.GONE
                        quotedVoiceMessageContainer?.visibility = View.VISIBLE
                        quotedVoiceDuration?.text = TimeUtils.formatDuration(displayMessage.chatMessage.quotedMessageVoiceDuration ?: 0)
                    }
                    displayMessage.chatMessage.quotedMessageGifUrl != null -> {
                        quotedMessageText.text = "GIF"
                        quotedMessageText.visibility = View.VISIBLE
                        quotedVoiceMessageContainer?.visibility = View.GONE
                        quotedGifImageView.visibility = View.VISIBLE
                        Glide.with(itemView.context).asGif().load(displayMessage.chatMessage.quotedMessageGifUrl).into(quotedGifImageView)
                    }
                    else -> {
                        quotedMessageText.text = displayMessage.chatMessage.quotedMessageText
                        quotedMessageText.visibility = View.VISIBLE
                        quotedVoiceMessageContainer?.visibility = View.GONE
                        quotedGifImageView.visibility = View.GONE
                    }
                }
            } else {
                quotedMessageView.visibility = View.GONE
            }

            updateSelectionState(selectedItems.contains(displayMessage.chatMessage.id))
        }

        override fun updateSelectionState(isSelected: Boolean) {
            val backgroundRes = if (isSelected) R.drawable.item_neon_background_selected else R.drawable.item_neon_background
            messageBubble.setBackgroundResource(backgroundRes)
        }

        override fun updateSeekBar(progress: Int) {
            voiceMessageSeekBar.progress = progress
            voiceMessageDuration.text = TimeUtils.formatDuration(progress.toLong())
        }
    }
}

class MessageDiffCallback : DiffUtil.ItemCallback<DisplayMessage>() {
    override fun areItemsTheSame(oldItem: DisplayMessage, newItem: DisplayMessage): Boolean {
        return oldItem.uniqueId == newItem.uniqueId
    }

    override fun areContentsTheSame(oldItem: DisplayMessage, newItem: DisplayMessage): Boolean {
        return oldItem == newItem
    }
}