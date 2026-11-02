package com.example.fishspot

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

// Verzija za Play: Google Play Integrity potvrđuje da je aplikacija originalna i instalirana sa Play-a
fun ukljuciAppCheck() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
}
