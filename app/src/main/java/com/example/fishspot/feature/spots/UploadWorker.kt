package com.example.fishspot.feature.spots

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.UUID

// Šalje slike ulova kad ima interneta: glavnu (spot_slike/{ime}.jpg) i dodatne (spot_slike/{ime}_d1.jpg...).
// Dodatne imaju isti početak imena, pa ih brisanje ulova na serveru briše zajedno sa glavnom.
class UploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString("FILE_PATH") ?: return Result.failure()
        val documentId = inputData.getString("DOC_ID") ?: return Result.failure()
        val dodatne = inputData.getStringArray("DODATNE").orEmpty().map { File(it) }.filter { it.exists() }
        // Isto ime pri svakom pokušaju, da ponovljeno slanje ne pravi nove fajlove
        val ime = inputData.getString("IME") ?: UUID.randomUUID().toString()

        val file = File(filePath)
        if (!file.exists()) {
            Log.e("UploadWorker", "Fajl ne postoji lokalno!")
            return Result.failure()
        }

        return try {
            val storage = FirebaseStorage.getInstance().reference
            suspend fun posalji(fajl: File, putanja: String): String {
                val ref = storage.child(putanja)
                // Slika poslata u prethodnom pokušaju se ne šalje ponovo (pravila ne dozvoljavaju prepisivanje)
                try {
                    return ref.downloadUrl.await().toString()
                } catch (e: com.google.firebase.storage.StorageException) {
                    if (e.errorCode != com.google.firebase.storage.StorageException.ERROR_OBJECT_NOT_FOUND) throw e
                }
                ref.putFile(Uri.fromFile(fajl)).await()
                return ref.downloadUrl.await().toString()
            }

            val glavnaUrl = posalji(file, "spot_slike/$ime.jpg")
            val dodatneUrl = dodatne.mapIndexed { i, fajl -> posalji(fajl, "spot_slike/${ime}_d${i + 1}.jpg") }

            // Jedan upis za sve slike; slikaUrl pokreće proveru glavne slike na serveru
            FirebaseFirestore.getInstance().collection("MestaZaPecanje")
                .document(documentId)
                .update(mapOf("slikaUrl" to glavnaUrl, "dodatneSlike" to dodatneUrl))
                .await()

            file.delete() // Brišemo lokalne kopije jer je posao završen
            dodatne.forEach { it.delete() }

            Log.d("UploadWorker", "Poslato slika: ${1 + dodatneUrl.size}")
            Result.success()
        } catch (e: Exception) {
            Log.e("UploadWorker", "Greška. Pokušaću ponovo kasnije.", e)
            Result.retry() // Ako ponovo pukne net, probaj opet kasnije!
        }
    }
}
