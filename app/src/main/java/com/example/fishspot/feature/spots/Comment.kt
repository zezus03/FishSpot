package com.example.fishspot.feature.spots

data class Comment(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val text: String = "",
    val timestamp: Long = System.currentTimeMillis()
)