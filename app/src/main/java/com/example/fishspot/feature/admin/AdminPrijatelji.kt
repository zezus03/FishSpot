package com.example.fishspot.feature.admin

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.fishspot.R
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.example.fishspot.core.theme.fishPoljeBoje
import com.google.firebase.functions.FirebaseFunctions

private data class KorisnikZaBedz(val uid: String, val ime: String, val opis: String, val slika: String)

private fun List<*>?.uKorisnike(): List<KorisnikZaBedz> = orEmpty().mapNotNull { stavka ->
    val m = stavka as? Map<*, *> ?: return@mapNotNull null
    KorisnikZaBedz(
        uid = m["uid"] as? String ?: return@mapNotNull null,
        ime = (m["ime"] as? String).orEmpty().ifBlank { "Pecaroš" },
        opis = (m["imePrezime"] as? String).orEmpty(),
        slika = (m["slika"] as? String).orEmpty()
    )
}

// Odeljak u admin panelu: bedž "Prijatelj FishSpot-a" (dodela i oduzimanje preko servera, adminBedzPrijatelj)
@Composable
fun AdminPrijateljiOdeljak() {
    var upit by remember { mutableStateOf("") }
    var rezultati by remember { mutableStateOf(emptyList<KorisnikZaBedz>()) }
    var prijatelji by remember { mutableStateOf(emptyList<KorisnikZaBedz>()) }
    var radi by remember { mutableStateOf(false) }
    var potvrda by remember { mutableStateOf<Pair<KorisnikZaBedz, Boolean>?>(null) } // (korisnik, dodeli?)

    fun pozovi(podaci: Map<String, Any>, posle: (Map<*, *>) -> Unit = {}) {
        radi = true
        FirebaseFunctions.getInstance().getHttpsCallable("adminBedzPrijatelj").call(podaci)
            .addOnSuccessListener { r ->
                radi = false
                val m = r.data as? Map<*, *> ?: return@addOnSuccessListener
                (m["prijatelji"] as? List<*>)?.let { prijatelji = it.uKorisnike() }
                posle(m)
            }
            .addOnFailureListener { radi = false; FishPoruka.prikazi(it.message ?: "Greška na serveru") }
    }
    LaunchedEffect(Unit) { pozovi(mapOf("akcija" to "lista")) }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.bedz_prijatelj), null, Modifier.size(44.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Prijatelj FishSpot-a", color = FishBoje.TekstPrimarni, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Jedinstven bedž koji dodeljuješ ti. Korisnik dobija obaveštenje.", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                upit, { upit = it.take(40) }, label = { Text("Korisničko ime (početak)") }, singleLine = true,
                colors = fishPoljeBoje(), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    pozovi(mapOf("akcija" to "trazi", "upit" to upit.trim())) { m ->
                        rezultati = (m["korisnici"] as? List<*>).uKorisnike()
                        if (rezultati.isEmpty()) FishPoruka.prikazi("Nema korisnika čije ime počinje sa „${upit.trim()}“")
                    }
                },
                enabled = upit.isNotBlank() && !radi
            ) { Icon(Icons.Default.Search, "Traži", tint = FishBoje.Akcenat) }
        }
        rezultati.forEach { k ->
            val vec = prijatelji.any { it.uid == k.uid }
            RedKorisnika(k, if (vec) "Već ima" else "Dodeli", enabled = !vec && !radi) { potvrda = k to true }
        }
        Text(
            if (prijatelji.isEmpty()) "Još niko nema ovaj bedž." else "Imaju bedž (${prijatelji.size})",
            color = FishBoje.TekstSekundarni, fontSize = 13.sp, fontWeight = FontWeight.Bold
        )
        prijatelji.forEach { k -> RedKorisnika(k, "Oduzmi", enabled = !radi, opasno = true) { potvrda = k to false } }
    }

    potvrda?.let { (k, dodeli) ->
        AlertDialog(
            onDismissRequest = { potvrda = null },
            containerColor = FishBoje.Kartica,
            titleContentColor = FishBoje.TekstPrimarni,
            textContentColor = FishBoje.TekstSekundarni,
            title = { Text(if (dodeli) "Dodeli bedž?" else "Oduzmi bedž?", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    if (dodeli) "${k.ime} postaje Prijatelj FishSpot-a i dobija obaveštenje."
                    else "${k.ime} više neće imati bedž Prijatelj FishSpot-a."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pozovi(mapOf("akcija" to if (dodeli) "dodeli" else "oduzmi", "uid" to k.uid)) {
                            FishPoruka.prikazi(if (dodeli) "Bedž je dodeljen" else "Bedž je oduzet")
                        }
                        potvrda = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (dodeli) FishBoje.Akcenat else FishBoje.Greska,
                        contentColor = if (dodeli) FishBoje.Pozadina else androidx.compose.ui.graphics.Color.White
                    )
                ) { Text(if (dodeli) "Dodeli" else "Oduzmi", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { potvrda = null }) { Text("Otkaži", color = FishBoje.TekstSekundarni) } }
        )
    }
}

@Composable
private fun RedKorisnika(k: KorisnikZaBedz, dugme: String, enabled: Boolean, opasno: Boolean = false, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(FishBoje.Krug), contentAlignment = Alignment.Center) {
            if (k.slika.isNotBlank()) AsyncImage(k.slika, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(k.ime.take(1).uppercase(), color = FishBoje.Akcenat, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(k.ime, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (k.opis.isNotBlank()) Text(k.opis, color = FishBoje.TekstPriguseni, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        TextButton(onClick = onClick, enabled = enabled) {
            Text(dugme, color = if (!enabled) FishBoje.TekstPriguseni else if (opasno) FishBoje.Greska else FishBoje.Akcenat, fontWeight = FontWeight.Bold)
        }
    }
}
