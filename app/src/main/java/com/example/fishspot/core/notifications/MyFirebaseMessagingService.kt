package com.example.fishspot.core.notifications

import android.app.ActivityManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.fishspot.MainActivity
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishPoruka
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.notification?.title ?: "Novi komentar!"
        val body = remoteMessage.notification?.body ?: "Neko je komentarisao tvoj ulov."

        // Dok je aplikacija otvorena: poruku već prikazuje balončić, a komentar ide kao poruka u aplikaciji
        if (aplikacijaNaEkranu()) {
            if (remoteMessage.data["tip"] == "poruka") return
            FishPoruka.prikazi("$title\n$body", dugo = true)
            return
        }
        prikaziNotifikaciju(title, body, remoteMessage.data)
    }

    // Ako se token promeni (npr. korisnik reinstalira app), ažuriraj ga
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        // Bez prijavljenog korisnika nemamo gde da upišemo token; upisaće ga MapScreen posle prijave
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        FirebaseFirestore.getInstance().collection("Korisnici").document(uid)
            .set(mapOf("fcmToken" to token), SetOptions.merge())
            .addOnFailureListener { Log.e("FCM", "Greška pri čuvanju novog tokena", it) }
    }

    private fun aplikacijaNaEkranu(): Boolean {
        val stanje = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(stanje)
        return stanje.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun prikaziNotifikaciju(title: String, messageBody: String, podaci: Map<String, String>) {
        // Otvara postojeći ekran (bez ponovnog pokretanja aplikacije) i zumira na ulov
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            podaci["ZUMIRAJ_LAT"]?.let { putExtra("ZUMIRAJ_LAT", it) }
            podaci["ZUMIRAJ_LNG"]?.let { putExtra("ZUMIRAJ_LNG", it) }
            podaci["OTVORI_CHAT"]?.let { putExtra("OTVORI_CHAT", it) }
            podaci["IME"]?.let { putExtra("IME", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val channelId = "fishspot_obavestenja"
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Komentari i Obaveštenja",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
