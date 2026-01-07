package com.rmrbranco.galacticcom

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

            // Set color based on tier
            // Note: Colors are ARGB.
            val tierColor = when (badge.currentTier) {
                BadgeTier.BRONZE -> 0xFFCD7F32.toInt()
                BadgeTier.SILVER -> 0xFFC0C0C0.toInt()
                BadgeTier.GOLD -> 0xFFFFD700.toInt()
                BadgeTier.PLATINUM -> 0xFFE5E4E2.toInt()
                BadgeTier.DIAMOND -> 0xFFB9F2FF.toInt()
                BadgeTier.ULTRA_RARE -> 0xFF9400D3.toInt()
                BadgeTier.LEGENDARY -> 0xFFFF0000.toInt()
                else -> 0xFF888888.toInt() // Grey for locked/none
            }
            
            icon.setColorFilter(tierColor)
            
            if (!badge.isUnlocked) {
                icon.alpha = 0.5f
                name.setTextColor(ContextCompat.getColor(itemView.context, R.color.darker_gray))
            } else {
                icon.alpha = 1.0f
                name.setTextColor(ContextCompat.getColor(itemView.context, R.color.neon_cyan))
            }
            
            itemView.setOnClickListener { onClick(badge) }
        }
    }
}
