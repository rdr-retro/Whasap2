package com.rdr.whasap2

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class NotificationItem(
    val sender: String,
    val content: String,
    val time: String,
    val channelId: String,
    val channelName: String
)

class NotificationAdapter(
    private val notifications: MutableList<NotificationItem>,
    private val mutedChannels: MutableSet<String>,
    private val onMuteToggle: (String, Boolean) -> Unit,
    private val onItemClick: (NotificationItem) -> Unit
) : RecyclerView.Adapter<NotificationAdapter.NotifViewHolder>() {

    class NotifViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView = view.findViewById(R.id.notif_sender)
        val content: TextView = view.findViewById(R.id.notif_content)
        val time: TextView = view.findViewById(R.id.notif_time)
        val muteCheck: CheckBox = view.findViewById(R.id.notif_mute_channel)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotifViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_notification, parent, false)
        return NotifViewHolder(view)
    }

    override fun onBindViewHolder(holder: NotifViewHolder, position: Int) {
        val item = notifications[position]
        holder.sender.text = item.sender
        holder.content.text = if (item.content.isNotEmpty()) item.content else "\uD83D\uDCCE Archivo"
        holder.time.text = "${item.time} · ${item.channelName}"

        // Mute checkbox (star)
        holder.muteCheck.setOnCheckedChangeListener(null) // clear old listener
        holder.muteCheck.isChecked = mutedChannels.contains(item.channelId)
        holder.muteCheck.setOnCheckedChangeListener { _, isChecked ->
            onMuteToggle(item.channelId, isChecked)
        }

        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount() = notifications.size
}
