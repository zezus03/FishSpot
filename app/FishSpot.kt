// Fajl: data/FishSpot.kt

package com.example.fishspot.data

import com.google.firebase.firestore.GeoPoint

data class FishSpot(
    val id: String = "", // ID dokumenta iz Firestore-a
    val authorId: String = "",
    val authorUsername: String = "",
    val naziv: String = "",
    val vrstaRibe: String = "",
    val ocena: Float = 0.0f,
    val opis: String = "",
    val slikaUrl: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis()
)