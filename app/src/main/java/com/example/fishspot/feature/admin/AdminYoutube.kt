package com.example.fishspot.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.example.fishspot.core.theme.fishPoljeBoje
import com.google.firebase.functions.FirebaseFunctions

// Odeljak u admin panelu: link YouTube kanala na profilu korisnika (npr. pecaroš koji reklamira FishSpot).
// Na njegovom profilu se pojavi dugme "YouTube kanal". Upisuje server (adminYoutube), korisnik sam ne može.
@Composable
fun AdminYoutubeOdeljak() {
    var korisnik by remember { mutableStateOf<Nalog?>(null) }
    var link by remember { mutableStateOf("") }
    var radi by remember { mutableStateOf(false) }

    fun sacuvaj(noviLink: String) {
        val k = korisnik ?: return
        radi = true
        FirebaseFunctions.getInstance().getHttpsCallable("adminYoutube").call(mapOf("uid" to k.uid, "link" to noviLink))
            .addOnSuccessListener {
                radi = false
                FishPoruka.prikazi(if (noviLink.isBlank()) "YouTube dugme uklonjeno sa profila ${k.ime}" else "YouTube dugme dodato na profil ${k.ime}")
                link = ""; korisnik = null
            }
            .addOnFailureListener { radi = false; FishPoruka.prikazi(it.message ?: "Greška na serveru", dugo = true) }
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.PlayArrow, null, tint = Color(0xFFE62117), modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("YouTube kanal", color = FishBoje.TekstPrimarni, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Dugme za kanal na profilu korisnika koji podržava FishSpot.", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
            }
        }
        IzborNaloga("Korisnik", korisnik) { korisnik = it }
        OutlinedTextField(
            value = link,
            onValueChange = { link = it.trim() },
            label = { Text("Link kanala") },
            placeholder = { Text("youtube.com/@ImeKanala") },
            singleLine = true,
            colors = fishPoljeBoje(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { sacuvaj("") },
                enabled = !radi && korisnik != null,
                modifier = Modifier.weight(1f)
            ) { Text("Ukloni dugme", color = FishBoje.TekstPrimarni) }
            Button(
                onClick = { sacuvaj(link) },
                enabled = !radi && korisnik != null && link.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = Color.Black),
                modifier = Modifier.weight(1f)
            ) { Text("Sačuvaj", fontWeight = FontWeight.Bold) }
        }
    }
}
