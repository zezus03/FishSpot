package com.example.fishspot.feature.ranking

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.SetMeal
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.fishspot.core.theme.FishBoje
import com.example.fishspot.core.theme.FishPoruka
import com.example.fishspot.core.theme.NaslovSekcije
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val ZLATNA = Color(0xFFFFC107)

fun opisMerila(merilo: String) = when (merilo) {
    "ukupno" -> "Najveća ukupna težina"
    "broj" -> "Najviše ulova"
    else -> "Najteža riba"
}

private fun rezultatTekst(merilo: String, u: UcesnikIzazova): String = when (merilo) {
    "broj" -> "${u.rezultat.toInt()} " + if (u.rezultat.toInt() == 1) "ulov" else "ulova"
    else -> String.format(Locale.US, "%.2f", u.rezultat).trimEnd('0').trimEnd('.').replace('.', ',') + " kg"
}

private fun datum(ms: Long) = SimpleDateFormat("d.M.yyyy.", Locale.getDefault()).format(Date(ms))

// "Još 5 dana", "Još 3 h", "Počinje za 2 dana", "Završen"
fun stanjeIzazova(iz: Izazov, sada: Long = System.currentTimeMillis()): String {
    fun trajanje(ms: Long): String {
        val dana = ms / 86_400_000L
        val sati = ms / 3_600_000L
        return when {
            dana >= 1 -> "$dana " + if (dana == 1L) "dan" else "dana"
            sati >= 1 -> "$sati h"
            else -> "${(ms / 60_000L).coerceAtLeast(1)} min"
        }
    }
    return when {
        iz.predstojeci(sada) -> "Počinje za " + trajanje(iz.od - sada)
        iz.aktivan(sada) -> "Još " + trajanje(iz.doKraja - sada)
        else -> "Završen"
    }
}

// Kartica Izazovi na rang listi: aktivni, predstojeći i završeni izazovi
@Composable
fun IzazoviSadrzaj(vm: IzazoviViewModel, nazivVrste: (String) -> String) {
    val izazovi by vm.izazovi.collectAsState()
    var otvoren by remember { mutableStateOf<Izazov?>(null) }
    val sada = System.currentTimeMillis()
    val aktivni = izazovi.filter { it.aktivan(sada) }.sortedBy { it.doKraja }
    val predstojeci = izazovi.filter { it.predstojeci(sada) }.sortedBy { it.od }
    val zavrseni = izazovi.filter { sada > it.doKraja }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (izazovi.isEmpty()) item {
            Text(
                "Trenutno nema izazova. Kad se pojavi novi, dobićeš obaveštenje.",
                color = FishBoje.TekstSekundarni, fontSize = 15.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(32.dp)
            )
        }
        listOf("Aktivni izazovi" to aktivni, "Uskoro počinju" to predstojeci, "Završeni" to zavrseni).forEach { (naslov, lista) ->
            if (lista.isNotEmpty()) {
                item { NaslovSekcije(naslov, Modifier.padding(top = 10.dp)) }
                items(lista, key = { it.id }) { iz -> KarticaIzazova(iz, nazivVrste) { otvoren = iz } }
            }
        }
    }

    otvoren?.let { iz ->
        // Uvek najnovija verzija izazova (broj učesnika se menja)
        val svez = izazovi.firstOrNull { it.id == iz.id } ?: iz
        IzazovDetaljiDialog(svez, vm, nazivVrste) { otvoren = null }
    }
}

@Composable
private fun KarticaIzazova(iz: Izazov, nazivVrste: (String) -> String, onClick: () -> Unit) {
    val aktivan = iz.aktivan()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FishBoje.Kartica)
            .border(1.dp, if (aktivan) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).background((if (aktivan) ZLATNA else FishBoje.TekstPriguseni).copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.EmojiEvents, null, tint = if (aktivan) ZLATNA else FishBoje.TekstPriguseni) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(iz.naziv, color = FishBoje.TekstPrimarni, fontSize = 17.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(opisMerila(iz.merilo) + " · " + vrsteTekst(iz, nazivVrste), color = FishBoje.TekstSekundarni,
                    fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(stanjeIzazova(iz), color = if (aktivan) FishBoje.Akcenat else FishBoje.TekstPriguseni,
                fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Podatak(Icons.Default.CalendarMonth, "${datum(iz.od)} – ${datum(iz.doKraja)}")
            Podatak(Icons.Default.Group, "${iz.brojUcesnika} " + if (iz.brojUcesnika == 1L) "učesnik" else "učesnika")
        }
    }
}

@Composable
private fun Podatak(ikona: ImageVector, tekst: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ikona, null, tint = FishBoje.Akcenat, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(tekst, color = FishBoje.TekstSekundarni, fontSize = 12.sp)
    }
}

private fun vrsteTekst(iz: Izazov, nazivVrste: (String) -> String) =
    if (iz.vrste.isEmpty()) "sve vrste" else iz.vrste.joinToString(", ") { nazivVrste(it) }

@Composable
private fun IzazovDetaljiDialog(iz: Izazov, vm: IzazoviViewModel, nazivVrste: (String) -> String, onZatvori: () -> Unit) {
    LaunchedEffect(iz.id) { vm.otvori(iz.id) }
    val ucesnici by vm.ucesnici.collectAsState()
    val prijavljujem by vm.prijavljujem.collectAsState()
    val mojIndeks = ucesnici.indexOfFirst { it.uid == vm.mojUid }
    val ucestvujem = mojIndeks >= 0
    val mozeDaSePrijavi = !ucestvujem && System.currentTimeMillis() <= iz.doKraja

    Dialog(onDismissRequest = onZatvori, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = FishBoje.Pozadina) {
            Column(Modifier.fillMaxSize().systemBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("FISHSPOT • IZAZOV", color = FishBoje.TekstPriguseni, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Text(iz.naziv, color = FishBoje.TekstPrimarni, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onZatvori) { Icon(Icons.Default.Close, "Zatvori", tint = FishBoje.TekstPrimarni) }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(FishBoje.Kartica).padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(stanjeIzazova(iz), color = if (iz.aktivan()) FishBoje.Akcenat else FishBoje.TekstPriguseni,
                                fontWeight = FontWeight.Bold)
                            if (iz.opis.isNotBlank()) Text(iz.opis, color = FishBoje.TekstSekundarni, fontSize = 14.sp)
                            Pravilo(Icons.Default.EmojiEvents, opisMerila(iz.merilo))
                            Pravilo(Icons.Default.SetMeal, "Vrste: " + vrsteTekst(iz, nazivVrste))
                            Pravilo(Icons.Default.CalendarMonth, "${datum(iz.od)} – ${datum(iz.doKraja)}")
                            if (iz.samoKamera) Pravilo(Icons.Default.CameraAlt, "Samo slike iz kamere u aplikaciji")
                            if (iz.nagrada.isNotBlank()) Pravilo(Icons.Default.CardGiftcard, "Nagrada: ${iz.nagrada}")
                            Pravilo(Icons.Default.Group, "${iz.brojUcesnika} " + if (iz.brojUcesnika == 1L) "učesnik" else "učesnika")
                            Text(
                                "Računaju se provereni ulovi objavljeni tokom izazova (ribe preko 5 kg tek kad ih admin odobri).",
                                color = FishBoje.TekstPriguseni, fontSize = 12.sp
                            )
                        }
                    }
                    if (ucestvujem) item {
                        Text(
                            "Ti si ${mojIndeks + 1}. mesto · ${rezultatTekst(iz.merilo, ucesnici[mojIndeks])}",
                            color = FishBoje.Akcenat, fontWeight = FontWeight.Bold, fontSize = 15.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }
                    item { NaslovSekcije("Rang lista", Modifier.padding(top = 8.dp)) }
                    if (ucesnici.isEmpty()) item {
                        Text("Još nema prijavljenih. Budi prvi!", color = FishBoje.TekstSekundarni, fontSize = 14.sp)
                    }
                    itemsIndexed(ucesnici, key = { _, u -> u.uid }) { i, u -> RedUcesnika(i + 1, u, iz.merilo, u.uid == vm.mojUid) }
                }
                if (mozeDaSePrijavi) {
                    Button(
                        onClick = {
                            vm.pridruziSe(iz.id) { greska ->
                                FishPoruka.prikazi(greska ?: "Prijavljen si! Tvoji ulovi iz ovog perioda se već računaju.", dugo = true)
                            }
                        },
                        enabled = !prijavljujem,
                        modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FishBoje.Akcenat, contentColor = FishBoje.Pozadina)
                    ) {
                        if (prijavljujem) CircularProgressIndicator(Modifier.size(22.dp), color = FishBoje.Pozadina, strokeWidth = 2.dp)
                        else Text("Pridruži se izazovu", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun Pravilo(ikona: ImageVector, tekst: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(ikona, null, tint = FishBoje.Akcenat, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(tekst, color = FishBoje.TekstPrimarni, fontSize = 14.sp)
    }
}

@Composable
private fun RedUcesnika(mesto: Int, u: UcesnikIzazova, merilo: String, jaSam: Boolean) {
    val boja = when (mesto) { 1 -> ZLATNA; 2 -> Color(0xFFB0BEC5); 3 -> Color(0xFFCD7F32); else -> FishBoje.TekstPriguseni }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (jaSam) FishBoje.Akcenat.copy(alpha = 0.12f) else FishBoje.Kartica)
            .border(1.dp, if (jaSam) FishBoje.AkcenatTamni else FishBoje.IvicaKartice, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$mesto.", color = boja, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.width(32.dp))
        Box(Modifier.size(40.dp).clip(CircleShape).background(FishBoje.Krug), contentAlignment = Alignment.Center) {
            if (u.slika.isNotBlank()) AsyncImage(u.slika, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            else Text(u.ime.take(1).uppercase(), color = FishBoje.Akcenat, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(u.ime + if (jaSam) " (ti)" else "", color = FishBoje.TekstPrimarni, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (u.najboljiVrsta.isNotBlank()) Text(u.najboljiVrsta, color = FishBoje.TekstPriguseni, fontSize = 12.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (u.najboljiSlika.isNotBlank()) {
            AsyncImage(u.najboljiSlika, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
            Spacer(Modifier.width(10.dp))
        }
        Text(rezultatTekst(merilo, u), color = FishBoje.TekstPrimarni, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
}
