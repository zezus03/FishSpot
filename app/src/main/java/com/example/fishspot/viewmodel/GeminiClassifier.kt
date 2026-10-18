package com.example.fishspot.ml

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.tasks.await
import java.io.ByteArrayOutputStream

// Prepoznavanje ribe ide preko Cloud Function "prepoznajRibu" (functions/index.js),
// tako da Gemini ključ ostaje na serveru i nije ugrađen u aplikaciju.
class GeminiClassifier {

    private val functions = Firebase.functions

    // Vraća Pair: Prva vrednost je Vrsta, druga vrednost je Težina
    suspend fun prepoznajRibu(bitmap: Bitmap, duzina: Double? = null): Pair<String, String>? {
        val podaci = hashMapOf<String, Any>(
            "slika" to uBase64Jpeg(bitmap),
            "duzina" to (duzina ?: 0.0)
        )

        // UKLONJEN TRY-CATCH: Puštamo grešku da ode u UI ekran kako bi iskočio Toast!
        val rezultat = functions.getHttpsCallable("prepoznajRibu").call(podaci).await()
        val mapa = rezultat.data as? Map<*, *> ?: return null

        val vrsta = mapa["vrsta"] as? String ?: return null
        val tezina = mapa["tezina"] as? String ?: ""
        Log.d("GeminiML", "Odgovor funkcije: $vrsta | $tezina")
        return Pair(vrsta, tezina)
    }

    // Smanjujemo sliku na najviše 1024 px da slanje bude brzo i ispod limita funkcije
    private fun uBase64Jpeg(bitmap: Bitmap): String {
        val maks = 1024
        val skala = minOf(1f, maks.toFloat() / maxOf(bitmap.width, bitmap.height))
        val manja = if (skala < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * skala).toInt(), (bitmap.height * skala).toInt(), true)
        } else bitmap

        val baos = ByteArrayOutputStream()
        manja.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)
    }
}
