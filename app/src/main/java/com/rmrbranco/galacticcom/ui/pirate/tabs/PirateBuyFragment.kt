package com.rmrbranco.galacticcom.ui.pirate.tabs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.R
import com.rmrbranco.galacticcom.data.model.MerchantItem
import com.rmrbranco.galacticcom.ui.pirate.PirateStoreAdapter

class PirateBuyFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: PirateStoreAdapter
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private val inventoryItems = mutableListOf<MerchantItem>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_list_only, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()
        recyclerView = view.findViewById(R.id.recycler_view)

        setupRecyclerView()
        fetchMerchantData()
    }

    private fun setupRecyclerView() {
        // Reusing the existing PirateStoreAdapter
        adapter = PirateStoreAdapter(inventoryItems) { item ->
            handleBuyItem(item)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun fetchMerchantData() {
        val merchantRef = database.getReference("merchants/captain_silas/inventory")
        merchantRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                inventoryItems.clear()
                for (itemSnapshot in snapshot.children) {
                    val item = itemSnapshot.getValue(MerchantItem::class.java)
                    if (item != null) {
                        inventoryItems.add(item)
                    }
                }
                adapter.notifyDataSetChanged()
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun handleBuyItem(item: MerchantItem) {
        val userId = auth.currentUser?.uid ?: return
        // CHANGE: Check 'inventory_data/credits' instead of 'experiencePoints'
        val creditsRef = database.getReference("users/$userId/inventory_data/credits")
        
        creditsRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val userCredits = snapshot.getValue(Long::class.java) ?: 0L
                // Assuming item.price is now in Credits (or conversion rate 1:1 for simplicity initially)
                if (userCredits >= item.price) {
                    showConfirmationDialog(item, userCredits)
                } else {
                    Toast.makeText(context, "Not enough Credits. You have ${formatNumber(userCredits)}.", Toast.LENGTH_SHORT).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun showConfirmationDialog(item: MerchantItem, currentCredits: Long) {
        if (context == null) return
        val dialog = Dialog(requireContext()).apply {
            setContentView(R.layout.dialog_custom)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            findViewById<TextView>(R.id.dialog_title).text = "Confirm Purchase"
            findViewById<TextView>(R.id.dialog_message).text = "Buy ${item.itemName} for ${formatNumber(item.price.toLong())} Credits?"
            findViewById<View>(R.id.galaxy_stats_layout).isGone = true
            findViewById<View>(R.id.dialog_subtitle).isGone = true
            
            val negBtn = findViewById<Button>(R.id.dialog_negative_button)
            val posBtn = findViewById<Button>(R.id.dialog_positive_button)
            negBtn.text = "Cancel"
            negBtn.setOnClickListener { dismiss() }
            posBtn.text = "Buy"
            posBtn.setOnClickListener { 
                purchaseItem(item, currentCredits)
                dismiss() 
            }
            show()
            window?.setLayout((resources.displayMetrics.widthPixels * 0.90).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun purchaseItem(item: MerchantItem, currentCredits: Long) {
        val userId = auth.currentUser?.uid ?: return
        val userRef = database.getReference("users/$userId")
        
        val newCredits = currentCredits - item.price
        
        // Transaction to ensure atomicity
        userRef.child("inventory_data/credits").setValue(newCredits).addOnSuccessListener {
            // Deliver Item
            when (item.itemId) {
                "item_001" -> userRef.child("inventory/hyperwave_booster").setValue(ServerValue.increment(1))
                "item_002" -> userRef.child("inventory/private_messages").setValue(ServerValue.increment(250))
                "item_003" -> userRef.child("inventory/daily_licenses").setValue(ServerValue.increment(10))
                "item_004" -> userRef.child("inventory/avatar_resets").setValue(ServerValue.increment(1))
                "item_005" -> userRef.child("emblems/lone_traveler").setValue(true)
            }
            Toast.makeText(context, "Purchase successful!", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Formatting Helper
    private fun formatNumber(value: Long): String {
        val v = value.toDouble()
        if (v < 1000) return "%.0f".format(v)
        val suffixes = arrayOf("", "k", "M", "B", "T")
        val exp = (Math.log10(v) / 3).toInt().coerceIn(0, suffixes.size - 1)
        return "%.1f%s".format(v / Math.pow(1000.0, exp.toDouble()), suffixes[exp])
    }
}