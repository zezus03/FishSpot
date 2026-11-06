package com.example.fishspot.feature.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
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

private data class Nalog(val uid: String, val ime: String, val opis: String)

// Odeljak u admin panelu: svi ulovi sa jednog naloga prelaze na drugi (npr. sa probnog naloga na pravi).
// Server (adminPrebaciUlove) prebacuje i poene, a bedževe i izazove računa ponovo za oba naloga.
@Composable
fun AdminPrenosOdeljak() {
    var sa by remember { mutableStateOf<Nalog?>(null) }
    var na by remember { mutableStateOf<Nalog?>(null) }
    var pregled by remember { mutableStateOf<Pair<Long, Long>?>(null) } // (ulova, poena)
    var radi by remember { mutableStateOf(false) }
    var potvrda by remember { mutableStateOf(false) }

    fun prebaci(probno: Boolean) {
        val s = sa ?: return
        val n = na ?: return
        radi = true
        FirebaseFunctions.getInstance().getHttpsCallable("adminPrebaciUlove")
            .call(mapOf("sa" to s.uid, "na" to n.uid, "probno" to probno))
            .addOnSuccessListener { r ->
                radi = false
                val m = r.data as? Map<*, *> ?: return@addOnSuccessListener
                val ulova = (m["ulova"] as? Number)?.toLong() ?: 0
                val poena = (m["poena"] as? Number)?.toLong() ?: 0
                if (probno) pregled = ulova to poena
                else {
                    FishPoruka.prikazi("Prebačeno $ulova ulova i $poena poena na nalog ${n.ime}", dugo = true)
                    sa = null; pregled = null
                }
            }
            .addOnFailureListener { radi = false; FishPoruka.prikazi(it.message ?: "Greška na serveru") }
    }

    if (potvrda) {
        val (ulova, poena) = pregled ?: (0L to 0L)
        AlertDialog(
            onDismissRequest = { potvrda = false },
            containerColor = FishBoje.Kartica,
            title = { Text("Prebaciti ulove?", color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "$ulova ulova i $poena poena prelaze sa naloga ${sa?.ime} na nalog ${na?.ime}. " +
                        "Bedževi i izazovi se računaju ponovo za oba naloga. Ovo se ne može lako vratiti.",
                    color = FishBoje.TekstSekundarni
                )
            },
            confirmButton = {
                Button(
                    onClick = { potvrda = false; prebaci(probno = false) },
                    colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Greska, contentColor = Color.White)
                ) { Text("Prebaci", fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { potvrda = false }) { Text("Otkaži", color = FishBoje.TekstSekundarni) } }
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FishBoje.Kartica)
            .border(1.dp, FishBoje.IvicaKartice, RoundedCornerShape(16.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.SwapHoriz, null, tint = FishBoje.Akcenat, modifier = Modifier.size(32.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Prebaci ulove", color = FishBoje.TekstPrimarni, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Svi ulovi, poeni i bedževi sa jednog naloga prelaze na drugi.", color = FishBoje.TekstPriguseni, fontSize = 12.sp)
            }
        }
        IzborNaloga("Sa naloga (probni)", sa) { sa = it; pregled = null }
        IzborNaloga("Na nalog (pravi)", na) { na = it; pregled = null }

        pregled?.let { (ulova, poena) ->
            Text(
                if (ulova == 0L) "Nalog ${sa?.ime} nema nijedan ulov."
                else "Prebacuje se $ulova ulova i $poena poena tekuće sezone.",
                color = FishBoje.TekstSekundarni, fontSize = 14.sp
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { prebaci(probno = true) },
                enabled = !radi && sa != null && na != null && sa?.uid != na?.uid,
                modifier = Modifier.weight(1f)
            ) { Text("Proveri", color = FishBoje.TekstPrimarni) }
            Button(
                onClick = { potvrda = true },
                enabled = !radi && (pregled?.first ?: 0L) > 0L,
                colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = Color.Black),
                modifier = Modifier.weight(1f)
            ) { Text("Prebaci", fontWeight = FontWeight.Bold) }
        }
        if (radi) LinearProgressIndicator(Modifier.fillMaxWidth(), color = FishBoje.Akcenat)
    }
}

// Polje za izbor naloga: pretraga po korisničkom imenu (isto kao za bedž Prijatelj)
@Composable
private fun IzborNaloga(naslov: String, izabran: Nalog?, onIzbor: (Nalog?) -> Unit) {
    var upit by remember { mutableStateOf("") }
    var rezultati by remember { mutableStateOf(emptyList<Nalog>()) }

    Text(naslov, color = FishBoje.TekstPriguseni, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    if (izabran != null) {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(FishBoje.Krug).padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(izabran.ime, color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold)
                if (izabran.opis.isNotBlank()) Text(izabran.opis, color = FishBoje.TekstPriguseni, fontSize = 12.sp)
            }
            IconButton(onClick = { onIzbor(null) }) { Icon(Icons.Default.Close, "Promeni", tint = FishBoje.TekstPriguseni) }
        }
        return
    }
    OutlinedTextField(
        value = upit,
        onValueChange = { upit = it },
        placeholder = { Text("Početak korisničkog imena") },
        singleLine = true,
        colors = fishPoljeBoje(),
        shape = RoundedCornerShape(12.dp),
        trailingIcon = {
            IconButton(onClick = {
                FirebaseFunctions.getInstance().getHttpsCallable("adminBedzPrijatelj")
                    .call(mapOf("akcija" to "trazi", "upit" to upit.trim()))
                    .addOnSuccessListener { r ->
                        rezultati = ((r.data as? Map<*, *>)?.get("korisnici") as? List<*>).orEmpty().mapNotNull { s ->
                            val m = s as? Map<*, *> ?: return@mapNotNull null
                            Nalog(m["uid"] as? String ?: return@mapNotNull null,
                                (m["ime"] as? String).orEmpty().ifBlank { "Pecaroš" }, (m["imePrezime"] as? String).orEmpty())
                        }
                        if (rezultati.isEmpty()) FishPoruka.prikazi("Nema korisnika sa tim imenom")
                    }
                    .addOnFailureListener { FishPoruka.prikazi(it.message ?: "Greška na serveru") }
            }) { Icon(Icons.Default.Search, "Traži") }
        },
        modifier = Modifier.fillMaxWidth()
    )
    rezultati.forEach { n ->
        Text(
            n.ime + if (n.opis.isNotBlank()) "  ·  ${n.opis}" else "",
            color = FishBoje.TekstPrimarni,
            modifier = Modifier.fillMaxWidth().clickable { onIzbor(n); rezultati = emptyList(); upit = "" }.padding(vertical = 8.dp)
        )
    }
}
