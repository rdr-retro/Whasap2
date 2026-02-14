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
        setContentView(R.layout.activity_intro)

        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val spinner = findViewById<Spinner>(R.id.spinner_language)
        val btnContinue = findViewById<Button>(R.id.btn_continue_intro)

        val options = listOf(
            LanguageOption("Español", "es"),
            LanguageOption("English", "en"),
            LanguageOption("Français", "fr"),
            LanguageOption("Deutsch", "de"),
            LanguageOption("Italiano", "it"),
            LanguageOption("Português", "pt"),
            LanguageOption("Русский", "ru"),
            LanguageOption("中文", "zh"),
            LanguageOption("日本語", "ja"),
            LanguageOption("العربية", "ar")
        )

        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            options.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        val savedCode = prefs.getString("APP_LANGUAGE", "es") ?: "es"
        val savedIndex = options.indexOfFirst { it.code == savedCode }.takeIf { it >= 0 } ?: 0
        spinner.setSelection(savedIndex)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("APP_LANGUAGE", options[position].code).apply()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        btnContinue.setOnClickListener {
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
}
