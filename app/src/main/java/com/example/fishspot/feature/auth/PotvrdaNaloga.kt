package com.example.fishspot.feature.auth

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Potvrda e-adrese. Nalog napravljen e-adresom može da gleda aplikaciju, ali za objavu ulova, komentare,
 * lajkove, poruke, Deda Peru i izazove mora prvo da klikne link iz maila (isto proveravaju i pravila baze).
 * Google nalozi su uvek potvrđeni.
 */
object PotvrdaNaloga {
    private val _prikazi = MutableStateFlow(false)
    val prikazi = _prikazi.asStateFlow()

    fun potvrdjen(): Boolean = FirebaseAuth.getInstance().currentUser?.isEmailVerified == true

    /** Vraća true ako je nalog potvrđen; inače otvara prozor za potvrdu i vraća false. */
    fun proveri(): Boolean {
        if (potvrdjen()) return true
        _prikazi.value = true
        return false
    }

    fun zatvori() { _prikazi.value = false }

    fun posaljiLink(tiho: Boolean = false) {
        val korisnik = FirebaseAuth.getInstance().currentUser ?: return
        korisnik.sendEmailVerification()
            .addOnSuccessListener {
                if (!tiho) FishPoruka.prikazi("Link je poslat na ${korisnik.email}. Proveri i Spam.", dugo = true)
            }
            .addOnFailureListener {
                if (!tiho) FishPoruka.prikazi("Link nije poslat. Pokušaj ponovo za minut.", dugo = true)
            }
    }

    /** Posle klika na link: osvežava nalog i token (u tokenu je oznaka potvrde za pravila baze) */
    suspend fun osvezi(): Boolean {
        val korisnik = FirebaseAuth.getInstance().currentUser ?: return false
        korisnik.reload().await()
        if (!korisnik.isEmailVerified) return false
        korisnik.getIdToken(true).await()
        return true
    }
}

// Prozor se prikazuje preko celog ekrana (MainActivity), kad god ga neka radnja zatraži
@Composable
fun PotvrdaNalogaHost() {
    val prikazi by PotvrdaNaloga.prikazi.collectAsState()
    if (!prikazi) return
    val scope = rememberCoroutineScope()
    var provera by remember { mutableStateOf(false) }
    val email = FirebaseAuth.getInstance().currentUser?.email.orEmpty()

    AlertDialog(
        onDismissRequest = { PotvrdaNaloga.zatvori() },
        containerColor = FishBoje.Kartica,
        titleContentColor = FishBoje.TekstPrimarni,
        textContentColor = FishBoje.TekstSekundarni,
        icon = { Icon(Icons.Default.MarkEmailUnread, null, tint = FishBoje.Akcenat) },
        title = { Text("Potvrdi e-adresu", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Da bi objavljivao ulove, komentarisao i slao poruke, klikni na link koji smo poslali na $email. " +
                    "Ako ga ne vidiš, proveri Spam ili pošalji novi link."
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    provera = true
                    scope.launch {
                        val ok = try { PotvrdaNaloga.osvezi() } catch (e: Exception) { false }
                        provera = false
                        if (ok) {
                            PotvrdaNaloga.zatvori()
                            FishPoruka.prikazi("Nalog je potvrđen. Hvala!")
                        } else {
                            FishPoruka.prikazi("Adresa još nije potvrđena. Klikni na link u mailu.", dugo = true)
                        }
                    }
                },
                enabled = !provera,
                colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
            ) {
                if (provera) CircularProgressIndicator(Modifier.size(18.dp), color = FishBoje.Pozadina, strokeWidth = 2.dp)
                else Text("Potvrdio sam", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { PotvrdaNaloga.posaljiLink() }) { Text("Pošalji novi link", color = FishBoje.Akcenat) }
        }
    )
}
