package com.hstream.android

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Spinner
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
import java.net.URLEncoder

class SearchFragment : Fragment() {

    private lateinit var adapter: VideoAdapter
    private lateinit var progressBar: ProgressBar
    private var currentPage = 1
    private var isLoading = false
    
    private val client: OkHttpClient
        get() = (requireActivity() as MainActivity).client

    // Listas extraidas de hstream.moe
    private val genresList = listOf(
        Pair("Ahegao", "ahegao"), Pair("Bestiality", "bestiality"), Pair("Bondage", "bondage"), Pair("Creampie", "creampie"),
        Pair("Gore", "gore"), Pair("Harem", "harem"), Pair("Incest", "incest"), Pair("Lactation", "lactation"),
        Pair("Lq", "lq"), Pair("Mind Break", "mind-break"), Pair("Mind Control", "mind-control"), Pair("Orc", "orc"),
        Pair("Scat", "scat"), Pair("Tentacle", "tentacle"), Pair("Toys", "toys"), Pair("Tsundere", "tsundere"),
        Pair("Virgin", "virgin"), Pair("Yuri", "yuri"), Pair("Anal", "anal"), Pair("Bdsm", "bdsm"), Pair("Facial", "facial"),
        Pair("Blow Job", "blow-job"), Pair("Boob Job", "boob-job"), Pair("Foot Job", "foot-job"), Pair("Hand Job", "hand-job"),
        Pair("Rimjob", "rimjob"), Pair("Inflation", "inflation"), Pair("Masturbation", "masturbation"), Pair("Public Sex", "public-sex"),
        Pair("Rape", "rape"), Pair("Reverse Rape", "reverse-rape"), Pair("Threesome", "threesome"), Pair("Orgy", "orgy"),
        Pair("Gangbang", "gangbang"), Pair("Loli", "loli"), Pair("Shota", "shota"), Pair("Milf", "milf"),
        Pair("Futanari", "futanari"), Pair("Big Boobs", "big-boobs"), Pair("Small Boobs", "small-boobs"), Pair("Dark Skin", "dark-skin"),
        Pair("Cosplay", "cosplay"), Pair("Elf", "elf"), Pair("Maid", "maid"), Pair("Nekomimi", "nekomimi"),
        Pair("Nurse", "nurse"), Pair("School Girl", "school-girl"), Pair("Succubus", "succubus"), Pair("Teacher", "teacher"),
        Pair("Trap", "trap"), Pair("Pregnant", "pregnant"), Pair("Glasses", "glasses"), Pair("Swim Suit", "swim-suit"),
        Pair("Ugly Bastard", "ugly-bastard"), Pair("Monster", "monster"), Pair("3D", "3d"), Pair("4K", "4k"),
        Pair("48Fps", "48fps"), Pair("4K 48Fps", "4k-48fps"), Pair("Censored", "censored"), Pair("Uncensored", "uncensored"),
        Pair("Comedy", "comedy"), Pair("Fantasy", "fantasy"), Pair("Horror", "horror"), Pair("Vanilla", "vanilla"),
        Pair("Ntr", "ntr"), Pair("Pov", "pov"), Pair("Filmed", "filmed"), Pair("X-Ray", "x-ray")
    )

    private val studiosList = listOf(
        Pair("Arms", "arms"), Pair("Blue Eyes", "blue-eyes"), Pair("BOMB! CUTE! BOMB!", "bomb-cute-bomb"),
        Pair("BreakBottle", "breakbottle"), Pair("CherryLips", "cherrylips"), Pair("ChiChinoya", "chichinoya"),
        Pair("ChuChu", "chuchu"), Pair("Circle Tribute", "circle-tribute"), Pair("Collaboration Works", "collaboration-works"),
        Pair("Cosmos", "cosmos"), Pair("Cranberry", "cranberry"), Pair("Digital Works", "digital-works"),
        Pair("Discovery", "discovery"), Pair("Edge", "edge"), Pair("Five Ways", "five-ways"),
        Pair("Flavors Soft", "flavors-soft"), Pair("Frontier Works", "frontier-works"), Pair("Godoy", "godoy"),
        Pair("Gold Bear", "gold-bear"), Pair("Green Bunny", "green-bunny"), Pair("Himajin Planning", "himajin-planning"),
        Pair("Jumondo", "jumondo"), Pair("King Bee", "king-bee"), Pair("L.", "l"), Pair("Lune Pictures", "lune-pictures"),
        Pair("Magic Bus", "magic-bus"), Pair("Majin", "majin"), Pair("Mary Jane", "mary-jane"),
        Pair("Media Blasters", "media-blasters"), Pair("Mediabank", "mediabank"), Pair("Metro Notes", "metro-notes"),
        Pair("Milky Animation Label", "milky-animation-label"), Pair("Moon Rock", "moon-rock"), Pair("Mousou Jitsugen Media", "mousou-jitsugen-media"),
        Pair("Mousou Senka", "mousou-senka"), Pair("MS Pictures", "ms-pictures"), Pair("Natural High", "natural-high"),
        Pair("Nihikime no Dozeu", "nihikime-no-dozeu"), Pair("Nur", "nur"), Pair("Pashmina", "pashmina"),
        Pair("Peach Pie", "peach-pie"), Pair("Peak Hunt", "peak-hunt"), Pair("Pink Pineapple", "pink-pineapple"),
        Pair("Pixy", "pixy"), Pair("Pixy Soft", "pixy-soft"), Pair("PoRO", "poro"), Pair("Queen Bee", "queen-bee"),
        Pair("Rabbit Gate", "rabbit-gate"), Pair("Ryuu M's", "ryuu-ms"), Pair("Schoolzone", "schoolzone"),
        Pair("SELFISH", "selfish"), Pair("Seven", "seven"), Pair("Shinjukuza", "shinjukuza"), Pair("Shinkuukan", "shinkuukan"),
        Pair("Showten", "showten"), Pair("SPEED", "speed"), Pair("Studio 1st", "studio-1st"), Pair("Studio 9 Maiami", "studio-9-maiami"),
        Pair("Studio Eromatick", "studio-eromatick"), Pair("Studio Fantasia", "studio-fantasia"), Pair("Studio Ten", "studio-ten"),
        Pair("Suiseisha", "suiseisha"), Pair("Suzuki Mirano", "suzuki-mirano"), Pair("T-Rex", "t-rex"), Pair("Toranoana", "toranoana"),
        Pair("Torudaya", "torudaya"), Pair("Union Cho", "union-cho"), Pair("Valkyria", "valkyria"), Pair("White Bear", "white-bear"),
        Pair("XTER", "xter"), Pair("ZIZ", "ziz"), Pair("Zyc", "zyc")
    )

    private val selectedGenres = BooleanArray(genresList.size)
    private val selectedBlacklist = BooleanArray(genresList.size)
    private val selectedStudios = BooleanArray(studiosList.size)

    companion object {
        fun newInstance(studioId: String? = null): SearchFragment {
            val fragment = SearchFragment()
            if (studioId != null) {
                val args = Bundle()
                args.putString("studioId", studioId)
                fragment.arguments = args
            }
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search, container, false)
        
        val preSelectedStudio = arguments?.getString("studioId")
        if (preSelectedStudio != null) {
            val idx = studiosList.indexOfFirst { it.second == preSelectedStudio }
            if (idx >= 0) {
                selectedStudios[idx] = true
            }
        }
        
        val spinnerSort: Spinner = view.findViewById(R.id.spinnerSort)
        val sortOptions = listOf(
            "Recently Uploaded",
            "Recently Released",
            "A-Z",
            "Z-A",
            "Oldest Uploads",
            "Oldest Releases",
            "View Count"
        )
        val sortAdapter = ArrayAdapter(
            requireContext(),
            R.layout.spinner_item,
            sortOptions
        )
        sortAdapter.setDropDownViewResource(R.layout.spinner_dropdown_item)
        spinnerSort.adapter = sortAdapter

        val btnGenres: androidx.cardview.widget.CardView = view.findViewById(R.id.btnGenres)
        val btnBlacklist: androidx.cardview.widget.CardView = view.findViewById(R.id.btnBlacklist)
        val btnStudios: androidx.cardview.widget.CardView = view.findViewById(R.id.btnStudios)
        
        val txtGenresSubtitle: android.widget.TextView = view.findViewById(R.id.txtGenresSubtitle)
        val txtBlacklistSubtitle: android.widget.TextView = view.findViewById(R.id.txtBlacklistSubtitle)
        val txtStudiosSubtitle: android.widget.TextView = view.findViewById(R.id.txtStudiosSubtitle)
        
        val btnToggleLayout: androidx.cardview.widget.CardView = view.findViewById(R.id.btnToggleLayout)
        val txtToggleLayout: android.widget.TextView = view.findViewById(R.id.txtToggleLayout)
        
        val btnApplyFilters: Button = view.findViewById(R.id.btnApplyFilters)
        val editSearch: EditText = view.findViewById(R.id.editSearch)
        
        val btnToggleFilters: android.widget.ImageButton = view.findViewById(R.id.btnToggleFilters)
        val filterContainer: android.widget.LinearLayout = view.findViewById(R.id.filterContainer)
        
        btnToggleFilters.setOnClickListener {
            if (filterContainer.visibility == View.VISIBLE) {
                filterContainer.visibility = View.GONE
            } else {
                filterContainer.visibility = View.VISIBLE
            }
        }
        
        progressBar = view.findViewById(R.id.progressSearch)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerSearch)

        adapter = VideoAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).handleVideoClick(url)
        }

        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", android.content.Context.MODE_PRIVATE)
        val searchDesign = prefs.getString("searchDesign", "cover")
        
        val cachedBlacklist = prefs.getStringSet("cached_blacklist", setOf()) ?: setOf()
        var blacklistCount = 0
        for (i in genresList.indices) {
            val tag = genresList[i].first
            if (cachedBlacklist.contains(tag)) {
                selectedBlacklist[i] = true
                blacklistCount++
            }
        }
        txtBlacklistSubtitle.text = "$blacklistCount Blocked"
        
        if (preSelectedStudio != null) {
            val idx = studiosList.indexOfFirst { it.second == preSelectedStudio }
            if (idx >= 0) {
                txtStudiosSubtitle.text = "1 Studios Selected"
            }
        }
        val layoutManager = GridLayoutManager(context, if (searchDesign == "thumbnail") 1 else 2)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        btnGenres.setOnClickListener {
            showFilterDialog("Genres", selectedGenres, txtGenresSubtitle, "Genres Selected")
        }

        btnBlacklist.setOnClickListener {
            showFilterDialog("Blacklist", selectedBlacklist, txtBlacklistSubtitle, "Blocked")
        }

        btnStudios.setOnClickListener {
            showMultiSelectDialog("Studios", studiosList.map { it.first }.toTypedArray(), selectedStudios, txtStudiosSubtitle)
        }
        
        var isPosterLayout = searchDesign != "thumbnail"
        txtToggleLayout.text = if (isPosterLayout) "Poster" else "Thumbnail"
        
        btnToggleLayout.setOnClickListener {
            isPosterLayout = !isPosterLayout
            val design = if (isPosterLayout) "cover" else "thumbnail"
            prefs.edit().putString("searchDesign", design).apply()
            txtToggleLayout.text = if (isPosterLayout) "Poster" else "Thumbnail"
            recyclerView.layoutManager = GridLayoutManager(context, if (isPosterLayout) 2 else 1)
        }

        btnApplyFilters.setOnClickListener {
            filterContainer.visibility = View.GONE
            currentPage = 1
            adapter.clearItems()
            performSearch(editSearch.text.toString(), spinnerSort.selectedItemPosition)
        }
        
        editSearch.setOnEditorActionListener { _, actionId, event ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == android.view.KeyEvent.KEYCODE_ENTER && event.action == android.view.KeyEvent.ACTION_DOWN)) {
                
                val imm = requireContext().getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(editSearch.windowToken, 0)
                
                btnApplyFilters.performClick()
                true
            } else {
                false
            }
        }

        val searchHeader = view.findViewById<android.widget.FrameLayout>(R.id.searchHeader)
        var isHeaderHidden = false
        val searchHeaderHeight = (50 * resources.displayMetrics.density).toInt()
        val toolbarHeight = (56 * resources.displayMetrics.density).toInt()
        val toolbar = requireActivity().findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy > 10 && !isHeaderHidden) {
                    if (filterContainer.visibility == View.VISIBLE) {
                        filterContainer.visibility = View.GONE
                    }
                    isHeaderHidden = true
                    val anim = android.animation.ValueAnimator.ofFloat(1f, 0f)
                    anim.addUpdateListener { valueAnimator ->
                        val fraction = valueAnimator.animatedValue as Float
                        
                        val lpSearch = searchHeader.layoutParams
                        lpSearch.height = (searchHeaderHeight * fraction).toInt()
                        searchHeader.layoutParams = lpSearch
                        
                        val lpToolbar = toolbar.layoutParams
                        lpToolbar.height = (toolbarHeight * fraction).toInt()
                        toolbar.layoutParams = lpToolbar
                    }
                    anim.duration = 200
                    anim.start()
                } else if (dy < -10 && isHeaderHidden) {
                    isHeaderHidden = false
                    val anim = android.animation.ValueAnimator.ofFloat(0f, 1f)
                    anim.addUpdateListener { valueAnimator ->
                        val fraction = valueAnimator.animatedValue as Float
                        
                        val lpSearch = searchHeader.layoutParams
                        lpSearch.height = (searchHeaderHeight * fraction).toInt()
                        searchHeader.layoutParams = lpSearch
                        
                        val lpToolbar = toolbar.layoutParams
                        lpToolbar.height = (toolbarHeight * fraction).toInt()
                        toolbar.layoutParams = lpToolbar
                    }
                    anim.duration = 200
                    anim.start()
                }
                
                if (dy > 0) {
                    val visibleItemCount = layoutManager.childCount
                    val totalItemCount = layoutManager.itemCount
                    val pastVisibleItems = layoutManager.findFirstVisibleItemPosition()

                    if (!isLoading && (visibleItemCount + pastVisibleItems) >= totalItemCount) {
                        currentPage++
                        performSearch(editSearch.text.toString(), spinnerSort.selectedItemPosition)
                    }
                }
            }
        })
        
        if (preSelectedStudio != null) {
            currentPage = 1
            adapter.clearItems()
            performSearch(editSearch.text.toString(), spinnerSort.selectedItemPosition)
        }

        return view
    }

    private fun showFilterDialog(title: String, checkedItemsArray: BooleanArray, subtitleView: android.widget.TextView, suffix: String) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<android.widget.TextView>(R.id.txtDialogTitle).text = title

        val categories = mapOf(
            "Genres" to listOf("Comedy", "Fantasy", "Horror", "Vanilla", "Ntr", "Pov", "Filmed", "X-Ray", "Romance", "Drama", "Slice of Life", "Sports", "Supernatural", "Mecha", "Mystery", "Harem", "Adventure", "Action"),
            "Actions" to listOf("Anal", "Blow Job", "Boob Job", "Foot Job", "Hand Job", "Rimjob", "Bdsm", "Bondage", "Creampie", "Facial", "Inflation", "Masturbation", "Public Sex", "Rape", "Reverse Rape", "Threesome", "Orgy", "Gangbang", "Tentacle", "Toys"),
            "Appearance" to listOf("Big Boobs", "Small Boobs", "Dark Skin", "Cosplay", "Elf", "Maid", "Nekomimi", "Nurse", "School Girl", "Succubus", "Teacher", "Trap", "Pregnant", "Glasses", "Swim Suit", "Ugly Bastard", "Monster", "Loli", "Shota", "Milf", "Futanari"),
            "Types" to listOf("3D", "4K", "48Fps", "4K 48Fps", "Censored", "Uncensored", "Ahegao", "Bestiality", "Gore", "Incest", "Lactation", "Lq", "Mind Break", "Mind Control", "Orc", "Scat", "Tsundere", "Virgin", "Yuri")
        )

        val headers = mapOf(
            "Genres" to dialogView.findViewById<android.widget.TextView>(R.id.headerGenres),
            "Actions" to dialogView.findViewById<android.widget.TextView>(R.id.headerActions),
            "Appearance" to dialogView.findViewById<android.widget.TextView>(R.id.headerAppearance),
            "Types" to dialogView.findViewById<android.widget.TextView>(R.id.headerTypes)
        )

        val grids = mapOf(
            "Genres" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridGenres),
            "Actions" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridActions),
            "Appearance" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridAppearance),
            "Types" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridTypes)
        )

        val allChips = mutableListOf<Pair<Int, android.widget.TextView>>() // index in genresList to TextView
        val categoryChips = mutableMapOf<String, MutableList<android.widget.TextView>>()

        categories.forEach { (cat, tags) ->
            val header = headers[cat]!!
            val grid = grids[cat]!!
            
            val updateHeaderFn = {
                var c = 0
                categoryChips[cat]?.forEach { if(it.isSelected) c++ }
                if (c > 0) {
                    val countStr = "  •  $c"
                    header.text = android.text.SpannableStringBuilder(cat).apply {
                        append(android.text.SpannableString(countStr).apply {
                            setSpan(android.text.style.ForegroundColorSpan(android.graphics.Color.parseColor("#BB86FC")), 0, countStr.length, 0)
                        })
                    }
                } else {
                    header.text = cat
                }
            }
            
            header.text = cat
            header.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0)
            
            header.setOnClickListener {
                if (grid.visibility == View.GONE) {
                    grid.visibility = View.VISIBLE
                    header.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_up, 0)
                } else {
                    grid.visibility = View.GONE
                    header.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_arrow_down, 0)
                }
            }

            tags.forEach { tag ->
                val index = genresList.indexOfFirst { it.first == tag }
                if (index != -1) {
                    val isChecked = checkedItemsArray[index]
                    val chip = android.widget.TextView(requireContext()).apply {
                        text = tag
                        textSize = 14f
                        setTextColor(if (isChecked) android.graphics.Color.parseColor("#BB86FC") else android.graphics.Color.parseColor("#E3E3E3"))
                        setPadding(16, 16, 16, 16)
                        val marginParams = android.widget.GridLayout.LayoutParams().apply {
                            setMargins(8, 8, 8, 8)
                            width = 0
                            columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                        }
                        layoutParams = marginParams
                        gravity = android.view.Gravity.CENTER
                        background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_selector)
                        isSelected = isChecked
                        isClickable = true
                        
                        setOnClickListener {
                            isSelected = !isSelected
                            setTextColor(if (isSelected) android.graphics.Color.parseColor("#BB86FC") else android.graphics.Color.parseColor("#E3E3E3"))
                            updateHeaderFn()
                        }
                    }
                    grid.addView(chip)
                    allChips.add(Pair(index, chip))
                    categoryChips.getOrPut(cat) { mutableListOf() }.add(chip)
                }
            }
            updateHeaderFn()
        }

        dialogView.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnDialogSave).setOnClickListener {
            var count = 0
            allChips.forEach { (index, chip) ->
                checkedItemsArray[index] = chip.isSelected
                if (chip.isSelected) count++
            }
            subtitleView.text = "$count $suffix"
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun showMultiSelectDialog(title: String, items: Array<String>, checkedItems: BooleanArray, subtitleView: android.widget.TextView) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        builder.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        builder.setPositiveButton("OK") { _, _ -> 
            val count = checkedItems.count { it }
            subtitleView.text = "$count Studios Selected"
        }
        builder.setNeutralButton("Clear") { _, _ ->
            for (i in checkedItems.indices) {
                checkedItems[i] = false
            }
            subtitleView.text = "0 Studios Selected"
        }
        builder.show()
    }

    private fun buildSearchUrl(query: String, sortIndex: Int, page: Int): String {
        val baseUrl = "https://hstream.moe/search?"
        val params = mutableListOf<String>()

        if (query.isNotEmpty()) {
            params.add("search=${URLEncoder.encode(query, "UTF-8")}")
        }

        // &tags[]=action&tags[]=comedy...
        var hasTags = false
        for (i in selectedGenres.indices) {
            if (selectedGenres[i]) {
                params.add("tags[]=${genresList[i].second}")
                hasTags = true
            }
        }
        if (hasTags) params.add("tags-mode=and") // or 'or' based on preference, hstream uses 'and' mostly

        for (i in selectedBlacklist.indices) {
            if (selectedBlacklist[i]) {
                params.add("blacklist[]=${genresList[i].second}")
            }
        }

        for (i in selectedStudios.indices) {
            if (selectedStudios[i]) {
                params.add("studios[]=${studiosList[i].second}")
            }
        }

        val orderParams = listOf(
            "recently-uploaded",
            "recently-released",
            "title-sort-asc",
            "title-sort-desc",
            "oldest-uploaded",
            "oldest-released",
            "most-viewed"
        )
        val order = if (sortIndex in orderParams.indices) orderParams[sortIndex] else "recently-released"
        params.add("order=$order")
        
        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", android.content.Context.MODE_PRIVATE)
        val searchDesign = prefs.getString("searchDesign", "poster")
        val viewParam = if (searchDesign == "thumbnail") "thumbnail" else "poster"
        params.add("view=$viewParam")
        
        params.add("page=$page")

        return baseUrl + params.joinToString("&")
    }

    private fun performSearch(query: String, sortIndex: Int) {
        isLoading = true
        if (currentPage == 1) progressBar.visibility = View.VISIBLE

        val url = buildSearchUrl(query, sortIndex, currentPage)

        CoroutineScope(Dispatchers.IO).launch {
            try {
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
                    val title = p?.text() ?: img.attr("alt").ifEmpty { "Unknown" }
                    
                    newItems.add(VideoItem(itemUrl, title, posterUrl))
                }

                withContext(Dispatchers.Main) {
                    if (currentPage == 1) {
                        progressBar.visibility = View.GONE
                        if (newItems.isEmpty()) {
                            Toast.makeText(context, "No results found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    if (currentPage == 1) {
                        adapter.clearItems()
                    }
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

    override fun onDestroyView() {
        super.onDestroyView()
        val toolbar = requireActivity().findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        val lp = toolbar.layoutParams
        lp.height = (56 * resources.displayMetrics.density).toInt()
        toolbar.layoutParams = lp
    }
}
