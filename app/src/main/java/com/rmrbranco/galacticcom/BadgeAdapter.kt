package com.rmrbranco.galacticcom

import android.graphics.PorterDuff
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.rmrbranco.galacticcom.data.model.Badge
import com.rmrbranco.galacticcom.data.model.BadgeTier

class BadgeAdapter(
    private var badges: List<Badge> = emptyList(),
    private val onBadgeClick: (Badge) -> Unit // New click listener
) : RecyclerView.Adapter<BadgeAdapter.BadgeViewHolder>() {

    fun updateBadges(newBadges: List<Badge>) {
        badges = newBadges
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BadgeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_badge, parent, false)
        return BadgeViewHolder(view)
    }

    override fun onBindViewHolder(holder: BadgeViewHolder, position: Int) {
        holder.bind(badges[position], onBadgeClick)
    }

    override fun getItemCount(): Int = badges.size

    class BadgeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.badge_icon)
        private val name: TextView = itemView.findViewById(R.id.badge_name)
        private val progress: ProgressBar = itemView.findViewById(R.id.badge_progress)

        fun bind(badge: Badge, onClick: (Badge) -> Unit) {
            name.text = badge.name
            progress.max = badge.maxProgress
            progress.progress = badge.progress

            if (badge.iconResId != 0) {
                icon.setImageResource(badge.iconResId)
            }

            if (!badge.isUnlocked) {
                icon.alpha = 0.5f
                // Tint locked badges gray to hide details/color
                val grayColor = ContextCompat.getColor(itemView.context, R.color.darker_gray)
                icon.setColorFilter(grayColor, PorterDuff.Mode.SRC_IN)
                name.setTextColor(grayColor)
            } else {
                icon.alpha = 1.0f
                icon.clearColorFilter() // Show full color original PNG
                name.setTextColor(ContextCompat.getColor(itemView.context, R.color.neon_cyan))
            }
            
            itemView.setOnClickListener { onClick(badge) }
        }
    }
}
