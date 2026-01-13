package com.rmrbranco.galacticcom

import android.app.Application
import android.util.Log
import com.google.android.gms.ads.MobileAds
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.rmrbranco.galacticcom.data.managers.AdManager
import com.rmrbranco.galacticcom.data.managers.SettingsManager

class GalacticComApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Initialize Firebase
        FirebaseApp.initializeApp(this)
        
        // Initialize App Check
        val firebaseAppCheck = FirebaseAppCheck.getInstance()
        firebaseAppCheck.installAppCheckProviderFactory(
            if (BuildConfig.DEBUG) {
                DebugAppCheckProviderFactory.getInstance()
            } else {
                PlayIntegrityAppCheckProviderFactory.getInstance()
            }
        )

        // Debug: Check if Token is being generated
        if (BuildConfig.DEBUG) {
            firebaseAppCheck.getAppCheckToken(false).addOnSuccessListener {
                Log.d("AppCheck", "Debug Token retrieved successfully. If login fails, check Firebase Console.")
            }.addOnFailureListener {
                Log.e("AppCheck", "Failed to retrieve Debug Token", it)
            }
        }
        
        AppLifecycleObserver.init(this)
        SettingsManager.initialize()

        // Initialize Google Mobile Ads SDK
        MobileAds.initialize(this) {
            // Load the first ad once SDK is initialized
            AdManager.loadRewardedAd(this)
        }
    }
}