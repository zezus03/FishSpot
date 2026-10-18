package com.example.fishspot.viewmodel

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class AuthViewModel {

    // Inicijalizacija Firebase instanci koje su nedostajale
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    fun loginSaGoogleom(idToken: String, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        // Pretvaramo Google Token u Firebase kredencijal
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = task.result?.user
                    if (user != null) {
                        // Kada se uspešno uloguje, inicijalizujemo njegove osnovne podatke u Firestore
                        inicijalizujKorisnikaUFirestore(user, onSuccess, onFailure)
                    } else {
                        onFailure(Exception("Korisnik je null nakon prijave."))
                    }
                } else {
                    task.exception?.let { onFailure(it) }
                }
            }
    }

    private fun inicijalizujKorisnikaUFirestore(user: FirebaseUser, onSuccess: () -> Unit, onFailure: (Exception) -> Unit) {
        val uid = user.uid

        // Izvlačimo osnovne podatke sa Google naloga
        val podaciKorisnika = hashMapOf(
            "uid" to uid,
            "email" to user.email,
            "username" to (user.displayName ?: "Pecaroš"),
            "slikaURL" to user.photoUrl.toString(),
            "kreiran" to System.currentTimeMillis()
        )

        // SetOptions.merge() osigurava da ako korisnik već postoji, ne brišemo njegove stare podatke (npr. lokaciju)
        firestore.collection("Korisnici").document(uid)
            .set(podaciKorisnika, SetOptions.merge())
            .addOnSuccessListener {
                Log.d("AuthViewModel", "Korisnik uspešno sinhronizovan sa Firestore-om.")
                onSuccess()
            }
            .addOnFailureListener { e ->
                Log.e("AuthViewModel", "Greška pri upisu korisnika u Firestore", e)
                onFailure(e)
            }
    }
}