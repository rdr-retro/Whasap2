package com.rdr.whasap2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import androidx.appcompat.app.AppCompatActivity

class IntroActivity : AppCompatActivity() {

    private data class LanguageOption(val label: String, val code: String)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(NotificationSettings.KEY_INTRO_SEEN, false) && savedInstanceState == null) {
            goToNextScreen(prefs)
            return
        }

        // Show intro only once, even if the app is closed before tapping Continue.
        prefs.edit().putBoolean(NotificationSettings.KEY_INTRO_SEEN, true).apply()

        setContentView(R.layout.activity_intro)
        val spinner = findViewById<Spinner>(R.id.spinner_language)
        val btnContinue = findViewById<Button>(R.id.btn_continue_intro)

        val options = listOf(
            LanguageOption(getString(R.string.lang_spanish), "es"),
            LanguageOption(getString(R.string.lang_english), "en"),
            LanguageOption(getString(R.string.lang_french), "fr"),
            LanguageOption(getString(R.string.lang_german), "de"),
            LanguageOption(getString(R.string.lang_italian), "it"),
            LanguageOption(getString(R.string.lang_portuguese), "pt")
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        var savedCode = LocaleManager.getSavedLanguage(this)
        val savedIndex = options.indexOfFirst { it.code == savedCode }.takeIf { it >= 0 } ?: 0
        spinner.setSelection(savedIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCode = options[position].code
                if (selectedCode != savedCode) {
                    savedCode = selectedCode
                    LocaleManager.setLanguage(this@IntroActivity, selectedCode)
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnContinue.setOnClickListener {
            goToNextScreen(prefs)
        }
    }

    private fun goToNextScreen(prefs: android.content.SharedPreferences) {
        val token = prefs.getString("DISCORD_TOKEN", null)
        val next = if (token.isNullOrBlank()) {
            WelcomeActivity::class.java
        } else {
            MainActivity::class.java
        }
        startActivity(Intent(this, next))
        finish()
    }
}
