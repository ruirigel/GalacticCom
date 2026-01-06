package com.rmrbranco.galacticcom

import android.os.Handler
import android.os.Looper
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.TimeUnit

data class GalaxyInfo(
    val name: String,
    val inhabitants: Int,
    val messageCount: Int, // New field for message count
    val morphology: String,
    val age: String,
    val dimension: String,
    val composition: String,
    val habitablePlanets: Int,
    val nonHabitablePlanets: Int,
    val isTraveling: Boolean = false,
    val cooldownTimestamp: Long? = null,
    val isCurrentGalaxy: Boolean = false
)

class GalaxyAdapter(
    private val onTravelClick: (GalaxyInfo) -> Unit,
    private val onItemClick: (GalaxyInfo) -> Unit
) : ListAdapter<GalaxyInfo, GalaxyAdapter.ViewHolder>(GalaxyDiffCallback()) {

    private val handler = Handler(Looper.getMainLooper())
    private val activeTimers = mutableMapOf<Button, Runnable>()

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val nameTextView: TextView = view.findViewById(R.id.tv_galaxy_item_name)
        private val countTextView: TextView = view.findViewById(R.id.tv_galaxy_item_count)
        private val messageCountTextView: TextView = view.findViewById(R.id.tv_galaxy_item_message_count) // New TextView
        private val morphologyTextView: TextView = view.findViewById(R.id.tv_galaxy_item_morphology)
        private val ageTextView: TextView = view.findViewById(R.id.tv_galaxy_item_age)
        private val dimensionTextView: TextView = view.findViewById(R.id.tv_galaxy_item_dimension)
        private val compositionTextView: TextView = view.findViewById(R.id.tv_galaxy_item_composition)
        private val habitablePlanetsTextView: TextView = view.findViewById(R.id.tv_galaxy_item_habitable_planets)
        private val nonHabitablePlanetsTextView: TextView = view.findViewById(R.id.tv_galaxy_item_non_habitable_planets)
        val travelButton: Button = view.findViewById(R.id.btn_travel_to_galaxy)

        fun bind(galaxyInfo: GalaxyInfo) {
            nameTextView.text = galaxyInfo.name

            fun setSpannableText(textView: TextView, label: String, value: String) {
                val spannable = SpannableString(label + value)
                val cyanColor = ContextCompat.getColor(itemView.context, R.color.neon_cyan)
                spannable.setSpan(
                    ForegroundColorSpan(cyanColor),
                    0,
                    label.length,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = spannable
            }

            setSpannableText(countTextView, "Inhabitants: ", galaxyInfo.inhabitants.toString())
            setSpannableText(messageCountTextView, "Messages: ", galaxyInfo.messageCount.toString()) // Bind new data
            setSpannableText(morphologyTextView, "Morphology: ", galaxyInfo.morphology)
            setSpannableText(ageTextView, "Age: ", galaxyInfo.age)
            setSpannableText(dimensionTextView, "Dimension: ", galaxyInfo.dimension)
            setSpannableText(compositionTextView, "Composition: ", galaxyInfo.composition)
            setSpannableText(habitablePlanetsTextView, "Habitable Planets: ", galaxyInfo.habitablePlanets.toString())
            setSpannableText(nonHabitablePlanetsTextView, "Non-Habitable Planets: ", galaxyInfo.nonHabitablePlanets.toString())

            updateButtonState(travelButton, galaxyInfo)

            itemView.setOnClickListener { onItemClick(galaxyInfo) }

            if (!galaxyInfo.isCurrentGalaxy) {
                travelButton.setOnClickListener { onTravelClick(galaxyInfo) }
            } else {
                travelButton.setOnClickListener(null)
            }
        }
    }

    private fun updateButtonState(button: Button, galaxyInfo: GalaxyInfo) {
        activeTimers[button]?.let { handler.removeCallbacks(it) }

        if (galaxyInfo.isCurrentGalaxy) {
            button.text = "You are here"
            button.isEnabled = false
            return
        }
        
        if (galaxyInfo.isTraveling) {
            button.text = "Travel to this Galaxy"
            button.isEnabled = false
            return // Stop here if a journey is in progress
        }

        val runnable = object : Runnable {
            override fun run() {
                val timeNow = System.currentTimeMillis()
                val lastTravel = galaxyInfo.cooldownTimestamp ?: 0
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
        activeTimers[button] = runnable
        handler.post(runnable)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_galaxy, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        activeTimers.values.forEach { handler.removeCallbacks(it) }
        activeTimers.clear()
    }
}

class GalaxyDiffCallback : DiffUtil.ItemCallback<GalaxyInfo>() {
    override fun areItemsTheSame(oldItem: GalaxyInfo, newItem: GalaxyInfo): Boolean {
        return oldItem.name == newItem.name
    }

    override fun areContentsTheSame(oldItem: GalaxyInfo, newItem: GalaxyInfo): Boolean {
        return oldItem == newItem
    }
}