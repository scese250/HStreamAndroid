package com.hstream.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class HistoryFragment : Fragment() {
    
    private lateinit var adapter: VideoAdapter
    private lateinit var txtEmptyHistory: TextView
    private lateinit var recyclerViewHistory: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history, container, false)
        
        txtEmptyHistory = view.findViewById(R.id.textHistoryEmpty)
        recyclerViewHistory = view.findViewById(R.id.recyclerViewHistory)
        
        adapter = VideoAdapter(mutableListOf()) { item ->
            (requireActivity() as MainActivity).handleVideoClick(
                item = item, 
                onRemoveFav = null, 
                onRemoveHistory = { removeHistory(item.url) }
            )
        }
        recyclerViewHistory.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerViewHistory.adapter = adapter
        
        loadHistory()
        
        return view
    }
    
    private fun loadHistory() {
        val historyList = HistoryManager.getHistory(requireContext())
        if (historyList.isEmpty()) {
            txtEmptyHistory.visibility = View.VISIBLE
            recyclerViewHistory.visibility = View.GONE
        } else {
            txtEmptyHistory.visibility = View.GONE
            recyclerViewHistory.visibility = View.VISIBLE
            adapter.updateItems(historyList)
        }
    }
    
    private fun removeHistory(url: String) {
        HistoryManager.removeHistory(requireContext(), url)
        loadHistory() // Refresh the list
    }
}
