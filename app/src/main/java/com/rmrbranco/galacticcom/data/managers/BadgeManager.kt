package com.rmrbranco.galacticcom.data.managers

import com.rmrbranco.galacticcom.data.model.ActionLogs
import com.rmrbranco.galacticcom.data.model.Badge
import com.rmrbranco.galacticcom.data.model.BadgeTier

object BadgeManager {

    fun getBadges(logs: ActionLogs): List<Badge> {
        // Returned in order of difficulty (Left -> Right)
        return listOf(
            getVoidGuardianBadge(logs),   // Easy: Click balloons
            getVoiceBadge(logs),          // Easy: Send audio
            getDarkMatterBadge(logs),     // Medium: Mining Grind
            getStarNomadBadge(logs),      // Medium: Economy Grind
            getMuranoBadge(logs),         // Medium: Exploration (Messages)
            getScholarBadge(logs),        // Hard: Exploration (Travel)
            getGhostFleetBadge(logs),     // Hard: Social/Detach
            getObserverBadge(logs),       // Very Hard: Time (10 Years)
            getHalleyBadge(logs),         // Ultra Rare: Event
            getSupernovaBadge(logs)       // Legendary: Event
        )
    }
    
    private fun getRewardForTier(tier: BadgeTier): Int {
        return when (tier) {
            BadgeTier.BRONZE -> 1000
            BadgeTier.SILVER -> 5000
            BadgeTier.GOLD -> 25000
            BadgeTier.PLATINUM -> 100000
            BadgeTier.DIAMOND -> 500000
            BadgeTier.ULTRA_RARE -> 500000 
            BadgeTier.LEGENDARY -> 1000000
            BadgeTier.NONE -> 0
        }
    }
    
    // Helper to determine what reward to show. 
    // If locked (NONE), show BRONZE reward as incentive.
    private fun getDisplayReward(currentTier: BadgeTier, badgeStartTier: BadgeTier = BadgeTier.BRONZE): Int {
        return if (currentTier == BadgeTier.NONE) {
            getRewardForTier(badgeStartTier)
        } else {
            getRewardForTier(currentTier)
        }
    }
    
    private fun isRewardClaimed(logs: ActionLogs, badgeId: String, tier: BadgeTier): Boolean {
        if (tier == BadgeTier.NONE) return false
        val key = "${badgeId}_${tier.name}"
        return logs.claimedBadgeRewards.containsKey(key)
    }

    private fun getObserverBadge(logs: ActionLogs): Badge {
        val months = logs.monthsWithBroadcast
        val tier: BadgeTier
        val nextTier: BadgeTier?
        val target: Int

        when {
            months >= 120 -> { 
                tier = BadgeTier.GOLD
                nextTier = null
                target = 120
            }
            months >= 60 -> { 
                tier = BadgeTier.SILVER
                nextTier = BadgeTier.GOLD
                target = 120
            }
            months >= 12 -> { 
                tier = BadgeTier.BRONZE
                nextTier = BadgeTier.SILVER
                target = 60
            }
            else -> {
                tier = BadgeTier.NONE
                nextTier = BadgeTier.BRONZE
                target = 12
            }
        }
        
        val id = "observer_eras"
        return Badge(
            id = id,
            name = "The Observer of Ages",
            description = "Maintain active account and broadcast at least once a month for 1, 5, and 10 years.",
            currentTier = tier,
            nextTier = nextTier,
            progress = months,
            maxProgress = target,
            progressPercentage = calculatePercentage(months, target),
            isUnlocked = tier != BadgeTier.NONE,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.BRONZE)
        )
    }

    private fun getHalleyBadge(logs: ActionLogs): Badge {
        val events = logs.specialEventsParticipated.size
        val target = 5
        val unlocked = events >= target
        val tier = if (unlocked) BadgeTier.ULTRA_RARE else BadgeTier.NONE
        val id = "halley_comet"

        return Badge(
            id = id,
            name = "Halley's Comet",
            description = "Be present during rare astronomical events (5x).",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.ULTRA_RARE,
            progress = events,
            maxProgress = target,
            progressPercentage = calculatePercentage(events, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.ULTRA_RARE)
        )
    }

    private fun getMuranoBadge(logs: ActionLogs): Badge {
        val count = logs.messagedGalaxies.size
        val target = 10
        val unlocked = count >= target
        val tier = if (unlocked) BadgeTier.GOLD else BadgeTier.NONE
        val id = "murano_glass"

        return Badge(
            id = id,
            name = "The Murano Glass Bottle",
            description = "Send messages to 10 different galaxies.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.GOLD,
            progress = count,
            maxProgress = target,
            progressPercentage = calculatePercentage(count, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.GOLD)
        )
    }

    private fun getGhostFleetBadge(logs: ActionLogs): Badge {
        val count = logs.severedConversationsCount
        val target = 1000
        val unlocked = count >= target
        val tier = if (unlocked) BadgeTier.LEGENDARY else BadgeTier.NONE
        val id = "ghost_fleet"

        return Badge(
            id = id,
            name = "Admiral of the Ghost Fleet",
            description = "Accumulate 1,000 severed private conversations.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.LEGENDARY,
            progress = count,
            maxProgress = target,
            progressPercentage = calculatePercentage(count, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.LEGENDARY)
        )
    }

    private fun getStarNomadBadge(logs: ActionLogs): Badge {
        val totalUniqueItemsNeeded = 10 
        val itemsMastered = logs.harlockItemsPurchased.count { it.value >= 50 }
        
        val target = totalUniqueItemsNeeded
        val unlocked = itemsMastered >= target
        val tier = if (unlocked) BadgeTier.DIAMOND else BadgeTier.NONE
        val id = "star_nomad"

        return Badge(
            id = id,
            name = "Star Nomad Lifetime Member",
            description = "Buy all unique items from Captain Harlock 50x.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.DIAMOND,
            progress = itemsMastered,
            maxProgress = target,
            progressPercentage = calculatePercentage(itemsMastered, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.DIAMOND)
        )
    }

    private fun getScholarBadge(logs: ActionLogs): Badge {
        val visited = logs.visitedGalaxies.size
        val target = 50 
        val unlocked = visited >= target
        val tier = if (unlocked) BadgeTier.PLATINUM else BadgeTier.NONE
        val id = "scholar_worlds"

        return Badge(
            id = id,
            name = "Scholar of a Thousand Worlds",
            description = "Inhabit all existing galaxies.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.PLATINUM,
            progress = visited,
            maxProgress = target,
            progressPercentage = calculatePercentage(visited, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.PLATINUM)
        )
    }

    private fun getDarkMatterBadge(logs: ActionLogs): Badge {
        val amount = logs.miningTotalAccumulated
        val target = 1000000L
        val unlocked = amount >= target
        val tier = if (unlocked) BadgeTier.DIAMOND else BadgeTier.NONE
        val id = "dark_matter_miner"

        return Badge(
            id = id,
            name = "Dark Matter Miner",
            description = "Extract 1,000,000 units of rare resources.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.DIAMOND,
            progress = amount.toInt(),
            maxProgress = target.toInt(),
            progressPercentage = calculatePercentage(amount.toInt(), target.toInt()),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.DIAMOND)
        )
    }

    private fun getVoiceBadge(logs: ActionLogs): Badge {
        val minutes = logs.totalVoiceMinutesSent
        val target = 520.0 
        val unlocked = minutes >= target
        val tier = if (unlocked) BadgeTier.GOLD else BadgeTier.NONE
        val id = "voice_stars"

        return Badge(
            id = id,
            name = "Voice of the Stars",
            description = "Send a total of 520 minutes of voice messages.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.GOLD,
            progress = minutes.toInt(),
            maxProgress = target.toInt(),
            progressPercentage = calculatePercentage(minutes.toInt(), target.toInt()),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.GOLD)
        )
    }

    private fun getVoidGuardianBadge(logs: ActionLogs): Badge {
        val intercepted = logs.deepSpaceMessagesIntercepted
        val target = 500
        val unlocked = intercepted >= target
        val tier = if (unlocked) BadgeTier.PLATINUM else BadgeTier.NONE
        val id = "void_guardian"

        return Badge(
            id = id,
            name = "Guardian of the Void",
            description = "Intercept 500 'Deep Space' messages.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.PLATINUM,
            progress = intercepted,
            maxProgress = target,
            progressPercentage = calculatePercentage(intercepted, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.PLATINUM)
        )
    }

    private fun getSupernovaBadge(logs: ActionLogs): Badge {
        val survived = logs.supernovasSurvived
        val target = 1
        val unlocked = survived >= target
        val tier = if (unlocked) BadgeTier.LEGENDARY else BadgeTier.NONE
        val id = "supernova_survivor"

        return Badge(
            id = id,
            name = "Supernova Survivor",
            description = "Be in a galaxy at the moment of rebirth.",
            currentTier = tier,
            nextTier = if (unlocked) null else BadgeTier.LEGENDARY,
            progress = survived,
            maxProgress = target,
            progressPercentage = calculatePercentage(survived, target),
            isUnlocked = unlocked,
            isClaimed = isRewardClaimed(logs, id, tier),
            rewardAmount = getDisplayReward(tier, BadgeTier.LEGENDARY)
        )
    }

    private fun calculatePercentage(current: Int, target: Int): Int {
        if (target <= 0) return 100
        return ((current.toDouble() / target) * 100).toInt().coerceIn(0, 100)
    }
}
