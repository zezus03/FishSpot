
package com.example.fishspot.viewmodel

import android.location.Location
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishspot.data.VodenoTelo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*

class WatersViewModel : ViewModel() {

    private val firestore = FirebaseFirestore.getInstance()

    private val _waters = MutableStateFlow<List<VodenoTelo>>(emptyList())
    private val _filterState = MutableStateFlow(FilterState())
    private val _currentUserLocation = MutableStateFlow<Location?>(null)

    val filteredWaters: StateFlow<List<VodenoTelo>> =
        combine(_waters, _filterState, _currentUserLocation) { waters, filter, userLocation ->
            waters.filter { water ->
                val searchTextFilterPassed = filter.searchText.isBlank() ||
                        water.naziv.contains(filter.searchText, ignoreCase = true) ||
                        water.tip.contains(filter.searchText, ignoreCase = true)

                val radiusFilterPassed = when {
                    filter.radiusKm == null -> true
                    userLocation == null -> true
                    else -> {
                        val waterLocation = Location("").apply {
                            latitude = water.lat
                            longitude = water.lng
                        }
                        val distanceInMeters = userLocation.distanceTo(waterLocation)
                        distanceInMeters <= filter.radiusKm * 1000
                    }
                }

                searchTextFilterPassed && radiusFilterPassed
            }
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())


    init {
        fetchWaters()
    }

    fun applyFilters(searchText: String, radiusKm: Float?) {
        _filterState.value = _filterState.value.copy(
            searchText = searchText,
            radiusKm = radiusKm
        )
    }

    fun updateUserLocation(location: Location) {
        _currentUserLocation.value = location
    }

    private fun fetchWaters() {
        firestore.collection("VodenaTela")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { return@addSnapshotListener }
                if (snapshot != null) {
                    val waterList = snapshot.documents.mapNotNull { doc ->
                        val water = doc.toObject(VodenoTelo::class.java)
                        water?.copy(id = doc.id)
                    }
                    _waters.value = waterList
                }
            }
    }
}