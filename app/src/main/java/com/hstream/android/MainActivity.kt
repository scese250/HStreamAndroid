package com.hstream.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnVideo1).setOnClickListener {
            playVideo("https://hstream.moe/hentai/chiisana-tsubomi-no-sono-oku-ni-4")
        }

        findViewById<Button>(R.id.btnVideo2).setOnClickListener {
            playVideo("https://hstream.moe/hentai/heart-mark-oome-1")
        }

        findViewById<Button>(R.id.btnVideo3).setOnClickListener {
            playVideo("https://hstream.moe/hentai/shinsei-futanari-idol-dekatama-kei-1")
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
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
