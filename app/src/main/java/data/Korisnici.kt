package com.example.fishspot.data

import com.google.firebase.firestore.PropertyName

data class Korisnici(
    var id: String = "",
    var userName: String = "",
    var imePrezime: String = "",
    var telefon: String = "",
    var email: String = "",
    var sifra: String = "",

    // Sada je var, pa @set: radi savršeno!
    @get:PropertyName("fotoUrl") @set:PropertyName("fotoUrl")
    @PropertyName("fotoUrl")
    var slikaURL: String = "",

    var poeni: Int = 0
)