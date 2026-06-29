package com.hstream.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLDecoder
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    private val client = OkHttpClient.Builder()
        .cookieJar(object : CookieJar {
            private val cookieStore = mutableMapOf<String, MutableList<Cookie>>()

            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                cookieStore[url.host] = cookies.toMutableList()
            }

            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: listOf()
            }
        })
        .build()

    private lateinit var adapter: VideoAdapter
    private lateinit var progressBar: ProgressBar
    private var currentPage = 1
    private var isLoading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        progressBar = findViewById(R.id.progressBar)
        val recyclerView: RecyclerView = findViewById(R.id.recyclerView)
        
        adapter = VideoAdapter(mutableListOf()) { url ->
            playVideo(url)
        }

        val layoutManager = GridLayoutManager(this, 2)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        // Scroll listener for pagination
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 0) { // Scrolling down
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
        fetchCatalogPage(currentPage)
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
                
                // Jsoup parsing
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
                    Toast.makeText(this@MainActivity, "Error página $page: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }
        }
    }

    private fun playVideo(url: String) {
        Toast.makeText(this, "Obteniendo stream...", Toast.LENGTH_SHORT).show()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. Obtener la página principal
                val request1 = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                
                val response1 = client.newCall(request1).execute()
                val html = response1.body?.string() ?: throw Exception("Empty body")
                
                // Extraer e_id
                val pattern = Pattern.compile("e_id\" type=\"hidden\" value=\"([^\"]*)")
                val matcher = pattern.matcher(html)
                if (!matcher.find()) {
                    throw Exception("No se encontró el e_id")
                }
                val eId = matcher.group(1)
                
                var xsrfToken = ""
                val host = url.toHttpUrlOrNull()?.host ?: "hstream.moe"
                val cookies = client.cookieJar.loadForRequest("https://hstream.moe".toHttpUrlOrNull()!!)
                for (cookie in cookies) {
                    if (cookie.name == "XSRF-TOKEN") {
                        xsrfToken = URLDecoder.decode(cookie.value, "UTF-8")
                        break
                    }
                }
                
                if (xsrfToken.isEmpty()) {
                    throw Exception("No se encontró XSRF-TOKEN")
                }

                // 2. Llamada a la API
                val jsonPayload = JSONObject().apply {
                    put("episode_id", eId)
                }.toString()
                
                val request2 = Request.Builder()
                    .url("https://hstream.moe/player/api")
                    .header("Referer", url)
                    .header("X-Requested-With", "XMLHttpRequest")
                    .header("X-Xsrf-Token", xsrfToken)
                    .post(jsonPayload.toRequestBody("application/json".toMediaType()))
                    .build()
                    
                val response2 = client.newCall(request2).execute()
                val jsonResponse = JSONObject(response2.body?.string() ?: "{}")
                
                val cdnDomain = jsonResponse.getJSONArray("stream_domains").getString(0)
                val streamUrl = jsonResponse.getString("stream_url")
                val mpdUrl = "$cdnDomain/$streamUrl/1080/manifest.mpd"
                
                val subPattern = Pattern.compile("href=\"([^\"]+\\.(?:ass|srt|vtt))\"")
                val subMatcher = subPattern.matcher(html)
                val subtitles = mutableListOf<Uri>()
                while (subMatcher.find()) {
                    var subUrl = subMatcher.group(1)!!
                    if (subUrl.startsWith("/")) subUrl = "https://hstream.moe$subUrl"
                    subtitles.add(Uri.parse(subUrl))
                }
                
                // 3. Lanzar intent al hilo principal
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(mpdUrl), "video/*")
                    
                    if (subtitles.isNotEmpty()) {
                        intent.putExtra("subs", subtitles.toTypedArray())
                    }
                    
                    startActivity(Intent.createChooser(intent, "Selecciona un reproductor (VLC, MX Player...)"))
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error al reproducir: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
