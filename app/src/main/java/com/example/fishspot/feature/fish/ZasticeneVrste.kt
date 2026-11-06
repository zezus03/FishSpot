package com.example.fishspot.feature.fish

// Vrste u trajnoj zabrani ulova koje postoje na spisku vrsta za ulov (VrsteRiba).
// Takav ulov ne može da se objavi, a server mu ne daje poene (functions/ulovi.js, ZASTICENE_VRSTE).
// Ostale zaštićene vrste (jesetre, vretenari, crnka...) nisu na spisku, pa ne mogu ni da se izaberu.
object ZasticeneVrste {
    val ID = setOf("karas", "linjak", "keciga")

    fun jeZasticena(vrstaId: String?): Boolean = vrstaId != null && vrstaId in ID

    fun poruka(naziv: String): String =
        "$naziv je u trajnoj zabrani ulova i ne sme se loviti. Pusti ribu odmah u vodu. " +
            "Ovakav ulov ne može da se objavi."

    // Greška za dugme Sačuvaj: vrsta ulova ili bilo koja vrsta u čuvarki je zaštićena
    fun greska(vrsta: VrstaRibe?, vrsteCuvarke: List<VrstaRibe>): String? {
        val zasticena = (listOfNotNull(vrsta) + vrsteCuvarke).firstOrNull { jeZasticena(it.id) } ?: return null
        return "${zasticena.naziv} je u trajnoj zabrani ulova, pusti ribu u vodu"
    }
}
