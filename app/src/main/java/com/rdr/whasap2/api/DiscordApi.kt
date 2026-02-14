package com.rdr.whasap2.api

import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DiscordApi {

    @GET("users/@me")
    suspend fun getUser(): DiscordUser

    @GET("users/@me/channels")
    suspend fun getChannels(): List<DiscordChannel>

    @GET("users/@me/guilds")
    suspend fun getGuilds(): List<DiscordGuild>

    @GET("guilds/{guildId}/channels")
    suspend fun getGuildChannels(@Path("guildId") guildId: String): List<DiscordChannel>

    @GET("guilds/{guildId}/voice-states")
    suspend fun getVoiceStates(@Path("guildId") guildId: String): List<VoiceState>

    @GET("channels/{channelId}/messages")
    suspend fun getMessages(
        @Path("channelId") channelId: String,
        @Query("limit") limit: Int = 50,
        @Query("before") before: String? = null
    ): List<DiscordMessage>

    @POST("channels/{channelId}/messages")
    suspend fun sendMessage(@Path("channelId") channelId: String, @Body body: SendMessageRequest): DiscordMessage

    @retrofit2.http.Multipart
    @POST("channels/{channelId}/messages")
    suspend fun sendFileMessage(
        @Path("channelId") channelId: String,
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): DiscordMessage

    @retrofit2.http.DELETE("channels/{channelId}/messages/{messageId}")
    suspend fun deleteMessage(
        @Path("channelId") channelId: String,
        @Path("messageId") messageId: String
    ): retrofit2.Response<Void>
}

data class DiscordChannel(
    val id: String,
    val type: Int,
    val name: String?, // Channels in guilds have names
    val recipients: List<DiscordUser>?,
    val last_message_id: String? = null,
    val bitrate: Int? = null,
    val user_limit: Int? = null
)

data class DiscordUser(
    val id: String,
    val username: String,
    val discriminator: String? = null,
    val avatar: String?
)

data class DiscordMessage(
    val id: String,
    val content: String,
    val author: DiscordUser,
    val timestamp: String,
    val attachments: List<DiscordAttachment>? = null,
    val embeds: List<DiscordEmbed>? = null
)

data class DiscordAttachment(
    val id: String,
    val filename: String,
    val url: String,
    val proxy_url: String,
    val content_type: String? = null,
    val width: Int? = null,
    val height: Int? = null
)

data class DiscordEmbed(
    val url: String? = null,
    val type: String? = null,
    val image: DiscordEmbedMedia? = null,
    val thumbnail: DiscordEmbedMedia? = null,
    val video: DiscordEmbedMedia? = null
)

data class DiscordEmbedMedia(
    val url: String? = null,
    val proxy_url: String? = null
)

data class SendMessageRequest(
    val content: String
)

data class DiscordGuild(
    val id: String? = null,
    val name: String? = null,
    val icon: String?
)

data class VoiceState(
    val channel_id: String?,
    val user_id: String,
    val member: GuildMember? = null,
    val self_mute: Boolean = false,
    val self_deaf: Boolean = false
)

data class GuildMember(
    val user: DiscordUser? = null,
    val nick: String? = null
)
