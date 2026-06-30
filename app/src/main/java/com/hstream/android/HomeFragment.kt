package com.hstream.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class HomeFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        
        val tabLayout: TabLayout = view.findViewById(R.id.tabLayoutHome)
        val viewPager: ViewPager2 = view.findViewById(R.id.viewPagerHome)
        
        val tabs = listOf(
            Pair("Recién Lanzados", "recently-released"),
            Pair("Recién Subidos", "recently-uploaded"),
            Pair("Tendencias", "trending")
        )
        
        viewPager.adapter = object : FragmentStateAdapter(this) {
            override fun getItemCount(): Int = tabs.size
            override fun createFragment(position: Int): Fragment {
                return CatalogFragment.newInstance(tabs[position].second)
            }
        }
        
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = tabs[position].first
        }.attach()
        
        // Cambiar título del ActionBar al deslizar
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                (requireActivity() as? AppCompatActivity)?.supportActionBar?.title = tabs[position].first
            }
        })

        return view
    }
}
