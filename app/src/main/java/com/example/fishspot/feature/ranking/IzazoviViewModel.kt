package com.example.fishspot.feature.ranking

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.functions.FirebaseFunctions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

// Izazov iz baze (pravi ga admin; vidi functions/izazovi.js)
data class Izazov(
    val id: String,
    val naziv: String,
    val opis: String,
    val nagrada: String,
    val od: Long,
    val doKraja: Long,
    val merilo: String, // najteza | ukupno | broj
    val vrste: List<String>,
    val samoKamera: Boolean,
    val brojUcesnika: Long,
    val slikaUrl: String = "" // slika izazova (opciono, postavlja admin)
) {
    fun aktivan(sada: Long = System.currentTimeMillis()) = sada in od..doKraja
    fun predstojeci(sada: Long = System.currentTimeMillis()) = sada < od
}

data class UcesnikIzazova(
    val uid: String,
    val ime: String,
    val slika: String,
    val rezultat: Double,
    val brojUlova: Long,
    val ukupnaTezina: Double,
    val najboljiSlika: String,
    val najboljiVrsta: String,
    val ulovi: List<UlovUIzazovu>
)

// Ulov sa kojim se učesnik takmiči (server ga upisuje u Izazovi/{id}/ucesnici/{uid}.ulovi)
data class UlovUIzazovu(
    val id: String,
    val slikaUrl: String,
    val vrsta: String,
    val tezina: Double,
    val vreme: Long,
    val cuvarka: Boolean
)

class IzazoviViewModel : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    val mojUid: String? = FirebaseAuth.getInstance().currentUser?.uid

    private val _izazovi = MutableStateFlow<List<Izazov>>(emptyList())
    val izazovi = _izazovi.asStateFlow()

    private val _ucesnici = MutableStateFlow<List<UcesnikIzazova>>(emptyList())
    val ucesnici = _ucesnici.asStateFlow()

    private val _prijavljujem = MutableStateFlow(false)
    val prijavljujem = _prijavljujem.asStateFlow()

    private var izazoviSlusalac: ListenerRegistration? = null
    private var ucesniciSlusalac: ListenerRegistration? = null

    init {
        izazoviSlusalac = db.collection("Izazovi").orderBy("do", Query.Direction.DESCENDING).limit(50)
            .addSnapshotListener { s, e ->
                if (e != null) { Log.e("Izazovi", "Učitavanje izazova", e); return@addSnapshotListener }
                _izazovi.value = s?.documents.orEmpty().map { d ->
                    @Suppress("UNCHECKED_CAST")
                    val vrste = (d.get("vrste") as? List<String>).orEmpty()
                    Izazov(
                        id = d.id,
                        naziv = d.getString("naziv").orEmpty(),
                        opis = d.getString("opis").orEmpty(),
                        nagrada = d.getString("nagrada").orEmpty(),
                        od = d.getLong("od") ?: 0L,
                        doKraja = d.getLong("do") ?: 0L,
                        merilo = d.getString("merilo") ?: "najteza",
                        vrste = vrste,
                        samoKamera = d.getBoolean("samoKamera") ?: false,
                        brojUcesnika = d.getLong("brojUcesnika") ?: 0L,
                        slikaUrl = d.getString("slikaUrl").orEmpty()
                    )
                }
            }
    }

    // Rang lista izabranog izazova (računa je server)
    fun otvori(izazovId: String) {
        ucesniciSlusalac?.remove()
        _ucesnici.value = emptyList()
        ucesniciSlusalac = db.collection("Izazovi").document(izazovId).collection("ucesnici")
            .orderBy("rezultat", Query.Direction.DESCENDING).limit(100)
            .addSnapshotListener { s, e ->
                if (e != null) { Log.e("Izazovi", "Učitavanje ranga", e); return@addSnapshotListener }
                _ucesnici.value = s?.documents.orEmpty().map { d ->
                    @Suppress("UNCHECKED_CAST")
                    val najbolji = d.get("najbolji") as? Map<String, Any?>
                    @Suppress("UNCHECKED_CAST")
                    val ulovi = (d.get("ulovi") as? List<Map<String, Any?>>).orEmpty().map { u ->
                        UlovUIzazovu(
                            id = u["id"] as? String ?: "",
                            slikaUrl = u["slikaUrl"] as? String ?: "",
                            vrsta = u["vrsta"] as? String ?: "",
                            tezina = (u["tezina"] as? Number)?.toDouble() ?: 0.0,
                            vreme = (u["vreme"] as? Number)?.toLong() ?: 0L,
                            cuvarka = u["cuvarka"] as? Boolean ?: false
                        )
                    }
                    UcesnikIzazova(
                        uid = d.id,
                        ime = d.getString("ime") ?: "Pecaroš",
                        slika = d.getString("slika").orEmpty(),
                        rezultat = d.getDouble("rezultat") ?: 0.0,
                        brojUlova = d.getLong("brojUlova") ?: 0L,
                        // Stariji zapisi nemaju ukupnu težinu: zbir iz spiska ulova
                        ukupnaTezina = d.getDouble("ukupnaTezina") ?: ulovi.sumOf { it.tezina },
                        najboljiSlika = najbolji?.get("slikaUrl") as? String ?: "",
                        najboljiVrsta = najbolji?.get("vrsta") as? String ?: "",
                        ulovi = ulovi
                    )
                    // Isti rezultat: prednost ima veća ukupna težina, pa veći broj ulova
                }.sortedWith(compareByDescending<UcesnikIzazova> { it.rezultat }
                    .thenByDescending { it.ukupnaTezina }.thenByDescending { it.brojUlova })
            }
    }

    fun pridruziSe(izazovId: String, gotovo: (String?) -> Unit) {
        if (!com.example.fishspot.feature.auth.PotvrdaNaloga.proveri()) return
        _prijavljujem.value = true
        FirebaseFunctions.getInstance().getHttpsCallable("pridruziSeIzazovu").call(mapOf("izazovId" to izazovId))
            .addOnSuccessListener { _prijavljujem.value = false; gotovo(null) }
            .addOnFailureListener {
                _prijavljujem.value = false
                gotovo(it.message ?: "Prijava nije uspela")
            }
    }

    override fun onCleared() {
        izazoviSlusalac?.remove()
        ucesniciSlusalac?.remove()
    }
}
