package com.example.fishspot.feature.spots

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Koliko je korisnik ulovio od jedne vrste ("Deverika" x 2)
data class BrojPoVrsti(val vrsta: String, val latinskiNaziv: String, val broj: Int)

/** Ulovi jednog korisnika (iz MestaZaPecanje, po authorId) i zbir po vrstama. */
class UloviKorisnikaViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _ulovi = MutableStateFlow<List<FishSpot>>(emptyList())
    val ulovi = _ulovi.asStateFlow()

    private val _poVrstama = MutableStateFlow<List<BrojPoVrsti>>(emptyList())
    val poVrstama = _poVrstama.asStateFlow()

    private val _imeKorisnika = MutableStateFlow<String?>(null)
    val imeKorisnika = _imeKorisnika.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private var slusalac: ListenerRegistration? = null
    private var ucitanKorisnik: String? = null

    fun ucitaj(uid: String) {
        if (ucitanKorisnik == uid) return
        ucitanKorisnik = uid
        slusalac?.remove()

        firestore.collection("Korisnici").document(uid).get()
            .addOnSuccessListener { doc -> _imeKorisnika.value = doc.getString("userName") }

        // Sortiramo u aplikaciji, da za upit ne treba poseban indeks (authorId + timestamp)
        slusalac = firestore.collection("MestaZaPecanje").whereEqualTo("authorId", uid)
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("UloviKorisnika", "Greška pri učitavanju ulova", error)
                    return@addSnapshotListener
                }
                val lista = snapshot?.documents.orEmpty()
                    .mapNotNull { it.toObject(FishSpot::class.java)?.copy(id = it.id)?.razdvojenLatinskiNaziv() }
                    .sortedByDescending { it.timestamp }
                _ulovi.value = lista
                _poVrstama.value = lista
                    .groupBy { it.vrstaRibe.ifBlank { "Nepoznata vrsta" } }
                    .map { (vrsta, ulovi) -> BrojPoVrsti(vrsta, ulovi.first().latinskiNaziv, ulovi.size) }
                    .sortedWith(compareByDescending<BrojPoVrsti> { it.broj }.thenBy { it.vrsta })
            }
    }

    override fun onCleared() {
        slusalac?.remove()
    }

    // Stariji ulovi (pre izbora vrste sa liste) imaju "Deverika (Abramis brama)" u jednom polju;
    // razdvajamo ih da bi se brojali zajedno sa ulovima "Deverika"
    private fun FishSpot.razdvojenLatinskiNaziv(): FishSpot {
        if (latinskiNaziv.isNotBlank()) return this
        val delovi = Regex("""^\s*(.+?)\s*\((.+)\)\s*$""").find(vrstaRibe)?.groupValues ?: return this
        return copy(vrstaRibe = delovi[1], latinskiNaziv = delovi[2])
    }
}
