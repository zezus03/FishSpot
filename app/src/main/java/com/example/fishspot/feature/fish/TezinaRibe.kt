package com.example.fishspot.feature.fish

import kotlin.math.pow

// Procena težine ribe iz dužine po formuli W = a * L^b
// (W u gramima, L ukupna dužina u cm).
//
// Izvor koeficijenata: FishBase "Bayesian length-weight" (Froese, Thorson & Reyes 2014,
// FishBase Ref. 93245), koji objedinjuje sva objavljena merenja vrste. Izuzeci su označeni
// komentarom. Stvarna težina zavisi od kondicije ribe (mrest, ishrana) i može da odstupa ±15–20%.
object TezinaRibe {

    private data class Koeficijenti(val a: Double, val b: Double)

    // Za vrste koje ne prepoznamo koristimo opštu formulu za "prosečnu" ribu
    private val OPSTI = Koeficijenti(0.0100, 3.00)

    private val koeficijenti = mapOf(
        // Ciprinidi (šaranske ribe)
        "Saran" to Koeficijenti(0.01778, 2.95),
        "Babuska" to Koeficijenti(0.01349, 3.05),
        "Karas" to Koeficijenti(0.01349, 3.04),
        "Amur" to Koeficijenti(0.00933, 3.09),
        "Beli tolstolobik" to Koeficijenti(0.00891, 3.10),
        "Sivi tolstolobik" to Koeficijenti(0.00977, 3.10),
        "Tolstolobik" to Koeficijenti(0.00891, 3.10), // bez oznake: kao beli, češći u ulovu
        "Linjak" to Koeficijenti(0.01122, 3.02),
        "Deverika" to Koeficijenti(0.00759, 3.14),
        "Krupatica" to Koeficijenti(0.00741, 3.15),
        "Crnooka" to Koeficijenti(0.00955, 3.10),
        "Nosara" to Koeficijenti(0.00724, 3.14),
        "Bodorka" to Koeficijenti(0.00741, 3.16),
        "Plotica" to Koeficijenti(0.00741, 3.16), // nema merenja vrste; kao bodorka (isti rod Rutilus)
        "Crvenperka" to Koeficijenti(0.00776, 3.16),
        "Klen" to Koeficijenti(0.00724, 3.13),
        "Jaz" to Koeficijenti(0.00589, 3.15),
        "Bucov" to Koeficijenti(0.00468, 3.14),
        "Skobalj" to Koeficijenti(0.00724, 3.12),
        "Mrena" to Koeficijenti(0.00912, 2.97),
        "Potocna mrena" to Koeficijenti(0.00794, 3.05), // procena za rod
        "Ukljeva" to Koeficijenti(0.00562, 3.12),
        "Sabljarka" to Koeficijenti(0.00400, 3.119), // merenja sa reke Mure (pritoka Dunava)
        "Krkusa" to Koeficijenti(0.00724, 3.12), // procena za rod
        "Gavcica" to Koeficijenti(0.01047, 3.09),

        // Grgečke ribe
        "Grgec" to Koeficijenti(0.00912, 3.11),
        "Smud kamenjar" to Koeficijenti(0.00832, 3.08), // procena za porodicu
        "Smud" to Koeficijenti(0.00589, 3.12),
        "Balavac" to Koeficijenti(0.00955, 3.00),

        // Grabljivice i ostale
        "Stuka" to Koeficijenti(0.00447, 3.08),
        "Som" to Koeficijenti(0.00631, 2.99),
        "Crni patuljasti som" to Koeficijenti(0.00871, 3.12),
        "Patuljasti som" to Koeficijenti(0.00832, 3.12),
        "Manic" to Koeficijenti(0.00537, 3.04),
        "Jegulja" to Koeficijenti(0.00095, 3.17),
        "Keciga" to Koeficijenti(0.00138, 3.28),
        "Kesega" to Koeficijenti(0.00138, 3.28), // lokalni naziv za kečigu

        // Salmonidi
        "Potocna pastrmka" to Koeficijenti(0.00871, 3.03),
        "Kalifornijska pastrmka" to Koeficijenti(0.00933, 3.03),
        "Duzicasta pastrmka" to Koeficijenti(0.00933, 3.03), // drugi naziv za kalifornijsku
        "Pastrmka" to Koeficijenti(0.00871, 3.03), // bez oznake: kao potočna
        "Mladica" to Koeficijenti(0.01202, 3.01),
        "Lipljen" to Koeficijenti(0.00741, 3.07),
        "Potocna zlatovcica" to Koeficijenti(0.00912, 3.03),

        // Unete (strane) vrste
        "Suncanica" to Koeficijenti(0.01148, 3.11),
        "Glavoc" to Koeficijenti(0.01122, 3.04)
    )

    // Duži nazivi prvi, da "Smud kamenjar" ne bude prepoznat kao "Smud"
    private val kljuceviPoDuzini = koeficijenti.keys.sortedByDescending { it.length }

    // Da li za unetu vrstu imamo posebne koeficijente (inače se koristi opšta formula)
    fun poznataVrsta(imeRibe: String): Boolean = nadjiVrstu(imeRibe) != null

    // Vraća procenjenu težinu u kilogramima ili null ako dužina nije uneta
    fun proceniKg(imeRibe: String, duzinaCm: Double?): Double? {
        if (duzinaCm == null || duzinaCm <= 0.0) return null
        val k = nadjiVrstu(imeRibe)?.let { koeficijenti[it] } ?: OPSTI
        return k.a * duzinaCm.pow(k.b) / 1000.0
    }

    private fun nadjiVrstu(imeRibe: String): String? {
        val unos = LovostajChecker.normalizujTekst(imeRibe)
        if (unos.isEmpty()) return null
        return kljuceviPoDuzini.find { unos.contains(LovostajChecker.normalizujTekst(it)) }
    }
}
