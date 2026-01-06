package com.rmrbranco.galacticcom

import android.app.Dialog
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import kotlinx.coroutines.launch
import java.util.Calendar

class UserProfileDialogFragment : DialogFragment() {

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null
    private var currentUserId: String? = null
    private var isBlocked = false

    companion object {
        private const val ARG_USER_ID = "user_id"

        fun newInstance(userId: String): UserProfileDialogFragment {
            val fragment = UserProfileDialogFragment()
            val args = Bundle()
            args.putString(ARG_USER_ID, userId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()
        currentUserId = auth.currentUser?.uid
        arguments?.let {
            userId = it.getString(ARG_USER_ID)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_public_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val profileImage = view.findViewById<ImageView>(R.id.profile_image)
        val profileName = view.findViewById<TextView>(R.id.profile_name)
        val profileEmblem = view.findViewById<ImageView>(R.id.profile_emblem_lone_traveler)
        val profileBio = view.findViewById<TextView>(R.id.profile_bio)
        val profileExperience = view.findViewById<TextView>(R.id.profile_experience)
        val profileGalaxy = view.findViewById<TextView>(R.id.profile_galaxy)
        val profileStar = view.findViewById<TextView>(R.id.profile_star)
        val profilePlanet = view.findViewById<TextView>(R.id.profile_planet)
        val closeButton = view.findViewById<Button>(R.id.dialog_close_button)
        val regenerateAvatarButton = view.findViewById<Button>(R.id.btn_regenerate_avatar)
        val blockUserButton = view.findViewById<Button>(R.id.btn_block_user)
        val reportAbuseButton = view.findViewById<Button>(R.id.btn_report_abuse)

        if (userId == currentUserId) {
            // Viewing own profile
            blockUserButton.visibility = View.GONE
            reportAbuseButton.visibility = View.GONE
            regenerateAvatarButton.visibility = View.VISIBLE
            regenerateAvatarButton.setOnClickListener { checkAvatarChangeLimitAndRegenerate(profileImage) }
        } else {
            // Viewing other user's profile
            regenerateAvatarButton.visibility = View.GONE
            blockUserButton.visibility = View.VISIBLE
            reportAbuseButton.visibility = View.VISIBLE
            blockUserButton.setOnClickListener { showBlockConfirmationDialog() }
            reportAbuseButton.setOnClickListener { showReportAbuseDialog() }
        }

        closeButton.setOnClickListener { dismiss() }

        loadUserProfile(profileImage, profileName, profileEmblem, profileBio, profileExperience, profileGalaxy, profileStar, profilePlanet, blockUserButton)
    }

    private fun checkAvatarChangeLimitAndRegenerate(profileImage: ImageView) {
        val actionLogsRef = database.reference.child("users/$currentUserId/actionLogs")
        actionLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastChangeTimestamp = snapshot.child("lastAvatarChangeTimestamp").getValue(Long::class.java) ?: 0L
                var changeCountThisMonth = snapshot.child("avatarChangeCountThisMonth").getValue(Int::class.java) ?: 0

                val today = Calendar.getInstance()
                val lastChangeMonth = Calendar.getInstance().apply { timeInMillis = lastChangeTimestamp }

                if (today.get(Calendar.MONTH) != lastChangeMonth.get(Calendar.MONTH) ||
                    today.get(Calendar.YEAR) != lastChangeMonth.get(Calendar.YEAR)) {
                    changeCountThisMonth = 0 // Reset counter for the new month
                }

                val monthlyLimit = SettingsManager.getMonthlyAvatarChangeLimit()

                if (changeCountThisMonth < monthlyLimit) {
                    regenerateAvatar(profileImage, changeCountThisMonth, actionLogsRef)
                } else {
                    val inventoryRef = database.getReference("users/$currentUserId/inventory/avatar_resets")
                    inventoryRef.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(invSnapshot: DataSnapshot) {
                            val seeds = invSnapshot.getValue(Int::class.java) ?: 0
                            if (seeds > 0) {
                                inventoryRef.setValue(seeds - 1).addOnSuccessListener {
                                    Toast.makeText(context, "Used 1 Avatar Seed. Remaining: ${seeds - 1}", Toast.LENGTH_SHORT).show()
                                    regenerateAvatar(profileImage, changeCountThisMonth, actionLogsRef)
                                }
                            } else {
                                Toast.makeText(context, "You have reached your monthly limit for avatar changes.", Toast.LENGTH_SHORT).show()
                            }
                        }
                        override fun onCancelled(error: DatabaseError) {
                            Toast.makeText(context, "Failed to verify inventory.", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to verify avatar change limit. Please try again.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun regenerateAvatar(profileImage: ImageView, currentCount: Int, logsRef: DatabaseReference) {
        val newSeed = System.currentTimeMillis().toString()
        database.reference.child("users").child(currentUserId!!).child("avatarSeed").setValue(newSeed).addOnSuccessListener {
            val updates = mapOf(
                "lastAvatarChangeTimestamp" to ServerValue.TIMESTAMP,
                "avatarChangeCountThisMonth" to (currentCount + 1)
            )
            logsRef.updateChildren(updates)

            viewLifecycleOwner.lifecycleScope.launch {
                val newAvatar = AlienAvatarGenerator.generate(newSeed, 256, 256)
                profileImage.setImageBitmap(newAvatar)
                Toast.makeText(context, "Avatar Regenerated!", Toast.LENGTH_SHORT).show()
            }
        }
    }
    
    private fun loadUserProfile(
        profileImage: ImageView, 
        profileName: TextView, 
        profileEmblem: ImageView, 
        profileBio: TextView, 
        profileExperience: TextView, 
        profileGalaxy: TextView, 
        profileStar: TextView, 
        profilePlanet: TextView, 
        blockUserButton: Button
    ) { 
        userId?.let { uid ->
            val userRef = database.reference.child("users").child(uid)
            
            if (uid == currentUserId) {
                // Viewing self: Can fetch entire node
                userRef.addListenerForSingleValueEvent(object : ValueEventListener { 
                    override fun onDataChange(snapshot: DataSnapshot) { 
                        val avatarSeed = snapshot.child("avatarSeed").getValue(String::class.java) ?: ""
                        val nickname = snapshot.child("nickname").getValue(String::class.java) ?: "N/A"
                        val bio = snapshot.child("bio").getValue(String::class.java) ?: "N/A"
                        val points = snapshot.child("experiencePoints").getValue(Long::class.java) ?: 0L
                        val galaxy = snapshot.child("galaxy").getValue(String::class.java) ?: "N/A"
                        val star = snapshot.child("star").getValue(String::class.java) ?: "N/A"
                        val planet = snapshot.child("planet").getValue(String::class.java) ?: "N/A"
                        val hasLoneTravelerEmblem = snapshot.child("emblems/lone_traveler").getValue(Boolean::class.java) ?: false

                        lifecycleScope.launch { 
                            val avatar = AlienAvatarGenerator.generate(avatarSeed, 256, 256)
                            profileImage.setImageBitmap(avatar) 
                        }
                        
                        profileName.text = nickname
                        profileBio.text = bio
                        profileExperience.text = points.toString()
                        profileGalaxy.text = galaxy
                        profileStar.text = star
                        profilePlanet.text = planet
                        profileEmblem.visibility = if (hasLoneTravelerEmblem) View.VISIBLE else View.GONE
                    } 
                    override fun onCancelled(error: DatabaseError) { 
                        Toast.makeText(context, "Failed to load user profile.", Toast.LENGTH_SHORT).show() 
                    } 
                })
            } else {
                // Viewing other: Must fetch ONLY public fields individually
                userRef.child("nickname").get().addOnSuccessListener { 
                    profileName.text = it.getValue(String::class.java) ?: "N/A"
                }
                userRef.child("bio").get().addOnSuccessListener { 
                    profileBio.text = it.getValue(String::class.java) ?: "N/A"
                }
                userRef.child("experiencePoints").get().addOnSuccessListener { 
                    val points = it.getValue(Long::class.java) ?: 0L
                    profileExperience.text = points.toString()
                }
                userRef.child("avatarSeed").get().addOnSuccessListener { 
                    val seed = it.getValue(String::class.java) ?: ""
                    lifecycleScope.launch { 
                        val avatar = AlienAvatarGenerator.generate(seed, 256, 256)
                        profileImage.setImageBitmap(avatar) 
                    }
                }
                userRef.child("emblems/lone_traveler").get().addOnSuccessListener { 
                    val hasEmblem = it.getValue(Boolean::class.java) ?: false
                    profileEmblem.visibility = if (hasEmblem) View.VISIBLE else View.GONE
                }
                
                // Location data might be restricted by rules, handle gracefully
                userRef.child("galaxy").get().addOnSuccessListener { 
                    profileGalaxy.text = it.getValue(String::class.java) ?: "Unknown" 
                }.addOnFailureListener { profileGalaxy.text = "Restricted" }
                
                userRef.child("star").get().addOnSuccessListener { 
                    profileStar.text = it.getValue(String::class.java) ?: "Unknown" 
                }.addOnFailureListener { profileStar.text = "Restricted" }
                
                userRef.child("planet").get().addOnSuccessListener { 
                    profilePlanet.text = it.getValue(String::class.java) ?: "Unknown" 
                }.addOnFailureListener { profilePlanet.text = "Restricted" }
            }
        }
        
        if (userId != currentUserId) { 
            currentUserId?.let { 
                database.reference.child("users").child(it).child("blockedUsers").child(userId!!).get().addOnSuccessListener { 
                    isBlocked = it.exists()
                    blockUserButton.text = if (isBlocked) "Unblock" else "Block" 
                } 
            } 
        } 
    }

    private fun showBlockConfirmationDialog() { 
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message)
        val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button)
        val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val galaxyStatsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout)
        
        val title = if (isBlocked) "Unblock User" else "Block User"
        val message = if (isBlocked) "Are you sure you want to unblock this user?" else "Are you sure you want to block this user?"
        
        titleTextView.text = title
        messageTextView.text = message
        negativeButton.text = "Cancel"
        positiveButton.text = if (isBlocked) "Unblock" else "Block"
        subtitleTextView.visibility = View.GONE
        galaxyStatsLayout.visibility = View.GONE
        messageTextView.gravity = Gravity.START
        titleTextView.gravity = Gravity.START
        
        negativeButton.setOnClickListener { dialog.dismiss() }
        positiveButton.setOnClickListener { 
            if (isBlocked) unblockUser() else blockUser()
            dialog.dismiss() 
        }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) 
    }

    private fun blockUser() { 
        if (currentUserId == null || userId == null) return
        // SECURITY FIX: Only write to my own node. Do not write to the other user's node.
        val updates = mapOf("/users/$currentUserId/blockedUsers/$userId" to true)
        
        database.reference.updateChildren(updates).addOnCompleteListener { task -> 
            if (task.isSuccessful) { 
                Toast.makeText(context, "User blocked.", Toast.LENGTH_SHORT).show() 
                isBlocked = true
                view?.findViewById<Button>(R.id.btn_block_user)?.text = "Unblock"
            } else { 
                Toast.makeText(context, "Failed to block user.", Toast.LENGTH_SHORT).show() 
            } 
        } 
    }

    private fun unblockUser() { 
        if (currentUserId == null || userId == null) return
        // SECURITY FIX: Only write to my own node.
        val updates = mapOf("/users/$currentUserId/blockedUsers/$userId" to null)
        
        database.reference.updateChildren(updates).addOnCompleteListener { task -> 
            if (task.isSuccessful) { 
                Toast.makeText(context, "User unblocked.", Toast.LENGTH_SHORT).show() 
                isBlocked = false
                view?.findViewById<Button>(R.id.btn_block_user)?.text = "Block"
            } else { 
                Toast.makeText(context, "Failed to unblock user.", Toast.LENGTH_SHORT).show() 
            } 
        } 
    }

    private fun showReportAbuseDialog() { 
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message)
        val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button)
        val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val galaxyStatsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout)
        
        titleTextView.text = "Report Abuse"
        messageTextView.text = "Are you sure you want to report this user for abuse?"
        negativeButton.text = "Cancel"
        positiveButton.text = "Report"
        subtitleTextView.visibility = View.GONE
        galaxyStatsLayout.visibility = View.GONE
        messageTextView.gravity = Gravity.START
        titleTextView.gravity = Gravity.START
        
        negativeButton.setOnClickListener { dialog.dismiss() }
        positiveButton.setOnClickListener { reportAbuse(); dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) 
    }

    private fun reportAbuse() { 
        if (currentUserId == null || userId == null) return
        val conversationId = if (currentUserId!! < userId!!) "${currentUserId}_${userId}" else "${userId}_${currentUserId}"
        val messagesRef = database.reference.child("conversations").child(conversationId).child("messages")
        messagesRef.orderByChild("timestamp").limitToLast(60).addListenerForSingleValueEvent(object : ValueEventListener { 
            override fun onDataChange(snapshot: DataSnapshot) { 
                val messages = snapshot.children.mapNotNull { it.getValue(ChatMessage::class.java) }
                val report = hashMapOf("reporterId" to currentUserId, "reportedId" to userId, "timestamp" to System.currentTimeMillis(), "conversationSnapshot" to messages)
                // Note: Ensure "reports" node has .write permission in rules if used
                database.reference.child("reports").push().setValue(report).addOnCompleteListener { task -> 
                    if (task.isSuccessful) { 
                        Toast.makeText(context, "User reported.", Toast.LENGTH_SHORT).show() 
                    } else { 
                        Toast.makeText(context, "Failed to report user.", Toast.LENGTH_SHORT).show() 
                    } 
                } 
            } 
            override fun onCancelled(error: DatabaseError) { 
                Toast.makeText(context, "Failed to create report.", Toast.LENGTH_SHORT).show() 
            } 
        }) 
    }
    
    override fun onStart() { 
        super.onStart()
        dialog?.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent) 
    }
}