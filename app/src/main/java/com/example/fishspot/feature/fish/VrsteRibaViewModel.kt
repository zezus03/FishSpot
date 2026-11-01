package com.example.fishspot.feature.fish

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.Collator
import java.util.Locale

class VrsteRibaViewModel : ViewModel() {

    private val _vrste = MutableStateFlow<List<VrstaRibe>>(emptyList())
    val vrste = _vrste.asStateFlow()

    init {
        // Lista se retko menja; Firestore je kešira pa radi i bez interneta posle prvog učitavanja
        FirebaseFirestore.getInstance().collection("VrsteRiba").get()
            .addOnSuccessListener { snapshot ->
                val collator = Collator.getInstance(Locale("sr", "RS"))
                _vrste.value = snapshot.documents
                    .mapNotNull { it.toObject(VrstaRibe::class.java)?.copy(id = it.id) }
                    // "Ostalo" uvek na kraju liste
                    .sortedWith(compareBy<VrstaRibe> { it.id == "ostalo" }.thenComparator { a, b -> collator.compare(a.naziv, b.naziv) })
            }
            .addOnFailureListener { Log.e("VrsteRiba", "Greška pri učitavanju vrsta", it) }
    }

    // Vrste koje odgovaraju unetom tekstu, po pravom, latinskom ili lokalnom nazivu ("karaš" -> Karaš i Babuška).
    // Prvo one čiji pravi naziv počinje tim tekstom ("dev" -> Deverika), pa lokalni nazivi.
    fun pretrazi(unos: String): List<VrstaRibe> {
        val trazeno = LovostajChecker.normalizujTekst(unos)
        if (trazeno.isEmpty()) return _vrste.value

        return _vrste.value
            .filter {
                LovostajChecker.normalizujTekst(it.naziv).contains(trazeno) ||
                    it.latinskiNaziv.lowercase().contains(trazeno) ||
                    LokalniNazivi.pogodak(it.id, unos) != null
            }
            .sortedBy { vrsta ->
                val naziv = LovostajChecker.normalizujTekst(vrsta.naziv)
                when {
                    naziv.startsWith(trazeno) -> 0
                    naziv.split(" ").any { it.startsWith(trazeno) } -> 1
                    LokalniNazivi.pogodak(vrsta.id, unos) != null -> 2
                    else -> 3
                }
            }
    }
}
