package com.rdr.whasap2

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject

class NotificationsActivity : AppCompatActivity() {

    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<NotificationItem>()
    private val mutedChannels = mutableSetOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notifications)

        findViewById<View>(R.id.btn_back_notif).setOnClickListener { finish() }

        loadMutedChannels()
        loadNotifications()

        val recycler = findViewById<RecyclerView>(R.id.notifications_recycler)
        recycler.layoutManager = LinearLayoutManager(this)

        adapter = NotificationAdapter(
            notifications,
            mutedChannels,
            onMuteToggle = { channelId, muted ->
                if (muted) {
                    mutedChannels.add(channelId)
                } else {
                    mutedChannels.remove(channelId)
                }
                saveMutedChannels()
            },
            onItemClick = { item ->
                // Open the chat
                val intent = Intent(this, ChatDetailActivity::class.java)
                intent.putExtra("CHAT_ID", item.channelId)
                intent.putExtra("CHAT_NAME", item.sender)
                startActivity(intent)
            }
        )
        recycler.adapter = adapter

        // Update empty state
        updateEmptyState()

        // Clear all button
        findViewById<TextView>(R.id.btn_clear_all).setOnClickListener {
            clearAllNotifications()
            Toast.makeText(this, "Notificaciones borradas ✓", Toast.LENGTH_SHORT).show()
        }

        // Global mute checkbox
        val cbMute = findViewById<CheckBox>(R.id.cb_mute)
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        cbMute.isChecked = !NotificationSettings.areNotificationsEnabled(this)
        cbMute.setOnCheckedChangeListener { _, isChecked ->
            val enabled = !isChecked
            NotificationSettings.setNotificationsEnabled(this, enabled)

            val serviceIntent = Intent(this, MessagePollingService::class.java)
            if (enabled) {
                // Avoid burst on re-enable: first cycle only updates checkpoints.
                NotificationSettings.markBaselineOnNextStart(this)
                startService(serviceIntent)
                Toast.makeText(this, "Notificaciones activadas 🔔", Toast.LENGTH_SHORT).show()
            } else {
                stopService(serviceIntent)
                Toast.makeText(this, "Notificaciones desactivadas 🔕", Toast.LENGTH_SHORT).show()
            }
        }

        // Mark all notifications as read
        prefs.edit().putInt("UNREAD_NOTIF_COUNT", 0).apply()
    }

    private fun loadNotifications() {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString("PENDING_NOTIFICATIONS", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            notifications.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                notifications.add(
                    NotificationItem(
                        sender = obj.optString("sender", ""),
                        content = obj.optString("content", ""),
                        time = obj.optString("time", ""),
                        channelId = obj.optString("channelId", ""),
                        channelName = obj.optString("channelName", "")
                    )
                )
            }
            // Most recent first
            notifications.reverse()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun clearAllNotifications() {
        notifications.clear()
        adapter.notifyDataSetChanged()
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("PENDING_NOTIFICATIONS", "[]")
            .putInt("UNREAD_NOTIF_COUNT", 0)
            .apply()
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val emptyState = findViewById<TextView>(R.id.empty_state)
        if (notifications.isEmpty()) {
            emptyState.visibility = View.VISIBLE
            findViewById<RecyclerView>(R.id.notifications_recycler).visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            findViewById<RecyclerView>(R.id.notifications_recycler).visibility = View.VISIBLE
        }
    }

    private fun loadMutedChannels() {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        val muted = prefs.getString("MUTED_CHANNELS", "") ?: ""
        mutedChannels.clear()
        if (muted.isNotEmpty()) {
            mutedChannels.addAll(muted.split(",").filter { it.isNotEmpty() })
        }
    }

    private fun saveMutedChannels() {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString("MUTED_CHANNELS", mutedChannels.joinToString(",")).apply()
    }

    companion object {
        /**
         * Store a notification in SharedPreferences for the notification center.
         * Called by MessagePollingService.
         */
        fun storeNotification(context: Context, sender: String, content: String, channelId: String, channelName: String) {
            if (!NotificationSettings.areNotificationsEnabled(context)) return

            val prefs = context.getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
            val json = prefs.getString("PENDING_NOTIFICATIONS", "[]") ?: "[]"
            val arr = try { JSONArray(json) } catch (e: Exception) { JSONArray() }

            val obj = JSONObject().apply {
                put("sender", sender)
                put("content", content)
                put("channelId", channelId)
                put("channelName", channelName)
                put("time", java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
            }
            arr.put(obj)

            // Keep max 50 notifications (JSONArray.remove() not available on API < 19)
            if (arr.length() > 50) {
                val trimmed = JSONArray()
                for (i in (arr.length() - 50) until arr.length()) {
                    trimmed.put(arr.get(i))
                }
                // Replace arr contents by using trimmed for saving
                prefs.edit()
                    .putString("PENDING_NOTIFICATIONS", trimmed.toString())
                    .putInt("UNREAD_NOTIF_COUNT", prefs.getInt("UNREAD_NOTIF_COUNT", 0) + 1)
                    .apply()
                return
            }

            val unreadCount = prefs.getInt("UNREAD_NOTIF_COUNT", 0) + 1
            prefs.edit()
                .putString("PENDING_NOTIFICATIONS", arr.toString())
                .putInt("UNREAD_NOTIF_COUNT", unreadCount)
                .apply()
        }

        fun isChannelMuted(context: Context, channelId: String): Boolean {
            if (!NotificationSettings.areNotificationsEnabled(context)) return true

            val prefs = context.getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
            val muted = prefs.getString("MUTED_CHANNELS", "") ?: ""
            return channelId in muted.split(",")
        }
    }
}
