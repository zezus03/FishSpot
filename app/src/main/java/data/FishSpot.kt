
package com.example.fishspot.data

data class FishSpot(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val naziv: String = "",
    val vrstaRibe: String = "",
    val ocena: Float = 0.0f,
    val opis: String = "",
    val slikaUrl: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val duzinaRibe: Double = 0.0,
    val tezinaRibe: Double = 0.0,
    val mamac: String = "",
    val tehnika: String = ""
)