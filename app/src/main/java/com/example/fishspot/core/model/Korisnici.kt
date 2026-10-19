package com.example.fishspot.core.model

data class Korisnici(
    var id: String = "",
    var userName: String = "",
    var imePrezime: String = "",
    var telefon: String = "",
    var email: String = "",
    var slikaURL: String = "",
    var poeni: Int = 0
)
