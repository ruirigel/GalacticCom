package com.rmrbranco.galacticcom.data.managers

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.ServerValue
import com.google.firebase.database.Transaction
import com.rmrbranco.galacticcom.data.model.ActionLogs
import com.rmrbranco.galacticcom.data.model.Badge
import java.util.Calendar

object BadgeProgressManager {

    private val database = FirebaseDatabase.getInstance()
    private val auth = FirebaseAuth.getInstance()

    // NEW: Claim Reward Logic
    fun claimReward(badge: Badge, onSuccess: () -> Unit) {
        val uid = auth.currentUser?.uid ?: return
        val badgeId = badge.id
        val tierName = badge.currentTier.name
        val rewardAmount = badge.rewardAmount
        
        if (rewardAmount <= 0) return

        val userRef = database.getReference("users/$uid")
        
        userRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val actionLogs = mutableData.child("actionLogs")
                val inventoryData = mutableData.child("inventory_data")
                
                // Check if already claimed to prevent race conditions
                val claimKey = "${badgeId}_${tierName}"
                if (actionLogs.child("claimedBadgeRewards").hasChild(claimKey)) {
                    return Transaction.abort()
                }
                
                // Add credits
                val currentCredits = inventoryData.child("credits").getValue(Long::class.java) ?: 0L
                inventoryData.child("credits").value = currentCredits + rewardAmount
                
                // Mark as claimed
                actionLogs.child("claimedBadgeRewards").child(claimKey).value = true
                
                return Transaction.success(mutableData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {
                if (committed) {
                    onSuccess()
                }
            }
        })
    }

    // Badge 7: Mineiro de Matéria Escura
    fun addMiningProgress(amount: Long) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs/miningTotalAccumulated")
        
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val current = mutableData.getValue(Long::class.java) ?: 0L
                mutableData.value = current + amount
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    // Badge 6: O Erudito de Mil Mundos
    fun recordGalaxyVisit(galaxyName: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs/visitedGalaxies/$galaxyName")
        ref.setValue(true)
    }

    // Badge 8: A Voz das Estrelas
    fun addVoiceSeconds(seconds: Long) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs/totalVoiceMinutesSent")
        
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentMinutes = mutableData.getValue(Double::class.java) ?: 0.0
                val addedMinutes = seconds / 60.0
                mutableData.value = currentMinutes + addedMinutes
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    // Badge 5: Sócio Vitalício do Star Nomad
    fun recordHarlockPurchase(itemId: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs/harlockItemsPurchased/$itemId")
        
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val currentCount = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = currentCount + 1
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }

    // Badge 1: O Observador de Eras (CLIENT-SIDE IMPLEMENTATION)
    fun recordBroadcast() {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs")
        
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                // Manually parse fields because ActionLogs might have new fields not in local model yet
                val lastTimestamp = mutableData.child("lastBroadcastForBadgeTimestamp").getValue(Long::class.java) ?: 0L
                val currentMonths = mutableData.child("monthsWithBroadcast").getValue(Int::class.java) ?: 0
                
                val now = System.currentTimeMillis()
                val calendarNow = Calendar.getInstance().apply { timeInMillis = now }
                val calendarLast = Calendar.getInstance().apply { timeInMillis = lastTimestamp }
                
                // Check if it's a different month OR if it's the very first broadcast (timestamp 0)
                val isDifferentMonth = (calendarNow.get(Calendar.YEAR) != calendarLast.get(Calendar.YEAR)) || 
                                       (calendarNow.get(Calendar.MONTH) != calendarLast.get(Calendar.MONTH))
                
                if (isDifferentMonth || lastTimestamp == 0L) {
                    mutableData.child("monthsWithBroadcast").value = currentMonths + 1
                    mutableData.child("lastBroadcastForBadgeTimestamp").value = now
                }
                
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }
    
    // Badge 3: A Garrafa de Vidro de Murano (UPDATED: Track Unique Target Galaxies)
    fun recordMessageSentToGalaxy(targetGalaxyName: String) {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs/messagedGalaxies/$targetGalaxyName")
        ref.setValue(true)
    }

    // Badge 9: O Guardião do Vácuo
    fun incrementInterceptedMessages() {
        val uid = auth.currentUser?.uid ?: return
        val ref = database.getReference("users/$uid/actionLogs/deepSpaceMessagesIntercepted")
        
        ref.runTransaction(object : Transaction.Handler {
            override fun doTransaction(mutableData: MutableData): Transaction.Result {
                val current = mutableData.getValue(Int::class.java) ?: 0
                mutableData.value = current + 1
                return Transaction.success(mutableData)
            }
            override fun onComplete(error: DatabaseError?, committed: Boolean, snapshot: DataSnapshot?) {}
        })
    }
}
