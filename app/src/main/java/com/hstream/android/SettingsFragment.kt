package com.hstream.android

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Request
import org.jsoup.Jsoup

class SettingsFragment : Fragment() {

    private val allBlacklistTags = arrayOf(
        "3D", "Ahegao", "Anal", "BDSM", "Big Breasts", "Blowjob", "Bondage",
        "Cheating", "Comedy", "Creampie", "Dark Skin", "Defloration",
        "Demon", "Elf", "Exhibitionism", "Facial", "Fantasy", "Futanari",
        "Gangbang", "Glasses", "Harem", "Incest", "Inflation", "Lactation",
        "Loli", "Maid", "Masturbation", "Milf", "Mind Break", "Mind Control",
        "Monster", "Netorare", "Ntr", "Nurse", "Orgy", "Pregnant", "Rape",
        "Romance", "Schoolgirl", "Shota", "Succubus", "Swimsuit", "Teacher",
        "Tentacle", "Threesome", "Toys", "Ugly Bastard", "Vanilla", "Virgin", "Yuri"
    ) // Abridged standard list for selection
    
    private var currentBlacklist = mutableListOf<String>()
    private var csrfToken = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_settings, container, false)
        val spinner: Spinner = view.findViewById(R.id.spinnerPlayer)

        val pm = requireContext().packageManager
        val queryIntent = Intent(Intent.ACTION_VIEW)
        queryIntent.setDataAndType(Uri.parse("http://example.com/video.mp4"), "video/*")
        val resolveInfos = pm.queryIntentActivities(queryIntent, 0)

        val dynamicPlayers = mutableListOf<Pair<String, String>>()
        dynamicPlayers.add(Pair("Preguntar Siempre", ""))

        for (info in resolveInfos) {
            val appName = info.loadLabel(pm).toString()
            val packageName = info.activityInfo.packageName
            if (dynamicPlayers.none { it.second == packageName }) {
                dynamicPlayers.add(Pair(appName, packageName))
            }
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_item,
            dynamicPlayers.map { it.first }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val savedPackage = prefs.getString("default_player", "")
        
        val selectedIndex = dynamicPlayers.indexOfFirst { it.second == savedPackage }.takeIf { it >= 0 } ?: 0
        spinner.setSelection(selectedIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedPackage = dynamicPlayers[position].second
                prefs.edit().putString("default_player", selectedPackage).apply()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        val switchPrivacy: Switch = view.findViewById(R.id.switchPrivacy)
        switchPrivacy.isChecked = prefs.getBoolean("privacy_lock", false)
        switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("privacy_lock", isChecked).apply()
        }

        setupLoginAndBlacklist(view)

        return view
    }
    
    private fun setupLoginAndBlacklist(view: View) {
        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        
        val btnLogin = view.findViewById<Button>(R.id.btnLogin)
        val txtLoginStatus = view.findViewById<TextView>(R.id.txtLoginStatus)
        val btnEditBlacklist = view.findViewById<Button>(R.id.btnEditBlacklist)
        val txtBlacklist = view.findViewById<TextView>(R.id.txtBlacklist)
        
        if (isLoggedIn) {
            txtLoginStatus.text = "Sesión Activa"
            btnLogin.text = "Log Out"
            btnLogin.setOnClickListener {
                prefs.edit().putBoolean("is_logged_in", false).apply()
                // Borrar cookies
                requireActivity().getSharedPreferences("CookiePrefs", Context.MODE_PRIVATE).edit().clear().apply()
                Toast.makeText(context, "Sesión cerrada", Toast.LENGTH_SHORT).show()
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            
            loadBlacklist(txtBlacklist)
            
            btnEditBlacklist.setOnClickListener {
                showBlacklistEditor(txtBlacklist)
            }
            
        } else {
            txtLoginStatus.text = "No has iniciado sesión"
            btnLogin.text = "Login"
            btnLogin.setOnClickListener {
                startActivity(Intent(context, LoginActivity::class.java))
            }
            txtBlacklist.text = "Inicia sesión para ver tu blacklist."
            btnEditBlacklist.visibility = View.GONE
        }
    }
    
    private fun loadBlacklist(txtBlacklist: TextView) {
        txtBlacklist.text = "Cargando..."
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                // Sincronizar cookies de OkHttp a WebView
                val prefs = requireActivity().getSharedPreferences("CookiePrefs", Context.MODE_PRIVATE)
                val cookiesStr = prefs.getString("hstream.moe", null)
                if (cookiesStr != null) {
                    val jsonArray = org.json.JSONArray(cookiesStr)
                    for (i in 0 until jsonArray.length()) {
                        val json = jsonArray.getJSONObject(i)
                        val cookieString = "${json.getString("name")}=${json.getString("value")}; domain=${json.getString("domain")}; path=/"
                        android.webkit.CookieManager.getInstance().setCookie("https://hstream.moe", cookieString)
                    }
                    android.webkit.CookieManager.getInstance().flush()
                }

                val webView = android.webkit.WebView(requireContext())
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView, url: String) {
                        if (url.contains("login")) {
                            txtBlacklist.text = "Error: Sesión expirada"
                            return
                        }
                        
                        val js = """
                            (function() {
                                var tags = Array.from(document.querySelectorAll('tag')).map(t => t.getAttribute('value'));
                                var token = document.querySelector('form[action="https://hstream.moe/user/blacklist"] input[name="_token"]');
                                return JSON.stringify({ tags: tags, token: token ? token.value : '' });
                            })();
                        """.trimIndent()
                        
                        view.evaluateJavascript(js) { result ->
                            try {
                                val cleanResult = result.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\")
                                val json = org.json.JSONObject(cleanResult)
                                csrfToken = json.optString("token", "")
                                
                                val tagsArray = json.optJSONArray("tags")
                                currentBlacklist.clear()
                                if (tagsArray != null) {
                                    for (i in 0 until tagsArray.length()) {
                                        currentBlacklist.add(tagsArray.getString(i))
                                    }
                                }
                                
                                if (currentBlacklist.isEmpty()) {
                                    txtBlacklist.text = "Ninguno"
                                } else {
                                    txtBlacklist.text = currentBlacklist.joinToString(", ")
                                }
                            } catch (e: Exception) {
                                txtBlacklist.text = "Error leyendo tags del DOM"
                            }
                        }
                    }
                }
                webView.loadUrl("https://hstream.moe/user/settings")
            } catch (e: Exception) {
                txtBlacklist.text = "Error cargando blacklist"
            }
        }
    }
    
    private fun showBlacklistEditor(txtBlacklist: TextView) {
        val checkedItems = BooleanArray(allBlacklistTags.size)
        for (i in allBlacklistTags.indices) {
            checkedItems[i] = currentBlacklist.contains(allBlacklistTags[i])
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Editar Blacklist")
            .setMultiChoiceItems(allBlacklistTags, checkedItems) { _, which, isChecked ->
                val tag = allBlacklistTags[which]
                if (isChecked) {
                    if (!currentBlacklist.contains(tag)) currentBlacklist.add(tag)
                } else {
                    currentBlacklist.remove(tag)
                }
            }
            .setPositiveButton("Guardar") { _, _ ->
                saveBlacklist(txtBlacklist)
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    
    private fun saveBlacklist(txtBlacklist: TextView) {
        txtBlacklist.text = "Guardando..."
        
        CoroutineScope(Dispatchers.Main).launch {
            try {
                val webView = android.webkit.WebView(requireContext())
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = object : android.webkit.WebViewClient() {
                    var submitted = false
                    override fun onPageFinished(view: android.webkit.WebView, url: String) {
                        if (!submitted) {
                            submitted = true
                            val tagsJsonBuilder = StringBuilder("[")
                            currentBlacklist.forEachIndexed { index, tag ->
                                tagsJsonBuilder.append("{\\\"value\\\":\\\"$tag\\\"}")
                                if (index < currentBlacklist.size - 1) tagsJsonBuilder.append(",")
                            }
                            tagsJsonBuilder.append("]")
                            val jsTags = tagsJsonBuilder.toString()
                            
                            val js = """
                                var input = document.querySelector('input[name="tags"]');
                                if (input) {
                                    input.value = '$jsTags';
                                    document.querySelector('form[action="https://hstream.moe/user/blacklist"]').submit();
                                }
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        } else {
                            Toast.makeText(context, "Blacklist guardado", Toast.LENGTH_SHORT).show()
                            loadBlacklist(txtBlacklist)
                        }
                    }
                }
                webView.loadUrl("https://hstream.moe/user/settings")
            } catch (e: Exception) {
                Toast.makeText(context, "Error al iniciar guardado", Toast.LENGTH_SHORT).show()
                loadBlacklist(txtBlacklist)
            }
        }
    }
}
