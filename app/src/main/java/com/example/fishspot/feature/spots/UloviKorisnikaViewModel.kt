package com.example.fishspot.feature.spots

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Album vrsta: koliko proverenih ulova korisnik ima od jedne vrste i koliko je bila najveća riba
data class VrstaUAlbumu(val vrstaId: String, val naziv: String, val broj: Int, val najvecaKg: Double)

// Ulovi upisani pre izlaska verzije 1.1 (kad album nije postojao) računaju se u album i bez provere,
// da niko ne izgubi ono što je već upecao. PRE OBJAVE 1.1 postaviti na dan objave (1.12.2026. je okvirno).
const val ALBUM_PROVERA_OD = 1_796_079_600_000L // 1.12.2026. 00:00 po beogradskom vremenu

// Koliko je korisnik ulovio od jedne vrste ("Deverika" x 2)
data class BrojPoVrsti(val vrsta: String, val latinskiNaziv: String, val broj: Int)

/** Ulovi jednog korisnika (iz MestaZaPecanje, po authorId) i zbir po vrstama. */
class UloviKorisnikaViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _ulovi = MutableStateFlow<List<FishSpot>>(emptyList())
    val ulovi = _ulovi.asStateFlow()

    private val _poVrstama = MutableStateFlow<List<BrojPoVrsti>>(emptyList())
    val poVrstama = _poVrstama.asStateFlow()

    // Ulovi čija je slika prošla proveru i koji su dobili poene (oznaka "Potvrđen ulov" na kartici za deljenje)
    private val _potvrdjeni = MutableStateFlow<Set<String>>(emptySet())
    val potvrdjeni = _potvrdjeni.asStateFlow()

    // Provereni ulovi (sa poenima), isto kao za bedž Kolekcionar, i svi ulovi iz verzije 1.0
    private val _album = MutableStateFlow<List<VrstaUAlbumu>>(emptyList())
    val album = _album.asStateFlow()

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
                _potvrdjeni.value = snapshot?.documents.orEmpty().filter { d ->
                    d.getString("izvorSlike") in listOf("aplikacija", "kamera", "poslata") &&
                        (d.getLong("poeni") ?: 0L) > 0L && d.getString("proveraAdmina") != "ceka"
                }.map { it.id }.toSet()
                _album.value = snapshot?.documents.orEmpty()
                    .filter { d ->
                        val proveren = (d.getLong("poeni") ?: 0L) > 0L && d.getString("proveraAdmina") != "ceka"
                        val izVerzije10 = (d.getLong("timestamp") ?: Long.MAX_VALUE) < ALBUM_PROVERA_OD
                        proveren || izVerzije10
                    }
                    .mapNotNull { it.toObject(FishSpot::class.java)?.razdvojenLatinskiNaziv() }
                    .flatMap { u ->
                        // Čuvarka se broji kod svake vrste u njoj, ali nije jedna riba, pa ne ulazi u najveću
                        if (u.cuvarka) u.cuvarkaVrste.map { Triple(it.id, it.naziv, 0.0) }
                        else listOf(Triple(u.vrstaId, u.vrstaRibe, u.tezinaRibe))
                    }
                    .filter { it.first.isNotBlank() || it.second.isNotBlank() }
                    .groupBy { it.first.ifBlank { it.second } }
                    .map { (_, l) -> VrstaUAlbumu(l.first().first, l.first().second, l.size, l.maxOf { it.third }) }
                // Čuvarka se računa kod svake vrste koja je u njoj
                _poVrstama.value = lista
                    .flatMap { u ->
                        if (u.cuvarka && u.cuvarkaVrste.isNotEmpty()) u.cuvarkaVrste.map { it.naziv to it.latinskiNaziv }
                        else listOf(u.vrstaRibe.ifBlank { "Nepoznata vrsta" } to u.latinskiNaziv)
                    }
                    .groupBy { it.first }
                    .map { (vrsta, ulovi) -> BrojPoVrsti(vrsta, ulovi.first().second, ulovi.size) }
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
