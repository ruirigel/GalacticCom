package com.rmrbranco.galacticcom

data class InboxConversation(
    val conversationId: String,
    val otherUserId: String,
    val otherUserNickname: String,
    val otherUserAvatarSeed: String,
    val lastMessage: String,
    val lastMessageTimestamp: Long,
    val hasUnreadMessages: Boolean,
    val isPrivate: Boolean
)
