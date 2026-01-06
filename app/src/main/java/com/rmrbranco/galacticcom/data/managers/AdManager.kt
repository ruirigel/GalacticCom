package com.rmrbranco.galacticcom.data.managers

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback

object AdManager {
    private const val TAG = "AdManager"
    // Production Ad Unit ID for Rewarded Ad
    private const val AD_UNIT_ID = "ca-app-pub-1730389524070241/1386389739"

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false

    fun loadRewardedAd(context: Context) {
        if (rewardedAd != null || isAdLoading) return

        isAdLoading = true
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(context, AD_UNIT_ID, adRequest, object : RewardedAdLoadCallback() {
            override fun onAdFailedToLoad(adError: LoadAdError) {
                Log.d(TAG, adError.toString())
                rewardedAd = null
                isAdLoading = false
            }

            override fun onAdLoaded(ad: RewardedAd) {
                Log.d(TAG, "Ad was loaded.")
                rewardedAd = ad
                isAdLoading = false
            }
        })
    }

    fun showRewardedAd(activity: Activity, onRewardEarned: (String, Int) -> Unit, onAdClosed: () -> Unit) {
        if (rewardedAd != null) {
            rewardedAd?.fullScreenContentCallback = object : FullScreenContentCallback() {
                override fun onAdClicked() {
                    Log.d(TAG, "Ad was clicked.")
                }

                override fun onAdDismissedFullScreenContent() {
                    Log.d(TAG, "Ad dismissed fullscreen content.")
                    rewardedAd = null
                    loadRewardedAd(activity) // Preload the next ad
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                    Log.e(TAG, "Ad failed to show fullscreen content.")
                    rewardedAd = null
                    onAdClosed()
                }

                override fun onAdImpression() {
                    Log.d(TAG, "Ad recorded an impression.")
                }

                override fun onAdShowedFullScreenContent() {
                    Log.d(TAG, "Ad showed fullscreen content.")
                }
            }

            rewardedAd?.show(activity) { rewardItem ->
                val rewardAmount = rewardItem.amount
                val rewardType = rewardItem.type
                Log.d(TAG, "User earned the reward. Amount: $rewardAmount, Type: $rewardType")
                onRewardEarned(rewardType, rewardAmount)
            }
        } else {
            Log.d(TAG, "The rewarded ad wasn't ready yet.")
            loadRewardedAd(activity)
            // Ideally notify caller that ad wasn't ready
        }
    }

    fun isAdReady(): Boolean {
        return rewardedAd != null
    }
}