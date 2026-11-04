package com.example.fishspot.feature.spots

import android.content.Context
import com.google.gson.stream.JsonReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sqrt

/**
 * Najbliža imenovana voda za mesto ulova, iz lokalne mape voda Srbije (assets/Mape/srbija_vode.json,
 * ista kao za obaveštenja o vodama). Radi i bez interneta. Npr. tačka u Babušnici -> "Lužnica (reka)".
 */
object NajblizaVoda {
    private const val NAJDALJE_M = 2000.0
    // Mali potok mora biti oko 300 m bliži od reke da bi bio izabran (ulov je češće na reci)
    private const val PREDNOST_VECIM_M = 300.0

    private class Voda(val naziv: String, val tip: String, val kategorija: String, val lat: FloatArray, val lng: FloatArray)

    private var vode: List<Voda>? = null
    // Imena tekućih voda: velike reke (npr. Dunav) su u mapi i kao vodena površina, pa se po imenu zna da su reka
    private var imenaReka: Set<String> = emptySet()

    // Nazivi koji nisu prava imena ("Kanal") i vode koje nisu za pecanje (fontana ispred kulturnog centra)
    private val opsteReci = setOf("kanal", "potok", "reka", "rečica", "jezero", "bara", "ribnjak", "lokva", "jarak")
    private val nijeZaPecanje = listOf("fontan", "bazen", "česm", "šadrvan", "šedrvan", "akvarijum", "vodoskok")
    private val brava = Mutex()

    /** Naziv za mesto ulova, npr. "Lužnica (reka)" ili "Vlasinsko jezero"; null ako u blizini nema vode. */
    suspend fun nadji(context: Context, lat: Double, lng: Double): String? = withContext(Dispatchers.Default) {
        val sve = ucitaj(context) ?: return@withContext null
        // Ravna projekcija oko tačke: dovoljno tačno za razdaljine do par kilometara
        val mPoStepenuLat = 111_320.0
        val mPoStepenuLng = 111_320.0 * cos(Math.toRadians(lat))
        var najbolja: Voda? = null
        var najboljiSkor = Double.MAX_VALUE
        for (v in sve) {
            var d = Double.MAX_VALUE
            for (i in v.lat.indices) {
                val x1 = (v.lng[i] - lng) * mPoStepenuLng
                val y1 = (v.lat[i] - lat) * mPoStepenuLat
                if (i + 1 < v.lat.size) {
                    // Razdaljina do duži između dve susedne tačke reke (velike reke imaju retke tačke)
                    val x2 = (v.lng[i + 1] - lng) * mPoStepenuLng
                    val y2 = (v.lat[i + 1] - lat) * mPoStepenuLat
                    d = minOf(d, doDuzi(x1, y1, x2, y2))
                } else d = minOf(d, sqrt(x1 * x1 + y1 * y1))
            }
            if (d > NAJDALJE_M) continue
            val skor = d + if (v.tip == "stream" && v.kategorija == "male") PREDNOST_VECIM_M else 0.0
            if (skor < najboljiSkor) { najboljiSkor = skor; najbolja = v }
        }
        najbolja?.let { opis(it) }
    }

    // Razdaljina od koordinatnog početka (mesto ulova) do duži (x1,y1)-(x2,y2), u metrima
    private fun doDuzi(x1: Double, y1: Double, x2: Double, y2: Double): Double {
        val dx = x2 - x1
        val dy = y2 - y1
        val duzina2 = dx * dx + dy * dy
        val t = if (duzina2 == 0.0) 0.0 else (-(x1 * dx + y1 * dy) / duzina2).coerceIn(0.0, 1.0)
        val px = x1 + t * dx
        val py = y1 + t * dy
        return sqrt(px * px + py * py)
    }

    // "Lužnica (reka)"; bez dodatka kad ime već kaže šta je ("Vlasinsko jezero", "Crni potok")
    // Ime kao u mapi ume da bude dvojezično ("Dunav / Дунав", "Dunărea - Дунав"): uzima se srpski deo, latinicom
    private fun delovi(naziv: String): List<String> =
        naziv.split(" / ", "/", " - ").map { it.trim() }.filter { it.isNotEmpty() }

    private fun cistoIme(naziv: String): String {
        val d = delovi(naziv)
        val srpski = d.firstOrNull { deo -> deo.any { it in 'Ѐ'..'ӿ' } } ?: d.firstOrNull() ?: naziv // deo na ćirilici
        return NazivMesta.uLatinicu(srpski).trim()
    }

    private fun opis(v: Voda): String {
        val ime = cistoIme(v.naziv)
        val malo = ime.lowercase()
        if (v.tip == "water" && delovi(v.naziv).any { NazivMesta.uLatinicu(it).lowercase() in imenaReka }) return "$ime (reka)"
        if (listOf("reka", "potok", "jezero", "kanal", "bara", "blato", "akumulacija", "rukavac").any { it in malo }) return ime
        val tip = when (v.tip) {
            "river" -> "reka"
            "stream" -> if (v.kategorija == "male") "potok" else "reka" // u mapi su i manje reke označene kao potok
            "canal" -> "kanal"
            else -> "jezero"
        }
        return "$ime ($tip)"
    }

    // Mapa se čita jednom, u sažetom obliku (oko 200.000 tačaka kao Float nizovi umesto JSON objekata)
    private suspend fun ucitaj(context: Context): List<Voda>? = brava.withLock {
        vode ?: try {
            withContext(Dispatchers.IO) {
                val lista = mutableListOf<Voda>()
                JsonReader(context.assets.open("Mape/srbija_vode.json").bufferedReader()).use { r ->
                    r.beginArray()
                    while (r.hasNext()) {
                        var naziv = ""; var tip = ""; var kategorija = ""
                        val lat = ArrayList<Float>(); val lng = ArrayList<Float>()
                        r.beginObject()
                        while (r.hasNext()) {
                            when (r.nextName()) {
                                "name" -> naziv = r.nextString()
                                "type" -> tip = r.nextString()
                                "kategorija" -> kategorija = r.nextString()
                                "coordinates" -> {
                                    r.beginArray()
                                    while (r.hasNext()) {
                                        r.beginArray()
                                        lat.add(r.nextDouble().toFloat()); lng.add(r.nextDouble().toFloat())
                                        while (r.hasNext()) r.skipValue()
                                        r.endArray()
                                    }
                                    r.endArray()
                                }
                                else -> r.skipValue()
                            }
                        }
                        r.endObject()
                        val malo = NazivMesta.uLatinicu(naziv).trim().lowercase()
                        val pravoIme = malo.isNotEmpty() && malo !in opsteReci && nijeZaPecanje.none { it in malo }
                        if (pravoIme && tip != "dock" && lat.isNotEmpty()) {
                            lista.add(Voda(naziv, tip, kategorija, lat.toFloatArray(), lng.toFloatArray()))
                        }
                    }
                    r.endArray()
                }
                lista
            }.also { lista ->
                imenaReka = lista.filter { it.tip == "river" || it.tip == "stream" }
                    .flatMap { delovi(it.naziv) }.map { NazivMesta.uLatinicu(it).lowercase() }.toSet()
                vode = lista
            }
        } catch (e: Exception) {
            android.util.Log.w("NajblizaVoda", "Mapa voda nije učitana", e)
            null
        }
    }
}
