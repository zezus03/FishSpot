
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

// Najveća strana i kvalitet slika ulova koje se šalju na server: glavna slika je veća
// (na njoj su provere i prikaz na mapi), dodatne (ekipa, štap, varalica) su manje zbog prostora
private const val MAKS_STRANA_SLIKE = 1600
private const val KVALITET_SLIKE = 82
private const val MAKS_STRANA_DODATNE = 1080
private const val KVALITET_DODATNE = 75

class SpotsViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _spots = MutableStateFlow<List<FishSpot>>(emptyList())
    // Ulovi bez onih od blokiranih korisnika (vidi Blokiranje)
    private val vidljiviUlovi: StateFlow<List<FishSpot>> =
        combine(_spots, com.example.fishspot.feature.chat.Blokiranje.uidovi) { spots, blokirani ->
            if (blokirani.isEmpty()) spots else spots.filter { it.authorId !in blokirani }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private val _filterState = zajednickiFilter
    val filterState = _filterState.asStateFlow()

    // Svi ulovi bez filtera (mapa); filter važi samo za listu ulova
    val sviUlovi: StateFlow<List<FishSpot>> get() = vidljiviUlovi

    companion object {
        // Filter liste ulova se pamti dok je aplikacija otvorena
        private val zajednickiFilter = MutableStateFlow(FilterState())
    }

    fun ponistiFilter() {
        _filterState.value = FilterState()
    }
    private val _currentUserLocation = MutableStateFlow<Location?>(null)

    private val komentari = MutableStateFlow<List<Comment>>(emptyList())
    val comments: StateFlow<List<Comment>> =
        combine(komentari, com.example.fishspot.feature.chat.Blokiranje.uidovi) { lista, blokirani ->
            if (blokirani.isEmpty()) lista else lista.filter { it.authorId !in blokirani }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val filteredSpots: StateFlow<List<FishSpot>> =
        combine(vidljiviUlovi, _filterState, _currentUserLocation) { spots, filter, userLocation ->
            spots.filter { spot ->
                val autorFilterPassed = filter.autor.isBlank() || spot.authorUsername.contains(filter.autor, ignoreCase = true)
                // Deo naziva je dovoljan ("dever" nalazi Deverika)
                val vrstaRibeFilterPassed = filter.vrstaRibe.isBlank() || spot.nazivVrsta.contains(filter.vrstaRibe.trim(), ignoreCase = true)
                val ocenaFilterPassed = spot.ocena >= filter.minimalnaOcena
                val startDateFilterPassed = filter.startDate == null || spot.timestamp >= filter.startDate
                val endDateFilterPassed = filter.endDate == null || spot.timestamp <= filter.endDate
                val searchTextFilterPassed = filter.searchText.isBlank() ||
                        spot.naziv.contains(filter.searchText, ignoreCase = true) ||
                        spot.opis.contains(filter.searchText, ignoreCase = true) ||
                        spot.nazivVrsta.contains(filter.searchText, ignoreCase = true)
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
            // Gornja granica čitanja pri svakom otvaranju aplikacije (najnoviji ulovi)
            .limit(1000)
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
        naziv: String, vrsta: VrstaRibe, ocena: Float, opis: String,
        glavnaSlika: Uri?, dodatneSlike: List<Uri>,
        location: android.location.Location,
        duzinaRibe: Double, tezinaRibe: Double, mamac: String, tehnika: String,temperatura: String,
        pritisak: String,
        vetar: String,
        izvorSlike: String,
        cuvarka: Boolean = false,
        cuvarkaVrste: List<VrstaRibe> = emptyList(),
        sakrijLokaciju: Boolean = false,
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
                    // Sakrivena lokacija: javno samo približno mesto, tačno ide u privatni deo
                    lat = if (sakrijLokaciju) SkrivenaLokacija.priblizno(location.latitude, location.longitude).latitude else location.latitude,
                    lng = if (sakrijLokaciju) SkrivenaLokacija.priblizno(location.latitude, location.longitude).longitude else location.longitude,
                    skrivenaLokacija = sakrijLokaciju,
                    timestamp = System.currentTimeMillis(),
                    duzinaRibe = duzinaRibe,
                    tezinaRibe = tezinaRibe,
                    mamac = mamac,
                    tehnika = tehnika,
                    temperatura = temperatura,
                    pritisak = pritisak,
                    vetar = vetar,
                    izvorSlike = izvorSlike,
                    cuvarka = cuvarka,
                    cuvarkaVrste = cuvarkaVrste.map { VrstaKratko(it.id, it.naziv, it.latinskiNaziv) }
                )

                // Zapis pod VrsteRiba/{vrsta}/Ulovi (rekordi) pravi server (funkcija noviUlov)
                if (sakrijLokaciju) {
                    // Ulov i tačna lokacija zajedno (radi i bez interneta)
                    firestore.batch()
                        .set(docRef, fishSpot)
                        .set(SkrivenaLokacija.privatnoRef(spotId), mapOf("lat" to location.latitude, "lng" to location.longitude))
                        .commit()
                        .addOnFailureListener { Log.e("SpotsViewModel", "Greška pri upisu ulova", it) }
                    mojeTacneLokacije.value = mojeTacneLokacije.value +
                        (spotId to com.google.android.gms.maps.model.LatLng(location.latitude, location.longitude))
                } else {
                    docRef.set(fishSpot)
                        .addOnFailureListener { Log.e("SpotsViewModel", "Greška pri upisu ulova", it) }
                }

                // Slike se smanjuju pre slanja (umesto 3-8 MB sa kamere šalje se oko 300 KB za glavnu
                // i oko 120 KB za dodatnu), pa se predaju WorkManager-u koji ih šalje kad ima interneta
                if (glavnaSlika != null) {
                    try {
                        val glavniFajl = java.io.File(context.filesDir, "upload_${spotId}.jpg")
                        val dodatniFajlovi = dodatneSlike.take(MAKS_SLIKA_ULOVA - 1).mapIndexed { i, _ ->
                            java.io.File(context.filesDir, "upload_${spotId}_d${i + 1}.jpg")
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            sacuvajSmanjeno(context, glavnaSlika, glavniFajl, MAKS_STRANA_SLIKE, KVALITET_SLIKE)
                            dodatniFajlovi.forEachIndexed { i, fajl ->
                                sacuvajSmanjeno(context, dodatneSlike[i], fajl, MAKS_STRANA_DODATNE, KVALITET_DODATNE)
                            }
                        }

                        val inputData = androidx.work.Data.Builder()
                            .putString("FILE_PATH", glavniFajl.absolutePath)
                            .putStringArray("DODATNE", dodatniFajlovi.map { it.absolutePath }.toTypedArray())
                            .putString("DOC_ID", spotId)
                            .putString("IME", UUID.randomUUID().toString()) // isto ime i kad se slanje ponovi
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

                // Poene dodeljuje server (proveriSlikuUlova) kad stigne slika: proveri izvor i da li je
                // ista slika već objavljena
                onResult(Result.success(Unit))
            } catch (e: Exception) {
                onResult(Result.failure(e))
            }
        }
    }
    // Pre objave: da li je ista slika već objavljena u FishSpot-u (server poredi otisak sa svim ulovima).
    // Bez interneta ili ako provera ne uspe vraća false, a server proverava ponovo kad slika stigne.
    suspend fun slikaVecObjavljena(context: android.content.Context, uri: Uri): Boolean {
        val otisak = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            OtisakSlike.izracunaj(context, uri)
        } ?: return false
        return try {
            val odgovor = kotlinx.coroutines.withTimeout(10_000) {
                com.google.firebase.functions.FirebaseFunctions.getInstance()
                    .getHttpsCallable("proveriOtisakSlike").call(mapOf("otisak" to otisak)).await()
            }
            (odgovor.data as? Map<*, *>)?.get("postoji") == true
        } catch (e: Exception) {
            Log.w("SpotsViewModel", "Provera iste slike nije uspela", e)
            false
        }
    }

    // Učita sliku uspravno, smanji je na najveću stranu i sačuva kao JPEG
    private fun sacuvajSmanjeno(context: android.content.Context, uri: Uri, fajl: java.io.File, maksStrana: Int, kvalitet: Int) {
        val slika = com.example.fishspot.feature.fish.ucitajSlikuUspravno(context, uri, maksStrana)
        val razmera = maksStrana.toFloat() / maxOf(slika.width, slika.height)
        val smanjena = if (razmera < 1f) {
            android.graphics.Bitmap.createScaledBitmap(slika, (slika.width * razmera).toInt(), (slika.height * razmera).toInt(), true)
        } else slika
        java.io.FileOutputStream(fajl).use { smanjena.compress(android.graphics.Bitmap.CompressFormat.JPEG, kvalitet, it) }
        if (smanjena != slika) slika.recycle()
        smanjena.recycle()
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

    fun DodajKomentar(spotId: String, text: String, odgovorNa: Comment? = null, onResult: (Boolean) -> Unit) {
        if (!com.example.fishspot.feature.auth.PotvrdaNaloga.proveri()) { onResult(false); return }
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
                    timestamp = System.currentTimeMillis(),
                    odgovorNaId = odgovorNa?.id.orEmpty(),
                    odgovorNaIme = odgovorNa?.authorUsername.orEmpty().take(60)
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

    // Komentar briše njegov autor ili vlasnik ulova (pravila baze)
    fun obrisiKomentar(spotId: String, komentarId: String) {
        firestore.collection("MestaZaPecanje").document(spotId).collection("komentari").document(komentarId).delete()
            .addOnSuccessListener { com.example.fishspot.core.theme.FishPoruka.prikazi("Komentar je obrisan") }
            .addOnFailureListener { com.example.fishspot.core.theme.FishPoruka.prikazi("Komentar nije obrisan") }
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
    fun izmeniUlov(context: android.content.Context, spot: FishSpot, izmena: IzmenaUlova, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                val izmene = mutableMapOf<String, Any>(
                    "naziv" to izmena.naziv,
                    "vrstaRibe" to izmena.vrsta.naziv,
                    "cuvarka" to izmena.cuvarka,
                    "cuvarkaVrste" to izmena.cuvarkaVrste.map {
                        mapOf("id" to it.id, "naziv" to it.naziv, "latinskiNaziv" to it.latinskiNaziv)
                    },
                    "vrstaId" to izmena.vrsta.id,
                    "latinskiNaziv" to izmena.vrsta.latinskiNaziv,
                    "duzinaRibe" to izmena.duzinaRibe,
                    "tezinaRibe" to izmena.tezinaRibe,
                    "mamac" to izmena.mamac,
                    "tehnika" to izmena.tehnika,
                    "ocena" to izmena.ocena,
                    "opis" to izmena.opis
                )
                // Dodatne slike: zadržane ostaju, nove se smanje i pošalju pod istim početkom imena kao glavna
                // (da ih brisanje ulova obriše zajedno sa njom); uklonjene briše server (izmenjenUlov)
                if (izmena.dodatneSlike != spot.dodatneSlike || izmena.noveSlike.isNotEmpty()) {
                    val osnova = spot.slikaUrl?.let { Regex("/o/([^?]+)").find(it)?.groupValues?.get(1) }
                        ?.let { java.net.URLDecoder.decode(it, "UTF-8").removeSuffix(".jpg") }
                    val nove = if (osnova == null) emptyList() else izmena.noveSlike.mapIndexed { i, uri ->
                        val fajl = java.io.File(context.cacheDir, "izmena_${spot.id}_$i.jpg")
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            sacuvajSmanjeno(context, uri, fajl, MAKS_STRANA_DODATNE, KVALITET_DODATNE)
                        }
                        val ref = storage.reference.child("${osnova}_e${System.currentTimeMillis()}_${i + 1}.jpg")
                        ref.putFile(Uri.fromFile(fajl), com.example.fishspot.core.model.SLIKA_JPEG).await()
                        fajl.delete()
                        ref.downloadUrl.await().toString()
                    }
                    izmene["dodatneSlike"] = (izmena.dodatneSlike + nove).take(MAKS_SLIKA_ULOVA - 1)
                }
                // Posle 30 minuta od objave podaci o ribi se ne šalju (pravila baze bi odbila izmenu)
                if (!spot.ribaMozeDaSeMenja) {
                    izmene.keys.removeAll(listOf("vrstaRibe", "vrstaId", "latinskiNaziv", "duzinaRibe", "tezinaRibe", "cuvarka", "cuvarkaVrste"))
                }
                promeniSakrivanje(spot, izmena.sakrijLokaciju, izmene)
                firestore.collection("MestaZaPecanje").document(spot.id).update(izmene).await()
                onResult(true)
            } catch (e: Exception) {
                Log.e("SpotsViewModel", "Greška pri izmeni ulova", e)
                onResult(false)
            }
        }
    }

    // Tačne lokacije sopstvenih ulova sa sakrivenom lokacijom (za pin na tačnom mestu na mapi)
    val mojeTacneLokacije = kotlinx.coroutines.flow.MutableStateFlow<Map<String, com.google.android.gms.maps.model.LatLng>>(emptyMap())

    fun ucitajMojeTacneLokacije(spotovi: List<FishSpot>) {
        val moj = auth.currentUser?.uid ?: return
        val fale = spotovi.filter { it.skrivenaLokacija && it.authorId == moj && it.id !in mojeTacneLokacije.value }
        if (fale.isEmpty()) return
        viewModelScope.launch {
            val nove = fale.mapNotNull { s -> SkrivenaLokacija.tacna(s.id)?.let { s.id to it } }
            mojeTacneLokacije.value = mojeTacneLokacije.value + nove
        }
    }

    // Uključivanje ili isključivanje sakrivene lokacije na postojećem ulovu
    private suspend fun promeniSakrivanje(spot: FishSpot, sakrij: Boolean, izmene: MutableMap<String, Any>) {
        if (sakrij == spot.skrivenaLokacija) return
        val privatno = SkrivenaLokacija.privatnoRef(spot.id)
        if (sakrij) {
            privatno.set(mapOf("lat" to spot.lat, "lng" to spot.lng)).await()
            val p = SkrivenaLokacija.priblizno(spot.lat, spot.lng)
            izmene["lat"] = p.latitude; izmene["lng"] = p.longitude; izmene["skrivenaLokacija"] = true
            mojeTacneLokacije.value = mojeTacneLokacije.value + (spot.id to com.google.android.gms.maps.model.LatLng(spot.lat, spot.lng))
        } else {
            val tacna = mojeTacneLokacije.value[spot.id] ?: SkrivenaLokacija.tacna(spot.id)
            if (tacna != null) { izmene["lat"] = tacna.latitude; izmene["lng"] = tacna.longitude }
            izmene["skrivenaLokacija"] = false
            privatno.delete()
            mojeTacneLokacije.value = mojeTacneLokacije.value - spot.id
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
        // Obaveštenje kad admin objavi novi izazov
        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("izazovi")
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
        if (!com.example.fishspot.feature.auth.PotvrdaNaloga.proveri()) return
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