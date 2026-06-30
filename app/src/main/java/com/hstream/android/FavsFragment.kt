package com.hstream.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class FavsFragment : Fragment() {
    
    private lateinit var adapter: VideoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var layoutNotLoggedIn: LinearLayout
    private lateinit var txtEmptyFavs: TextView
    private lateinit var recyclerViewFavs: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_favs, container, false)
        
        progressBar = view.findViewById(R.id.progressBarFavs)
        layoutNotLoggedIn = view.findViewById(R.id.layoutNotLoggedIn)
        txtEmptyFavs = view.findViewById(R.id.textFavsEmpty)
        recyclerViewFavs = view.findViewById(R.id.recyclerViewFavs)
        val btnFavsLogin = view.findViewById<Button>(R.id.btnFavsLogin)
        
        adapter = VideoAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).handleVideoClick(url)
        }
        recyclerViewFavs.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerViewFavs.adapter = adapter
        
        btnFavsLogin.setOnClickListener {
            startActivity(Intent(requireContext(), LoginActivity::class.java))
        }

        checkLoginAndLoadFavs()

        return view
    }
    
    private fun checkLoginAndLoadFavs() {
        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        
        if (isLoggedIn) {
            layoutNotLoggedIn.visibility = View.GONE
            recyclerViewFavs.visibility = View.VISIBLE
            loadFavs()
        } else {
            layoutNotLoggedIn.visibility = View.VISIBLE
            recyclerViewFavs.visibility = View.GONE
        }
    }
    
    private fun loadFavs() {
        progressBar.visibility = View.VISIBLE
        txtEmptyFavs.visibility = View.GONE
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = (requireActivity() as MainActivity).client
                val request = Request.Builder()
                    .url("https://hstream.moe/user/likes")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: throw Exception("Empty body")
                
                val doc = Jsoup.parse(html)
                val links = doc.select("a[href^=/hentai/], a[href^=https://hstream.moe/hentai/]")
                
                val newItems = mutableListOf<VideoItem>()
                val seenUrls = mutableSetOf<String>()

                for (link in links) {
                    var itemUrl = link.attr("href")
                    if (itemUrl.startsWith("/")) itemUrl = "https://hstream.moe$itemUrl"
                    
                    if (seenUrls.contains(itemUrl)) continue
                    seenUrls.add(itemUrl)
                    
                    val img = link.selectFirst("img") ?: continue
                    var posterUrl = img.attr("data-src").ifEmpty { img.attr("src") }
                    if (posterUrl.isEmpty()) continue
                    if (posterUrl.startsWith("/")) posterUrl = "https://hstream.moe$posterUrl"
                    
                    val title = img.attr("alt").ifEmpty { "Desconocido" }
                    
                    newItems.add(VideoItem(itemUrl, title, posterUrl))
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (newItems.isEmpty()) {
                        txtEmptyFavs.visibility = View.VISIBLE
                    } else {
                        adapter.clearItems()
                        adapter.addItems(newItems)
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(requireContext(), "Error loading favs: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
