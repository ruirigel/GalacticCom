package com.rmrbranco.galacticcom

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> HomeFragment()
            1 -> GalaxyDirectoryFragment()
            2 -> ComposeDeepSpaceFragment()
            3 -> InboxFragment()
            4 -> SettingsFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
    }
}
