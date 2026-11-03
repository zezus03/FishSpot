package com.example.fishspot.core.model

// Javni profil (Korisnici/{uid}): vide ga svi prijavljeni. Zato ovde nema e-adrese ni telefona;
// e-adresu ima samo Firebase prijava.
data class Korisnici(
    var id: String = "",
    var userName: String = "",
    var imePrezime: String = "",
    var slikaURL: String = "",
    var poeni: Int = 0
)
