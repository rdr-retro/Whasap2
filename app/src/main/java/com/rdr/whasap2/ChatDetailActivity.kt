package com.rdr.whasap2

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rdr.whasap2.api.DiscordAttachment
import com.rdr.whasap2.api.DiscordEmbed
import com.rdr.whasap2.api.DiscordMessage
import java.io.File

class ChatDetailActivity : AppCompatActivity(), MessageAdapter.SelectionListener {

    private lateinit var adapter: MessageAdapter
    private val messages = mutableListOf<Message>()
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false
    private var currentChatId: String? = null
    private var isLoadingOlderMessages = false
    private var hasMoreOlderMessages = true
    private var oldestLoadedMessageId: String? = null

    // Auto-refresh
    private val refreshHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val REFRESH_INTERVAL = 5000L // 5 seconds
    private var isRefreshing = false

    companion object {
        private const val PICK_IMAGE_REQUEST = 1001
        private const val PICK_BG_REQUEST = 1002
        private const val MESSAGE_PAGE_SIZE = 50
        private const val HISTORY_PRELOAD_THRESHOLD = 5
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat_detail)

        val chatName = intent.getStringExtra("CHAT_NAME") ?: "Chat"
        val chatId = intent.getStringExtra("CHAT_ID")

        findViewById<TextView>(R.id.header_title).text = chatName

        val recyclerView = findViewById<RecyclerView>(R.id.message_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter(messages, this)
        recyclerView.adapter = adapter

        if (chatId != null) {
            currentChatId = chatId
            setupHistoryPagination(recyclerView, chatId)
            fetchMessages(chatId)
            setupInputButtons(chatId)
            registerChannelForNotifications(chatId)
            loadChatBackground(chatId, recyclerView)
            startAutoRefresh(chatId)
        }

        // Back button — clear selection or go back
        findViewById<android.view.View>(R.id.btn_back).setOnClickListener {
            if (adapter.selectedPositions.isNotEmpty()) {
                adapter.clearSelection()
            } else {
                finish()
            }
        }

        // Trash icon — delete selected messages
        findViewById<ImageView>(R.id.header_delete).setOnClickListener {
            val chatChannelId = currentChatId ?: return@setOnClickListener
            val selected = adapter.getSelectedMessages()
            if (selected.isEmpty()) return@setOnClickListener

            AlertDialog.Builder(this)
                .setTitle("Eliminar mensajes")
                .setMessage("¿Eliminar ${selected.size} mensaje(s)?")
                .setPositiveButton("Eliminar") { _, _ ->
                    deleteSelectedMessages(chatChannelId, selected)
                }
                .setNegativeButton("Cancelar", null)
                .show()
        }

        // Copy icon — copy selected message text
        findViewById<ImageView>(R.id.header_copy).setOnClickListener {
            val selected = adapter.getSelectedMessages()
            if (selected.isEmpty()) return@setOnClickListener

            val text = selected.joinToString("\n") { it.content }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("messages", text)
            clipboard.setPrimaryClip(clip)
            adapter.clearSelection()
            Toast.makeText(this, "Texto copiado ✓", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onSelectionChanged(count: Int) {
        val deleteBtn = findViewById<ImageView>(R.id.header_delete)
        val copyBtn = findViewById<ImageView>(R.id.header_copy)
        val headerTitle = findViewById<TextView>(R.id.header_title)
        if (count > 0) {
            copyBtn.visibility = android.view.View.VISIBLE
            headerTitle.text = "$count seleccionado(s)"

            // Show trash only if at least one outgoing message is selected
            val hasOutgoing = adapter.getSelectedMessages().any { it.type == Message.TYPE_OUTGOING && it.messageId != null }
            deleteBtn.visibility = if (hasOutgoing) android.view.View.VISIBLE else android.view.View.GONE
        } else {
            deleteBtn.visibility = android.view.View.GONE
            copyBtn.visibility = android.view.View.GONE
            headerTitle.text = intent.getStringExtra("CHAT_NAME") ?: "Chat"
        }
    }

    private fun deleteSelectedMessages(channelId: String, selected: List<Message>) {
        lifecycleScope.launchWhenResumed {
            var deleted = 0
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@ChatDetailActivity)
                for (msg in selected) {
                    val msgId = msg.messageId ?: continue
                    try {
                        val response = api.deleteMessage(channelId, msgId)
                        if (response.isSuccessful) deleted++
                    } catch (e: Exception) {
                        // Skip messages we can't delete (permissions etc.)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            adapter.clearSelection()
            Toast.makeText(this@ChatDetailActivity, "$deleted mensaje(s) eliminado(s)", Toast.LENGTH_SHORT).show()
            // Reload latest messages to reflect deletions accurately.
            fetchMessages(channelId)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_chat, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val chatId = intent.getStringExtra("CHAT_ID") ?: return super.onOptionsItemSelected(item)
        return when (item.itemId) {
            R.id.menu_change_bg -> {
                showBackgroundPicker(chatId)
                true
            }
            R.id.menu_reset_bg -> {
                resetChatBackground(chatId)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showBackgroundPicker(chatId: String) {
        val colors = arrayOf(
            "⬜ Crema" to "#FDF5ED",
            "🟩 Verde claro" to "#DCF8C6",
            "🟦 Azul claro" to "#D1ECFF",
            "🟪 Rosa" to "#FFE0F0",
            "🟫 Beige" to "#F5E6CC",
            "⬛ Oscuro" to "#2C2C2C",
            "🖼 Elegir imagen..." to "IMAGE"
        )

        AlertDialog.Builder(this)
            .setTitle("Fondo del chat")
            .setItems(colors.map { it.first }.toTypedArray()) { _, which ->
                val (_, value) = colors[which]
                if (value == "IMAGE") {
                    val intent = Intent(Intent.ACTION_PICK)
                    intent.type = "image/*"
                    startActivityForResult(intent, PICK_BG_REQUEST)
                } else {
                    saveChatBackground(chatId, value, isColor = true)
                    val recyclerView = findViewById<RecyclerView>(R.id.message_recycler_view)
                    recyclerView.setBackgroundColor(Color.parseColor(value))
                    Toast.makeText(this, "Fondo cambiado ✓", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    private fun saveChatBackground(chatId: String, value: String, isColor: Boolean) {
        val prefs = getSharedPreferences("whasap_prefs", MODE_PRIVATE)
        prefs.edit()
            .putString("chat_bg_$chatId", value)
            .putBoolean("chat_bg_iscolor_$chatId", isColor)
            .apply()
    }

    private fun loadChatBackground(chatId: String, recyclerView: RecyclerView) {
        val prefs = getSharedPreferences("whasap_prefs", MODE_PRIVATE)
        val bgValue = prefs.getString("chat_bg_$chatId", null) ?: return
        val isColor = prefs.getBoolean("chat_bg_iscolor_$chatId", true)

        if (isColor) {
            try {
                recyclerView.setBackgroundColor(Color.parseColor(bgValue))
            } catch (e: Exception) { /* invalid color */ }
        } else {
            try {
                val file = File(bgValue)
                if (file.exists()) {
                    val bitmap = decodeSampledBitmap(file.absolutePath)
                    if (bitmap != null) {
                        val drawable = BitmapDrawable(resources, bitmap)
                        recyclerView.background = drawable
                    }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    /**
     * Decode bitmap at very reduced resolution for low-memory devices.
     * Uses RGB_565 (2 bytes/pixel vs 4 for ARGB_8888) and caps at 800px max.
     */
    private fun decodeSampledBitmap(filePath: String): android.graphics.Bitmap? {
        try {
            // First, read only dimensions
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(filePath, boundsOptions)

            if (boundsOptions.outWidth <= 0 || boundsOptions.outHeight <= 0) return null

            // Cap at 800px max dimension for old devices
            val maxDim = 800
            var sampleSize = 1
            while (boundsOptions.outWidth / sampleSize > maxDim || boundsOptions.outHeight / sampleSize > maxDim) {
                sampleSize *= 2
            }

            // Decode with aggressive sampling + RGB_565 (half memory of ARGB_8888)
            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = android.graphics.Bitmap.Config.RGB_565
            }

            return try {
                BitmapFactory.decodeFile(filePath, decodeOptions)
            } catch (oom: OutOfMemoryError) {
                // If still OOM, try even smaller
                System.gc()
                decodeOptions.inSampleSize = sampleSize * 2
                try {
                    BitmapFactory.decodeFile(filePath, decodeOptions)
                } catch (oom2: OutOfMemoryError) {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun resetChatBackground(chatId: String) {
        val prefs = getSharedPreferences("whasap_prefs", MODE_PRIVATE)
        prefs.edit()
            .remove("chat_bg_$chatId")
            .remove("chat_bg_iscolor_$chatId")
            .apply()
        val recyclerView = findViewById<RecyclerView>(R.id.message_recycler_view)
        recyclerView.setBackgroundColor(Color.parseColor("#FDF5ED"))
        Toast.makeText(this, "Fondo restablecido ✓", Toast.LENGTH_SHORT).show()
    }

    private fun registerChannelForNotifications(channelId: String) {
        val prefs = getSharedPreferences("whasap_prefs", android.content.Context.MODE_PRIVATE)
        val existing = prefs.getString("NOTIF_CHANNELS", "") ?: ""
        val channels = existing.split(",").filter { it.isNotEmpty() }.toMutableSet()
        channels.add(channelId)
        // Keep max 20 channels
        val trimmed = channels.toList().takeLast(20)
        prefs.edit().putString("NOTIF_CHANNELS", trimmed.joinToString(",")).apply()
    }

    private fun setupInputButtons(channelId: String) {
        val input = findViewById<EditText>(R.id.input_message)
        val sendBtn = findViewById<ImageView>(R.id.btn_send)
        val attachBtn = findViewById<ImageView>(R.id.header_clip)
        val audioBtn = findViewById<ImageView>(R.id.btn_audio)

        // Send text message
        sendBtn.setOnClickListener {
            val text = input.text.toString()
            if (text.isNotEmpty()) {
                sendMessage(channelId, text)
                input.text.clear()
            }
        }

        // Attach image
        attachBtn.setOnClickListener {
            val intent = Intent(Intent.ACTION_PICK)
            intent.type = "image/*"
            startActivityForResult(intent, PICK_IMAGE_REQUEST)
        }

        // Hold to record audio
        audioBtn.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startRecording()
                    v.alpha = 0.5f
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.alpha = 1.0f
                    if (isRecording) {
                        stopRecording(channelId)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun startRecording() {
        try {
            val audioDir = File(getExternalFilesDir(null), "audio")
            if (!audioDir.exists()) audioDir.mkdirs()
            audioFile = File(audioDir, "audio_${System.currentTimeMillis()}.mp4")

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(audioFile!!.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            Toast.makeText(this, "\uD83C\uDFA4 Grabando...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error al grabar: ${e.message}", Toast.LENGTH_SHORT).show()
            isRecording = false
        }
    }

    private fun stopRecording(channelId: String) {
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
            mediaRecorder = null
            isRecording = false

            if (audioFile != null && audioFile!!.exists()) {
                Toast.makeText(this, "\uD83C\uDFA4 Audio guardado (${audioFile!!.length() / 1024}KB)", Toast.LENGTH_SHORT).show()
                // Upload audio as attachment
                uploadFile(channelId, audioFile!!, "audio/mp4")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecording = false
            Toast.makeText(this, "Error al parar grabación", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Handle background image pick
        if (requestCode == PICK_BG_REQUEST && resultCode == Activity.RESULT_OK && data?.data != null) {
            val chatId = intent.getStringExtra("CHAT_ID") ?: return
            try {
                val inputStream = contentResolver.openInputStream(data.data!!)
                val bgFile = File(filesDir, "chat_bg_${chatId}.jpg")
                inputStream?.use { input -> bgFile.outputStream().use { output -> input.copyTo(output) } }
                saveChatBackground(chatId, bgFile.absolutePath, isColor = false)
                val bitmap = decodeSampledBitmap(bgFile.absolutePath)
                if (bitmap != null) {
                    val drawable = BitmapDrawable(resources, bitmap)
                    findViewById<RecyclerView>(R.id.message_recycler_view).background = drawable
                    Toast.makeText(this, "Fondo cambiado ✓", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Imagen demasiado grande", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this, "Error al cambiar fondo", Toast.LENGTH_SHORT).show()
            }
            return
        }

        if (requestCode == PICK_IMAGE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            val imageUri = data.data
            if (imageUri != null) {
                val chatId = intent.getStringExtra("CHAT_ID") ?: return
                
                // Copy URI to temp file and upload
                try {
                    val inputStream = contentResolver.openInputStream(imageUri)
                    val tempFile = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
                    inputStream?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    
                    val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
                    uploadFile(chatId, tempFile, mimeType)
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this, "Error al enviar imagen: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun uploadFile(channelId: String, file: File, mimeType: String) {
        lifecycleScope.launchWhenCreated {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@ChatDetailActivity)
                
                val requestBody = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(mimeType), file
                )
                val filePart = okhttp3.MultipartBody.Part.createFormData(
                    "files[0]", file.name, requestBody
                )

                api.sendFileMessage(channelId, filePart)

                // Add preview to local messages
                if (mimeType.startsWith("image/")) {
                    messages.add(Message(Message.TYPE_OUTGOING, "", "Now", imageUrl = file.absolutePath))
                } else {
                    messages.add(Message(Message.TYPE_OUTGOING, "\uD83C\uDFA4 Audio", "Now", audioUrl = "file://" + file.absolutePath))
                }
                adapter.notifyItemInserted(messages.size - 1)
                findViewById<RecyclerView>(R.id.message_recycler_view).scrollToPosition(messages.size - 1)

                Toast.makeText(this@ChatDetailActivity, "Enviado ✓", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@ChatDetailActivity, "Error al enviar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun isImageAttachment(attachment: DiscordAttachment): Boolean {
        return attachment.content_type?.startsWith("image/") == true ||
            attachment.filename.endsWith(".png", true) ||
            attachment.filename.endsWith(".jpg", true) ||
            attachment.filename.endsWith(".jpeg", true) ||
            attachment.filename.endsWith(".gif", true) ||
            attachment.filename.endsWith(".webp", true)
    }

    private fun isAudioAttachment(attachment: DiscordAttachment): Boolean {
        return attachment.content_type?.startsWith("audio/") == true ||
            attachment.filename.endsWith(".mp3", true) ||
            attachment.filename.endsWith(".mp4", true) ||
            attachment.filename.endsWith(".m4a", true) ||
            attachment.filename.endsWith(".ogg", true) ||
            attachment.filename.endsWith(".wav", true) ||
            attachment.filename.endsWith(".3gp", true)
    }

    private fun extractImageUrlFromEmbeds(embeds: List<DiscordEmbed>?): String? {
        if (embeds.isNullOrEmpty()) return null

        return embeds.asSequence()
            .flatMap { embed ->
                sequenceOf(
                    embed.image?.proxy_url,
                    embed.image?.url,
                    embed.thumbnail?.proxy_url,
                    embed.thumbnail?.url,
                    embed.video?.proxy_url,
                    embed.video?.url,
                    embed.url
                ).filterNotNull()
            }
            .map { sanitizeMediaUrl(it) }
            .firstOrNull { isSupportedEmbeddedMediaUrl(it) }
    }

    private fun extractImageUrlFromContent(content: String): String? {
        if (content.isBlank()) return null

        val urlRegex = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)
        return urlRegex.findAll(content)
            .map { sanitizeMediaUrl(it.value) }
            .firstOrNull { isSupportedEmbeddedMediaUrl(it) }
    }

    private fun sanitizeMediaUrl(raw: String): String {
        return raw.trim()
            .trim('<', '>')
            .trimEnd('.', ',', ';', ':', ')', ']', '}', '>', '"', '\'')
    }

    private fun isSupportedEmbeddedMediaUrl(url: String): Boolean {
        val normalized = sanitizeMediaUrl(url)
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
            host.contains("media.tenor.com") ||
                host.contains("cdn.discordapp.com") ||
                host.contains("media.discordapp.net")
        } catch (e: Exception) {
            false
        }
    }

    private fun toUiMessage(msg: DiscordMessage, myUserId: String?): Message {
        val type = if (msg.author.id == myUserId) Message.TYPE_OUTGOING else Message.TYPE_INCOMING
        val safeTime = if (msg.timestamp.length >= 16) msg.timestamp.substring(11, 16) else ""

        val imageAttachment = msg.attachments?.firstOrNull { isImageAttachment(it) }
        val audioAttachment = msg.attachments?.firstOrNull { isAudioAttachment(it) }
        val embedImageUrl = extractImageUrlFromEmbeds(msg.embeds)
        val contentImageUrl = extractImageUrlFromContent(msg.content)

        return Message(
            type = type,
            content = msg.content,
            time = safeTime,
            sender = msg.author.username,
            imageUrl = imageAttachment?.proxy_url ?: imageAttachment?.url ?: embedImageUrl ?: contentImageUrl,
            audioUrl = audioAttachment?.url,
            messageId = msg.id
        )
    }

    private fun getMyUserId(): String? {
        val prefs = getSharedPreferences("whasap_prefs", MODE_PRIVATE)
        return prefs.getString("MY_USER_ID", "")
    }

    private fun mapDiscordMessagesToUi(discordMessages: List<DiscordMessage>, myUserId: String?): List<Message> {
        return discordMessages.reversed().map { msg -> toUiMessage(msg, myUserId) }
    }

    private fun updateOldestLoadedMessageId() {
        oldestLoadedMessageId = messages.firstOrNull { !it.messageId.isNullOrBlank() }?.messageId
    }

    private fun setupHistoryPagination(recyclerView: RecyclerView, channelId: String) {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                if (dy >= 0) return

                val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                if (
                    firstVisible <= HISTORY_PRELOAD_THRESHOLD &&
                    !isLoadingOlderMessages &&
                    hasMoreOlderMessages &&
                    !oldestLoadedMessageId.isNullOrBlank()
                ) {
                    loadOlderMessages(channelId)
                }
            }
        })
    }

    private fun loadOlderMessages(channelId: String) {
        val beforeId = oldestLoadedMessageId ?: return
        val recyclerView = findViewById<RecyclerView>(R.id.message_recycler_view)
        val layoutManager = recyclerView.layoutManager as? LinearLayoutManager ?: return
        val myUserId = getMyUserId()

        isLoadingOlderMessages = true
        lifecycleScope.launchWhenResumed {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@ChatDetailActivity)
                val olderDiscordMessages = api.getMessages(
                    channelId = channelId,
                    limit = MESSAGE_PAGE_SIZE,
                    before = beforeId
                )

                if (olderDiscordMessages.isEmpty()) {
                    hasMoreOlderMessages = false
                    return@launchWhenResumed
                }

                hasMoreOlderMessages = olderDiscordMessages.size >= MESSAGE_PAGE_SIZE
                val olderUiMessages = mapDiscordMessagesToUi(olderDiscordMessages, myUserId)
                val existingIds = messages.mapNotNull { it.messageId }.toHashSet()
                val toInsert = olderUiMessages.filter { it.messageId !in existingIds }

                if (toInsert.isNotEmpty()) {
                    val firstVisiblePos = layoutManager.findFirstVisibleItemPosition()
                    val topOffset = layoutManager.findViewByPosition(firstVisiblePos)?.top ?: 0

                    messages.addAll(0, toInsert)
                    adapter.notifyItemRangeInserted(0, toInsert.size)

                    if (firstVisiblePos != RecyclerView.NO_POSITION) {
                        layoutManager.scrollToPositionWithOffset(firstVisiblePos + toInsert.size, topOffset)
                    }
                }

                updateOldestLoadedMessageId()
            } catch (e: Exception) {
                // Silent fail for pagination.
            } finally {
                isLoadingOlderMessages = false
            }
        }
    }

    private fun mergeLatestMessages(latestMessages: List<Message>): Boolean {
        if (latestMessages.isEmpty()) return false
        if (messages.isEmpty()) {
            messages.addAll(latestMessages)
            return true
        }

        var changed = false
        for (incoming in latestMessages) {
            val incomingId = incoming.messageId
            if (incomingId.isNullOrBlank()) continue

            val existingIndex = messages.indexOfFirst { it.messageId == incomingId }
            if (existingIndex >= 0) {
                if (messages[existingIndex] != incoming) {
                    messages[existingIndex] = incoming
                    changed = true
                }
            } else {
                messages.add(incoming)
                changed = true
            }
        }
        return changed
    }

    private fun fetchMessages(channelId: String) {
        val myUserId = getMyUserId()
        hasMoreOlderMessages = true
        oldestLoadedMessageId = null

        lifecycleScope.launchWhenCreated {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@ChatDetailActivity)
                val discordMessages = api.getMessages(channelId = channelId, limit = MESSAGE_PAGE_SIZE)
                val newMessages = mapDiscordMessagesToUi(discordMessages, myUserId)

                messages.clear()
                messages.addAll(newMessages)
                adapter.notifyDataSetChanged()
                updateOldestLoadedMessageId()
                hasMoreOlderMessages = discordMessages.size >= MESSAGE_PAGE_SIZE && !oldestLoadedMessageId.isNullOrBlank()

                if (messages.isNotEmpty()) {
                    findViewById<RecyclerView>(R.id.message_recycler_view).scrollToPosition(messages.size - 1)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendMessage(channelId: String, text: String) {
        lifecycleScope.launchWhenCreated {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@ChatDetailActivity)
                val request = com.rdr.whasap2.api.SendMessageRequest(text)
                api.sendMessage(channelId, request)

                messages.add(Message(Message.TYPE_OUTGOING, text, "Now"))
                adapter.notifyItemInserted(messages.size - 1)
                findViewById<RecyclerView>(R.id.message_recycler_view).scrollToPosition(messages.size - 1)

                // Play send sound
                playSendSound()

            } catch (e: Exception) {
                Toast.makeText(this@ChatDetailActivity, "Failed to send", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun playSendSound() {
        try {
            val mp = android.media.MediaPlayer.create(this, R.raw.send_sound)
            mp?.setOnCompletionListener { it.release() }
            mp?.start()
        } catch (e: Exception) {
            // Silent fail — sound is not critical
        }
    }

    override fun onResume() {
        super.onResume()
        currentChatId?.let { startAutoRefresh(it) }
    }

    override fun onPause() {
        super.onPause()
        stopAutoRefresh()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoRefresh()
        adapter.releaseMediaPlayer()
        if (isRecording) {
            try {
                mediaRecorder?.stop()
                mediaRecorder?.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun startAutoRefresh(channelId: String) {
        if (isRefreshing) return
        isRefreshing = true
        refreshHandler.postDelayed(object : Runnable {
            override fun run() {
                if (isRefreshing) {
                    refreshMessages(channelId)
                    refreshHandler.postDelayed(this, REFRESH_INTERVAL)
                }
            }
        }, REFRESH_INTERVAL)
    }

    private fun stopAutoRefresh() {
        isRefreshing = false
        refreshHandler.removeCallbacksAndMessages(null)
    }

    private fun refreshMessages(channelId: String) {
        val myUserId = getMyUserId()

        lifecycleScope.launchWhenResumed {
            try {
                val api = com.rdr.whasap2.api.RetrofitClient.getInstance(this@ChatDetailActivity)
                val discordMessages = api.getMessages(channelId = channelId, limit = MESSAGE_PAGE_SIZE)
                val latestMessages = mapDiscordMessagesToUi(discordMessages, myUserId)

                val recyclerView = findViewById<RecyclerView>(R.id.message_recycler_view)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val wasAtBottom = layoutManager.findLastVisibleItemPosition() >= messages.size - 2

                if (mergeLatestMessages(latestMessages)) {
                    adapter.notifyDataSetChanged()
                    updateOldestLoadedMessageId()

                    if (wasAtBottom && messages.isNotEmpty()) {
                        recyclerView.scrollToPosition(messages.size - 1)
                    }
                }

            } catch (e: Exception) {
                // Silent fail — don't interrupt the user
            }
        }
    }
}
