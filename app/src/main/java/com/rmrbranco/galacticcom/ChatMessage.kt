package com.rmrbranco.galacticcom

import com.google.firebase.database.Exclude
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

@IgnoreExtraProperties
data class ChatMessage(
    var senderId: String = "",
    var messageText: String = "",
    var timestamp: Any = 0L,
    var quotedMessageText: String? = null,
    var quotedMessageAuthor: String? = null,
    var gifUrl: String? = null,
    var quotedMessageGifUrl: String? = null,
    var voiceMessageUrl: String? = null,
    var voiceMessageDuration: Long? = null,
    var quotedMessageVoiceUrl: String? = null,
    var quotedMessageVoiceDuration: Long? = null,
    var imageUrl: String? = null,
    @get:PropertyName("isEdited")
    @set:PropertyName("isEdited")
    var isEdited: Boolean = false,
    @get:PropertyName("isSeen")
    @set:PropertyName("isSeen")
    var isSeen: Boolean = false
) {
    @get:Exclude
    var id: String? = null
}
