package com.example.fishspot.feature.fish

import kotlin.math.pow

// Procena težine ribe iz dužine po formuli W = a * L^b
// (W u gramima, L ukupna dužina u cm). Koeficijenti a i b su približne
// prosečne vrednosti za vrstu (FishBase); stvarna težina zavisi od kondicije ribe.
object TezinaRibe {

    private data class Koeficijenti(val a: Double, val b: Double)

    // Za vrste koje ne prepoznamo koristimo opštu formulu za "prosečnu" ribu
    private val OPSTI = Koeficijenti(0.0100, 3.00)

    private val koeficijenti = mapOf(
        "Saran" to Koeficijenti(0.0138, 3.02),
        "Som" to Koeficijenti(0.0048, 3.10),
        "Stuka" to Koeficijenti(0.0049, 3.10),
        "Smud kamenjar" to Koeficijenti(0.0070, 3.10),
        "Smud" to Koeficijenti(0.0063, 3.08),
        "Bucov" to Koeficijenti(0.0074, 3.10),
        "Deverika" to Koeficijenti(0.0089, 3.12),
        "Klen" to Koeficijenti(0.0098, 3.06),
        "Potocna mrena" to Koeficijenti(0.0090, 3.05),
        "Mrena" to Koeficijenti(0.0081, 3.07),
        "Plotica" to Koeficijenti(0.0100, 3.08),
        "Jaz" to Koeficijenti(0.0098, 3.10),
        "Skobalj" to Koeficijenti(0.0080, 3.10),
        "Mladica" to Koeficijenti(0.0062, 3.10),
        "Lipljen" to Koeficijenti(0.0080, 3.10),
        "Potocna pastrmka" to Koeficijenti(0.0105, 3.03),
        "Duzicasta pastrmka" to Koeficijenti(0.0110, 3.03),
        "Kalifornijska pastrmka" to Koeficijenti(0.0110, 3.03),
        "Pastrmka" to Koeficijenti(0.0105, 3.03),
        "Grgec" to Koeficijenti(0.0089, 3.13),
        "Bodorka" to Koeficijenti(0.0105, 3.07),
        "Crvenperka" to Koeficijenti(0.0110, 3.07),
        "Krupatica" to Koeficijenti(0.0100, 3.10),
        "Kesega" to Koeficijenti(0.0030, 3.15),
        "Jegulja" to Koeficijenti(0.0010, 3.20),
        "Ukljeva" to Koeficijenti(0.0070, 3.15),
        "Sabljarka" to Koeficijenti(0.0060, 3.10),
        "Babuska" to Koeficijenti(0.0150, 3.05),
        "Karas" to Koeficijenti(0.0160, 3.05),
        "Amur" to Koeficijenti(0.0110, 3.03),
        "Tolstolobik" to Koeficijenti(0.0115, 3.03),
        "Linjak" to Koeficijenti(0.0120, 3.08),
        "Suncanica" to Koeficijenti(0.0150, 3.10),
        "Patuljasti som" to Koeficijenti(0.0100, 3.05)
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
