package com.rdr.whasap2

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.rdr.whasap2.api.DiscordChannel

class ChannelAdapter(
    private var channels: MutableList<DiscordChannel>,
    private val voiceMembers: Map<String, List<String>>,
    private val onChannelClick: (DiscordChannel) -> Unit
) : RecyclerView.Adapter<ChannelAdapter.ChannelViewHolder>() {

    var isReorderMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var itemTouchHelper: ItemTouchHelper? = null

    class ChannelViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val bg: LinearLayout = view.findViewById(R.id.channel_bg)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
        val icon: ImageView = view.findViewById(R.id.channel_icon)
        val name: TextView = view.findViewById(R.id.channel_name)
        val voiceCount: TextView = view.findViewById(R.id.channel_voice_count)
        val voiceMembersContainer: LinearLayout = view.findViewById(R.id.voice_members_container)
        val voiceMembersList: TextView = view.findViewById(R.id.voice_members_list)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChannelViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_channel, parent, false)
        return ChannelViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChannelViewHolder, position: Int) {
        val channel = channels[position]
        val displayName = channel.name ?: "Unknown Channel"
        val isVoice = channel.type == 2

        // Set background based on channel type
        if (isVoice) {
            holder.bg.setBackgroundResource(R.drawable.bg_channel_voice)
            holder.icon.setImageResource(android.R.drawable.ic_lock_silent_mode_off)
            holder.name.text = "\uD83D\uDD0A $displayName"

            val members = voiceMembers[channel.id]
            if (members != null && members.isNotEmpty()) {
                holder.voiceCount.visibility = View.VISIBLE
                holder.voiceCount.text = "${members.size} \uD83D\uDC64"
                holder.voiceMembersContainer.visibility = View.VISIBLE
                holder.voiceMembersList.text = members.joinToString("\n") { "  \uD83D\uDFE2 $it" }
            } else {
                holder.voiceCount.visibility = View.GONE
                holder.voiceMembersContainer.visibility = View.GONE
            }
        } else {
            holder.bg.setBackgroundResource(R.drawable.bg_channel_item)
            holder.icon.setImageResource(android.R.drawable.ic_menu_edit)
            holder.name.text = "# $displayName"
            holder.voiceCount.visibility = View.GONE
            holder.voiceMembersContainer.visibility = View.GONE
        }

        // Drag handle visibility
        if (isReorderMode) {
            holder.dragHandle.visibility = View.VISIBLE
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(holder)
                }
                false
            }
        } else {
            holder.dragHandle.visibility = View.GONE
            holder.dragHandle.setOnTouchListener(null)
        }

        holder.itemView.setOnClickListener {
            if (!isReorderMode) {
                onChannelClick(channel)
            }
        }
    }

    fun moveItem(from: Int, to: Int) {
        val item = channels.removeAt(from)
        channels.add(to, item)
        notifyItemMoved(from, to)
    }

    fun getChannels(): List<DiscordChannel> = channels

    fun filter(query: String, allChannels: List<DiscordChannel>) {
        channels = if (query.isEmpty()) {
            allChannels.toMutableList()
        } else {
            allChannels.filter {
                (it.name ?: "").contains(query, ignoreCase = true)
            }.toMutableList()
        }
        notifyDataSetChanged()
    }

    override fun getItemCount() = channels.size
}
