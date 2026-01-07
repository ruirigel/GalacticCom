package com.rmrbranco.galacticcom

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.widget.TextViewCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

class GalaxyDirectoryFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var galaxiesRecyclerView: RecyclerView
    private lateinit var galaxyMapView: GalaxyMapView
    private lateinit var titleTextView: TextView
    private lateinit var galaxyAdapter: GalaxyAdapter

    private var isMapView = true

    private val currentUserId: String by lazy { auth.currentUser!!.uid }
    private var travelCompletionTimestamp: Long? = null
    private var currentUserGalaxy: String? = null

    private var userDataListener: ValueEventListener? = null
    private lateinit var userRef: DatabaseReference

    private var cachedGalaxies: List<GalaxyInfo> = emptyList()
    private val handler = Handler(Looper.getMainLooper())

    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        userRef = database.reference.child("users").child(currentUserId)

        val sharedPreferences = requireActivity().getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        isMapView = sharedPreferences.getBoolean("default_view_is_visual", true)

        return inflater.inflate(R.layout.fragment_galaxy_directory, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        galaxiesRecyclerView = view.findViewById(R.id.rv_galaxies)
        galaxyMapView = view.findViewById(R.id.galaxy_map_view)
        titleTextView = view.findViewById(R.id.cosmos_title)

        setupRecyclerView()
        setupMapView()
        
        loadGalaxyData()
        listenForUserStatus()
    }

    fun toggleViewMode() {
        isMapView = !isMapView
        toggleViews()
    }
    
    private fun checkIntergalacticTravelLimitAndProceed(galaxy: GalaxyInfo) {
        val actionLogsRef = userRef.child("actionLogs")
        actionLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return

                val lastTravelTimestamp = snapshot.child("lastIntergalacticTravelTimestamp").getValue(Long::class.java) ?: 0L
                var travelsThisWeek = snapshot.child("intergalacticTravelCountThisWeek").getValue(Int::class.java) ?: 0

                val today = Calendar.getInstance()
                val lastTravelWeek = Calendar.getInstance().apply { timeInMillis = lastTravelTimestamp }

                if (today.get(Calendar.WEEK_OF_YEAR) != lastTravelWeek.get(Calendar.WEEK_OF_YEAR) ||
                    today.get(Calendar.YEAR) != lastTravelWeek.get(Calendar.YEAR)) {
                    travelsThisWeek = 0
                }

                val weeklyLimit = SettingsManager.getWeeklyIntergalacticTravelLimit()

                if (travelsThisWeek >= weeklyLimit) {
                    Toast.makeText(context, getString(R.string.weekly_travel_limit_message), Toast.LENGTH_SHORT).show()
                } else {
                    showTravelConfirmationDialog(galaxy, travelsThisWeek, actionLogsRef)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                if (isAdded) {
                    Toast.makeText(context, "Failed to verify travel limit. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun showTravelConfirmationDialog(galaxy: GalaxyInfo, currentCount: Int, logsRef: DatabaseReference) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title)
        val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle)
        val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message)
        val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button)
        val statsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout)

        statsLayout.visibility = View.GONE
        titleTextView.visibility = View.GONE
        subtitleTextView.text = getString(R.string.confirm_travel_title)
        
        val travelTimeMinutes = SettingsManager.getIntergalacticTravelTimeMinutes()
        messageTextView.text = getString(R.string.travel_confirmation_message, travelTimeMinutes)

        val now = System.currentTimeMillis()
        if (travelCompletionTimestamp != null && now < travelCompletionTimestamp!!) {
            val remaining = travelCompletionTimestamp!! - now
            val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
            Toast.makeText(context, "You are already traveling! Wait $seconds seconds.", Toast.LENGTH_LONG).show()
            positiveButton.isEnabled = false
            positiveButton.alpha = 0.5f
            positiveButton.text = "Traveling..."
        }

        negativeButton.text = getString(R.string.cancel_button_text)
        
        if (positiveButton.isEnabled) {
             positiveButton.text = getString(R.string.travel_button_text)
        }

        negativeButton.setOnClickListener { dialog.dismiss() }

        positiveButton.setOnClickListener {
            initiateIntergalacticTravel(galaxy, currentCount, logsRef)
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun initiateIntergalacticTravel(galaxy: GalaxyInfo, currentCount: Int, logsRef: DatabaseReference) {
        val travelDurationMs = TimeUnit.MINUTES.toMillis(SettingsManager.getIntergalacticTravelTimeMinutes())
        val completionTimestamp = System.currentTimeMillis() + travelDurationMs
        
        val updates = mapOf(
            "travelCompletionTimestamp" to completionTimestamp,
            "travelType" to "INTERGALACTIC",
            "targetGalaxy" to galaxy.name,
            "travelBoosted" to false
        )

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                val logUpdates = mapOf(
                    "lastIntergalacticTravelTimestamp" to ServerValue.TIMESTAMP,
                    "intergalacticTravelCountThisWeek" to (currentCount + 1)
                )
                logsRef.updateChildren(logUpdates)

                Toast.makeText(context, getString(R.string.journey_initiated_message, galaxy.name), Toast.LENGTH_LONG).show()
                
                val args = bundleOf(
                    "completion_timestamp" to completionTimestamp,
                    "currentUserGalaxy" to currentUserGalaxy, 
                    "targetGalaxy" to galaxy.name
                )
                findNavController().navigate(R.id.action_galaxyDirectory_to_travelInProgress, args)
            }
            .addOnFailureListener {
                Toast.makeText(context, getString(R.string.travel_failed_message), Toast.LENGTH_SHORT).show()
            }
    }
    
    // ... Other functions remain the same
    private fun toggleViews() { if (isMapView) { galaxiesRecyclerView.visibility = View.GONE; galaxyMapView.visibility = View.VISIBLE } else { galaxiesRecyclerView.visibility = View.VISIBLE; galaxyMapView.visibility = View.GONE } }
    private fun setupRecyclerView() { galaxyAdapter = GalaxyAdapter(onTravelClick = { galaxy -> checkIntergalacticTravelLimitAndProceed(galaxy) }, onItemClick = { galaxy -> showGalaxyInfoDialog(galaxy) }); galaxiesRecyclerView.layoutManager = LinearLayoutManager(context); galaxiesRecyclerView.adapter = galaxyAdapter }
    private fun setupMapView() { galaxyMapView.onGalaxyClickListener = { galaxy -> showGalaxyInfoDialog(galaxy) } }
    private fun startTitleLoadingAnimation() { titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }; titleLoadingRunnable = object : Runnable { private var dotCount = 0; override fun run() { dotCount = (dotCount + 1) % 4; val dots = when (dotCount) { 1 -> "."; 2 -> ".."; 3 -> "..."; else -> "" }; 
                if (isAdded) {
                    titleTextView.text = getString(R.string.cosmos_title) + dots
                }
                titleLoadingHandler.postDelayed(this, 500) 
            } }; titleLoadingHandler.post(titleLoadingRunnable!!) }
    private fun stopTitleLoadingAnimation() { titleLoadingRunnable?.let { titleLoadingHandler.removeCallbacks(it) }; titleLoadingRunnable = null; if (isAdded) titleTextView.text = getString(R.string.cosmos_title) }
    private fun showGalaxyInfoDialog(galaxy: GalaxyInfo) { val dialog = Dialog(requireContext()); dialog.setContentView(R.layout.dialog_custom); dialog.window?.setBackgroundDrawableResource(android.R.color.transparent); val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title); val subtitleTextView = dialog.findViewById<TextView>(R.id.dialog_subtitle); val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message); val negativeButton = dialog.findViewById<MaterialButton>(R.id.dialog_negative_button); val positiveButton = dialog.findViewById<MaterialButton>(R.id.dialog_positive_button); val buttonsLayout = dialog.findViewById<LinearLayout>(R.id.dialog_buttons); val statsLayout = dialog.findViewById<LinearLayout>(R.id.galaxy_stats_layout); titleTextView.text = galaxy.name; messageTextView.gravity = Gravity.START; titleTextView.gravity = Gravity.START; fun setSpannableText(textView: TextView, label: String, value: String) { val spannable = SpannableString(label + value); val cyanColor = ContextCompat.getColor(textView.context, R.color.neon_cyan); spannable.setSpan(ForegroundColorSpan(cyanColor), 0, label.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE); textView.text = spannable }; setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_count), getString(R.string.inhabitants_label), galaxy.inhabitants.toString()); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_message_count), getString(R.string.messages_label), galaxy.messageCount.toString()); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_morphology), getString(R.string.morphology_label), galaxy.morphology); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_age), getString(R.string.age_label), galaxy.age); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_dimension), getString(R.string.dimension_label), galaxy.dimension); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_composition), getString(R.string.composition_label), galaxy.composition); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_habitable_planets), getString(R.string.habitable_planets_label), galaxy.habitablePlanets.toString()); setSpannableText(dialog.findViewById(R.id.dialog_galaxy_item_non_habitable_planets), getString(R.string.non_habitable_planets_label), galaxy.nonHabitablePlanets.toString()); if (galaxy.isCurrentGalaxy) { subtitleTextView.text = getString(R.string.you_are_here); subtitleTextView.gravity = Gravity.CENTER; TextViewCompat.setTextAppearance(subtitleTextView, R.style.Widget_App_Button); subtitleTextView.isAllCaps = true; messageTextView.visibility = View.GONE; buttonsLayout.visibility = View.GONE } else { subtitleTextView.visibility = View.GONE; messageTextView.visibility = View.GONE; negativeButton.text = getString(R.string.close); positiveButton.text = getString(R.string.travel_to_this_galaxy); val textButtonStyle = com.google.android.material.R.style.Widget_MaterialComponents_Button_TextButton; negativeButton.setTextAppearance(textButtonStyle); positiveButton.setTextAppearance(textButtonStyle); negativeButton.setOnClickListener { dialog.dismiss() }; positiveButton.setOnClickListener { dialog.dismiss(); checkIntergalacticTravelLimitAndProceed(galaxy) } }; dialog.show(); dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT) }
    private fun listenForUserStatus() { userDataListener = userRef.addValueEventListener(object : ValueEventListener { override fun onDataChange(snapshot: DataSnapshot) { travelCompletionTimestamp = snapshot.child("travelCompletionTimestamp").getValue(Long::class.java); currentUserGalaxy = snapshot.child("galaxy").getValue(String::class.java); val lastTravel = snapshot.child("lastTravelTimestamp").getValue(Long::class.java); updateViews(lastTravel, currentUserGalaxy); handler.removeCallbacksAndMessages(null); val now = System.currentTimeMillis(); if (travelCompletionTimestamp != null && now < travelCompletionTimestamp!!) { val delay = travelCompletionTimestamp!! - now; handler.postDelayed({ updateViews(lastTravel, currentUserGalaxy) }, delay) } } override fun onCancelled(error: DatabaseError) { if (isAdded) Toast.makeText(context, getString(R.string.user_status_error), Toast.LENGTH_SHORT).show() } }) }
    
    // UPDATED FUNCTION: ROBUST SELF-HEALING GALAXY LOADING
    private fun loadGalaxyData() {
        startTitleLoadingAnimation()
        galaxiesRecyclerView.visibility = View.GONE
        galaxyMapView.visibility = View.GONE

        // 1. First, fetch my own galaxy location reliably from my user profile
        userRef.child("galaxy").get().addOnSuccessListener { galaxySnapshot ->
            val myGalaxy = galaxySnapshot.getValue(String::class.java) ?: "Milky Way"

            // 2. Then fetch message stats
            database.reference.child("public_broadcasts").get().addOnSuccessListener { messagesSnapshot ->
                val messageCounts = mutableMapOf<String, Int>()
                messagesSnapshot.children.forEach { galaxyMessages ->
                    val galaxyName = galaxyMessages.key
                    if (galaxyName != null) {
                        messageCounts[galaxyName] = galaxyMessages.childrenCount.toInt()
                    }
                }

                // 3. Then fetch presence
                database.reference.child("galaxy_presence").get().addOnSuccessListener { presenceSnapshot ->
                    val galaxyCounts = mutableMapOf<String, Int>()
                    
                    presenceSnapshot.children.forEach { galaxySnapshot ->
                        val galaxyName = galaxySnapshot.key
                        val inhabitantsCount = galaxySnapshot.childrenCount.toInt()
                        if (galaxyName != null) {
                            galaxyCounts[galaxyName] = inhabitantsCount
                        }
                    }

                    // 4. SELF-HEALING: Ensure I am counted in my current galaxy
                    if (!galaxyCounts.containsKey(myGalaxy)) {
                        // My galaxy isn't even in the list -> Add it locally
                        galaxyCounts[myGalaxy] = 1
                        // Fix the DB so others can see it too
                        database.reference.child("galaxy_presence/$myGalaxy/$currentUserId").setValue(true)
                    } else {
                        // Galaxy exists, but check if I am listed inside it
                        val myGalaxyNode = presenceSnapshot.child(myGalaxy)
                        if (!myGalaxyNode.hasChild(currentUserId)) {
                             // I am missing from the count -> Add +1 locally
                             galaxyCounts[myGalaxy] = (galaxyCounts[myGalaxy] ?: 0) + 1
                             // Fix the DB
                             database.reference.child("galaxy_presence/$myGalaxy/$currentUserId").setValue(true)
                        }
                    }

                    cachedGalaxies = galaxyCounts.map {
                        GalaxyInfo(
                            name = it.key,
                            inhabitants = it.value,
                            messageCount = messageCounts[it.key.replace(" ", "_")] ?: 0,
                            morphology = CosmicNameGenerator.getMorphologyForGalaxy(it.key),
                            age = CosmicNameGenerator.getGalaxyAge(it.key),
                            dimension = CosmicNameGenerator.getGalaxyDimension(it.key),
                            composition = CosmicNameGenerator.getGalaxyComposition(it.key),
                            habitablePlanets = CosmicNameGenerator.getHabitablePlanets(it.key),
                            nonHabitablePlanets = CosmicNameGenerator.getNonHabitablePlanets(it.key)
                        )
                    }.sortedByDescending { it.inhabitants }

                    updateViews(null, myGalaxy)
                    stopTitleLoadingAnimation()
                    toggleViews()
                    
                }.addOnFailureListener {
                    stopTitleLoadingAnimation()
                    Toast.makeText(context, "Failed to load galaxy directory.", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                stopTitleLoadingAnimation()
            }
        }.addOnFailureListener {
            stopTitleLoadingAnimation()
        }
    }
    private fun updateViews(lastTravel: Long?, currentGalaxy: String?) { val isTraveling = travelCompletionTimestamp != null && System.currentTimeMillis() < travelCompletionTimestamp!!; val updatedList = cachedGalaxies.map { it.copy(isTraveling = isTraveling, cooldownTimestamp = lastTravel, isCurrentGalaxy = it.name == currentGalaxy) }; galaxyAdapter.submitList(updatedList); galaxyMapView.setGalaxies(updatedList) }
    override fun onDestroyView() { super.onDestroyView(); userDataListener?.let { userRef.removeEventListener(it) }; handler.removeCallbacksAndMessages(null); titleLoadingHandler.removeCallbacksAndMessages(null) }
}