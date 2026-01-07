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
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.data.managers.AdManager
import com.rmrbranco.galacticcom.data.managers.BadgeManager
import com.rmrbranco.galacticcom.data.managers.BadgeProgressManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import com.rmrbranco.galacticcom.data.model.ActionLogs
import com.rmrbranco.galacticcom.data.model.Badge
import com.rmrbranco.galacticcom.data.model.BadgeTier
import kotlinx.coroutines.launch
import java.util.Calendar

class UserProfileDialogFragment : DialogFragment() {

    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private var userId: String? = null
    private var currentUserId: String? = null
    private var isBlocked = false
    private lateinit var badgeAdapter: BadgeAdapter
    
    // Hold references to profile views to allow refreshing
    private var mProfileImage: ImageView? = null
    private var mProfileName: TextView? = null
    private var mProfileEmblem: ImageView? = null
    private var mProfileBio: TextView? = null
    private var mProfileExperience: TextView? = null
    private var mProfileGalaxy: TextView? = null
    private var mProfileStar: TextView? = null
    private var mProfilePlanet: TextView? = null
    private var mBlockUserButton: Button? = null

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

        mProfileImage = view.findViewById(R.id.profile_image)
        mProfileName = view.findViewById(R.id.profile_name)
        mProfileEmblem = view.findViewById(R.id.profile_emblem_lone_traveler)
        mProfileBio = view.findViewById(R.id.profile_bio)
        mProfileExperience = view.findViewById(R.id.profile_experience)
        mProfileGalaxy = view.findViewById(R.id.profile_galaxy)
        mProfileStar = view.findViewById(R.id.profile_star)
        mProfilePlanet = view.findViewById(R.id.profile_planet)
        val closeButton = view.findViewById<Button>(R.id.dialog_close_button)
        val regenerateAvatarButton = view.findViewById<Button>(R.id.btn_regenerate_avatar)
        mBlockUserButton = view.findViewById(R.id.btn_block_user)
        val reportAbuseButton = view.findViewById<Button>(R.id.btn_report_abuse)
        val badgesRecyclerView = view.findViewById<RecyclerView>(R.id.badges_recycler_view)
        
        // Load Ad
        AdManager.loadRewardedAd(requireContext())

        // Setup RecyclerView for Badges with Click Listener
        badgeAdapter = BadgeAdapter { badge ->
            showBadgeDetailsDialog(badge)
        }
        badgesRecyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        badgesRecyclerView.adapter = badgeAdapter

        if (userId == currentUserId) {
            // Viewing own profile
            mBlockUserButton?.visibility = View.GONE
            reportAbuseButton.visibility = View.GONE
            regenerateAvatarButton.visibility = View.VISIBLE
            regenerateAvatarButton.setOnClickListener { checkAvatarChangeLimitAndRegenerate(mProfileImage!!) }
        } else {
            // Viewing other user's profile
            regenerateAvatarButton.visibility = View.GONE
            mBlockUserButton?.visibility = View.VISIBLE
            reportAbuseButton.visibility = View.VISIBLE
            mBlockUserButton?.setOnClickListener { showBlockConfirmationDialog() }
            reportAbuseButton.setOnClickListener { showReportAbuseDialog() }
        }

        closeButton.setOnClickListener { dismiss() }

        loadUserProfile()
    }

    private fun showBadgeDetailsDialog(badge: Badge) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_badge_details)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val iconView = dialog.findViewById<ImageView>(R.id.dialog_badge_icon)
        val nameView = dialog.findViewById<TextView>(R.id.dialog_badge_name)
        val tierView = dialog.findViewById<TextView>(R.id.dialog_badge_tier)
        val rewardView = dialog.findViewById<TextView>(R.id.dialog_badge_reward_value) // New View
        val descriptionView = dialog.findViewById<TextView>(R.id.dialog_badge_description)
        val progressText = dialog.findViewById<TextView>(R.id.dialog_badge_progress_text)
        val progressBar = dialog.findViewById<ProgressBar>(R.id.dialog_badge_progress_bar)
        val claimButton = dialog.findViewById<Button>(R.id.btn_claim_badge_reward)
        val closeButton = dialog.findViewById<Button>(R.id.btn_close_badge_dialog)

        nameView.text = badge.name
        descriptionView.text = badge.description
        
        // Progress Logic
        progressBar.max = badge.maxProgress
        progressBar.progress = badge.progress
        progressText.text = "Progress: ${badge.progress} / ${badge.maxProgress}"

        // Tier Logic & Coloring
        val tierColor = when (badge.currentTier) {
            BadgeTier.BRONZE -> 0xFFCD7F32.toInt()
            BadgeTier.SILVER -> 0xFFC0C0C0.toInt()
            BadgeTier.GOLD -> 0xFFFFD700.toInt()
            BadgeTier.PLATINUM -> 0xFFE5E4E2.toInt()
            BadgeTier.DIAMOND -> 0xFFB9F2FF.toInt()
            BadgeTier.ULTRA_RARE -> 0xFF9400D3.toInt()
            BadgeTier.LEGENDARY -> 0xFFFF0000.toInt()
            else -> ContextCompat.getColor(requireContext(), R.color.darker_gray)
        }

        iconView.setColorFilter(tierColor)
        tierView.setTextColor(tierColor)
        tierView.text = "TIER: ${badge.currentTier.name.replace("_", " ")}"
        
        // Force white color for reward text
        rewardView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        
        if (badge.rewardAmount > 0) {
            rewardView.text = "Reward: ${formatNumber(badge.rewardAmount.toLong())} Credits"
            rewardView.visibility = View.VISIBLE
        } else {
            rewardView.visibility = View.GONE
        }
        
        if (!badge.isUnlocked) {
            iconView.alpha = 0.5f
            tierView.text = "TIER: LOCKED"
            rewardView.alpha = 1.0f // Ensure text is fully opaque even if locked
            claimButton.visibility = View.GONE
        } else {
            iconView.alpha = 1.0f
            rewardView.alpha = 1.0f
            
            // Show Claim Button if unlocked AND not claimed AND user is viewing own profile
            if (userId == currentUserId && !badge.isClaimed && badge.rewardAmount > 0) {
                claimButton.visibility = View.VISIBLE
                claimButton.text = "CLAIM (AD)" // Hint that it requires an Ad
                claimButton.setOnClickListener {
                    if (AdManager.isAdReady()) {
                        AdManager.showRewardedAd(requireActivity(), { _, _ ->
                            // Reward earned
                            BadgeProgressManager.claimReward(badge) {
                                if (isAdded) {
                                    Toast.makeText(context, "Claimed ${badge.rewardAmount} Credits!", Toast.LENGTH_SHORT).show()
                                    dialog.dismiss()
                                    loadUserProfile() // Refresh badges
                                }
                            }
                        }, {
                            // Ad closed without reward or after reward
                        })
                    } else {
                        Toast.makeText(context, "Ad loading... please wait.", Toast.LENGTH_SHORT).show()
                        AdManager.loadRewardedAd(requireContext())
                    }
                }
            } else if (badge.isClaimed) {
                claimButton.visibility = View.GONE
                rewardView.text = "Reward: Claimed"
                rewardView.setTextColor(ContextCompat.getColor(requireContext(), R.color.darker_gray))
            } else {
                claimButton.visibility = View.GONE
            }
        }

        closeButton.setOnClickListener { dialog.dismiss() }
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }
    
    // Helper for number formatting (reused)
    private fun formatNumber(value: Long): String {
        val v = value.toDouble()
        if (v < 1000) return "%.0f".format(v)
        val suffixes = arrayOf("", "k", "M", "B", "T")
        val exp = (Math.log10(v) / 3).toInt().coerceIn(0, suffixes.size - 1)
        return "%.1f%s".format(v / Math.pow(1000.0, exp.toDouble()), suffixes[exp])
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
    
    private fun loadUserProfile() { 
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
                        
                        // Fetch ActionLogs for Badges
                        val actionLogsSnapshot = snapshot.child("actionLogs")
                        val actionLogs = actionLogsSnapshot.getValue(ActionLogs::class.java) ?: ActionLogs()
                        val badges = BadgeManager.getBadges(actionLogs)
                        badgeAdapter.updateBadges(badges)

                        lifecycleScope.launch { 
                            val avatar = AlienAvatarGenerator.generate(avatarSeed, 256, 256)
                            mProfileImage?.setImageBitmap(avatar) 
                        }
                        
                        mProfileName?.text = nickname
                        mProfileBio?.text = bio
                        mProfileExperience?.text = points.toString()
                        mProfileGalaxy?.text = galaxy
                        mProfileStar?.text = star
                        mProfilePlanet?.text = planet
                        mProfileEmblem?.visibility = if (hasLoneTravelerEmblem) View.VISIBLE else View.GONE
                    } 
                    override fun onCancelled(error: DatabaseError) { 
                        Toast.makeText(context, "Failed to load user profile.", Toast.LENGTH_SHORT).show() 
                    } 
                })
            } else {
                // Viewing other: Must fetch ONLY public fields individually or specific public node
                userRef.child("nickname").get().addOnSuccessListener { 
                    mProfileName?.text = it.getValue(String::class.java) ?: "N/A"
                }
                userRef.child("bio").get().addOnSuccessListener { 
                    mProfileBio?.text = it.getValue(String::class.java) ?: "N/A"
                }
                userRef.child("experiencePoints").get().addOnSuccessListener { 
                    val points = it.getValue(Long::class.java) ?: 0L
                    mProfileExperience?.text = points.toString()
                }
                userRef.child("avatarSeed").get().addOnSuccessListener { 
                    val seed = it.getValue(String::class.java) ?: ""
                    lifecycleScope.launch { 
                        val avatar = AlienAvatarGenerator.generate(seed, 256, 256)
                        mProfileImage?.setImageBitmap(avatar) 
                    }
                }
                userRef.child("emblems/lone_traveler").get().addOnSuccessListener { 
                    val hasEmblem = it.getValue(Boolean::class.java) ?: false
                    mProfileEmblem?.visibility = if (hasEmblem) View.VISIBLE else View.GONE
                }
                
                // Fetch ActionLogs for Badges (Allow viewing others badges)
                userRef.child("actionLogs").get().addOnSuccessListener {
                    val actionLogs = it.getValue(ActionLogs::class.java) ?: ActionLogs()
                    val badges = BadgeManager.getBadges(actionLogs)
                    badgeAdapter.updateBadges(badges)
                }

                // Location data might be restricted by rules, handle gracefully
                userRef.child("galaxy").get().addOnSuccessListener { 
                    mProfileGalaxy?.text = it.getValue(String::class.java) ?: "Unknown" 
                }.addOnFailureListener { mProfileGalaxy?.text = "Restricted" }
                
                userRef.child("star").get().addOnSuccessListener { 
                    mProfileStar?.text = it.getValue(String::class.java) ?: "Unknown" 
                }.addOnFailureListener { mProfileStar?.text = "Restricted" }
                
                userRef.child("planet").get().addOnSuccessListener { 
                    mProfilePlanet?.text = it.getValue(String::class.java) ?: "Unknown" 
                }.addOnFailureListener { mProfilePlanet?.text = "Restricted" }
            }
        }
        
        if (userId != currentUserId) { 
            currentUserId?.let { 
                database.reference.child("users").child(it).child("blockedUsers").child(userId!!).get().addOnSuccessListener { 
                    isBlocked = it.exists()
                    mBlockUserButton?.text = if (isBlocked) "Unblock" else "Block" 
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
        val updates = mapOf("/users/$currentUserId/blockedUsers/$userId" to true)
        
        database.reference.updateChildren(updates).addOnCompleteListener { task -> 
            if (task.isSuccessful) { 
                Toast.makeText(context, "User blocked.", Toast.LENGTH_SHORT).show() 
                isBlocked = true
                mBlockUserButton?.text = "Unblock"
            } else { 
                Toast.makeText(context, "Failed to block user.", Toast.LENGTH_SHORT).show() 
            } 
        } 
    }

    private fun unblockUser() { 
        if (currentUserId == null || userId == null) return
        val updates = mapOf("/users/$currentUserId/blockedUsers/$userId" to null)
        
        database.reference.updateChildren(updates).addOnCompleteListener { task -> 
            if (task.isSuccessful) { 
                Toast.makeText(context, "User unblocked.", Toast.LENGTH_SHORT).show() 
                isBlocked = false
                mBlockUserButton?.text = "Block"
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