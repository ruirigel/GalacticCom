package com.rmrbranco.galacticcom.data.model

import com.google.firebase.database.PropertyName

/**
 * Represents a single item sold by a merchant in the GalacticCom universe.
 * The structure of this class maps directly to the Firebase Realtime Database schema.
 */
data class MerchantItem(
    @get:PropertyName("item_id") @set:PropertyName("item_id") var itemId: String = "",
    @get:PropertyName("item_name") @set:PropertyName("item_name") var itemName: String = "",
    @get:PropertyName("description") @set:PropertyName("description") var description: String = "",
    @get:PropertyName("price") @set:PropertyName("price") var price: Int = 0,
    @get:PropertyName("currency_type") @set:PropertyName("currency_type") var currencyType: String = "XP",
    @get:PropertyName("total_sold") @set:PropertyName("total_sold") var totalSold: Int = 0
) {
    // No-argument constructor required for Firebase deserialization
    constructor() : this("", "", "", 0, "XP", 0)
}