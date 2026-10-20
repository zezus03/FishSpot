
package com.example.fishspot.feature.spots

import android.location.Location
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope

import com.example.fishspot.core.model.Korisnici
import com.example.fishspot.feature.fish.VrstaRibe
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

    private val komentari = MutableStateFlow<List<Comment>>(emptyList())
    val comments = komentari.asStateFlow()

    val filteredSpots: StateFlow<List<FishSpot>> =
        combine(_spots, _filterState, _currentUserLocation) { spots, filter, userLocation ->
            spots.filter { spot ->
                val autorFilterPassed = filter.autor.isBlank() || spot.authorUsername.contains(filter.autor, ignoreCase = true)
                // Deo naziva je dovoljan ("dever" nalazi Deverika)
                val vrstaRibeFilterPassed = filter.vrstaRibe.isBlank() || spot.vrstaRibe.contains(filter.vrstaRibe.trim(), ignoreCase = true)
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
        context: android.content.Context, // Dodat Context!
        naziv: String, vrsta: VrstaRibe, ocena: Float, opis: String, slikaUri: Uri?,
        location: android.location.Location,
        duzinaRibe: Double, tezinaRibe: Double, mamac: String, tehnika: String,temperatura: String,
        pritisak: String,
        vetar: String,
        onResult: (Result<Unit>) -> Unit
    ) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                onResult(Result.failure(Exception("Korisnik nije ulogovan.")))
                return@launch
            }
            try {
                // Generišemo ID dokumenta unapred (radi i bez neta)
                val docRef = firestore.collection("MestaZaPecanje").document()
                val spotId = docRef.id

                val userDoc = firestore.collection("Korisnici").document(user.uid).get().await()
                val username = userDoc.getString("userName") ?: "Nepoznat autor"

                val fishSpot = FishSpot(
                    id = spotId,
                    authorId = user.uid,
                    authorUsername = username,
                    naziv = naziv,
                    vrstaRibe = vrsta.naziv,
                    vrstaId = vrsta.id,
                    latinskiNaziv = vrsta.latinskiNaziv,
                    ocena = ocena,
                    opis = opis,
                    slikaUrl = null, // Šalje se naknadno
                    lat = location.latitude,
                    lng = location.longitude,
                    timestamp = System.currentTimeMillis(),
                    duzinaRibe = duzinaRibe,
                    tezinaRibe = tezinaRibe,
                    mamac = mamac,
                    tehnika = tehnika,
                    temperatura = temperatura,
                    pritisak = pritisak,
                    vetar = vetar

                )

                // Ulov se čuva i pod svojom vrstom (VrsteRiba/{vrsta}/Ulovi) sa istim ID-jem,
                // u jednom paketu sa glavnim upisom (Firestore rešava offline keširanje)
                val ulovPoVrsti = mapOf(
                    "authorId" to user.uid,
                    "authorUsername" to username,
                    "duzinaRibe" to duzinaRibe,
                    "tezinaRibe" to tezinaRibe,
                    "mamac" to mamac,
                    "tehnika" to tehnika,
                    "timestamp" to fishSpot.timestamp
                )
                firestore.batch()
                    .set(docRef, fishSpot)
                    .set(
                        firestore.collection("VrsteRiba").document(vrsta.id)
                            .collection("Ulovi").document(spotId),
                        ulovPoVrsti
                    )
                    .commit()
                    .addOnFailureListener { Log.e("SpotsViewModel", "Greška pri upisu ulova", it) }

                // Ako ima slike, predajemo je WorkManager-u
                if (slikaUri != null) {
                    try {
                        val inputStream = context.contentResolver.openInputStream(slikaUri)
                        val localFile = java.io.File(context.filesDir, "upload_${spotId}.jpg")
                        val outputStream = java.io.FileOutputStream(localFile)
                        inputStream?.copyTo(outputStream)
                        inputStream?.close()
                        outputStream.close()

                        val inputData = androidx.work.Data.Builder()
                            .putString("FILE_PATH", localFile.absolutePath)
                            .putString("DOC_ID", spotId)
                            .build()

                        // Radnik se budi samo kad ima interneta
                        val constraints = androidx.work.Constraints.Builder()
                            .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED)
                            .build()

                        val uploadWorkRequest = androidx.work.OneTimeWorkRequestBuilder<UploadWorker>()
                            .setConstraints(constraints)
                            .setInputData(inputData)
                            .build()

                        androidx.work.WorkManager.getInstance(context).enqueue(uploadWorkRequest)

                    } catch (e: Exception) {
                        android.util.Log.e("SpotsViewModel", "Greška pri pripremi slike", e)
                    }
                }

                // Računanje poena
                val poeniZaOvajUlov = when {
                    tezinaRibe > 5.0 -> 50L
                    tezinaRibe > 2.0 -> 20L
                    tezinaRibe > 1.0 -> 10L
                    tezinaRibe > 0.5 -> 5L
                    else -> 0L
                }

                if (poeniZaOvajUlov > 0) {
                    val userRef = firestore.collection("Korisnici").document(user.uid)
                    userRef.update("poeni", com.google.firebase.firestore.FieldValue.increment(poeniZaOvajUlov))
                }

                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
    fun loadComments(spotId: String) {
        viewModelScope.launch {
            try {
                firestore.collection("MestaZaPecanje").document(spotId)
                    .collection("komentari")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.w("SpotsViewModel", "Greška pri slušanju komentara:", error)
                            komentari.value = emptyList()
                            return@addSnapshotListener
                        }
                        if (snapshot != null) {
                            val commentsList = snapshot.documents.mapNotNull { doc ->
                                doc.toObject(Comment::class.java)?.copy(id = doc.id)
                            }
                            komentari.value = commentsList
                        } else {
                            komentari.value = emptyList()
                        }
                    }
            } catch (e: Exception) {
                Log.e("SpotsViewModel", "Neuspešno učitavanje komentara", e)
                komentari.value = emptyList()
            }
        }
    }

    fun DodajKomentar(spotId: String, text: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val user = auth.currentUser
            if (user == null) {
                onResult(false)
                return@launch
            }
            try {
                val userDoc = firestore.collection("Korisnici").document(user.uid).get().await()
                val username = userDoc.getString("userName") ?: "Nepoznat autor"

                val newComment = Comment(
                    authorId = user.uid,
                    authorUsername = username,
                    text = text,
                    timestamp = System.currentTimeMillis()
                )

                firestore.collection("MestaZaPecanje").document(spotId)
                    .collection("komentari")
                    .add(newComment).await()

                onResult(true)
            } catch (e: Exception) {
                Log.e("SpotsViewModel", "Greška pri dodavanju komentara", e)
                onResult(false)
            }
        }
    }

    fun clearComments() {
        komentari.value = emptyList()
    }

    // ─── DETALJI ULOVA: autor i prijava ───

    private val _autorUlova = MutableStateFlow<Korisnici?>(null)
    val autorUlova = _autorUlova.asStateFlow()

    // Da li je trenutni korisnik već prijavio izabrani ulov
    private val _ulovPrijavljen = MutableStateFlow(false)
    val ulovPrijavljen = _ulovPrijavljen.asStateFlow()

    fun ucitajDetaljeUlova(spot: FishSpot) {
        _autorUlova.value = null
        _ulovPrijavljen.value = false

        firestore.collection("Korisnici").document(spot.authorId).get()
            .addOnSuccessListener { doc ->
                _autorUlova.value = doc.toObject(Korisnici::class.java)?.copy(id = doc.id)
            }

        val uid = auth.currentUser?.uid ?: return
        firestore.collection("Prijave").document("${spot.id}_$uid").get()
            .addOnSuccessListener { _ulovPrijavljen.value = it.exists() }
    }

    fun prijaviUlov(spot: FishSpot, razlog: String, opis: String, onResult: (Boolean) -> Unit) {
        val uid = auth.currentUser?.uid ?: return onResult(false)
        val prijava = mapOf(
            "spotId" to spot.id,
            "autorUlovaId" to spot.authorId,
            "prijavioId" to uid,
            "razlog" to razlog,
            "opis" to opis.trim().take(500),
            "timestamp" to System.currentTimeMillis()
        )
        firestore.collection("Prijave").document("${spot.id}_$uid").set(prijava)
            .addOnSuccessListener {
                _ulovPrijavljen.value = true
                onResult(true)
            }
            .addOnFailureListener {
                Log.e("SpotsViewModel", "Greška pri prijavi ulova", it)
                onResult(false)
            }
    }
    // Autor menja svoj ulov; poene i spisak po vrsti usklađuje Cloud Function izmenjenUlov
    fun izmeniUlov(spot: FishSpot, izmena: IzmenaUlova, onResult: (Boolean) -> Unit) {
        val izmene = mapOf(
            "naziv" to izmena.naziv,
            "vrstaRibe" to izmena.vrsta.naziv,
            "vrstaId" to izmena.vrsta.id,
            "latinskiNaziv" to izmena.vrsta.latinskiNaziv,
            "duzinaRibe" to izmena.duzinaRibe,
            "tezinaRibe" to izmena.tezinaRibe,
            "mamac" to izmena.mamac,
            "tehnika" to izmena.tehnika,
            "ocena" to izmena.ocena,
            "opis" to izmena.opis
        )
        firestore.collection("MestaZaPecanje").document(spot.id).update(izmene)
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                Log.e("SpotsViewModel", "Greška pri izmeni ulova", it)
                onResult(false)
            }
    }

    // Autor briše svoj ulov; komentare, sliku i poene sređuje Cloud Function obrisanUlov
    fun obrisiUlov(spot: FishSpot, onResult: (Boolean) -> Unit) {
        firestore.collection("MestaZaPecanje").document(spot.id).delete()
            .addOnSuccessListener { onResult(true) }
            .addOnFailureListener {
                Log.e("SpotsViewModel", "Greška pri brisanju ulova", it)
                onResult(false)
            }
    }

    fun sacuvajFcmToken() {
        val user = auth.currentUser ?: return
        com.google.firebase.messaging.FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.w("SpotsViewModel", "Fetching FCM registration token failed", task.exception)
                return@addOnCompleteListener
            }
            // Dobili smo token uređaja
            val token = task.result

            // Zapisujemo ga u Firestore u dokument trenutnog korisnika
            firestore.collection("Korisnici").document(user.uid)
                .update("fcmToken", token)
                .addOnFailureListener {
                    Log.e("SpotsViewModel", "Greška pri čuvanju tokena", it)
                }
        }
    }
    fun toggleLike(spotId: String, isLiked: Boolean) {
        val user = auth.currentUser ?: return
        val spotRef = firestore.collection("MestaZaPecanje").document(spotId)

        if (isLiked) {
            // Ako je lajkovano, dodajemo ID korisnika u niz
            spotRef.update("likedBy", FieldValue.arrayUnion(user.uid))
        } else {
            // Ako je "odlajkovano", brišemo ID korisnika iz niza
            spotRef.update("likedBy", FieldValue.arrayRemove(user.uid))
        }
    }
}