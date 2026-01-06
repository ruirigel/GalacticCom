package com.rmrbranco.galacticcom.data

import com.google.firebase.database.FirebaseDatabase
import com.rmrbranco.galacticcom.data.model.MerchantItem
import com.rmrbranco.galacticcom.data.model.PirateMerchant
import java.util.concurrent.TimeUnit

/**
 * A utility object to seed the Firebase Realtime Database with initial data.
 * This should be run once to set up the initial state for merchants and settings.
 */
object DatabaseSeeder {

    fun seedInitialData() {
        val database = FirebaseDatabase.getInstance()
        val merchantsRef = database.getReference("merchants")
        val settingsRef = database.getReference("settings")

        // 1. Create the inventory for Captain Silas
        val inventory = mapOf(
            "item_001" to MerchantItem("item_001", "Hyperwave Booster", "Sends your next broadcast instantly. A luxury for the impatient.", 500, "XP", 0),
            "item_002" to MerchantItem("item_002", "Void Cloak", "Your next broadcast is sent anonymously. No one will know who sent it.", 250, "XP", 0),
            "item_003" to MerchantItem("item_003", "Cosmic Noise Filter", "Reduces the chance of your next message being corrupted by a solar storm.", 150, "XP", 0),
            "item_004" to MerchantItem("item_004", "Alien Avatar Seed", "Allows you to generate a new procedural avatar without changing your pseudonym.", 300, "XP", 0),
            "item_005" to MerchantItem("item_005", "Lone Traveler's Emblem", "A cosmetic emblem to display on your profile. Shows you've seen a few stars.", 75, "XP", 0)
        )

        // 2. Create Captain Silas, the merchant
        val captainSilas = PirateMerchant(
            merchantName = "Captain Harlock",
            shipName = "The Star Nomad",
            avatarSeed = "captain-harlock-avatar-seed-pirate",
            currentGalaxy = null, // Initially not in any galaxy
            visibleUntil = 0,
            inventory = inventory
        )

        // 3. Push the data to Firebase under /merchants/captain_silas
        merchantsRef.child("captain_silas").setValue(captainSilas)

        // 4. Set all initial app settings
        val initialSettings = mapOf(
            // Merchant Settings
            "merchantAppearanceDurationHours" to 8,

            // Travel Parameters (in minutes)
            "planetaryTravelTimeMinutes" to 60, // 1 hour
            "intergalacticTravelTimeMinutes" to TimeUnit.HOURS.toMinutes(24), // 24 hours

            // Communication Parameters
            "publicMessageDailyLimit" to 1,
            "defaultPublicMessageDeliveryTimeMinutes" to TimeUnit.HOURS.toMinutes(2), // 2 hours
            "privateMessageDailyLimit" to 200,

            // User Action Limits
            "dailyPlanetaryTravelLimit" to 3,
            "weeklyIntergalacticTravelLimit" to 1,
            "monthlyAvatarChangeLimit" to 1
        )
        settingsRef.setValue(initialSettings)
    }
}