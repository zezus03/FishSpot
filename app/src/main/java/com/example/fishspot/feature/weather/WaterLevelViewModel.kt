package com.example.fishspot.feature.weather

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// Jedna hidrološka stanica iz Vodostaji/sve (upisuje je Cloud Function osveziVodostaje)
data class StanicaVodostaja(
    val id: String,
    val reka: String,
    val stanica: String,
    val vodostaj: Int?,     // cm
    val promena: Int?,      // cm u odnosu na prethodni dan
    val proticaj: Double?,  // m³/s
    val temperatura: Double?, // °C
    val tendencija: String  // "raste", "opada", "miruje" ili "nepoznato"
)

// Merenje na sat za jednu stanicu (funkcija detaljiStanice), t = "20.10.2026 15:00"
data class MerenjeVodostaja(val t: String, val v: Int)

/**
 * Vodostaji se ne preuzimaju sa RHMZ sajta direktno: server ih preuzima jednom na sat
 * i upisuje u Firestore, a aplikacija samo čita (radi i bez interneta, iz keša).
 */
class WaterLevelViewModel : ViewModel() {

    private val _stanice = MutableStateFlow<List<StanicaVodostaja>>(emptyList())
    val stanice = _stanice.asStateFlow()

    // Vreme merenja RHMZ-a, npr. "20.10.2026. u 8:00"
    private val _merenje = MutableStateFlow<String?>(null)
    val merenje = _merenje.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading = _isLoading.asStateFlow()

    private val _greska = MutableStateFlow<String?>(null)
    val greska = _greska.asStateFlow()

    // Detalji izabrane stanice (merenja na sat za 7 dana)
    private val _merenja = MutableStateFlow<List<MerenjeVodostaja>>(emptyList())
    val merenja = _merenja.asStateFlow()

    private val _isDetaljiLoading = MutableStateFlow(false)
    val isDetaljiLoading = _isDetaljiLoading.asStateFlow()

    private val _detaljiGreska = MutableStateFlow<String?>(null)
    val detaljiGreska = _detaljiGreska.asStateFlow()

    private var slusalac: ListenerRegistration? = null
    private var izabranaStanica: String? = null

    init {
        slusalac = FirebaseFirestore.getInstance().collection("Vodostaji").document("sve")
            .addSnapshotListener { snapshot, error ->
                _isLoading.value = false
                if (error != null) {
                    Log.e("Vodostaji", "Greška pri čitanju vodostaja", error)
                    _greska.value = "Vodostaji trenutno nisu dostupni."
                    return@addSnapshotListener
                }
                if (snapshot == null || !snapshot.exists()) {
                    _greska.value = "Podaci o vodostaju se pripremaju, pokušaj ponovo za koji minut."
                    return@addSnapshotListener
                }
                _greska.value = null
                _merenje.value = snapshot.getString("merenje")
                @Suppress("UNCHECKED_CAST")
                val lista = snapshot.get("stanice") as? List<Map<String, Any?>> ?: emptyList()
                _stanice.value = lista.mapNotNull { it.uStanicu() }
            }
    }

    fun ucitajDetalje(hmId: String) {
        izabranaStanica = hmId
        _merenja.value = emptyList()
        _detaljiGreska.value = null
        _isDetaljiLoading.value = true

        viewModelScope.launch {
            try {
                val rezultat = Firebase.functions.getHttpsCallable("detaljiStanice")
                    .call(mapOf("hmId" to hmId)).await()
                @Suppress("UNCHECKED_CAST")
                val lista = (rezultat.data as? Map<String, Any?>)?.get("merenja") as? List<Map<String, Any?>>
                // Ako je korisnik u međuvremenu otvorio drugu stanicu, ovaj rezultat ne prikazujemo
                if (izabranaStanica == hmId) {
                    _merenja.value = lista.orEmpty().mapNotNull { m ->
                        val t = m["t"] as? String ?: return@mapNotNull null
                        val v = (m["v"] as? Number)?.toInt() ?: return@mapNotNull null
                        MerenjeVodostaja(t, v)
                    }
                }
            } catch (e: Exception) {
                Log.e("Vodostaji", "Greška pri učitavanju merenja stanice $hmId", e)
                if (izabranaStanica == hmId) _detaljiGreska.value = "Merenja za ovu stanicu trenutno nisu dostupna."
            } finally {
                if (izabranaStanica == hmId) _isDetaljiLoading.value = false
            }
        }
    }

    fun zatvoriDetalje() {
        izabranaStanica = null
        _merenja.value = emptyList()
        _detaljiGreska.value = null
        _isDetaljiLoading.value = false
    }

    override fun onCleared() {
        slusalac?.remove()
    }

    private fun Map<String, Any?>.uStanicu(): StanicaVodostaja? {
        val id = this["id"] as? String ?: return null
        return StanicaVodostaja(
            id = id,
            reka = this["reka"] as? String ?: "",
            stanica = this["stanica"] as? String ?: "",
            vodostaj = (this["vodostaj"] as? Number)?.toInt(),
            promena = (this["promena"] as? Number)?.toInt(),
            proticaj = (this["proticaj"] as? Number)?.toDouble(),
            temperatura = (this["temperatura"] as? Number)?.toDouble(),
            tendencija = this["tendencija"] as? String ?: "nepoznato"
        )
    }
}
