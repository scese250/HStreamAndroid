package com.hstream.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class LoginActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val progressLogin = findViewById<ProgressBar>(R.id.progressLogin)
        val webView = findViewById<WebView>(R.id.loginWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/114.0.0.0 Safari/537.36"
        
        // Limpiar cookies previas del WebView para forzar login nuevo
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                progressLogin.visibility = View.GONE
                
                // Si la URL es la raíz o el dashboard, el login fue exitoso
                if (url == "https://hstream.moe/" || url.contains("/dashboard") || url.contains("/user/")) {
                    val cookieStr = CookieManager.getInstance().getCookie("https://hstream.moe")
                    if (cookieStr != null) {
                        val cookiesList = mutableListOf<Cookie>()
                        for (pair in cookieStr.split(";")) {
                            val parts = pair.trim().split("=", limit = 2)
                            if (parts.size == 2) {
                                val c = Cookie.Builder()
                                    .name(parts[0])
                                    .value(parts[1])
                                    .domain("hstream.moe")
                                    .path("/")
                                    .build()
                                cookiesList.add(c)
                            }
                        }
                        val httpUrl = "https://hstream.moe".toHttpUrlOrNull()!!
                        PersistentCookieJar(this@LoginActivity).saveFromResponse(httpUrl, cookiesList)
                    }

                    getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE).edit().putBoolean("is_logged_in", true).apply()
                    Toast.makeText(this@LoginActivity, "Login exitoso", Toast.LENGTH_SHORT).show()

                    val intent = Intent(this@LoginActivity, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            }
        }

        webView.loadUrl("https://hstream.moe/login")
    }
}
