package com.example.fishspot

import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

// Verzija za razvoj: probni token (ispiše se u Logcat-u, pa se doda u Firebase konzoli → App Check → Manage debug tokens)
fun ukljuciAppCheck() {
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
