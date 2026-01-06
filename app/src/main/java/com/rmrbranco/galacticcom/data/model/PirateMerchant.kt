package com.rmrbranco.galacticcom.data.model

import com.google.firebase.database.PropertyName

/**
 * Represents a traveling merchant in the GalacticCom universe.
 * Maps to the structure under /merchants/{merchantId} in Firebase.
 */
data class PirateMerchant(
    @get:PropertyName("merchant_name") @set:PropertyName("merchant_name") var merchantName: String = "",
    @get:PropertyName("ship_name") @set:PropertyName("ship_name") var shipName: String = "",
    @get:PropertyName("avatar_seed") @set:PropertyName("avatar_seed") var avatarSeed: String = "",
    @get:PropertyName("current_galaxy") @set:PropertyName("current_galaxy") var currentGalaxy: String? = null,
    @get:PropertyName("visible_until") @set:PropertyName("visible_until") var visibleUntil: Long = 0,
    @get:PropertyName("inventory") @set:PropertyName("inventory") var inventory: Map<String, MerchantItem> = emptyMap()
) {
    // No-argument constructor required for Firebase deserialization
    constructor() : this("", "", "", null, 0, emptyMap())
}