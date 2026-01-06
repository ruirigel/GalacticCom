package com.rmrbranco.galacticcom

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue

class InterplanetaryTravelFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_interplanetary_travel, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val planet = view.findViewById<ImageView>(R.id.planet)
        val moon = view.findViewById<ImageView>(R.id.moon)
        val countdownTextView = view.findViewById<TextView>(R.id.countdown_timer_textview)

        // Set initial state (minuscule and invisible)
        planet.scaleX = 0.01f
        planet.scaleY = 0.01f
        planet.alpha = 0f
        moon.scaleX = 0.01f
        moon.scaleY = 0.01f
        moon.alpha = 0f
        moon.translationX = 1f
        moon.translationY = -1f

        // 1. Animate from a tiny point to near
        val planetScaleX = ObjectAnimator.ofFloat(planet, "scaleX", 0.01f, 2.0f)
        val planetScaleY = ObjectAnimator.ofFloat(planet, "scaleY", 0.01f, 2.0f)
        val planetAlpha = ObjectAnimator.ofFloat(planet, "alpha", 0f, 1f)

        val moonScaleX = ObjectAnimator.ofFloat(moon, "scaleX", 0.01f, 2.0f)
        val moonScaleY = ObjectAnimator.ofFloat(moon, "scaleY", 0.01f, 2.0f)
        val moonAlpha = ObjectAnimator.ofFloat(moon, "alpha", 0f, 1f)
        val moonTranslateX = ObjectAnimator.ofFloat(moon, "translationX", 1f, 500f)
        val moonTranslateY = ObjectAnimator.ofFloat(moon, "translationY", -1f, -500f)

        val travelAnimation = AnimatorSet().apply {
            playTogether(planetScaleX, planetScaleY, planetAlpha, moonScaleX, moonScaleY, moonAlpha, moonTranslateX, moonTranslateY)
            interpolator = AccelerateDecelerateInterpolator()
            duration = 10000
        }

        // Countdown Timer
        object : CountDownTimer(10000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                countdownTextView.text = "Traveling... ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                countdownTextView.text = "Traveling... 0s"
            }
        }.start()

        travelAnimation.start()

        // 2. After the animation, find a new system and then navigate back.
        Handler(Looper.getMainLooper()).postDelayed({
            findNewCosmicSystem()
        }, 10500)
    }

    private fun findNewCosmicSystem() {
        val currentUserGalaxy = arguments?.getString("currentUserGalaxy")
        if (currentUserGalaxy == null) {
            Toast.makeText(context, "Error: Galaxy information not found.", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_interplanetary_travel_to_settings)
            return
        }

        val auth = FirebaseAuth.getInstance()
        val database = FirebaseDatabase.getInstance()
        val userRef = auth.currentUser?.uid?.let { database.reference.child("users").child(it) }

        if (userRef == null) {
            Toast.makeText(context, "Error: User not found.", Toast.LENGTH_SHORT).show()
            findNavController().navigate(R.id.action_interplanetary_travel_to_settings)
            return
        }

        val newStar = CosmicNameGenerator.generateStars(currentUserGalaxy).random()
        val newPlanet = CosmicNameGenerator.generatePlanets(newStar).random()
        val newOrbit = (100..1000).random()

        val updates = mapOf(
            "star" to newStar,
            "planet" to newPlanet,
            "orbit" to newOrbit
        )

        userRef.updateChildren(updates)
            .addOnSuccessListener {
                if (!isAdded) return@addOnSuccessListener
                Toast.makeText(context, "New planetary system discovered!", Toast.LENGTH_LONG).show()
                findNavController().navigate(R.id.action_interplanetary_travel_to_settings)
            }
            .addOnFailureListener {
                if (!isAdded) return@addOnFailureListener
                Toast.makeText(context, "Failed to find a new system.", Toast.LENGTH_SHORT).show()
                findNavController().navigate(R.id.action_interplanetary_travel_to_settings)
            }
    }
}