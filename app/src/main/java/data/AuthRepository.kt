
package com.example.fishspot.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class AuthRepository {

    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    suspend fun registrujKorisnika(
        email: String,
        sifra: String,
        userName: String,
        imePrezime: String,
        telefon: String,
        slikaBytes: ByteArray?
    ): Result<Unit> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, sifra).await()
            val uid = result.user?.uid ?: throw Exception("Korisnik nije napravljen")

            var slikaUrl = ""
            if (slikaBytes != null && slikaBytes.isNotEmpty()) {
                val ref = storage.reference.child("profilne_slike/$uid.jpg")
                ref.putBytes(slikaBytes).await()
                slikaUrl = ref.downloadUrl.await().toString()
            }

            val korisnik = Korisnici(
                id = uid,
                email = email,
                userName = userName,
                imePrezime = imePrezime,
                telefon = telefon,
                sifra = sifra,
                slikaURL = slikaUrl,
                poeni = 0
            )

            firestore.collection("Korisnici").document(uid).set(korisnik).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepo", "Greška prilikom registracije: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun prijaviKorisnika(email: String, sifra: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, sifra).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("AuthRepo", "Greška prilikom prijave: ${e.message}", e)
            Result.failure(e)
        }
    }
}