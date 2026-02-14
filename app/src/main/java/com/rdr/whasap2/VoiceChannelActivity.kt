package com.rdr.whasap2

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope

class VoiceChannelActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_voice_channel)

        val channelName = intent.getStringExtra("CHANNEL_NAME") ?: "Canal de voz"
        val channelId = intent.getStringExtra("CHANNEL_ID") ?: ""
        val guildId = intent.getStringExtra("GUILD_ID") ?: ""
        val membersString = intent.getStringExtra("MEMBERS") ?: ""

        findViewById<TextView>(R.id.voice_channel_name).text = "\uD83D\uDD0A $channelName"
        findViewById<ImageView>(R.id.btn_back_voice).setOnClickListener { finish() }

        // Display connected members
        val membersContainer = findViewById<LinearLayout>(R.id.members_list_container)
        val members = if (membersString.isNotEmpty()) membersString.split("||") else emptyList()

        if (members.isNotEmpty()) {
            findViewById<TextView>(R.id.connected_count).text = "${members.size} conectados"

            for (member in members) {
                val tv = TextView(this).apply {
                    text = "  \uD83D\uDFE2 $member"
                    textSize = 16f
                    setTextColor(0xFF333333.toInt())
                    setPadding(16, 12, 16, 12)
                }
                membersContainer.addView(tv)
            }
        } else {
            findViewById<TextView>(R.id.connected_count).text = "Canal vacío"
        }

        // Join button
        findViewById<TextView>(R.id.btn_join_voice).setOnClickListener {
            joinVoiceChannel(guildId, channelId)
        }
    }

    private fun joinVoiceChannel(guildId: String, channelId: String) {
        // Discord voice requires WebSocket Gateway + WebRTC
        // For now, show a message about the limitation
        Toast.makeText(
            this,
            "La conexión a voz requiere Discord Gateway WebSocket.\nEsta función está en desarrollo.",
            Toast.LENGTH_LONG
        ).show()
    }
}
