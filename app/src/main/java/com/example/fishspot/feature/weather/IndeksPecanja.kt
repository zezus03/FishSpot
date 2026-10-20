package com.example.fishspot.feature.weather

import java.util.Calendar
import kotlin.math.abs

/**
 * Procena uslova za pecanje (0–100%) iz vremenske prognoze.
 *
 * Kreće od neutralnih 45 bodova i dodaje/oduzima po faktorima koje pecaroši prate:
 * pritisak i njegova promena, temperatura, vetar, oblačnost/padavine, godišnje doba,
 * faza meseca i (samo za trenutno vreme) doba dana. Rezultat je ograničen na 5–95.
 */
object IndeksPecanja {

    private const val POCETNI_BODOVI = 45

    /**
     * @param promenaPritiska promena za ~24 h u hPa (sutra minus danas), null ako nije poznata
     * @param sifraVremena OpenWeather šifra ikonice, npr. "04d" (oblačno), "10n" (kiša)
     * @param sansaKise 0.0–1.0
     * @param sat sat u danu za trenutne uslove; null za procenu celog dana
     */
    fun izracunaj(
        pritisak: Int,
        promenaPritiska: Int?,
        temperatura: Double,
        vetar: Double,
        sifraVremena: String?,
        sansaKise: Double?,
        vreme: Long,
        sat: Int?
    ): Int {
        var bodovi = POCETNI_BODOVI

        // 1. Pritisak: stabilan, umeren pritisak je najbolji
        bodovi += when (pritisak) {
            in 1010..1020 -> 10
            in 1005..1009, in 1021..1025 -> 4
            in 1000..1004, in 1026..1030 -> -4
            else -> -12
        }

        // 2. Promena pritiska: blag pad pred front aktivira ribu, nagla promena je smiruje
        if (promenaPritiska != null) {
            bodovi += when {
                abs(promenaPritiska) <= 3 -> 4
                promenaPritiska in -6..-4 -> 6
                promenaPritiska in 4..6 -> 0
                else -> -8
            }
        }

        // 3. Temperatura vazduha
        bodovi += when (temperatura.toInt()) {
            in 12..24 -> 8
            in 8..11, in 25..28 -> 0
            in 4..7, in 29..32 -> -8
            else -> -15
        }

        // 4. Vetar: blag vetar pravi talasić i meša vodu, jak vetar smeta
        bodovi += when {
            vetar < 1.0 -> 0
            vetar <= 5.0 -> 4
            vetar < 10.0 -> -2
            else -> -10
        }

        // 5. Oblačnost i padavine (prema OpenWeather šifri: 01 vedro ... 11 oluja, 13 sneg, 50 magla)
        bodovi += when (sifraVremena?.take(2)) {
            "02", "03", "04" -> 4
            "01" -> if (temperatura > 26) -3 else 0
            "09", "10" -> if ((sansaKise ?: 0.0) < 0.6) 2 else -3
            "11" -> -15
            "13" -> -10
            "50" -> 2
            else -> 0
        }

        // 6. Godišnje doba (meseci 0 = januar)
        val kalendar = Calendar.getInstance().apply { timeInMillis = vreme }
        bodovi += when (kalendar.get(Calendar.MONTH)) {
            11, 0, 1 -> -10 // zima: usporen metabolizam
            2 -> 0
            3, 4 -> 5       // prolećno buđenje
            5, 6, 7 -> 0    // leto: aktivno, ali vrućina zna da smeta
            8, 9 -> 8       // jesen: riba se hrani pred zimu
            else -> -3      // novembar
        }

        // 7. Mesec: mlad ili pun mesec (±2 dana) prema solunarnoj teoriji
        val faza = fazaMeseca(vreme)
        if (faza < 0.07 || faza > 0.93 || faza in 0.43..0.57) bodovi += 5

        // 8. Doba dana (samo za trenutne uslove): zora i sumrak su najbolji
        if (sat != null) {
            bodovi += when (sat) {
                in 5..9, in 17..21 -> 10
                22, 23, in 0..4 -> -5
                in 12..15 -> if (temperatura > 25) -5 else 0
                else -> 0
            }
        }

        return bodovi.coerceIn(5, 95)
    }

    // 0.0 = mlad mesec, 0.5 = pun mesec
    private fun fazaMeseca(vreme: Long): Double {
        val poznatMladMesec = 1704974220000L // 11.01.2024. mlad mesec
        val ciklus = 29.530588853 * 24 * 60 * 60 * 1000
        val faza = ((vreme - poznatMladMesec) % ciklus) / ciklus
        return if (faza < 0) faza + 1 else faza
    }
}
