package com.rdr.whasap2

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.rdr.whasap2.api.DiscordGuild
import com.rdr.whasap2.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var isReorderMode = false
    private var itemTouchHelper: ItemTouchHelper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Check if token exists
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        if (!prefs.contains("DISCORD_TOKEN")) {
            val next = if (prefs.getBoolean(NotificationSettings.KEY_INTRO_SEEN, false)) {
                WelcomeActivity::class.java
            } else {
                IntroActivity::class.java
            }
            startActivity(android.content.Intent(this, next))
            finish()
            return
        }

        setupRecyclerView()
        setupReorderButton()
        fetchDiscordChats()
        setupNotificationBell()

        // Keep polling service aligned with notification toggle.
        val serviceIntent = android.content.Intent(this, MessagePollingService::class.java)
        if (NotificationSettings.areNotificationsEnabled(this)) {
            startService(serviceIntent)
        } else {
            stopService(serviceIntent)
        }
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
    }

    private fun setupNotificationBell() {
        binding.headerNotifications.setOnClickListener {
            startActivity(android.content.Intent(this, NotificationsActivity::class.java))
        }
        updateNotificationBadge()
    }

    private fun updateNotificationBadge() {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val count = prefs.getInt("UNREAD_NOTIF_COUNT", 0)
        val badge = binding.notificationBadge
        if (count > 0) {
            badge.visibility = android.view.View.VISIBLE
            badge.text = if (count > 9) "9+" else count.toString()
        } else {
            badge.visibility = android.view.View.GONE
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = binding.chatRecyclerView
        recyclerView.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
        val adapter = ChatAdapter(mutableListOf()) { chat ->
            if (chat.isGuild) {
                val intent = android.content.Intent(this, GuildChannelsActivity::class.java)
                intent.putExtra("GUILD_ID", chat.id)
                intent.putExtra("GUILD_NAME", chat.name)
                startActivity(intent)
            } else {
                val intent = android.content.Intent(this, ChatDetailActivity::class.java)
                intent.putExtra("CHAT_NAME", chat.name)
                intent.putExtra("CHAT_ID", chat.id)
                startActivity(intent)
            }
        }
        recyclerView.adapter = adapter

        // Setup drag & drop
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled(): Boolean = false

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.adapterPosition
                val to = target.adapterPosition
                adapter.moveItem(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    viewHolder?.itemView?.alpha = 0.7f
                }
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.alpha = 1.0f
            }
        }

        itemTouchHelper = ItemTouchHelper(callback)
        itemTouchHelper!!.attachToRecyclerView(recyclerView)
        adapter.itemTouchHelper = itemTouchHelper
    }

    private fun setupReorderButton() {
        binding.headerEdit.setOnClickListener {
            isReorderMode = !isReorderMode
            val adapter = binding.chatRecyclerView.adapter as ChatAdapter
            adapter.isReorderMode = isReorderMode

            if (isReorderMode) {
                binding.headerTitle.text = getString(R.string.main_header_reorder)
                Toast.makeText(this, R.string.toast_reorder_chats, Toast.LENGTH_SHORT).show()
            } else {
                binding.headerTitle.text = getString(R.string.main_header_chats)
                // Save the new order
                saveChatOrder(adapter.getChats())
                Toast.makeText(this, R.string.toast_order_saved, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveChatOrder(chats: List<Chat>) {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val orderedIds = chats.map { it.id }
        prefs.edit().putString("CHAT_ORDER", orderedIds.joinToString(",")).apply()
    }

    private fun loadChatOrder(): List<String> {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val orderString = prefs.getString("CHAT_ORDER", null) ?: return emptyList()
        return orderString.split(",").filter { it.isNotEmpty() }
    }

    private fun applySavedOrder(chats: List<Chat>): List<Chat> {
        val savedOrder = loadChatOrder()
        if (savedOrder.isEmpty()) return chats

        val chatMap = chats.associateBy { it.id }
        val orderedChats = mutableListOf<Chat>()

        // Add chats in saved order
        for (id in savedOrder) {
            chatMap[id]?.let { orderedChats.add(it) }
        }

        // Add any new chats not in saved order at the end
        for (chat in chats) {
            if (chat.id !in savedOrder) {
                orderedChats.add(chat)
            }
        }

        return orderedChats
    }

    private fun saveGuildCache(guilds: List<DiscordGuild>) {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val arr = JSONArray()
        guilds.forEach { guild ->
            val guildId = guild.id ?: return@forEach
            val obj = JSONObject().apply {
                put("id", guildId)
                put("name", guild.name ?: getString(R.string.main_server))
                put("icon", guild.icon)
            }
            arr.put(obj)
        }
        prefs.edit().putString("GUILD_CACHE", arr.toString()).apply()
    }

    private fun loadGuildCache(): List<DiscordGuild> {
        val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("GUILD_CACHE", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val guildId = obj.optString("id", "")
                    if (guildId.isBlank()) continue
                    add(
                        DiscordGuild(
                            id = guildId,
                            name = obj.optString("name", getString(R.string.main_server)),
                            icon = obj.optString("icon", null)
                        )
                    )
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun fetchDiscordChats() {
        lifecycleScope.launchWhenCreated {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@MainActivity)

                // Fetch Channels (DMs) and Guilds
                val channels = try {
                    api.getChannels()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error fetching channels", e)
                    emptyList()
                }
                val fetchedGuilds = try {
                    api.getGuilds()
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error fetching guilds", e)
                    null
                }
                val guilds = when {
                    fetchedGuilds == null -> loadGuildCache()
                    fetchedGuilds.isNotEmpty() -> {
                        saveGuildCache(fetchedGuilds)
                        fetchedGuilds
                    }
                    else -> {
                        val cached = loadGuildCache()
                        if (cached.isNotEmpty()) cached else emptyList()
                    }
                }

                // Map DMs
                val dmChats = channels.filter { it.type == 1 || it.type == 3 }.map { channel ->
                    val recipient = channel.recipients?.firstOrNull()
                    val name = if (channel.type == 1) {
                        recipient?.username ?: getString(R.string.main_unknown_user)
                    } else {
                        channel.recipients?.joinToString(", ") { it.username } ?: getString(R.string.main_group_chat)
                    }

                    val imageUrl = if (channel.type == 1 && recipient?.avatar != null) {
                        "https://cdn.discordapp.com/avatars/${recipient.id}/${recipient.avatar}.png"
                    } else {
                        null
                    }

                    Chat(
                        id = channel.id,
                        name = name,
                        message = getString(R.string.main_tap_to_view_messages),
                        time = "",
                        unreadCount = 0,
                        isGuild = false,
                        imageUrl = imageUrl
                    )
                }

                // Map Guilds
                val guildChats = guilds.mapNotNull { guild ->
                    val guildId = guild.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val guildName = guild.name?.takeIf { it.isNotBlank() } ?: getString(R.string.main_server)
                    val imageUrl = guild.icon?.let { "https://cdn.discordapp.com/icons/$guildId/$it.png" }

                    Chat(
                        id = guildId,
                        name = guildName,
                        message = getString(R.string.main_server),
                        time = "",
                        unreadCount = 0,
                        isGuild = true,
                        imageUrl = imageUrl
                    )
                }

                val allChats = dmChats + guildChats

                // Apply saved order
                val orderedChats = applySavedOrder(allChats)

                (binding.chatRecyclerView.adapter as ChatAdapter).updateData(orderedChats)

                if (allChats.isEmpty()) {
                    Toast.makeText(
                        this@MainActivity,
                        R.string.toast_load_chats_failed,
                        Toast.LENGTH_LONG
                    ).show()
                }

            } catch (e: Exception) {
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.toast_fetch_chats_error, e.message ?: "-"),
                    Toast.LENGTH_LONG
                ).show()
                e.printStackTrace()
            }
        }
    }
}
