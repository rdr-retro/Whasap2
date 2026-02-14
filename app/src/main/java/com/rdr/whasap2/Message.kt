package com.rdr.whasap2

data class Message(
    val type: Int, // 0: Incoming, 1: Outgoing, 2: System
    val content: String,
    val time: String = "",
    val sender: String = "",
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val messageId: String? = null
) {
    companion object {
        const val TYPE_INCOMING = 0
        const val TYPE_OUTGOING = 1
        const val TYPE_SYSTEM = 2
    }
}
