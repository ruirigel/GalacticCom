package com.rmrbranco.galacticcom.ui.pirate.tabs

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter

class PirateStorePagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> PirateBuyFragment()
            1 -> PirateSellFragment()
            else -> PirateBuyFragment()
        }
    }
}