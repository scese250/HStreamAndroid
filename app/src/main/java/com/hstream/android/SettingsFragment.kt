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
import coil.load

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
        val btnEditPlayer: android.widget.ImageButton = view.findViewById(R.id.btnEditPlayer)

        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val pm = requireContext().packageManager
        
        val savedPackage = prefs.getString("default_player", "")
        if (!savedPackage.isNullOrEmpty()) {
            try {
                val icon = pm.getApplicationIcon(savedPackage)
                btnEditPlayer.setImageDrawable(icon)
                btnEditPlayer.imageTintList = null
            } catch (e: Exception) {
                // Not found
            }
        }
        
        btnEditPlayer.setOnClickListener {
            val sheet = PlayerSelectorBottomSheet { selectedPackage, selectedIcon ->
                prefs.edit().putString("default_player", selectedPackage).apply()
                if (selectedPackage.isEmpty()) {
                    btnEditPlayer.setImageResource(android.R.drawable.ic_media_play)
                    btnEditPlayer.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#BB86FC"))
                } else {
                    btnEditPlayer.setImageDrawable(selectedIcon)
                    btnEditPlayer.imageTintList = null
                }
            }
            sheet.show(parentFragmentManager, "PlayerSelector")
        }

        val switchPrivacy: Switch = view.findViewById(R.id.switchPrivacy)
        switchPrivacy.isChecked = prefs.getBoolean("privacy_lock", false)
        switchPrivacy.setOnCheckedChangeListener { _, isChecked ->
            prefs.edit().putBoolean("privacy_lock", isChecked).apply()
        }

        setupLoginAndBlacklist(view)

        return view
    }
    
    private var searchDesign = "cover"
    private var topDesign = "cover"
    private var middleDesign = "cover"

    private fun setupLoginAndBlacklist(view: View) {
        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val isLoggedIn = prefs.getBoolean("is_logged_in", false)
        
        val btnLogin = view.findViewById<Button>(R.id.btnSettingsLogin)
        val cardIndicator = view.findViewById<androidx.cardview.widget.CardView>(R.id.sessionIndicatorCard)
        val imgAvatar = view.findViewById<android.widget.ImageView>(R.id.imgSettingsAvatar)
        val txtUsername = view.findViewById<TextView>(R.id.txtSettingsUsername)
        val btnEditBlacklist = view.findViewById<Button>(R.id.btnEditBlacklist)
        val txtBlacklist = view.findViewById<TextView>(R.id.txtBlacklistPreview)
        val switchLayout = view.findViewById<Switch>(R.id.switchLayout)
        val txtLayoutStatus = view.findViewById<TextView>(R.id.txtLayoutStatus)
        
        if (isLoggedIn) {
            cardIndicator.setCardBackgroundColor(android.graphics.Color.parseColor("#34C759"))
            val savedUser = prefs.getString("username", "User")
            txtUsername.text = savedUser
            
            val savedAvatar = prefs.getString("avatar_url", "")
            if (!savedAvatar.isNullOrEmpty()) {
                imgAvatar.load(savedAvatar) {
                    transformations(coil.transform.CircleCropTransformation())
                }
            }
            btnLogin.text = "LOGOUT"
            btnLogin.setOnClickListener {
                prefs.edit().putBoolean("is_logged_in", false).apply()
                // Borrar cookies
                requireActivity().getSharedPreferences("CookiePrefs", Context.MODE_PRIVATE).edit().clear().apply()
                Toast.makeText(context, "Logged out", Toast.LENGTH_SHORT).show()
                val intent = Intent(context, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            }
            
            btnEditBlacklist.visibility = View.VISIBLE
            switchLayout.visibility = View.VISIBLE
            txtLayoutStatus.visibility = View.VISIBLE
            
            setupDesignSwitch(view)
            loadBlacklist(txtBlacklist, view)
            
            btnEditBlacklist.setOnClickListener {
                showBlacklistEditor(txtBlacklist, view)
            }
            
        } else {
            cardIndicator.setCardBackgroundColor(android.graphics.Color.parseColor("#FF3B30"))
            txtUsername.text = "Guest"
            imgAvatar.setImageResource(android.R.drawable.ic_menu_camera)
            imgAvatar.imageTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#888888"))
            btnLogin.text = "LOGIN"
            btnLogin.setOnClickListener {
                startActivity(Intent(context, LoginActivity::class.java))
            }
            txtBlacklist.text = "Log in to view your blacklist."
            btnEditBlacklist.visibility = View.GONE
            switchLayout.visibility = View.GONE
            txtLayoutStatus.visibility = View.GONE
        }
    }
    
    private fun setupDesignSwitch(view: View) {
        val switchSearchDesign = view.findViewById<Switch>(R.id.switchLayout)
        val txtSearchDesignState = view.findViewById<TextView>(R.id.txtLayoutStatus)
        
        switchSearchDesign.isChecked = searchDesign != "thumbnail"
        txtSearchDesignState.text = if (switchSearchDesign.isChecked) "Poster" else "Thumbnail"
        
        switchSearchDesign.setOnCheckedChangeListener { _, isChecked ->
            val newValue = if (isChecked) "poster" else "thumbnail"
            txtSearchDesignState.text = if (isChecked) "Poster" else "Thumbnail"
            
            searchDesign = newValue
            topDesign = newValue
            middleDesign = newValue
            
            requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE).edit()
                .putString("searchDesign", newValue)
                .apply()
                
            switchSearchDesign.isEnabled = false
            txtSearchDesignState.text = "Saving..."
            saveDesignToServer(switchSearchDesign, txtSearchDesignState, isChecked)
        }
    }
    
    private fun saveDesignToServer(switchView: Switch, statusText: TextView, isChecked: Boolean) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = (requireActivity() as MainActivity).client
                
                val reqSettings = Request.Builder().url("https://hstream.moe/user/settings").build()
                val respSettings = client.newCall(reqSettings).execute()
                val html = respSettings.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                
                val formBody = FormBody.Builder()
                val form = doc.selectFirst("form[action$=/settings], form[action$=/user/settings]")
                if (form != null) {
                    val inputs = form.select("input, select, textarea")
                    for (input in inputs) {
                        val name = input.attr("name")
                        if (name.isEmpty()) continue
                        
                        val type = input.attr("type").lowercase()
                        if (type == "checkbox" || type == "radio") {
                            if (!input.hasAttr("checked") && name != "searchDesign" && name != "topDesign" && name != "middleDesign") {
                                continue
                            }
                        }
                        
                        val value = when (name) {
                            "searchDesign", "topDesign", "middleDesign" -> searchDesign
                            else -> input.`val`()
                        }
                        formBody.add(name, value)
                    }
                } else {
                    val token = doc.select("input[name=_token]").firstOrNull()?.attr("value") ?: ""
                    formBody.add("_token", token)
                    formBody.add("searchDesign", searchDesign)
                    formBody.add("topDesign", topDesign)
                    formBody.add("middleDesign", middleDesign)
                }
                    
                val req = Request.Builder()
                    .url("https://hstream.moe/user/settings")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://hstream.moe/user/settings")
                    .post(formBody.build())
                    .build()
                    
                val saveResp = client.newCall(req).execute()
                val saveHtml = saveResp.body?.string() ?: ""
                saveResp.close()
                
                withContext(Dispatchers.Main) {
                    switchView.isEnabled = true
                    statusText.text = if (isChecked) "Poster" else "Thumbnail"
                    
                    if (saveResp.isSuccessful || saveResp.isRedirect) {
                        Toast.makeText(context, "Layout updated successfully!", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to update layout on server.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    switchView.isEnabled = true
                    statusText.text = if (isChecked) "Poster" else "Thumbnail"
                    Toast.makeText(context, "Error saving layout: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun loadBlacklist(txtBlacklist: TextView, view: View) {
        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        val cached = prefs.getStringSet("cached_blacklist", null)
        
        if (cached != null) {
            currentBlacklist.clear()
            currentBlacklist.addAll(cached)
            txtBlacklist.text = if (currentBlacklist.isEmpty()) "None" else currentBlacklist.joinToString(", ")
        } else {
            txtBlacklist.text = "Loading..."
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val client = (requireActivity() as MainActivity).client
                    val reqApi = Request.Builder()
                        .url("https://hstream.moe/user/blacklist")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Accept", "application/json")
                        .header("X-Requested-With", "XMLHttpRequest")
                        .build()
                    val respApi = client.newCall(reqApi).execute()
                    val jsonStr = respApi.body?.string() ?: ""
                    
                    currentBlacklist.clear()
                    try {
                        val json = org.json.JSONObject(jsonStr)
                        val userTags = json.optJSONArray("usertags")
                        if (userTags != null) {
                            for (i in 0 until userTags.length()) {
                                currentBlacklist.add(userTags.getString(i))
                            }
                        }
                    } catch (e: Exception) {}
                    
                    prefs.edit().putStringSet("cached_blacklist", currentBlacklist.toSet()).apply()
                    
                    withContext(Dispatchers.Main) {
                        txtBlacklist.text = if (currentBlacklist.isEmpty()) "None" else currentBlacklist.joinToString(", ")
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        txtBlacklist.text = "Error loading settings"
                    }
                }
            }
        }
    }
    
    private fun showBlacklistEditor(txtBlacklist: TextView, root: View) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_filter, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.txtDialogTitle).text = "Edit Blacklist"

        val categories = mapOf(
            "Genres" to listOf("Comedy", "Drama", "Fantasy", "Harem", "Horror", "Mecha", "Mystery", "Romance", "Sci-Fi", "Slice of Life", "Sports", "Supernatural"),
            "Actions" to listOf("Anal", "Blowjob", "Bondage", "Creampie", "Defloration", "Exhibitionism", "Facial", "Futanari", "Gangbang", "Masturbation", "Orgy", "Rape", "Tentacle", "Threesome", "Toys"),
            "Appearance" to listOf("Big Breasts", "Dark Skin", "Elf", "Glasses", "Loli", "Maid", "Milf", "Monster", "Nurse", "Schoolgirl", "Shota", "Succubus", "Swimsuit", "Teacher"),
            "Types" to listOf("3D", "Ahegao", "Cheating", "Incest", "Inflation", "Lactation", "Mind Break", "Mind Control", "Netorare", "Ntr", "Pregnant", "Ugly Bastard", "Vanilla", "Virgin", "Yuri")
        )

        val headers = mapOf(
            "Genres" to dialogView.findViewById<TextView>(R.id.headerGenres),
            "Actions" to dialogView.findViewById<TextView>(R.id.headerActions),
            "Appearance" to dialogView.findViewById<TextView>(R.id.headerAppearance),
            "Types" to dialogView.findViewById<TextView>(R.id.headerTypes)
        )

        val grids = mapOf(
            "Genres" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridGenres),
            "Actions" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridActions),
            "Appearance" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridAppearance),
            "Types" to dialogView.findViewById<android.widget.GridLayout>(R.id.gridTypes)
        )

        val allChips = mutableListOf<TextView>()
        val categoryChips = mutableMapOf<String, MutableList<TextView>>()

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
                val chip = TextView(requireContext()).apply {
                    text = tag
                    textSize = 14f
                    setTextColor(if (currentBlacklist.contains(tag)) android.graphics.Color.parseColor("#BB86FC") else android.graphics.Color.parseColor("#E3E3E3"))
                    setPadding(16, 16, 16, 16)
                    val marginParams = android.widget.GridLayout.LayoutParams().apply {
                        setMargins(8, 8, 8, 8)
                        width = 0
                        columnSpec = android.widget.GridLayout.spec(android.widget.GridLayout.UNDEFINED, 1f)
                    }
                    layoutParams = marginParams
                    gravity = android.view.Gravity.CENTER
                    background = androidx.core.content.ContextCompat.getDrawable(requireContext(), R.drawable.bg_chip_selector)
                    isSelected = currentBlacklist.contains(tag)
                    isClickable = true
                    
                    setOnClickListener {
                        isSelected = !isSelected
                        setTextColor(if (isSelected) android.graphics.Color.parseColor("#BB86FC") else android.graphics.Color.parseColor("#E3E3E3"))
                        updateHeaderFn()
                    }
                }
                grid.addView(chip)
                allChips.add(chip)
                categoryChips.getOrPut(cat) { mutableListOf() }.add(chip)
            }
            updateHeaderFn()
        }

        dialogView.findViewById<Button>(R.id.btnDialogCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.btnDialogSave).setOnClickListener {
            currentBlacklist.clear()
            allChips.forEach { chip ->
                if (chip.isSelected) {
                    currentBlacklist.add(chip.text.toString())
                }
            }
            saveBlacklist(txtBlacklist, root)
            dialog.dismiss()
        }

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }
    
    private fun saveBlacklist(txtBlacklist: TextView, view: View) {
        val prefs = requireActivity().getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("cached_blacklist", currentBlacklist.toSet()).apply()
        txtBlacklist.text = if (currentBlacklist.isEmpty()) "None" else currentBlacklist.joinToString(", ")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val client = (requireActivity() as MainActivity).client
                
                val reqSettings = Request.Builder().url("https://hstream.moe/user/settings").build()
                val respSettings = client.newCall(reqSettings).execute()
                val html = respSettings.body?.string() ?: ""
                val doc = Jsoup.parse(html)
                var token = doc.select("form[action=https://hstream.moe/user/blacklist] input[name=_token]").attr("value")
                if (token.isEmpty()) token = doc.select("input[name=_token]").firstOrNull()?.attr("value") ?: ""
                
                val tagsJsonBuilder = StringBuilder("[")
                currentBlacklist.forEachIndexed { index, tag ->
                    tagsJsonBuilder.append("{\"value\":\"$tag\"}")
                    if (index < currentBlacklist.size - 1) tagsJsonBuilder.append(",")
                }
                tagsJsonBuilder.append("]")
                
                val formBody = FormBody.Builder()
                    .add("_token", token)
                    .add("tags", tagsJsonBuilder.toString())
                    .build()
                    
                val req = Request.Builder()
                    .url("https://hstream.moe/user/blacklist")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Referer", "https://hstream.moe/user/settings")
                    .post(formBody)
                    .build()
                    
                client.newCall(req).execute().close()
            } catch (e: Exception) {
            }
        }
    }
}

class PlayerSelectorBottomSheet(private val onPlayerSelected: (String, android.graphics.drawable.Drawable?) -> Unit) : com.google.android.material.bottomsheet.BottomSheetDialogFragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val context = requireContext()
        val scrollView = android.widget.ScrollView(context)
        val layout = android.widget.LinearLayout(context)
        layout.orientation = android.widget.LinearLayout.VERTICAL
        layout.setPadding(32, 32, 32, 32)
        layout.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        scrollView.addView(layout)

        val title = TextView(context).apply {
            text = "Select Video Player"
            textSize = 18f
            setTextColor(android.graphics.Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, 32)
        }
        layout.addView(title)

        val pm = context.packageManager
        val queryIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("http://example.com/video.mp4"), "video/*")
        }
        val resolveInfos = pm.queryIntentActivities(queryIntent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        
        val uniquePackages = mutableSetOf<String>()

        addOption(layout, "System Choice", "", androidx.core.content.ContextCompat.getDrawable(context, android.R.drawable.ic_media_play))

        for (info in resolveInfos) {
            val packageName = info.activityInfo.packageName
            if (uniquePackages.add(packageName)) {
                val appName = info.loadLabel(pm).toString()
                val icon = info.loadIcon(pm)
                addOption(layout, appName, packageName, icon)
            }
        }
        return scrollView
    }

    private fun addOption(layout: android.widget.LinearLayout, name: String, packageName: String, icon: android.graphics.drawable.Drawable?) {
        val context = layout.context
        val itemLayout = android.widget.LinearLayout(context).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 24, 0, 24)
            isClickable = true
            val outValue = android.util.TypedValue()
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener {
                onPlayerSelected(packageName, icon)
                dismiss()
            }
        }

        val imageView = android.widget.ImageView(context).apply {
            setImageDrawable(icon)
            layoutParams = android.widget.LinearLayout.LayoutParams(96, 96).apply {
                setMargins(0, 0, 32, 0)
            }
        }
        itemLayout.addView(imageView)

        val textView = TextView(context).apply {
            text = name
            textSize = 16f
            setTextColor(android.graphics.Color.WHITE)
        }
        itemLayout.addView(textView)

        layout.addView(itemLayout)
    }
}
