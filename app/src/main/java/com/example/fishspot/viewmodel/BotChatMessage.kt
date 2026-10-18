package com.example.fishspot.viewmodel



enum class BotSender {
    USER, BOT
}

data class BotChatMessage(
    val id: String = "",
    val text: String = "",
    val sender: BotSender = BotSender.USER,
    val timestamp: Long = 0L
)