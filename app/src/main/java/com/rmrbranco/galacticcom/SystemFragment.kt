package com.rmrbranco.galacticcom

import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.data.managers.AdManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import com.rmrbranco.galacticcom.data.model.UserInventory
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class SystemFragment : Fragment() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var userRef: DatabaseReference? = null

    // Views
    private lateinit var galaxyDataTextView: TextView
    private lateinit var starDataTextView: TextView
    private lateinit var planetDataTextView: TextView
    private lateinit var orbitDataTextView: TextView
    private lateinit var resourceNameTextView: TextView
    private lateinit var reservesDataTextView: TextView
    private lateinit var findNewSystemButton: Button
    private lateinit var contentScrollView: ScrollView
    private lateinit var titleTextView: TextView
    
    // Mining Views (Passive)
    private lateinit var creditsTextView: TextView
    private lateinit var miningStatusTextView: TextView
    private lateinit var accumulatedResourcesTextView: TextView
    // Collect Button Components - Changed type to View or CardView
    private lateinit var collectButtonContainer: View 
    private lateinit var collectButtonTextView: TextView
    
    private lateinit var resourcesListTextView: TextView
    private lateinit var stressProgressBar: ProgressBar
    private lateinit var stressPercentageTextView: TextView

    private var currentUserGalaxy: String = ""
    private var currentUserPlanet: String = ""
    private var userDataListener: ValueEventListener? = null
    private var inventoryListener: ValueEventListener? = null
    private var travelCompletionTimestamp: Long? = null
    private var currentInventory: UserInventory? = null

    private val handler = Handler(Looper.getMainLooper())
    private var buttonUpdateRunnable: Runnable? = null
    private var miningUpdateRunnable: Runnable? = null

    private val titleLoadingHandler = Handler(Looper.getMainLooper())
    private var titleLoadingRunnable: Runnable? = null
    
    // Mining Status Animation
    private val miningStatusLoadingHandler = Handler(Looper.getMainLooper())
    private var miningStatusLoadingRunnable: Runnable? = null
    private var currentMiningBaseText = ""
    
    // Mining Limits
    private val MAX_MINING_TIME_MS = 4 * 60 * 60 * 1000L // 4 hours

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        auth.currentUser?.uid?.let {
            userRef = database.reference.child("users").child(it)
        }
        return inflater.inflate(R.layout.fragment_system, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Bind views
        galaxyDataTextView = view.findViewById(R.id.tv_system_galaxy_data)
        starDataTextView = view.findViewById(R.id.tv_system_star_data)
        planetDataTextView = view.findViewById(R.id.tv_system_planet_data)
        orbitDataTextView = view.findViewById(R.id.tv_system_orbit_data)
        resourceNameTextView = view.findViewById(R.id.tv_system_resource_name)
        reservesDataTextView = view.findViewById(R.id.tv_system_reserves_data)
        findNewSystemButton = view.findViewById(R.id.btn_find_new_system)
        contentScrollView = view.findViewById(R.id.system_scroll_view)
        titleTextView = view.findViewById(R.id.system_title)
        
        // Bind Mining Views
        creditsTextView = view.findViewById(R.id.tv_credits)
        miningStatusTextView = view.findViewById(R.id.tv_mining_status)
        accumulatedResourcesTextView = view.findViewById(R.id.tv_accumulated_resources)
        
        // Changed to find Generic View to avoid casting issues with CardView/LinearLayout inside
        // Actually the ID is on the CardView now.
        collectButtonContainer = view.findViewById(R.id.btn_collect_resources_container)
        collectButtonTextView = view.findViewById(R.id.tv_collect_resources_text)
        
        resourcesListTextView = view.findViewById(R.id.tv_resources_list)
        stressProgressBar = view.findViewById(R.id.pb_mining_stress)
        stressPercentageTextView = view.findViewById(R.id.tv_stress_percentage)

        findNewSystemButton.setOnClickListener { checkPlanetaryTravelLimitAndProceed() }
        // OnClickListener is now set in updateAccumulatedResourcesUI based on state

        listenForUserStatus()
        listenForInventory()
        
        // Load ad
        AdManager.loadRewardedAd(requireContext())
    }

    private fun listenForInventory() {
        val inventoryRef = userRef?.child("inventory_data")
        inventoryListener = inventoryRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                currentInventory = snapshot.getValue(UserInventory::class.java)
                
                if (currentInventory == null) {
                    val newInventory = UserInventory()
                    newInventory.lastCollectionTimestamp = System.currentTimeMillis()
                    // Initialize miningStartTime
                    newInventory.miningStartTime = System.currentTimeMillis()
                    inventoryRef.setValue(newInventory)
                    currentInventory = newInventory
                } else {
                    var needsUpdate = false
                    if (currentInventory!!.lastCollectionTimestamp == 0L) {
                        currentInventory!!.lastCollectionTimestamp = System.currentTimeMillis()
                        needsUpdate = true
                    }
                    if (currentInventory!!.miningStartTime == 0L) {
                        // If missing, assume it started now or match collection
                        currentInventory!!.miningStartTime = currentInventory!!.lastCollectionTimestamp
                        needsUpdate = true
                    }
                    if (needsUpdate) {
                        inventoryRef.setValue(currentInventory)
                    }
                }
                
                if (currentInventory!!.planetTotalReserves == -1L && currentUserPlanet.isNotEmpty()) {
                     val initialReserves = generateInitialReserves(currentUserGalaxy, currentUserPlanet)
                     inventoryRef.child("planetTotalReserves").setValue(initialReserves)
                     currentInventory!!.planetTotalReserves = initialReserves
                }
                
                updateInventoryUI()
                startMiningLoop()
            }

            override fun onCancelled(error: DatabaseError) { }
        })
    }

    private fun generateInitialReserves(galaxy: String, planet: String): Long {
        val seed = (galaxy + planet).hashCode()
        val random = java.util.Random(seed.toLong())
        // Reserves between 20,000 and 100,000 units
        return (random.nextInt(80000) + 20000).toLong()
    }

    private fun startMiningLoop() {
        miningUpdateRunnable?.let { handler.removeCallbacks(it) }
        miningUpdateRunnable = object : Runnable {
            override fun run() {
                updateAccumulatedResourcesUI()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(miningUpdateRunnable!!)
    }

    private fun getAccumulatedAmount(): Int {
        val inventory = currentInventory ?: return 0
        val now = System.currentTimeMillis()
        val lastCollection = inventory.lastCollectionTimestamp
        
        if (lastCollection == 0L) return 0
        
        val elapsedMillis = max(0L, now - lastCollection)
        // Check OVERHEAT based on MiningStartTime
        val stressElapsed = now - inventory.miningStartTime
        
        // If system is already overheated (past 4 hours), we cap production at the moment it overheated.
        // But for simplicity, we cap based on elapsed time if we assume continuous mining.
        // Actually, 'elapsedMillis' is time since last collection. 
        // If stressElapsed > MAX, it means we stopped producing at (miningStartTime + MAX).
        
        var effectiveMillis = elapsedMillis
        
        if (stressElapsed > MAX_MINING_TIME_MS) {
            // Calculate when it overheated
            val overheatTime = inventory.miningStartTime + MAX_MINING_TIME_MS
            // If last collection was BEFORE overheat, we only produce until overheat time.
            if (lastCollection < overheatTime) {
                effectiveMillis = overheatTime - lastCollection
            } else {
                // If last collection was AFTER overheat (shouldn't happen if we force cool down, but logic safety)
                effectiveMillis = 0
            }
        }
        
        // Clamp to 0 just in case
        effectiveMillis = max(0L, effectiveMillis)
        
        // Rate: 1 ore per minute
        val ratePerMinute = 1.0
        val potentialAmount = (effectiveMillis / 60000.0) * ratePerMinute
        
        // Cap amount to remaining reserves
        val maxAvailable = inventory.planetTotalReserves
        // if reserves are -1 (new), treat as 0 for now until generated
        if (maxAvailable < 0) return 0
        
        return min(potentialAmount.toLong(), maxAvailable).toInt()
    }

    private fun determinePlanetResource(galaxy: String, planet: String): String {
        val seed = (galaxy + planet).hashCode()
        val random = java.util.Random(seed.toLong())
        
        val roll = random.nextInt(100)
        return when {
            roll < 60 -> "iron_ore"       
            roll < 85 -> "gold_dust"      
            roll < 98 -> "plasma_crystal" 
            else -> "dark_matter"         
        }
    }

    private fun updateAccumulatedResourcesUI() {
        if (!isAdded) return
        
        val resourceName = if (currentUserGalaxy.isNotEmpty() && currentUserPlanet.isNotEmpty()) {
            determinePlanetResource(currentUserGalaxy, currentUserPlanet)
        } else {
            "Scanning..."
        }
        
        val formattedName = resourceName.replace("_", " ").capitalize()
        resourceNameTextView.text = formattedName
        
        val amount = getAccumulatedAmount()
        val totalReserves = if (currentInventory?.planetTotalReserves == -1L) 0L else (currentInventory?.planetTotalReserves ?: 0L)
        val currentReserves = max(0L, totalReserves - amount)
        
        // STRESS CALCULATION (Based on Mining Start Time)
        val miningStart = currentInventory?.miningStartTime ?: System.currentTimeMillis()
        val elapsedStress = System.currentTimeMillis() - miningStart
        val stressPercentage = min(100.0, (elapsedStress.toDouble() / MAX_MINING_TIME_MS) * 100.0).toInt()
        val isOverheated = stressPercentage >= 100
        
        stressProgressBar.progress = stressPercentage
        stressPercentageTextView.text = "$stressPercentage%"
        
        // Color logic for stress
        if (isOverheated) {
            stressProgressBar.progressTintList = ContextCompat.getColorStateList(requireContext(), R.color.material_red_500)
            stressPercentageTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.material_red_500))
        } else {
            stressProgressBar.progressTintList = ContextCompat.getColorStateList(requireContext(), R.color.neon_cyan)
            stressPercentageTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.neon_cyan))
        }

        reservesDataTextView.text = "${formatNumber(currentReserves)} / ${formatNumber(totalReserves)}"
        accumulatedResourcesTextView.text = "Accumulated: ${formatNumber(amount.toLong())} $formattedName"
        
        if (currentReserves == 0L && amount == 0) {
             // DEPLETED STATE (Priority 1)
             stopMiningStatusAnimation()
             miningStatusTextView.text = "Planet Depleted. Recharge energy or travel."
             miningStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.material_red_500))
             
             collectButtonTextView.text = "Recharge Energy (Ad)"
             collectButtonContainer.isEnabled = true
             collectButtonContainer.alpha = 1.0f
             collectButtonContainer.setOnClickListener {
                 showRechargeAd()
             }
        } else if (isOverheated) {
             // OVERHEATED STATE (Priority 2)
             stopMiningStatusAnimation()
             miningStatusTextView.text = "SYSTEM OVERHEAT - MINING PAUSED"
             miningStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.material_red_500))
             
             collectButtonTextView.text = "Cool Down System (Ad)"
             collectButtonContainer.isEnabled = true
             collectButtonContainer.alpha = 1.0f
             collectButtonContainer.setOnClickListener { showCoolDownAd() }
        } else {
             // NORMAL STATE
             startMiningStatusAnimation("Extracting $formattedName")
             miningStatusTextView.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
             
             collectButtonTextView.text = "Collect Cargo"
             val hasCargo = amount > 0
             collectButtonContainer.isEnabled = hasCargo
             collectButtonContainer.alpha = if (hasCargo) 1.0f else 0.5f
             // Fix: Click Listener must be on the container (CardView) or the inner Layout (if clickable)
             // Since CardView is container, set on it.
             collectButtonContainer.setOnClickListener { if (hasCargo) collectResources(resetStress = false) }
        }
    }
    
    private fun startMiningStatusAnimation(baseText: String) {
        // If text changed, update it
        if (currentMiningBaseText != baseText) {
            currentMiningBaseText = baseText
        }
        
        // If already running, do nothing
        if (miningStatusLoadingRunnable != null) return
        
        miningStatusLoadingRunnable = object : Runnable {
            private var dotCount = 0
            override fun run() {
                dotCount = (dotCount + 1) % 4
                val dots = when (dotCount) {
                    1 -> "."
                    2 -> ".."
                    3 -> "..."
                    else -> ""
                }
                // Ensure fragment is attached
                if (isAdded) {
                    miningStatusTextView.text = "$currentMiningBaseText$dots"
                }
                miningStatusLoadingHandler.postDelayed(this, 500)
            }
        }
        miningStatusLoadingHandler.post(miningStatusLoadingRunnable!!)
    }
    
    private fun stopMiningStatusAnimation() {
        miningStatusLoadingRunnable?.let {
            miningStatusLoadingHandler.removeCallbacks(it)
        }
        miningStatusLoadingRunnable = null
    }

    private fun showRechargeAd() {
        if (AdManager.isAdReady()) {
            AdManager.showRewardedAd(requireActivity(), { type, amount -> 
                rechargeReserves()
            }, {
                // Ad closed
            })
        } else {
            Toast.makeText(context, "Ad not ready yet. Please try again in a moment.", Toast.LENGTH_SHORT).show()
            AdManager.loadRewardedAd(requireContext())
        }
    }
    
    private fun showCoolDownAd() {
        if (AdManager.isAdReady()) {
            AdManager.showRewardedAd(requireActivity(), { type, amount -> 
                // Reward: Cool down system (Reset Mining Start Time)
                collectResources(resetStress = true) 
                Toast.makeText(context, "System Cooled Down! Mining Resumed.", Toast.LENGTH_SHORT).show()
            }, {
                // Ad closed
            })
        } else {
            Toast.makeText(context, "Ad not ready yet. Please try again in a moment.", Toast.LENGTH_SHORT).show()
            AdManager.loadRewardedAd(requireContext())
        }
    }
    
    private fun rechargeReserves() {
        // Recharge reserves by 20,000
        val currentReserves = currentInventory?.planetTotalReserves ?: 0L
        val newReserves = if(currentReserves < 0) 20000L else currentReserves + 20000L
        
        userRef?.child("inventory_data")?.child("planetTotalReserves")?.setValue(newReserves)
            ?.addOnSuccessListener {
                Toast.makeText(context, "Energy Recharged! Reserves increased.", Toast.LENGTH_SHORT).show()
                // Local update
                currentInventory?.planetTotalReserves = newReserves
                updateAccumulatedResourcesUI()
            }
    }

    private fun updateInventoryUI() {
        val inventory = currentInventory ?: return
        creditsTextView.text = "${formatNumber(inventory.credits)} Credits"
        
        if (inventory.resources.isEmpty()) {
            resourcesListTextView.text = "Empty"
        } else {
            val resourcesText = inventory.resources.entries.joinToString("\n") { 
                val name = it.key.replace("_", " ").capitalize()
                "$name: ${formatNumber(it.value.toLong())}"
            }
            resourcesListTextView.text = resourcesText
        }
    }

    private fun collectResources(resetStress: Boolean = false) {
        val inventory = currentInventory ?: return
        val amount = getAccumulatedAmount()
        
        val now = System.currentTimeMillis()
        val resourceName = determinePlanetResource(currentUserGalaxy, currentUserPlanet)
        val currentAmount = inventory.getResourceAmount(resourceName)
        val newReserves = max(0L, inventory.planetTotalReserves - amount)
        
        val updates = mutableMapOf<String, Any>(
            "lastCollectionTimestamp" to now,
            "planetTotalReserves" to newReserves,
            "resources/$resourceName" to (currentAmount + amount)
        )
        
        if (resetStress) {
            updates["miningStartTime"] = now
        }
        
        userRef?.child("inventory_data")?.updateChildren(updates)?.addOnSuccessListener {
            if (amount > 0) {
                 Toast.makeText(context, "Collected ${formatNumber(amount.toLong())} ${resourceName.replace("_", " ").capitalize()}", Toast.LENGTH_SHORT).show()
            }
            // Optimistic Update
            currentInventory?.lastCollectionTimestamp = now
            currentInventory?.planetTotalReserves = newReserves
            currentInventory?.addResource(resourceName, amount)
            if (resetStress) {
                currentInventory?.miningStartTime = now
            }
            updateAccumulatedResourcesUI()
        }
    }
    
    private fun String.capitalize(): String {
        return this.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
    
    // Formatting Helper
    private fun formatNumber(value: Long): String {
        val v = value.toDouble()
        if (v < 1000) return "%.0f".format(v)
        val suffixes = arrayOf("", "k", "M", "B", "T")
        val exp = (Math.log10(v) / 3).toInt().coerceIn(0, suffixes.size - 1)
        return "%.1f%s".format(v / Math.pow(1000.0, exp.toDouble()), suffixes[exp])
    }

    private fun checkPlanetaryTravelLimitAndProceed() {
        val actionLogsRef = userRef?.child("actionLogs") ?: return
        actionLogsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lastTravelTimestamp = snapshot.child("lastPlanetaryTravelTimestamp").getValue(Long::class.java) ?: 0L
                var travelCountToday = snapshot.child("planetaryTravelCountToday").getValue(Int::class.java) ?: 0

                val today = Calendar.getInstance()
                val lastTravelDay = Calendar.getInstance().apply { timeInMillis = lastTravelTimestamp }

                if (today.get(Calendar.DAY_OF_YEAR) != lastTravelDay.get(Calendar.DAY_OF_YEAR) ||
                    today.get(Calendar.YEAR) != lastTravelDay.get(Calendar.YEAR)) {
                    travelCountToday = 0 // Reset counter for the new day
                }

                val dailyLimit = SettingsManager.getDailyPlanetaryTravelLimit()

                if (travelCountToday >= dailyLimit) {
                    Toast.makeText(context, "You have reached your daily limit for planetary travel.", Toast.LENGTH_SHORT).show()
                } else {
                    showFindNewSystemConfirmationDialog(travelCountToday, actionLogsRef)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(context, "Failed to verify travel limit. Please try again.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun showFindNewSystemConfirmationDialog(currentCount: Int, logsRef: DatabaseReference) {
        val dialog = Dialog(requireContext())
        dialog.setContentView(R.layout.dialog_custom)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val titleTextView = dialog.findViewById<TextView>(R.id.dialog_title)
        val messageTextView = dialog.findViewById<TextView>(R.id.dialog_message)
        val negativeButton = dialog.findViewById<Button>(R.id.dialog_negative_button)
        val positiveButton = dialog.findViewById<Button>(R.id.dialog_positive_button)
        
        dialog.findViewById<View>(R.id.galaxy_stats_layout).visibility = View.GONE
        dialog.findViewById<View>(R.id.dialog_subtitle).visibility = View.GONE

        titleTextView.text = "Find New System"
        messageTextView.text = "Are you sure you want to find a new system? This will count as one of your daily planetary travels."
        negativeButton.text = "Cancel"
        positiveButton.text = "Find"

        negativeButton.setOnClickListener { dialog.dismiss() }

        positiveButton.setOnClickListener {
            if (travelCompletionTimestamp != null && System.currentTimeMillis() < travelCompletionTimestamp!!) {
                Toast.makeText(context, "Cannot find a new system while traveling.", Toast.LENGTH_SHORT).show()
            } else {
                // Determine travel time from Settings
                val travelMinutes = SettingsManager.getPlanetaryTravelTimeMinutes()
                val travelDurationMs = travelMinutes * 60 * 1000L
                val completionTime = System.currentTimeMillis() + travelDurationMs
                
                // Update the counter before navigating
                val updates = mapOf(
                    "lastPlanetaryTravelTimestamp" to ServerValue.TIMESTAMP,
                    "planetaryTravelCountToday" to (currentCount + 1)
                )
                logsRef.updateChildren(updates)
                
                // Set Travel Completion Timestamp on User
                val userUpdates = mutableMapOf<String, Any>(
                    "travelCompletionTimestamp" to completionTime,
                    "travelType" to "INTERPLANETARY",
                    "travelBoosted" to false // Explicitly reset boost for new trip
                )
                userRef?.updateChildren(userUpdates)
                
                // RESET PLANET DATA ON TRAVEL
                // Reset timestamp AND planetTotalReserves to -1 (NEW) so it regenerates on arrival
                val inventoryUpdates = mapOf(
                    "lastCollectionTimestamp" to ServerValue.TIMESTAMP,
                    "planetTotalReserves" to -1L
                )
                userRef?.child("inventory_data")?.updateChildren(inventoryUpdates)
                
                val args = bundleOf(
                    "currentUserGalaxy" to currentUserGalaxy,
                    "completion_timestamp" to completionTime
                )
                // Use the correct action to go to TravelInProgressFragment
                findNavController().navigate(R.id.action_system_to_travelInProgress, args)
            }
            dialog.dismiss()
        }

        dialog.show()
        dialog.window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // ... (rest of the file remains similar)
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
                titleTextView.text = "System$dots"
                titleLoadingHandler.postDelayed(this, 500)
            }
        }
        titleLoadingHandler.post(titleLoadingRunnable!!)
    }

    private fun stopTitleLoadingAnimation() {
        titleLoadingHandler.removeCallbacksAndMessages(null)
        titleTextView.text = "System"
    }

    private fun listenForUserStatus() {
        startTitleLoadingAnimation()
        contentScrollView.visibility = View.GONE
        
        userDataListener = userRef?.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                
                val maxLength = 35
                val galaxy = snapshot.child("galaxy").getValue(String::class.java) ?: "N/A"
                currentUserGalaxy = galaxy
                galaxyDataTextView.text = if (galaxy.length > maxLength) galaxy.take(maxLength) + "..." else galaxy
                
                val star = snapshot.child("star").getValue(String::class.java) ?: "N/A"
                starDataTextView.text = if (star.length > maxLength) star.take(maxLength) + "..." else star
                
                val planet = snapshot.child("planet").getValue(String::class.java) ?: "N/A"
                currentUserPlanet = planet 
                planetDataTextView.text = if (planet.length > maxLength) planet.take(maxLength) + "..." else planet
                
                val orbit = snapshot.child("orbit").getValue(Long::class.java) ?: 0L
                orbitDataTextView.text = "$orbit hours"
                
                travelCompletionTimestamp = snapshot.child("travelCompletionTimestamp").getValue(Long::class.java)
                
                updateFindNewSystemButtonState()
                stopTitleLoadingAnimation()
                contentScrollView.visibility = View.VISIBLE
            }

            override fun onCancelled(error: DatabaseError) {
                if (!isAdded) return
                stopTitleLoadingAnimation()
                Toast.makeText(context, "Failed to load user data.", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateFindNewSystemButtonState() {
        buttonUpdateRunnable?.let { handler.removeCallbacks(it) }
        buttonUpdateRunnable = object : Runnable {
            override fun run() {
                val isTraveling = travelCompletionTimestamp != null && System.currentTimeMillis() < travelCompletionTimestamp!!
                if (isTraveling) {
                    val remaining = travelCompletionTimestamp!! - System.currentTimeMillis()
                    // If remaining is very large, format appropriately
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                    findNewSystemButton.text = String.format("Traveling... %02d:%02ds", minutes, seconds)
                    findNewSystemButton.isEnabled = false
                    
                    // If the user is on this screen but traveling, they might want to go to the travel screen to boost?
                    // Optional: Make button clickable to go back to travel screen? 
                    // For now, keep disabled as per original logic, but formatted nicely.
                    
                    handler.postDelayed(this, 1000)
                } else {
                    findNewSystemButton.text = "Find New System"
                    findNewSystemButton.isEnabled = true
                }
            }
        }
        handler.post(buttonUpdateRunnable!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        userDataListener?.let { userRef?.removeEventListener(it) }
        inventoryListener?.let { userRef?.child("inventory_data")?.removeEventListener(it) }
        
        buttonUpdateRunnable?.let { handler.removeCallbacks(it) }
        buttonUpdateRunnable = null
        
        miningUpdateRunnable?.let { handler.removeCallbacks(it) }
        miningUpdateRunnable = null
        
        titleLoadingHandler.removeCallbacksAndMessages(null)
        titleLoadingRunnable = null
        
        miningStatusLoadingHandler.removeCallbacksAndMessages(null)
        miningStatusLoadingRunnable = null
    }
}