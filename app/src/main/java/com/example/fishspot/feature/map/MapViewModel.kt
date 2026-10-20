package com.example.fishspot.feature.map

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.fishspot.R
import com.example.fishspot.core.model.Korisnici
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.tasks.await
import java.net.HttpURLConnection
import java.net.URL
import com.example.fishspot.BuildConfig

private const val CHANNEL_ID = "fishspot_channel"
private val WATERWAY_TYPES = setOf("river", "stream", "canal", "drain", "ditch")

// Koristimo isključivo ovaj model za lokalni JSON format podataka
data class LocalWaterLine(
    val id: Long,
    val name: String,
    val type: String,
    val kategorija: String,
    val coordinates: List<List<Double>>
)

class MapViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val vremeObavestenjaVode = mutableMapOf<String, Long>()
    private val vremeObavestenjaKorisnika = mutableMapOf<String, Long>()
    private val LIMIT_ZATISJA_MS = 15 * 60 * 1000L

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation = _userLocation.asStateFlow()

    private val _vode = MutableStateFlow<List<OsmElement>>(emptyList())
    val vode = _vode.asStateFlow()

    private val _fokusirajLokaciju = MutableStateFlow<Pair<Double, Double>?>(null)
    val fokusirajLokaciju = _fokusirajLokaciju.asStateFlow()

    private val _trenutniKorisnik = MutableStateFlow<Korisnici?>(null)
    val trenutniKorisnik = _trenutniKorisnik.asStateFlow()

    private val _userProfileImage = MutableStateFlow<String?>(null)
    val userProfileImage = _userProfileImage.asStateFlow()

    private val _vodeNotificationsEnabled = MutableStateFlow(true)
    val vodeNotificationsEnabled = _vodeNotificationsEnabled.asStateFlow()

    // Da li korisnik prikazuje svoju lokaciju drugima na mapi (podrazumevano NE)
    private val _deliLokaciju = MutableStateFlow(false)
    val deliLokaciju = _deliLokaciju.asStateFlow()

    private var lastOsmCheckLocation: Location? = null

    init {
        // Pozivamo odmah pri kreiranju ViewModel-a
        ucitajPodatkeKorisnika()
    }

    fun setVodeNotificationsEnabled(context: Context, enabled: Boolean) {
        _vodeNotificationsEnabled.value = enabled
        val sharedPref = context.getSharedPreferences("fishspot_prefs", Context.MODE_PRIVATE)
        sharedPref.edit().putBoolean("vode_notifications_enabled", enabled).apply()

        if (enabled) {
            lastOsmCheckLocation = null
        }
    }

    fun ucitajPreferences(context: Context) {
        val sharedPref = context.getSharedPreferences("fishspot_prefs", Context.MODE_PRIVATE)
        _vodeNotificationsEnabled.value = sharedPref.getBoolean("vode_notifications_enabled", true)
        _deliLokaciju.value = sharedPref.getBoolean("deli_lokaciju", false)
    }

    fun setDeliLokaciju(context: Context, deli: Boolean) {
        _deliLokaciju.value = deli
        context.getSharedPreferences("fishspot_prefs", Context.MODE_PRIVATE)
            .edit().putBoolean("deli_lokaciju", deli).apply()
        // Kad isključi, odmah nestaje sa mape drugih korisnika
        if (!deli) auth.currentUser?.uid?.let { firestore.collection("ActiveUsers").document(it).delete() }
    }

    fun startLocationUpdates(context: Context) {
        ucitajPreferences(context)
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 15_000L)
            .setMinUpdateIntervalMillis(10_000L)
            .setMinUpdateDistanceMeters(15f)
            .build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { loc ->
                    Log.d("MapViewModel", "Nova lokacija: ${loc.latitude}, ${loc.longitude}")
                    _userLocation.value = loc
                    proveriBlizinu(loc, context)

                    if (_trenutniKorisnik.value == null) {
                        ucitajPodatkeKorisnika()
                    }
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Odmah uzmi keširanu lokaciju da mapa ne bude prazna
            fusedLocationClient.lastLocation.addOnSuccessListener { loc: Location? ->
                if (loc != null && _userLocation.value == null) {
                    Log.d("MapViewModel", "Uzeta keširana lokacija: ${loc.latitude}")
                    _userLocation.value = loc
                    proveriBlizinu(loc, context)
                }
            }

            // Potom nastavi sa redovnim osluškivanjem
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            createNotificationChannel(context)
        }
    }

    fun ucitajPodatkeKorisnika(forceReload: Boolean = false) {
        val uid = auth.currentUser?.uid ?: return
       // if (!forceReload && _trenutniKorisnik.value != null) return

        // Umesto .get().addOnSuccessListener, koristimo addSnapshotListener za osluškivanje uživo
        firestore.collection("Korisnici").document(uid)
            .addSnapshotListener { document, error ->
                if (error != null) {
                    Log.e("MapViewModel", "Greška pri osluškivanju promena korisnika: ", error)
                    return@addSnapshotListener
                }

                if (document != null && document.exists()) {
                    uskladiStaraPolja(document)
                    val korisnik = document.toObject(Korisnici::class.java)
                    _trenutniKorisnik.value = korisnik

                    // Čim se slikaURL promeni u bazi, odmah generišemo novi URL sa timestamp-om
                    val slikaUrl = korisnik?.slikaURL
                    if (!slikaUrl.isNullOrBlank()) {
                        val urlSaCacheBusting = "$slikaUrl?t=${System.currentTimeMillis()}"
                        _userProfileImage.value = urlSaCacheBusting
                        Log.d("MapViewModel", "Profilna slika detektovana i osvežena uživo: $urlSaCacheBusting")
                    }
                }
            }
    }
    // Stari dokumenti imaju polja fotoUrl/username umesto slikaURL/userName ili nemaju poene,
    // pa ih prebacujemo na nova imena. Posle prve izmene listener ne nalazi ništa i ne upisuje ponovo.
    private fun uskladiStaraPolja(document: DocumentSnapshot) {
        val izmene = mutableMapOf<String, Any>()

        val slika = document.getString("slikaURL")?.takeIf { it.isNotBlank() && it != "null" }
        if (slika == null) {
            // Prvo stara slika iz baze, pa slika sa naloga (npr. Google profilna)
            val zamena = document.getString("fotoUrl")?.takeIf { it.isNotBlank() && it != "null" }
                ?: auth.currentUser?.photoUrl?.toString()
            if (zamena != null) {
                izmene["slikaURL"] = zamena
            } else if (document.getString("slikaURL") == "null") {
                izmene["slikaURL"] = ""
            }
        }
        if (document.contains("fotoUrl")) izmene["fotoUrl"] = FieldValue.delete()
        // Lokacija se više ne čuva u profilu (profil vide svi prijavljeni korisnici)
        listOf("lat", "lng", "timestamp").forEach { if (document.contains(it)) izmene[it] = FieldValue.delete() }

        val staroIme = document.getString("username")
        if (document.getString("userName").isNullOrBlank() && !staroIme.isNullOrBlank()) {
            izmene["userName"] = staroIme
        }
        if (document.contains("username")) izmene["username"] = FieldValue.delete()

        if (!document.contains("poeni")) izmene["poeni"] = 0

        if (izmene.isNotEmpty()) {
            document.reference.update(izmene)
                .addOnFailureListener { Log.e("MapViewModel", "Greška pri usklađivanju polja korisnika", it) }
        }
    }

    fun azurirajProfilnuSliku(novaUrl: String) {
        val urlSaCacheBusting = "$novaUrl?t=${System.currentTimeMillis()}"
        _userProfileImage.value = urlSaCacheBusting
        Log.d("MapViewModel", "Profilna slika ažurirana na mapi: $urlSaCacheBusting")
    }

    // Obaveštenje kad je blizu neko ko deli lokaciju (lista dolazi iz ActiveUsersViewModel-a)
    fun proveriBliskeKorisnike(korisnici: List<com.example.fishspot.feature.chat.ActiveUser>, context: Context) {
        val myLoc = _userLocation.value ?: return
        val mojUid = auth.currentUser?.uid
        val sada = System.currentTimeMillis()
        for (k in korisnici) {
            if (k.userId == mojUid) continue
            val udaljenost = myLoc.distanceTo(Location("").apply { latitude = k.lat; longitude = k.lng })
            val poslednje = vremeObavestenjaKorisnika[k.userId] ?: 0L
            if (udaljenost < 300 && sada - poslednje > LIMIT_ZATISJA_MS) {
                prikaziNotifikaciju("Korisnik ${k.username} je u tvojoj blizini!", context, k.lat, k.lng)
                vremeObavestenjaKorisnika[k.userId] = sada
            }
        }
    }

    private fun proveriBlizinu(myLoc: Location, context: Context) {
        val trenutnoVreme = System.currentTimeMillis()

        // 2. PROVERA SVIH VODA U RADIJUSU OD 5KM
        if (lastOsmCheckLocation == null || myLoc.distanceTo(lastOsmCheckLocation!!) >= 1500) {

            if (!_vodeNotificationsEnabled.value) {
                Log.d("MapViewModel-Vode", "Obaveštenja o vodama su isključena od strane korisnika.")
                return
            }

            lastOsmCheckLocation = myLoc

            // POPRAVKA: Dodat Dispatchers.IO kako se mrežni zahtevi i čitanje fajlova ne bi radili na glavnoj niti!
            viewModelScope.launch(Dispatchers.IO) {
                val userLat = myLoc.latitude
                val userLng = myLoc.longitude

                val osvezeneVode = if (isLocationInSerbia(userLat, userLng)) {
                    Log.d("MapViewModel-Vode", "Korisnik je u Srbiji. Čitam iz lokalnog JSON-a...")
                    getNearbyWatersFromLocal(context, userLat, userLng, 5000f)
                } else {
                    Log.d("MapViewModel-Vode", "Korisnik je van Srbije. Pokrećem eksterni API...")
                    fetchNearbyWatersFromApi(userLat, userLng, 5000f)
                }

                obradiVodeINotifikacije(osvezeneVode, myLoc, trenutnoVreme, context)
            }
        }
    }

    private fun obradiVodeINotifikacije(vodeList: List<OsmElement>, myLoc: Location, trenutnoVreme: Long, context: Context) {
        _vode.value = vodeList
        if (vodeList.isEmpty()) {
            Log.d("MapViewModel-Vode", "Nema nađenih voda u krugu od 5km.")
            return
        }

        for (voda in vodeList) {
            val latVode = voda.center?.lat ?: voda.lat ?: 0.0
            val lngVode = voda.center?.lon ?: voda.lon ?: 0.0

            if (latVode != 0.0 && lngVode != 0.0) {
                val locVode = Location("").apply { latitude = latVode; longitude = lngVode }
                val d = myLoc.distanceTo(locVode)

                if (d <= 5000) {
                    val tagovi = voda.tags
                    val naziv = tagovi?.get("name") ?: "Nepoznata voda"

                    val tipVode = when (tagovi?.get("waterway")) {
                        "river"  -> "Reka"
                        "stream" -> "Potok"
                        "canal"  -> "Kanal"
                        "drain"  -> "Odvod"
                        "ditch"  -> "Jarak"
                        else -> when (tagovi?.get("water")) {
                            "lake"       -> "Jezero"
                            "reservoir"  -> "Akumulacija"
                            "pond"       -> "Bara"
                            else         -> "Voda"
                        }
                    }

                    val poslednjeObavestenjeZaVodu = vremeObavestenjaVode[naziv] ?: 0L

                    if (trenutnoVreme - poslednjeObavestenjeZaVodu > LIMIT_ZATISJA_MS) {
                        val tekstUdaljenosti = if (d >= 1000) {
                            String.format("%.1f km", d / 1000f)
                        } else {
                            "${d.toInt()} metara"
                        }

                        Log.d("USPEH_REKA", "Nađena voda: $naziv ($tipVode) na $tekstUdaljenosti")

                        prikaziNotifikaciju(
                            "Na $tekstUdaljenosti od tebe je $naziv ($tipVode)",
                            context, latVode, lngVode
                        )
                        vremeObavestenjaVode[naziv] = trenutnoVreme
                    }
                }
            }
        }
    }

    // Vode van Srbije daje naša Cloud Function vodeUBlizini (Geoapify ključ je samo na serveru)
    private suspend fun fetchNearbyWatersFromApi(lat: Double, lng: Double, radiusInMeters: Float): List<OsmElement> {
        return try {
            val rezultat = com.google.firebase.functions.FirebaseFunctions.getInstance()
                .getHttpsCallable("vodeUBlizini")
                .call(mapOf("lat" to lat, "lng" to lng))
                .await()
            @Suppress("UNCHECKED_CAST")
            val vode = (rezultat.data as? Map<String, Any?>)?.get("vode") as? List<Map<String, Any?>> ?: emptyList()
            vode.mapNotNull { v ->
                val latVode = (v["lat"] as? Number)?.toDouble() ?: return@mapNotNull null
                val lonVode = (v["lon"] as? Number)?.toDouble() ?: return@mapNotNull null
                val naziv = v["naziv"] as? String ?: "Nepoznata voda"
                val tagovi = when (val tip = v["tip"] as? String) {
                    "river", "stream", "canal" -> mapOf("name" to naziv, "waterway" to tip)
                    else -> mapOf("name" to naziv, "water" to "lake")
                }
                OsmElement(
                    id = (v["id"] as? String)?.hashCode()?.toLong() ?: latVode.hashCode().toLong(),
                    tags = tagovi,
                    lat = latVode,
                    lon = lonVode,
                    center = Center(latVode, lonVode)
                )
            }
        } catch (e: Exception) {
            Log.e("MapViewModel-Vode", "Vode u blizini nisu dostupne", e)
            emptyList()
        }
    }
    private fun prikaziNotifikaciju(poruka: String, context: Context, lat: Double? = null, lng: Double? = null) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val intent = Intent(context, com.example.fishspot.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (lat != null && lng != null) {
                putExtra("ZUMIRAJ_LAT", lat)
                putExtra("ZUMIRAJ_LNG", lng)
            }
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FishSpot obaveštenje")
            .setContentText(poruka)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    fun postaviLokacijuZaFokus(lat: Double, lng: Double) { _fokusirajLokaciju.value = Pair(lat, lng) }
    fun resetujFokus() { _fokusirajLokaciju.value = null }

    fun logout() {
        auth.signOut()
        _trenutniKorisnik.value = null
        _userProfileImage.value = null
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "FishSpot Obaveštenja", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Kanal za obaveštenja o blizini korisnika i ribolovnih voda"
            }
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun isLocationInSerbia(lat: Double, lng: Double): Boolean {
        return lat in 42.23..46.19 && lng in 18.83..23.01
    }

    private fun getNearbyWatersFromLocal(context: Context, userLat: Double, userLng: Double, radiusInMeters: Float): List<OsmElement> {
        return try {
            val jsonString = context.assets.open("Mape/srbija_vode.json").bufferedReader().use { it.readText() }

            val listType = object : TypeToken<List<LocalWaterLine>>() {}.type
            val allWaters: List<LocalWaterLine> = Gson().fromJson(jsonString, listType)
            val filteredElements = mutableListOf<OsmElement>()

            // PRIVREMENI DEBUG LOG: Pomaže nam da vidimo tačan raspored koordinata u tvom fajlu!
            if (allWaters.isNotEmpty()) {
                val prvaVoda = allWaters.first()
                Log.d("MapViewModel-Debug", "Prva voda: ${prvaVoda.name}, prve koordinate: ${prvaVoda.coordinates.firstOrNull()}")
            }

            for (water in allWaters) {
                if (water.coordinates.isNullOrEmpty()) continue

                var isWithinRadius = false
                var minDistance = Float.MAX_VALUE // Počinjemo sa maksimalnom mogućom vrednošću
                var matchingLat = 0.0
                var matchingLon = 0.0

                // Prolazimo kroz SVE koordinate reke/vode bez prekidanja
                for (point in water.coordinates) {
                    if (point.size < 2) continue

                    val lat = point[0]
                    val lon = point[1]

                    val results = FloatArray(1)
                    Location.distanceBetween(userLat, userLng, lat, lon, results)
                    val currentDistance = results[0]

                    // Uslov 1: Tačka mora biti unutar tvog radijusa
                    // Uslov 2: Tačka mora biti BLIŽA od bilo koje tačke koju smo do sada našli za ovu reku
                    if (currentDistance <= radiusInMeters && currentDistance < minDistance) {
                        minDistance = currentDistance
                        matchingLat = lat  // Pamtimo ovu tačku jer nam je trenutno najbliža
                        matchingLon = lon
                        isWithinRadius = true
                        // Ovde VIŠE NEMA 'break' - puštamo petlju da proveri i ostale tačke do kraja reke
                    }
                }

                if (isWithinRadius) {
                    val tags: Map<String, String> = if (water.type in WATERWAY_TYPES) {
                        mapOf(
                            "name"     to water.name,
                            "waterway" to water.type
                        )
                    } else {
                        mapOf(
                            "name"    to water.name,
                            "natural" to "water",
                            "water"   to water.type
                        )
                    }

                    filteredElements.add(
                        OsmElement(
                            id     = water.id,
                            tags   = tags,
                            lat    = matchingLat, // Pin će se sada prikazati na garantovano najbližoj tački tebi
                            lon    = matchingLon,
                            center = Center(matchingLat, matchingLon)
                        )
                    )
                }
            }
            Log.d("MapViewModel-Local", "Učitano ${filteredElements.size} voda u radijusu ${radiusInMeters.toInt()}m")
            filteredElements
        } catch (e: Exception) {
            Log.e("MapViewModel-Local", "Greška pri čitanju lokalnog JSON-a", e)
            emptyList()
        }
    }
}
