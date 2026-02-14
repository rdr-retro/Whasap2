package com.rdr.whasap2

import android.content.Intent
import android.media.MediaPlayer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class MessageAdapter(
    private val messages: List<Message>,
    private val selectionListener: SelectionListener? = null
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var currentMediaPlayer: MediaPlayer? = null
    private var currentlyPlayingPosition: Int = -1
    val selectedPositions = mutableSetOf<Int>()

    interface SelectionListener {
        fun onSelectionChanged(count: Int)
    }

    class IncomingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val sender: TextView = view.findViewById(R.id.msg_sender)
        val content: TextView = view.findViewById(R.id.msg_content)
        val time: TextView = view.findViewById(R.id.msg_time)
        val image: ImageView = view.findViewById(R.id.msg_image)
        val audioContainer: LinearLayout = view.findViewById(R.id.msg_audio_container)
        val btnPlayAudio: ImageView = view.findViewById(R.id.btn_play_audio)
        val audioLabel: TextView = view.findViewById(R.id.msg_audio_label)
    }

    class OutgoingViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.msg_content)
        val time: TextView = view.findViewById(R.id.msg_time)
        val image: ImageView = view.findViewById(R.id.msg_image)
        val audioContainer: LinearLayout = view.findViewById(R.id.msg_audio_container)
        val btnPlayAudio: ImageView = view.findViewById(R.id.btn_play_audio)
        val audioLabel: TextView = view.findViewById(R.id.msg_audio_label)
    }

    class SystemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val content: TextView = view.findViewById(R.id.msg_content)
    }

    override fun getItemViewType(position: Int): Int {
        return messages[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            Message.TYPE_INCOMING -> IncomingViewHolder(inflater.inflate(R.layout.item_message_incoming, parent, false))
            Message.TYPE_OUTGOING -> OutgoingViewHolder(inflater.inflate(R.layout.item_message_outgoing, parent, false))
            else -> SystemViewHolder(inflater.inflate(R.layout.item_message_system, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val message = messages[position]
        when (holder) {
            is IncomingViewHolder -> {
                holder.sender.text = message.sender
                val displayContent = getDisplayContent(message)
                holder.content.text = displayContent
                holder.time.text = message.time
                bindImage(holder.image, message, holder.itemView)
                bindAudio(holder.audioContainer, holder.btnPlayAudio, holder.audioLabel, message, position)

                // Hide content text if empty and has media
                if (displayContent.isEmpty() && (message.imageUrl != null || message.audioUrl != null)) {
                    holder.content.visibility = View.GONE
                } else {
                    holder.content.visibility = View.VISIBLE
                }
                // Selection highlight
                if (selectedPositions.contains(position)) {
                    holder.itemView.setBackgroundColor(0x80B3D4FC.toInt())
                } else {
                    holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }

                // Long press to select (for copy)
                holder.itemView.setOnLongClickListener {
                    toggleSelection(holder.adapterPosition)
                    true
                }

                // Tap to toggle if in selection mode
                holder.itemView.setOnClickListener {
                    if (selectedPositions.isNotEmpty()) {
                        toggleSelection(holder.adapterPosition)
                    }
                }
            }
            is OutgoingViewHolder -> {
                val displayContent = getDisplayContent(message)
                holder.content.text = displayContent
                holder.time.text = message.time
                bindImage(holder.image, message, holder.itemView)
                bindAudio(holder.audioContainer, holder.btnPlayAudio, holder.audioLabel, message, position)

                if (displayContent.isEmpty() && (message.imageUrl != null || message.audioUrl != null)) {
                    holder.content.visibility = View.GONE
                } else {
                    holder.content.visibility = View.VISIBLE
                }

                // Selection highlight
                if (selectedPositions.contains(position)) {
                    holder.itemView.setBackgroundColor(0x80B3D4FC.toInt()) // light blue
                } else {
                    holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }

                // Long press to select
                holder.itemView.setOnLongClickListener {
                    toggleSelection(holder.adapterPosition)
                    true
                }

                // Tap to toggle if in selection mode
                holder.itemView.setOnClickListener {
                    if (selectedPositions.isNotEmpty()) {
                        toggleSelection(holder.adapterPosition)
                    }
                }
            }
            is SystemViewHolder -> {
                holder.content.text = message.content
                holder.itemView.setBackgroundColor(android.graphics.Color.TRANSPARENT)
            }
        }
    }

    private fun toggleSelection(position: Int) {
        if (position < 0 || position >= messages.size) return
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
        } else {
            selectedPositions.add(position)
        }
        notifyItemChanged(position)
        selectionListener?.onSelectionChanged(selectedPositions.size)
    }

    fun clearSelection() {
        val positions = selectedPositions.toList()
        selectedPositions.clear()
        positions.forEach { notifyItemChanged(it) }
        selectionListener?.onSelectionChanged(0)
    }

    fun getSelectedMessages(): List<Message> {
        return selectedPositions.mapNotNull { pos ->
            if (pos < messages.size) messages[pos] else null
        }
    }

    private fun bindImage(imageView: ImageView, message: Message, itemView: View) {
        if (message.imageUrl != null) {
            imageView.visibility = View.VISIBLE
            val requestManager = com.bumptech.glide.Glide.with(itemView.context)
            val requestBuilder = if (isGifUrl(message.imageUrl)) {
                requestManager.asGif().load(message.imageUrl)
            } else {
                requestManager.load(message.imageUrl)
            }

            requestBuilder
                .dontTransform()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_close_clear_cancel)
                .into(imageView)

            imageView.setOnClickListener {
                val intent = Intent(itemView.context, ImageViewerActivity::class.java)
                intent.putExtra("IMAGE_URL", message.imageUrl)
                itemView.context.startActivity(intent)
            }
        } else {
            imageView.visibility = View.GONE
            imageView.setOnClickListener(null)
        }
    }

    private fun isGifUrl(url: String): Boolean {
        val path = url.lowercase().substringBefore('?').substringBefore('#')
        return path.endsWith(".gif")
    }

    private fun getDisplayContent(message: Message): String {
        if (message.content.isBlank() || message.imageUrl == null) return message.content

        val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        val withoutMediaLinks = urlRegex.replace(message.content) { match ->
            val normalized = sanitizeUrl(match.value)
            if (isMediaPreviewLink(normalized)) "" else match.value
        }

        return withoutMediaLinks
            .split('\n')
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun sanitizeUrl(raw: String): String {
        return raw.trim()
            .trim('<', '>')
            .trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')
    }

    private fun isMediaPreviewLink(url: String): Boolean {
        val normalized = sanitizeUrl(url)
        val path = normalized.lowercase().substringBefore('?').substringBefore('#')
        if (
            path.endsWith(".png") ||
            path.endsWith(".jpg") ||
            path.endsWith(".jpeg") ||
            path.endsWith(".gif") ||
            path.endsWith(".webp")
        ) {
            return true
        }

        return try {
            val host = android.net.Uri.parse(normalized).host?.lowercase() ?: return false
            host.contains("tenor.com") ||
                host.contains("media.tenor.com") ||
                host.contains("cdn.discordapp.com") ||
                host.contains("media.discordapp.net")
        } catch (e: Exception) {
            false
        }
    }

    private fun bindAudio(
        container: LinearLayout,
        playBtn: ImageView,
        label: TextView,
        message: Message,
        position: Int
    ) {
        if (message.audioUrl != null) {
            container.visibility = View.VISIBLE

            // Update icon based on playing state
            if (currentlyPlayingPosition == position) {
                playBtn.setImageResource(android.R.drawable.ic_media_pause)
                label.text = container.context.getString(R.string.message_audio_playing)
            } else {
                playBtn.setImageResource(android.R.drawable.ic_media_play)
                label.text = container.context.getString(R.string.message_audio_label)
            }

            playBtn.setOnClickListener {
                if (currentlyPlayingPosition == position) {
                    // Stop current playback
                    stopAudio()
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                    label.text = container.context.getString(R.string.message_audio_label)
                } else {
                    // Stop any existing playback
                    stopAudio()

                    // Start new playback
                    playAudio(message.audioUrl, playBtn, label, position)
                }
            }
        } else {
            container.visibility = View.GONE
            playBtn.setOnClickListener(null)
        }
    }

    private fun playAudio(url: String, playBtn: ImageView, label: TextView, position: Int) {
        try {
            currentMediaPlayer = MediaPlayer().apply {
                setDataSource(url)
                prepareAsync()
                setOnPreparedListener {
                    start()
                    currentlyPlayingPosition = position
                    playBtn.setImageResource(android.R.drawable.ic_media_pause)
                    label.text = playBtn.context.getString(R.string.message_audio_playing)
                }
                setOnCompletionListener {
                    currentlyPlayingPosition = -1
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                    label.text = playBtn.context.getString(R.string.message_audio_label)
                    release()
                    currentMediaPlayer = null
                }
                setOnErrorListener { _, _, _ ->
                    currentlyPlayingPosition = -1
                    playBtn.setImageResource(android.R.drawable.ic_media_play)
                    label.text = playBtn.context.getString(R.string.message_audio_error)
                    Toast.makeText(playBtn.context, R.string.toast_audio_play_error, Toast.LENGTH_SHORT).show()
                    release()
                    currentMediaPlayer = null
                    true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(
                playBtn.context,
                playBtn.context.getString(R.string.toast_generic_error, e.message ?: "-"),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun stopAudio() {
        try {
            currentMediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentMediaPlayer = null
        currentlyPlayingPosition = -1
    }

    override fun getItemCount() = messages.size

    fun releaseMediaPlayer() {
        stopAudio()
    }
}
