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

class CatalogFragment : Fragment() {

    private lateinit var adapter: VideoAdapter
    private lateinit var progressBar: ProgressBar
    private var currentPage = 1
    private var isLoading = false
    private var type: String = "recently-released"

    private val isHomeTab: Boolean
        get() = type in HOME_TABS

    private val client: OkHttpClient
        get() = (requireActivity() as MainActivity).client

    companion object {
        // Recently Released y Trending pre-renderizan sus listas en la home
        // (hstream.moe/#tabs-<id>) con orden cronológico correcto; /search es
        // poco fiable para "order=recently-released".
        private val HOME_TABS = setOf("recently-released", "trending")

        fun newInstance(type: String): CatalogFragment {
            val fragment = CatalogFragment()
            val args = Bundle()
            args.putString("type", type)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_catalog, container, false)

        type = arguments?.getString("type") ?: "recently-released"

        // Resolver Bundle de estado: backstack pop (HomeFragment preservado, CatalogFragment
        // también) -> savedInstanceState; drawer nav desde Home (HomeFragment destruido y los
        // CatalogFragment hijos también) -> cache de MainActivity con clave estable por type.
        val stateBundle: Bundle? = savedInstanceState
            ?: (activity as? MainActivity)?.consumeTabState("catalog:$type")

        // Restaurar currentPage antes de cualquier loadCatalog() / addItems().
        if (stateBundle != null) {
            currentPage = stateBundle.getInt("currentPage", 1)
        }

        progressBar = view.findViewById(R.id.progressCatalog)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerCatalog)

        adapter = VideoAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).handleVideoClick(url)
        }

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 4 else 2
        val layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        if (stateBundle != null) {
            // Re-aplicar items cargados y scroll DESPUÉS de atar el adapter y ANTES de añadir
            // el scroll listener que dispara paginación. Si no hay items, no navegamos hacia arriba.
            stateBundle.getParcelableArrayList<VideoItem>("videoItems")?.let { adapter.addItems(it) }
            @Suppress("DEPRECATION")
            stateBundle.getParcelable<android.os.Parcelable>("recyclerState")?.let {
                layoutManager.onRestoreInstanceState(it)
            }
        }

        if (!isHomeTab) {
            recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    if (dy > 0) {
                        val visibleItemCount = layoutManager.childCount
                        val totalItemCount = layoutManager.itemCount
                        val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                        if (!isLoading && (visibleItemCount + pastVisibleItems) >= totalItemCount) {
                            currentPage++
                            loadCatalog()
                        }
                    }
                }
            })
        }

        if (stateBundle == null) {
            loadCatalog()
        }

        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentPage", currentPage)
        view?.let { v ->
            v.findViewById<RecyclerView>(R.id.recyclerCatalog).layoutManager?.let {
                outState.putParcelable("recyclerState", it.onSaveInstanceState())
            }
        }
        if (this::adapter.isInitialized) {
            outState.putParcelableArrayList("videoItems", ArrayList(adapter.getItems()))
        }

        // Bridge de drawer: si HomeFragment se destruye por navegación del drawer, sus
        // CatalogFragment hijos también pasan por onSaveInstanceState y dejan aquí su
        // snapshot por si el usuario vuelve a Home.
        (activity as? MainActivity)?.cacheTabState("catalog:$type", Bundle(outState))
    }

    override fun onDestroyView() {
        // Safety net para el cache: onDestroyView SIEMPRE dispara cuando la view se va,
        // incluso en fragment-transactions sin backstack donde onSaveInstanceState puede no
        // invocarse. Idempotente con la copia en onSaveInstanceState.
        view?.let { v ->
            val cacheBundle = Bundle()
            cacheBundle.putInt("currentPage", currentPage)
            v.findViewById<RecyclerView>(R.id.recyclerCatalog).layoutManager?.let {
                cacheBundle.putParcelable("recyclerState", it.onSaveInstanceState())
            }
            if (this::adapter.isInitialized) {
                cacheBundle.putParcelableArrayList("videoItems", ArrayList(adapter.getItems()))
            }
            (activity as? MainActivity)?.cacheTabState("catalog:$type", cacheBundle)
        }
        super.onDestroyView()
    }

    private fun loadCatalog() {
        isLoading = true
        if (currentPage == 1) progressBar.visibility = View.VISIBLE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val url = if (isHomeTab) {
                    "https://hstream.moe/"
                } else {
                    "https://hstream.moe/search?order=$type&page=$currentPage"
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: throw Exception("Empty body")

                val doc = Jsoup.parse(html)

                val links = if (isHomeTab) {
                    doc.select("#tabs-$type a[href^=/hentai/], #tabs-$type a[href^=https://hstream.moe/hentai/]")
                } else {
                    doc.select("a[href^=/hentai/], a[href^=https://hstream.moe/hentai/]")
                }

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
                    if (currentPage == 1) progressBar.visibility = View.GONE
                    adapter.addItems(newItems)
                    isLoading = false
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (currentPage == 1) progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }
        }
    }
}
