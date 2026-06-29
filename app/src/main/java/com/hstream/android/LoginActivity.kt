package com.hstream.android

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup

class LoginActivity : AppCompatActivity() {

    private lateinit var client: OkHttpClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Usar un cliente temporal que comparta el mismo CookieJar
        client = OkHttpClient.Builder()
            .cookieJar(PersistentCookieJar(this))
            .build()

        val editEmail = findViewById<EditText>(R.id.editEmail)
        val editPassword = findViewById<EditText>(R.id.editPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val progressLogin = findViewById<ProgressBar>(R.id.progressLogin)

        btnLogin.setOnClickListener {
            val email = editEmail.text.toString()
            val pass = editPassword.text.toString()
            
            if (email.isEmpty() || pass.isEmpty()) {
                Toast.makeText(this, "Llenar campos", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            progressLogin.visibility = View.VISIBLE
            btnLogin.isEnabled = false

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val req1 = Request.Builder()
                        .url("https://hstream.moe/login")
                        .header("User-Agent", "Mozilla/5.0")
                        .build()
                        
                    val resp1 = client.newCall(req1).execute()
                    val html = resp1.body?.string() ?: throw Exception("Sin respuesta")
                    
                    val doc = Jsoup.parse(html)
                    val tokenInput = doc.selectFirst("input[name=_token]")
                    val token = tokenInput?.attr("value") ?: throw Exception("No CSRF token")
                    
                    val formBody = FormBody.Builder()
                        .add("_token", token)
                        .add("email", email)
                        .add("password", pass)
                        .add("remember", "on")
                        .build()
                        
                    val req2 = Request.Builder()
                        .url("https://hstream.moe/login")
                        .header("User-Agent", "Mozilla/5.0")
                        .header("Referer", "https://hstream.moe/login")
                        .post(formBody)
                        .build()
                        
                    val resp2 = client.newCall(req2).execute()
                    val responseHtml = resp2.body?.string() ?: ""
                    
                    // Si el login fue exitoso, no habrá un form de login en la respuesta o redirigirá
                    withContext(Dispatchers.Main) {
                        progressLogin.visibility = View.GONE
                        btnLogin.isEnabled = true
                        
                        if ("Dashboard" in responseHtml || "Profile" in responseHtml || "cuentas1912" in responseHtml || "Log Out" in responseHtml || !responseHtml.contains("name=\"email\"")) {
                            Toast.makeText(this@LoginActivity, "Login exitoso", Toast.LENGTH_SHORT).show()
                            getSharedPreferences("HStreamPrefs", Context.MODE_PRIVATE).edit().putBoolean("is_logged_in", true).apply()
                            
                            val intent = Intent(this@LoginActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } else {
                            Toast.makeText(this@LoginActivity, "Credenciales inválidas", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        progressLogin.visibility = View.GONE
                        btnLogin.isEnabled = true
                        Toast.makeText(this@LoginActivity, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
