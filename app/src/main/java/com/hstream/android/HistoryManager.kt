package com.hstream.android

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object HistoryManager {
    private const val PREFS_NAME = "HStreamHistory"
    private const val KEY_HISTORY = "history_list"
    private const val MAX_HISTORY = 100 // Keep last 100 watched episodes

    fun getHistory(context: Context): List<VideoItem> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val jsonStr = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val list = mutableListOf<VideoItem>()
        try {
            val jsonArray = JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val item = VideoItem(
                    title = obj.optString("title", ""),
                    url = obj.optString("url", ""),
                    posterUrl = obj.optString("posterUrl", "")
                )
                list.add(item)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun addHistory(context: Context, item: VideoItem) {
        val list = getHistory(context).toMutableList()
        
        // Remove if it already exists to put it at the top
        list.removeAll { it.url == item.url }
        
        list.add(0, item) // Add to top
        
        if (list.size > MAX_HISTORY) {
            list.removeAt(list.size - 1)
        }
        
        saveHistory(context, list)
    }

    fun removeHistory(context: Context, url: String) {
        val list = getHistory(context).toMutableList()
        list.removeAll { it.url == url }
        saveHistory(context, list)
    }

    private fun saveHistory(context: Context, list: List<VideoItem>) {
        val jsonArray = JSONArray()
        for (item in list) {
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("posterUrl", item.posterUrl)
            jsonArray.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_HISTORY, jsonArray.toString()).apply()
    }
}
