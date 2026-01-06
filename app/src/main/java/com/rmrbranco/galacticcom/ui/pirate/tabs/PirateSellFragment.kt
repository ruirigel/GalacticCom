package com.rmrbranco.galacticcom.ui.pirate.tabs

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.rmrbranco.galacticcom.R
import com.rmrbranco.galacticcom.data.model.UserInventory
import java.util.Locale

class PirateSellFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SellAdapter
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private var userInventory: UserInventory? = null
    
    // Listener reference for cleanup
    private var inventoryListener: ValueEventListener? = null
    private var inventoryRef: DatabaseReference? = null

    // Base prices for resources
    private val resourcePrices = mapOf(
        "iron_ore" to 5,
        "gold_dust" to 15,
        "plasma_crystal" to 50,
        "dark_matter" to 500
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_list_only, container, false) // Generic layout with just a RecyclerView
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        database = FirebaseDatabase.getInstance()
        auth = FirebaseAuth.getInstance()
        recyclerView = view.findViewById(R.id.recycler_view) 

        setupRecyclerView()
        listenForInventory()
    }

    private fun setupRecyclerView() {
        adapter = SellAdapter { resourceName, quantity, totalValue ->
            sellResource(resourceName, quantity, totalValue)
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }

    private fun listenForInventory() {
        val userId = auth.currentUser?.uid ?: return
        inventoryRef = database.getReference("users/$userId/inventory_data")

        inventoryListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                userInventory = snapshot.getValue(UserInventory::class.java)
                updateList()
            }
            override fun onCancelled(error: DatabaseError) {}
        }
        inventoryRef?.addValueEventListener(inventoryListener!!)
    }

    private fun updateList() {
        val resources = userInventory?.resources ?: return
        val list = resources.map { entry ->
            SellItem(
                name = entry.key,
                quantity = entry.value,
                pricePerUnit = resourcePrices[entry.key] ?: 1
            )
        }.filter { it.quantity > 0 } // Only show what user has
        
        adapter.submitList(list)
    }

    private fun sellResource(resourceName: String, quantity: Int, totalValue: Int) {
        val userId = auth.currentUser?.uid ?: return
        val inventoryRef = database.getReference("users/$userId/inventory_data")

        inventoryRef.runTransaction(object : Transaction.Handler {
            override fun doTransaction(currentData: MutableData): Transaction.Result {
                val inventory = currentData.getValue(UserInventory::class.java) ?: return Transaction.success(currentData)
                
                val currentQty = inventory.resources[resourceName] ?: 0
                if (currentQty >= quantity) {
                    // Deduct resource
                    inventory.resources[resourceName] = currentQty - quantity
                    // Add credits
                    inventory.credits += totalValue
                    currentData.setValue(inventory)
                    return Transaction.success(currentData)
                } else {
                    return Transaction.abort()
                }
            }

            override fun onComplete(error: DatabaseError?, committed: Boolean, currentData: DataSnapshot?) {
                if (committed) {
                    Toast.makeText(context, "Sold for ${formatNumber(totalValue.toLong())} Credits!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Transaction failed.", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }
    
    // Formatting Helper
    private fun formatNumber(value: Long): String {
        val v = value.toDouble()
        if (v < 1000) return "%.0f".format(v)
        val suffixes = arrayOf("", "k", "M", "B", "T")
        val exp = (Math.log10(v) / 3).toInt().coerceIn(0, suffixes.size - 1)
        return "%.1f%s".format(v / Math.pow(1000.0, exp.toDouble()), suffixes[exp])
    }
    
    override fun onDestroyView() {
        super.onDestroyView()
        inventoryListener?.let { inventoryRef?.removeEventListener(it) }
    }

    // --- Inner Classes for Adapter ---
    data class SellItem(val name: String, val quantity: Int, val pricePerUnit: Int)

    inner class SellAdapter(private val onSellClick: (String, Int, Int) -> Unit) : RecyclerView.Adapter<SellAdapter.ViewHolder>() {
        private var items: List<SellItem> = emptyList()

        fun submitList(newItems: List<SellItem>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pirate_sell, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = items[position]
            holder.bind(item)
        }

        override fun getItemCount() = items.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val nameTv: TextView = itemView.findViewById(R.id.tv_resource_name)
            private val qtyTv: TextView = itemView.findViewById(R.id.tv_resource_quantity)
            private val priceTv: TextView = itemView.findViewById(R.id.tv_sell_price)
            private val sellBtn: Button = itemView.findViewById(R.id.btn_sell)

            fun bind(item: SellItem) {
                val displayName = item.name.replace("_", " ").replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                nameTv.text = displayName
                qtyTv.text = "Owned: ${formatNumber(item.quantity.toLong())}"
                priceTv.text = "${formatNumber(item.pricePerUnit.toLong())} Credits/unit."
                
                val totalValue = item.quantity * item.pricePerUnit
                sellBtn.text = "Sell All (${formatNumber(totalValue.toLong())})"
                
                sellBtn.setOnClickListener {
                    onSellClick(item.name, item.quantity, totalValue)
                }
            }
        }
    }
}