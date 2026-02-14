package com.rdr.whasap2

data class Chat(
    val id: String = "",
    val name: String,
    val message: String,
    val time: String,
    val unreadCount: Int = 0,
    val isGuild: Boolean = false,
    val imageUrl: String? = null
)
