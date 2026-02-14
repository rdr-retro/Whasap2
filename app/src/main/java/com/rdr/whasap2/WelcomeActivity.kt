package com.rdr.whasap2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import java.security.Security

class WelcomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Install Conscrypt as the primary security provider
        // This replaces the old system OpenSSL with modern BoringSSL
        installConscrypt()
        
        setContentView(R.layout.activity_welcome)
        
        // Check if token already exists
        checkTokenAndNavigate()

        val inputToken = findViewById<EditText>(R.id.input_token)
        val btnConnect = findViewById<Button>(R.id.btn_connect)
        val btnHelp = findViewById<Button>(R.id.btn_help)
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)

        btnConnect.setOnClickListener {
            val token = inputToken.text.toString().trim()
            if (token.isNotEmpty()) {
                prefs.edit().putString("DISCORD_TOKEN", token).apply()

                // Fetch User ID
                lifecycleScope.launchWhenCreated {
                     try {
                         val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@WelcomeActivity)
                         val user = api.getUser()
                         prefs.edit().putString("MY_USER_ID", user.id).apply()
                         
                         startActivity(Intent(this@WelcomeActivity, MainActivity::class.java))
                         finish()
                     } catch (e: Exception) {
                         e.printStackTrace()
                         val errorText = findViewById<android.widget.TextView>(R.id.text_error_log)
                         errorText.visibility = android.view.View.VISIBLE
                         val causeMessage = e.cause?.message ?: "-"
                         errorText.text = getString(R.string.error_with_cause, e.message ?: "-", causeMessage)
                         Toast.makeText(this@WelcomeActivity, R.string.toast_connection_error, Toast.LENGTH_SHORT).show()
                     }
                }
            } else {
                Toast.makeText(this, R.string.toast_enter_valid_token, Toast.LENGTH_SHORT).show()
            }
        }

        btnHelp.setOnClickListener {
            showTokenHelpDialog()
        }
    }

    private fun installConscrypt() {
        try {
            val conscrypt = org.conscrypt.Conscrypt.newProvider()
            // Insert as the first (highest priority) security provider
            Security.insertProviderAt(conscrypt, 1)
            android.util.Log.d("WelcomeActivity", "Conscrypt provider installed successfully")
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("WelcomeActivity", "Failed to install Conscrypt: ${e.message}")
        }
    }

    private fun checkTokenAndNavigate() {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val savedToken = prefs.getString("DISCORD_TOKEN", null)

        if (!savedToken.isNullOrBlank()) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun showTokenHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_token_help_title)
            .setMessage(getString(R.string.dialog_token_help_message))
            .setPositiveButton(R.string.dialog_ok_understood, null)
            .show()
    }
}
