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

        // activeTab: backstack pop -> savedInstanceState; drawer nav -> cache de MainActivity.
        // Si no hay nada guardado, arranca en 0 (Recently Released), mismo comportamiento que antes.
        val activeTab = when {
            savedInstanceState != null -> savedInstanceState.getInt("activeTab", 0)
            else -> (activity as? MainActivity)
                ?.consumeTabState("home:active_tab")
                ?.getInt("activeTab", 0) ?: 0
        }

        val tabLayout: TabLayout = view.findViewById(R.id.tabLayoutHome)
        val viewPager: ViewPager2 = view.findViewById(R.id.viewPagerHome)

        val tabs = listOf(
            Pair("Recently Released", "recently-released"),
            Pair("Recently Uploaded", "recently-uploaded"),
            Pair("Trending", "trending")
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

        // Restaurar pestaña activa sin animación. coerceIn protege contra índices inválidos
        // si el # de tabs cambió entre versiones.
        val clamped = activeTab.coerceIn(0, tabs.size - 1)
        if (viewPager.currentItem != clamped) {
            viewPager.setCurrentItem(clamped, false)
        }

        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        view?.let { v ->
            outState.putInt(
                "activeTab",
                v.findViewById<ViewPager2>(R.id.viewPagerHome).currentItem
            )
            // Bridge de drawer: solo cachear cuando tenemos la view con un currentItem válido.
            // Si la view es null no poluimos el cache con un bundle sin "activeTab".
            (activity as? MainActivity)?.cacheTabState("home:active_tab", Bundle(outState))
        }
    }
}
