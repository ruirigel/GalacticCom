package com.rmrbranco.galacticcom.data.managers

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.TimeUnit

/**
 * A singleton object responsible for loading and providing access to global app settings
 * from the Firebase Realtime Database.
 */
object SettingsManager {

    private val settings = mutableMapOf<String, Any>()

    fun initialize() {
        val settingsRef = FirebaseDatabase.getInstance().getReference("settings")
        settingsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                settings.clear()
                for (child in snapshot.children) {
                    child.key?.let { key ->
                        child.value?.let { value ->
                            settings[key] = value
                        }
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                // In a real app, you might want more robust error handling,
                // like falling back to default values.
            }
        })
    }
    
    // Helper to get a long value with a default
    private fun getLong(key: String, defaultValue: Long): Long {
        val value = settings[key]
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is String -> value.toLongOrNull() ?: defaultValue
            else -> defaultValue
        }
    }

    // --- Accessor Methods ---

    // Travel Parameters
    fun getPlanetaryTravelTimeMinutes(): Long = getLong("planetaryTravelTimeMinutes", 60)
    fun getIntergalacticTravelTimeMinutes(): Long = getLong("intergalacticTravelTimeMinutes", TimeUnit.HOURS.toMinutes(24))

    // Communication Parameters
    fun getPublicMessageDailyLimit(): Long = getLong("publicMessageDailyLimit", 1)
    fun getDefaultPublicMessageDeliveryTimeMinutes(): Long = getLong("defaultPublicMessageDeliveryTimeMinutes", TimeUnit.HOURS.toMinutes(2))
    fun getPrivateMessageDailyLimit(): Long = getLong("privateMessageDailyLimit", 200)

    // User Action Limits
    fun getDailyPlanetaryTravelLimit(): Long = getLong("dailyPlanetaryTravelLimit", 3)
    fun getWeeklyIntergalacticTravelLimit(): Long = getLong("weeklyIntergalacticTravelLimit", 1)
    fun getMonthlyAvatarChangeLimit(): Long = getLong("monthlyAvatarChangeLimit", 1)
}