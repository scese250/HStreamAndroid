package com.hstream.android

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import org.json.JSONArray
import org.json.JSONObject

class PersistentCookieJar(context: Context) : CookieJar {
    private val prefs: SharedPreferences = context.getSharedPreferences("CookiePrefs", Context.MODE_PRIVATE)

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val domain = url.host
        val jsonArray = JSONArray()
        for (cookie in cookies) {
            val json = JSONObject()
            json.put("name", cookie.name)
            json.put("value", cookie.value)
            json.put("domain", cookie.domain)
            json.put("path", cookie.path)
            json.put("secure", cookie.secure)
            json.put("httpOnly", cookie.httpOnly)
            json.put("expiresAt", cookie.expiresAt)
            jsonArray.put(json)
        }
        prefs.edit().putString(domain, jsonArray.toString()).apply()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val domain = url.host
        val cookiesStr = prefs.getString(domain, null) ?: return emptyList()
        val cookiesList = mutableListOf<Cookie>()
        try {
            val jsonArray = JSONArray(cookiesStr)
            for (i in 0 until jsonArray.length()) {
                val json = jsonArray.getJSONObject(i)
                val builder = Cookie.Builder()
                    .name(json.getString("name"))
                    .value(json.getString("value"))
                    .domain(json.getString("domain"))
                    .path(json.getString("path"))
                    .expiresAt(json.getLong("expiresAt"))
                if (json.getBoolean("secure")) builder.secure()
                if (json.getBoolean("httpOnly")) builder.httpOnly()
                cookiesList.add(builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return cookiesList
    }
}
