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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = (requireActivity() as MainActivity).client
                val req = Request.Builder()
                    .url("https://hstream.moe/user/settings")
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
                val resp = client.newCall(req).execute()
                val html = resp.body?.string() ?: ""
                
                val doc = Jsoup.parse(html)
                // Obtener CSRF token del form de blacklist
                csrfToken = doc.select("form[action=https://hstream.moe/user/blacklist] input[name=_token]").attr("value")
                
                var tagsInput = ""
                val inputElement = doc.selectFirst("input[name=tags], textarea[name=tags]")
                if (inputElement != null) {
                    tagsInput = inputElement.attr("value")
                    if (tagsInput.isEmpty()) {
                        tagsInput = inputElement.text()
                    }
                }
                
                currentBlacklist.clear()
                
                if (tagsInput.startsWith("[")) {
                    try {
                        val jsonArray = org.json.JSONArray(tagsInput)
                        for (i in 0 until jsonArray.length()) {
                            val obj = jsonArray.getJSONObject(i)
                            if (obj.has("value")) {
                                currentBlacklist.add(obj.getString("value"))
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else if (tagsInput.isNotEmpty()) {
                    currentBlacklist.addAll(tagsInput.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                }
                
                if (currentBlacklist.isEmpty()) {
                    val tags = doc.select("tag").map { it.attr("value") }
                    currentBlacklist.addAll(tags)
                }
                
                // Paracaídas final de Ponytail: si todavía está vacío, buscar en todo el HTML en crudo
                if (currentBlacklist.isEmpty()) {
                    val regex = Regex("""\"value\"[ ]*:[ ]*\"([^\"]+)\"""")
                    val matches = regex.findAll(html)
                    for (match in matches) {
                        val tag = match.groupValues[1]
                        if (allBlacklistTags.contains(tag)) {
                            currentBlacklist.add(tag)
                        }
                    }
                    val uniqueTags = currentBlacklist.distinct().toMutableList()
                    currentBlacklist.clear()
                    currentBlacklist.addAll(uniqueTags)
                }
                
                withContext(Dispatchers.Main) {
                    if (currentBlacklist.isEmpty()) {
                        txtBlacklist.text = "Ninguno"
                    } else {
                        txtBlacklist.text = currentBlacklist.joinToString(", ")
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    txtBlacklist.text = "Error cargando blacklist"
                }
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
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = (requireActivity() as MainActivity).client
                
                // Tagify usa formato JSON: [{"value":"Tag1"},{"value":"Tag2"}]
                val tagsJsonBuilder = StringBuilder("[")
                currentBlacklist.forEachIndexed { index, tag ->
                    tagsJsonBuilder.append("{\"value\":\"$tag\"}")
                    if (index < currentBlacklist.size - 1) tagsJsonBuilder.append(",")
                }
                tagsJsonBuilder.append("]")
                
                val formBody = FormBody.Builder()
                    .add("_token", csrfToken)
                    .add("tags", tagsJsonBuilder.toString())
                    .build()
                    
                val req = Request.Builder()
                    .url("https://hstream.moe/user/blacklist")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://hstream.moe/user/settings")
                    .post(formBody)
                    .build()
                    
                client.newCall(req).execute().close() // El post hace redirección a veces o devuelve éxito
                
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Blacklist guardado", Toast.LENGTH_SHORT).show()
                    loadBlacklist(txtBlacklist)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error al guardar", Toast.LENGTH_SHORT).show()
                    loadBlacklist(txtBlacklist) // Restaurar
                }
            }
        }
    }
}
