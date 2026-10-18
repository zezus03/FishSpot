package com.example.fishspot.viewmodel

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

class UploadWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val filePath = inputData.getString("FILE_PATH") ?: return Result.failure()
        val documentId = inputData.getString("DOC_ID") ?: return Result.failure()

        val file = File(filePath)
        if (!file.exists()) {
            Log.e("UploadWorker", "Fajl ne postoji lokalno!")
            return Result.failure()
        }

        return try {
            Log.d("UploadWorker", "Internet stigao! Šaljem sliku...")

            val storageRef = FirebaseStorage.getInstance().reference.child("spot_slike/${UUID.randomUUID()}.jpg")
            storageRef.putFile(Uri.fromFile(file)).await()
            val downloadUrl = storageRef.downloadUrl.await().toString()

            FirebaseFirestore.getInstance().collection("MestaZaPecanje")
                .document(documentId)
                .update("slikaUrl", downloadUrl)
                .await()

            file.delete() // Brišemo lokalnu kopiju jer je posao završen

            Log.d("UploadWorker", "Slika uspešno poslata i baza ažurirana!")
            Result.success()
        } catch (e: Exception) {
            Log.e("UploadWorker", "Greška. Pokušaću ponovo kasnije.", e)
            Result.retry() // Ako ponovo pukne net, probaj opet kasnije!
        }
    }
}