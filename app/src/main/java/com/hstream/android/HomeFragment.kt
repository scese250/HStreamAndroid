package com.hstream.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class HomeFragment : Fragment() {

    private lateinit var adapter: VideoAdapter
    private lateinit var progressBar: ProgressBar
    private var currentPage = 1
    private var isLoading = false
    
    // El cliente OkHttp ahora se pasará desde la actividad, o se creará aquí si es necesario.
    // Para simplificar, obtenemos la instancia desde MainActivity.
    private val client: OkHttpClient
        get() = (requireActivity() as MainActivity).client

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        progressBar = view.findViewById(R.id.progressBar)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerView)
        
        adapter = VideoAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).handleVideoClick(url)
        }

        val layoutManager = GridLayoutManager(context, 2)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && (visibleItemCount + pastVisibleItems) >= totalItemCount) {
                        currentPage++
                        fetchCatalogPage(currentPage)
                    }
                }
            }
        })

        // Initial load
        if (adapter.itemCount == 0) {
            fetchCatalogPage(currentPage)
        }

        return view
    }

    private fun fetchCatalogPage(page: Int) {
        isLoading = true
        if (page == 1) progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = "https://hstream.moe/search?view=poster&order=recently-released&page=$page"
                val request = Request.Builder()
                    .url(url)
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
                    
                    val p = link.selectFirst("p")
                    val title = p?.text() ?: img.attr("alt").ifEmpty { "Desconocido" }
                    
                    newItems.add(VideoItem(itemUrl, title, posterUrl))
                }

                withContext(Dispatchers.Main) {
                    if (page == 1) progressBar.visibility = View.GONE
                    adapter.addItems(newItems)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (page == 1) progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error página $page: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }
        }
    }
}
