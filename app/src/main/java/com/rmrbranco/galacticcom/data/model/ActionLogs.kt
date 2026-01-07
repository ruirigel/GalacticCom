package com.rmrbranco.galacticcom.data.model

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class ActionLogs(
    val accountCreationTimestamp: Long = 0,
    val totalYearsActive: Double = 0.0,
    val consecutiveMonthsActive: Int = 0,
    
    // Badge 1: O Observador de Eras
    val monthsWithBroadcast: Int = 0,
    
    // Badge 2: Cometa Halley
    val specialEventsParticipated: MutableMap<String, Boolean> = mutableMapOf(),
    
    // Badge 3: A Garrafa de Vidro de Murano (Updated Logic: Target Unique Galaxies)
    val messagedGalaxies: MutableMap<String, Boolean> = mutableMapOf(),
    
    // Badge 4: Almirante da Frota Fantasma
    val severedConversationsCount: Int = 0,
    
    // Badge 5: Sócio Vitalício do Star Nomad
    val harlockItemsPurchased: MutableMap<String, Int> = mutableMapOf(),
    
    // Badge 6: O Erudito de Mil Mundos
    val visitedGalaxies: MutableMap<String, Boolean> = mutableMapOf(),
    
    // Badge 7: Mineiro de Matéria Escura
    val miningTotalAccumulated: Long = 0,
    
    // Badge 8: A Voz das Estrelas
    val totalVoiceMinutesSent: Double = 0.0,
    
    // Badge 9: O Guardião do Vácuo
    val deepSpaceMessagesIntercepted: Int = 0,
    
    // Badge 10: Supernova Survivor
    val supernovasSurvived: Int = 0,
    
    val totalMessagesIntercepted: Int = 0,
    
    // Track claimed rewards
    val claimedBadgeRewards: MutableMap<String, Boolean> = mutableMapOf()
) {
    // Deprecated but kept for compatibility if needed during migration
    val muranoMessagesSent: Int = 0 
}
