package com.rdr.whasap2

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rdr.whasap2.api.DiscordChannel

class GuildChannelsActivity : AppCompatActivity() {

    private lateinit var adapter: ChannelAdapter
    private var guildId: String? = null
    private var isReorderMode = false
    private var itemTouchHelper: ItemTouchHelper? = null
    private var allChannels: List<DiscordChannel> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_guild_channels)

        guildId = intent.getStringExtra("GUILD_ID")
        val guildName = intent.getStringExtra("GUILD_NAME") ?: "Server"

        findViewById<TextView>(R.id.guild_name).text = guildName
        findViewById<View>(R.id.btn_back).setOnClickListener { finish() }

        setupRecyclerView()
        setupSearchButton()
        setupReorderButton()

        if (guildId != null) {
            fetchChannels(guildId!!)
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.channels_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)

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
    }

    private fun setupSearchButton() {
        val searchBtn = findViewById<ImageView>(R.id.btn_search)
        val searchBar = findViewById<LinearLayout>(R.id.search_bar)
        val searchInput = findViewById<EditText>(R.id.search_input)

        searchBtn.setOnClickListener {
            if (searchBar.visibility == View.VISIBLE) {
                searchBar.visibility = View.GONE
                searchInput.text.clear()
                if (::adapter.isInitialized) {
                    adapter.filter("", allChannels)
                }
            } else {
                searchBar.visibility = View.VISIBLE
                searchInput.requestFocus()
            }
        }

        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (::adapter.isInitialized) {
                    adapter.filter(s.toString(), allChannels)
                }
            }
        })
    }

    private fun setupReorderButton() {
        val editBtn = findViewById<ImageView>(R.id.btn_edit_channels)
        val guildNameView = findViewById<TextView>(R.id.guild_name)

        editBtn.setOnClickListener {
            isReorderMode = !isReorderMode
            if (::adapter.isInitialized) {
                adapter.isReorderMode = isReorderMode
            }

            if (isReorderMode) {
                guildNameView.text = "Reordenar"
                Toast.makeText(this, "Arrastra los canales para reordenar", Toast.LENGTH_SHORT).show()
            } else {
                guildNameView.text = intent.getStringExtra("GUILD_NAME") ?: "Server"
                // Save the new order
                if (::adapter.isInitialized) {
                    saveChannelOrder(adapter.getChannels())
                }
                Toast.makeText(this, "Orden guardado ✓", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun saveChannelOrder(channels: List<DiscordChannel>) {
        val prefs = getSharedPreferences("whasap_prefs", android.content.Context.MODE_PRIVATE)
        val key = "CHANNEL_ORDER_${guildId}"
        val orderedIds = channels.map { it.id }
        prefs.edit().putString(key, orderedIds.joinToString(",")).apply()
    }

    private fun loadChannelOrder(): List<String> {
        val prefs = getSharedPreferences("whasap_prefs", android.content.Context.MODE_PRIVATE)
        val key = "CHANNEL_ORDER_${guildId}"
        val orderString = prefs.getString(key, null) ?: return emptyList()
        return orderString.split(",").filter { it.isNotEmpty() }
    }

    private fun applySavedOrder(channels: List<DiscordChannel>): List<DiscordChannel> {
        val savedOrder = loadChannelOrder()
        if (savedOrder.isEmpty()) return channels

        val channelMap = channels.associateBy { it.id }
        val ordered = mutableListOf<DiscordChannel>()

        for (id in savedOrder) {
            channelMap[id]?.let { ordered.add(it) }
        }
        for (ch in channels) {
            if (ch.id !in savedOrder) {
                ordered.add(ch)
            }
        }
        return ordered
    }

    private fun fetchChannels(guildId: String) {
        lifecycleScope.launchWhenCreated {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@GuildChannelsActivity)
                val channels = api.getGuildChannels(guildId)

                // Get voice states
                val voiceMembers = mutableMapOf<String, List<String>>()
                try {
                    val voiceStates = api.getVoiceStates(guildId)
                    val grouped = voiceStates.filter { it.channel_id != null }.groupBy { it.channel_id!! }
                    for ((channelId, states) in grouped) {
                        val names = states.map { state ->
                            state.member?.nick
                                ?: state.member?.user?.username
                                ?: "User ${state.user_id.takeLast(4)}"
                        }
                        voiceMembers[channelId] = names
                    }
                } catch (e: Exception) {
                    android.util.Log.w("GuildChannels", "Could not fetch voice states: ${e.message}")
                }

                // Filter text + voice channels
                val visibleChannels = channels
                    .filter { it.type == 0 || it.type == 2 }
                    .sortedWith(compareBy({ it.type }, { it.name }))

                // Apply saved order
                val orderedChannels = applySavedOrder(visibleChannels)
                allChannels = orderedChannels

                adapter = ChannelAdapter(orderedChannels.toMutableList(), voiceMembers) { channel ->
                    if (channel.type == 2) {
                        val members = voiceMembers[channel.id] ?: emptyList()
                        val intent = android.content.Intent(this@GuildChannelsActivity, VoiceChannelActivity::class.java)
                        intent.putExtra("CHANNEL_NAME", channel.name)
                        intent.putExtra("CHANNEL_ID", channel.id)
                        intent.putExtra("GUILD_ID", guildId)
                        intent.putExtra("MEMBERS", members.joinToString("||"))
                        startActivity(intent)
                    } else {
                        val intent = android.content.Intent(this@GuildChannelsActivity, ChatDetailActivity::class.java)
                        intent.putExtra("CHAT_NAME", "# ${channel.name}")
                        intent.putExtra("CHAT_ID", channel.id)
                        startActivity(intent)
                    }
                }
                adapter.itemTouchHelper = itemTouchHelper
                findViewById<RecyclerView>(R.id.channels_recycler_view).adapter = adapter

            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@GuildChannelsActivity, "Error fetching channels", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
