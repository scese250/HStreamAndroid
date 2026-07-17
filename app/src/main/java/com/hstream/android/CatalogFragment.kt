package com.hstream.android

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
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
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class CatalogFragment : Fragment() {

    private lateinit var adapter: VideoAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var pageControlBar: LinearLayout
    private lateinit var txtPageIndicator: TextView
    private lateinit var edtPageInput: EditText
    private lateinit var fabToggle: TextView
    private var currentPage = 1
    private var totalPages = 1
    private var isLoading = false
    private var type: String = "recently-released"
    // true después de cargar el panel del home. Mientras false, los tabs HYBRID usan el panel;
    // una vez true, las siguientes calls van a /search con dedupe por URL.
    private var hasLoadedPanel = false
    // Set vivo entre loadCatalog calls; items vistos (panel + /search paginación). Evita
    // duplicados visibles cuando el endpoint /search solapa con el panel.
    //
    // CAVEAT de tamaño: con deep-scroll (~1000+ items), este set se serializa en Bundle
    // vía putStringArrayList("seenUrls") tanto en savedInstanceState como en
    // cachedSearchBundle. Junto al ParcelableArrayList<VideoItem> puede acercarse al
    // límite de ~1MB del Binder transaction. Mitigación futura: truncar a últimas N
    // páginas o almacenar hashes en lugar de URLs completas.
    private val seenUrls = mutableSetOf<String>()

    private val isHybridTab: Boolean
        get() = type in HYBRID_TYPES

    private val isPanelOnly: Boolean
        get() = type in PANEL_ONLY

    private val client: OkHttpClient
        get() = (requireActivity() as MainActivity).client

    companion object {
        // HYBRID_TYPES: panel del home (orden canónico) + paginación desde /search con dedupe.
        // /search?order=recently-released estaba desincronizado con el home; el panel corrige
        // el orden de las primeras N series, y la paginación trae series más viejas sin duplicar.
        private val HYBRID_TYPES = setOf("recently-released", "recently-uploaded")

        // PANEL_ONLY: solo panel del home, sin paginación histórica. Trending no tiene histórico:
        // el ranking se mueve hacia abajo a medida que los videos envejecen.
        private val PANEL_ONLY = setOf("trending")

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

        // stateBundle: backstack pop (HomeFragment preservado -> savedInstanceState);
        // drawer nav desde Home (HomeFragment destruido -> cache en MainActivity).
        val stateBundle: Bundle? = savedInstanceState
            ?: (activity as? MainActivity)?.consumeTabState("catalog:$type")

        if (stateBundle != null) {
            currentPage = stateBundle.getInt("currentPage", 1)
            totalPages = stateBundle.getInt("totalPages", 1)
            hasLoadedPanel = stateBundle.getBoolean("hasLoadedPanel", false)
            stateBundle.getStringArrayList("seenUrls")?.let { seenUrls.addAll(it) }
        }

        progressBar = view.findViewById(R.id.progressCatalog)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerCatalog)
        pageControlBar = view.findViewById(R.id.pageControlBar)
        txtPageIndicator = view.findViewById(R.id.txtPageIndicator)
        edtPageInput = view.findViewById(R.id.edtPageInput)
        fabToggle = view.findViewById(R.id.fabToggle)

        // FAB sólo aparece en tabs con paginación propia (no en Trending).
        val pageOverlay: View = view.findViewById(R.id.pageOverlay)
        pageOverlay.visibility = if (isPanelOnly) View.GONE else View.VISIBLE

        adapter = VideoAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).handleVideoClick(url)
        }

        val isLandscape = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val spanCount = if (isLandscape) 4 else 2
        val layoutManager = GridLayoutManager(context, spanCount)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        if (stateBundle != null) {
            stateBundle.getParcelableArrayList<VideoItem>("videoItems")?.let { adapter.addItems(it) }
            @Suppress("DEPRECATION")
            stateBundle.getParcelable<android.os.Parcelable>("recyclerState")?.let {
                layoutManager.onRestoreInstanceState(it)
            }
        }

        // Scroll listener sólo en HYBRID: panel + paginación /search. PANEL_ONLY no tiene más.
        if (isHybridTab) {
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

        updatePageIndicatorView()

        // FAB click: toggle el desplegado del pageControlBar.
        fabToggle.setOnClickListener { togglePageOverlay() }

        // Enter en el EditText: navegar a la página tipeada.
        edtPageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                handlePageInputSubmit()
                true
            } else false
        }

        if (stateBundle == null) {
            loadCatalog()
        }

        return view
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("currentPage", currentPage)
        outState.putInt("totalPages", totalPages)
        outState.putBoolean("hasLoadedPanel", hasLoadedPanel)
        outState.putStringArrayList("seenUrls", ArrayList(seenUrls))
        view?.let { v ->
            v.findViewById<RecyclerView>(R.id.recyclerCatalog).layoutManager?.let {
                outState.putParcelable("recyclerState", it.onSaveInstanceState())
            }
        }
        if (this::adapter.isInitialized) {
            outState.putParcelableArrayList("videoItems", ArrayList(adapter.getItems()))
        }
        (activity as? MainActivity)?.cacheTabState("catalog:$type", Bundle(outState))
    }

    override fun onDestroyView() {
        // Safety net para el cache: onDestroyView SIEMPRE dispara cuando la view se va,
        // incluso en fragment-transactions sin backstack donde onSaveInstanceState puede no
        // invocarse. Idempotente con la copia en onSaveInstanceState.
        view?.let { v ->
            val cacheBundle = Bundle()
            cacheBundle.putInt("currentPage", currentPage)
            cacheBundle.putInt("totalPages", totalPages)
            cacheBundle.putBoolean("hasLoadedPanel", hasLoadedPanel)
            cacheBundle.putStringArrayList("seenUrls", ArrayList(seenUrls))
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
                val url: String
                val selector: String

                if (isHybridTab && !hasLoadedPanel) {
                    // Primera carga HYBRID: panel del home (orden canónico de las primeras N).
                    url = "https://hstream.moe/"
                    selector = "#tabs-$type a[href^=/hentai/], #tabs-$type a[href^=https://hstream.moe/hentai/]"
                } else if (isPanelOnly) {
                    // Trending: panel único, sin paginación.
                    url = "https://hstream.moe/"
                    selector = "#tabs-$type a[href^=/hentai/], #tabs-$type a[href^=https://hstream.moe/hentai/]"
                } else {
                    // /search para HYBRID después del panel (con dedupe) o futuros tabs sin panel.
                    url = "https://hstream.moe/search?order=$type&page=$currentPage"
                    selector = "a[href^=/hentai/], a[href^=https://hstream.moe/hentai/]"
                }

                val request = Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()

                val response = client.newCall(request).execute()
                val html = response.body?.string() ?: throw Exception("Empty body")
                val doc = Jsoup.parse(html)
                val links = doc.select(selector)

                // Parse total de páginas a partir de los anclas ?page=K del HTML (sólo
                // cuando el endpoint es /search — el panel del home no expone paginación).
                if (!isPanelOnly && hasLoadedPanel || !isHybridTab && !isPanelOnly) {
                    val pageTotal = parseTotalPages(doc)
                    if (pageTotal > totalPages) totalPages = pageTotal
                }

                // Dedupe cross-page: si el panel y /search comparten URLs, las saltamos.
                // seenUrls persiste entre loadCatalog calls (mismo MutableSet vivo).
                val newItems = mutableListOf<VideoItem>()
                val seenUrlsInThisFetch = mutableSetOf<String>()
                for (link in links) {
                    var itemUrl = link.attr("href")
                    if (itemUrl.startsWith("/")) itemUrl = "https://hstream.moe$itemUrl"

                    if (seenUrls.contains(itemUrl)) continue
                    if (!seenUrlsInThisFetch.add(itemUrl)) continue  // dedupe intra-page también
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

                    // Panel terminado — la próxima scroll-trigger incrementará currentPage a 2 y
                    // cargará /search?page=2. Saltamos page=1 a propósito: la mayoría de sus
                    // items solapan con el panel y el dedupe los filtraría igual.
                    if (isHybridTab && !hasLoadedPanel) {
                        hasLoadedPanel = true
                    }

                    if (newItems.isEmpty()) {
                        // HYBRID agotó /search. Toast informativo al usuario.
                        if (isHybridTab && hasLoadedPanel) {
                            Toast.makeText(context, "No more results", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        adapter.addItems(newItems)
                    }
                    updatePageIndicatorView()
                    isLoading = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    if (currentPage == 1) progressBar.visibility = View.GONE
                    Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    isLoading = false
                }
            }
        }
    }

    /**
     * Salto directo a una página específica. Limpia el set de dedupe y los items actuales
     * para que el usuario pueda hacer scroll-up sin ver duplicados viejos. Marca
     * hasLoadedPanel=true en HYBRID para que loadCatalog tome la ruta /search y no el panel.
     */
    private fun jumpToPage(page: Int) {
        currentPage = page
        seenUrls.clear()
        if (isHybridTab) hasLoadedPanel = true
        adapter.clearItems()
        isLoading = false
        loadCatalog()
    }

    private fun updatePageIndicatorView() {
        if (this::txtPageIndicator.isInitialized) {
            txtPageIndicator.text = "$currentPage / $totalPages"
        }
    }

    /**
     * Extrae el máximo número de página encontrando anclas ?page=K y/o texto "of N".
     * Usado sólo en respuestas /search; el panel del home no expone paginación.
     */
    private fun parseTotalPages(doc: Document): Int {
        var maxFound = 1
        for (a in doc.select("a[href]")) {
            val href = a.attr("href")
            val m = Regex("""[?&]page=(\d+)""").find(href)
            if (m != null) {
                val k = m.groupValues[1].toIntOrNull()
                if (k != null && k > maxFound) maxFound = k
            }
        }
        val ofTxt = Regex("""of\s+(\d+)""", RegexOption.IGNORE_CASE).find(doc.body().text())
        if (ofTxt != null) {
            val y = ofTxt.groupValues[1].toIntOrNull()
            if (y != null && y > maxFound) maxFound = y
        }
        return maxFound
    }

    private fun togglePageOverlay() {
        if (pageControlBar.visibility == View.GONE) {
            // Aparecer con animación: alpha + translationY hacia arriba, OvershootInterpolator.
            pageControlBar.alpha = 0f
            pageControlBar.translationY = (12 * resources.displayMetrics.density)
            pageControlBar.visibility = View.VISIBLE
            pageControlBar.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(180)
                .setInterpolator(OvershootInterpolator())
                .start()

            edtPageInput.setText(currentPage.toString())
            edtPageInput.setSelection(edtPageInput.text.length)
            edtPageInput.requestFocus()
            val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(edtPageInput, InputMethodManager.SHOW_IMPLICIT)
        } else {
            hidePageOverlay()
        }
    }

    private fun hidePageOverlay() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(edtPageInput.windowToken, 0)
        val offsetPx = (12 * resources.displayMetrics.density)
        pageControlBar.animate()
            .alpha(0f)
            .translationY(offsetPx)
            .setDuration(160)
            .withEndAction {
                pageControlBar.visibility = View.GONE
                pageControlBar.alpha = 1f
                pageControlBar.translationY = 0f
            }
            .start()
    }

    private fun handlePageInputSubmit() {
        val raw = edtPageInput.text.toString().trim()
        val page = raw.toIntOrNull()
        if (page == null || page < 1) {
            Toast.makeText(context, "Invalid page", Toast.LENGTH_SHORT).show()
            return
        }
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(edtPageInput.windowToken, 0)
        pageControlBar.visibility = View.GONE
        pageControlBar.alpha = 1f
        pageControlBar.translationY = 0f
        if (page == currentPage) {
            Toast.makeText(context, "Already on page $page", Toast.LENGTH_SHORT).show()
            return
        }
        jumpToPage(page)
    }
}
