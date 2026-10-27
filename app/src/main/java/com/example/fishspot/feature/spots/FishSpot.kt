
package com.example.fishspot.feature.spots

data class FishSpot(
    val id: String = "",
    val authorId: String = "",
    val authorUsername: String = "",
    val naziv: String = "",
    val vrstaRibe: String = "",
    val vrstaId: String = "", // ID iz kolekcije VrsteRiba (prazno za stare ulove)
    val latinskiNaziv: String = "",
    val ocena: Float = 0.0f,
    val opis: String = "",
    val slikaUrl: String? = null,
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val duzinaRibe: Double = 0.0,
    val tezinaRibe: Double = 0.0,
    val mamac: String = "",
    val tehnika: String = "",
    val temperatura: String = "",
    val pritisak: String = "",
    val vetar: String = "",
    val likedBy: List<String> = emptyList() // <-- OVO DODAJ!
)
// Ponuđeni mamci i tehnike (dodavanje i izmena ulova)
object OpcijeUlova {
    val mamci = listOf("Crv", "Glista", "Šećerac", "Boila", "Pop-up", "Keder", "Varalica", "Mušica")
    val tehnike = listOf("Plovak", "Dubinac", "Varalica", "Feeder", "Mušičarenje")

    // Koje tehnike imaju smisla uz izabrani mamac (iste parove proveravaju i pravila baze)
    fun dozvoljeneTehnike(mamac: String): List<String> = when (mamac) {
        "Varalica" -> listOf("Varalica")
        "Mušica" -> listOf("Mušičarenje")
        "Boila", "Pop-up" -> listOf("Dubinac", "Feeder")
        "Keder" -> listOf("Plovak", "Dubinac")
        else -> listOf("Plovak", "Dubinac", "Feeder")
    }
}
