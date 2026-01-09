package com.rmrbranco.galacticcom

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavOptions
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.storage.FirebaseStorage
import com.rmrbranco.galacticcom.data.managers.AdManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

class SettingsFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var userRef: DatabaseReference? = null

    // Views
    private lateinit var avatarImageView: ImageView
    private lateinit var nicknameDataTextView: TextView
    private lateinit var bioDataTextView: TextView
    private lateinit var pointsDataTextView: TextView
    private lateinit var notificationsSwitch: SwitchMaterial
    private lateinit var defaultViewSwitch: SwitchMaterial
    private lateinit var musicSwitch: SwitchMaterial
    private lateinit var glitchSwitch: SwitchMaterial
    
    // Privacy Switches
    private lateinit var badgesSwitch: SwitchMaterial
    private lateinit var bioXpSwitch: SwitchMaterial
    private lateinit var planetarySwitch: SwitchMaterial

    private lateinit var contentScrollView: ScrollView
    private lateinit var emailDataTextView: TextView
    private lateinit var titleTextView: TextView
    private lateinit var backButton: ImageButton
    private lateinit var blockedCountTextView: TextView

    private var currentUserNickname: String = ""
    private var currentUserBio: String = ""
    private var currentUserAvatarSeed: String = ""
    private var userDataListener: ValueEventListener? = null
    private var privacyListener: ValueEventListener? = null
    private var blockedUsersListener: ValueEventListener? = null
    private var currentBlockedCount: Long = 0

    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        auth.currentUser?.uid?.let {
            userRef = database.reference.child("users").child(it)
        }
        return inflater.inflate(R.layout.fragment_settings, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        avatarImageView = view.findViewById(R.id.iv_setting_avatar)
        nicknameDataTextView = view.findViewById(R.id.tv_setting_nickname_data)
        bioDataTextView = view.findViewById(R.id.tv_setting_bio_data)
        pointsDataTextView = view.findViewById(R.id.tv_setting_experience_data)
        notificationsSwitch = view.findViewById(R.id.switch_notifications)
        defaultViewSwitch = view.findViewById(R.id.switch_default_view)
        musicSwitch = view.findViewById(R.id.switch_music)
        glitchSwitch = view.findViewById(R.id.switch_glitch_effect)
        
        badgesSwitch = view.findViewById(R.id.switch_show_badges)
        bioXpSwitch = view.findViewById(R.id.switch_show_bio_xp)
        planetarySwitch = view.findViewById(R.id.switch_show_planetary_system)

        contentScrollView = view.findViewById(R.id.settings_scroll_view)
        emailDataTextView = view.findViewById(R.id.tv_setting_email_data)
        titleTextView = view.findViewById(R.id.settings_title)
        backButton = view.findViewById(R.id.back_button)
        blockedCountTextView = view.findViewById(R.id.tv_blocked_count)

        // Setup listeners
        backButton.setOnClickListener { findNavController().popBackStack() }
        view.findViewById<ImageButton>(R.id.btn_edit_nickname).setOnClickListener { checkNicknameChangeEligibility() }
        view.findViewById<ImageButton>(R.id.btn_edit_bio).setOnClickListener { showEditBioDialog() }
        view.findViewById<Button>(R.id.btn_change_password).setOnClickListener { showChangePasswordDialog() }
        view.findViewById<Button>(R.id.btn_setting_logout).setOnClickListener { showLogoutConfirmationDialog() }
        view.findViewById<Button>(R.id.btn_setting_delete).setOnClickListener { showDeleteConfirmationDialog() }
        view.findViewById<Button>(R.id.btn_view_public_profile).setOnClickListener { showPublicProfileDialog() }
        
        // Blocked Users Button
        view.findViewById<View>(R.id.btn_blocked_users).setOnClickListener {
             if (currentBlockedCount > 0) {
                 showBlockedUsersDialog()
             } else {
                 Toast.makeText(context, "No blocked users found", Toast.LENGTH_SHORT).show()
             }
        }

        setupDefaultViewSwitch()
        setupMusicSwitch()
        setupGlitchSwitch()
        listenForUserStatus()
        setupPrivacySwitches()
        listenForBlockedUsersCount()
        
        // Load Ad for nickname change
        AdManager.loadRewardedAd(requireContext())
    }
    
    private fun listenForBlockedUsersCount() {
        val currentUserId = auth.currentUser?.uid ?: return
        val blockedRef = database.reference.child("users").child(currentUserId).child("blockedUsers")
        blockedUsersListener = blockedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                currentBlockedCount = snapshot.childrenCount
                blockedCountTextView.text = snapshot.childrenCount.toString()
            }
            override fun onCancelled(error: DatabaseError) {
                // Ignore
            }
        })
    }
    
    private fun checkNicknameChangeEligibility() {
        if (!isAdded) return
        
        // Check global settings for cooldown days (create if not exists)
        val settingsRef = database.reference.child("settings").child("nicknameCooldownDays")
        settingsRef.get().addOnSuccessListener { settingsSnapshot ->
            var cooldownDays = settingsSnapshot.getValue(Long::class.java)
            if (cooldownDays == null) {
                // If parameter doesn't exist, create it with default value 30
                cooldownDays = 30L
                settingsRef.setValue(cooldownDays)
            }

            // Check user's last change timestamp
            userRef?.child("lastNicknameChangeTimestamp")?.get()?.addOnSuccessListener { userSnapshot ->
                val lastTimestamp = userSnapshot.getValue(Long::class.java) ?: 0L
                val currentTimestamp = System.currentTimeMillis()
                
                val cooldownMillis = TimeUnit.DAYS.toMillis(cooldownDays)
                val timeSinceLastChange = currentTimestamp - lastTimestamp

                if (timeSinceLastChange < cooldownMillis) {
                    val daysRemaining = TimeUnit.MILLISECONDS.toDays(cooldownMillis - timeSinceLastChange) + 1
                    Toast.makeText(context, "Nickname change available in $daysRemaining days.", Toast.LENGTH_LONG).show()
                } else {
                    showEditNicknameDialog()
                }
            }?.addOnFailureListener {
                Toast.makeText(context, "Failed to check eligibility.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showPublicProfileDialog() { val userId = auth.currentUser?.uid; if (userId != null) { val userProfileDialog = UserProfileDialogFragment.newInstance(userId); userProfileDialog.show(parentFragmentManager, "UserProfileDialogFragment") } }
    
    private fun setupDefaultViewSwitch() { 
        val sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val useVisualView = sharedPreferences.getBoolean("default_view_is_visual", true)
        defaultViewSwitch.isChecked = useVisualView
        defaultViewSwitch.setOnCheckedChangeListener { _, isChecked -> 
            sharedPreferences.edit { putBoolean("default_view_is_visual", isChecked) }
            Toast.makeText(context, "View preference saved.", Toast.LENGTH_SHORT).show() 
        } 
    }
    
    private fun setupMusicSwitch() {
        val sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val musicEnabled = sharedPreferences.getBoolean("music_enabled", true)
        musicSwitch.isChecked = musicEnabled
        musicSwitch.setOnCheckedChangeListener { _, isChecked ->
            (requireActivity() as? MainActivity)?.setMusicEnabled(isChecked)
            Toast.makeText(context, "Music preference saved.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupGlitchSwitch() {
        val sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val glitchEnabled = sharedPreferences.getBoolean("glitch_enabled", true)
        glitchSwitch.isChecked = glitchEnabled
        glitchSwitch.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit { putBoolean("glitch_enabled", isChecked) }
            (requireActivity() as? MainActivity)?.setGlitchEnabled(isChecked)
            Toast.makeText(context, "Glitch effect preference saved.", Toast.LENGTH_SHORT).show()
        }
    }
    
    private fun setupPrivacySwitches() {
        val privacyRef = userRef?.child("privacySettings")
        privacyListener = privacyRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val showBadges = snapshot.child("showBadges").getValue(Boolean::class.java) ?: true
                val showBioXp = snapshot.child("showBioAndExperience").getValue(Boolean::class.java) ?: true
                val showPlanetary = snapshot.child("showPlanetarySystem").getValue(Boolean::class.java) ?: true

                badgesSwitch.setOnCheckedChangeListener(null)
                bioXpSwitch.setOnCheckedChangeListener(null)
                planetarySwitch.setOnCheckedChangeListener(null)

                badgesSwitch.isChecked = showBadges
                bioXpSwitch.isChecked = showBioXp
                planetarySwitch.isChecked = showPlanetary

                badgesSwitch.setOnCheckedChangeListener { _, isChecked -> updatePrivacySetting("showBadges", isChecked) }
                bioXpSwitch.setOnCheckedChangeListener { _, isChecked -> updatePrivacySetting("showBioAndExperience", isChecked) }
                planetarySwitch.setOnCheckedChangeListener { _, isChecked -> updatePrivacySetting("showPlanetarySystem", isChecked) }
            }
            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })
    }

    private fun updatePrivacySetting(key: String, value: Boolean) {
        userRef?.child("privacySettings")?.child(key)?.setValue(value)
            ?.addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "Privacy setting saved.", Toast.LENGTH_SHORT).show()
            }
            ?.addOnFailureListener {
                if (isAdded) Toast.makeText(context, "Failed to update privacy setting.", Toast.LENGTH_SHORT).show()
            }
    }

    private fun startTitleLoadingAnimation() { 
        titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }
        titleLoadingRunnable = object : Runnable { 
            private var dotCount = 0
            override fun run() { 
                dotCount = (dotCount + 1) % 4
                val dots = when (dotCount) { 
                    1 -> "."
                    2 -> ".."
                    3 -> "..."
                    else -> "" 
                }
                titleTextView.text = "Settings$dots"
                titleLoadingHandler.postDelayed(this, 500) 
            } 
        }
        titleLoadingHandler.post(titleLoadingRunnable!!) 
    }
    
    private fun stopTitleLoadingAnimation() { 
        titleLoadingHandler.removeCallbacksAndMessages(null)
        titleTextView.text = "Settings" 
    }
    
    private fun listenForUserStatus() { startTitleLoadingAnimation(); contentScrollView.visibility = View.GONE; emailDataTextView.text = auth.currentUser?.email ?: "N/A"; userDataListener = userRef?.addValueEventListener(object : ValueEventListener { override fun onDataChange(snapshot: DataSnapshot) { if (!isAdded) return; val maxLength = 35; val nickname = snapshot.child("nickname").getValue(String::class.java) ?: "N/A"; currentUserNickname = nickname; nicknameDataTextView.text = if (nickname.length > maxLength) nickname.take(maxLength) + "..." else nickname; currentUserAvatarSeed = snapshot.child("avatarSeed").getValue(String::class.java) ?: auth.currentUser?.uid ?: ""; if (isAdded && currentUserAvatarSeed.isNotEmpty()) { viewLifecycleOwner.lifecycleScope.launch { val avatar = AlienAvatarGenerator.generate(currentUserAvatarSeed, 256, 256); avatarImageView.setImageBitmap(avatar) } }; val bio = snapshot.child("bio").getValue(String::class.java) ?: "N/A"; currentUserBio = bio; bioDataTextView.text = if (bio.length > maxLength) bio.take(maxLength) + "..." else bio; val points = snapshot.child("experiencePoints").getValue(Long::class.java) ?: 0L; pointsDataTextView.text = points.toString(); 
        val notificationsEnabled = snapshot.child("notificationsEnabled").getValue(Boolean::class.java) ?: true; notificationsSwitch.setOnCheckedChangeListener(null); notificationsSwitch.isChecked = notificationsEnabled; notificationsSwitch.setOnCheckedChangeListener { _, isChecked -> updateNotificationPreference(isChecked) }; 
        stopTitleLoadingAnimation(); contentScrollView.visibility = View.VISIBLE } override fun onCancelled(error: DatabaseError) { if (!isAdded) return; stopTitleLoadingAnimation(); Toast.makeText(context, "Failed to load user data.", Toast.LENGTH_SHORT).show() } }) }
    
    private fun showChangePasswordDialog() { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_change_password); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val oldPasswordEditText = dialog.findViewById<EditText>(R.id.et_old_password); val newPasswordEditText = dialog.findViewById<EditText>(R.id.et_new_password); val confirmPasswordEditText = dialog.findViewById<EditText>(R.id.et_confirm_password); val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button); negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { val oldPassword = oldPasswordEditText.text.toString(); val newPassword = newPasswordEditText.text.toString(); val confirmPassword = confirmPasswordEditText.text.toString(); if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) { Toast.makeText(context, "Please fill in all fields.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; if (newPassword != confirmPassword) { Toast.makeText(context, "Passwords do not match.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; if (newPassword.length < 6) { Toast.makeText(context, "Password must be at least 6 characters long.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; val user = auth.currentUser; val email = user?.email; if (user == null || email == null) { Toast.makeText(context, "User not found. Please re-login.", Toast.LENGTH_SHORT).show(); logout(); return@setOnClickListener }; val credential = EmailAuthProvider.getCredential(email, oldPassword); user.reauthenticate(credential).addOnCompleteListener { reauthTask -> if (reauthTask.isSuccessful) { user.updatePassword(newPassword).addOnCompleteListener { task -> if (task.isSuccessful) { Toast.makeText(context, "Password updated successfully.", Toast.LENGTH_SHORT).show(); dialog.dismiss() } else { Toast.makeText(context, "Failed to update password: ${task.exception?.message}", Toast.LENGTH_LONG).show() } } } else { Toast.makeText(context, "Authentication failed. Wrong password?", Toast.LENGTH_LONG).show() } } }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    
    private fun logout() {
    if (isAdded && view != null) {
        try {
            auth.signOut()
            val navOptions = NavOptions.Builder()
                .setPopUpTo(R.id.nav_graph, true)
                .build()
            findNavController().navigate(R.id.loginFragment, null, navOptions)
        } catch (e: IllegalStateException) {
            Log.e("SettingsFragment", "Failed to navigate to login screen after logout.", e)
        }
    } else {
        auth.signOut()
    }
}
    private fun showEditNicknameDialog() { 
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom_input)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title)
        val inputEditText = dialog.findViewById<EditText>(R.id.dialog_input)
        val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button)
        
        titleTextView.text = "Edit Nickname"
        inputEditText.setText(currentUserNickname)
        negativeButton.text = "Cancel"
        positiveButton.text = "Watch Ad & Save"
        
        negativeButton.setOnClickListener { dialog.dismiss() }
        
        positiveButton.setOnClickListener { 
            val newNickname = inputEditText.text.toString().trim()
            if (isValidNickname(newNickname)) { 
                if (AdManager.isAdReady()) {
                    AdManager.showRewardedAd(requireActivity(), { type, amount ->
                        // On Ad Watched
                        val updates = mapOf(
                            "nickname" to newNickname,
                            "lastNicknameChangeTimestamp" to System.currentTimeMillis()
                        )
                        
                        userRef?.updateChildren(updates)?.addOnSuccessListener { 
                            if (!isAdded) return@addOnSuccessListener
                            Toast.makeText(context, "Nickname updated!", Toast.LENGTH_SHORT).show() 
                            dialog.dismiss() 
                        }?.addOnFailureListener { 
                            if (!isAdded) return@addOnFailureListener
                            Toast.makeText(context, "Failed to update nickname.", Toast.LENGTH_SHORT).show() 
                        }
                    }, {
                         // On Ad Closed without reward or error
                         // No action usually, but you might want to reload ad
                    })
                } else {
                    Toast.makeText(context, "Ad not ready yet. Please try again in a few seconds.", Toast.LENGTH_SHORT).show()
                    AdManager.loadRewardedAd(requireContext())
                }
            } 
        }
        
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) 
    }
    
    private fun isValidNickname(nickname: String): Boolean { if (nickname.length < 3) { Toast.makeText(context, "Nickname must be at least 3 characters long.", Toast.LENGTH_SHORT).show(); return false }; if (nickname.length > 20) { Toast.makeText(context, "Nickname cannot exceed 20 characters.", Toast.LENGTH_SHORT).show(); return false }; val pattern = "[a-zA-Z0-9_.-]+"; if (!nickname.matches(Regex(pattern))) { Toast.makeText(context, "Nickname can only contain letters, numbers, and the characters _, -, .", Toast.LENGTH_LONG).show(); return false }; return true }
    private fun showEditBioDialog() { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_custom_input); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title); val inputEditText = dialog.findViewById<EditText>(R.id.dialog_input); val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button); titleTextView.text = "Edit Bio"; inputEditText.setText(currentUserBio); negativeButton.text = "Cancel"; positiveButton.text = "Save"; negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { val newBio = inputEditText.text.toString().trim(); if (newBio.isNotEmpty()) { userRef?.child("bio")?.setValue(newBio)?.addOnSuccessListener { if (!isAdded) return@addOnSuccessListener; Toast.makeText(context, "Bio updated.", Toast.LENGTH_SHORT).show(); dialog.dismiss() }?.addOnFailureListener { if (!isAdded) return@addOnFailureListener; Toast.makeText(context, "Failed to update bio.", Toast.LENGTH_SHORT).show() } } else { Toast.makeText(context, "Bio cannot be empty.", Toast.LENGTH_SHORT).show() } }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun updateNotificationPreference(isChecked: Boolean) { userRef?.child("notificationsEnabled")?.setValue(isChecked)?.addOnSuccessListener { if (!isAdded) return@addOnSuccessListener; Toast.makeText(context, "Notification preference saved.", Toast.LENGTH_SHORT).show() }?.addOnFailureListener { if (!isAdded) return@addOnFailureListener; Toast.makeText(context, "Failed to save preference.", Toast.LENGTH_SHORT).show(); notificationsSwitch.isChecked = !isChecked } }
    private fun showLogoutConfirmationDialog() { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_custom); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title); val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message); val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button); val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle); val galaxyStatsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout); titleTextView.text = "Logout"; messageTextView.text = "Are you sure you want to logout?"; negativeButton.text = "Cancel"; positiveButton.text = "Logout"; subtitleTextView.visibility = View.GONE; galaxyStatsLayout.visibility = View.GONE; messageTextView.gravity = Gravity.START; titleTextView.gravity = Gravity.START; negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { logout(); dialog.dismiss() }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun showDeleteConfirmationDialog() { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_custom); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title); val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message); val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button); val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle); val galaxyStatsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout); titleTextView.text = "Delete Account"; messageTextView.text = "Are you sure you want to permanently delete your account? All your data will be lost. This action cannot be undone."; negativeButton.text = "Cancel"; positiveButton.text = "Delete"; subtitleTextView.visibility = View.GONE; galaxyStatsLayout.visibility = View.GONE; messageTextView.gravity = Gravity.START; titleTextView.gravity = Gravity.START; negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { dialog.dismiss(); showReauthenticationForDeleteDialog() }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun showReauthenticationForDeleteDialog() { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_custom_input); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title); val inputEditText = dialog.findViewById<EditText>(R.id.dialog_input); val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button); titleTextView.text = "Confirm Deletion"; inputEditText.hint = "Enter your password"; inputEditText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; negativeButton.text = "Cancel"; positiveButton.text = "Confirm"; negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { val password = inputEditText.text.toString(); if (password.isEmpty()) { Toast.makeText(context, "Password is required.", Toast.LENGTH_SHORT).show(); return@setOnClickListener }; val user = auth.currentUser; val email = user?.email; if (user == null || email == null) { if (!isAdded) return@setOnClickListener; Toast.makeText(context, "User not found. Please re-login.", Toast.LENGTH_SHORT).show(); logout(); return@setOnClickListener }; val credential = EmailAuthProvider.getCredential(email, password); user.reauthenticate(credential).addOnCompleteListener { reauthTask -> if (!isAdded) return@addOnCompleteListener; if (reauthTask.isSuccessful) { deleteUserAccount(); dialog.dismiss() } else { Toast.makeText(context, "Authentication failed. Wrong password?", Toast.LENGTH_LONG).show() } } }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun clearAppCache() { try { val cache = requireContext().cacheDir; val appDir = cache.parentFile; if (appDir.isDirectory) { for (child in appDir.listFiles() ?: arrayOf()) { if (child.name != "lib") { deleteDir(child) } } } } catch (e: Exception) { Log.e("SettingsFragment", "Failed to clear cache", e) } }
    private fun deleteDir(dir: java.io.File?): Boolean { if (dir != null && dir.isDirectory) { val children = dir.list(); if (children != null) { for (i in children.indices) { val success = deleteDir(java.io.File(dir, children[i])); if (!success) { return false } } } }; return dir?.delete() ?: false }
    
    private fun deleteUserAccount() {
    startTitleLoadingAnimation()
    contentScrollView.visibility = View.GONE
    val user = auth.currentUser
    val userId = user?.uid
    if (user == null || userId == null) {
        if (!isAdded) return
        Toast.makeText(context, "User not found. Please re-login.", Toast.LENGTH_SHORT).show()
        logout()
        return
    }

    val rootRef = database.reference
    val storage = FirebaseStorage.getInstance()
    val allFileUrls = mutableListOf<String>()
    val pathsToUpdate = mutableMapOf<String, Any?>()

    // 1. DELETE GALAXY PRESENCE (NEW)
    // Scan all galaxies to remove the user from presence lists
    val galaxyPresenceRef = rootRef.child("galaxy_presence")
    val deletePresenceTask = galaxyPresenceRef.get().continueWithTask { presenceTask ->
        if (!isAdded) return@continueWithTask Tasks.forCanceled()
        if (presenceTask.isSuccessful) {
            presenceTask.result.children.forEach { galaxySnapshot ->
                if (galaxySnapshot.hasChild(userId)) {
                    pathsToUpdate["/galaxy_presence/${galaxySnapshot.key}/$userId"] = null
                }
            }
        }
        // Proceed even if failed
        Tasks.forResult(null)
    }

    // 2. Task to delete all public broadcasts from the user
    val publicBroadcastsRef = rootRef.child("public_broadcasts")
    val deletePublicBroadcastsTask = publicBroadcastsRef.get().continueWithTask { galaxiesTask ->
        if (!isAdded) return@continueWithTask Tasks.forCanceled()
        if (!galaxiesTask.isSuccessful) return@continueWithTask Tasks.forException(galaxiesTask.exception!!)

        val queryTasks = mutableListOf<Task<DataSnapshot>>()
        val affectedGalaxies = mutableSetOf<String>()

        galaxiesTask.result.children.forEach { galaxySnapshot ->
            val galaxyName = galaxySnapshot.key
            if (galaxyName != null) {
                affectedGalaxies.add(galaxyName)
                val query = publicBroadcastsRef.child(galaxyName).orderByChild("senderId").equalTo(userId)
                queryTasks.add(query.get())
            }
        }
        
        Tasks.whenAllSuccess<DataSnapshot>(queryTasks).continueWithTask { allMessagesTask ->
            if (!isAdded) return@continueWithTask Tasks.forCanceled()
            allMessagesTask.result.forEach { messagesSnapshot ->
                messagesSnapshot.children.forEach { messageSnapshot ->
                    val msg = messageSnapshot.getValue(ChatMessage::class.java)
                    msg?.gifUrl?.let { allFileUrls.add(it) }
                    msg?.voiceMessageUrl?.let { allFileUrls.add(it) }
                    msg?.imageUrl?.let { allFileUrls.add(it) }
                    messageSnapshot.ref.path.toString().let { pathsToUpdate[it] = null }
                }
            }
            // After deleting messages, we don't delete the galaxy node itself if empty to avoid concurrency issues, just messages.
            Tasks.forResult(null)
        }
    }

    // 3. Task to delete all conversations from the user
    val deleteConversationsTask = rootRef.child("users").child(userId).get().continueWithTask { userSnapshotTask ->
        if (!isAdded) return@continueWithTask Tasks.forCanceled()
        if (!userSnapshotTask.isSuccessful) return@continueWithTask Tasks.forException(userSnapshotTask.exception!!)

        val userSnapshot = userSnapshotTask.result
        val conversationTasks = mutableListOf<Task<DataSnapshot>>()
        userSnapshot.child("conversations").children.forEach { convoSnapshot ->
            val conversationId = convoSnapshot.key
            if (conversationId != null) {
                // Delete the whole conversation node (if allowed)
                pathsToUpdate["/conversations/$conversationId"] = null
                
                // Get messages to find files to delete
                conversationTasks.add(rootRef.child("conversations/$conversationId/messages").get())
            }
        }
        
        Tasks.whenAllSuccess<DataSnapshot>(conversationTasks).addOnSuccessListener { results ->
            if (!isAdded) return@addOnSuccessListener
            results.forEach { snapshot ->
                snapshot.children.forEach { messageSnap ->
                    val msg = messageSnap.getValue(ChatMessage::class.java)
                    msg?.gifUrl?.let { allFileUrls.add(it) }
                    msg?.voiceMessageUrl?.let { allFileUrls.add(it) }
                    msg?.imageUrl?.let { allFileUrls.add(it) }
                }
            }
        }
    }

    // Combine all tasks
    Tasks.whenAll(deletePresenceTask, deletePublicBroadcastsTask, deleteConversationsTask).continueWithTask {
        if (!isAdded) return@continueWithTask Tasks.forCanceled()

        // Delete all collected files from storage
        val deleteFileTasks = allFileUrls.map { url -> 
            try { storage.getReferenceFromUrl(url).delete() } catch (e: Exception) { Tasks.forResult(null) }
        }
        
        Tasks.whenAll(deleteFileTasks).addOnCompleteListener {
            // Proceed to DB deletion regardless of storage errors
        }

        // Add user root data to deletion map
        pathsToUpdate["/users/$userId"] = null
        pathsToUpdate["/invitations/$userId"] = null

        // Perform the final multi-path delete
        rootRef.updateChildren(pathsToUpdate)
    }.addOnCompleteListener { dbTask ->
        if (!isAdded) return@addOnCompleteListener
        
        // Even if DB update fails partially (e.g. permission denied for other user's inbox), 
        // we MUST delete the Auth account to finalize.
        
        user.delete().addOnCompleteListener { authTask ->
            if (!isAdded) return@addOnCompleteListener
            if (authTask.isSuccessful) {
                clearAppCache()
                Toast.makeText(context, "Account deleted successfully.", Toast.LENGTH_LONG).show()
                logout()
            } else {
                Log.e("SettingsFragment", "Failed to delete user from Firebase Auth.", authTask.exception)
                Toast.makeText(context, "Error deleting account. Please try again.", Toast.LENGTH_LONG).show()
                logout()
            }
        }
    }
}
    
    private fun showBlockedUsersDialog() {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_blocked_users)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val recyclerView = dialog.findViewById<RecyclerView>(R.id.rv_blocked_users_list)
        val noUsersTextView = dialog.findViewById<TextView>(R.id.tv_no_blocked_users)
        val closeButton = dialog.findViewById<Button>(R.id.dialog_close_button)

        recyclerView.layoutManager = LinearLayoutManager(context)
        
        closeButton.setOnClickListener { dialog.dismiss() }

        val currentUserId = auth.currentUser?.uid ?: return
        val blockedRef = database.reference.child("users").child(currentUserId).child("blockedUsers")

        blockedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val blockedUids = snapshot.children.mapNotNull { it.key }
                
                if (blockedUids.isEmpty()) {
                    dialog.dismiss()
                } else {
                    noUsersTextView.visibility = View.GONE
                    recyclerView.visibility = View.VISIBLE
                    loadBlockedUsersDetails(blockedUids, recyclerView, dialog)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                if (isAdded) Toast.makeText(context, "Failed to load blocked users.", Toast.LENGTH_SHORT).show()
            }
        })

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun loadBlockedUsersDetails(uids: List<String>, recyclerView: RecyclerView, dialog: Dialog) {
        val usersList = mutableListOf<BlockedUserItem>()
        
        val tasks = uids.map { uid ->
            val userRef = database.reference.child("users").child(uid)
            val t1 = userRef.child("nickname").get()
            val t2 = userRef.child("avatarSeed").get()
            
            Tasks.whenAllSuccess<DataSnapshot>(t1, t2).continueWith { task ->
                val results = task.result
                val nickname = results[0].getValue(String::class.java) ?: "Unknown"
                val avatarSeed = results[1].getValue(String::class.java) ?: uid
                BlockedUserItem(uid, nickname, avatarSeed)
            }
        }
        
        Tasks.whenAllSuccess<BlockedUserItem>(tasks).addOnSuccessListener { items ->
            if (!isAdded) return@addOnSuccessListener
            usersList.addAll(items)
            val adapter = BlockedUsersAdapter(usersList) { user ->
                showUnblockConfirmationDialog(user)
            }
            recyclerView.adapter = adapter
        }.addOnFailureListener {
            // Even if some fail, we might want to show partial results?
            // Tasks.whenAllSuccess fails if *any* task fails.
            // If permissions deny reading, the individual task fails.
            // So we should handle individual failures to at least show the UID.
            // However, with the current structure, if nickname reading is allowed, it should not fail.
            // If it does, we probably can't do much better than showing the toast.
            if (!isAdded) return@addOnFailureListener
            Toast.makeText(context, "Error loading some user details.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showUnblockConfirmationDialog(user: BlockedUserItem) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        
        val title = dialog.findViewById<TextView>(R.id.dialog_title)
        val message = dialog.findViewById<TextView>(R.id.dialog_message)
        val negativeBtn = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveBtn = dialog.findViewById<Button>(R.id.dialog_positive_button)
        val subtitle = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val statsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout)

        title.text = "Unblock User"
        message.text = "Are you sure you want to unblock ${user.nickname}?"
        negativeBtn.text = "Cancel"
        positiveBtn.text = "Unblock"
        subtitle.visibility = View.GONE
        statsLayout.visibility = View.GONE

        negativeBtn.setOnClickListener { dialog.dismiss() }
        positiveBtn.setOnClickListener {
            unblockUser(user.uid)
            dialog.dismiss()
        }
        
        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun unblockUser(uid: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        database.reference.child("users").child(currentUserId).child("blockedUsers").child(uid).removeValue()
            .addOnSuccessListener {
                if (isAdded) Toast.makeText(context, "User unblocked.", Toast.LENGTH_SHORT).show()
                // The addValueEventListener in showBlockedUsersDialog will automatically update the list
            }
            .addOnFailureListener {
                if (isAdded) Toast.makeText(context, "Failed to unblock user.", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() { super.onDestroyView(); userDataListener?.let { userRef?.removeEventListener(it) }; privacyListener?.let { userRef?.child("privacySettings")?.removeEventListener(it) }; blockedUsersListener?.let { userRef?.child("blockedUsers")?.removeEventListener(it) }; titleLoadingHandler.removeCallbacksAndMessages(null) }
}