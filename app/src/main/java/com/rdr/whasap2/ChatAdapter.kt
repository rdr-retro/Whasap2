package com.rdr.whasap2

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

class ChatAdapter(
    private var chats: MutableList<Chat>,
    private val onChatClick: (Chat) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    var isReorderMode = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    var itemTouchHelper: ItemTouchHelper? = null

    fun updateData(newChats: List<Chat>) {
        chats = newChats.toMutableList()
        notifyDataSetChanged()
    }

    fun getChats(): List<Chat> = chats

    fun moveItem(from: Int, to: Int) {
        val item = chats.removeAt(from)
        chats.add(to, item)
        notifyItemMoved(from, to)
    }

    class ChatViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.chat_name)
        val message: TextView = view.findViewById(R.id.chat_message)
        val time: TextView = view.findViewById(R.id.chat_time)
        val unreadBadge: TextView = view.findViewById(R.id.chat_unread_badge)
        val avatar: ImageView = view.findViewById(R.id.chat_avatar)
        val dragHandle: ImageView = view.findViewById(R.id.drag_handle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chats[position]
        holder.name.text = chat.name
        holder.message.text = chat.message
        holder.time.text = chat.time

        // Bluish-Gray for Guilds, White for DMs
        if (chat.isGuild) {
            holder.itemView.setBackgroundColor(android.graphics.Color.parseColor("#E3E9F0"))
        } else {
            holder.itemView.setBackgroundColor(android.graphics.Color.WHITE)
        }

        // Load Image
        if (chat.imageUrl != null) {
            com.bumptech.glide.Glide.with(holder.itemView.context)
                .load(chat.imageUrl)
                .circleCrop()
                .placeholder(R.drawable.icondroid)
                .error(R.drawable.icondroid)
                .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.icondroid)
        }

        if (chat.unreadCount > 0) {
            holder.unreadBadge.text = chat.unreadCount.toString()
            holder.unreadBadge.visibility = View.VISIBLE
        } else {
            holder.unreadBadge.visibility = View.GONE
        }

        // Show/hide drag handle based on reorder mode
        if (isReorderMode) {
            holder.dragHandle.visibility = View.VISIBLE
            holder.time.visibility = View.GONE
            holder.dragHandle.setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    itemTouchHelper?.startDrag(holder)
                }
                false
            }
        } else {
            holder.dragHandle.visibility = View.GONE
            holder.time.visibility = View.VISIBLE
            holder.dragHandle.setOnTouchListener(null)
        }

        holder.itemView.setOnClickListener {
            if (!isReorderMode) {
                onChatClick(chat)
            }
        }
    }

    override fun getItemCount() = chats.size
}
