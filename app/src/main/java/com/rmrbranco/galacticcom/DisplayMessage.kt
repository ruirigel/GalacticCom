package com.rmrbranco.galacticcom

data class DisplayMessage(
    val chatMessage: ChatMessage,
    val isSentByCurrentUser: Boolean,
    val senderNickname: String,
    val senderAvatarSeed: String,
    val recipientAvatarSeed: String,
    val isEdited: Boolean = false,
    val isSending: Boolean = false
) {
    val uniqueId: Long
        get() = chatMessage.id?.hashCode()?.toLong() ?: 0L
}
