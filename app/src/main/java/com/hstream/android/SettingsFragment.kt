package com.hstream.android

import android.content.Context
import android.content.Intent
import android.net.Uri
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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val spinner: Spinner = view.findViewById(R.id.spinnerPlayer)

        val pm = requireContext().packageManager
        val queryIntent = Intent(Intent.ACTION_VIEW)
        queryIntent.setDataAndType(Uri.parse("http://example.com/video.mp4"), "video/*")
        val resolveInfos = pm.queryIntentActivities(queryIntent, 0)

        val dynamicPlayers = mutableListOf<Pair<String, String>>()
        dynamicPlayers.add(Pair("Preguntar Siempre", ""))

        for (info in resolveInfos) {
            val appName = info.loadLabel(pm).toString()
            val packageName = info.activityInfo.packageName
            if (dynamicPlayers.none { it.second == packageName }) {
                dynamicPlayers.add(Pair(appName, packageName))
            }
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            dynamicPlayers.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val savedPackage = prefs.getString("default_player", "")
        
        val selectedIndex = dynamicPlayers.indexOfFirst { it.second == savedPackage }.takeIf { it >= 0 } ?: 0
        spinner.setSelection(selectedIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPackage = dynamicPlayers[position].second
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
