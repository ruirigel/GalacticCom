package com.rmrbranco.galacticcom

import com.google.firebase.database.ServerValue

data class PublicMessage(
    var messageId: String? = null,
    val senderId: String? = null,
    val message: String? = null,
    val senderNickname: String? = null,
    val senderGalaxy: String? = null,
    val timestamp: Any? = ServerValue.TIMESTAMP,
    val arrivalTime: Long = 0,
    val catastropheType: String? = null // Novo campo
)
