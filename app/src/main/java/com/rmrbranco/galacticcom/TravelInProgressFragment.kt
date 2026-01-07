package com.rmrbranco.galacticcom

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import com.rmrbranco.galacticcom.data.managers.AdManager
import com.rmrbranco.galacticcom.data.managers.BadgeProgressManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min

class TravelInProgressFragment : Fragment() {

    private var travelAnimator: AnimatorSet? = null
    private val MIN_ANIMATION_DURATION = 1000L 

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null
    
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var timeListener: ValueEventListener? = null
    private var userRef: DatabaseReference? = null
    
    private var currentCompletionTimestamp: Long = 0L
    private var currentUserGalaxy: String? = null
    private var targetGalaxy: String? = null
    private var hasArrived = false
    
    // Views
    private var galaxyLogo: ImageView? = null
    private var planetView: ImageView? = null
    private var moonView: ImageView? = null
    private var travelMessageTextView: TextView? = null
    private var boostButton: Button? = null
    
    private var totalTravelDurationMs: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        val uid = auth.currentUser?.uid
        if (uid != null) {
            userRef = database.reference.child("users").child(uid)
        }
        return inflater.inflate(R.layout.fragment_travel_in_progress, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Do not use args for timestamp to avoid flickering old state
        // currentCompletionTimestamp = arguments?.getLong("completion_timestamp") ?: System.currentTimeMillis()
        
        currentUserGalaxy = arguments?.getString("currentUserGalaxy")
        targetGalaxy = arguments?.getString("targetGalaxy") // This might be stale too
        
        galaxyLogo = view.findViewById(R.id.galaxy_logo_travel)
        planetView = view.findViewById(R.id.planet)
        moonView = view.findViewById(R.id.moon)
        
        travelMessageTextView = view.findViewById(R.id.travel_message)
        boostButton = view.findViewById<Button>(R.id.btn_boost_travel)

        travelMessageTextView?.text = "Syncing flight data..."
        boostButton?.isEnabled = false // Disable until data loaded

        // Do not start countdown or animation here. Wait for Firebase data.
        
        boostButton?.setOnClickListener {
             // We check logic inside showBoostAd/applySpeedBoost
             showBoostAd(boostButton!!, targetGalaxy != null)
        }
        
        AdManager.loadRewardedAd(requireContext())
        
        // Listen for Real-Time Updates (Time, Target Galaxy, Boost status)
        listenForRealTimeUpdates()
    }
    
    private fun setupUI(isIntergalactic: Boolean) {
        val durationMinutes = if (isIntergalactic) {
            SettingsManager.getIntergalacticTravelTimeMinutes()
        } else {
            SettingsManager.getPlanetaryTravelTimeMinutes()
        }
        totalTravelDurationMs = durationMinutes * 60 * 1000L

        if (isIntergalactic) {
            galaxyLogo?.visibility = View.VISIBLE
            planetView?.visibility = View.GONE
            moonView?.visibility = View.GONE
        } else {
            galaxyLogo?.visibility = View.GONE
            planetView?.visibility = View.VISIBLE
            moonView?.visibility = View.VISIBLE
        }
    }
    
    private fun listenForRealTimeUpdates() {
        timeListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val remoteTimestamp = snapshot.child("travelCompletionTimestamp").getValue(Long::class.java)
                val remoteTargetGalaxy = snapshot.child("targetGalaxy").getValue(String::class.java)
                val travelType = snapshot.child("travelType").getValue(String::class.java)
                val isBoosted = snapshot.child("travelBoosted").getValue(Boolean::class.java) ?: false
                
                if (remoteTargetGalaxy != null) {
                    targetGalaxy = remoteTargetGalaxy
                }
                
                // Determine if intergalactic based on DB state
                val isIntergalactic = targetGalaxy != null || travelType == "INTERGALACTIC"
                
                // Setup UI once we have data
                setupUI(isIntergalactic)

                // Update Boost Button State
                if (isBoosted) {
                    boostButton?.text = "Warp Active"
                    boostButton?.isEnabled = false
                    boostButton?.alpha = 0.5f
                } else {
                    boostButton?.text = "BOOST (Watch Ad)"
                    boostButton?.isEnabled = true
                    boostButton?.alpha = 1.0f
                }

                if (remoteTimestamp != null) {
                    currentCompletionTimestamp = remoteTimestamp
                    
                    // Start countdown if not already running
                    if (countdownRunnable == null) {
                        startCountdown()
                    }
                    
                    val remaining = remoteTimestamp - System.currentTimeMillis()
                    if (remaining <= 0) {
                         // Only arrive if we actually have a valid timestamp that is passed
                         // and we haven't processed arrival yet (though arriveAtNewSystem checks hasArrived)
                        arriveAtNewSystem()
                    } else {
                        updateAnimationState(remoteTimestamp, isIntergalactic)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        
        // Listen to parent node to get timestamp, targetGalaxy, boost status
        userRef?.addValueEventListener(timeListener!!)
    }
    
    private fun updateAnimationState(completionTime: Long, isIntergalactic: Boolean) {
        val remainingTime = max(0L, completionTime - System.currentTimeMillis())
        if (remainingTime <= 0) return

        val elapsedTime = max(0L, totalTravelDurationMs - remainingTime)
        // Ensure progress is within 0.0 to 1.0
        val progress = min(1.0f, max(0.0f, elapsedTime.toFloat() / totalTravelDurationMs.toFloat()))
        
        if (isIntergalactic) {
            val initialScale = max(0.001f, progress) 
            galaxyLogo?.apply {
                scaleX = initialScale
                scaleY = initialScale
                alpha = initialScale 
            }
        } else {
            // Interplanetary Params
            val planetScale = 0.01f + (progress * (2.0f - 0.01f))
            planetView?.apply {
                scaleX = planetScale
                scaleY = planetScale
                alpha = progress
            }
            
            val moonScale = 0.01f + (progress * (2.0f - 0.01f))
            val moonX = 1f + (progress * (500f - 1f))
            val moonY = -1f + (progress * (-500f - (-1f)))
            
            moonView?.apply {
                scaleX = moonScale
                scaleY = moonScale
                alpha = progress
                translationX = moonX
                translationY = moonY
            }
        }
        
        startAnimation(remainingTime, isIntergalactic)
    }
    
    private fun startAnimation(durationMs: Long, isIntergalactic: Boolean) {
        // Only start if not running or if significantly different? 
        // Simply restarting logic for now to ensure sync with new duration
        travelAnimator?.cancel()
        val animDuration = max(durationMs, MIN_ANIMATION_DURATION)

        if (isIntergalactic) {
            val logo = galaxyLogo ?: return
            val scaleX = ObjectAnimator.ofFloat(logo, "scaleX", logo.scaleX, 1f)
            val scaleY = ObjectAnimator.ofFloat(logo, "scaleY", logo.scaleY, 1f)
            val alpha = ObjectAnimator.ofFloat(logo, "alpha", logo.alpha, 1f)

            travelAnimator = AnimatorSet().apply {
                playTogether(scaleX, scaleY, alpha)
                duration = animDuration
                interpolator = LinearInterpolator()
            }
        } else {
            // Interplanetary Animation
            val planet = planetView ?: return
            val moon = moonView ?: return
            
            val planetScaleX = ObjectAnimator.ofFloat(planet, "scaleX", planet.scaleX, 2.0f)
            val planetScaleY = ObjectAnimator.ofFloat(planet, "scaleY", planet.scaleY, 2.0f)
            val planetAlpha = ObjectAnimator.ofFloat(planet, "alpha", planet.alpha, 1f)

            val moonScaleX = ObjectAnimator.ofFloat(moon, "scaleX", moon.scaleX, 2.0f)
            val moonScaleY = ObjectAnimator.ofFloat(moon, "scaleY", moon.scaleY, 2.0f)
            val moonAlpha = ObjectAnimator.ofFloat(moon, "alpha", moon.alpha, 1f)
            val moonTranslateX = ObjectAnimator.ofFloat(moon, "translationX", moon.translationX, 500f)
            val moonTranslateY = ObjectAnimator.ofFloat(moon, "translationY", moon.translationY, -500f)

            travelAnimator = AnimatorSet().apply {
                playTogether(planetScaleX, planetScaleY, planetAlpha, moonScaleX, moonScaleY, moonAlpha, moonTranslateX, moonTranslateY)
                interpolator = AccelerateDecelerateInterpolator()
                duration = animDuration
            }
        }
        
        travelAnimator?.start()
    }
    
    private fun showBoostAd(button: Button, isIntergalactic: Boolean) {
        if (AdManager.isAdReady()) {
            AdManager.showRewardedAd(requireActivity(), { type, amount ->
                applySpeedBoost(button, isIntergalactic)
            }, {
                // Ad closed
            })
        } else {
             Toast.makeText(context, "Ad not ready yet...", Toast.LENGTH_SHORT).show()
             AdManager.loadRewardedAd(requireContext())
        }
    }
    
    private fun applySpeedBoost(button: Button, isIntergalactic: Boolean) {
        userRef?.child("travelCompletionTimestamp")?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val remoteTimestamp = snapshot.getValue(Long::class.java) ?: currentCompletionTimestamp
                val now = System.currentTimeMillis()
                val remaining = remoteTimestamp - now
                
                if (remaining > 1000) {
                    val newRemaining = remaining / 2
                    val newCompletion = now + newRemaining
                    
                    val updates = mapOf(
                        "travelCompletionTimestamp" to newCompletion,
                        "travelBoosted" to true
                    )
                    
                    userRef?.updateChildren(updates)?.addOnSuccessListener {
                        if (isAdded) {
                            Toast.makeText(context, "Warp Drive Activated! Time reduced by 50%.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } else {
                    Toast.makeText(context, "Arriving soon...", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun startCountdown() {
        if (countdownRunnable != null) return // Already running
        
        countdownRunnable = object : Runnable {
            override fun run() {
                val remaining = currentCompletionTimestamp - System.currentTimeMillis()
                if (remaining > 0) {
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining)
                    travelMessageTextView?.text = "Traveling... $seconds seconds"
                    handler.postDelayed(this, 1000)
                } else {
                    travelMessageTextView?.text = "Arrived!"
                    if (isAdded && !hasArrived) {
                        arriveAtNewSystem()
                    }
                }
            }
        }
        handler.post(countdownRunnable!!)
    }
    
    private fun arriveAtNewSystem() {
        if (hasArrived) return
        hasArrived = true
        
        val updates = mutableMapOf<String, Any?>()
        
        val isIntergalactic = targetGalaxy != null
        val galaxy = if (isIntergalactic) targetGalaxy!! else (currentUserGalaxy ?: "Milky Way")
        
        if (isIntergalactic) {
             updates["galaxy"] = galaxy
             // Badge Logic: Record Galaxy Visit
             BadgeProgressManager.recordGalaxyVisit(galaxy)
        }
        
        val newStar = CosmicNameGenerator.generateStars(galaxy).random()
        val newPlanet = CosmicNameGenerator.generatePlanets(newStar).random()
        val newOrbit = (100..1000).random()
        
        updates["star"] = newStar
        updates["planet"] = newPlanet
        updates["orbit"] = newOrbit
        updates["travelType"] =  null
        updates["targetGalaxy"] = null // Clear target galaxy
        updates["travelCompletionTimestamp"] = null // Clear timestamp to prevent re-arrival loops
        updates["travelBoosted"] = null // Reset boost status
        
        // --- ADDED INVENTORY RESET ---
        updates["inventory_data/planetTotalReserves"] = -1L
        updates["inventory_data/lastCollectionTimestamp"] = ServerValue.TIMESTAMP
        // -----------------------------
        
        userRef?.updateChildren(updates)?.addOnCompleteListener {
             if (isAdded) {
                 val welcomeMsg = if (isIntergalactic) "Welcome to the $galaxy Galaxy!" else "Welcome to the $newStar system!"
                 Toast.makeText(context, welcomeMsg, Toast.LENGTH_LONG).show()
                 findNavController().popBackStack()
             }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        travelAnimator?.cancel()
        travelAnimator = null
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        timeListener?.let { userRef?.removeEventListener(it) } // Removing listener from USER ref
    }
}