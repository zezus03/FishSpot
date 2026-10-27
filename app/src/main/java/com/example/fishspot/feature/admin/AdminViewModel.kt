package com.example.fishspot.feature.admin

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishspot.core.theme.FishPoruka
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Da li da se prikaže ulaz u admin panel: odluku donosi server (functions/admin.js, adrese su samo tamo)
object Admini {
    private var poslednjiUid: String? = null
    private var rezultat = false

    suspend fun proveri(): Boolean {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        if (uid == poslednjiUid) return rezultat
        return try {
            @Suppress("UNCHECKED_CAST")
            val odgovor = FirebaseFunctions.getInstance().getHttpsCallable("jesamLiAdmin").call().await().data as Map<String, Any?>
            rezultat = odgovor["admin"] == true || odgovor["potvrda"] == true
            poslednjiUid = uid
            rezultat
        } catch (e: Exception) {
            false
        }
    }
}

data class Razlog(val razlog: String, val opis: String, val prijavio: String, val vreme: Long?)

data class PrijavljenUlov(
    val id: String,
    val status: String,
    val brojPrijava: Int,
    val autorUlova: String,
    val poslednjaPrijava: Long?,
    val oduzetoPoena: Int,
    val resioAdmin: String,
    val vrstaRibe: String,
    val naziv: String,
    val opis: String,
    val duzinaRibe: Double,
    val tezinaRibe: Double,
    val mamac: String,
    val tehnika: String,
    val slikaUrl: String,
    val razlozi: List<Razlog>
)

data class AdminStatistika(val korisnika: Long = 0, val ulova: Long = 0, val prijavaDanas: Long = 0, val uklonjenih: Int = 0)

class AdminViewModel : ViewModel() {
    private val functions = FirebaseFunctions.getInstance()

    private val _prijavljeni = MutableStateFlow<List<PrijavljenUlov>>(emptyList())
    val prijavljeni = _prijavljeni.asStateFlow()
    private val _statistika = MutableStateFlow(AdminStatistika())
    val statistika = _statistika.asStateFlow()
    private val _ucitava = MutableStateFlow(true)
    val ucitava = _ucitava.asStateFlow()
    private val _uToku = MutableStateFlow<String?>(null) // id ulova čija se akcija izvršava
    val uToku = _uToku.asStateFlow()

    // Server pušta samo admine sa potvrđenom e-adresom (nalozi napravljeni lozinkom je nemaju dok se ne potvrdi)
    private val _trebaPotvrda = MutableStateFlow(false)
    val trebaPotvrda = _trebaPotvrda.asStateFlow()

    fun posaljiLinkZaPotvrdu() {
        val korisnik = FirebaseAuth.getInstance().currentUser ?: return
        korisnik.sendEmailVerification()
            .addOnSuccessListener { FishPoruka.prikazi("Link za potvrdu je poslat na ${korisnik.email}. Otvori mail i klikni na link.", dugo = true) }
            .addOnFailureListener { FishPoruka.prikazi("Link nije poslat. Pokušaj ponovo za minut.", dugo = true) }
    }

    // Posle klika na link: osvežavamo nalog i token (u tokenu je oznaka da je adresa potvrđena)
    fun proveriPotvrdu() {
        viewModelScope.launch {
            try {
                val korisnik = FirebaseAuth.getInstance().currentUser ?: return@launch
                korisnik.reload().await()
                if (korisnik.isEmailVerified) {
                    korisnik.getIdToken(true).await()
                    _trebaPotvrda.value = false
                    osvezi()
                } else {
                    FishPoruka.prikazi("Adresa još nije potvrđena. Klikni na link u mailu (proveri i Spam).", dugo = true)
                }
            } catch (e: Exception) {
                FishPoruka.prikazi("Provera nije uspela. Proveri internet.", dugo = true)
            }
        }
    }

    init { osvezi() }

    fun osvezi() {
        viewModelScope.launch {
            _ucitava.value = true
            try {
                @Suppress("UNCHECKED_CAST")
                val podaci = functions.getHttpsCallable("adminPregled").call().await().data as Map<String, Any?>
                val st = podaci["statistika"] as? Map<String, Any?> ?: emptyMap()
                _statistika.value = AdminStatistika(
                    korisnika = (st["korisnika"] as? Number)?.toLong() ?: 0,
                    ulova = (st["ulova"] as? Number)?.toLong() ?: 0,
                    prijavaDanas = (st["prijavaDanas"] as? Number)?.toLong() ?: 0,
                    uklonjenih = (st["uklonjenih"] as? Number)?.toInt() ?: 0
                )
                @Suppress("UNCHECKED_CAST")
                _prijavljeni.value = (podaci["prijavljeni"] as? List<Map<String, Any?>>).orEmpty().map { it.uPrijavu() }
            } catch (e: Exception) {
                Log.e("Admin", "Pregled nije učitan", e)
                val odbijen = (e as? com.google.firebase.functions.FirebaseFunctionsException)?.code ==
                    com.google.firebase.functions.FirebaseFunctionsException.Code.PERMISSION_DENIED
                if (odbijen && FirebaseAuth.getInstance().currentUser?.isEmailVerified == false) {
                    _trebaPotvrda.value = true
                } else {
                    FishPoruka.prikazi(if (odbijen) "Ovaj nalog nema admin pristup." else "Admin pregled nije učitan. Proveri internet.", dugo = true)
                }
            } finally {
                _ucitava.value = false
            }
        }
    }

    fun ulovURedu(id: String) = akcija(id, "adminUlovURedu", "Ulov je vraćen i zaštićen od novih prijava")
    fun obrisiTrajno(id: String) = akcija(id, "adminObrisiUlov", "Ulov je trajno obrisan")

    private fun akcija(id: String, funkcija: String, poruka: String) {
        viewModelScope.launch {
            _uToku.value = id
            try {
                functions.getHttpsCallable(funkcija).call(mapOf("spotId" to id)).await()
                // Rešen ulov odmah nestaje sa liste
                _prijavljeni.value = _prijavljeni.value.filterNot { it.id == id }
                FishPoruka.prikazi(poruka)
                kotlinx.coroutines.delay(1500) // da server završi vraćanje ili brisanje pre osvežavanja
                osvezi()
            } catch (e: Exception) {
                Log.e("Admin", "$funkcija nije uspela", e)
                FishPoruka.prikazi("Akcija nije uspela. Pokušaj ponovo.", dugo = true)
            } finally {
                _uToku.value = null
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun Map<String, Any?>.uPrijavu(): PrijavljenUlov {
        val u = this["ulov"] as? Map<String, Any?> ?: emptyMap()
        fun s(m: Map<String, Any?>, k: String) = m[k] as? String ?: ""
        fun d(m: Map<String, Any?>, k: String) = (m[k] as? Number)?.toDouble() ?: 0.0
        return PrijavljenUlov(
            id = s(this, "id"),
            status = s(this, "status"),
            brojPrijava = (this["brojPrijava"] as? Number)?.toInt() ?: 0,
            autorUlova = s(this, "autorUlova"),
            poslednjaPrijava = (this["poslednjaPrijava"] as? Number)?.toLong(),
            oduzetoPoena = (this["oduzetoPoena"] as? Number)?.toInt() ?: 0,
            resioAdmin = s(this, "resioAdmin"),
            vrstaRibe = s(u, "vrstaRibe"), naziv = s(u, "naziv"), opis = s(u, "opis"),
            duzinaRibe = d(u, "duzinaRibe"), tezinaRibe = d(u, "tezinaRibe"),
            mamac = s(u, "mamac"), tehnika = s(u, "tehnika"), slikaUrl = s(u, "slikaUrl"),
            razlozi = (this["razlozi"] as? List<Map<String, Any?>>).orEmpty().map {
                Razlog(s(it, "razlog"), s(it, "opis"), s(it, "prijavio"), (it["vreme"] as? Number)?.toLong())
            }
        )
    }
}
