package com.rdr.whasap2

import com.rdr.whasap2.api.DiscordChannel
import com.rdr.whasap2.api.DiscordGuild
import com.rdr.whasap2.api.DiscordMessage
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Synchronous version of DiscordApi for use in the background service.
 * Uses Call<> instead of suspend functions so it can be called from regular threads.
 */
interface DiscordApiSync {

    @GET("users/@me/channels")
    fun getDMChannels(): Call<List<DiscordChannel>>

    @GET("users/@me/guilds")
    fun getGuilds(): Call<List<DiscordGuild>>

    @GET("guilds/{guildId}/channels")
    fun getGuildChannels(@Path("guildId") guildId: String): Call<List<DiscordChannel>>

    @GET("channels/{channelId}/messages")
    fun getMessagesSync(
        @Path("channelId") channelId: String,
        @Query("limit") limit: Int = 5
    ): Call<List<DiscordMessage>>
}

