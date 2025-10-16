package com.example.fishspot.viewmodel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.ViewModel
import com.example.fishspot.R
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val CHANNEL_ID = "fishspot_channel"

class MapViewModel : ViewModel() {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _userLocation = MutableStateFlow<Location?>(null)
    val userLocation = _userLocation.asStateFlow()

    fun startLocationUpdates(context: Context) {
        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            30000
        ).build()

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.lastLocation?.let { loc ->
                    Log.d("MapViewModel", "Nova lokacija: ${loc.latitude}, ${loc.longitude}")
                    _userLocation.value = loc
                    sacuvajLokaciju(loc)
                    proveriBlizinu(loc, context)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            createNotificationChannel(context)
        }
    }

    private fun sacuvajLokaciju(location: Location) {
        val data = hashMapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "timestamp" to System.currentTimeMillis()
        )
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("Korisnici").document(uid).set(data, SetOptions.merge())
        }
    }

    private fun proveriBlizinu(myLoc: Location, context: Context) {
        firestore.collection("VodenaTela").get().addOnSuccessListener { result ->
            for (doc in result) {
                val lat = doc.getDouble("lat") ?: continue
                val lng = doc.getDouble("lng") ?: continue
                val naziv = doc.getString("naziv") ?: "Nepoznato"
                val tip = doc.getString("tip") ?: "objekat"

                val locObj = Location("").apply {
                    latitude = lat
                    longitude = lng
                }
                val distance = myLoc.distanceTo(locObj)

                if (distance < 5000) {
                    prikaziNotifikaciju("U blizini je $naziv ($tip)", context)
                }
            }
        }

        firestore.collection("Korisnici").get().addOnSuccessListener { result ->
            for (doc in result) {
                if (doc.id == auth.currentUser?.uid) continue
                val lat = doc.getDouble("lat") ?: continue
                val lng = doc.getDouble("lng") ?: continue
                val username = doc.getString("userName") ?: "Nepoznat korisnik"

                val locUser = Location("").apply {
                    latitude = lat
                    longitude = lng
                }
                val distance = myLoc.distanceTo(locUser)

                if (distance < 300) {
                    prikaziNotifikaciju("Korisnik $username je u tvojoj blizini!", context)
                }
            }
        }
    }

    private fun prikaziNotifikaciju(poruka: String, context: Context) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("FishSpot obaveštenje")
            .setContentText(poruka)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(context)) {
            notify(System.currentTimeMillis().toInt(), builder.build())
        }
    }

    fun logout() {
        auth.signOut()
    }

    private fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FishSpot Obaveštenja",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Kanal za obaveštenja o blizini korisnika i ribolovnih voda"
            }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }
}