package com.rdr.whasap2

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MessagePollingService : Service() {

    private val TAG = "MsgPollingService"
    private val handler = Handler(Looper.getMainLooper())
    private val POLL_INTERVAL = 30000L // 30 seconds (optimized: few API calls per cycle)
    private var isRunning = false

    companion object {
        const val CHANNEL_ID = "whasap2_messages"
        const val FOREGROUND_CHANNEL_ID = "whasap2_service"
    }

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (isRunning) {
                checkForNewMessages()
                handler.postDelayed(this, POLL_INTERVAL)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Service foreground channel (silent)
            val serviceChannel = NotificationChannel(
                FOREGROUND_CHANNEL_ID,
                getString(R.string.message_service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.message_service_channel_desc)
                setSound(null, null)
            }
            notifManager.createNotificationChannel(serviceChannel)

            // Message notification channel (with sound)
            val msgChannel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.message_alerts_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.message_alerts_channel_desc)
                enableVibration(true)
                enableLights(true)
            }
            notifManager.createNotificationChannel(msgChannel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!NotificationSettings.areNotificationsEnabled(this)) {
            Log.d(TAG, "Notifications disabled; stopping polling service")
            stopSelf()
            return START_NOT_STICKY
        }

        Log.d(TAG, "Service starting")

        // Install Conscrypt if not already
        try {
            val conscrypt = org.conscrypt.Conscrypt.newProvider()
            java.security.Security.insertProviderAt(conscrypt, 1)
        } catch (e: Exception) {
            // Already installed or error
        }

        // Start as foreground service with persistent notification
        showForegroundNotification()

        // Start polling
        if (!isRunning) {
            isRunning = true
            handler.post(pollRunnable)
        }

        return START_STICKY
    }

    private fun getPendingIntentFlags(extraFlags: Int = 0): Int {
        return if (Build.VERSION.SDK_INT >= 23) {
            extraFlags or PendingIntent.FLAG_IMMUTABLE
        } else {
            extraFlags
        }
    }

    private fun showForegroundNotification() {
        val notifIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notifIntent, getPendingIntentFlags())

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, FOREGROUND_CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.message_foreground_connected))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.message_foreground_connected))
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()
        }

        startForeground(1, notification)
    }

    private fun checkForNewMessages() {
        Thread {
            try {
                val prefs = getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
                if (!NotificationSettings.areNotificationsEnabled(this)) {
                    Log.d(TAG, "Notifications disabled during polling; stopping service")
                    stopSelf()
                    return@Thread
                }

                val token = prefs.getString("DISCORD_TOKEN", "") ?: ""
                if (token.isEmpty()) {
                    Log.w(TAG, "No token found")
                    return@Thread
                }

                val myUserId = prefs.getString("MY_USER_ID", "") ?: ""
                val baselineOnly = prefs.getBoolean(NotificationSettings.KEY_BASELINE_ON_NEXT_START, false)
                val api = createApi(token)

                // Collect channels that have new messages (using last_message_id from channel list)
                val changedChannels = mutableListOf<Pair<String, String>>() // channelId, channelName

                // 1) Check DM channels — 1 API call
                try {
                    val dmResponse = api.getDMChannels().execute()
                    if (dmResponse.isSuccessful) {
                        val dmChannels = dmResponse.body()?.filter { it.type == 1 || it.type == 3 } ?: emptyList()
                        Log.d(TAG, "DMs: ${dmChannels.size} channels")

                        for (ch in dmChannels) {
                            val lastKnown = prefs.getString("last_msg_${ch.id}", "") ?: ""
                            val current = ch.last_message_id ?: ""
                            if (baselineOnly) {
                                if (current.isNotEmpty()) {
                                    prefs.edit().putString("last_msg_${ch.id}", current).apply()
                                }
                                continue
                            }
                            if (current.isNotEmpty() && lastKnown.isNotEmpty() && current != lastKnown) {
                                val name = ch.recipients?.firstOrNull()?.username
                                    ?: ch.name
                                    ?: getString(R.string.message_dm_fallback)
                                changedChannels.add(ch.id to name)
                            }
                            // Save current last_message_id if first time
                            if (lastKnown.isEmpty() && current.isNotEmpty()) {
                                prefs.edit().putString("last_msg_${ch.id}", current).apply()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching DMs: ${e.message}")
                }

                // 2) Check guild channels — 1 call per guild (no per-channel calls)
                try {
                    val guildsResponse = api.getGuilds().execute()
                    if (guildsResponse.isSuccessful) {
                        val guilds = guildsResponse.body() ?: emptyList()
                        Log.d(TAG, "Guilds: ${guilds.size}")

                        for (guild in guilds) {
                            val guildId = guild.id ?: continue
                            val guildName = guild.name ?: guildId
                            try {
                                Thread.sleep(300)
                                val chResponse = api.getGuildChannels(guildId).execute()
                                if (chResponse.isSuccessful) {
                                    val textChannels = chResponse.body()?.filter { it.type == 0 } ?: emptyList()

                                    for (ch in textChannels) {
                                        val lastKnown = prefs.getString("last_msg_${ch.id}", "") ?: ""
                                        val current = ch.last_message_id ?: ""
                                        if (baselineOnly) {
                                            if (current.isNotEmpty()) {
                                                prefs.edit().putString("last_msg_${ch.id}", current).apply()
                                            }
                                            continue
                                        }
                                        if (current.isNotEmpty() && lastKnown.isNotEmpty() && current != lastKnown) {
                                            changedChannels.add(
                                                ch.id to (ch.name ?: getString(R.string.message_channel_fallback))
                                            )
                                        }
                                        if (lastKnown.isEmpty() && current.isNotEmpty()) {
                                            prefs.edit().putString("last_msg_${ch.id}", current).apply()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(TAG, "Error guild $guildName: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error fetching guilds: ${e.message}")
                }

                if (baselineOnly) {
                    prefs.edit().putBoolean(NotificationSettings.KEY_BASELINE_ON_NEXT_START, false).apply()
                    Log.d(TAG, "Baseline sync completed; notifications resume next cycle")
                    return@Thread
                }

                // 3) Only fetch messages for channels that actually changed
                Log.d(TAG, "Changed channels: ${changedChannels.size}")
                for ((channelId, channelName) in changedChannels) {
                    try {
                        Thread.sleep(500)
                        val msgResponse = api.getMessagesSync(channelId, 1).execute()
                        if (msgResponse.isSuccessful) {
                            val messages = msgResponse.body()
                            if (!messages.isNullOrEmpty()) {
                                val msg = messages[0]
                                prefs.edit().putString("last_msg_$channelId", msg.id).apply()
                                if (msg.author.id != myUserId) {
                                    showNotification(msg.author.username, msg.content, channelId)
                                }
                            }
                        } else if (msgResponse.code() == 429) {
                            Log.w(TAG, "Rate limited, stopping this cycle")
                            break
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error fetching msg for $channelId: ${e.message}")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error polling: ${e.message}")
            }
        }.start()
    }

    private fun showNotification(sender: String, content: String, channelId: String) {
        if (!NotificationSettings.areNotificationsEnabled(this)) return

        // Always store in notification center
        NotificationsActivity.storeNotification(this, sender, content, channelId, sender)

        // Check if muted - skip system notification if so
        if (NotificationsActivity.isChannelMuted(this, channelId)) {
            Log.d(TAG, "Channel $channelId is muted, skipping system notification")
            return
        }

        val intent = Intent(this, ChatDetailActivity::class.java).apply {
            putExtra("CHAT_ID", channelId)
            putExtra("CHAT_NAME", sender)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, channelId.hashCode(), intent,
            getPendingIntentFlags(PendingIntent.FLAG_UPDATE_CURRENT)
        )

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val displayContent = if (content.isNotEmpty()) content else getString(R.string.message_attachment)

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(sender)
                .setContentText(displayContent)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle(sender)
                .setContentText(displayContent)
                .setSmallIcon(android.R.drawable.ic_dialog_email)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setSound(soundUri)
                .setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
                .build()
        }

        val notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notifManager.notify(channelId.hashCode(), notification)
        Log.d(TAG, "Notification shown for $sender")
    }

    private fun createApi(token: String): DiscordApiSync {
        val authInterceptor = Interceptor { chain ->
            val request = chain.request().newBuilder()
                .addHeader("Authorization", token)
                .build()
            chain.proceed(request)
        }

        val builder = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)

        if (Build.VERSION.SDK_INT < 22) {
            try {
                val sc = javax.net.ssl.SSLContext.getInstance("TLS")
                sc.init(null, null, null)
                val trustManagerFactory = javax.net.ssl.TrustManagerFactory.getInstance(
                    javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
                )
                trustManagerFactory.init(null as java.security.KeyStore?)
                val trustManagers = trustManagerFactory.trustManagers
                if (trustManagers.isNotEmpty() && trustManagers[0] is javax.net.ssl.X509TrustManager) {
                    val trustManager = trustManagers[0] as javax.net.ssl.X509TrustManager
                    builder.sslSocketFactory(sc.socketFactory, trustManager)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val client = builder.build()

        return Retrofit.Builder()
            .baseUrl("https://discord.com/api/v10/")
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DiscordApiSync::class.java)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        handler.removeCallbacks(pollRunnable)
        Log.d(TAG, "Service destroyed")
    }
}
