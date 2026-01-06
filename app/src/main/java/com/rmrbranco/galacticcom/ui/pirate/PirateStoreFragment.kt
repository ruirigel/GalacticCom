package com.rmrbranco.galacticcom.ui.pirate

import android.os.Bundle
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.AlienAvatarGenerator
import com.rmrbranco.galacticcom.R
import com.rmrbranco.galacticcom.ui.pirate.tabs.PirateStorePagerAdapter
import kotlinx.coroutines.launch
import java.util.Locale

class PirateStoreFragment : Fragment() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager: ViewPager2
    private lateinit var balanceTextView: TextView
    private lateinit var avatarImageView: ImageView
    private lateinit var pirateNameTextView: TextView
    private lateinit var toolbar: Toolbar
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pirate_store, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()

        tabLayout = view.findViewById(R.id.tab_layout)
        viewPager = view.findViewById(R.id.view_pager)
        balanceTextView = view.findViewById(R.id.tv_user_balance)
        avatarImageView = view.findViewById(R.id.iv_pirate_avatar)
        pirateNameTextView = view.findViewById(R.id.tv_pirate_name)
        toolbar = view.findViewById(R.id.toolbar)

        setupToolbar()
        setupViewPager()
        loadMerchantData()
        listenForBalance()
    }

    private fun setupToolbar() {
        toolbar.setNavigationOnClickListener {
            findNavController().navigateUp()
        }
    }

    private fun setupViewPager() {
        val adapter = PirateStorePagerAdapter(this)
        viewPager.adapter = adapter

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = when (position) {
                0 -> "BUY ITEMS"
                1 -> "SELL ORE"
                else -> ""
            }
        }.attach()
    }

    private fun loadMerchantData() {
        // Load default placeholder immediately
        lifecycleScope.launch {
            val placeholder = AlienAvatarGenerator.generate("captain-harlock-avatar-seed-pirate", 256, 256)
            avatarImageView.setImageBitmap(placeholder)
        }

        val merchantRef = database.getReference("merchants/captain_silas")
        merchantRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val name = snapshot.child("merchant_name").getValue(String::class.java)
                val ship = snapshot.child("ship_name").getValue(String::class.java)
                val avatarSeed = snapshot.child("avatar_seed").getValue(String::class.java)

                pirateNameTextView.text = name ?: "Captain Harlock"
                toolbar.title = ship ?: "The Star Nomad"

                if (!avatarSeed.isNullOrEmpty()) {
                    lifecycleScope.launch {
                        val avatar = AlienAvatarGenerator.generate(avatarSeed, 256, 256)
                        avatarImageView.setImageBitmap(avatar)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun listenForBalance() {
        val userId = auth.currentUser?.uid ?: return
        val creditsRef = database.getReference("users/$userId/inventory_data/credits")

        creditsRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val credits = snapshot.getValue(Long::class.java) ?: 0L
                updateBalanceText(credits)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateBalanceText(credits: Long) {
        val label = "Balance: "
        val value = "${formatNumber(credits)} Credits"
        val fullText = label + value
        val spannable = SpannableString(fullText)

        val cyanColor = ContextCompat.getColor(requireContext(), R.color.neon_cyan)
        val whiteColor = ContextCompat.getColor(requireContext(), R.color.white)

        // "Balance: " -> Cyan
        spannable.setSpan(ForegroundColorSpan(cyanColor), 0, label.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
        // "X Credits" -> White
        spannable.setSpan(ForegroundColorSpan(whiteColor), label.length, fullText.length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)

        balanceTextView.text = spannable
    }

    private fun formatNumber(value: Long): String {
        val v = value.toDouble()
        if (v < 1000) return "%.0f".format(v)
        val suffixes = arrayOf("", "k", "M", "B", "T")
        val exp = (Math.log10(v) / 3).toInt().coerceIn(0, suffixes.size - 1)
        return "%.1f%s".format(v / Math.pow(1000.0, exp.toDouble()), suffixes[exp])
    }
}