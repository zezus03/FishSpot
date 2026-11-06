package com.example.fishspot

import android.app.Application
import com.google.firebase.FirebaseApp

// Pokreće se pre svih ekrana: uključuje App Check, da server zna da zahtev stiže iz prave aplikacije
class FishSpotApp : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        ukljuciAppCheck()
        // Spisak blokiranih korisnika prati prijavljenog korisnika od pokretanja
        com.example.fishspot.feature.chat.Blokiranje.pokreni()
        // Obaveštenja u aplikaciji (zvonce u profilu, tačkica na mapi)
        com.example.fishspot.feature.profile.Obavestenja.pokreni()
    }
}
