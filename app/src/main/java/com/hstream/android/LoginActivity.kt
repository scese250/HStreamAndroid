package com.hstream.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

class LoginActivity : AppCompatActivity() {

    private var isSubmitting = false
    private var hasLoadedLogin = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val progressLogin = findViewById<ProgressBar>(R.id.progressLogin)
        val webView = findViewById<WebView>(R.id.hiddenWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        
        // Limpiar cookies previas del WebView para no arrastrar basura
        CookieManager.getInstance().removeAllCookies(null)
        CookieManager.getInstance().flush()

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                if (url.startsWith("https://hstream.moe/login")) {
                    hasLoadedLogin = true
                    
                    if (isSubmitting) {
                        // Revisar si hubo un error al enviar
                        view.evaluateJavascript("document.querySelector('.text-red-600') ? document.querySelector('.text-red-600').innerText : ''") { result ->
                            val errorText = result?.replace("\"", "")?.trim() ?: ""
                            if (errorText.isNotEmpty()) {
                                Toast.makeText(this@LoginActivity, errorText, Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this@LoginActivity, "Credenciales inválidas o captcha fallido", Toast.LENGTH_SHORT).show()
                            }
                            progressLogin.visibility = View.GONE
                            btnLogin.isEnabled = true
                            isSubmitting = false
                        }
                    }
                } else if (url == "https://hstream.moe/" || url.contains("dashboard")) {
                    if (isSubmitting) {
                        // Éxito, extraer cookies de CookieManager y guardar en PersistentCookieJar
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
                                        // Sin expiresAt = infinito/sesión
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
        }

        // Cargar login silenciosamente para resolver el Altcha
        webView.loadUrl("https://hstream.moe/login")

        btnLogin.setOnClickListener {
            val email = editEmail.text.toString()
            val pass = editPassword.text.toString()
            
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Llenar campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!hasLoadedLogin) {
                Toast.makeText(this, "Cargando seguridad, espera unos segundos...", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            progressLogin.visibility = View.VISIBLE
            btnLogin.isEnabled = false
            isSubmitting = true

            val js = """
                var emailInput = document.querySelector('input[name="email"]');
                var passInput = document.querySelector('input[name="password"]');
                if(emailInput && passInput) {
                    emailInput.value = '$email';
                    passInput.value = '$pass';
                    document.querySelector('form').submit();
                }
            """.trimIndent()

            webView.evaluateJavascript(js, null)
        }
    }
}
