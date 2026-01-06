package com.rmrbranco.galacticcom

import android.app.Application
import com.google.android.gms.ads.MobileAds
import com.rmrbranco.galacticcom.data.managers.AdManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager

class GalacticComApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLifecycleObserver.init(this)
        SettingsManager.initialize()

        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) {
            // Load the first ad once SDK is initialized
            AdManager.loadRewardedAd(this)
        }
    }
}