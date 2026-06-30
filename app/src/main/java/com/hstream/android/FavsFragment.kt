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
import okhttp3.MediaType.Companion.toMediaTypeOrNull

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
            (requireActivity() as MainActivity).handleVideoClick(url) {
                removeFavorite(url)
            }
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

    private fun removeFavorite(url: String) {
        val mainActivity = requireActivity() as MainActivity
        progressBar.visibility = View.VISIBLE
        Toast.makeText(context, "Removing from favorites...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val getRequest = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                
                val response = mainActivity.client.newCall(getRequest).execute()
                val html = response.body?.string() ?: throw Exception("Empty body")
                val doc = Jsoup.parse(html)
                
                val scriptTag = doc.selectFirst("script[data-csrf]")
                val csrfToken = scriptTag?.attr("data-csrf") ?: throw Exception("CSRF token not found")
                
                val pattern = Regex("""wire:snapshot="([^"]+)"""")
                val matches = pattern.findAll(html)
                var snapshot = ""
                for (match in matches) {
                    val content = match.groupValues[1]
                    val decoded = android.text.Html.fromHtml(content, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                    if (decoded.contains("like-button") || decoded.contains("episodeId")) {
                        snapshot = decoded
                        break
                    }
                }
                
                if (snapshot.isEmpty()) throw Exception("Like component snapshot not found")
                
                val payload = org.json.JSONObject()
                payload.put("_token", csrfToken)
                
                val component = org.json.JSONObject()
                component.put("snapshot", snapshot)
                component.put("updates", org.json.JSONObject())
                
                val call = org.json.JSONObject()
                call.put("path", "")
                call.put("method", "like")
                call.put("params", org.json.JSONArray())
                
                val callsArray = org.json.JSONArray()
                callsArray.put(call)
                
                component.put("calls", callsArray)
                
                val componentsArray = org.json.JSONArray()
                componentsArray.put(component)
                
                payload.put("components", componentsArray)
                
                val jsonPayload = payload.toString()
                
                val mediaType = "application/json".toMediaTypeOrNull()
                val body = if (mediaType != null) {
                    okhttp3.RequestBody.create(mediaType, jsonPayload)
                } else {
                    okhttp3.RequestBody.create(null, jsonPayload)
                }
                val postRequest = Request.Builder()
                    .url("https://hstream.moe/livewire/update")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("X-CSRF-TOKEN", csrfToken)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("Content-Type", "application/json")
                    .post(body)
                    .build()
                    
                val postResponse = mainActivity.client.newCall(postRequest).execute()
                if (!postResponse.isSuccessful) {
                    throw Exception("Failed to remove favorite: ${postResponse.code}")
                }
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Removed from favorites", Toast.LENGTH_SHORT).show()
                    loadFavs()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error removing favorite: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
