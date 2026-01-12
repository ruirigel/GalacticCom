package com.rmrbranco.galacticcom

import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import java.util.concurrent.TimeUnit

class GalaxyOptionsBottomSheet : BottomSheetDialogFragment() {

    private var galaxyName: String = ""
    private var morphology: String = ""
    private var age: String = ""
    private var dimension: String = ""
    private var composition: String = ""
    private var inhabitants: Int = 0
    private var messageCount: Int = 0
    private var habitablePlanets: Int = 0
    private var nonHabitablePlanets: Int = 0
    private var isCurrentGalaxy: Boolean = false
    private var isTraveling: Boolean = false
    private var cooldownTimestamp: Long = 0L

    var onTravelClick: (() -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private var timerRunnable: Runnable? = null

    companion object {
        fun newInstance(galaxyInfo: GalaxyInfo): GalaxyOptionsBottomSheet {
            val fragment = GalaxyOptionsBottomSheet()
            val args = Bundle()
            args.putString("name", galaxyInfo.name)
            args.putString("morphology", galaxyInfo.morphology)
            args.putString("age", galaxyInfo.age)
            args.putString("dimension", galaxyInfo.dimension)
            args.putString("composition", galaxyInfo.composition)
            args.putInt("inhabitants", galaxyInfo.inhabitants)
            args.putInt("messageCount", galaxyInfo.messageCount)
            args.putInt("habitablePlanets", galaxyInfo.habitablePlanets)
            args.putInt("nonHabitablePlanets", galaxyInfo.nonHabitablePlanets)
            args.putBoolean("isCurrentGalaxy", galaxyInfo.isCurrentGalaxy)
            args.putBoolean("isTraveling", galaxyInfo.isTraveling)
            args.putLong("cooldownTimestamp", galaxyInfo.cooldownTimestamp ?: 0L)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            galaxyName = it.getString("name", "")
            morphology = it.getString("morphology", "")
            age = it.getString("age", "")
            dimension = it.getString("dimension", "")
            composition = it.getString("composition", "")
            inhabitants = it.getInt("inhabitants", 0)
            messageCount = it.getInt("messageCount", 0)
            habitablePlanets = it.getInt("habitablePlanets", 0)
            nonHabitablePlanets = it.getInt("nonHabitablePlanets", 0)
            isCurrentGalaxy = it.getBoolean("isCurrentGalaxy", false)
            isTraveling = it.getBoolean("isTraveling", false)
            cooldownTimestamp = it.getLong("cooldownTimestamp", 0L)
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = (it as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.background = ColorDrawable(Color.TRANSPARENT)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_galaxy_options, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupUI(view)
    }

    private fun setupUI(view: View) {
        val nameTextView = view.findViewById<TextView>(R.id.dialog_galaxy_name)
        val inhabitantsTextView = view.findViewById<TextView>(R.id.dialog_galaxy_inhabitants)
        val messagesTextView = view.findViewById<TextView>(R.id.dialog_galaxy_messages)
        val morphologyTextView = view.findViewById<TextView>(R.id.dialog_galaxy_morphology)
        val ageTextView = view.findViewById<TextView>(R.id.dialog_galaxy_age)
        val dimensionTextView = view.findViewById<TextView>(R.id.dialog_galaxy_dimension)
        val compositionTextView = view.findViewById<TextView>(R.id.dialog_galaxy_composition)
        val habitableTextView = view.findViewById<TextView>(R.id.dialog_galaxy_habitable)
        val nonHabitableTextView = view.findViewById<TextView>(R.id.dialog_galaxy_non_habitable)
        val travelButton = view.findViewById<Button>(R.id.btn_travel)

        nameTextView.text = galaxyName
        
        inhabitantsTextView.text = "Inhabitants: $inhabitants"
        messagesTextView.text = "Messages: $messageCount"
        morphologyTextView.text = "Morphology: $morphology"
        ageTextView.text = "Age: $age"
        dimensionTextView.text = "Dimension: $dimension"
        compositionTextView.text = "Composition: $composition"
        habitableTextView.text = "Habitable Planets: $habitablePlanets"
        nonHabitableTextView.text = "Non-Habitable Planets: $nonHabitablePlanets"
        
        // Travel Cost removed

        updateButtonState(travelButton)
        
        travelButton.setOnClickListener {
            onTravelClick?.invoke()
            dismiss()
        }
    }

    private fun updateButtonState(button: Button) {
        timerRunnable?.let { handler.removeCallbacks(it) }

        if (isCurrentGalaxy) {
            button.text = "You are here"
            button.isEnabled = false
            return
        }
        
        if (isTraveling) {
            button.text = "Travel to this Galaxy"
            button.isEnabled = false
            return 
        }

        timerRunnable = object : Runnable {
            override fun run() {
                val timeNow = System.currentTimeMillis()
                val lastTravel = cooldownTimestamp
                val timeSinceLastTravel = timeNow - lastTravel
                val cooldownDuration = TimeUnit.HOURS.toMillis(24)

                if (timeSinceLastTravel < cooldownDuration) {
                    val remaining = cooldownDuration - timeSinceLastTravel
                    val hours = TimeUnit.MILLISECONDS.toHours(remaining)
                    val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining) % 60
                    val seconds = TimeUnit.MILLISECONDS.toSeconds(remaining) % 60
                    button.text = String.format("Next Jump: %02d:%02d:%02d", hours, minutes, seconds)
                    button.isEnabled = false
                    handler.postDelayed(this, 1000)
                } else {
                    button.text = "Travel to this Galaxy"
                    button.isEnabled = true
                }
            }
        }
        handler.post(timerRunnable!!)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        timerRunnable?.let { handler.removeCallbacks(it) }
    }
}