package com.hstream.android

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.dash.DashMediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.SingleSampleMediaSource
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi

@UnstableApi
class PlayerActivity : AppCompatActivity() {

    companion object {
        var sharedClient: OkHttpClient? = null
        const val EXTRA_MPD_URL       = "extra_mpd_url"
        const val EXTRA_EPISODE_URL   = "extra_episode_url"
        const val EXTRA_EPISODE_TITLE = "extra_episode_title"
        const val EXTRA_SUBTITLE_URL  = "extra_subtitle_url"
        const val EXTRA_REFERER       = "extra_referer"
        const val EXTRA_EPISODE_LIST  = "extra_episode_list"
        const val EXTRA_CURRENT_INDEX = "extra_current_index"
    }

    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var controlsOverlay: View
    private lateinit var btnPlayPause: ImageButton
    private lateinit var seekBar: SeekBar
    private lateinit var txtCurrent: TextView
    private lateinit var txtTotal: TextView
    private lateinit var txtTitle: TextView
    private lateinit var btnEpList: ImageButton
    private lateinit var btnBack: ImageButton
    private lateinit var btnSeekConfig: ImageButton
    private lateinit var panelEpisodes: LinearLayout
    private lateinit var recyclerEpisodes: RecyclerView
    private lateinit var loadingBar: ProgressBar
    private lateinit var txtSeekFeedback: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val hideControlsRunnable = Runnable { hideControls() }
    private val updateSeekRunnable = object : Runnable {
        override fun run() {
            updateSeekBar()
            handler.postDelayed(this, 500)
        }
    }

    private var episodeList: List<VideoItem> = emptyList()
    private var currentIndex: Int = 0
    private var currentEpisodeUrl: String = ""
    private var referer: String = ""
    private var isPanelOpen = false
    private var isSeekBarTracking = false
    private var episodeListAdapter: EpisodeListAdapter? = null
    private lateinit var panelScrim: View
    private lateinit var snackbarScrim: View
    private var currentSnackbar: com.google.android.material.snackbar.Snackbar? = null

    // --- Lifecycle ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        makeFullscreen()

        setContentView(R.layout.activity_player)

        playerView      = findViewById(R.id.playerView)
        controlsOverlay = findViewById(R.id.controlsOverlay)
        btnPlayPause    = findViewById(R.id.btnPlayPause)
        seekBar         = findViewById(R.id.playerSeekBar)
        txtCurrent      = findViewById(R.id.txtCurrentTime)
        txtTotal        = findViewById(R.id.txtTotalTime)
        txtTitle        = findViewById(R.id.txtPlayerTitle)
        btnEpList       = findViewById(R.id.btnEpisodeList)
        btnBack         = findViewById(R.id.btnPlayerBack)
        btnSeekConfig   = findViewById(R.id.btnSeekConfig)
        panelEpisodes   = findViewById(R.id.panelEpisodes)
        panelScrim      = findViewById(R.id.panelScrim)
        panelScrim.setOnClickListener { if (isPanelOpen) togglePanel() }
        snackbarScrim   = findViewById(R.id.snackbarScrim)
        snackbarScrim.setOnClickListener { dismissSnackbarScrim() }
        recyclerEpisodes = findViewById(R.id.recyclerEpisodes)
        loadingBar      = findViewById(R.id.playerLoadingBar)
        txtSeekFeedback = findViewById(R.id.txtSeekFeedback)

        @Suppress("UNCHECKED_CAST")
        episodeList  = intent.getParcelableArrayListExtra<VideoItem>(EXTRA_EPISODE_LIST) ?: emptyList()
        currentIndex = intent.getIntExtra(EXTRA_CURRENT_INDEX, 0)
        currentEpisodeUrl = intent.getStringExtra(EXTRA_EPISODE_URL) ?: ""
        referer = intent.getStringExtra(EXTRA_REFERER) ?: ""

        val mpdUrl      = intent.getStringExtra(EXTRA_MPD_URL) ?: ""
        val title       = intent.getStringExtra(EXTRA_EPISODE_TITLE) ?: ""
        val subtitleUrl = intent.getStringExtra(EXTRA_SUBTITLE_URL) ?: ""

        txtTitle.text = title

        setupEpisodePanel()
        setupControls()
        setupDoubleTap()

        initPlayer(mpdUrl, subtitleUrl, referer)
        checkSavedPosition()

        if (episodeList.isEmpty() && currentEpisodeUrl.isNotEmpty()) {
            fetchEpisodeListForUrl(currentEpisodeUrl)
        }
    }

    override fun onPause() {
        super.onPause()
        savePLayerPosition()
        player?.pause()
    }

    override fun onResume() {
        super.onResume()
        makeFullscreen()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        player?.release()
        player = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    // --- Player ---

    private fun initPlayer(mpdUrl: String, subtitleUrl: String, referer: String) {
        val client = sharedClient ?: run {
            Toast.makeText(this, "Error: cliente HTTP no disponible", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val dataSourceFactory = OkHttpDataSource.Factory(client)
            .setDefaultRequestProperties(mapOf("Referer" to referer, "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"))

        val dashSource = DashMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(mpdUrl))

        val newPlayer = ExoPlayer.Builder(this).build()
        player = newPlayer
        playerView.player = newPlayer

        // Quitar fondo negro de los subtitulos
        playerView.subtitleView?.setStyle(
            androidx.media3.ui.CaptionStyleCompat(
                android.graphics.Color.WHITE,
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT,
                androidx.media3.ui.CaptionStyleCompat.EDGE_TYPE_DROP_SHADOW,
                android.graphics.Color.BLACK,
                null
            )
        )

        if (subtitleUrl.isNotEmpty()) {
            loadingBar.visibility = View.VISIBLE
            CoroutineScope(Dispatchers.IO).launch {
                val srtContent = downloadAndConvertSubtitle(subtitleUrl, client)
                var tempFile: java.io.File? = null
                if (srtContent.isNotEmpty()) {
                    tempFile = java.io.File(cacheDir, "sub_${currentEpisodeUrl.hashCode()}.srt")
                    tempFile.writeText(srtContent)
                }
                withContext(Dispatchers.Main) {
                    loadingBar.visibility = View.GONE
                    if (tempFile != null) {
                        val srtUri = android.net.Uri.fromFile(tempFile)
                        val subDataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this@PlayerActivity)
                        val subItem = MediaItem.SubtitleConfiguration.Builder(srtUri)
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("ja")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                        val subSource = SingleSampleMediaSource.Factory(subDataSourceFactory)
                            .createMediaSource(subItem, C.TIME_UNSET)
                        newPlayer.setMediaSource(MergingMediaSource(dashSource, subSource))
                    } else {
                        newPlayer.setMediaSource(dashSource)
                    }
                    newPlayer.prepare()
                    newPlayer.playWhenReady = true
                }
            }
        } else {
            newPlayer.setMediaSource(dashSource)
            newPlayer.prepare()
            newPlayer.playWhenReady = true
        }

        newPlayer.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                btnPlayPause.setImageResource(
                    if (isPlaying) android.R.drawable.ic_media_pause
                    else android.R.drawable.ic_media_play
                )
                if (isPlaying) handler.post(updateSeekRunnable)
                else handler.removeCallbacks(updateSeekRunnable)
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    val duration = newPlayer.duration
                    if (duration > 0) {
                        seekBar.max = (duration / 1000).toInt()
                        txtTotal.text = formatMs(duration)
                    }
                }
            }
        })
    }

    private fun downloadAndConvertSubtitle(url: String, client: OkHttpClient): String {
        return try {
            val req = Request.Builder().url(url)
                .header("Referer", referer)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val response = client.newCall(req).execute()
            val body = response.body?.string() ?: return ""
            if (url.endsWith(".ass", ignoreCase = true) || url.endsWith(".ssa", ignoreCase = true)) {
                AssToSrtConverter.convert(body)
            } else {
                body // SRT/VTT: pasar directo
            }
        } catch (e: Exception) { "" }
    }

    private fun loadEpisode(item: VideoItem, newIndex: Int) {
        loadingBar.visibility = View.VISIBLE
        currentIndex = newIndex
        currentEpisodeUrl = item.url
        txtTitle.text = item.title

        episodeListAdapter?.setCurrentIndex(newIndex)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Reutilizar la logica de obtencion del MPD de MainActivity
                val result = fetchMpdForEpisode(item)
                withContext(Dispatchers.Main) {
                    loadingBar.visibility = View.GONE
                    if (result != null) {
                        player?.release()
                        initPlayer(result.first, result.second, item.url)
                        checkSavedPosition()
                    } else {
                        Toast.makeText(this@PlayerActivity, "Error al cargar el episodio", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    loadingBar.visibility = View.GONE
                    Toast.makeText(this@PlayerActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun fetchMpdForEpisode(item: VideoItem): Pair<String, String>? {
        val client = sharedClient ?: return null
        val url = item.url

        val req1 = Request.Builder().url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)").build()
        val html = client.newCall(req1).execute().body?.string() ?: return null

        val pattern = java.util.regex.Pattern.compile("e_id\" type=\"hidden\" value=\"([^\"]*)")
        val matcher = pattern.matcher(html)
        if (!matcher.find()) return null
        val eId = matcher.group(1)

        var xsrfToken = ""
        val cookies = client.cookieJar.loadForRequest(okhttp3.HttpUrl.Builder().scheme("https").host("hstream.moe").build())
        for (cookie in cookies) {
            if (cookie.name == "XSRF-TOKEN") {
                xsrfToken = java.net.URLDecoder.decode(cookie.value, "UTF-8"); break
            }
        }
        if (xsrfToken.isEmpty()) return null

        val jsonPayload = org.json.JSONObject().apply { put("episode_id", eId) }.toString()
        val req2 = Request.Builder()
            .url("https://hstream.moe/player/api")
            .header("Referer", url)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("X-Xsrf-Token", xsrfToken)
            .post(jsonPayload.toRequestBody("application/json".toMediaType()))
            .build()

        val jsonResp = org.json.JSONObject(client.newCall(req2).execute().body?.string() ?: return null)
        val domains = jsonResp.getJSONArray("stream_domains")
        var cdnDomain = domains.getString(0)

        val testClient = client.newBuilder().connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS).build()
        for (i in 0 until domains.length()) {
            val domain = domains.getString(i)
            try { testClient.newCall(Request.Builder().url("$domain/").head().build()).execute().close(); cdnDomain = domain; break }
            catch (e: Exception) {}
        }

        val streamUrl = jsonResp.getString("stream_url")
        val mpdUrl = "$cdnDomain/$streamUrl/1080/manifest.mpd"

        val subPattern = java.util.regex.Pattern.compile("href=\"([^\"]+\\.(?:ass|srt|vtt))\"")
        val subMatcher = subPattern.matcher(html)
        var subtitleUrl = ""
        if (subMatcher.find()) {
            subtitleUrl = subMatcher.group(1)!!
            if (subtitleUrl.startsWith("/")) subtitleUrl = "https://hstream.moe$subtitleUrl"
        }

        return Pair(mpdUrl, subtitleUrl)
    }



    // --- Controles ---

    private fun setupControls() {


        playerView.setOnClickListener { toggleControls() }
        controlsOverlay.setOnClickListener { toggleControls() }

        btnBack.setOnClickListener { finish() }

        btnPlayPause.setOnClickListener {
            val p = player ?: return@setOnClickListener
            if (p.isPlaying) p.pause() else p.play()
            resetHideTimer()
        }

        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) txtCurrent.text = formatMs(progress * 1000L)
            }
            override fun onStartTrackingTouch(sb: SeekBar) { isSeekBarTracking = true; handler.removeCallbacks(hideControlsRunnable) }
            override fun onStopTrackingTouch(sb: SeekBar) {
                isSeekBarTracking = false
                player?.seekTo(sb.progress * 1000L)
                resetHideTimer()
            }
        })

        btnEpList.setOnClickListener { togglePanel() }
        findViewById<android.widget.ImageButton>(R.id.btnClosePanel).setOnClickListener { if (isPanelOpen) togglePanel() }

        btnSeekConfig.setOnClickListener { showSeekConfigDialog() }
    }

    private fun setupDoubleTap() {
        val zoneTapLeft  = findViewById<View>(R.id.zoneTapLeft)
        val zoneTapRight = findViewById<View>(R.id.zoneTapRight)

        val gestureLeft = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                seekRelative(-getSeekSeconds())
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls(); return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (Math.abs(vX) > Math.abs(vY) * 1.5f) {
                    val p = player
                    if (p != null && !p.isPlaying && vX > 0 && !isPanelOpen) togglePanel()
                    else seekRelative(if (vX > 0) getSeekSeconds() else -getSeekSeconds())
                    return true
                }
                return false
            }
        })

        val gestureRight = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                seekRelative(+getSeekSeconds())
                return true
            }
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                toggleControls(); return true
            }
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vX: Float, vY: Float): Boolean {
                if (Math.abs(vX) > Math.abs(vY) * 1.5f) {
                    val p = player
                    if (p != null && !p.isPlaying && vX > 0 && !isPanelOpen) togglePanel()
                    else seekRelative(if (vX > 0) getSeekSeconds() else -getSeekSeconds())
                    return true
                }
                return false
            }
        })

        zoneTapLeft.setOnTouchListener  { _, ev -> gestureLeft.onTouchEvent(ev); true }
        zoneTapRight.setOnTouchListener { _, ev -> gestureRight.onTouchEvent(ev); true }
    }

    private fun seekRelative(seconds: Int) {
        val p = player ?: return
        val newPos = (p.currentPosition + seconds * 1000L).coerceIn(0, p.duration)
        p.seekTo(newPos)
        val label = if (seconds > 0) "+${seconds}s" else "${seconds}s"
        showSeekFeedback(label)
        resetHideTimer()
    }

    private fun showSeekFeedback(label: String) {
        txtSeekFeedback.text = label
        txtSeekFeedback.visibility = View.VISIBLE
        handler.removeCallbacksAndMessages("seek_feedback")
        handler.postDelayed({ txtSeekFeedback.visibility = View.GONE }, 600)
    }

    private fun getSeekSeconds(): Int {
        val prefs = getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        return prefs.getInt("seek_seconds", 5)
    }

    private fun showSeekConfigDialog() {
        val options = arrayOf("5 seconds", "10 seconds", "15 seconds", "30 seconds")
        val values  = intArrayOf(5, 10, 15, 30)
        val current = getSeekSeconds()
        val selectedIndex = values.indexOfFirst { it == current }.coerceAtLeast(0)

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Skip Duration")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->
                getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
                    .edit().putInt("seek_seconds", values[which]).apply()
                dialog.dismiss()
            }
            .show()
        resetHideTimer()
    }

    private fun toggleControls() {
        if (isPanelOpen) {
            togglePanel()
            return
        }
        if (controlsOverlay.visibility == View.VISIBLE) hideControls()
        else showControls()
    }

    private fun showControls() {
        controlsOverlay.animate().cancel()
        controlsOverlay.visibility = View.VISIBLE
        controlsOverlay.animate().alpha(1f).setDuration(200).start()
        resetHideTimer()
    }

    private fun hideControls() {
        if (!isSeekBarTracking && !isPanelOpen) {
            controlsOverlay.animate().alpha(0f).setDuration(300).withEndAction {
                if (!isSeekBarTracking && !isPanelOpen) controlsOverlay.visibility = View.GONE
            }.start()
        }
    }

    private fun resetHideTimer() {
        handler.removeCallbacks(hideControlsRunnable)
        handler.postDelayed(hideControlsRunnable, 3000)
    }

    private fun updateSeekBar() {
        val p = player ?: return
        if (!isSeekBarTracking) {
            val pos = p.currentPosition
            seekBar.progress = (pos / 1000).toInt()
            txtCurrent.text = formatMs(pos)

            // Borrar posicion guardada si el episodio esta terminando (>= 98%)
            val duration = p.duration
            if (duration > 0 && pos >= duration * 0.98) {
                getSharedPreferences("PlayerPositions", Context.MODE_PRIVATE)
                    .edit().remove(currentEpisodeUrl).apply()
            }
        }
    }



    // --- Panel de episodios ---

    private fun setupEpisodePanel() {
        episodeListAdapter = EpisodeListAdapter(episodeList, currentIndex) { item, index ->
            togglePanel()
            loadEpisode(item, index)
        }
        recyclerEpisodes.layoutManager = LinearLayoutManager(this)
        recyclerEpisodes.adapter = episodeListAdapter
    }

    private fun togglePanel() {
        isPanelOpen = !isPanelOpen
        val targetX = if (isPanelOpen) 0f else 280f * resources.displayMetrics.density
        panelEpisodes.animate().translationX(targetX).setDuration(250).start()
        panelScrim.visibility = if (isPanelOpen) View.VISIBLE else View.GONE
        if (isPanelOpen) handler.removeCallbacks(hideControlsRunnable)
        else resetHideTimer()
    }

    private fun fetchEpisodeListForUrl(episodeUrl: String) {
        val client = sharedClient ?: return
        // Derivar la URL base de serie quitando el sufijo "-numero" final
        val seriesUrl = episodeUrl.replace(Regex("-\\d+$"), "")
        if (seriesUrl == episodeUrl) return // no habia numero al final

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = okhttp3.Request.Builder()
                    .url(seriesUrl)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val html = client.newCall(req).execute().body?.string() ?: return@launch
                val doc = org.jsoup.Jsoup.parse(html)

                val episodeRegex = Regex("^${Regex.escape(seriesUrl)}-\\d+$")
                val newItems = mutableListOf<VideoItem>()
                val seen = mutableSetOf<String>()

                for (link in doc.select("a[href^=/hentai/], a[href^=https://hstream.moe/hentai/]")) {
                    var url = link.attr("href")
                    if (url.startsWith("/")) url = "https://hstream.moe$url"
                    if (seen.contains(url) || !url.matches(episodeRegex)) continue
                    seen.add(url)

                    val img = link.selectFirst("img") ?: continue
                    var poster = img.attr("data-src").ifEmpty { img.attr("src") }
                    if (poster.isEmpty()) continue
                    if (poster.startsWith("/")) poster = "https://hstream.moe$poster"

                    val title = link.selectFirst("p")?.text() ?: img.attr("alt").ifEmpty { "Capitulo" }
                    newItems.add(VideoItem(url, title, poster))
                }

                if (newItems.isEmpty()) return@launch

                // Detectar el indice del episodio actual
                val newIndex = newItems.indexOfFirst { it.url == episodeUrl }

                withContext(Dispatchers.Main) {
                    episodeList = newItems
                    currentIndex = if (newIndex >= 0) newIndex else 0
                    episodeListAdapter = EpisodeListAdapter(episodeList, currentIndex) { item, index ->
                        togglePanel()
                        loadEpisode(item, index)
                    }
                    recyclerEpisodes.adapter = episodeListAdapter

                }
            } catch (e: Exception) { /* silencioso */ }
        }
    }

    // --- Posicion guardada ---

    private fun checkSavedPosition() {
        val prefs = getSharedPreferences("PlayerPositions", Context.MODE_PRIVATE)
        val savedPos = prefs.getLong(currentEpisodeUrl, -1L)
        if (savedPos > 30_000L) {
            val label = formatMs(savedPos)
            val snackbar = com.google.android.material.snackbar.Snackbar.make(
                playerView, "Continuar desde $label", 8000
            )
            snackbar.setAction("Continuar") {
                player?.seekTo(savedPos)
                dismissSnackbarScrim()
            }
            snackbar.addCallback(object : com.google.android.material.snackbar.Snackbar.Callback() {
                override fun onDismissed(sb: com.google.android.material.snackbar.Snackbar, event: Int) {
                    snackbarScrim.visibility = View.GONE
                    currentSnackbar = null
                }
            })
            currentSnackbar = snackbar
            snackbarScrim.visibility = View.VISIBLE
            snackbar.show()
        }
    }

    private fun dismissSnackbarScrim() {
        currentSnackbar?.dismiss()
        currentSnackbar = null
        snackbarScrim.visibility = View.GONE
    }

    private fun savePLayerPosition() {
        val p = player ?: return
        val pos = p.currentPosition
        val duration = p.duration
        if (pos > 30_000L && (duration <= 0 || pos < duration * 0.98)) {
            getSharedPreferences("PlayerPositions", Context.MODE_PRIVATE)
                .edit().putLong(currentEpisodeUrl, pos).apply()
        }
    }

    // --- Helpers ---

    private fun makeFullscreen() {
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun formatMs(ms: Long): String {
        val totalSec = ms / 1000
        val sec = totalSec % 60
        val min = (totalSec / 60) % 60
        val hour = totalSec / 3600
        return if (hour > 0) "%d:%02d:%02d".format(hour, min, sec)
        else "%02d:%02d".format(min, sec)
    }

    // --- Adapter interno para lista de episodios en el panel ---

    inner class EpisodeListAdapter(
        private val items: List<VideoItem>,
        private var currentIdx: Int,
        private val onClick: (VideoItem, Int) -> Unit
    ) : RecyclerView.Adapter<EpisodeListAdapter.VH>() {

        fun setCurrentIndex(idx: Int) { currentIdx = idx; notifyDataSetChanged() }

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val thumb: ImageView = view.findViewById(R.id.imgEpPlayerThumb)
            val title: TextView  = view.findViewById(R.id.txtEpPlayerTitle)
            val indicator: View  = view.findViewById(R.id.indicatorCurrentEp)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_episode_player, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            holder.title.text = item.title
            holder.thumb.load(item.posterUrl) { crossfade(true) }
            holder.indicator.visibility = if (position == currentIdx) View.VISIBLE else View.GONE
            holder.itemView.setOnClickListener { onClick(item, position) }
        }

        override fun getItemCount() = items.size
    }
}
