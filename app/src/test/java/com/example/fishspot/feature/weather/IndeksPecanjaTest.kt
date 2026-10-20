package com.example.fishspot.feature.weather

import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class IndeksPecanjaTest {

    private fun datum(godina: Int, mesec: Int, dan: Int, sat: Int = 12): Long =
        Calendar.getInstance().apply { set(godina, mesec, dan, sat, 0, 0) }.timeInMillis

    @Test
    fun dobarJesenjiDanJeVisoko() {
        val indeks = IndeksPecanja.izracunaj(
            pritisak = 1015, promenaPritiska = -1, temperatura = 16.0, vetar = 3.0,
            sifraVremena = "04d", sansaKise = 0.2, vreme = datum(2026, Calendar.OCTOBER, 15), sat = null
        )
        println("Dobar jesenji dan: $indeks%")
        assertTrue("Dobar jesenji dan treba da bude iznad 70%, a dobijeno je $indeks", indeks > 70)
    }

    @Test
    fun obicanLetnjiDanJeDobar() {
        val indeks = IndeksPecanja.izracunaj(
            pritisak = 1012, promenaPritiska = 2, temperatura = 24.0, vetar = 2.0,
            sifraVremena = "01d", sansaKise = 0.0, vreme = datum(2026, Calendar.JULY, 10), sat = null
        )
        println("Običan letnji dan: $indeks%")
        assertTrue("Običan letnji dan treba da bude između 50% i 85%, a dobijeno je $indeks", indeks in 51..85)
    }

    @Test
    fun losZimskiDanJeNisko() {
        val indeks = IndeksPecanja.izracunaj(
            pritisak = 1035, promenaPritiska = 9, temperatura = -3.0, vetar = 12.0,
            sifraVremena = "13d", sansaKise = 0.9, vreme = datum(2026, Calendar.JANUARY, 20), sat = null
        )
        println("Loš zimski dan: $indeks%")
        assertTrue("Loš zimski dan treba da bude ispod 20%, a dobijeno je $indeks", indeks < 20)
    }

    @Test
    fun olujaSmanjujeIndeks() {
        fun dan(sifra: String) = IndeksPecanja.izracunaj(
            pritisak = 1008, promenaPritiska = -2, temperatura = 22.0, vetar = 4.0,
            sifraVremena = sifra, sansaKise = 0.8, vreme = datum(2026, Calendar.JUNE, 12), sat = null
        )
        println("Oblačno: ${dan("04d")}%, oluja: ${dan("11d")}%")
        assertTrue(dan("11d") < dan("04d") - 10)
    }

    @Test
    fun zoraJeBoljaOdPodnevaUleto() {
        fun uSat(sat: Int) = IndeksPecanja.izracunaj(
            pritisak = 1014, promenaPritiska = 0, temperatura = 27.0, vetar = 2.0,
            sifraVremena = "01d", sansaKise = 0.0, vreme = datum(2026, Calendar.AUGUST, 5, sat), sat = sat
        )
        println("Zora (6h): ${uSat(6)}%, podne (13h): ${uSat(13)}%")
        assertTrue(uSat(6) > uSat(13))
    }

    @Test
    fun indeksJeUvekIzmedju5i95() {
        val najbolji = IndeksPecanja.izracunaj(1015, -5, 18.0, 3.0, "04d", 0.1, datum(2026, Calendar.SEPTEMBER, 7, 6), 6)
        val najgori = IndeksPecanja.izracunaj(985, 15, -10.0, 20.0, "11d", 1.0, datum(2026, Calendar.JANUARY, 10, 2), 2)
        println("Najbolji mogući: $najbolji%, najgori mogući: $najgori%")
        assertTrue(najbolji in 5..95 && najgori in 5..95)
    }
}
