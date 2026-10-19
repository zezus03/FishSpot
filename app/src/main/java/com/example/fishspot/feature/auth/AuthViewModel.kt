package com.example.fishspot.feature.auth

import android.util.Log
import com.example.fishspot.core.model.Korisnici
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
        val korisnikRef = firestore.collection("Korisnici").document(user.uid)

        korisnikRef.get()
            .continueWithTask { task ->
                val postoji = task.result?.exists() == true
                if (postoji) {
                    // Postojećem korisniku ne diramo ime i sliku koje je možda promenio na profilu
                    korisnikRef.set(mapOf("email" to (user.email ?: "")), SetOptions.merge())
                } else {
                    // Ista polja kao kod registracije emailom (klasa Korisnici)
                    val noviKorisnik = Korisnici(
                        id = user.uid,
                        email = user.email ?: "",
                        userName = user.displayName ?: "Pecaroš",
                        imePrezime = user.displayName ?: "",
                        slikaURL = user.photoUrl?.toString() ?: "",
                        poeni = 0
                    )
                    korisnikRef.set(noviKorisnik)
                }
            }
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