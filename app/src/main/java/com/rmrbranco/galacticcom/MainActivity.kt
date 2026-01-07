package com.rmrbranco.galacticcom

import android.Manifest
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var navController: NavController
    private lateinit var bottomNavView: BottomNavigationView
    private lateinit var settingsFab: FloatingActionButton
    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private var seamlessMediaPlayer: SeamlessLoopMediaPlayer? = null
    private lateinit var sharedPreferences: SharedPreferences

    // Glitch Global
    private lateinit var glitchLayout: GlitchLayout
    private val glitchHandler = Handler(Looper.getMainLooper())
    private var glitchRunnable: Runnable? = null
    
    // Global Travel Listener
    private var travelListener: ValueEventListener? = null
    private var travelUserRef: DatabaseReference? = null
    private var lastProcessedTimestamp: Long = 0

    private val notificationBadgeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val messageType = intent?.getStringExtra(MyFirebaseMessagingService.EXTRA_MESSAGE_TYPE)
            when (messageType) {
                "private_message" -> showBadge(R.id.inboxFragment)
                "public_message" -> showBadge(R.id.homeFragment)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        glitchLayout = findViewById(R.id.main_glitch_layout)
        settingsFab = findViewById(R.id.fab_settings)
        bottomNavView = findViewById(R.id.bottom_nav_view)
        
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        
        // --- NAVIGATION SETUP ---
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        navController = navHostFragment.navController
        
        // Link Bottom Navigation with Controller (Always needs to be done)
        bottomNavView.setupWithNavController(navController)
        setupDestinationListener() // Setup UI visibility logic
        setupLongPressNavigation()

        val user = auth.currentUser

        // Only enforce start destination if this is a fresh start (not a rotation or process restore)
        if (savedInstanceState == null) {
            val startDestination = if (user != null) {
                R.id.homeFragment
            } else {
                R.id.loginFragment
            }
            
            // Inflate and set graph ONLY on fresh launch
            val navGraph = navController.navInflater.inflate(R.navigation.nav_graph)
            navGraph.setStartDestination(startDestination)
            navController.graph = navGraph
        }

        // --- BACKGROUND TASKS ---
        // These should run regardless of whether it's a fresh start or a restore,
        // provided the user is logged in.
        if (user != null) {
            updateIpAddressAndCountry()
            getAndStoreFcmToken()
            listenForTravelCompletion(user.uid)
        }

        settingsFab.setOnClickListener {
            navController.navigate(R.id.settingsFragment)
        }

        clearNotifications()

        LocalBroadcastManager.getInstance(this).registerReceiver(
            notificationBadgeReceiver, IntentFilter(MyFirebaseMessagingService.ACTION_SHOW_NOTIFICATION_BADGE)
        )

        askNotificationPermission()
        initializeMediaPlayer()
        
        val glitchEnabled = sharedPreferences.getBoolean("glitch_enabled", true)
        if (glitchEnabled) {
            startGlobalGlitchLoop()
        }
    }
    
    private fun setupLongPressNavigation() {
        bottomNavView.findViewById<View>(R.id.homeFragment)?.setOnLongClickListener {
            val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
            if (currentFragment is HomeFragment) {
                currentFragment.toggleViewMode()
                true
            } else {
                false
            }
        }

        bottomNavView.findViewById<View>(R.id.galaxyDirectoryFragment)?.setOnLongClickListener {
             val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
             val currentFragment = navHostFragment?.childFragmentManager?.fragments?.firstOrNull()
             if (currentFragment is GalaxyDirectoryFragment) {
                 currentFragment.toggleViewMode()
                 true
             } else {
                 false
             }
        }
    }
    
    // Moved UI logic to a separate helper, called in onCreate
    private fun setupDestinationListener() {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val isAuthScreen = destination.id == R.id.loginFragment || destination.id == R.id.registerFragment
            val isConversation = destination.id == R.id.conversationFragment
            val isSettings = destination.id == R.id.settingsFragment
            
            // Hide FAB on Auth, Conversation, and Settings itself
            val shouldHideFab = isAuthScreen || isConversation || isSettings
            settingsFab.visibility = if (shouldHideFab) View.GONE else View.VISIBLE
            
            // Hide Bottom Nav on Auth, Conversation and Settings
            val shouldHideBottomNav = isAuthScreen || isConversation || isSettings
            bottomNavView.visibility = if (shouldHideBottomNav) View.GONE else View.VISIBLE

            if (destination.id == R.id.inboxFragment) removeBadge(R.id.inboxFragment)
            if (destination.id == R.id.homeFragment) removeBadge(R.id.homeFragment)
        }
    }
    
    // Removed old setupNavigation function as it was resetting the graph

    private fun listenForTravelCompletion(userId: String) {
        // If we already have a listener attached (e.g. from a previous lifecycle), don't duplicate
        if (travelListener != null) return

        travelUserRef = database.reference.child("users").child(userId)
        
        travelListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val travelCompletionTimestamp = snapshot.child("travelCompletionTimestamp").getValue(Long::class.java)
                val travelType = snapshot.child("travelType").getValue(String::class.java)
                
                if (travelType != null && travelCompletionTimestamp != null) {
                    if (System.currentTimeMillis() >= travelCompletionTimestamp) {
                        if (travelCompletionTimestamp != lastProcessedTimestamp) {
                             lastProcessedTimestamp = travelCompletionTimestamp
                             completeGlobalTravel(snapshot)
                        }
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        
        travelUserRef?.addValueEventListener(travelListener!!)
    }
    
    private fun completeGlobalTravel(snapshot: DataSnapshot) {
        val travelType = snapshot.child("travelType").getValue(String::class.java) ?: return
        val currentGalaxy = snapshot.child("galaxy").getValue(String::class.java) ?: "Milky Way"
        val targetGalaxy = snapshot.child("targetGalaxy").getValue(String::class.java)
        
        if (travelType == "INTERPLANETARY") {
             arriveAtNewSystemGlobal(currentGalaxy, null)
        } else if (travelType == "INTERGALACTIC") {
             val destinationGalaxy = targetGalaxy ?: currentGalaxy
             arriveAtNewSystemGlobal(destinationGalaxy, destinationGalaxy)
             Toast.makeText(this@MainActivity, "Hyperspace Jump Complete! Welcome to the $destinationGalaxy Galaxy.", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun arriveAtNewSystemGlobal(galaxyForGeneration: String, newGalaxyToSet: String?) {
        val newStar = CosmicNameGenerator.generateStars(galaxyForGeneration).random()
        val newPlanet = CosmicNameGenerator.generatePlanets(newStar).random()
        val newOrbit = (100..1000).random()
        
        val updates = mutableMapOf<String, Any?>()
        if (newGalaxyToSet != null) {
            updates["galaxy"] = newGalaxyToSet
        }
        updates["star"] = newStar
        updates["planet"] = newPlanet
        updates["orbit"] = newOrbit
        
        updates["travelType"] = null 
        updates["targetGalaxy"] = null
        updates["travelCompletionTimestamp"] = null
        updates["travelBoosted"] = null
        
        // Reset reserves on arrival
        updates["inventory_data/planetTotalReserves"] = -1L
        updates["inventory_data/lastCollectionTimestamp"] = ServerValue.TIMESTAMP
        
        travelUserRef?.updateChildren(updates)?.addOnSuccessListener {
             if (newGalaxyToSet == null) {
                 Toast.makeText(this@MainActivity, "Arrived at $newStar system!", Toast.LENGTH_LONG).show()
             }
        }
    }

    private fun initializeMediaPlayer() {
        if (seamlessMediaPlayer == null) {
            seamlessMediaPlayer = SeamlessLoopMediaPlayer(this, R.raw.synth_ambience)
        }
        
        val musicEnabled = sharedPreferences.getBoolean("music_enabled", true)
        if (musicEnabled) {
            seamlessMediaPlayer?.start()
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        sharedPreferences.edit().putBoolean("music_enabled", enabled).apply()
        if (enabled) {
            if (seamlessMediaPlayer == null) {
                 initializeMediaPlayer()
            }
            if (seamlessMediaPlayer?.isPlaying() == false) {
                seamlessMediaPlayer?.start()
            }
        } else {
            seamlessMediaPlayer?.pause()
        }
    }
    
    fun setGlitchEnabled(enabled: Boolean) {
        if (enabled) {
            startGlobalGlitchLoop()
        } else {
            stopGlobalGlitchLoop()
            glitchLayout.setIntensity(0f)
        }
    }

    private fun startGlobalGlitchLoop() {
        stopGlobalGlitchLoop()

        glitchRunnable = Runnable {
            val intensity = Random.nextFloat() * 0.4f + 0.6f 
            val duration = Random.nextLong(400, 900)

            val animator = ValueAnimator.ofFloat(0f, intensity, 0f)
            animator.duration = duration
            animator.interpolator = android.view.animation.BounceInterpolator()
            animator.addUpdateListener { 
                val v = it.animatedValue as Float
                glitchLayout.setIntensity(v) 
            }
            animator.start()

            val nextDelay = Random.nextLong(5000, 20000)
            glitchHandler.postDelayed(glitchRunnable!!, nextDelay)
        }
        
        glitchHandler.postDelayed(glitchRunnable!!, 3000)
    }

    private fun stopGlobalGlitchLoop() {
        glitchRunnable?.let { glitchHandler.removeCallbacks(it) }
    }

    override fun onResume() {
        super.onResume()
        val musicEnabled = sharedPreferences.getBoolean("music_enabled", true)
        if (musicEnabled && seamlessMediaPlayer?.isPlaying() == false) {
            seamlessMediaPlayer?.start()
        }
        
        val glitchEnabled = sharedPreferences.getBoolean("glitch_enabled", true)
        if (glitchEnabled) {
             startGlobalGlitchLoop()
        }
    }

    override fun onPause() {
        super.onPause()
        seamlessMediaPlayer?.pause()
        stopGlobalGlitchLoop()
    }

    private fun updateIpAddressAndCountry() {
        lifecycleScope.launch {
            val ipInfo = IpApiService.getIpInfo()
            if (ipInfo != null) {
                val userId = auth.currentUser?.uid
                if (userId != null) {
                    val updates = mapOf(
                        "ipAddress" to ipInfo.ipAddress,
                        "country" to ipInfo.country
                    )
                    database.reference.child("users").child(userId).updateChildren(updates)
                }
            }
        }
    }

    private fun getAndStoreFcmToken() {
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("MainActivity", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }

            val token = task.result
            val userId = auth.currentUser?.uid

            if (userId != null && token != null) {
                database.reference.child("users").child(userId).child("fcmToken").setValue(token)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(notificationBadgeReceiver)
        seamlessMediaPlayer?.release()
        seamlessMediaPlayer = null
        stopGlobalGlitchLoop()
        travelListener?.let { travelUserRef?.removeEventListener(it) }
        travelListener = null // Ensure listener is cleared so it can be re-added if activity recreates
    }

    private fun showBadge(menuItemId: Int) {
        val badge = bottomNavView.getOrCreateBadge(menuItemId)
        badge.isVisible = true
    }

    private fun removeBadge(menuItemId: Int) {
        bottomNavView.removeBadge(menuItemId)
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* No action needed */ }


    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun clearNotifications() {
        val notificationManager = NotificationManagerCompat.from(this)
        notificationManager.cancelAll()
    }
}