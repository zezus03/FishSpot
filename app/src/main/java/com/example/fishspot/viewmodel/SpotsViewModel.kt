
package com.example.fishspot.viewmodel

import android.location.Location
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishspot.data.FishSpot
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

data class FilterState(
    val autor: String = "",
    val vrstaRibe: String = "",
    val minimalnaOcena: Float = 0f,
    val startDate: Long? = null,
    val endDate: Long? = null,
    val searchText: String = "",
    val radiusKm: Float? = null
)

class SpotsViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _spots = MutableStateFlow<List<FishSpot>>(emptyList())
    private val _filterState = MutableStateFlow(FilterState())
    val filterState = _filterState.asStateFlow()
    private val _currentUserLocation = MutableStateFlow<Location?>(null)

    val filteredSpots: StateFlow<List<FishSpot>> =
        combine(_spots, _filterState, _currentUserLocation) { spots, filter, userLocation ->
            spots.filter { spot ->
                val autorFilterPassed = filter.autor.isBlank() || spot.authorUsername.contains(filter.autor, ignoreCase = true)
                val vrstaRibeFilterPassed = filter.vrstaRibe.isBlank() || spot.vrstaRibe.equals(filter.vrstaRibe, ignoreCase = true)
                val ocenaFilterPassed = spot.ocena >= filter.minimalnaOcena
                val startDateFilterPassed = filter.startDate == null || spot.timestamp >= filter.startDate
                val endDateFilterPassed = filter.endDate == null || spot.timestamp <= filter.endDate
                val searchTextFilterPassed = filter.searchText.isBlank() ||
                        spot.naziv.contains(filter.searchText, ignoreCase = true) ||
                        spot.opis.contains(filter.searchText, ignoreCase = true) ||
                        spot.vrstaRibe.contains(filter.searchText, ignoreCase = true)
                val radiusFilterPassed = when {
                    filter.radiusKm == null -> true
                    userLocation == null -> true
                    else -> {
                        val spotLocation = Location("").apply {
                            latitude = spot.lat
                            longitude = spot.lng
                        }
                        val distanceInMeters = userLocation.distanceTo(spotLocation)
                        distanceInMeters <= filter.radiusKm * 1000
                    }
                }
                autorFilterPassed && vrstaRibeFilterPassed && ocenaFilterPassed &&
                        startDateFilterPassed && endDateFilterPassed && searchTextFilterPassed && radiusFilterPassed
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    init {
        fetchSpots()
    }

    fun updateUserLocation(location: Location) {
        _currentUserLocation.value = location
    }

    fun applyFilters(
        autor: String, vrstaRibe: String, ocena: Float,
        startDate: Long?, endDate: Long?,
        searchText: String, radiusKm: Float?
    ) {
        _filterState.value = FilterState(
            autor = autor,
            vrstaRibe = vrstaRibe,
            minimalnaOcena = ocena,
            startDate = startDate,
            endDate = endDate,
            searchText = searchText,
            radiusKm = radiusKm
        )
    }

    private fun fetchSpots() {
        firestore.collection("MestaZaPecanje")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { return@addSnapshotListener }
                if (snapshot != null) {
                    val spotsList = snapshot.documents.mapNotNull { doc ->
                        val spot = doc.toObject(FishSpot::class.java)
                        spot?.copy(id = doc.id)
                    }
                    _spots.value = spotsList
                }
            }
    }

    fun addSpot(
        naziv: String, vrstaRibe: String, ocena: Float, opis: String, slikaUri: Uri?,
        location: android.location.Location,
        duzinaRibe: Double, tezinaRibe: Double, mamac: String, tehnika: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                onResult(Result.failure(Exception("Korisnik nije ulogovan.")))
                return@launch
            }
            try {
                val userDoc = firestore.collection("Korisnici").document(user.uid).get().await()
                val username = userDoc.getString("userName") ?: "Nepoznat autor"
                var slikaUrl: String? = null
                if (slikaUri != null) {
                    val ref = storage.reference.child("spot_slike/${UUID.randomUUID()}.jpg")
                    ref.putFile(slikaUri).await()
                    slikaUrl = ref.downloadUrl.await().toString()
                }
                val fishSpot = FishSpot(
                    authorId = user.uid,
                    authorUsername = username,
                    naziv = naziv,
                    vrstaRibe = vrstaRibe,
                    ocena = ocena,
                    opis = opis,
                    slikaUrl = slikaUrl,
                    lat = location.latitude,
                    lng = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    duzinaRibe = duzinaRibe,
                    tezinaRibe = tezinaRibe,
                    mamac = mamac,
                    tehnika = tehnika
                )

                firestore.collection("MestaZaPecanje").add(fishSpot).await()

                val poeniZaOvajUlov = when {
                    tezinaRibe > 5.0 -> 50L
                    tezinaRibe > 2.0 -> 20L
                    tezinaRibe > 1.0 -> 10L
                    else -> 0L
                }

                if (poeniZaOvajUlov > 0) {
                    val userRef = firestore.collection("Korisnici").document(user.uid)
                    userRef.update("poeni", FieldValue.increment(poeniZaOvajUlov)).await()
                }

                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
}