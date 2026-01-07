package com.rmrbranco.galacticcom.data.model

data class Badge(
    val id: String,
    val name: String,
    val description: String,
    val iconResId: Int = 0, // Placeholder
    val isUnlocked: Boolean = false,
    val currentTier: BadgeTier = BadgeTier.NONE,
    val nextTier: BadgeTier? = null,
    val progress: Int = 0,       // Current progress value
    val maxProgress: Int = 100,  // Target value for next tier
    val progressPercentage: Int = 0, // 0-100
    
    // Reward Logic
    val isClaimed: Boolean = false, // Is the reward for the CURRENT tier claimed?
    val rewardAmount: Int = 0       // How much is the reward for this tier?
)

enum class BadgeTier {
    NONE, BRONZE, SILVER, GOLD, PLATINUM, DIAMOND, ULTRA_RARE, LEGENDARY
}
