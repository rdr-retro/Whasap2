package com.rdr.whasap2

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Restarts the MessagePollingService when the device boots up.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences(NotificationSettings.PREFS_NAME, Context.MODE_PRIVATE)
            val token = prefs.getString("DISCORD_TOKEN", "") ?: ""

            // Only start service if user has token and notifications enabled.
            if (token.isNotEmpty() && NotificationSettings.areNotificationsEnabled(context)) {
                Log.d("BootReceiver", "Boot completed, starting polling service")
                val serviceIntent = Intent(context, MessagePollingService::class.java)
                context.startService(serviceIntent)
            } else {
                Log.d("BootReceiver", "Boot completed, polling disabled")
            }
        }
    }
}
