package com.rmrbranco.galacticcom.ui.pirate

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rmrbranco.galacticcom.R
import com.rmrbranco.galacticcom.data.model.MerchantItem
import java.util.Locale

class PirateStoreAdapter(
    private val items: List<MerchantItem>,
    private val onBuyClicked: (MerchantItem) -> Unit
) : RecyclerView.Adapter<PirateStoreAdapter.ItemViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_merchant, parent, false)
        return ItemViewHolder(view)
    }

    override fun onBindViewHolder(holder: ItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, onBuyClicked)
    }

    override fun getItemCount(): Int = items.size

    class ItemViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.item_name)
        private val descriptionTextView: TextView = itemView.findViewById(R.id.item_description)
        private val priceTextView: TextView = itemView.findViewById(R.id.item_price)
        private val buyButton: Button = itemView.findViewById(R.id.buy_button)

        fun bind(item: MerchantItem, onBuyClicked: (MerchantItem) -> Unit) {
            nameTextView.text = item.itemName
            descriptionTextView.text = item.description
            priceTextView.text = "${formatNumber(item.price.toLong())} Credits"
            buyButton.setOnClickListener { onBuyClicked(item) }
        }

        private fun formatNumber(value: Long): String {
            val v = value.toDouble()
            if (v < 1000) return "%.0f".format(v)
            val suffixes = arrayOf("", "k", "M", "B", "T")
            val exp = (Math.log10(v) / 3).toInt().coerceIn(0, suffixes.size - 1)
            return "%.1f%s".format(v / Math.pow(1000.0, exp.toDouble()), suffixes[exp])
        }
    }
}