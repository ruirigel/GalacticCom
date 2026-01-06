package com.rmrbranco.galacticcom

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.MutableData
import com.google.firebase.database.Transaction

object ExperienceUtils {

    fun incrementExperience(userId: String) {
        val database = FirebaseDatabase.getInstance()
        val userExperienceRef = database.reference.child("users").child(userId).child("experiencePoints")

        userExperienceRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                var currentValue = currentData.getValue(Long::class.java) ?: 0L
                currentValue++
                currentData.value = currentValue
                return Transaction.success(currentData)
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (error != null) {
                    Log.w("ExperienceUtils", "Experience point transaction failed.", error.toException())
                }
            }
        })
    }
}
