package com.hstream.android

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import coil.load
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLDecoder
import java.util.regex.Pattern

class MainActivity : AppCompatActivity() {

    lateinit var drawerLayout: DrawerLayout

    lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        client = OkHttpClient.Builder()
            .cookieJar(PersistentCookieJar(this))
            .build()
            
        val prefs = getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("privacy_lock", false)) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        setContentView(R.layout.activity_main)

        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        drawerLayout = findViewById(R.id.drawerLayout)
        val navView: NavigationView = findViewById(R.id.navigationView)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.navigation_drawer_open, R.string.navigation_drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        val headerView = navView.getHeaderView(0)
        val navUsername = headerView.findViewById<android.widget.TextView>(R.id.navHeaderUsername)
        val navAvatar = headerView.findViewById<android.widget.ImageView>(R.id.navHeaderAvatar)

        if (prefs.getBoolean("is_logged_in", false)) {
            val savedUser = prefs.getString("username", "")
            val savedAvatar = prefs.getString("avatar_url", "")
            if (!savedUser.isNullOrEmpty()) {
                navUsername.text = savedUser
                if (!savedAvatar.isNullOrEmpty()) {
                    navAvatar.load(savedAvatar)
                }
            } else {
                fetchUserProfile(navUsername, navAvatar)
            }
        } else {
            navUsername.text = "Guest"
        }

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> replaceFragment(HomeFragment(), "Home")
                R.id.nav_search -> replaceFragment(SearchFragment(), "Search")
                R.id.nav_favs -> replaceFragment(FavsFragment(), "Favs")
                R.id.nav_settings -> replaceFragment(SettingsFragment(), "Settings")
            }
            drawerLayout.closeDrawers()
            true
        }

        if (savedInstanceState == null) {
            replaceFragment(HomeFragment(), "Home")
            navView.setCheckedItem(R.id.nav_home)
        }
    }

    private fun replaceFragment(fragment: Fragment, title: String) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
        
        supportActionBar?.title = title
    }

    override fun onResume() {
        super.onResume()
        val prefs = getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("privacy_lock", false)) {
            window.setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
            )
        } else {
            window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun fetchUserProfile(usernameText: android.widget.TextView, avatarImage: android.widget.ImageView) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val req = Request.Builder()
                    .url("https://hstream.moe/user/settings")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: ""
                val doc = org.jsoup.Jsoup.parse(html)
                
                var nameInput = doc.select("input[name=username]").attr("value")
                if (nameInput.isEmpty()) {
                    nameInput = doc.select(".dropdown-menu .dropdown-header").text()
                }
                val finalName = if(nameInput.isNotEmpty()) nameInput else "User"
                
                val avatarSrc = doc.select(".user-avatar img").attr("src").ifEmpty {
                    doc.select("img.rounded-circle").firstOrNull()?.attr("src") ?: ""
                }
                
                withContext(Dispatchers.Main) {
                    usernameText.text = finalName
                    if (avatarSrc.isNotEmpty()) {
                        avatarImage.load(avatarSrc)
                        getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE).edit()
                            .putString("username", finalName)
                            .putString("avatar_url", avatarSrc)
                            .apply()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun handleVideoClick(url: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Opciones")
            .setMessage("¿Qué deseas hacer?")
            .setPositiveButton("Mirar Capítulo") { _, _ ->
                playVideo(url)
            }
            .setNegativeButton("Ver Serie") { _, _ ->
                openSeriesFragment(url)
            }
            .show()
    }

    fun openSeriesFragment(url: String) {
        // Remover el sufijo "-#" de los capítulos para ir a la serie
        val seriesUrl = url.replace(Regex("-\\d+$"), "")
        val fragment = SeriesFragment.newInstance(seriesUrl)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .addToBackStack(null)
            .commit()
        supportActionBar?.title = "Serie"
    }

    fun playVideo(url: String) {
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
                if (!matcher.find()) throw Exception("No se encontró el e_id")
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
                
                if (xsrfToken.isEmpty()) throw Exception("No se encontró XSRF-TOKEN")

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
                
                val domainsArray = jsonResponse.getJSONArray("stream_domains")
                var cdnDomain = domainsArray.getString(0)
                
                // Buscar el primer servidor CDN que esté online
                val testClient = client.newBuilder()
                    .connectTimeout(2, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                    
                for (i in 0 until domainsArray.length()) {
                    val domain = domainsArray.getString(i)
                    try {
                        val testReq = Request.Builder().url("$domain/").head().build()
                        testClient.newCall(testReq).execute().close()
                        cdnDomain = domain
                        break
                    } catch (e: Exception) {
                        // Ignorar y probar el siguiente
                    }
                }
                
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
                
                // 3. Lanzar intent
                withContext(Dispatchers.Main) {
                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(Uri.parse(mpdUrl), "video/*")
                    if (subtitles.isNotEmpty()) {
                        intent.putExtra("subs", subtitles.toTypedArray())
                    }
                    
                    val headers = Bundle()
                    headers.putString("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    headers.putString("Referer", url)
                    intent.putExtra("android.media.intent.extra.HTTP_HEADERS", headers)
                    
                    val prefs = getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
                    val defaultPlayer = prefs.getString("default_player", "")
                    
                    if (!defaultPlayer.isNullOrEmpty()) {
                        intent.setPackage(defaultPlayer)
                        try {
                            startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, "Reproductor no instalado, abriendo selector", Toast.LENGTH_SHORT).show()
                            intent.setPackage(null)
                            startActivity(Intent.createChooser(intent, "Selecciona reproductor"))
                        }
                    } else {
                        startActivity(Intent.createChooser(intent, "Selecciona reproductor"))
                    }
                }
                
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
