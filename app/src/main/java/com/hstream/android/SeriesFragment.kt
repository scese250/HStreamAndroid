package com.hstream.android

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.jsoup.Jsoup

class SeriesFragment : Fragment() {

    private lateinit var adapter: EpisodeAdapter
    private var seriesUrl: String = ""

    companion object {
        fun newInstance(url: String): SeriesFragment {
            val fragment = SeriesFragment()
            val args = Bundle()
            args.putString("url", url)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        seriesUrl = arguments?.getString("url") ?: ""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_series, container, false)
        
        val recycler: RecyclerView = view.findViewById(R.id.recyclerSeriesEpisodes)
        adapter = EpisodeAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).playVideo(url)
        }
        recycler.layoutManager = LinearLayoutManager(requireContext())
        recycler.adapter = adapter
        
        loadSeriesData(view)
        
        return view
    }
    
    private fun loadSeriesData(view: View) {
        if (seriesUrl.isEmpty()) return
        
        val imgPoster: ImageView = view.findViewById(R.id.imgSeriesPoster)
        val txtTitle: TextView = view.findViewById(R.id.txtSeriesTitle)
        val txtDate: TextView = view.findViewById(R.id.txtSeriesDate)
        val txtStudio: TextView = view.findViewById(R.id.txtSeriesStudio)
        val txtTags: TextView = view.findViewById(R.id.txtSeriesTags)
        val txtSynopsis: TextView = view.findViewById(R.id.txtSeriesSynopsis)
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = (requireActivity() as MainActivity).client
                val request = Request.Builder()
                    .url(seriesUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                    
                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: throw Exception("Empty body")
                val doc = Jsoup.parse(html)
                
                // Extract Title
                val title = doc.selectFirst("h1")?.text() ?: doc.title()
                
                // Extract Poster
                var posterUrl = doc.selectFirst(".md\\:w-1\\/4 img")?.attr("src") ?: ""
                if (posterUrl.isEmpty()) {
                    posterUrl = doc.selectFirst("img[alt*=$title]")?.attr("src") ?: ""
                }
                if (posterUrl.isEmpty()) {
                    posterUrl = doc.selectFirst("img")?.attr("src") ?: ""
                }
                if (posterUrl.startsWith("/")) posterUrl = "https://hstream.moe$posterUrl"
                
                // Extract Date
                val releaseDate = doc.selectFirst("i.fa-calendar")?.parent()?.text()?.trim() ?: ""
                
                // Extract Studio
                val studioLink = doc.selectFirst("a[href*=studios%5B0%5D]")
                val studioName = studioLink?.text()?.trim() ?: ""
                // El href es tipo /search?order=recently-uploaded&studios[0]=poro
                val studioUrl = studioLink?.attr("href") ?: ""
                val studioId = Regex("studios%5B0%5D=([^&]+)").find(studioUrl)?.groupValues?.get(1)
                
                // Extract Synopsis
                var synopsis = doc.selectFirst("meta[property=og:description]")?.attr("content") ?: ""
                if (synopsis.isEmpty()) {
                    synopsis = doc.selectFirst("meta[name=description]")?.attr("content") ?: ""
                }
                if (synopsis.isEmpty()) {
                    synopsis = doc.select(".mt-8 p").joinToString("\n\n") { it.text() }
                }
                
                // Extract Tags
                val tags = doc.select("a[href*=tags%5B0%5D]").joinToString(" • ") { it.text() }
                
                // Extract Servers
                val servers = mutableListOf<Pair<String, String>>()
                val iframes = doc.select("iframe")
                iframes.forEachIndexed { index, iframe ->
                    val src = iframe.attr("src")
                    if (src.isNotEmpty()) {
                        servers.add(Pair("IFrame ${index + 1}", src))
                    }
                }
                
                val serverBtns = doc.select(".server-btn, [data-server], [data-src]")
                serverBtns.forEach { btn ->
                    val src = btn.attr("data-src").ifEmpty { btn.attr("data-server") }
                    val name = btn.text().trim().ifEmpty { "Server ${servers.size + 1}" }
                    if (src.isNotEmpty() && !src.endsWith(".jpg") && !src.endsWith(".png") && !src.endsWith(".webp") && btn.tagName() != "img") {
                        if (servers.none { it.second == src }) {
                            servers.add(Pair(name, src))
                        }
                    }
                }

                // Extract Episodes
                val newItems = mutableListOf<VideoItem>()
                val seenUrls = mutableSetOf<String>()
                val episodeLinks = doc.select("a[href^=/hentai/], a[href^=https://hstream.moe/hentai/]")
                
                val episodeRegex = Regex("^${Regex.escape(seriesUrl)}-\\d+$")
                
                for (link in episodeLinks) {
                    var itemUrl = link.attr("href")
                    if (itemUrl.startsWith("/")) itemUrl = "https://hstream.moe$itemUrl"
                    
                    if (seenUrls.contains(itemUrl)) continue
                    if (!itemUrl.matches(episodeRegex)) continue
                    seenUrls.add(itemUrl)
                    
                    val img = link.selectFirst("img") ?: continue
                    var epPosterUrl = img.attr("data-src").ifEmpty { img.attr("src") }
                    if (epPosterUrl.isEmpty()) continue
                    if (epPosterUrl.startsWith("/")) epPosterUrl = "https://hstream.moe$epPosterUrl"
                    
                    val p = link.selectFirst("p")
                    val epTitle = p?.text() ?: img.attr("alt").ifEmpty { "Capítulo" }
                    
                    newItems.add(VideoItem(itemUrl, epTitle, epPosterUrl))
                }
                
                withContext(Dispatchers.Main) {
                    val webView: android.webkit.WebView = view.findViewById(R.id.webViewPlayer)
                    val layoutServer: View = view.findViewById(R.id.layoutServerSelector)
                    val spinner: android.widget.Spinner = view.findViewById(R.id.spinnerServers)
                    val imgBlur: ImageView = view.findViewById(R.id.imgSeriesBackgroundBlur)

                    if (servers.isNotEmpty()) {
                        webView.visibility = View.VISIBLE
                        layoutServer.visibility = View.VISIBLE
                        
                        webView.settings.javaScriptEnabled = true
                        webView.settings.domStorageEnabled = true
                        webView.webChromeClient = android.webkit.WebChromeClient()
                        webView.webViewClient = android.webkit.WebViewClient()
                        
                        val serverNames = servers.map { it.first }
                        val spinAdapter = android.widget.ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, serverNames)
                        spinAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                        spinner.adapter = spinAdapter
                        
                        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                            override fun onItemSelected(parent: android.widget.AdapterView<*>?, v: View?, position: Int, id: Long) {
                                val url = servers[position].second
                                webView.loadUrl(url)
                            }
                            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
                        }
                    } else {
                        webView.visibility = View.GONE
                        layoutServer.visibility = View.GONE
                    }

                    txtTitle.text = title
                    
                    if (releaseDate.isNotEmpty()) {
                        txtDate.text = "Release Date: $releaseDate"
                        txtDate.visibility = View.VISIBLE
                    } else {
                        txtDate.visibility = View.GONE
                    }
                    
                    if (studioName.isNotEmpty()) {
                        txtStudio.text = "$studioName ↗"
                        txtStudio.paintFlags = txtStudio.paintFlags or android.graphics.Paint.UNDERLINE_TEXT_FLAG
                        txtStudio.visibility = View.VISIBLE
                        txtStudio.setOnClickListener {
                            if (studioId != null) {
                                val fragment = SearchFragment.newInstance(studioId)
                                parentFragmentManager.beginTransaction()
                                    .replace(R.id.fragmentContainer, fragment)
                                    .addToBackStack(null)
                                    .commit()
                            }
                        }
                    } else {
                        txtStudio.visibility = View.GONE
                    }
                    
                    txtTags.text = tags
                    txtSynopsis.text = synopsis
                    imgPoster.load(posterUrl) {
                        crossfade(true)
                    }
                    imgBlur.load(posterUrl) {
                        crossfade(true)
                        transformations(BlurTransformation(requireContext(), 20f))
                    }
                    adapter.addItems(newItems)
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error loading series: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

class BlurTransformation(private val context: Context, private val radius: Float = 10f) : coil.transform.Transformation {
    override val cacheKey: String = "blur-$radius"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val rs = android.renderscript.RenderScript.create(context)
        val bitmapAlloc = android.renderscript.Allocation.createFromBitmap(rs, input)
        val blurScript = android.renderscript.ScriptIntrinsicBlur.create(rs, android.renderscript.Element.U8_4(rs))
        blurScript.setRadius(radius.coerceIn(0f, 25f))
        blurScript.setInput(bitmapAlloc)
        val outAlloc = android.renderscript.Allocation.createTyped(rs, bitmapAlloc.type)
        blurScript.forEach(outAlloc)
        val outBitmap = android.graphics.Bitmap.createBitmap(input.width, input.height, input.config)
        outAlloc.copyTo(outBitmap)
        rs.destroy()
        return outBitmap
    }
}
