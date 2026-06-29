package com.hstream.android

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.Switch
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment() {

    private val players = listOf(
        Pair("Preguntar Siempre", ""),
        Pair("VLC", "org.videolan.vlc"),
        Pair("MX Player", "com.mxtech.videoplayer.ad"),
        Pair("MX Player Pro", "com.mxtech.videoplayer.pro"),
        Pair("Just Player", "com.brouken.player")
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val spinner: Spinner = view.findViewById(R.id.spinnerPlayer)

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            players.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val savedPackage = prefs.getString("default_player", "")
        
        val selectedIndex = players.indexOfFirst { it.second == savedPackage }.takeIf { it >= 0 } ?: 0
        spinner.setSelection(selectedIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPackage = players[position].second
                prefs.edit().putString("default_player", selectedPackage).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val switchPrivacy: Switch = view.findViewById(R.id.switchPrivacy)
        switchPrivacy.isChecked = prefs.getBoolean("privacy_lock", false)
        switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("privacy_lock", isChecked).apply()
        }

        return view
    }
}
