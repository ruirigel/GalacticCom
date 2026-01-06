package com.rmrbranco.galacticcom

import android.app.Application
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

object AppLifecycleObserver : DefaultLifecycleObserver {

    var isAppInForeground = false
        private set
        
    var currentConversationId: String? = null

    fun init(application: Application) {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        Log.d("AppLifecycle", "App entered foreground.")
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        Log.d("AppLifecycle", "App entered background.")
    }
}