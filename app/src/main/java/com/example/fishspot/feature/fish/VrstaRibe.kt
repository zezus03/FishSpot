package com.example.fishspot.feature.fish

// Vrsta ribe iz kolekcije VrsteRiba (lista je u functions/podaci/vrsteRiba.json)
data class VrstaRibe(
    val id: String = "",
    val naziv: String = "",
    val latinskiNaziv: String = ""
) {
    // Tekst koji korisnik vidi u padajućem meniju i u polju: "Deverika (Abramis brama)"
    val prikaz: String
        get() = if (latinskiNaziv.isBlank()) naziv else "$naziv ($latinskiNaziv)"
}
