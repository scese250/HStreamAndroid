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
        val sortAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            listOf("Recently Uploaded", "Most Viewed") // hstream uses recently-released, most-viewed
        )
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerSort.adapter = sortAdapter

        val btnGenres: Button = view.findViewById(R.id.btnGenres)
        val btnBlacklist: Button = view.findViewById(R.id.btnBlacklist)
        val btnStudios: Button = view.findViewById(R.id.btnStudios)
        val btnApplyFilters: Button = view.findViewById(R.id.btnApplyFilters)
        val editSearch: EditText = view.findViewById(R.id.editSearch)
        
        progressBar = view.findViewById(R.id.progressSearch)
        val recyclerView: RecyclerView = view.findViewById(R.id.recyclerSearch)

        adapter = VideoAdapter(mutableListOf()) { url ->
            (requireActivity() as MainActivity).handleVideoClick(url)
        }

        val layoutManager = GridLayoutManager(context, 2)
        recyclerView.layoutManager = layoutManager
        recyclerView.adapter = adapter

        btnGenres.setOnClickListener {
            showMultiSelectDialog("Géneros", genresList.map { it.first }.toTypedArray(), selectedGenres)
        }

        btnBlacklist.setOnClickListener {
            showMultiSelectDialog("Blacklist", genresList.map { it.first }.toTypedArray(), selectedBlacklist)
        }

        btnStudios.setOnClickListener {
            showMultiSelectDialog("Estudios", studiosList.map { it.first }.toTypedArray(), selectedStudios)
        }

        btnApplyFilters.setOnClickListener {
            currentPage = 1
            adapter.clearItems()
            performSearch(editSearch.text.toString(), spinnerSort.selectedItemPosition)
        }

        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
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

        return view
    }

    private fun showMultiSelectDialog(title: String, items: Array<String>, checkedItems: BooleanArray) {
        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(title)
        builder.setMultiChoiceItems(items, checkedItems) { _, which, isChecked ->
            checkedItems[which] = isChecked
        }
        builder.setPositiveButton("Aceptar", null)
        builder.setNeutralButton("Limpiar") { _, _ ->
            for (i in checkedItems.indices) {
                checkedItems[i] = false
            }
        }
        builder.show()
    }

    private fun buildSearchUrl(query: String, sortIndex: Int, page: Int): String {
        val baseUrl = "https://hstream.moe/search?"
        val params = mutableListOf<String>()

        if (query.isNotEmpty()) {
            params.add("key=${URLEncoder.encode(query, "UTF-8")}")
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

        val order = if (sortIndex == 0) "recently-released" else "most-viewed"
        params.add("order=$order")
        params.add("view=poster")
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
                    val title = p?.text() ?: img.attr("alt").ifEmpty { "Desconocido" }
                    
                    newItems.add(VideoItem(itemUrl, title, posterUrl))
                }

                withContext(Dispatchers.Main) {
                    if (currentPage == 1) {
                        progressBar.visibility = View.GONE
                        if (newItems.isEmpty()) {
                            Toast.makeText(context, "No se encontraron resultados", Toast.LENGTH_SHORT).show()
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
}
